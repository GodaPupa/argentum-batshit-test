package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh1.cards.GenerousGift
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GenerousGiftScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + GenerousGift)
        d.initMirrorMatch(
            deck = Deck.of("Plains" to 40),
            skipMulligans = true,
            startingPlayer = 0,
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("destroyed permanent controller receives the Elephant token") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)

        val target = d.putLandOnBattlefield(opponent, "Plains")
        d.giveMana(caster, Color.WHITE, 3)
        val gift = d.putCardInHand(caster, "Generous Gift")

        d.castSpell(caster, gift, targets = listOf(target)).isSuccess shouldBe true
        d.bothPass()

        d.findPermanent(opponent, "Plains") shouldBe null
        d.findPermanent(caster, "Elephant Token") shouldBe null

        val elephant = d.findPermanent(opponent, "Elephant Token")!!
        d.state.projectedState.getPower(elephant) shouldBe 3
        d.state.projectedState.getToughness(elephant) shouldBe 3
        d.state.getEntity(elephant)?.get<CardComponent>()?.colors shouldBe setOf(Color.GREEN)
    }

    test("illegal target at resolution creates no Elephant") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)

        val target = d.putLandOnBattlefield(opponent, "Plains")
        d.giveMana(caster, Color.WHITE, 3)
        val gift = d.putCardInHand(caster, "Generous Gift")

        d.castSpell(caster, gift, targets = listOf(target)).isSuccess shouldBe true

        // The target leaves before resolution. Generous Gift must be countered by the
        // rules for having no legal targets, so the token-producing effect never happens.
        d.moveToGraveyard(target)
        d.bothPass()

        d.findPermanent(opponent, "Elephant Token") shouldBe null
        d.findPermanent(caster, "Elephant Token") shouldBe null
    }
})
