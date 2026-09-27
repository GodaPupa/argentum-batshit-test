package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SurveiledEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.otj.cards.ConduitPylons
import com.wingedsheep.sdk.core.Color
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed, excluded card fixtures; no structural candidate or official seed vector is loaded. */
class ConduitPylonsScenarioTest : ScenarioTestBase() {
    private fun landInHand(): TestGame = scenario()
        .withPlayers("Controller", "Opponent")
        .withRngSeed(0x50594c4f4eL)
        .withCardInHand(1, "Conduit Pylons")
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(1, "Mountain")
        .withCardInLibrary(2, "Swamp")
        .build()

    init {
        test("playing Pylons triggers surveil one and can put the top card in its owner's graveyard") {
            val game = landInHand()
            val pylons = game.findCardsInHand(1, "Conduit Pylons").single()
            val library = game.state.getLibrary(game.player1Id).toList()
            val opponentLibrary = game.state.getLibrary(game.player2Id).toList()
            game.execute(PlayLand(game.player1Id, pylons)).error shouldBe null
            game.state.getEntity(pylons)!!.has<TappedComponent>() shouldBe false
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 1
            game.resolveStack()
            val choose = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            choose.playerId shouldBe game.player1Id
            choose.options shouldBe listOf(library[0])
            choose.minSelections shouldBe 0
            choose.maxSelections shouldBe 1

            val completion = game.selectCards(listOf(library[0]))
            completion.error shouldBe null
            completion.events.filterIsInstance<SurveiledEvent>().single().count shouldBe 1
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldBe listOf(library[1])
            game.findCardsInGraveyard(1, "Island") shouldBe listOf(library[0])
            game.state.getLibrary(game.player2Id) shouldBe opponentLibrary
            game.handSize(1) shouldBe 0
        }

        test("declining the surveil graveyard choice keeps the same top card") {
            val game = landInHand()
            val library = game.state.getLibrary(game.player1Id).toList()
            game.execute(PlayLand(game.player1Id, game.findCardsInHand(1, "Conduit Pylons").single())).error shouldBe null
            game.resolveStack()
            game.skipSelection().error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<ReorderLibraryDecision>().cards shouldBe listOf(library[0])
            val completion = game.keepLibraryOrder()
            completion.error shouldBe null
            completion.events.filterIsInstance<SurveiledEvent>().single().count shouldBe 1
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldBe library
            game.graveyardSize(1) shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("the colorless ability adds one colorless mana immediately and taps Pylons") {
            val game = scenario().withPlayers().withRngSeed(0x50594c4f4eL)
                .withCardOnBattlefield(1, "Conduit Pylons", enteredThisTurn = true)
                .build()
            val pylons = game.findPermanent("Conduit Pylons")!!
            game.execute(ActivateAbility(game.player1Id, pylons, ConduitPylons.activatedAbilities[0].id)).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.colorless shouldBe 1
            pool.total shouldBe 1
            game.state.getEntity(pylons)!!.has<TappedComponent>() shouldBe true
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        for (color in Color.entries) {
            test("filtering to $color consumes another land's one mana and adds only one chosen mana") {
                val game = scenario().withPlayers().withRngSeed(0x50594c4f4eL)
                    .withCardOnBattlefield(1, "Conduit Pylons")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .build()
                val pylons = game.findPermanent("Conduit Pylons")!!
                val forest = game.findPermanent("Forest")!!
                game.execute(ActivateAbility(
                    game.player1Id, pylons, ConduitPylons.activatedAbilities[1].id,
                    manaColorChoice = color
                )).error shouldBe null

                val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                pool.getAmount(color) shouldBe 1
                pool.total shouldBe 1
                pool.colorless shouldBe 0
                game.state.getEntity(pylons)!!.has<TappedComponent>() shouldBe true
                game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
                game.state.stack.size shouldBe 0
                game.hasPendingDecision() shouldBe false
            }
        }

        test("Pylons cannot fund its own filter activation through auto or explicit payment") {
            for (explicit in listOf(false, true)) {
                val game = scenario().withPlayers().withRngSeed(0x50594c4f4eL)
                    .withCardOnBattlefield(1, "Conduit Pylons")
                    .build()
                val pylons = game.findPermanent("Conduit Pylons")!!
                val before = game.state
                val payment = if (explicit) PaymentStrategy.Explicit(listOf(pylons)) else PaymentStrategy.AutoPay
                game.execute(ActivateAbility(
                    game.player1Id, pylons, ConduitPylons.activatedAbilities[1].id,
                    manaColorChoice = Color.BLUE, paymentStrategy = payment
                )).isSuccess shouldBe false

                game.state shouldBe before
                game.state.getEntity(pylons)!!.has<TappedComponent>() shouldBe false
                game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            }
        }
    }
}
