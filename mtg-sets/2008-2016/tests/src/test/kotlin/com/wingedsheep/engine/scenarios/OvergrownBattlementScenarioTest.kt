package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.roe.cards.OvergrownBattlement
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OvergrownBattlementScenarioTest : FunSpec({
    test("Overgrown Battlement counts every defender its controller controls for green mana") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(OvergrownBattlement)
        d.initMirrorMatch(deck = Deck.of("Forest" to 30), startingLife = 20)
        val p1 = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val source = d.putCreatureOnBattlefield(p1, "Overgrown Battlement")
        d.putCreatureOnBattlefield(p1, "Overgrown Battlement")
        d.putCreatureOnBattlefield(p1, "Overgrown Battlement")
        d.putCreatureOnBattlefield(d.player2, "Overgrown Battlement")
        d.removeSummoningSickness(source)

        val abilityId = OvergrownBattlement.activatedAbilities.single().id
        d.submitSuccess(ActivateAbility(playerId = p1, sourceId = source, abilityId = abilityId))

        d.state.getEntity(p1)!!.get<ManaPoolComponent>()!!.green shouldBe 3
        (d.state.getEntity(source)!!.get<TappedComponent>() != null) shouldBe true
    }
})
