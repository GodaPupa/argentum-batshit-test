package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe

class KrarkClanShamanScenarioTest : ScenarioTestBase() {
    init {
        test("artifact is sacrificed before one damage hits every nonflier") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardOnBattlefield(2, "Ornithopter")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val shaman = game.findPermanent("Krark-Clan Shaman")!!
            val fodder = game.findPermanent("Bonesplitter")!!
            val ability = cardRegistry.requireCard("Krark-Clan Shaman").activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, shaman, ability,
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder)))).error shouldBe null
            game.resolveStack(); game.checkStateBasedActions()
            game.isInGraveyard(1, "Bonesplitter") shouldBe true
            game.isInGraveyard(1, "Krark-Clan Shaman") shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.isOnBattlefield("Ornithopter") shouldBe true
        }
    }
}
