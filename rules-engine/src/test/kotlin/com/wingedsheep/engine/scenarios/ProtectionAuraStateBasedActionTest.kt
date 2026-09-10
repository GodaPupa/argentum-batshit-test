package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * CR 702.16c: a permanent with protection can't be enchanted by Auras that have the stated
 * quality — such Auras are put into their owners' graveyards as a state-based action. This
 * covers protection gained AFTER the Aura attached (targeting-time protection is a separate,
 * already-enforced check).
 *
 * The classic Alpha case: a creature wearing Holy Strength (a white Aura) gains protection
 * from white via White Ward → Holy Strength falls off into the graveyard; White Ward itself
 * stays ("This effect doesn't remove this Aura"), and off-color Auras are untouched.
 */
class ProtectionAuraStateBasedActionTest : ScenarioTestBase() {

    init {
        context("protection gained after attachment removes same-color auras") {
            test("White Ward's pro-white sends an attached white aura to the graveyard, keeps itself and off-color auras") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardAttachedTo(1, "Holy Strength", "Grizzly Bears")   // white aura
                    .withCardAttachedTo(1, "Unholy Strength", "Grizzly Bears") // black aura
                    .withCardInHand(1, "White Ward")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val result = game.castSpell(1, "White Ward", bears)
                withClue("Casting White Ward on the (not yet protected) creature should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                withClue("Holy Strength (white) becomes an illegal attachment and goes to the graveyard") {
                    game.isOnBattlefield("Holy Strength") shouldBe false
                    game.isInGraveyard(1, "Holy Strength") shouldBe true
                }
                withClue("White Ward itself stays attached — its own effect doesn't remove it") {
                    game.isOnBattlefield("White Ward") shouldBe true
                }
                withClue("Unholy Strength (black) is unaffected by protection from white") {
                    game.isOnBattlefield("Unholy Strength") shouldBe true
                }
                withClue("The enchanted creature is unharmed") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }
        }
    }
}
