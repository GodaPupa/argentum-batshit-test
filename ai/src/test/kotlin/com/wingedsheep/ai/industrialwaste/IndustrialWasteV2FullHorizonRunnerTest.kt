package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class IndustrialWasteV2FullHorizonRunnerTest : FunSpec({

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

    fun labels(deck: Deck): List<String> {
        val seen = mutableMapOf<String, Int>()
        return deck.cards.map { name ->
            val ordinal = (seen[name] ?: 0) + 1
            seen[name] = ordinal
            "$name#$ordinal"
        }
    }

    fun execute(path: String, startingPlayer: Int, row: Int):
        Triple<IndustrialWasteV2FullHorizonRunner.Result, String, List<String>> {
        val deck = loadMain(path)
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set ->
                registerCards(set.cards)
                registerCards(set.basicLands)
            }
        }
        val original = labels(deck)
        val byLabel = original.associateBy { it }
        val landFirst = original.sortedWith(
            compareByDescending<String> { label ->
                val name = label.substringBeforeLast('#')
                driver.cardRegistry.requireCard(name).typeLine.isLand
            }.thenBy { original.indexOf(byLabel.getValue(it)) }
        )
        landFirst.take(15).all { label ->
            driver.cardRegistry.requireCard(label.substringBeforeLast('#')).typeLine.isLand
        } shouldBe true

        val plan = LibraryOrderingPlan(
            namespace = "IW_V2_R1_SYNTHETIC_FULL_HORIZON_ONLY",
            row = row,
            openingOrders = List(4) { landFirst },
        )
        driver.initGame(
            deck1 = deck,
            deck2 = Deck.of("Forest" to 60),
            skipMulligans = false,
            startingLife = 20,
            startingPlayer = startingPlayer,
            seed = 9_250_925_006L + row,
            libraryOrdering1 = plan,
        )

        val result = IndustrialWasteV2FullHorizonRunner(
            driver = driver,
            measuredPlayer = driver.player1,
            passivePlayer = driver.player2,
        ).run()

        result.status.status shouldBe IndustrialWasteV2StopStatus.TURN_CAP
        result.status.ownTurnsCompleted shouldBe 8
        result.status.submittedActions shouldBe result.status.acceptedActions
        result.status.diagnostic shouldBe null
        result.checkpoints.map { it.ownTurn } shouldBe (1..8).toList()
        result.checkpoints.all { !it.unresolved } shouldBe true
        result.eventMetrics.acceptedTransitions shouldBe result.status.acceptedActions
        result.eventMetrics.actualLethalTurn shouldBe null
        result.eventMetrics.certifiedFutureConversionTurn shouldBe null

        return Triple(
            result,
            json.encodeToString(GameState.serializer(), driver.state),
            driver.events.map { it.toString() },
        )
    }

    frozen.forEachIndexed { deckIndex, path ->
        listOf(0, 1).forEach { startingPlayer ->
            val seat = if (startingPlayer == 0) "play" else "draw"
            test("full-horizon capability reaches exact own-turn-eight cap for $path on the $seat schedule") {
                execute(path, startingPlayer, 10_000 + deckIndex * 10 + startingPlayer)
            }
        }
    }

    test("full-horizon capability is deterministic for an identical excluded ordering fixture") {
        val first = execute(frozen.first(), 0, 10_100)
        val replay = execute(frozen.first(), 0, 10_100)
        replay.first shouldBe first.first
        replay.second shouldBe first.second
        replay.third shouldBe first.third
    }

    test("runner refuses the frozen official R1 namespace before submitting an action") {
        val deck = loadMain(frozen.first())
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set ->
                registerCards(set.cards)
                registerCards(set.basicLands)
            }
        }
        val original = labels(deck)
        val plan = LibraryOrderingPlan(
            namespace = "IW_V2_R1_ORDERINGS_2026_09_25",
            row = 1,
            openingOrders = List(4) { original },
        )
        driver.initGame(
            deck1 = deck,
            deck2 = Deck.of("Forest" to 60),
            skipMulligans = true,
            startingPlayer = 0,
            seed = 9_250_925_006L,
            libraryOrdering1 = plan,
        )
        val before = driver.state
        val failure = runCatching {
            IndustrialWasteV2FullHorizonRunner(driver, driver.player1, driver.player2).run()
        }.exceptionOrNull()
        (failure is IllegalStateException) shouldBe true
        driver.state shouldBe before
    }
})
