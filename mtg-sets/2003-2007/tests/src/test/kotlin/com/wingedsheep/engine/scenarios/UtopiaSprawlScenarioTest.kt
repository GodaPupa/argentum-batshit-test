package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.battlefield.chosenColor
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Actual card actions in fixed regression fixtures, excluded from Phase 2 sampled gameplay. */
class UtopiaSprawlScenarioTest : ScenarioTestBase() {
    private fun fixture(hostName: String = "Forest", hostPlayer: Int = 1): ScenarioBuilder = scenario()
        .withPlayers("Aura controller", "Opponent")
        .withRngSeed(9250925012L)
        .withCardOnBattlefield(hostPlayer, hostName)
        .withCardOnBattlefield(1, "Llanowar Elves")
        .withCardInHand(1, "Utopia Sprawl")

    private fun attachByCasting(game: TestGame, host: EntityId, color: Color = Color.BLUE): EntityId {
        val aura = game.findCardsInHand(1, "Utopia Sprawl").single()
        game.execute(CastSpell(
            game.player1Id, aura, listOf(ChosenTarget.Permanent(host)),
            paymentStrategy = PaymentStrategy.Explicit(listOf(game.findPermanent("Llanowar Elves")!!))
        )).error shouldBe null
        game.hasPendingDecision() shouldBe false
        game.resolveStack()
        game.isOnBattlefield("Utopia Sprawl") shouldBe false
        val choice = game.getPendingDecision().shouldBeInstanceOf<ChooseColorDecision>()
        choice.playerId shouldBe game.player1Id
        choice.availableColors shouldBe Color.entries.toSet()
        game.submitDecision(ColorChosenResponse(choice.id, color)).error shouldBe null
        game.resolveStack()
        game.state.getEntity(aura)!!.get<AttachedToComponent>()!!.targetId shouldBe host
        game.state.getEntity(aura)!!.chosenColor() shouldBe color
        game.state.getEntity(host)!!.has<TappedComponent>() shouldBe false
        return aura
    }

    init {
        test("color is fixed as the Aura enters and every tap preserves the Forest's original green") {
            val game = fixture().withCardOnBattlefield(1, "Voyaging Satyr").build()
            val forest = game.findPermanent("Forest")!!
            val aura = attachByCasting(game, forest, Color.BLUE)
            game.execute(ActivateAbility(
                game.player1Id, forest, AbilityId.intrinsicMana('G')
            )).error shouldBe null
            var pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.green shouldBe 1
            pool.blue shouldBe 1
            pool.red shouldBe 0
            pool.total shouldBe 2
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
            game.state.projectedState.getColors(forest) shouldBe emptySet()

            game.execute(ActivateAbility(
                game.player1Id, game.findPermanent("Voyaging Satyr")!!,
                cardRegistry.getCard("Voyaging Satyr")!!.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(forest))
            )).error shouldBe null
            game.resolveStack()
            game.execute(ActivateAbility(game.player1Id, forest, AbilityId.intrinsicMana('G'))).error shouldBe null

            pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.green shouldBe 2
            pool.blue shouldBe 2
            pool.total shouldBe 4
            game.state.getEntity(aura)!!.chosenColor() shouldBe Color.BLUE
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a nonbasic Forest is legal and retains its choice of original land mana") {
            val game = fixture("Temple Garden").build()
            val garden = game.findPermanent("Temple Garden")!!
            game.state.projectedState.hasSubtype(garden, "Forest") shouldBe true
            game.state.projectedState.hasType(garden, "BASIC") shouldBe false
            attachByCasting(game, garden, Color.RED)
            game.execute(ActivateAbility(game.player1Id, garden, AbilityId.intrinsicMana('W'))).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.white shouldBe 1
            pool.red shouldBe 1
            pool.green shouldBe 0
            pool.total shouldBe 2
            game.state.projectedState.getColors(garden) shouldBe emptySet()
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a non-Forest land and a creature are illegal enchant targets") {
            val game = fixture()
                .withCardOnBattlefield(1, "Island")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .build()
            val before = game.state
            for (name in listOf("Island", "Grizzly Bears")) {
                game.castSpell(1, "Utopia Sprawl", game.findPermanent(name)!!).isSuccess shouldBe false
                game.state shouldBe before
            }
            game.isInHand(1, "Utopia Sprawl") shouldBe true
            game.hasPendingDecision() shouldBe false
        }

