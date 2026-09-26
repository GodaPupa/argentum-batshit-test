package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.GameRng
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * A cycling trigger originates outside the battlefield. The damage-source LKI guard must not
 * demand a battlefield-departure snapshot for that source, including after its card is exiled.
 * Exercises the repository's canonical LGN Gempalm Incinerator, without substituting a fixture
 * implementation: cycling {1}{R}, then optional creature damage equal to battlefield Goblins.
 * This is a generic engine regression, not admission of the card to a Pauper candidate pool.
 */
class GempalmIncineratorScenarioTest : ScenarioTestBase() {
    private fun TestGame.resolveOne() {
        passPriority().error shouldBe null
        passPriority().error shouldBe null
        state.pendingDecision shouldBe null
        state.gameOver shouldBe false
    }

    init {
        for ((label, exileSource, removeGoblin) in listOf(
            Triple("source remains in graveyard", false, false),
            Triple("source exiled before damage", true, false),
            Triple("Goblin count changes before resolution", false, true)
        )) {
            test("cycling damage remains valid without battlefield source LKI - $label") {
                val game = scenario()
                    .withPlayers("Cycler", "Opponent")
                    .withCardInHand(1, "Gempalm Incinerator")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(1, "Goblin Piker")
                    .withCardOnBattlefield(2, "Goblin Raider")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInHand(2, "Fade from Memory")
                    .withCardInHand(2, "Shock")
                    .withLandsOnBattlefield(2, "Swamp", 1)
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                game.state = game.state.copy(rng = GameRng.seeded(0xFE000079))
                val giant = game.findPermanent("Hill Giant")!!
                val raider = game.findPermanent("Goblin Raider")!!
                val initialLibrary = game.librarySize(1)

                game.cycleCard(1, "Gempalm Incinerator").error shouldBe null
                game.isInGraveyard(1, "Gempalm Incinerator") shouldBe true
                game.findPermanents("Mountain").count { id ->
                    game.state.getEntity(id)?.has<TappedComponent>() == true
                } shouldBe 2
                game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                game.selectTargets(listOf(giant)).error shouldBe null
                game.state.pendingDecision shouldBe null
                game.state.stack.isNotEmpty() shouldBe true

                if (exileSource || removeGoblin) {
                    game.passPriority().error shouldBe null
                    if (exileSource) {
                        game.castSpellTargetingGraveyardCard(2, "Fade from Memory", 1, "Gempalm Incinerator")
                            .error shouldBe null
                    } else {
                        game.castSpell(2, "Shock", raider).error shouldBe null
                    }
                    game.resolveOne()
                    if (exileSource) game.isInExile(1, "Gempalm Incinerator") shouldBe true
                    else game.isInGraveyard(2, "Goblin Raider") shouldBe true
                    game.state.stack.isNotEmpty() shouldBe true
                }

                game.resolveStack().forEach { it.error shouldBe null }
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                game.answerYesNo(true).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }

                game.state.pendingDecision shouldBe null
                game.state.stack.isEmpty() shouldBe true
                game.state.gameOver shouldBe false
                game.findPermanent("Hill Giant") shouldBe giant
                // Count Goblins controlled by either player at resolution, excluding the cycled card.
                game.state.getEntity(giant)!!.get<DamageComponent>()!!.amount shouldBe if (removeGoblin) 1 else 2
                game.librarySize(1) shouldBe initialLibrary - 1
                game.handSize(1) shouldBe 1
                game.getLifeTotal(1) shouldBe 20
                game.getLifeTotal(2) shouldBe 20
            }
        }
    }
}
