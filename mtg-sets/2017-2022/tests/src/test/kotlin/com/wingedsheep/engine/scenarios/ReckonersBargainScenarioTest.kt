package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Pins Reckoner's Bargain's additional sacrifice, last-known mana value, life gain, and draw. */
class ReckonersBargainScenarioTest : ScenarioTestBase() {
    init {
        test("sacrificing a mana-value-3 creature gains 3 life and draws two cards") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Reckoner's Bargain")
                .withCardOnBattlefield(1, "Centaur Courser")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Swamp")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            val cast = game.castSpellWithAdditionalSacrifice(
                playerNumber = 1,
                spellName = "Reckoner's Bargain",
                sacrificeCreatureName = "Centaur Courser",
            )
            withClue("Reckoner's Bargain should cast: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            withClue("the sacrificed creature left before resolution") {
                game.findPermanent("Centaur Courser") shouldBe null
            }
            withClue("last-known mana value 3 determines the life gain") {
                game.getLifeTotal(1) shouldBe 23
            }
            withClue("casting spends one card and drawing two nets one card") {
                game.handSize(1) shouldBe handBefore + 1
            }
        }
    }
}
