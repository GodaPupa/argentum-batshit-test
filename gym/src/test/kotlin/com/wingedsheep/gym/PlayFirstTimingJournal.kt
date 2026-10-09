package com.wingedsheep.gym

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant

/** Diagnostic only: no timer interrupts, action changes, retries, or engine dependencies.
 * Single calling thread; force each record before/after exactly one supplied call.
 * A retained START without END/ERROR identifies a boundary, not the internal cause of a stall.
 * fsync is a local best effort, not independent custody or protection from host loss.
 */
class PlayFirstTimingJournal(
    path: Path,
    identity: Map<String, String>,
    private val nanoClock: () -> Long = System::nanoTime,
    private val wallClock: () -> String = { Instant.now().toString() },
) : AutoCloseable {
    private val identity = identity.toMap()
    private val ownerThread = Thread.currentThread().id
    private val channel: FileChannel
    private var nextSpan = 0L
    private var closed = false

    init {
        require(identity.isNotEmpty())
        require(identity.keys.all { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*")) })
        channel = FileChannel.open(path, CREATE_NEW, WRITE)
    }

    fun <T> measure(phase: String, context: Map<String, String> = emptyMap(), body: () -> T): T {
        check(!closed && Thread.currentThread().id == ownerThread) { "Journal is closed or used by another thread" }
        require(phase.matches(Regex("[A-Z][A-Z0-9_]*")))
        val capturedContext = context.toMap()
        val span = ++nextSpan
        val started = nanoClock()
        emit("CALL_START", span, phase, capturedContext, started, null, null)
        val result = try {
            body()  // exactly once, on the original calling thread, with no fallback
        } catch (failure: Throwable) {
            try {
                val ended = nanoClock()
                emit("CALL_ERROR", span, phase, capturedContext, ended, ended - started, failure)
            } catch (recordingFailure: Throwable) {
                failure.addSuppressed(recordingFailure)
            }
            throw failure
        }
        val ended = nanoClock()
        emit("CALL_END", span, phase, capturedContext, ended, ended - started, null)
        return result
    }

    private fun emit(kind: String, span: Long, phase: String, context: Map<String, String>,
                     tick: Long, elapsed: Long?, failure: Throwable?) {
        val fields = mutableListOf(
            "\"recordType\":" + quote(kind), "\"span\":$span", "\"phase\":" + quote(phase),
            "\"wallTimeUtc\":" + quote(wallClock()), "\"monotonicNanos\":$tick",
            "\"threadId\":$ownerThread", "\"identity\":" + objectJson(identity),
            "\"context\":" + objectJson(context),
        )
        if (elapsed != null) fields += "\"elapsedNanos\":$elapsed"
        if (failure != null) {
            fields += "\"failureClass\":" + quote(failure.javaClass.name)
            fields += "\"failureMessage\":" + quote(failure.message ?: "")
        }
        val buffer = ByteBuffer.wrap(("{" + fields.joinToString(",") + "}\n").toByteArray(Charsets.UTF_8))
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }

    override fun close() {
        if (!closed) {
            closed = true
            channel.close()
        }
    }

    private fun objectJson(values: Map<String, String>): String = values.toSortedMap().entries
        .joinToString(",", "{", "}") { (key, value) -> quote(key) + ":" + quote(value) }

    private fun quote(value: String): String = buildString {
        append('"')
        for (char in value) when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 32 || char.code in 0xD800..0xDFFF)
                append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
        }
        append('"')
    }
}
