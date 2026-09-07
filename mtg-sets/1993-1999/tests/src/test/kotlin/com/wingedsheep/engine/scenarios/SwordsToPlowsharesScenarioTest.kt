package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Swords to Plowshares (LEA #40).
 *
 * "{W} Instant — Exile target creature. Its controller gains life equal to its power."
 *
 * Grizzly Bears has power 2 and is controlled by player 2; player 1 casts Swords to
 * Plowshares at it, so player 2 (the creature's controller) gains 2 life and the
 * creature is exiled.
 */
class SwordsToPlowsharesScenarioTest : ScenarioTestBase() {

    init {
        test("Swords to Plowshares exiles the creature and its controller gains life equal to its power") {
            val game = scenario()
                .withPlayers("Caster", "Defender")
                .withCardInHand(1, "Swords to Plowshares")
                .withLandsOnBattlefield(1, "Plains", 1)
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!

            val cast = game.castSpell(1, "Swords to Plowshares", targetId = bears)
            withClue("Casting Swords to Plowshares at Grizzly Bears should succeed: ${cast.error}") {
                cast.error shouldBe null
            }
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
            game.resolveStack()

            withClue("Grizzly Bears (the targeted creature) should be exiled") {
                game.isOnBattlefield("Grizzly Bears") shouldBe false
            }
            withClue("The creature's controller (player 2) should gain 2 life (Grizzly Bears' power)") {
                game.getLifeTotal(2) shouldBe 22
            }
            withClue("The caster (player 1) should not gain life") {
                game.getLifeTotal(1) shouldBe 20
            }
        }
    }
}
