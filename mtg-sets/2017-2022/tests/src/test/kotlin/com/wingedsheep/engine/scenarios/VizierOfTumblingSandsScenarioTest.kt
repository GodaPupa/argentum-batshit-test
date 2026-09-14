package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class VizierOfTumblingSandsScenarioTest : ScenarioTestBase() {
    private val abilityId = cardRegistry.getCard("Vizier of Tumbling Sands")!!.activatedAbilities.single().id

    init {
        test("its tap ability untaps another target permanent") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(
                    1,
                    "Vizier of Tumbling Sands",
                    tapped = false,
                    summoningSickness = false,
                )
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val vizier = game.findPermanent("Vizier of Tumbling Sands")!!
            val forest = game.findPermanent("Forest")!!

            game.execute(
                ActivateAbility(game.player1Id, vizier, abilityId, targets = listOf(forest))
            ).error shouldBe null
            game.resolveStack()

            game.state.getEntity(vizier)?.get<TappedComponent>() shouldBe TappedComponent
            game.state.getEntity(forest)?.get<TappedComponent>() shouldBe null
        }

        test("cycling it untaps a target permanent and still draws") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Vizier of Tumbling Sands")
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInLibrary(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val forest = game.findPermanent("Forest")!!

            val cycle = game.cycleCard(1, "Vizier of Tumbling Sands")
            withClue("cycling should succeed: ${cycle.error}") { cycle.error shouldBe null }
            game.autoPayIfAsked()
            if (game.getPendingDecision() is ChooseTargetsDecision) game.selectTargets(listOf(forest))
            game.resolveStack()

            game.state.getEntity(forest)?.get<TappedComponent>() shouldBe null
            game.isInGraveyard(1, "Vizier of Tumbling Sands") shouldBe true
            game.isInHand(1, "Grizzly Bears") shouldBe true
        }
    }
}
