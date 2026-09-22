package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.DeemInferior
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DeemInferiorCostScenarioTest : ScenarioTestBase() {
    init {
        test("cost reduction tracks cards drawn this turn and never removes the blue pip") {
            for ((drawn, expectedGeneric) in listOf(0 to 3, 1 to 2, 2 to 1, 3 to 0, 5 to 0)) {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Deem Inferior")
                    .withCardsDrawnThisTurn(1, drawn)
                    .withActivePlayer(1)
                    .build()

                val cost = CostCalculator(cardRegistry).calculateEffectiveCost(
                    game.state,
                    cardRegistry.requireCard("Deem Inferior"),
                    game.player1Id,
                )

                cost.genericAmount shouldBe expectedGeneric
                cost.colorCount[Color.BLUE] shouldBe 1
            }
        }
    }
}

class DeemInferiorResolutionScenarioTest : FunSpec({
    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + DeemInferior)
        return driver
    }

    test("target permanent's owner chooses second from top") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val p1 = driver.player1
        val p2 = driver.player2
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val spell = driver.putCardInHand(p1, "Deem Inferior")
        val target = driver.putCreatureOnBattlefield(p2, "Centaur Courser")
        val originalTop = driver.state.getZone(ZoneKey(p2, Zone.LIBRARY)).first()

        driver.giveMana(p1, Color.BLUE, 1)
        driver.giveColorlessMana(p1, 3)
        driver.submit(
            CastSpell(
                playerId = p1,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        driver.bothPass()

        val decision = driver.pendingDecision as ChooseOptionDecision
        decision.playerId shouldBe p2
        decision.options shouldBe listOf("Second from top of library", "Bottom of library")
        driver.submit(SubmitDecision(p2, OptionChosenResponse(decision.id, 0))).isSuccess shouldBe true

        val library = driver.state.getZone(ZoneKey(p2, Zone.LIBRARY))
        library.first() shouldBe originalTop
        library[1] shouldBe target
        driver.state.getEntity(target)?.get<RevealedToComponent>() shouldBe
            RevealedToComponent(setOf(p1, p2))
    }

    test("target permanent's owner may choose bottom") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        val p1 = driver.player1
        val p2 = driver.player2
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val spell = driver.putCardInHand(p1, "Deem Inferior")
        val target = driver.putCreatureOnBattlefield(p2, "Centaur Courser")
        driver.giveMana(p1, Color.BLUE, 1)
        driver.giveColorlessMana(p1, 3)
        driver.submit(
            CastSpell(
                playerId = p1,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        driver.bothPass()

        val decision = driver.pendingDecision as ChooseOptionDecision
        driver.submit(SubmitDecision(p2, OptionChosenResponse(decision.id, 1))).isSuccess shouldBe true

        driver.state.getZone(ZoneKey(p2, Zone.LIBRARY)).last() shouldBe target
    }
})
