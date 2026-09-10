package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lea.cards.LlanowarElves
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LlanowarElvesScenarioTest : FunSpec({
    fun driver() = GameTestDriver().apply { registerCards(TestCards.all + LlanowarElves) }

    test("summoning sickness prevents activating its tap mana ability") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val elves = d.putCreatureOnBattlefield(player, "Llanowar Elves")

        val result = d.submit(
            ActivateAbility(player, elves, LlanowarElves.activatedAbilities.single().id)
        )

        result.error shouldBe "This creature has summoning sickness"
        d.isTapped(elves) shouldBe false
        d.state.getEntity(player)?.get<ManaPoolComponent>()?.green shouldBe 0
    }

    test("a creature under its controller's control since the turn began taps for one green") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val elves = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.removeSummoningSickness(elves)

        val result = d.submit(
            ActivateAbility(player, elves, LlanowarElves.activatedAbilities.single().id)
        )

        result.isSuccess shouldBe true
        d.isTapped(elves) shouldBe true
        d.state.getEntity(player)?.get<ManaPoolComponent>()?.green shouldBe 1
    }
})
