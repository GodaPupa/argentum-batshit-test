package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.matchers.shouldBe

/** Actual card actions in fixed regression fixtures, excluded from Phase 2 sampled gameplay. */
class WildGrowthScenarioTest : ScenarioTestBase() {
    private fun fixture(hostName: String = "Island", hostPlayer: Int = 1): ScenarioBuilder = scenario()
        .withPlayers("Aura controller", "Opponent")
        .withRngSeed(9250925011L)
        .withCardOnBattlefield(hostPlayer, hostName)
        .withCardOnBattlefield(1, "Llanowar Elves")
        .withCardInHand(1, "Wild Growth")

    private fun attachByCasting(game: TestGame, host: EntityId): EntityId {
        val aura = game.findCardsInHand(1, "Wild Growth").single()
        val payer = game.findPermanent("Llanowar Elves")!!
        game.execute(CastSpell(
            game.player1Id, aura, listOf(ChosenTarget.Permanent(host)),
            paymentStrategy = PaymentStrategy.Explicit(listOf(payer))
        )).error shouldBe null
        game.resolveStack()
        game.state.getEntity(aura)!!.get<AttachedToComponent>()!!.targetId shouldBe host
        game.state.getEntity(host)!!.has<TappedComponent>() shouldBe false
        game.hasPendingDecision() shouldBe false
        return aura
    }

    init {
        for ((name, symbol) in listOf("Island" to 'U', "Tropical Island" to 'G')) {
            test("$name keeps its original mana and immediately adds one green from Wild Growth") {
                val game = fixture(name).build()
                val land = game.findPermanent(name)!!
                attachByCasting(game, land)
                game.execute(ActivateAbility(game.player1Id, land, AbilityId.intrinsicMana(symbol))).error shouldBe null

                val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                pool.green shouldBe if (symbol == 'G') 2 else 1
                pool.blue shouldBe if (symbol == 'U') 1 else 0
                pool.total shouldBe 2
                game.state.projectedState.getColors(land) shouldBe emptySet()
                game.state.getEntity(land)!!.has<TappedComponent>() shouldBe true
                game.state.stack.size shouldBe 0
                game.hasPendingDecision() shouldBe false
            }
        }

        test("enchanting an opponent's land gives the additional green to that opponent") {
            val game = fixture("Mountain", hostPlayer = 2).build()
            val mountain = game.findPermanent("Mountain")!!
            attachByCasting(game, mountain)
            game.passPriority().error shouldBe null
            game.execute(ActivateAbility(game.player2Id, mountain, AbilityId.intrinsicMana('R'))).error shouldBe null

            val opponentPool = game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!
            opponentPool.red shouldBe 1
            opponentPool.green shouldBe 1
            opponentPool.total shouldBe 2
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("Icy Manipulator tapping the enchanted land does not produce mana") {
            val game = fixture()
                .withCardOnBattlefield(2, "Icy Manipulator")
                .withLandsOnBattlefield(2, "Plains", 1)
                .build()
            val island = game.findPermanent("Island")!!
            attachByCasting(game, island)
            game.passPriority().error shouldBe null
            game.execute(ActivateAbility(
                game.player2Id, game.findPermanent("Icy Manipulator")!!,
                cardRegistry.getCard("Icy Manipulator")!!.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(island))
            )).error shouldBe null
            game.state.getEntity(island)!!.has<TappedComponent>() shouldBe false
            game.resolveStack()

            game.state.getEntity(island)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.stack.size shouldBe 0
        }

        test("automatic payment uses an Island's bonus green to cast Grizzly Bears") {
            val game = fixture().withCardInHand(1, "Grizzly Bears").build()
            val island = game.findPermanent("Island")!!
            attachByCasting(game, island)
            game.getLegalActions(1).any {
                it.actionType == "CastSpell" && it.description.contains("Grizzly Bears") && it.isAffordable
            } shouldBe true

            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.state.getEntity(island)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a creature is not a legal target for enchant land") {
            val game = fixture().withCardOnBattlefield(1, "Grizzly Bears").build()
            val before = game.state
            game.castSpell(1, "Wild Growth", game.findPermanent("Grizzly Bears")!!).isSuccess shouldBe false

            game.state shouldBe before
            game.isInHand(1, "Wild Growth") shouldBe true
            game.state.stack.size shouldBe 0
        }

        test("returning the targeted land before resolution makes Wild Growth fail to enter") {
            val game = fixture("Tropical Island")
                .withCardInHand(2, "Boomerang")
                .withLandsOnBattlefield(2, "Island", 2)
                .build()
            val land = game.findPermanent("Tropical Island")!!
            val aura = game.findCardsInHand(1, "Wild Growth").single()
            game.execute(CastSpell(
                game.player1Id, aura, listOf(ChosenTarget.Permanent(land)),
                paymentStrategy = PaymentStrategy.Explicit(listOf(game.findPermanent("Llanowar Elves")!!))
            )).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Boomerang", land).error shouldBe null
            game.resolveStack()

            game.isInHand(1, "Tropical Island") shouldBe true
            game.isInGraveyard(1, "Wild Growth") shouldBe true
            game.isOnBattlefield("Wild Growth") shouldBe false
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.stack.size shouldBe 0
        }

        test("destroying Wild Growth removes the bonus from a subsequent untap and mana activation") {
            val game = fixture()
                .withCardInHand(1, "Naturalize")
                .withCardOnBattlefield(1, "Voyaging Satyr")
                .build()
            val island = game.findPermanent("Island")!!
            val aura = attachByCasting(game, island)
            // This payment consumes the Island's own blue and the Aura's additional green.
            game.castSpell(1, "Naturalize", aura).error shouldBe null
            game.resolveStack()
            game.isInGraveyard(1, "Wild Growth") shouldBe true
            game.execute(ActivateAbility(
                game.player1Id, game.findPermanent("Voyaging Satyr")!!,
                cardRegistry.getCard("Voyaging Satyr")!!.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Permanent(island))
            )).error shouldBe null
            game.resolveStack()
            game.state.getEntity(island)!!.has<TappedComponent>() shouldBe false
            game.execute(ActivateAbility(game.player1Id, island, AbilityId.intrinsicMana('U'))).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.blue shouldBe 1
            pool.green shouldBe 0
            pool.total shouldBe 1
            game.state.stack.size shouldBe 0
        }
    }
}
