package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.ManaCost
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

/**
 * Excluded, artificial late-board AI call profiling ONLY. Not a G10 replay, not G11,
 * not a formal sample, not a new exhibition and not a gameplay-result generator.
 * Never loads any G10 original seed, case, journal, or state. Test fixture has its
 * own freshly created 40-Forest mirror deck and manually minted battlefield cards.
 * The only fixture actions are bounded phase setup by GameTestDriver; no selected
 * action is ever submitted to GameTestDriver or the actual game executor.
 */
class G10R5LateBoardBoundedSourceTest : FunSpec({
    val enabled = System.getenv("ARGENTUM_G10_R5_PROFILE") == "EXCLUDED_LATE_BOARD_ONLY"

    test("excluded mid late-board Monster Tron AI attribution").config(enabled = enabled, timeout = 9.minutes) {
        runR5SyntheticState(
            label = "mid", mine = 2, plant = 2, tower = 2, forest = 2,
            pylons = 1, ornaments = 1, trails = 1, boulders = 0, creatureCount = 1,
        )
    }
    test("excluded crowded late-board Monster Tron AI attribution").config(enabled = enabled, timeout = 9.minutes) {
        runR5SyntheticState(
            label = "crowded", mine = 4, plant = 4, tower = 4, forest = 2,
            pylons = 2, ornaments = 3, trails = 2, boulders = 1, creatureCount = 3,
        )
    }
})

private fun runR5SyntheticState(
    label: String, mine: Int, plant: Int, tower: Int, forest: Int,
    pylons: Int, ornaments: Int, trails: Int, boulders: Int, creatureCount: Int,
) {
    check(System.getenv("ARGENTUM_G10_R5_PROFILE") == "EXCLUDED_LATE_BOARD_ONLY")
    check(System.getenv("PLAY_FIRST_ONE_SHOT_ACK").isNullOrBlank())
    check(System.getenv("PLAY_FIRST_ISOLATED_ACK").isNullOrBlank())
    check(System.getenv("PLAY_FIRST_ISOLATED_CASE_FILE").isNullOrBlank())
    check(System.getenv("ARGENTUM_SUCCESSOR_REQUEST_JSON").isNullOrBlank())
    System.getenv("GITHUB_REF")?.let {
        check(it == "refs/heads/play-first/g10-r5-bounded-source-choice-20261014")
    }
    System.getenv("GITHUB_RUN_ATTEMPT")?.let { check(it == "1") }

    val path = System.getenv("ARGENTUM_G10_R5_EVIDENCE_DIR")?.let { Path.of(it) }
        ?: Files.createTempDirectory("g10-excluded-r5-")
    Files.createDirectories(path)

    R5DurableJournal(path.resolve("synthetic-" + label + ".jsonl"), label).use { j ->
        val driver = GameTestDriver()
        // R2-style test harness; registration may take seconds but is intentionally
        // outside the per-decision attribution and never executes a full game loop.
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val me = driver.player1
        val opponent = driver.player2
        check(driver.state.priorityPlayerId == me)
        fun battlefield(name: String, count: Int, ownerIsMe: Boolean = true) {
            repeat(count) {
                val p = if (ownerIsMe) me else opponent
                when (name) {
                    "Forest", "Urza's Mine", "Urza's Power Plant", "Urza's Tower", "Conduit Pylons" ->
                        driver.putLandOnBattlefield(p, name)
                    "Centaur Courser" -> driver.putCreatureOnBattlefield(p, name)
                    else -> driver.putPermanentOnBattlefield(p, name)
                }
            }
        }
        battlefield("Urza's Mine", mine)
        battlefield("Urza's Power Plant", plant)
        battlefield("Urza's Tower", tower)
        battlefield("Forest", forest)
        battlefield("Conduit Pylons", pylons)
        battlefield("Bonder's Ornament", ornaments)
        battlefield("Candy Trail", trails)
        battlefield("Giant's Boulder", boulders)
        battlefield("Centaur Courser", creatureCount)
        battlefield("Centaur Courser", creatureCount + 1, ownerIsMe = false)
        // These cards are alternatives, not a reconstruction of the G10 hand.
        listOf("Ancient Stirrings", "Expedition Map", "Boulderbranch Golem",
            "Maelstrom Colossus", "Crop Rotation", "Forest").forEach { driver.putCardInHand(me, it) }

        val state = driver.state
        check(!state.gameOver && state.pendingDecision == null && state.priorityPlayerId == me)
        j.note("ARTIFICIAL_BOARD_READY", mapOf(
            "mine" to mine.toString(), "plant" to plant.toString(), "tower" to tower.toString(),
            "forest" to forest.toString(), "pylons" to pylons.toString(),
            "ornaments" to ornaments.toString(), "trails" to trails.toString(),
            "boulders" to boulders.toString(), "creatures" to creatureCount.toString(),
        ))

        val simulator = GameSimulator(driver.cardRegistry)
        val solver = ManaSolver(driver.cardRegistry)
        val player = AIPlayer.create(driver.cardRegistry, me, PestMonsterTronPolicy.profile)
        val payments = listOf("{3}{G}", "{8}", "{9}")
        for ((index, cost) in payments.withIndex()) {
            val feasible = j.measure("MANA_CAN_PAY_" + index) {
                solver.canPay(state, me, ManaCost.parse(cost))
            }
            j.note("MANA_PAYMENT_DIAGNOSTIC", mapOf("cost" to cost, "canPay" to feasible.toString()))
        }

        val legal: List<LegalAction> = j.measure("LEGAL_ACTION_ENUMERATION") {
            simulator.getLegalActions(state, me)
        }
        val candidateTypes = legal.groupingBy { it.actionType }.eachCount()
        check(legal.isNotEmpty())
        j.note("LEGAL_ACTION_COUNTS", mapOf(
            "total" to legal.size.toString(),
            "affordable" to legal.count { it.affordable }.toString(),
            "types" to candidateTypes.toSortedMap().toString(),
        ))
        val samples = legal.filter { it.affordable && it.actionType in
            setOf("CastSpell", "ActivateAbility", "PlayLand") }.take(2)
        for ((index, option) in samples.withIndex()) {
            // This is a scratch simulation. The original GameTestDriver state
            // is never replaced, advanced, or used to submit the returned move.
            val result = j.measure("CANDIDATE_SIMULATION_" + index) {
                simulator.simulate(state, option.action)
            }
            j.note("CANDIDATE_DIAGNOSTIC", mapOf(
                "index" to index.toString(), "actionType" to option.actionType,
                "simulationResultType" to result::class.java.simpleName,
            ))
        }

        val chosen = j.measure("STRATEGY_CHOOSE_FROM") { player.chooseFrom(state, legal).action }
        check(chosen.playerId == me)
        check(driver.state === state) { "Profiling changed original artificial fixture state" }
        j.note("SYNTHETIC_DECISION_COMPLETE", mapOf("choiceClass" to chosen::class.java.simpleName))
        println("G10_R5_EXCLUDED profile=" + label + " legal=" + legal.size +
            " sampled=" + samples.size + " decision=" + chosen::class.java.simpleName)
    }
}

