package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.PlayerInitiativeComponent
import com.wingedsheep.engine.state.components.player.UndercityProgressComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.UndercityRoom
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteGate10ClbScenarioTest : ScenarioTestBase() {
    init {
        test("Avenging Hunter takes the initiative and enters Secret Entrance") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Avenging Hunter")
                .withLandsOnBattlefield(1, "Forest", 5)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Elvish Mystic")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Avenging Hunter")
            withClue("Avenging Hunter should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }

            // Resolve the creature, its ETB, the initiative's inherent venture trigger, and the
            // Secret Entrance room trigger. The basic-land search is the first player decision.
            game.resolveStack()

            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            search.minSelections shouldBe 0
            search.maxSelections shouldBe 1
            game.selectCards(search.options).error shouldBe null
            game.resolveStack()

            game.state.getEntity(game.player1Id)?.has<PlayerInitiativeComponent>() shouldBe true
            game.state.getEntity(game.player2Id)?.has<PlayerInitiativeComponent>() shouldBe false
            game.state.getEntity(game.player1Id)
                ?.get<UndercityProgressComponent>()?.room shouldBe UndercityRoom.SECRET_ENTRANCE
            game.isInHand(1, "Forest") shouldBe true
        }

        test("Throne reveals ten and requires a creature choice before applying its rider") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Avenging Hunter")
                .withLandsOnBattlefield(1, "Forest", 5)
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.state = game.state
                .updateEntity(game.player1Id) {
                    it.with(PlayerInitiativeComponent)
                        .with(UndercityProgressComponent(UndercityRoom.CATACOMBS))
                }

            game.castSpell(1, "Avenging Hunter").error shouldBe null
            val resolution = game.resolveStack()

            val revealed = resolution
                .flatMap { it.events }
                .filterIsInstance<CardsRevealedEvent>()
                .firstOrNull { it.cardNames.size == 10 }
            withClue("Throne must publicly reveal the full top ten") {
                revealed?.cardNames?.size shouldBe 10
            }

            val choice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            choice.minSelections shouldBe 1
            choice.maxSelections shouldBe 1
            choice.options.size shouldBe 2

            val chosen = choice.options.first()
            val chosenName = game.state.getEntity(chosen)
                ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name
            chosenName shouldBe listOf("Elvish Mystic", "Fyndhorn Elves").first { it == chosenName }

            game.selectCards(listOf(chosen)).error shouldBe null
            game.resolveStack()

            val creature = game.findPermanent(chosenName!!)
            creature shouldBe chosen
            game.state.getEntity(creature!!)
                ?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            game.state.projectedState.hasKeyword(creature, Keyword.HEXPROOF) shouldBe true
            revealed!!.cardNames shouldContain "Elvish Mystic"
            revealed.cardNames shouldContain "Fyndhorn Elves"
        }

        test("initiative holder ventures at upkeep, chooses Forge, and resolves its room ability") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardOnBattlefield(1, "Elvish Mystic", summoningSickness = false)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.state = game.state.updateEntity(game.player1Id) {
                it.with(PlayerInitiativeComponent)
                    .with(UndercityProgressComponent(UndercityRoom.SECRET_ENTRANCE))
            }

            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.resolveStack()

            val route = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
            route.options shouldBe listOf("Forge", "Lost Well")
            game.submitDecision(
                OptionChosenResponse(route.id, route.options.indexOf("Forge"))
            ).error shouldBe null
            game.resolveStack()

            val mystic = game.findPermanent("Elvish Mystic")!!
            game.selectTargets(listOf(mystic)).error shouldBe null
            game.resolveStack()

            game.state.getEntity(game.player1Id)
                ?.get<UndercityProgressComponent>()?.room shouldBe UndercityRoom.FORGE
            game.state.getEntity(mystic)
                ?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
        }

        test("combat damage to the initiative holder transfers initiative and ventures for the new holder") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardOnBattlefield(1, "Elvish Mystic", summoningSickness = false)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.state = game.state.updateEntity(game.player2Id) {
                it.with(PlayerInitiativeComponent)
            }

            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.declareAttackers(mapOf("Elvish Mystic" to 2)).error shouldBe null

            // passUntilPhase intentionally auto-resolves any decisions encountered while moving
            // through combat. The initiative transfer resolves in the combat-damage step, then its
            // inherent venture enters Secret Entrance; the room's optional basic-land search is
            // therefore also auto-answered before END_COMBAT is reached. Assert the resulting
            // rules state rather than looking for a decision that this helper already consumed.
            game.passUntilPhase(Phase.COMBAT, Step.END_COMBAT)

            game.state.getEntity(game.player1Id)?.has<PlayerInitiativeComponent>() shouldBe true
            game.state.getEntity(game.player2Id)?.has<PlayerInitiativeComponent>() shouldBe false
            game.state.getEntity(game.player1Id)
                ?.get<UndercityProgressComponent>()?.room shouldBe UndercityRoom.SECRET_ENTRANCE
        }
    }
}
