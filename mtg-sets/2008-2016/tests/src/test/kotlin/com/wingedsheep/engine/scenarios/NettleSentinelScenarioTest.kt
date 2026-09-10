package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.eve.cards.NettleSentinel
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NettleSentinelScenarioTest : FunSpec({
    test("casting a green spell may untap Nettle Sentinel") {
        val d = GameTestDriver().apply { registerCards(TestCards.all + NettleSentinel) }
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val sentinel = d.putCreatureOnBattlefield(player, "Nettle Sentinel")
        d.tapPermanent(sentinel)
        val spell = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN, 2)

        d.castSpell(player, spell).isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.bothPass()
        d.submitYesNo(player, true)
        d.bothPass()
        d.state.getEntity(sentinel)?.has<TappedComponent>() shouldBe false
    }
})
