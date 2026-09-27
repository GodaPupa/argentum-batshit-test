package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.ScriedEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.woe.cards.CandyTrail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed, excluded card fixtures; these do not load a candidate deck or sample allocation. */
class CandyTrailScenarioTest : ScenarioTestBase() {
    private fun castableTrail(): TestGame = scenario()
        .withPlayers("Controller", "Opponent")
        .withRngSeed(0x43414e4459L)
        .withCardInHand(1, "Candy Trail")
        .withLandsOnBattlefield(1, "Forest", 3)
        .withCardInLibrary(1, "Island")
        .withCardInLibrary(1, "Mountain")
        .withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Plains")
        .build()

    init {
        test("entry looks at exactly two cards and bottoms only the chosen card") {
            val game = castableTrail()
            val library = game.state.getLibrary(game.player1Id).toList()
            val opponentLibrary = game.state.getLibrary(game.player2Id).toList()
            game.castSpell(1, "Candy Trail").error shouldBe null
            game.hasPendingDecision() shouldBe false
            game.resolveStack()

            val choose = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            choose.playerId shouldBe game.player1Id
            choose.options shouldBe library.take(2)
            choose.minSelections shouldBe 0
            choose.maxSelections shouldBe 2
            game.selectCards(listOf(library[0])).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<ReorderLibraryDecision>().cards shouldBe listOf(library[1])
            val completion = game.keepLibraryOrder()
            completion.error shouldBe null
            completion.events.filterIsInstance<ScriedEvent>().single().count shouldBe 2
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldBe listOf(library[1], library[2], library[0])
            game.state.getLibrary(game.player2Id) shouldBe opponentLibrary
            game.graveyardSize(1) shouldBe 0
            game.handSize(1) shouldBe 0
            game.isOnBattlefield("Candy Trail") shouldBe true
        }

        test("keeping both scried cards permits their chosen top order") {
            val game = castableTrail()
            val library = game.state.getLibrary(game.player1Id).toList()
            game.castSpell(1, "Candy Trail").error shouldBe null
            game.resolveStack()
            game.skipSelection().error shouldBe null
            val order = game.getPendingDecision().shouldBeInstanceOf<ReorderLibraryDecision>()
            order.cards shouldBe library.take(2)
            game.submitDecision(OrderedResponse(order.id, listOf(library[1], library[0]))).error shouldBe null
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldBe listOf(library[1], library[0], library[2])
            game.graveyardSize(1) shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a newly entered Trail pays two mana and sacrifices before gaining life and drawing") {
            val game = castableTrail()
            val firstCard = game.state.getLibrary(game.player1Id).first()
            game.castSpell(1, "Candy Trail").error shouldBe null
            game.resolveStack()
            game.skipSelection().error shouldBe null
            game.keepLibraryOrder().error shouldBe null
            game.resolveStack()
            val trail = game.findPermanent("Candy Trail")!!
            game.findPermanents("Forest").count { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe 1

            game.execute(ActivateAbility(game.player1Id, trail, CandyTrail.activatedAbilities.single().id)).error shouldBe null

            game.isInGraveyard(1, "Candy Trail") shouldBe true
            game.isOnBattlefield("Candy Trail") shouldBe false
            game.findPermanents("Forest").all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.getLifeTotal(1) shouldBe 20
            game.handSize(1) shouldBe 0
            game.state.stack.size shouldBe 1
            game.resolveStack()

            game.getLifeTotal(1) shouldBe 23
            game.getLifeTotal(2) shouldBe 20
            game.state.getHand(game.player1Id) shouldBe listOf(firstCard)
            game.librarySize(1) shouldBe 2
            game.hasPendingDecision() shouldBe false
        }

        test("one mana cannot pay the activation and does not sacrifice the Trail") {
            val game = scenario()
                .withPlayers()
                .withRngSeed(0x43414e4459L)
                .withCardOnBattlefield(1, "Candy Trail")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Island")
                .build()
            val trail = game.findPermanent("Candy Trail")!!
            val before = game.state

            game.execute(ActivateAbility(game.player1Id, trail, CandyTrail.activatedAbilities.single().id)).isSuccess shouldBe false

            game.state shouldBe before
            game.isOnBattlefield("Candy Trail") shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.handSize(1) shouldBe 0
        }

        test("an already tapped Trail cannot pay its tap cost even with two available mana") {
            val game = scenario()
                .withPlayers()
                .withRngSeed(0x43414e4459L)
                .withCardOnBattlefield(1, "Candy Trail", tapped = true)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Island")
                .build()
            val trail = game.findPermanent("Candy Trail")!!
            val before = game.state

            game.execute(ActivateAbility(game.player1Id, trail, CandyTrail.activatedAbilities.single().id)).isSuccess shouldBe false

            game.state shouldBe before
            game.isInGraveyard(1, "Candy Trail") shouldBe false
            game.handSize(1) shouldBe 0
        }
    }
}
