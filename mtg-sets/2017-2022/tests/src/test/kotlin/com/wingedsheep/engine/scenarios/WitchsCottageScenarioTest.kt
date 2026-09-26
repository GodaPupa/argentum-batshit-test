package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.matchers.shouldBe

/** Canonical ELD Cottage: three other Swamps for entry, actual untapped-entry trigger, optional return. */
class WitchsCottageScenarioTest : ScenarioTestBase() {
    private fun setup() = scenario().withPlayers("Cottage", "Opponent")
        .withCardInHand(1, "Witch's Cottage").withCardInGraveyard(1, "Grizzly Bears")
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
        .withRngSeed(0xFEC004).withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.advance() { resolveStack().forEach { it.error shouldBe null } }
    private fun TestGame.finish() {
        advance(); state.pendingDecision shouldBe null; state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }
    private fun TestGame.playCottage() {
        execute(PlayLand(player1Id, findCardsInHand(1, "Witch's Cottage").single())).error shouldBe null
    }
    private fun TestGame.targetBears() {
        (state.pendingDecision is ChooseTargetsDecision) shouldBe true
        selectTargets(findCardsInGraveyard(1, "Grizzly Bears")).error shouldBe null
    }
    private fun TestGame.chooseReturn(accept: Boolean) {
        advance(); (state.pendingDecision is YesNoDecision) shouldBe true
        answerYesNo(accept).error shouldBe null; finish()
    }

    init {
        test("two other Swamps are insufficient and the Cottage enters tapped without a trigger") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 2).build()
            game.playCottage(); game.finish()
            game.state.getEntity(game.findPermanent("Witch's Cottage")!!)!!.has<TappedComponent>() shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
        }

        test("three other Swamps permit untapped entry and the selected creature returns to library top") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 3).build()
            game.playCottage()
            game.state.getEntity(game.findPermanent("Witch's Cottage")!!)!!.has<TappedComponent>() shouldBe false
            game.targetBears(); game.chooseReturn(true)
            game.isInGraveyard(1, "Grizzly Bears") shouldBe false
            game.state.getLibrary(game.player1Id).first() shouldBe game.findCardsInLibrary(1, "Grizzly Bears").single()
        }

        test("a nonbasic Swamp counts toward the three other Swamps") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(1, "Geothermal Bog").build()
            game.playCottage()
            game.state.getEntity(game.findPermanent("Witch's Cottage")!!)!!.has<TappedComponent>() shouldBe false
            game.targetBears(); game.chooseReturn(true)
            game.isInGraveyard(1, "Grizzly Bears") shouldBe false
        }

        test("the return can be declined on resolution after choosing its target") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 3).build()
            game.playCottage(); game.targetBears(); game.chooseReturn(false)
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.findCardsInLibrary(1, "Grizzly Bears") shouldBe emptyList()
        }

        test("tapping the Cottage for black mana in response does not cancel its untapped-entry trigger") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 3).build()
            game.playCottage(); game.targetBears()
            game.execute(ActivateAbility(
                game.player1Id, game.findPermanent("Witch's Cottage")!!, AbilityId("intrinsic_mana_B")
            )).error shouldBe null
            game.state.getEntity(game.findPermanent("Witch's Cottage")!!)!!.has<TappedComponent>() shouldBe true
            game.chooseReturn(true)
            game.isInGraveyard(1, "Grizzly Bears") shouldBe false
        }

        test("graveyard exile before resolution prevents the targeted return") {
            val game = setup().withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInHand(1, "Cremate").build()
            game.playCottage(); game.targetBears()
            game.castSpellTargetingGraveyardCard(1, "Cremate", 1, "Grizzly Bears").error shouldBe null
            game.finish()
            game.isInExile(1, "Grizzly Bears") shouldBe true
            game.findCardsInLibrary(1, "Grizzly Bears") shouldBe emptyList()
        }
    }
}
