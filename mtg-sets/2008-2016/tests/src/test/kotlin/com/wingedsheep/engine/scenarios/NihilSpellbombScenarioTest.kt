package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NihilSpellbombScenarioTest : ScenarioTestBase() {
    init {
        test("sacrifice exiles every card in the targeted player's graveyard") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInGraveyard(2, "Hill Giant")
                .withCardInGraveyard(2, "Ornithopter")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.getCard("Nihil Spellbomb")!!.activatedAbilities.single().id
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = bomb,
                    abilityId = ability,
                    targets = listOf(ChosenTarget.Player(game.player2Id)),
                ),
            ).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Nihil Spellbomb") shouldBe true
            game.isInExile(2, "Hill Giant") shouldBe true
            game.isInExile(2, "Ornithopter") shouldBe true
        }

        test("paying black for the battlefield-to-graveyard trigger draws one card") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInLibrary(1, "Ornithopter")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.getCard("Nihil Spellbomb")!!.activatedAbilities.single().id
            val handBefore = game.handSize(1)
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = bomb,
                    abilityId = ability,
                    targets = listOf(ChosenTarget.Player(game.player2Id)),
                ),
            ).error shouldBe null

            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.submitManaSourcesAutoPay().error shouldBe null
            game.resolveStack()

            game.handSize(1) shouldBe handBefore + 1
            game.isInGraveyard(1, "Nihil Spellbomb") shouldBe true
        }
    }
}
