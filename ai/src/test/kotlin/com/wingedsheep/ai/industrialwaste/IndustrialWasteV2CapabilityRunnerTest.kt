package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class IndustrialWasteV2CapabilityRunnerTest : FunSpec({
    val regressionSeed = 9_250_925_005L

    fun projectRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isDirectory(it.resolve("industrial-waste/v2/candidates")) }

    fun loadMain(relative: String): Deck {
        var section = ""
        val cards = mutableListOf<String>()
        for (line in Files.readAllLines(projectRoot().resolve(relative))) {
            if (line.startsWith("[")) {
                section = line.removeSurrounding("[", "]")
                continue
            }
            if (section != "main" || line.isBlank()) continue
            val row = Regex("([1-9][0-9]*) (.+)").matchEntire(line)
                ?: error("Malformed frozen deck row: $line")
            repeat(row.groupValues[1].toInt()) { cards += row.groupValues[2] }
        }
        cards.size shouldBe 60
        return Deck(cards)
    }

    test("all four frozen R1 lists complete one measured own turn through the composed real runner") {
        val frozen = listOf(
            "industrial-waste/control/industrial-waste-v1.0-submitted.dck",
            "industrial-waste/v2/candidates/compact-loop.dck",
            "industrial-waste/v2/candidates/recursive-eggs.dck",
            "industrial-waste/v2/candidates/lean-tron-hybrid.dck",
        )
        for (path in frozen) {
            val driver = GameTestDriver().apply {
                MtgSetCatalog.all.forEach { set ->
                    registerCards(set.cards)
                    registerCards(set.basicLands)
                }
                initGame(
                    loadMain(path),
                    Deck.of("Forest" to 60),
                    skipMulligans = true,
                    startingLife = 20,
                    startingPlayer = 0,
                    seed = regressionSeed,
                )
            }
            val status = IndustrialWasteV2CapabilityRunner(
                driver,
                driver.player1,
                driver.player2,
            ).runOneMeasuredTurn()

            status.status shouldBe IndustrialWasteV2StopStatus.RUNNING
            status.ownTurnsCompleted shouldBe 1
            (status.submittedActions > 0) shouldBe true
            status.submittedActions shouldBe status.acceptedActions
            status.engineGameOver shouldBe false
            status.engineWinnerId shouldBe null
            status.diagnostic shouldBe null
        }
    }

    test("composed one-turn capability runner replays identically for all four frozen lists") {
        val frozen = listOf(
            "industrial-waste/control/industrial-waste-v1.0-submitted.dck",
            "industrial-waste/v2/candidates/compact-loop.dck",
            "industrial-waste/v2/candidates/recursive-eggs.dck",
            "industrial-waste/v2/candidates/lean-tron-hybrid.dck",
        )
        val json = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
            encodeDefaults = true
        }

        fun execute(path: String): Triple<IndustrialWasteV2ExecutionStatus, String, List<String>> {
            val driver = GameTestDriver().apply {
                MtgSetCatalog.all.forEach { set ->
                    registerCards(set.cards)
                    registerCards(set.basicLands)
                }
                initGame(
                    loadMain(path),
                    Deck.of("Forest" to 60),
                    skipMulligans = true,
                    startingLife = 20,
                    startingPlayer = 0,
                    seed = regressionSeed,
                )
            }
            val status = IndustrialWasteV2CapabilityRunner(
                driver,
                driver.player1,
                driver.player2,
            ).runOneMeasuredTurn()
            return Triple(
                status,
                json.encodeToString(GameState.serializer(), driver.state),
                driver.events.map { it.toString() },
            )
        }

        for (path in frozen) {
            execute(path) shouldBe execute(path)
        }
    }

})
