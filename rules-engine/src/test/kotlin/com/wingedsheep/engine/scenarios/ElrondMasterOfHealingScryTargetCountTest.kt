package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Elrond, Master of Healing (LTR) — first ability: "Whenever you scry, put a +1/+1 counter on each
 * of up to X target creatures, where X is the number of cards looked at while scrying this way."
 *
 * The target cap is `dynamicMaxCount = ContextProperty(TRIGGER_SCRY_COUNT)`. It must be resolved
 * from the trigger's scry count when the ability goes on the stack — previously the snapshot built
 * an EffectContext without `triggerScryCount`, so the cap resolved to 0 and the player could pick no
 * targets ("up to up to X target creatures, 0 / 0"). After a Serum Visions (scry 2) the cap is 2.
 */
class ElrondMasterOfHealingScryTargetCountTest : ScenarioTestBase() {

    init {
        context("scry trigger target cap = number of cards scried") {

            test("Serum Visions' scry 2 lets Elrond target up to 2 creatures") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Elrond, Master of Healing", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(1, "Island")
                    .withCardInHand(1, "Serum Visions")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Serum Visions: draw a card, then scry 2.
                game.castSpell(1, "Serum Visions").error shouldBe null
                game.resolveStack()

                // Resolve the scry: keep all 2 looked-at cards on top (select none to bottom, then
                // keep their order). Both steps look at 2 cards, so X = 2.
                withClue("Serum Visions' scry 2 prompts a scry decision") {
                    (game.getPendingDecision() is SelectCardsDecision) shouldBe true
                }
                game.selectCards(emptyList())
                (game.getPendingDecision() as? ReorderLibraryDecision)?.let {
                    game.submitDecision(OrderedResponse(it.id, it.cards))
                }

                // Elrond's "whenever you scry" trigger now asks for its targets.
                val targetDecision = game.getPendingDecision()
                withClue("Elrond's scry trigger prompts a target selection [now=${targetDecision?.let { it::class.simpleName + ":" + it.prompt }}]") {
                    (targetDecision is ChooseTargetsDecision) shouldBe true
                }
                targetDecision as ChooseTargetsDecision
                val req = targetDecision.targetRequirements.first()

                withClue("X = 2 cards scried, so the cap is 2 (was 0 before the fix)") {
                    req.maxTargets shouldBe 2
                }
                withClue("optional trigger lets you choose zero") {
                    req.minTargets shouldBe 0
                }
                withClue("the prompt label must not double the quantifier") {
                    req.description shouldNotContain "up to up to"
                    req.description shouldContain "up to"
                }
            }
        }
    }
}