private class R5DurableJournal(path: Path, private val caseLabel: String) : AutoCloseable {
    private val channel = FileChannel.open(path, CREATE_NEW, WRITE)
    private val finished = AtomicBoolean(false)
    private val caller = Thread.currentThread()
    @Volatile private var currentPhase: String = "ARTIFICIAL_FIXTURE_SETUP"
    private val sampler = Thread({
        try {
            while (!finished.get()) {
                Thread.sleep(1_000L)
                if (finished.get()) break
                val frames = caller.stackTrace
                val category = classify(frames)
                write(buildJsonObject {
                    put("contract", "ARGENTUM_G10_R5_EXCLUDED_SYNTHETIC_V1")
                    put("scenario", caseLabel)
                    put("type", "NON_INTERRUPTING_STACK_SAMPLE")
                    put("phase", currentPhase)
                    put("topCategory", category)
                    put("stack", frames.take(45).joinToString("\n") { it.toString() })
                    put("wallTimeUtc", Instant.now().toString())
                }.toString())
            }
        } catch (_: InterruptedException) {
            // Observational thread is a daemon; never cancels, changes or retries AI work.
        }
    }, "g10-r5-observation-$caseLabel")

    init { sampler.isDaemon = true; sampler.start() }

    private fun classify(frames: Array<StackTraceElement>): String {
        for (f in frames) {
            val n = f.className
            if ("ManaSolver" in n || ".mechanics.mana." in n) return "MANA_OR_PAYMENT"
            if (".legalactions." in n) return "ACTION_ENUMERATION"
            if (".rollout." in n) return "ROLLOUT_OR_PLAYOUT"
            if ("GameSimulator" in n || "ActionProcessor" in n) return "CANDIDATE_SIMULATION_OR_ENGINE"
            if ("Strategist" in n) return "STRATEGY_EVALUATION"
        }
        return "OTHER"
    }

    @Synchronized private fun write(row: String) {
        val bytes = ByteBuffer.wrap((row + "\n").toByteArray(Charsets.UTF_8))
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
    }

    fun <T> measure(phase: String, body: () -> T): T {
        check(Regex("[A-Z][A-Z0-9_]*").matches(phase))
        val begin = System.nanoTime()
        currentPhase = phase
        emit("CALL_START", phase, begin, null, null)
        val value = try { body() } catch (original: Throwable) {
            val end = System.nanoTime()
            try { emit("CALL_ERROR", phase, end, end - begin, original.javaClass.name) }
            catch (failure: Throwable) { original.addSuppressed(failure) }
            currentPhase = "AFTER_" + phase
            throw original
        }
        val end = System.nanoTime()
        emit("CALL_END", phase, end, end - begin, null)
        currentPhase = "AFTER_" + phase
        return value
    }

    private fun emit(type: String, phase: String, nanos: Long, elapsed: Long?, err: String?) {
        write(buildJsonObject {
            put("contract", "ARGENTUM_G10_R5_EXCLUDED_SYNTHETIC_V1")
            put("scenario", caseLabel)
            put("type", type)
            put("phase", phase)
            put("monotonicNanos", nanos)
            elapsed?.let { put("elapsedNanos", it) }
            err?.let { put("errorType", it) }
            put("wallTimeUtc", Instant.now().toString())
        }.toString())
    }

    fun note(type: String, fields: Map<String, String>) {
        write(buildJsonObject {
            put("contract", "ARGENTUM_G10_R5_EXCLUDED_SYNTHETIC_V1")
            put("scenario", caseLabel)
            put("type", type)
            for ((key, value) in fields.toSortedMap()) put(key, value)
            put("wallTimeUtc", Instant.now().toString())
        }.toString())
    }

    override fun close() {
        finished.set(true)
        sampler.interrupt()
        sampler.join(3_000L)
        channel.force(true)
        channel.close()
    }
}
