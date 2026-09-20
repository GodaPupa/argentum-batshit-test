package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaAgents
import com.wingedsheep.ai.arena.TableGameRunner
import com.wingedsheep.ai.arena.TableSetup
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class IndustrialWasteGoldfishTelemetryTest : FunSpec({
    test("one real-mulligan goldfish produces internally valid metrics") {
        val repository = findRepositoryRoot()
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val observer = IndustrialWasteGoldfishObserver(registry)
        val outcome = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = List(2) { ArenaAgents.resolve("v0") },
            decks = listOf(
                parseMain(repository.resolve("industrial-waste/control/industrial-waste-v1.0-submitted.dck")),
                Deck.of("Forest" to 60),
            ),
            seed = 1L,
            groupId = 1,
            rotation = 0,
            maxTurns = 2,
            maxActions = 500,
            trainingObserver = observer,
            skipMulligans = false,
        )
        val metrics = observer.snapshot()

        outcome.exception shouldBe null
        outcome.illegalActions.keys.shouldBeEmpty()
        metrics.mulligans shouldBeGreaterThanOrEqual 0
        metrics.coloredManaFailureTurns shouldBeGreaterThanOrEqual 0
        metrics.nonInfinitePactdollLifeLoss shouldBeGreaterThanOrEqual 0
        metrics.combatDamageToOpponent shouldBeGreaterThanOrEqual 0
    }
})

private fun findRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun parseMain(path: Path): Deck {
    var inMain = false
    val cards = buildList {
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            when {
                line == "[main]" -> inMain = true
                line.startsWith("[") -> inMain = false
                inMain && line.isNotEmpty() -> {
                    val split = line.indexOf(' ')
                    require(split > 0) { "Malformed deck line in ${path}: ${line}" }
                    val count = line.substring(0, split).toInt()
                    val name = line.substring(split + 1)
                    repeat(count) { add(name) }
                }
            }
        }
    }
    return Deck(cards)
}
