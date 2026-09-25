package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/** Synthetic card scenarios only; these do not initialize any frozen experimental deck. */
class PongifyScenarioTest : ScenarioTestBase() {

    private fun TestGame.resolveWithoutErrors() {
        resolveStack().forEach { it.error shouldBe null }
        state.stack.isEmpty() shouldBe true
        hasPendingDecision() shouldBe false
    }

    private fun TestGame.assertOnlyToken(controllerNumber: Int) {
        val tokens = state.getBattlefield().filter {
            state.getEntity(it)?.has<TokenComponent>() == true
        }
        tokens shouldHaveSize 1
        val token = tokens.single()
        val expectedController = if (controllerNumber == 1) player1Id else player2Id
        val projected = state.projectedState
        projected.getController(token) shouldBe expectedController
        state.getEntity(token)?.get<OwnerComponent>()?.playerId shouldBe expectedController
        projected.isCreature(token) shouldBe true
        projected.getPower(token) shouldBe 3
        projected.getToughness(token) shouldBe 3
        projected.getColors(token) shouldBe setOf("GREEN")
        projected.getSubtypes(token) shouldBe setOf("Ape")
    }

    init {
        test("destroys an opposing creature and gives its controller a 3/3 green Ape") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Pongify")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Pongify", target).error shouldBe null
            game.resolveWithoutErrors()

            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Pongify") shouldBe true
            game.assertOnlyToken(2)
        }

        test("the creature's projected controller receives the token when its owner differs") {
            val game = scenario()
                .withPlayers("Caster", "Owner")
                .withCardInHand(1, "Pongify")
                .withCardOnBattlefield(2, "Frogmite")
                .withCardAttachedTo(1, "Domineer", "Frogmite")
                .withLandsOnBattlefield(1, "Island", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanent("Frogmite")!!
            game.state.projectedState.getController(target) shouldBe game.player1Id
            game.state.getEntity(target)?.get<OwnerComponent>()?.playerId shouldBe game.player2Id
            game.castSpell(1, "Pongify", target).error shouldBe null
            game.resolveWithoutErrors()

            game.isInGraveyard(2, "Frogmite") shouldBe true
            game.assertOnlyToken(1)
        }

        test("an indestructible creature survives and its controller still receives the token") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Pongify")
                .withCardOnBattlefield(2, "Darksteel Myr")
                .withLandsOnBattlefield(1, "Island", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanent("Darksteel Myr")!!
            game.state.projectedState.hasKeyword(target, Keyword.INDESTRUCTIBLE) shouldBe true
            game.castSpell(1, "Pongify", target).error shouldBe null
            game.resolveWithoutErrors()

            game.findPermanent("Darksteel Myr") shouldBe target
            game.isInGraveyard(2, "Darksteel Myr") shouldBe false
            game.assertOnlyToken(2)
        }

        test("a target that dies before resolution prevents both destruction and token creation") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Pongify")
                .withCardInHand(1, "Lightning Bolt")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Island", 1)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Pongify", target).error shouldBe null
            game.castSpell(1, "Lightning Bolt", target).error shouldBe null
            game.resolveWithoutErrors()

            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Pongify") shouldBe true
            game.state.getBattlefield().any {
                game.state.getEntity(it)?.has<TokenComponent>() == true
            } shouldBe false
        }

        test("a real regeneration shield does not prevent destruction or the replacement token") {
            val game = scenario()
                .withPlayers("Caster", "Opponent")
                .withCardInHand(1, "Pongify")
                .withCardOnBattlefield(1, "Welding Jar")
                .withCardOnBattlefield(2, "Frogmite")
                .withLandsOnBattlefield(1, "Island", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val jar = game.findPermanent("Welding Jar")!!
            val target = game.findPermanent("Frogmite")!!
            val regeneration = cardRegistry.getCard("Welding Jar")!!.activatedAbilities.single().id
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = jar,
                    abilityId = regeneration,
                    targets = listOf(ChosenTarget.Permanent(target))
                )
            ).error shouldBe null
            game.resolveWithoutErrors()
            game.state.floatingEffects.any {
                it.effect.modification is SerializableModification.RegenerationShield &&
                    target in it.effect.affectedEntities
            } shouldBe true

            game.castSpell(1, "Pongify", target).error shouldBe null
            game.resolveWithoutErrors()

            game.isInGraveyard(2, "Frogmite") shouldBe true
            game.findPermanent("Frogmite") shouldBe null
            game.assertOnlyToken(2)
        }
    }
}

