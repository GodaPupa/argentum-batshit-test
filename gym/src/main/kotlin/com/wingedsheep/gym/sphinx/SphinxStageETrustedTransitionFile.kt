package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Prospective trusted-side durable storage for accepted Ponder transitions. The actor never
 * receives the file, raw states, or recorded actions. This is not an official attempt journal.
 */
internal class SphinxStageETrustedTransitionFile private constructor(private val path: Path) {
    @Serializable
    private data class Line(val index: Int, val previousSha256: String,
                            val recordJson: String, val sha256: String)

    private val json = Json { encodeDefaults = true }

    companion object {
        fun create(path: Path): SphinxStageETrustedTransitionFile {
            Files.createDirectories(path.toAbsolutePath().parent)
            FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                it.force(true)
            }
            return SphinxStageETrustedTransitionFile(path)
        }

        fun reopen(path: Path): SphinxStageETrustedTransitionFile {
            require(Files.isRegularFile(path) && !Files.isSymbolicLink(path))
            val file = SphinxStageETrustedTransitionFile(path)
            file.records() // Verify the entire existing chain before another append.
            return file
        }

        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun append(recordJson: String) {
        require(recordJson.isNotBlank() && !recordJson.contains('\n'))
        val prior = records()
        val previous = prior.lastOrNull()?.second ?: "0".repeat(64)
        val index = prior.size
        val hash = digest("$index:$previous:$recordJson")
        val bytes = (json.encodeToString(Line(index, previous, recordJson, hash)) + "\n")
            .toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
            channel.lock().use {
                // Refuse a concurrent writer or bytes added since verification.
                require(records().size == index) { "Trusted transition journal changed during append" }
                val data = ByteBuffer.wrap(bytes)
                while (data.hasRemaining()) channel.write(data)
                channel.force(true)
            }
        }
    }

    /** The complete verified, ordered record stream; a torn line or hash drift rejects replay. */
    fun records(): List<Pair<String, String>> {
        val bytes = Files.readAllBytes(path)
        if (bytes.isEmpty()) return emptyList()
        require(bytes.last() == '\n'.code.toByte()) { "Torn trusted transition journal" }
        val lines = bytes.toString(StandardCharsets.UTF_8).trimEnd('\n').split('\n')
        var previous = "0".repeat(64)
        return lines.mapIndexed { index, raw ->
            val line = json.decodeFromString<Line>(raw)
            require(line.index == index && line.previousSha256 == previous &&
                line.sha256 == digest("$index:$previous:${line.recordJson}")) {
                "Trusted transition journal chain drift at $index"
            }
            previous = line.sha256
            line.recordJson to line.sha256
        }
    }

    fun replay(preState: GameState, transitions: SphinxStageEAcceptedTransitionJournal): GameState =
        records().fold(preState) { state, (record, _) -> transitions.replay(state, record) }
}
