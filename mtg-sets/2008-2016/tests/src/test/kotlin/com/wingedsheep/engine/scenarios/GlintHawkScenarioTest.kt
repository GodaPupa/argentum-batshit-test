package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.atq.cards.AshnodsTransmogrant
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Canonical SOM Glint Hawk: resolution-time, non-targeting artifact payment and sacrifice. */
class GlintHawkScenarioTest : ScenarioTestBase() {
    private fun setup() = scenario().withPlayers("Hawk", "Opponent")
        .withCardInHand(1, "Glint Hawk").withLandsOnBattlefield(1, "Plains", 3)
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
        .withRngSeed(0xFEC001).withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.advance() { resolveStack().forEach { it.error shouldBe null } }
    private fun TestGame.finish() {
        advance(); state.pendingDecision shouldBe null; state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }
    private fun TestGame.castHawk() { castSpell(1, "Glint Hawk").error shouldBe null; advance() }

    init {
        test("returns an artifact you control without targeting and keeps the Hawk") {
            val game = setup().withCardOnBattlefield(1, "Ornithopter").build()
            val artifact = game.findPermanent("Ornithopter")!!
            game.castHawk()
            val choice = game.state.pendingDecision as? SelectCardsDecision ?: error("Expected artifact payment")
            choice.options shouldBe listOf(artifact)
            game.selectCards(listOf(artifact)).error shouldBe null
            game.finish()
            game.isInHand(1, "Ornithopter") shouldBe true
            game.isOnBattlefield("Glint Hawk") shouldBe true
        }

        test("declining a legal return sacrifices the Hawk and leaves the artifact") {
            val game = setup().withCardOnBattlefield(1, "Ornithopter").build()
            game.castHawk()
            (game.state.pendingDecision is SelectCardsDecision) shouldBe true
            game.selectCards(emptyList()).error shouldBe null
            game.finish()
            game.isInGraveyard(1, "Glint Hawk") shouldBe true
            game.isOnBattlefield("Ornithopter") shouldBe true
        }

        test("an opponent's artifact cannot pay and no legal artifact sacrifices the Hawk") {
            val game = setup().withCardOnBattlefield(2, "Ornithopter").build()
            game.castHawk(); game.finish()
            game.isInGraveyard(1, "Glint Hawk") shouldBe true
            game.isOnBattlefield("Ornithopter") shouldBe true
        }

        test("removing the only artifact before the trigger resolves forces sacrifice") {
            val game = setup().withCardOnBattlefield(1, "Ornithopter")
                .withCardInHand(1, "Disenchant").build()
            val artifact = game.findPermanent("Ornithopter")!!
            game.castSpell(1, "Glint Hawk").error shouldBe null
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.stack.size shouldBe 1
            game.castSpell(1, "Disenchant", artifact).error shouldBe null
            game.finish()
            game.isInGraveyard(1, "Ornithopter") shouldBe true
            game.isInGraveyard(1, "Glint Hawk") shouldBe true
        }

        test("a controlled artifact owned by the opponent returns to its owner's hand") {
            val game = setup().withCardOnBattlefield(2, "Ornithopter").build()
            val artifact = game.findPermanent("Ornithopter")!!
            game.state = game.state.updateEntity(artifact) { it.with(ControllerComponent(game.player1Id)) }
            game.castHawk()
            game.selectCards(listOf(artifact)).error shouldBe null
            game.finish()
            game.isInHand(2, "Ornithopter") shouldBe true
            game.isInHand(1, "Ornithopter") shouldBe false
            game.isOnBattlefield("Glint Hawk") shouldBe true
        }

        test("returning an artifact token pays but the departed token does not remain in hand") {
            val game = setup().withCardOnBattlefield(1, "Treasure", isToken = true).build()
            val token = game.findPermanent("Treasure")!!
            game.castHawk()
            game.selectCards(listOf(token)).error shouldBe null
            game.finish()
            game.isOnBattlefield("Glint Hawk") shouldBe true
            game.isOnBattlefield("Treasure") shouldBe false
            game.isInHand(1, "Treasure") shouldBe false
        }

        test("the Hawk can return itself if it becomes an artifact before the trigger resolves") {
            val game = setup().withCardOnBattlefield(1, "Ashnod's Transmogrant").build()
            game.castSpell(1, "Glint Hawk").error shouldBe null
            game.passPriority().error shouldBe null; game.passPriority().error shouldBe null
            val hawk = game.findPermanent("Glint Hawk")!!
            game.execute(ActivateAbility(
                game.player1Id, game.findPermanent("Ashnod's Transmogrant")!!,
                AshnodsTransmogrant.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(hawk))
            )).error shouldBe null
            game.advance()
            val choice = game.state.pendingDecision as? SelectCardsDecision ?: error("Expected artifact payment")
            choice.options shouldBe listOf(hawk)
            game.selectCards(listOf(hawk)).error shouldBe null; game.finish()
            game.isInHand(1, "Glint Hawk") shouldBe true
            game.isInGraveyard(1, "Glint Hawk") shouldBe false
            game.isInGraveyard(1, "Ashnod's Transmogrant") shouldBe true
        }

        test("the old entry trigger cannot sacrifice a Hawk that has left and returned") {
            val game = setup().withCardOnBattlefield(1, "Ornithopter")
                .withCardInHand(1, "Momentary Blink").build()
            val artifact = game.findPermanent("Ornithopter")!!
            game.castSpell(1, "Glint Hawk").error shouldBe null
            game.passPriority().error shouldBe null; game.passPriority().error shouldBe null
            game.castSpell(1, "Momentary Blink", game.findPermanent("Glint Hawk")!!).error shouldBe null
            game.advance()
            (game.state.pendingDecision is SelectCardsDecision) shouldBe true
            game.selectCards(listOf(artifact)).error shouldBe null
            game.finish()
            game.isInHand(1, "Ornithopter") shouldBe true
            game.isOnBattlefield("Glint Hawk") shouldBe true
            game.isInGraveyard(1, "Glint Hawk") shouldBe false
        }
    }
}
