package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

/**
 * EXCLUDED synthetic state only. This file must not be enabled by normal CI.
 *
 * A GameTestDriver creates a throwaway synthetic state, never the G10 frozen case;
 * no real gameplay actions are submitted, no original case or seed is loaded and
 * no winner, formal result, or gameplay approval can be produced here.
 *
 * The original AI's getLegalActions and chooseFrom calls are passed unchanged to
 * G10DecisionPhaseProbe. Durable boundaries and asynchronous stack samples
 * distinguish enumeration from strategy work without mutating their arguments,
 * interrupting them, supplying fallback actions, or retrying them.
 */
class G10SyntheticDecisionAttributionTest : FunSpec({
    val enabled = System.getenv("ARGENTUM_G10_SYNTHETIC_ATTRIBUTION") ==
        "EXCLUDED_SYNTHETIC_STATE_ONLY"

    test("excluded sparse synthetic Monster Tron decision").config(
        enabled = enabled, timeout = 7.minutes
    ) {
        profileSyntheticDecision("sparse", forestCount = 6, creatureCount = 2)
    }

    test("excluded dense synthetic Monster Tron decision").config(
        enabled = enabled, timeout = 7.minutes
    ) {
        profileSyntheticDecision("dense", forestCount = 18, creatureCount = 6)
    }
})

private fun profileSyntheticDecision(label: String, forestCount: Int, creatureCount: Int) {
    check(System.getenv("ARGENTUM_G10_SYNTHETIC_ATTRIBUTION") == "EXCLUDED_SYNTHETIC_STATE_ONLY")
    check(System.getenv("PLAY_FIRST_ONE_SHOT_ACK").isNullOrBlank())
    check(System.getenv("PLAY_FIRST_ISOLATED_CASE_FILE").isNullOrBlank())
    check(System.getenv("ARGENTUM_SUCCESSOR_REQUEST_JSON").isNullOrBlank())
    System.getenv("GITHUB_REF")?.let {
        check(it == "refs/heads/play-first/g10-latency-synthetic-validation-20261014")
    }
    System.getenv("GITHUB_RUN_ATTEMPT")?.let { check(it == "1") }

    val root = System.getenv("ARGENTUM_G10_SYNTHETIC_EVIDENCE_DIR")?.let { Path.of(it) }
        ?: Files.createTempDirectory("g10-excluded-synthetic-")
    Files.createDirectories(root)

    DurableG10SyntheticJournal(root.resolve("synthetic-" + label + ".jsonl"), label).use { journal ->
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        // These are standalone test-deck placeholders, not the real Pest/Tron decks.
        // Initialization is confined to synthetic test-fixture setup; no game loop runs.
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 40),
            skipMulligans = true,
            startingPlayer = 0,
        )
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val actor = driver.player1
        val opponent = driver.player2
        repeat(forestCount) { driver.putLandOnBattlefield(actor, "Forest") }
        repeat(creatureCount) { driver.putCreatureOnBattlefield(actor, "Centaur Courser") }
        repeat(creatureCount / 2) { driver.putCreatureOnBattlefield(opponent, "Centaur Courser") }
        driver.putCardInHand(actor, "Giant Growth")
        driver.putCardInHand(actor, "Centaur Courser")
        driver.putCardInHand(actor, "Force of Nature")
        val state = driver.state
        check(state.priorityPlayerId == actor)
        check(state.pendingDecision == null && !state.gameOver)

        val agent = AIPlayer.create(driver.cardRegistry, actor, PestMonsterTronPolicy.profile)
        val simulator = GameSimulator(driver.cardRegistry)
        journal.note("SYNTHETIC_STATE_PREPARED", forestCount, creatureCount)
        var enumerated: List<LegalAction>? = null
        var receivedByStrategist: List<LegalAction>? = null
        try {
            val action = G10DecisionPhaseProbe.choose(
                enumerate = {
                    simulator.getLegalActions(state, actor).also { enumerated = it }
                },
                chooseFrom = { options ->
                    receivedByStrategist = options
                    agent.chooseFrom(state, options).action
                },
                emit = journal::boundary,
            )
            check(enumerated != null && enumerated!!.isNotEmpty())
            check(receivedByStrategist === enumerated)
            check(action.playerId == actor)
            check(driver.state === state)
            journal.note("SYNTHETIC_DECISION_COMPLETE", forestCount, creatureCount)
            println("G10_EXCLUDED_SYNTHETIC_DECISION " + label +
                " options=" + enumerated!!.size + " result=" + action::class.java.simpleName)
        } catch (failure: Throwable) {
            journal.note("SYNTHETIC_DECISION_FAILED", forestCount, creatureCount)
            throw failure
        }
    }
}

/** Synchronously fsync each record; stack-sampler never interrupts AI evaluation. */
private class DurableG10SyntheticJournal(path: Path, private val scenario: String) : AutoCloseable {
    private val channel = FileChannel.open(path, CREATE_NEW, WRITE)
    private val closed = AtomicBoolean(false)
    private val worker = Thread.currentThread()
    @Volatile private var phase = "SYNTHETIC_SETUP"
    private val sampler = Thread({
        try {
            while (!closed.get()) {
                Thread.sleep(5_000L)
                if (closed.get()) break
                val current = phase
                val stack = worker.stackTrace.take(70).map { it.toString() }
                append(buildJsonObject {
                    put("contract", "ARGENTUM_G10_EXCLUDED_SYNTHETIC_PROFILE_V1")
                    put("scenario", scenario)
                    put("kind", "THREAD_STACK_SAMPLE")
                    put("phase", current)
                    put("stackFrames", stack.joinToString("\n"))
                    put("wallClockUtc", java.time.Instant.now().toString())
                }.toString())
            }
        } catch (_: InterruptedException) {
            // Termination of the observation daemon, never a modification of AI work.
        }
    }, "g10-excluded-stack-sample")

    init {
        sampler.isDaemon = true
        sampler.start()
    }

    @Synchronized private fun append(record: String) {
        val bytes = (record + "\n").toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }

    fun boundary(event: G10DecisionPhaseProbe.Boundary) {
        if (event.kind == "CALL_START") phase = event.phase
        append(buildJsonObject {
            put("contract", "ARGENTUM_G10_EXCLUDED_SYNTHETIC_PROFILE_V1")
            put("scenario", scenario)
            put("kind", event.kind)
            put("phase", event.phase)
            put("monotonicNanos", event.monotonicNanos)
            event.elapsedNanos?.let { put("elapsedNanos", it) }
            event.errorType?.let { put("errorType", it) }
            put("wallClockUtc", java.time.Instant.now().toString())
        }.toString())
        if (event.kind != "CALL_START") phase = "AFTER_" + event.phase
    }

    fun note(kind: String, forestCount: Int, creatureCount: Int) {
        append(buildJsonObject {
            put("contract", "ARGENTUM_G10_EXCLUDED_SYNTHETIC_PROFILE_V1")
            put("scenario", scenario)
            put("kind", kind)
            put("forestCount", forestCount)
            put("creatureCount", creatureCount)
            put("wallClockUtc", java.time.Instant.now().toString())
        }.toString())
    }

    override fun close() {
        closed.set(true)
        sampler.interrupt()
        sampler.join(3_000L)
        channel.force(true)
        channel.close()
    }
}
