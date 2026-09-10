package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class NihilSpellbombScenarioTest : ScenarioTestBase() {
    init {
        test("exiles the targeted graveyard and paying black draws") {
            val game = scenario().withPlayers("P1", "P2").withCardOnBattlefield(1, "Nihil Spellbomb")
                .withLandsOnBattlefield(1, "Swamp", 1).withCardInLibrary(1, "Forest")
                .withCardInGraveyard(2, "Hill Giant").withCardInGraveyard(2, "Ornithopter")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.requireCard("Nihil Spellbomb").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, bomb, ability, targets = listOf(ChosenTarget.Player(game.player2Id)))).error shouldBe null
            game.resolveStack()
            (game.getPendingDecision() is YesNoDecision) shouldBe true
            game.answerYesNo(true)
            (game.getPendingDecision() is SelectManaSourcesDecision) shouldBe true
            game.submitManaSourcesAutoPay(); game.resolveStack()
            game.state.getGraveyard(game.player2Id).isEmpty() shouldBe true
            game.isInExile(2, "Hill Giant") shouldBe true
            game.handSize(1) shouldBe 1
        }
        test("declining black payment still exiles the graveyard") {
            val game = scenario().withPlayers("P1", "P2").withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(2, "Hill Giant").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.requireCard("Nihil Spellbomb").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, bomb, ability, targets = listOf(ChosenTarget.Player(game.player2Id))))
            game.resolveStack(); game.answerYesNo(false); game.resolveStack()
            game.isInExile(2, "Hill Giant") shouldBe true
            game.handSize(1) shouldBe 0
        }
    }
}
