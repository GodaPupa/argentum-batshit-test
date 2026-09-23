package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tmp.cards.Capsize
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CapsizeScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + Capsize)
        d.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.state.stack.isNotEmpty() && guard++ < 30) {
            d.bothPass()
        }
        withClue("stack should resolve completely") {
            d.state.stack.isEmpty() shouldBe true
        }
    }

    fun castWithBuyback(
        d: GameTestDriver,
        caster: EntityId,
        capsize: EntityId,
        target: EntityId
    ) {
        d.giveMana(caster, Color.BLUE, 6)
        d.submit(
            CastSpell(
                playerId = caster,
                cardId = capsize,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = ChoiceSlot.BUYBACK
            )
        ).error shouldBe null
    }

    test("legal actions expose buyback as buyback rather than kicker") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        d.putLandOnBattlefield(opponent, "Island")
        d.putCardInHand(caster, "Capsize")
        d.giveMana(caster, Color.BLUE, 6)

        val buybackAction = d.legalActions(caster)
            .singleOrNull { it.description == "Cast Capsize (Buyback)" }

        withClue("buyback variant should be offered with its own mechanic label") {
            (buybackAction != null) shouldBe true
        }
        val cast = buybackAction!!.action as CastSpell
        cast.declaredCostSlot shouldBe ChoiceSlot.BUYBACK
        buybackAction.affordable shouldBe true
        buybackAction.manaCostString shouldBe "{4}{U}{U}"
    }

    test("normal Capsize returns target and goes to graveyard") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val capsize = d.putCardInHand(caster, "Capsize")
        d.giveMana(caster, Color.BLUE, 3)

        d.castSpellWithTargets(
            caster,
            capsize,
            listOf(ChosenTarget.Permanent(target))
        ).error shouldBe null
        resolveStack(d)

        d.getHand(opponent).contains(target) shouldBe true
        d.getGraveyardCardNames(caster).contains("Capsize") shouldBe true
        d.getHand(caster).contains(capsize) shouldBe false
    }

    test("paid buyback returns Capsize to hand after successful resolution") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val capsize = d.putCardInHand(caster, "Capsize")

        castWithBuyback(d, caster, capsize, target)
        resolveStack(d)

        d.getHand(opponent).contains(target) shouldBe true
        d.getHand(caster).contains(capsize) shouldBe true
        d.getGraveyardCardNames(caster).contains("Capsize") shouldBe false
    }

    test("countered buyback Capsize goes to graveyard instead of hand") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val capsize = d.putCardInHand(caster, "Capsize")
        val counterspell = d.putCardInHand(opponent, "Counterspell")
        d.giveMana(opponent, Color.BLUE, 2)

        castWithBuyback(d, caster, capsize, target)
        d.passPriority(caster).error shouldBe null
        d.castSpellWithTargets(
            opponent,
            counterspell,
            listOf(ChosenTarget.Spell(capsize))
        ).error shouldBe null
        resolveStack(d)

        d.findPermanent(opponent, "Grizzly Bears") shouldBe target
        d.getGraveyardCardNames(caster).contains("Capsize") shouldBe true
        d.getHand(caster).contains(capsize) shouldBe false
    }
})
