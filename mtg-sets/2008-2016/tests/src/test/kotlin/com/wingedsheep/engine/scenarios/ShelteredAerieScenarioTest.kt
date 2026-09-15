package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class ShelteredAerieScenarioTest : ScenarioTestBase() {

    init {
        test("Sheltered Aerie grants its land a second mana ability") {
            val plain = scenario()
                .withPlayers("Player", "Opponent")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val plainForest = plain.findPermanent("Forest")!!
            val before = plain.getLegalActions(1)
                .count { (it.action as? ActivateAbility)?.sourceId == plainForest }

            val enchanted = scenario()
                .withPlayers("Player", "Opponent")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardAttachedTo(1, "Sheltered Aerie", "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val enchantedForest = enchanted.findPermanent("Forest")!!
            val after = enchanted.getLegalActions(1)
                .count { (it.action as? ActivateAbility)?.sourceId == enchantedForest }

            after shouldBe before + 1
        }
    }
}
