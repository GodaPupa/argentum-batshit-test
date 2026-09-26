package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Canonical ZEN Journey: separate linked triggers, target failure, bounce and ownership. */
class JourneyToNowhereScenarioTest : ScenarioTestBase() {
    private fun setup() = scenario().withPlayers("Journey", "Opponent")
        .withCardInHand(1, "Journey to Nowhere").withCardInHand(1, "Disenchant")
        .withLandsOnBattlefield(1, "Plains", 4).withLandsOnBattlefield(1, "Island", 1)
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
        .withRngSeed(0xFEC003).withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.advance() { resolveStack().forEach { it.error shouldBe null } }
    private fun TestGame.finish() {
        advance(); state.pendingDecision shouldBe null; state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }
    private fun TestGame.announceExile(target: EntityId) {
        castSpell(1, "Journey to Nowhere").error shouldBe null; advance()
        (state.pendingDecision is ChooseTargetsDecision) shouldBe true
        selectTargets(listOf(target)).error shouldBe null
    }
    private fun TestGame.destroyJourney() {
        castSpell(1, "Disenchant", findPermanent("Journey to Nowhere")!!).error shouldBe null
        finish()
    }

    init {
        test("exiles a creature then returns it when the Journey is destroyed") {
            val game = setup().withCardOnBattlefield(2, "Craw Wurm").build()
            game.announceExile(game.findPermanent("Craw Wurm")!!); game.finish()
            game.isInExile(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe false
            game.destroyJourney()
            game.isInExile(2, "Craw Wurm") shouldBe false
            game.isOnBattlefield("Craw Wurm") shouldBe true
        }

        test("Journey leaving before its exile trigger resolves leaves the later exile without a return") {
            val game = setup().withCardOnBattlefield(2, "Craw Wurm").build()
            game.announceExile(game.findPermanent("Craw Wurm")!!)
            game.state.stack.size shouldBe 1
            game.destroyJourney()
            game.isInGraveyard(1, "Journey to Nowhere") shouldBe true
            game.isInExile(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe false
        }

        test("bouncing the Journey with Kor Skyfisher also returns its exiled creature") {
            val game = setup().withCardOnBattlefield(2, "Craw Wurm")
                .withCardInHand(1, "Kor Skyfisher").build()
            game.announceExile(game.findPermanent("Craw Wurm")!!); game.finish()
            game.castSpell(1, "Kor Skyfisher").error shouldBe null; game.advance()
            (game.state.pendingDecision is SelectCardsDecision) shouldBe true
            game.selectCards(listOf(game.findPermanent("Journey to Nowhere")!!)).error shouldBe null
            game.finish()
            game.isInHand(1, "Journey to Nowhere") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.isOnBattlefield("Kor Skyfisher") shouldBe true
        }

        test("a target bounced in response is not exiled when the trigger resolves") {
            val game = setup().withCardOnBattlefield(2, "Craw Wurm")
                .withCardInHand(1, "Unsummon").build()
            val target = game.findPermanent("Craw Wurm")!!
            game.announceExile(target)
            game.castSpell(1, "Unsummon", target).error shouldBe null; game.finish()
            game.isInHand(2, "Craw Wurm") shouldBe true
            game.isInExile(2, "Craw Wurm") shouldBe false
            game.destroyJourney()
            game.isInHand(2, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe false
        }

        test("a stolen creature returns under its owner's control") {
            val game = setup().withCardOnBattlefield(2, "Craw Wurm").build()
            val target = game.findPermanent("Craw Wurm")!!
            game.state = game.state.updateEntity(target) { it.with(ControllerComponent(game.player1Id)) }
            game.announceExile(target); game.finish(); game.destroyJourney()
            val returned = game.findPermanent("Craw Wurm")!!
            game.state.getEntity(returned)!!.get<ControllerComponent>()!!.playerId shouldBe game.player2Id
        }

        test("the exile trigger must choose a legal target even if only your own creature is available") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            game.castSpell(1, "Journey to Nowhere").error shouldBe null; game.advance()
            (game.state.pendingDecision is ChooseTargetsDecision) shouldBe true
            val before = game.state
            val rejected = game.skipTargets()
            rejected.error.shouldNotBeNull()
            rejected.state shouldBe before
            game.state shouldBe before
            game.selectTargets(listOf(game.findPermanent("Grizzly Bears")!!)).error shouldBe null
            game.finish()
            game.isInExile(1, "Grizzly Bears") shouldBe true
        }

        test("an exiled creature token ceases to exist and cannot return") {
            val game = setup().withCardOnBattlefield(2, "Grizzly Bears", isToken = true).build()
            game.announceExile(game.findPermanent("Grizzly Bears")!!); game.finish()
            game.isInExile(2, "Grizzly Bears") shouldBe false
            game.destroyJourney()
            game.isOnBattlefield("Grizzly Bears") shouldBe false
        }
    }
}
