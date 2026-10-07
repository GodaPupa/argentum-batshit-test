package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.pls.cards.StarCompass
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StarCompassScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + StarCompass)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun pool(d: GameTestDriver, player: com.wingedsheep.sdk.model.EntityId): ManaPoolComponent =
        d.state.getEntity(player)?.get<ManaPoolComponent>() ?: ManaPoolComponent()

    test("enters the battlefield tapped") {
        val d = driver()
        val me = d.activePlayer!!
        val card = d.putCardInHand(me, StarCompass.name)
        d.giveColorlessMana(me, 2)

        d.castSpell(me, card).isSuccess shouldBe true
        d.bothPass()

        val compass = d.findPermanent(me, StarCompass.name)!!
        d.isTapped(compass) shouldBe true
    }

    test("uses only basic lands you control, not an opponent's basic land") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val compass = d.putPermanentOnBattlefield(me, StarCompass.name)
        val forest = d.putLandOnBattlefield(me, "Forest")
        d.putLandOnBattlefield(enemy, "Island")
        d.untapPermanent(compass)

        d.submit(ActivateAbility(me, compass, StarCompass.activatedAbilities.single().id, manaColorChoice = Color.GREEN)).isSuccess shouldBe true
        pool(d, me).green shouldBe 1

        d.untapPermanent(compass)
        d.submit(ActivateAbility(me, compass, StarCompass.activatedAbilities.single().id, manaColorChoice = Color.BLUE)).isSuccess shouldBe true
        pool(d, me).blue shouldBe 0
        pool(d, me).green shouldBe 2
        d.isTapped(forest) shouldBe false
    }

    test("a tapped basic land still contributes its producible color") {
        val d = driver()
        val me = d.activePlayer!!
        val compass = d.putPermanentOnBattlefield(me, StarCompass.name)
        val island = d.putLandOnBattlefield(me, "Island")
        d.tapPermanent(island)
        d.untapPermanent(compass)

        d.submit(ActivateAbility(me, compass, StarCompass.activatedAbilities.single().id, manaColorChoice = Color.BLUE)).isSuccess shouldBe true
        pool(d, me).blue shouldBe 1
    }
})
