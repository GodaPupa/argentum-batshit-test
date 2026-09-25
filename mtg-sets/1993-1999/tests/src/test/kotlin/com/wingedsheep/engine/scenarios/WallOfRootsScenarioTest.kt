package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mir.cards.WallOfRoots
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WallOfRootsScenarioTest : FunSpec({
    test("Wall of Roots adds green, pays a -0/-1 counter, and activates only once each turn") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(WallOfRoots)
        d.initMirrorMatch(deck = Deck.of("Forest" to 30), startingLife = 20)
        val p1 = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val wall = d.putCreatureOnBattlefield(p1, "Wall of Roots")
        val abilityId = WallOfRoots.activatedAbilities.single().id
        val action = ActivateAbility(playerId = p1, sourceId = wall, abilityId = abilityId)

        d.submitSuccess(action)

        d.state.getEntity(p1)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        d.state.getEntity(wall)!!.get<CountersComponent>()!!.counters.values.sum() shouldBe 1
        StateProjector().getProjectedToughness(d.state, wall) shouldBe 4

        d.submitExpectFailure(action)
        d.state.getEntity(p1)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        d.state.getEntity(wall)!!.get<CountersComponent>()!!.counters.values.sum() shouldBe 1
    }

    test("Wall of Roots can activate on the opponent turn after its per-turn limit resets") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(WallOfRoots)
        d.initMirrorMatch(deck = Deck.of("Forest" to 30), startingLife = 20)
        val p1 = d.activePlayer!!
        val p2 = d.getOpponent(p1)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val wall = d.putCreatureOnBattlefield(p1, "Wall of Roots")
        val action = ActivateAbility(playerId = p1, sourceId = wall,
            abilityId = WallOfRoots.activatedAbilities.single().id)
        d.submitSuccess(action)
        d.passPriorityUntil(Step.UPKEEP)
        d.activePlayer shouldBe p2
        d.passPriority(p2)
        d.submitSuccess(action)
        d.state.getEntity(p1)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        StateProjector().getProjectedToughness(d.state, wall) shouldBe 3
        d.submitExpectFailure(action)
    }
})
