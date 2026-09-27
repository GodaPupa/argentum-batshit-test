package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files
import java.nio.file.Path

/** All rows below are excluded deterministic fixtures, never an R1 corpus allocation. */
class IndustrialWasteV2QuietCheckpointReplayTest : FunSpec({
    val codec = IndustrialWasteV2AllocationTrace.CODEC
    val serializer = IndustrialWasteV2QuietCheckpointObservation.serializer()
    val labels = (1..60).map { "Forest#$it" }
    val input = IndustrialWasteV2AllocationInput(
        "SYNTHETIC_QUIET_REPLAY", List(60) { "Forest" },
        "IW_V2_R1_SYNTHETIC_QUIET_REPLAY_ONLY", 20_003, List(4) { labels }, 1,
        9_250_925_111L,
    )
    // One real excluded initialization, then each adversary restores its recorded snapshot.
    // The production verifier never constructs a new game or invokes an ordering shuffle.
    val baseline: Path by lazy {
        Files.createTempDirectory("iw-v2-quiet-baseline").resolve("allocation").also {
            val result = IndustrialWasteV2AllocationRunner.runExcluded(input, it)
            result.status.status shouldBe IndustrialWasteV2StopStatus.TURN_CAP
        }
    }
    fun rows(directory: Path) = Files.readAllLines(directory.resolve("quiet-checkpoint-observations.jsonl"))
        .map { codec.decodeFromString(serializer, it) }
    fun writeRows(directory: Path, rows: List<IndustrialWasteV2QuietCheckpointObservation>) {
        Files.writeString(directory.resolve("quiet-checkpoint-observations.jsonl"),
            rows.joinToString("\n", postfix = "\n") { codec.encodeToString(serializer, it) })
    }
    fun withChangedTrace(change: (Path) -> Unit) {
        val directory = Files.createTempDirectory("iw-v2-quiet-adversary")
        try {
            listOf("input.json", "initial-state.json", "final-state.json", "actions.json",
                "transitions.jsonl", "checkpoints.json", "quiet-checkpoint-observations.jsonl").forEach {
                Files.copy(baseline.resolve(it), directory.resolve(it))
            }
            change(directory)
            shouldThrow<IllegalStateException> { IndustrialWasteV2AllocationRunner.verifyReplay(directory) }
        } finally {
            Files.list(directory).use { files -> files.forEach(Files::delete) }
            Files.delete(directory)
        }
    }

    test("snapshot replay certifies every quiet state and the first-per-turn compatibility view on the draw") {
        val observations = rows(baseline)
        (observations.size > 8) shouldBe true
        observations.groupBy { it.checkpoint.ownTurn }.keys.toList() shouldBe (1..8).toList()
        observations.zipWithNext().all { (a, b) -> a.acceptedActions < b.acceptedActions } shouldBe true
        val replay = IndustrialWasteV2AllocationRunner.verifyReplay(baseline)
        replay.status shouldBe "EXACT_ACTION_EVENT_STATE_REPLAY"
        replay.checkpointReplay shouldBe "EXACT_EVERY_QUIET_CHECKPOINT_REPLAY"
        replay.quietCheckpointObservations shouldBe observations.size
        replay.actions shouldBe codec.decodeFromString(IndustrialWasteV2AllocationReplay.serializer(),
            Files.readString(baseline.resolve("replay.json"))).actions
        val initial = codec.decodeFromString(GameState.serializer(), Files.readString(baseline.resolve("initial-state.json")))
        val measured = initial.turnOrder.single {
            initial.getEntity(it)?.get<LibraryOrderingComponent>()?.plan?.namespace == input.namespace
        }
        // The measured player starts second. Replaying the first turnOrder seat instead would
        // certify the passive deck's observations and is not equivalent to the recorded contract.
        (initial.turnOrder.indexOf(measured) == 1) shouldBe true
    }

    test("missing the first eligible quiet state cannot pass replay") {
        withChangedTrace { writeRows(it, rows(it).drop(1)) }
    }
    test("missing a later state from the same own turn cannot hide behind the compatibility view") {
        withChangedTrace { directory ->
            val original = rows(directory)
            val later = original.indices.first { it > 0 &&
                original[it].checkpoint.ownTurn == original[it - 1].checkpoint.ownTurn }
            writeRows(directory, original.filterIndexed { index, _ -> index != later })
        }
    }
    test("extra quiet observations after the final eligible state are rejected") {
        withChangedTrace { directory ->
            val original = rows(directory)
            writeRows(directory, original + original.last().copy(acceptedActions = original.last().acceptedActions + 1))
        }
    }
    test("reordered quiet observations are rejected even when every row is intact") {
        withChangedTrace { directory ->
            val changed = rows(directory).toMutableList()
            val first = changed[0]
            changed[0] = changed[1]
            changed[1] = first
            writeRows(directory, changed)
        }
    }
    test("duplicate action indices cannot certify two observations of one accepted state") {
        withChangedTrace { directory ->
            val original = rows(directory)
            writeRows(directory, listOf(original.first(), original.first()) + original.drop(1))
        }
    }
    test("a well-formed substituted state digest is rejected against the real replayed state") {
        withChangedTrace { directory ->
            val original = rows(directory)
            (original[0].stateSha256 != original[1].stateSha256) shouldBe true
            writeRows(directory, listOf(original[0].copy(stateSha256 = original[1].stateSha256)) + original.drop(1))
        }
    }
    test("altered checkpoint values fail semantic replay despite intact action state and event hashes") {
        withChangedTrace { directory ->
            val original = rows(directory)
            val first = original.first()
            writeRows(directory, listOf(first.copy(checkpoint = first.checkpoint.copy(
                totalGenericEquivalentMana = first.checkpoint.totalGenericEquivalentMana + 1))) + original.drop(1))
        }
    }
    test("a valid later same-turn checkpoint cannot replace the first-per-turn compatibility row") {
        withChangedTrace { directory ->
            val original = rows(directory)
            val first = original.first().checkpoint
            val later = original.first { it.checkpoint.ownTurn == first.ownTurn && it.checkpoint != first }.checkpoint
            val legacy = codec.decodeFromString(ListSerializer(IndustrialWasteV2CheckpointMana.serializer()),
                Files.readString(directory.resolve("checkpoints.json")))
            Files.writeString(directory.resolve("checkpoints.json"), codec.encodeToString(
                ListSerializer(IndustrialWasteV2CheckpointMana.serializer()), listOf(later) + legacy.drop(1)))
        }
    }
    test("a real terminal has no quiet priority even when the engine retains its prior main-step fields") {
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { registerCards(it.cards); registerCards(it.basicLands) }
            registerCards(PredefinedTokens.allTokens)
            initMirrorMatch(Deck.of("Forest" to 60), seed = 9_250_925_111L)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val measured = driver.player1
        driver.state.isIndustrialWasteV2QuietCheckpoint(measured) shouldBe true
        val before = driver.state
        driver.submit(Concede(driver.player2)).error shouldBe null
        driver.state.gameOver shouldBe true
        driver.state.priorityPlayerId shouldBe before.priorityPlayerId
        driver.state.step shouldBe before.step
        driver.state.isIndustrialWasteV2QuietCheckpoint(measured) shouldBe false
    }
    test("the final real four-thousandth action preserves its eligible quiet checkpoint before the cap stops execution") {
        // An excluded semantic setup uses only existing frozen-list identities. It runs the
        // unchanged public policy and real processor, not a counter override or injected pilot.
        // High starting life and a real initial mana pool keep this boundary test separate from
        // lethal conversion and the pending complete explicit-resource feasibility capability.
        val deck = Deck(List(55) { "Forest" } + listOf("Ashnod's Altar", "Myr Retriever",
            "Myr Retriever", "Pactdoll Terror", "Ichor Wellspring"))
        val seen = mutableMapOf<String, Int>()
        val copyLabels = deck.cards.map { "$it#${seen.merge(it, 1, Int::plus)}" }
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { registerCards(it.cards); registerCards(it.basicLands) }
            registerCards(PredefinedTokens.allTokens)
            initGame(deck, Deck.of("Forest" to 60), skipMulligans = true, startingPlayer = 0,
                seed = 9_250_925_112L, libraryOrdering1 = LibraryOrderingPlan(
                    "IW_V2_R1_SYNTHETIC_FINAL_QUIET_CAP_ONLY", 20_004, List(4) { copyLabels }))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val measured = driver.player1
        val ordering = driver.state.getEntity(measured)!!.get<LibraryOrderingComponent>()!!
        val byLabel = ordering.originalCopies.entries.associate { it.value to it.key }
        fun place(label: String, zone: Zone) {
            val id = byLabel.getValue(label)
            val from = driver.state.zones.keys.single { id in driver.state.getZone(it) }
            var moved = driver.state.moveToZone(id, from, ZoneKey(measured, zone))
            if (zone == Zone.BATTLEFIELD) moved = moved.updateEntity(id) { it.with(ControllerComponent(measured)) }
            driver.replaceState(moved)
        }
        // Keep every original entity and copy label. The initial semantic setup moves physical
        // copies before the runner starts; no live action is rewritten or inserted afterward.
        driver.state.getHand(measured).toList().forEach { card ->
            driver.replaceState(driver.state.moveToZone(card, ZoneKey(measured, Zone.HAND), ZoneKey(measured, Zone.LIBRARY)))
        }
        place("Ashnod's Altar#1", Zone.BATTLEFIELD)
        place("Myr Retriever#1", Zone.BATTLEFIELD)
        place("Pactdoll Terror#1", Zone.BATTLEFIELD)
        place("Myr Retriever#2", Zone.GRAVEYARD)
        place("Ichor Wellspring#1", Zone.GRAVEYARD)
        driver.giveColorlessMana(measured, 10_000)
        driver.setLifeTotal(driver.player2, 10_000)
        val observations = mutableListOf<Pair<Int, IndustrialWasteV2CheckpointMana>>()
        var lastObserved: GameState? = null
        val result = IndustrialWasteV2FullHorizonRunner(driver, measured, driver.player2,
            checkpointObservation = { checkpoint, index, state ->
                observations += index to checkpoint
                lastObserved = state
            }).run()
        result.status.status shouldBe IndustrialWasteV2StopStatus.ACTION_CAP
        result.status.submittedActions shouldBe 4_000
        result.status.acceptedActions shouldBe 4_000
        result.actions.size shouldBe 4_000
        result.status.engineGameOver shouldBe false
        result.status.ownTurnsStarted shouldBe 1
        result.status.diagnostic shouldBe null
        driver.state.isIndustrialWasteV2QuietCheckpoint(measured) shouldBe true
        observations.first().first shouldBe 0
        observations.last().first shouldBe 4_000
        observations.map { it.first }.let { it == it.distinct().sorted() } shouldBe true
        lastObserved shouldBe driver.state
        observations.last().second.unresolved shouldBe false
        // Later same-turn observations are mandatory, but the old compatibility view stays
        // exactly the first quiet observation. It cannot replace this final observation stream.
        result.checkpoints shouldBe listOf(observations.first().second)
    }
})
