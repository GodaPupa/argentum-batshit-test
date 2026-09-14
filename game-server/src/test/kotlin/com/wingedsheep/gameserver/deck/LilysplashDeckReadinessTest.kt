package com.wingedsheep.gameserver.deck

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import java.nio.file.Files
import java.nio.file.Path
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/** Deterministic structural gate for the preserved Lilysplash Mentor PDH control deck. */
class LilysplashDeckReadinessTest : FunSpec({

    data class SubmittedDeck(val commander: String, val library: List<String>)

    fun submittedDeck(): SubmittedDeck {
        val relative = Path.of("docs/experiments/lilysplash/submitted-v0.1.txt")
        val snapshot = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull { Files.isRegularFile(it) }
            ?: error("Could not locate $relative from the test working directory")
        val lines = Files.readAllLines(snapshot)
            .filter { it.isNotBlank() && !it.startsWith("#") }
        lines.first() shouldBe "Commander"
        val entry = Regex("""^(\d+)\s+(.+)$""")
        val commanderEntry = entry.matchEntire(lines[1])
            ?: error("Unparseable submitted commander line: ${lines[1]}")
        commanderEntry.groupValues[1].toInt() shouldBe 1
        val commander = commanderEntry.groupValues[2]
        lines[2] shouldBe "Deck"
        val library = lines.drop(3).flatMap { line ->
            val match = entry.matchEntire(line) ?: error("Unparseable submitted deck line: $line")
            List(match.groupValues[1].toInt()) { match.groupValues[2] }
        }
        return SubmittedDeck(commander, library)
    }

    fun registry(): CardRegistry = CardRegistry().apply {
        register(MtgSetCatalog.all.flatMap { it.cards + it.basicLands })
    }

    test("the submitted PDH snapshot shape and unresolved registry inventory are explicit") {
        val submitted = submittedDeck()
        val registry = registry()
        val commander = registry.getCard(submitted.commander) ?: error("Missing commander definition")

        submitted.library.size shouldBe 99
        val submittedCounts = (submitted.library + submitted.commander).groupingBy { it }.eachCount()
        submittedCounts.values.sum() shouldBe 100
        submittedCounts.filterValues { it > 1 } shouldBe mapOf("Forest" to 9, "Island" to 8)

        val unresolved = submitted.library.distinct().filter { registry.getCard(it) == null }.toSet()
        unresolved shouldBe setOf(
            "Capsize",
            "Hidden Strings",
            "Secret Door",
        )

        val commanderIdentity = commander.colorIdentity
        val offIdentity = submitted.library.distinct().mapNotNull(registry::getCard).filter { card ->
            card.colorIdentity.any { it !in commanderIdentity }
        }
        offIdentity.shouldBeEmpty()
    }
})
