package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Scenario tests for Up the Beanstalk. */
class UpTheBeanstalkScenarioTest : ScenarioTestBase() {

    private fun stunCounters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.STUN) ?: 0

    private fun isTapped(game: TestGame, id: EntityId): Boolean =
        game.state.getEntity(id)?.get<TappedComponent>() != null

    private fun plusOneCounters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        for (announcedX in listOf(0, 3)) {
            test("Beanstalk uses Ballista stack mana value for announced X $announcedX and off stack X remains zero") {
                val game = scenario().withPlayers("Manual", "Opponent")
                    .withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardOnBattlefield(1, "Up the Beanstalk")
                    .withCardInHand(1, "Walking Ballista")
                    .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Forest")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val ballista = game.state.getHand(game.player1Id).single()
                game.state.manaValueOf(ballista) shouldBe 0
                val animar = game.findPermanent("Animar, Soul of Elements")!!
                game.state = game.state.updateEntity(animar) {
                    it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 6))
                }
                val cast = game.castXSpell(1, "Walking Ballista", xValue = announcedX)
                cast.error shouldBe null
                cast.events.filterIsInstance<SpellCastEvent>().single().manaValue shouldBe announcedX * 2
                game.state.manaValueOf(ballista) shouldBe announcedX * 2
                repeat(8) {
                    val choice = game.state.pendingDecision as? ChooseOptionDecision
                    if (choice != null) game.execute(SubmitDecision(choice.playerId, OptionChosenResponse(choice.id, 0))).error shouldBe null
                    game.resolveStack()
                }
                game.state.stack.size shouldBe 0
                game.state.pendingDecision shouldBe null
                game.state.getHand(game.player1Id).size shouldBe if (announcedX == 3) 1 else 0
                game.state.manaValueOf(ballista) shouldBe 0
                val destination = if (announcedX == 3) Zone.BATTLEFIELD else Zone.GRAVEYARD
                game.state.getZone(ZoneKey(game.player1Id, destination)).contains(ballista) shouldBe true
            }
        }

        context("Up the Beanstalk — draws on enter and on expensive spells") {
            test("entering draws one card, then a mana value 6 spell draws another") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Up the Beanstalk")
                    .withCardInHand(1, "Craw Wurm")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                game.castSpell(1, "Up the Beanstalk").error shouldBe null
                game.resolveStack()

                withClue("Beanstalk left hand (-1) and its enters trigger drew a card (+1)") {
                    game.handSize(1) shouldBe handBefore
                }

                game.castSpell(1, "Craw Wurm").error shouldBe null
                game.resolveStack()

                withClue("Craw Wurm is mana value 6, so casting it drew another card") {
                    // Craw Wurm left hand (-1), the cast trigger drew (+1).
                    game.handSize(1) shouldBe handBefore
                }
            }
        }
    }
}
