package com.wingedsheep.gameserver.deck

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import java.nio.file.Files
import java.nio.file.Path
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

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

    test("the submitted PDH deck is singleton, inside Lilysplash's identity, and initializes its command zone") {
        val submitted = submittedDeck()
        val registry = registry()
        val commander = registry.getCard(submitted.commander) ?: error("Missing commander definition")

        submitted.library.size shouldBe 99
        submitted.library.forEach { name -> registry.getCard(name) shouldNotBe null }

        // The map overload proves the existing 100-card/singleton Commander shape without applying
        // legendary-only commander eligibility. PDH deliberately allows an uncommon creature such
        // as Lilysplash Mentor to be the commander, while DeckFormat.PAUPER_COMMANDER is not yet a
        // product format in this repository.
        val submittedCounts = (submitted.library + submitted.commander).groupingBy { it }.eachCount()
        DeckValidator(registry).validate(submittedCounts, DeckFormat.COMMANDER).valid shouldBe true

        val commanderIdentity = commander.colorIdentity
        val offIdentity = submitted.library.distinct().filter { name ->
            registry.getCard(name)!!.colorIdentity.any { it !in commanderIdentity }
        }
        offIdentity.shouldBeEmpty()

        val library = Deck(cards = submitted.library)
        val initialized = GameInitializer(registry).initializeGame(
            GameConfig(
                format = Format.Commander(),
                players = listOf(
                    PlayerConfig("P1", library, commanderCardName = submitted.commander),
                    PlayerConfig("P2", library, commanderCardName = submitted.commander),
                ),
                skipMulligans = true,
            ),
        )
        initialized.playerIds.forEach { player ->
            val commandZone = initialized.state.getZone(ZoneKey(player, Zone.COMMAND))
            commandZone.size shouldBe 1
            val commanderId = commandZone.single()
            initialized.state.getEntity(commanderId)!!.get<CardComponent>()!!.name shouldBe submitted.commander
            initialized.state.getEntity(commanderId)!!.get<CommanderComponent>() shouldNotBe null
            (commanderId in initialized.state.getZone(ZoneKey(player, Zone.LIBRARY))) shouldBe false
        }
    }
})
