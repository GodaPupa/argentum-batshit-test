package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh2.cards.DeepwoodDenizen
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class DeepwoodDenizenScenarioTest : FunSpec({
    for (ownCounterCount in listOf(4, 5, 9)) {
        test("only own creature counters reduce generic activation cost: $ownCounterCount") {
            val d = GameTestDriver()
            d.registerCards(TestCards.all + DeepwoodDenizen)
            d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val me = d.activePlayer!!
            val denizen = d.putCreatureOnBattlefield(me, "Deepwood Denizen")
            val other = d.putCreatureOnBattlefield(me, "Deepwood Denizen")
            val enemy = d.putCreatureOnBattlefield(d.getOpponent(me), "Deepwood Denizen")
            val land = d.putPermanentOnBattlefield(me, "Forest")
            d.removeSummoningSickness(denizen)
            d.tapPermanent(land)
            d.addComponent(denizen, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
            d.addComponent(other, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to ownCounterCount - 2)))
            d.addComponent(enemy, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 7)))
            d.addComponent(land, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 7)))
            // No colored mana: even an oversized discount cannot pay the green pip.
            val action = ActivateAbility(me, denizen, DeepwoodDenizen.activatedAbilities.single().id)
            d.submit(action).isSuccess shouldBe false
            d.giveMana(me, Color.GREEN, 1)
            val before = d.state.getHand(me).size
            d.submit(action).isSuccess shouldBe (ownCounterCount >= 5)
            if (ownCounterCount >= 5) {
                d.bothPass()
                d.state.getHand(me).size shouldBe before + 1
            } else {
                d.state.getHand(me).size shouldBe before
            }
            DeepwoodDenizen.keywords shouldContain Keyword.VIGILANCE
        }
    }
})
