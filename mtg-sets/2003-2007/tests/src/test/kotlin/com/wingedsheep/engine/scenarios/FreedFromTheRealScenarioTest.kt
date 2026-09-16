package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.sok.cards.FreedFromTheReal
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class FreedFromTheRealScenarioTest : ScenarioTestBase() {
    init {
        test("the Aura's controller can tap and untap the enchanted creature") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                .withCardAttachedTo(1, "Freed from the Real", "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val aura = game.findPermanent("Freed from the Real")!!
            val creature = game.findPermanent("Grizzly Bears")!!
            game.execute(ActivateAbility(game.player1Id, aura, FreedFromTheReal.activatedAbilities[0].id))
                .error shouldBe null
            game.resolveStack()
            game.state.getEntity(creature)?.has<TappedComponent>() shouldBe true

            game.execute(ActivateAbility(game.player1Id, aura, FreedFromTheReal.activatedAbilities[1].id))
                .error shouldBe null
            game.resolveStack()
            game.state.getEntity(creature)?.has<TappedComponent>() shouldBe false
        }
    }
}
