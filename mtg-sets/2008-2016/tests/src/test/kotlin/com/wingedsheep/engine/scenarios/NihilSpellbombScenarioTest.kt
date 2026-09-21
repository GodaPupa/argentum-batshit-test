package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Pins the independent graveyard-exile ability and black-payment dies trigger. */
class NihilSpellbombScenarioTest : ScenarioTestBase() {
    init {
        test("activation exiles the target graveyard and paying black draws a card") {
            val game = scenario()
                .withPlayers("Controller", "Target")
                .withCardOnBattlefield(1, "Nihil Spellbomb", tapped = false)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInLibrary(1, "Forest")
                .withCardInGraveyard(2, "Grizzly Bears")
                .withCardInGraveyard(2, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spellbomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.requireCard("Nihil Spellbomb").activatedAbilities.single()
            val activated = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = spellbomb,
                    abilityId = ability.id,
                    targets = listOf(ChosenTarget.Player(game.player2Id)),
                )
            )
            withClue("activation should succeed: ${activated.error}") { activated.error shouldBe null }
            withClue("sacrifice is paid immediately") {
                game.isInGraveyard(1, "Nihil Spellbomb") shouldBe true
            }

            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true)
            game.getPendingDecision().shouldBeInstanceOf<SelectManaSourcesDecision>()
            game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.resolveStack()

            withClue("the separate {B} payment drew exactly one card") {
                game.handSize(1) shouldBe 1
            }
            withClue("the activated ability exiled every card in the targeted graveyard") {
                game.graveyardSize(2) shouldBe 0
                game.isInExile(2, "Grizzly Bears") shouldBe true
                game.isInExile(2, "Hill Giant") shouldBe true
            }
        }

        test("declining the black payment does not draw") {
            val game = scenario()
                .withPlayers("Controller", "Target")
                .withCardOnBattlefield(1, "Nihil Spellbomb", tapped = false)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val spellbomb = game.findPermanent("Nihil Spellbomb")!!
            val ability = cardRegistry.requireCard("Nihil Spellbomb").activatedAbilities.single()
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = spellbomb,
                    abilityId = ability.id,
                    targets = listOf(ChosenTarget.Player(game.player2Id)),
                )
            ).error shouldBe null

            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(false)
            game.resolveStack()
            game.resolveStack()

            game.handSize(1) shouldBe 0
        }
    }
}
