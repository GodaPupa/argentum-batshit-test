package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the Duskmourn: House of Horror Overlord cycle (Impending creatures whose
 * "enters or attacks" ability fires on entry):
 *
 *  - Overlord of the Boilerbilges (DSK #146) — {4}{R}{R} 5/5, Impending 4—{2}{R}{R};
 *    "Whenever this permanent enters or attacks, it deals 4 damage to any target."
 *  - Overlord of the Floodpits (DSK #68) — {3}{U}{U} 5/3 Flying, Impending 4—{1}{U}{U};
 *    "Whenever this permanent enters or attacks, draw two cards, then discard a card."
 *  - Overlord of the Hauntwoods (DSK #194) — {3}{G}{G} 6/5, Impending 4—{1}{G}{G};
 *    "Whenever this permanent enters or attacks, create a tapped colorless land token named
 *    Everywhere that is every basic land type."
 *
 * Impending itself is exercised by ImpendingMechanicTest; these tests cover each card's body
 * ability via the enters-the-battlefield trigger (cast for the normal mana cost so it enters as
 * a creature immediately and the trigger fires).
 */
class DskOverlordCycleScenarioTest : ScenarioTestBase() {

    init {
        context("Overlord of the Boilerbilges — enters: deal 4 damage to any target") {
            test("the enters trigger deals 4 damage to a chosen player") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Overlord of the Boilerbilges")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val opponentLifeBefore = game.getLifeTotal(2)

                game.castSpell(1, "Overlord of the Boilerbilges").error shouldBe null
                game.resolveStack()

                withClue("The enters trigger should pause to choose a target") {
                    game.hasPendingDecision() shouldBe true
                }
                val opponent = EntityId.of("player-2")
                game.selectTargets(listOf(opponent))
                game.resolveStack()

                withClue("It deals 4 damage to the chosen player") {
                    game.getLifeTotal(2) shouldBe opponentLifeBefore - 4
                }
            }
        }

        context("Overlord of the Floodpits — enters: draw two, then discard a card") {
            test("the enters trigger nets one card (draw two, discard one)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Overlord of the Floodpits")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // After casting, the Overlord leaves hand; hand is empty until it draws.
                game.castSpell(1, "Overlord of the Floodpits").error shouldBe null
                game.resolveStack()

                // Draw two, then choose a card to discard (a SelectCards decision).
                withClue("The enters trigger should pause to choose a card to discard") {
                    game.hasPendingDecision() shouldBe true
                }
                val toDiscard = game.findCardsInHand(1, "Mountain").take(1)
                game.selectCards(toDiscard)
                game.resolveStack()

                withClue("Net +1 card: drew two, discarded one") {
                    game.handSize(1) shouldBe 1
                }
                withClue("The discarded card is in the graveyard") {
                    game.graveyardSize(1) shouldBe 1
                }
            }
        }

        context("Overlord of the Hauntwoods — enters: create a tapped Everywhere land token") {
            test("the enters trigger creates one tapped Everywhere land token") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Overlord of the Hauntwoods")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Overlord of the Hauntwoods").error shouldBe null
                game.resolveStack()

                val tokens = game.findPermanents("Everywhere")
                withClue("Exactly one Everywhere token is created") {
                    tokens.size shouldBe 1
                }
                val token = tokens.first()
                token shouldNotBe null
                withClue("The Everywhere token enters tapped") {
                    game.state.getEntity(token)!!.has<TappedComponent>() shouldBe true
                }
            }
        }
    }
}
