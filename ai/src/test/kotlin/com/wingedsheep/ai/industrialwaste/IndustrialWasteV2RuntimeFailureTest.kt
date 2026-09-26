package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.state.GameState
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Deliberate excluded technical failures; these are neither corpus allocations nor deck losses. */
class IndustrialWasteV2RuntimeFailureTest : FunSpec({
    val codec = IndustrialWasteV2AllocationTrace.CODEC
    fun registry(driver: GameTestDriver) {
        MtgSetCatalog.all.forEach { set -> driver.registerCards(set.cards); driver.registerCards(set.basicLands) }
        driver.registerCards(PredefinedTokens.allTokens)
    }
    fun fixture() = GameTestDriver().apply {
        registry(this)
        initGame(Deck.of("Forest" to 60), Deck.of("Forest" to 60), skipMulligans = true,
            startingPlayer = 0, seed = 9_250_925_112L,
            libraryOrdering1 = LibraryOrderingPlan("IW_V2_R1_SYNTHETIC_FAILURE_FINALIZATION_ONLY", 27_001,
                List(4) { (1..60).map { "Forest#$it" } }))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("a real selection failure retains accepted actions and typed invalid final artifacts without another action") {
        val directory = Files.createTempDirectory("iw-v2-runtime-failure").resolve("allocation")
        IndustrialWasteV2AllocationTrace(directory).use { trace ->
            trace.write("input.json", "{\"namespace\":\"IW_V2_R1_SYNTHETIC_FAILURE_FINALIZATION_ONLY\",\"official\":false}")
            trace.write("fixture-declaration.json", "{\"beforeFixtureInitialization\":true,\"setup\":\"Add a Mountain to the passive hand to trigger the real frozen-policy precondition\"}")
            val driver = fixture()
            driver.putCardInHand(driver.player2, "Mountain")
            val initial = driver.state
            trace.write("initial-state.json", codec.encodeToString(GameState.serializer(), initial))
            trace.write("initial-events.json", codec.encodeToString(ListSerializer(GameEvent.serializer()), driver.events.toList()))
            val result = IndustrialWasteV2FullHorizonRunner(driver, driver.player1, driver.player2,
                beforeSubmission = trace::before,
                afterSubmission = { trace.after(it.events, it.newState, it.error) },
                paymentIntentRecord = trace::payment, checkpointObservation = trace::checkpoint).run()
            result.status.status shouldBe IndustrialWasteV2StopStatus.EXCEPTION
            result.status.diagnostic!!.startsWith("Action selection:") shouldBe true
            (result.status.acceptedActions > 0) shouldBe true
            result.status.submittedActions shouldBe result.status.acceptedActions
            result.actions.size shouldBe result.status.submittedActions
            result.eventMetrics.acceptedTransitions shouldBe result.status.acceptedActions
            result.status.engineGameOver shouldBe false
            result.status.engineWinnerId shouldBe null
            result.finalState shouldBe driver.state
            trace.stopped(result)
            codec.decodeFromString(IndustrialWasteV2ExecutionStatus.serializer(),
                Files.readString(directory.resolve("execution-status.json"))) shouldBe result.status
            codec.decodeFromString(ListSerializer(GameAction.serializer()),
                Files.readString(directory.resolve("actions.json"))) shouldBe result.actions
            codec.decodeFromString(GameState.serializer(),
                Files.readString(directory.resolve("final-state.json"))) shouldBe result.finalState
            Files.readString(directory.resolve("failure.txt")) shouldBe result.status.diagnostic
            Files.readAllLines(directory.resolve("transitions.jsonl")).size shouldBe result.actions.size * 2
            Files.exists(directory.resolve("replay.json")) shouldBe false
            val replay = GameTestDriver().apply { registry(this); replaceState(initial) }
            result.actions.forEach { replay.submit(it).error shouldBe null }
            replay.state shouldBe result.finalState
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.isDirectory(it.resolve("industrial-waste/v2")) }
            val process = ProcessBuilder("python3", "-c",
                "import importlib.util,json,pathlib; p=pathlib.Path(r'$directory'); " +
                    "s=importlib.util.spec_from_file_location('projection','industrial-waste/v2/r1_metric_projection.py'); " +
                    "m=importlib.util.module_from_spec(s); s.loader.exec_module(m); " +
                    "r=m.project_metrics(*[json.loads((p/f).read_text()) for f in ['execution-status.json','event-metrics.json','checkpoints.json']], " +
                    "[json.loads(row) for row in (p/'quiet-checkpoint-observations.jsonl').read_text().splitlines()]); " +
                    "assert r['validity']=='INVALID'; assert all(r[k] is None for k in m.METRICS)")
                .directory(root.toFile()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }
        }
    }

    test("a real checkpoint identity failure stops before submitting or fabricating any action") {
        val driver = fixture()
        driver.putCardInHand(driver.player1, "Forest") // Deliberately lacks an original-copy identity.
        val before = driver.state
        val result = IndustrialWasteV2FullHorizonRunner(driver, driver.player1, driver.player2).run()
        result.status.status shouldBe IndustrialWasteV2StopStatus.EXCEPTION
        result.status.diagnostic!!.contains("Checkpoint card lacks frozen original-copy identity") shouldBe true
        result.status.submittedActions shouldBe 0
        result.status.acceptedActions shouldBe 0
        result.eventMetrics.acceptedTransitions shouldBe 0
        result.actions shouldBe emptyList()
        result.checkpoints shouldBe emptyList()
        result.finalState shouldBe before
        driver.state shouldBe before
    }

    test("checkpoint evidence writer failure propagates before any action") {
        val driver = fixture()
        val before = driver.state
        val failure = shouldThrow<IOException> {
            IndustrialWasteV2FullHorizonRunner(driver, driver.player1, driver.player2,
                checkpointObservation = { _, _, _ -> throw IOException("excluded checkpoint writer failure") }).run()
        }
        failure.message shouldBe "excluded checkpoint writer failure"
        driver.state shouldBe before
    }

    test("payment intent writer failure propagates before its planned funding action") {
        val driver = fixture()
        driver.replaceState(driver.state.copy(zones = driver.state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
        driver.putPermanentOnBattlefield(driver.player1, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(driver.player1, "Myr Retriever")
        driver.putPermanentOnBattlefield(driver.player1, "Chromatic Star")
        val before = driver.state
        var writes = 0
        val failure = shouldThrow<IOException> {
            IndustrialWasteV2FullHorizonRunner(driver, driver.player1, driver.player2,
                paymentIntentRecord = { writes++; throw IOException("excluded payment writer failure") }).run()
        }
        failure.message shouldBe "excluded payment writer failure"
        writes shouldBe 1
        driver.state shouldBe before
    }
})
