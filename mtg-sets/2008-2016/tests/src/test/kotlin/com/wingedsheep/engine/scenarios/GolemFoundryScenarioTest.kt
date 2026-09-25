package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.som.cards.GolemFoundry
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed, excluded engine fixtures for the existing Foundry; no candidate games are loaded. */
class GolemFoundryScenarioTest : ScenarioTestBase() {
    private fun charges(game: TestGame, foundry: EntityId): Int =
        game.state.getEntity(foundry)!!.get<CountersComponent>()?.getCount(Counters.CHARGE) ?: 0

    private fun tokens(game: TestGame): List<EntityId> = game.state.getBattlefield().filter {
        game.state.getEntity(it)!!.has<TokenComponent>() && game.state.projectedState.hasSubtype(it, "Golem")
    }

    init {
        test("three accepted artifact cast triggers fund a new Foundry's exact token ability") {
            val game = scenario().withPlayers().withRngSeed(0x464f554e445259L)
                .withCardInHand(1, "Golem Foundry")
                .withCardsInHand(1, "Bonesplitter", 3)
                .withLandsOnBattlefield(1, "Forest", 6)
                .build()
            game.castSpell(1, "Golem Foundry").error shouldBe null
            game.resolveStack()
            val foundry = game.findPermanent("Golem Foundry")!!
            // A Foundry spell does not trigger the ability of the permanent it will become.
            charges(game, foundry) shouldBe 0
            game.hasPendingDecision() shouldBe false

            repeat(3) { index ->
                game.castSpell(1, "Bonesplitter").error shouldBe null
                game.state.stack.size shouldBe 2
                game.resolveStack()
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
                game.findPermanents("Bonesplitter").size shouldBe index
                game.answerYesNo(true).error shouldBe null
                charges(game, foundry) shouldBe index + 1
                // The cast trigger resolves above the artifact spell.
                game.findPermanents("Bonesplitter").size shouldBe index
                game.state.stack.size shouldBe 1
                game.resolveStack()
                game.findPermanents("Bonesplitter").size shouldBe index + 1
            }

            game.execute(ActivateAbility(game.player1Id, foundry, GolemFoundry.activatedAbilities.single().id)).error shouldBe null
            charges(game, foundry) shouldBe 0
            tokens(game) shouldBe emptyList()
            game.state.getEntity(foundry)!!.has<TappedComponent>() shouldBe false
            game.state.stack.size shouldBe 1
            game.resolveStack()

            val token = tokens(game).single()
            val projected = game.state.projectedState
            projected.getPower(token) shouldBe 3
            projected.getToughness(token) shouldBe 3
            projected.hasType(token, "ARTIFACT") shouldBe true
            projected.isCreature(token) shouldBe true
            projected.getColors(token) shouldBe emptySet()
            game.state.getEntity(token)!!.get<ControllerComponent>()!!.playerId shouldBe game.player1Id
            game.state.getEntity(token)!!.get<CardComponent>()!!.ownerId shouldBe game.player1Id
            game.state.getEntity(token)!!.has<SummoningSicknessComponent>() shouldBe true
            // The artifact token entered without being cast, so it creates no new charge.
            charges(game, foundry) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.execute(DeclareAttackers(game.player1Id, mapOf(token to game.player2Id))).isSuccess shouldBe false
        }

        test("declining a cast trigger leaves no counter when the artifact subsequently enters") {
            val game = scenario().withPlayers().withRngSeed(0x464f554e445259L)
                .withCardOnBattlefield(1, "Golem Foundry")
                .withCardInHand(1, "Memnite")
                .build()
            val foundry = game.findPermanent("Golem Foundry")!!
            game.castSpell(1, "Memnite").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(false).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Memnite") shouldBe true
            charges(game, foundry) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }

        test("an opponent's artifact spell does not trigger this Foundry") {
            val game = scenario().withPlayers().withRngSeed(0x464f554e445259L)
                .withCardOnBattlefield(1, "Golem Foundry")
                .withCardInHand(2, "Memnite")
                .withActivePlayer(2)
                .build()
            val foundry = game.findPermanent("Golem Foundry")!!
            game.castSpell(2, "Memnite").error shouldBe null
            game.state.stack.size shouldBe 1
            game.resolveStack()

            charges(game, foundry) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.isOnBattlefield("Memnite") shouldBe true
        }

        test("a nonartifact spell and the artifact it returns do not trigger Foundry") {
            val game = scenario().withPlayers().withRngSeed(0x464f554e445259L)
                .withCardOnBattlefield(1, "Golem Foundry")
                .withCardInGraveyard(1, "Myr Retriever")
                .withCardInHand(1, "Unearth")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .build()
            val foundry = game.findPermanent("Golem Foundry")!!
            val retriever = game.findCardsInGraveyard(1, "Myr Retriever").single()
            game.castSpellTargetingGraveyardCard(1, "Unearth", listOf(retriever)).error shouldBe null
            game.state.stack.size shouldBe 1
            game.resolveStack()

            game.isOnBattlefield("Myr Retriever") shouldBe true
            game.isInGraveyard(1, "Unearth") shouldBe true
            charges(game, foundry) shouldBe 0
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }

        test("a tapped Foundry removes exactly three counters without paying a tap cost") {
            val game = scenario().withPlayers().withRngSeed(0x464f554e445259L)
                .withCardOnBattlefield(1, "Golem Foundry", tapped = true, enteredThisTurn = true)
                .build()
            val foundry = game.findPermanent("Golem Foundry")!!
            game.state = game.state.updateEntity(foundry) { it.with(CountersComponent(mapOf(Counters.CHARGE to 4))) }

            game.execute(ActivateAbility(game.player1Id, foundry, GolemFoundry.activatedAbilities.single().id)).error shouldBe null
            charges(game, foundry) shouldBe 1
            game.state.getEntity(foundry)!!.has<TappedComponent>() shouldBe true
            game.resolveStack()

            tokens(game).size shouldBe 1
            charges(game, foundry) shouldBe 1
            game.hasPendingDecision() shouldBe false
        }

        test("fewer than three charge counters cannot create a token or partially pay") {
            val game = scenario().withPlayers().withRngSeed(0x464f554e445259L)
                .withCardOnBattlefield(1, "Golem Foundry")
                .build()
            val foundry = game.findPermanent("Golem Foundry")!!
            game.state = game.state.updateEntity(foundry) { it.with(CountersComponent(mapOf(Counters.CHARGE to 2))) }
            val before = game.state

            game.execute(ActivateAbility(game.player1Id, foundry, GolemFoundry.activatedAbilities.single().id)).isSuccess shouldBe false

            game.state shouldBe before
            charges(game, foundry) shouldBe 2
            tokens(game) shouldBe emptyList()
        }
    }
}
