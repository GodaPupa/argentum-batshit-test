package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Isolated card-slot research only. The v0.7 control deck and CURRENT remain frozen. */
class ManualV08WanChallengerTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { set ->
            cardRegistry.register(set.cards)
            cardRegistry.register(set.basicLands)
        }
        for (x in listOf(0, 2, 6)) {
            test("Animar with " + x + " pre-cast counters lets Wan Shi Tong X=" + x +
                " pay only two blue, enter with X counters, and draw floor(X/2)") {
                val builder = scenario().withPlayers("Manual", "Opponent")
                    .withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardInHand(1, "Wan Shi Tong, Librarian")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(15) { builder.withCardInLibrary(1, "Forest") }
                repeat(15) { builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()
                val animar = requireNotNull(game.findPermanent("Animar, Soul of Elements"))
                game.state = game.state.updateEntity(animar) {
                    it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, x))
                }
                val wan = game.state.getHand(game.player1Id).single {
                    game.state.getEntity(it)
                        ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                        ?.name == "Wan Shi Tong, Librarian"
                }
                val offered = LegalActionEnumerator.create(cardRegistry).enumerate(
                    game.state, game.player1Id
                ).single { (it.action as? CastSpell)?.cardId == wan }
                check(offered.affordable && (offered.maxAffordableX ?: -1) >= x) {
                    "Expected affordable selected X " + x + ", got " + offered
                }
                val before = game.state.getHand(game.player1Id).size
                val cast = game.castXSpell(1, "Wan Shi Tong, Librarian", xValue = x)
                cast.error shouldBe null
                val spent = cast.events.filterIsInstance<ManaSpentEvent>()
                spent.sumOf { it.total } shouldBe 2
                spent.sumOf { it.blue } shouldBe 2
                cast.events.filterIsInstance<SpellCastEvent>().single().manaValue shouldBe x + 2
                game.resolveStack().forEach { it.error shouldBe null }

                fun counters(id: com.wingedsheep.sdk.model.EntityId): Int =
                    game.state.getEntity(id)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

                counters(animar) shouldBe x + 1
                val permanent = requireNotNull(game.findPermanent("Wan Shi Tong, Librarian"))
                counters(permanent) shouldBe x
                game.state.getHand(game.player1Id).size shouldBe before - 1 + x / 2
                println("WAN_V08_ANIMAR_X_RESULT x=" + x + " manaSpent=2 blue=2 " +
                    "WanCounters=" + counters(permanent) + " AnimarCounters=" + counters(animar) +
                    " draw=" + (x / 2))
            }
        }
        test("Wan v0.8 challenger differs from frozen v0.7 by exactly one declared swap") {
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first {
                Files.exists(it.resolve("settings.gradle.kts"))
            }
            fun cards(path: Path): List<String> = Files.readAllLines(path).flatMap { line ->
                val match = Regex("^(\\d+) (.+)$").matchEntire(line)
                if (match == null) emptyList()
                else List(match.groupValues[1].toInt()) { match.groupValues[2] }
            }
            val frozen = cards(root.resolve("experiments/manual-transmission/control/v0.7.decklist.txt"))
            val candidate = cards(root.resolve("gameplay/manual-v08-wan/CHALLENGER.decklist.txt"))
            frozen.size shouldBe 100
            candidate.size shouldBe 100
            val expected = frozen.toMutableList()
            check(expected.remove("Lifecrafter’s Bestiary"))
            expected.add("Wan Shi Tong, Librarian")
            candidate.sorted() shouldBe expected.sorted()
            AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
        }
    }
}
