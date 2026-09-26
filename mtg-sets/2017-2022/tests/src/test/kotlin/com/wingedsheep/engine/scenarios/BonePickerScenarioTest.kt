package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.akh.cards.BonePicker
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.GameRng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BonePickerScenarioTest : FunSpec({
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + BonePicker)
        initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        // Initial shuffle is of identical basic lands; pin subsequent fixture RNG explicitly.
        replaceState(state.copy(rng = GameRng.seeded(2026092601L)))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("without a death the full cost is required and both keywords are present") {
        val d = driver()
        val me = d.activePlayer!!
        val card = d.putCardInHand(me, "Bone Picker")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, card).isSuccess shouldBe false
        d.giveColorlessMana(me, 3)
        d.castSpell(me, card).isSuccess shouldBe true
        d.bothPass()
        val bird = d.findPermanent(me, "Bone Picker")!!
        d.state.projectedState.getPower(bird) shouldBe 3
        d.state.projectedState.getToughness(bird) shouldBe 2
        d.state.projectedState.hasKeyword(bird, Keyword.FLYING) shouldBe true
        d.state.projectedState.hasKeyword(bird, Keyword.DEATHTOUCH) shouldBe true
    }

    for (token in listOf(false, true)) {
        test("an actual opposing ${if (token) "token" else "nontoken"} death enables the one-black cost") {
            val d = driver()
            val me = d.activePlayer!!
            val opponent = d.getOpponent(me)
            val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
            if (token) d.addComponent(victim, TokenComponent)
            val bolt = d.putCardInHand(me, "Lightning Bolt")
            d.giveMana(me, Color.RED, 1)
            d.castSpell(me, bolt, listOf(victim)).isSuccess shouldBe true
            d.bothPass()
            d.state.getBattlefield().contains(victim) shouldBe false
            val bird = d.putCardInHand(me, "Bone Picker")
            d.giveMana(me, Color.BLACK, 1)
            d.castSpell(me, bird).isSuccess shouldBe true
            d.bothPass()
            (d.findPermanent(me, "Bone Picker") != null) shouldBe true
        }
    }

    test("destroying a noncreature artifact land does not enable the reduction") {
        val d = driver()
        val me = d.activePlayer!!
        val land = d.putLandOnBattlefield(d.getOpponent(me), "Great Furnace")
        val shatter = d.putCardInHand(me, "Shatter")
        d.giveMana(me, Color.RED, 2)
        d.castSpell(me, shatter, listOf(land)).isSuccess shouldBe true
        d.bothPass()
        d.state.getBattlefield().contains(land) shouldBe false
        val bird = d.putCardInHand(me, "Bone Picker")
        d.giveMana(me, Color.BLACK, 1)
        d.castSpell(me, bird).isSuccess shouldBe false
    }

    test("a death in the preceding turn does not reduce this turn's cost") {
        val d = driver()
        val me = d.activePlayer!!
        val victim = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.passPriorityUntil(Step.END)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val nextPlayer = d.activePlayer!!
        val bird = d.putCardInHand(nextPlayer, "Bone Picker")
        d.giveMana(nextPlayer, Color.BLACK, 1)
        d.castSpell(nextPlayer, bird).isSuccess shouldBe false
    }
})
