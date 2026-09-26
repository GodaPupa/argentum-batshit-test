package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class IndustrialWasteV2AllocationRunnerTest : FunSpec({
    val codec = IndustrialWasteV2AllocationTrace.CODEC
    val cards = List(60) { "Forest" }
    val labels = (1..60).map { "Forest#$it" }
    val input = IndustrialWasteV2AllocationInput(
        "SYNTHETIC_TRACE_1", cards, "IW_V2_R1_SYNTHETIC_ALLOCATION_TRACE_ONLY", 20_001,
        List(4) { labels }, 0, 9_250_925_109L,
    )

    test("complete excluded allocation preserves exact actions events checkpoints and replay") {
        val directory = Files.createTempDirectory("iw-v2-allocation-trace").resolve("allocation")
        val result = IndustrialWasteV2AllocationRunner.runExcluded(input, directory)
        result.status.status shouldBe IndustrialWasteV2StopStatus.TURN_CAP
        result.checkpoints.map { it.ownTurn } shouldBe (1..8).toList()
        val actions = codec.decodeFromString(ListSerializer(GameAction.serializer()),
            Files.readString(directory.resolve("actions.json")))
        actions shouldBe result.actions
        val transitions = Files.readAllLines(directory.resolve("transitions.jsonl"))
            .map { codec.parseToJsonElement(it).jsonObject }
        transitions.size shouldBe result.status.submittedActions * 2
        val observations = Files.readAllLines(directory.resolve("quiet-checkpoint-observations.jsonl"))
            .map { codec.parseToJsonElement(it).jsonObject }
        // Land-first fixtures revisit quiet priority within one own turn. Keeping only the
        // compatibility view's eight rows would lose those real, already-classified states.
        (observations.size > result.checkpoints.size) shouldBe true
        val observationIndices = observations.map { it.getValue("acceptedActions").jsonPrimitive.int }
        observationIndices shouldBe observationIndices.distinct().sorted()
        observations.forEach { observation ->
            val accepted = observation.getValue("acceptedActions").jsonPrimitive.int
            val expectedState = if (accepted == 0) IndustrialWasteV2AllocationTrace.sha256(
                Files.readString(directory.resolve("initial-state.json")))
            else transitions[accepted * 2 - 1].getValue("stateSha256").jsonPrimitive.content
            observation.getValue("stateSha256").jsonPrimitive.content shouldBe expectedState
        }

        val replay = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
            initGame(Deck(cards), Deck.of("Forest" to 60), skipMulligans = false,
                startingLife = 20, startingPlayer = 0, seed = input.initializerSeed,
                libraryOrdering1 = LibraryOrderingPlan(input.namespace, input.row, input.openingOrders))
        }
        codec.encodeToString(GameState.serializer(), replay.state) shouldBe Files.readString(directory.resolve("initial-state.json"))
        actions.forEachIndexed { index, action ->
            val returned = replay.submit(action)
            returned.error shouldBe null
            val saved = transitions[index * 2 + 1]
            saved.getValue("type").jsonPrimitive.content shouldBe "ACTION_RETURNED"
            saved.getValue("stateSha256").jsonPrimitive.content shouldBe IndustrialWasteV2AllocationTrace.sha256(
                codec.encodeToString(GameState.serializer(), returned.newState))
            saved.getValue("events") shouldBe codec.parseToJsonElement(
                codec.encodeToString(ListSerializer(GameEvent.serializer()), returned.events))
        }
        codec.encodeToString(GameState.serializer(), replay.state) shouldBe Files.readString(directory.resolve("final-state.json"))
        // Deliberately corrupt only excluded fixture output; the production writer never rewrites.
        val originalTrace = Files.readString(directory.resolve("transitions.jsonl"))
        listOf(0 to "index", 1 to "index", 1 to "error").forEach { (line, key) ->
            val changed = transitions.toMutableList()
            changed[line] = JsonObject(changed[line] + (key to
                if (key == "index") JsonPrimitive(999) else JsonPrimitive("corrupted recorded error")))
            Files.writeString(directory.resolve("transitions.jsonl"), changed.joinToString("\n", postfix = "\n"))
            shouldThrow<IllegalStateException> { IndustrialWasteV2AllocationRunner.verifyReplay(directory) }
            Files.writeString(directory.resolve("transitions.jsonl"), originalTrace)
        }

        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("industrial-waste/v2")) }
        val process = ProcessBuilder("python3", "-c",
            "import importlib.util,json,pathlib; p=pathlib.Path(r'$directory'); " +
                "s=importlib.util.spec_from_file_location('projection','industrial-waste/v2/r1_metric_projection.py'); " +
                "m=importlib.util.module_from_spec(s); s.loader.exec_module(m); " +
                "r=m.project_metrics(*[json.loads((p/f).read_text()) for f in ['execution-status.json','event-metrics.json','checkpoints.json']], " +
                "[json.loads(row) for row in (p/'quiet-checkpoint-observations.jsonl').read_text().splitlines()]); " +
                "assert r['validity']=='VALID'; assert not any(r[k] for k in m.METRICS)")
            .directory(root.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
    }

    test("allocation directory is create-once and a second invocation cannot replace evidence") {
        val directory = Files.createTempDirectory("iw-v2-allocation-duplicate").resolve("claimed")
        Files.createDirectory(directory)
        Files.writeString(directory.resolve("sentinel.txt"), "original evidence")
        shouldThrow<java.nio.file.FileAlreadyExistsException> {
            IndustrialWasteV2AllocationRunner.runExcluded(input, directory)
        }
        Files.list(directory).use { it.count() } shouldBe 1
        Files.readString(directory.resolve("sentinel.txt")) shouldBe "original evidence"
    }

    test("frozen Compact Loop spell-bearing fixture composes the public pilot typed metrics and snapshot replay") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("industrial-waste/v2")) }
        val deck = Files.readAllLines(root.resolve("industrial-waste/v2/candidates/compact-loop.dck"))
            .dropWhile { it != "[main]" }.drop(1).takeWhile { !it.startsWith("[") }
            .filter { it.isNotBlank() }.flatMap { line ->
                val (count, name) = line.split(' ', limit = 2)
                List(count.toInt()) { name }
            }
        val copies = mutableMapOf<String, Int>()
        val allLabels = deck.map { name -> "$name#${copies.merge(name, 1, Int::plus)}" }
        val prefix = listOf("Urza's Tower#1", "Urza's Mine#1", "Urza's Power Plant#1", "Swamp#1",
            "Chromatic Star#1", "Myr Retriever#1", "Ashnod's Altar#1", "Myr Retriever#2",
            "Golem Foundry#1", "Blood Fountain#1", "Ichor Wellspring#1")
        val ordering = prefix + allLabels.filter { it !in prefix }
        val directory = Files.createTempDirectory("iw-v2-spell-trace").resolve("allocation")
        val spellInput = input.copy(allocationId = "SYNTHETIC_COMPACT_SPELL_TRACE", deckCards = deck,
            namespace = "IW_V2_R1_SYNTHETIC_COMPACT_SPELL_TRACE_ONLY", row = 20_002,
            openingOrders = List(4) { ordering }, initializerSeed = 9_250_925_110L)
        val result = IndustrialWasteV2AllocationRunner.runExcluded(spellInput, directory,
            preserveReplayDiagnostics = true)
        result.status.diagnostic shouldBe null
        (result.status.status in setOf(IndustrialWasteV2StopStatus.TURN_CAP,
            IndustrialWasteV2StopStatus.REAL_TERMINAL, IndustrialWasteV2StopStatus.ACTION_CAP)) shouldBe true
        result.actions.any { it is CastSpell } shouldBe true
        result.eventMetrics.acceptedTransitions shouldBe result.status.acceptedActions
        result.checkpoints.all { it.activatedAbilityCoverageComplete && !it.unresolved } shouldBe true
        val process = ProcessBuilder("python3", "-c",
            "import importlib.util,json,pathlib; p=pathlib.Path(r'$directory'); " +
                "s=importlib.util.spec_from_file_location('projection','industrial-waste/v2/r1_metric_projection.py'); " +
                "m=importlib.util.module_from_spec(s); s.loader.exec_module(m); " +
                "r=m.project_metrics(*[json.loads((p/f).read_text()) for f in ['execution-status.json','event-metrics.json','checkpoints.json']], " +
                "[json.loads(row) for row in (p/'quiet-checkpoint-observations.jsonl').read_text().splitlines()]); " +
                "assert r['validity']=='VALID'").directory(root.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
    }

    test("official namespace or official allocation id is rejected before any filesystem mutation") {
        val directory = Files.createTempDirectory("iw-v2-allocation-official").resolve("not-created")
        listOf(input.copy(namespace = "IW_V2_R1_ORDERINGS_2026_09_25"),
            input.copy(allocationId = "IW_V2_R1_0001")).forEach { forbidden ->
            shouldThrow<IllegalArgumentException> {
                IndustrialWasteV2AllocationRunner.runExcluded(forbidden, directory)
            }
            Files.exists(directory) shouldBe false
        }
    }
})
