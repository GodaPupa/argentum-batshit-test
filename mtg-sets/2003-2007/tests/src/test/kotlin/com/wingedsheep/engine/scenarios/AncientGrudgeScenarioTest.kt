package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class AncientGrudgeScenarioTest : ScenarioTestBase() {
    init {
        test("destroys target artifact for its normal cost") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Ancient Grudge")
                .withCardOnBattlefield(2, "Ichor Wellspring")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanent("Ichor Wellspring")!!
            game.castSpell(1, "Ancient Grudge", target).error shouldBe null
            game.resolveStack()

            game.findPermanent("Ichor Wellspring") shouldBe null
            game.isInGraveyard(2, "Ichor Wellspring") shouldBe true
            game.isInGraveyard(1, "Ancient Grudge") shouldBe true
        }

        test("flashback green destroys an artifact and exiles Ancient Grudge") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardInGraveyard(1, "Ancient Grudge")
                .withCardOnBattlefield(2, "Ichor Wellspring")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val grudge = game.findCardsInGraveyard(1, "Ancient Grudge").single()
            val target = game.findPermanent("Ichor Wellspring")!!
            val cast = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = grudge,
                    targets = listOf(ChosenTarget.Permanent(target)),
                    useAlternativeCost = true,
                    alternativeCostType = AlternativeCostType.FLASHBACK,
                ),
            )
            withClue("flashback {G}: ${cast.error}") { cast.error shouldBe null }
            game.resolveStack()

            game.findPermanent("Ichor Wellspring") shouldBe null
            game.isInExile(1, "Ancient Grudge") shouldBe true
            game.isInGraveyard(1, "Ancient Grudge") shouldBe false
        }
    }
}
