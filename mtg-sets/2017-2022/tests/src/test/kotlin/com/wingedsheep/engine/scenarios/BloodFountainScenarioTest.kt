package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.vow.cards.BloodFountain
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Blood Fountain (VOW #95) — {B} Artifact.
 *
 *   When this artifact enters, create a Blood token.
 *   {3}{B}, {T}, Sacrifice this artifact: Return up to two target creature cards from your
 *   graveyard to your hand.
 *
 * Exercises the ETB Blood token creation and the tap-sacrifice activated ability returning two
 * targeted creature cards from the graveyard to hand.
 */
class BloodFountainScenarioTest : ScenarioTestBase() {

    init {
        // Every activation fixture obtains its token by resolving the actual producer.
        fun driverWithProducedBlood(): GameTestDriver {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all)
            driver.registerCards(PredefinedTokens.allTokens)
            driver.registerCard(BloodFountain)
            driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val me = driver.activePlayer!!
            val fountain = driver.putCardInHand(me, "Blood Fountain")
            driver.giveMana(me, Color.BLACK, 1)
            driver.submitSuccess(CastSpell(me, fountain, paymentStrategy = PaymentStrategy.FromPool))
            driver.bothPass().isSuccess shouldBe true // Fountain spell
            driver.bothPass().isSuccess shouldBe true // Its mandatory ETB trigger
            driver.state.stack.isEmpty() shouldBe true
            driver.state.pendingDecision shouldBe null
            checkNotNull(driver.findPermanent(me, "Blood"))
            return driver
        }

        context("Blood Fountain") {

            test("entering the battlefield creates a Blood token") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Blood Fountain")
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Blood Fountain").error shouldBe null
                game.resolveStack()

                withClue("a Blood token is created on entering the battlefield") {
                    game.findPermanents("Blood").size shouldBe 1
                }
            }

            test("the {3}{B}, tap, sacrifice ability returns two graveyard creatures to hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Blood Fountain")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fountain = game.findPermanent("Blood Fountain")!!
                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                val giant = game.findCardsInGraveyard(1, "Hill Giant").single()
                val abilityId = cardRegistry.getCard("Blood Fountain")!!.activatedAbilities.first().id

                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fountain,
                        abilityId = abilityId,
                        targets = listOf(
                            ChosenTarget.Card(bears, game.player1Id, Zone.GRAVEYARD),
                            ChosenTarget.Card(giant, game.player1Id, Zone.GRAVEYARD)
                        )
                    )
                )
                withClue("Activating the ability should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStack()

                withClue("Blood Fountain was sacrificed to pay the cost") {
                    game.isOnBattlefield("Blood Fountain") shouldBe false
                    game.isInGraveyard(1, "Blood Fountain") shouldBe true
                }
                withClue("Both creature cards returned to hand") {
                    game.isInHand(1, "Grizzly Bears") shouldBe true
                    game.isInHand(1, "Hill Giant") shouldBe true
                }
                withClue("Both are gone from the graveyard") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe false
                    game.isInGraveyard(1, "Hill Giant") shouldBe false
                }
            }
        }

        context("Blood produced by Blood Fountain") {
            test("pays mana tap sacrifice and own discard before drawing exactly one card") {
                val driver = driverWithProducedBlood()
                val me = driver.activePlayer!!
                val blood = driver.findPermanent(me, "Blood")!!
                val discard = driver.putCardInHand(me, "Grizzly Bears")
                val drawn = driver.putCardOnTopOfLibrary(me, "Forest")
                val handBefore = driver.getHand(me).toSet()
                driver.giveColorlessMana(me, 1)
                val action = ActivateAbility(
                    me, blood, PredefinedTokens.Blood.activatedAbilities.first().id,
                    costPayment = AdditionalCostPayment(discardedCards = listOf(discard)),
                    paymentStrategy = PaymentStrategy.FromPool
                )
                driver.submitSuccess(action)
                driver.state.getEntity(me)!!.get<ManaPoolComponent>()!!.total shouldBe 0
                driver.findPermanent(me, "Blood") shouldBe null
                driver.getHand(me).toSet() shouldBe handBefore - discard
                (discard in driver.state.getZone(ZoneKey(me, Zone.GRAVEYARD))) shouldBe true
                driver.state.getZone(ZoneKey(me, Zone.LIBRARY)).first() shouldBe drawn
                driver.state.stack.size shouldBe 1

                // The sacrificed token cannot pay for another activation while the first waits.
                val beforeRepeat = driver.state
                val repeat = driver.submit(action)
                repeat.isSuccess shouldBe false
                repeat.isPaused shouldBe false
                repeat.newState shouldBe beforeRepeat
                driver.bothPass().isSuccess shouldBe true
                driver.getHand(me).toSet() shouldBe (handBefore - discard) + drawn
                driver.state.stack.isEmpty() shouldBe true
                driver.state.pendingDecision shouldBe null
            }

            for (failure in listOf("unfunded", "tapped", "empty hand", "opponent discard")) {
                test("rejects $failure without spending resources or drawing") {
                    val driver = driverWithProducedBlood()
                    val me = driver.activePlayer!!
                    val blood = driver.findPermanent(me, "Blood")!!
                    if (failure != "unfunded") driver.giveColorlessMana(me, 1)
                    if (failure == "tapped") driver.tapPermanent(blood)
                    if (failure == "empty hand") driver.getHand(me).toList().forEach(driver::moveToGraveyard)
                    val discarded = when (failure) {
                        "empty hand" -> emptyList()
                        "opponent discard" -> listOf(driver.putCardInHand(driver.getOpponent(me), "Grizzly Bears"))
                        else -> listOf(driver.getHand(me).first())
                    }
                    val before = driver.state
                    val result = driver.submit(ActivateAbility(
                        me, blood, PredefinedTokens.Blood.activatedAbilities.first().id,
                        costPayment = AdditionalCostPayment(discardedCards = discarded),
                        paymentStrategy = PaymentStrategy.FromPool
                    ))
                    result.isSuccess shouldBe false
                    result.isPaused shouldBe false
                    result.newState shouldBe before
                    driver.state shouldBe before
                }
            }
        }
    }
}