        test("the opponent controlling the enchanted Forest receives the fixed-color bonus") {
            val game = fixture(hostPlayer = 2).build()
            val forest = game.findPermanent("Forest")!!
            attachByCasting(game, forest, Color.BLUE)
            game.passPriority().error shouldBe null
            game.execute(ActivateAbility(game.player2Id, forest, AbilityId.intrinsicMana('G'))).error shouldBe null

            val pool = game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!
            pool.green shouldBe 1
            pool.blue shouldBe 1
            pool.total shouldBe 2
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a tap from Icy Manipulator creates neither base nor additional mana") {
            val game = fixture()
                .withCardOnBattlefield(2, "Icy Manipulator")
                .withLandsOnBattlefield(2, "Plains", 1)
                .build()
            val forest = game.findPermanent("Forest")!!
            attachByCasting(game, forest)
            game.passPriority().error shouldBe null
            game.execute(ActivateAbility(
                game.player2Id, game.findPermanent("Icy Manipulator")!!,
                cardRegistry.getCard("Icy Manipulator")!!.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(forest))
            )).error shouldBe null
            game.resolveStack()

            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.stack.size shouldBe 0
        }

        test("automatic payment uses the chosen blue with the Forest's green to cast Merfolk Looter") {
            val game = fixture().withCardInHand(1, "Merfolk Looter").build()
            val forest = game.findPermanent("Forest")!!
            attachByCasting(game, forest, Color.BLUE)
            game.getLegalActions(1).any {
                it.actionType == "CastSpell" && it.description.contains("Merfolk Looter") && it.isAffordable
            } shouldBe true
            game.castSpell(1, "Merfolk Looter").error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Merfolk Looter") shouldBe true
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a Forest removed before resolution makes the Aura fail without an entry color choice") {
            val game = fixture()
                .withCardInHand(2, "Boomerang")
                .withLandsOnBattlefield(2, "Island", 2)
                .build()
            val forest = game.findPermanent("Forest")!!
            val aura = game.findCardsInHand(1, "Utopia Sprawl").single()
            game.execute(CastSpell(
                game.player1Id, aura, listOf(ChosenTarget.Permanent(forest)),
                paymentStrategy = PaymentStrategy.Explicit(listOf(game.findPermanent("Llanowar Elves")!!))
            )).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Boomerang", forest).error shouldBe null
            game.resolveStack()

            game.isInHand(1, "Forest") shouldBe true
            game.isInGraveyard(1, "Utopia Sprawl") shouldBe true
            game.isOnBattlefield("Utopia Sprawl") shouldBe false
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 0
        }

        test("losing the Forest subtype removes Utopia Sprawl and its mana bonus") {
            val game = fixture()
                .withCardInHand(1, "Sea's Claim")
                .withLandsOnBattlefield(1, "Island", 1)
                .build()
            val forest = game.findPermanent("Forest")!!
            attachByCasting(game, forest, Color.RED)
            val claim = game.findCardsInHand(1, "Sea's Claim").single()
            val island = game.findPermanent("Island")!!
            game.execute(CastSpell(
                game.player1Id, claim, listOf(ChosenTarget.Permanent(forest)),
                paymentStrategy = PaymentStrategy.Explicit(listOf(island))
            )).error shouldBe null
            game.resolveStack()

            game.state.projectedState.hasSubtype(forest, "Forest") shouldBe false
            game.state.projectedState.hasSubtype(forest, "Island") shouldBe true
            game.isInGraveyard(1, "Utopia Sprawl") shouldBe true
            game.isOnBattlefield("Sea's Claim") shouldBe true
            game.execute(ActivateAbility(game.player1Id, forest, AbilityId.intrinsicMana('U'))).error shouldBe null
            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.blue shouldBe 1
            pool.red shouldBe 0
            pool.total shouldBe 1
            game.state.stack.size shouldBe 0
        }
    }
}
