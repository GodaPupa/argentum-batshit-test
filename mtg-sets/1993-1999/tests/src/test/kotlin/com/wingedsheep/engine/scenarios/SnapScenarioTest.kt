package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SnapScenarioTest : ScenarioTestBase() {
    private fun position() = scenario().withPlayers("Caster", "Opponent")
        .withCardInHand(1, "Snap")
        .withLandsOnBattlefield(1, "Island", 2)
        .withCardOnBattlefield(2, "Forest", tapped = true)
        .withCardOnBattlefield(2, "Grizzly Bears")
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Forest")
        .build()

    init {
        test("bounce resolves before non-target land choice and either player's lands are eligible") {
            val game = position()
            game.castSpell(1, "Snap", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.isInHand(2, "Grizzly Bears") shouldBe true
            val decision = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            decision.minSelections shouldBe 0
            decision.maxSelections shouldBe 2
            decision.options.size shouldBe 3
            val forest = game.findPermanent("Forest")!!
            val island = decision.options.first { game.state.getEntity(it)?.get<CardComponent>()?.name == "Island" }
            game.selectCards(listOf(forest, island)).error shouldBe null
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe false
            game.state.getEntity(island)!!.has<TappedComponent>() shouldBe false
            game.isInGraveyard(1, "Snap") shouldBe true
        }

        test("controller can choose zero lands") {
            val game = position()
            game.castSpell(1, "Snap", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            game.resolveStack()
            game.selectCards(emptyList()).error shouldBe null
            game.state.getBattlefield(game.player1Id).all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
            game.state.getEntity(game.findPermanent("Forest")!!)!!.has<TappedComponent>() shouldBe true
        }

        test("controller can choose one land") {
            val game = position()
            game.castSpell(1, "Snap", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            game.resolveStack()
            val forest = game.findPermanent("Forest")!!
            game.selectCards(listOf(forest)).error shouldBe null
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe false
        }

        test("an illegal sole creature target prevents every effect including the land untap") {
            val game = position()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Snap", bears).error shouldBe null
            game.state = ZoneTransitionService.moveToZone(game.state, bears, Zone.GRAVEYARD).state
            game.resolveStack().forEach { it.error shouldBe null }
            game.hasPendingDecision() shouldBe false
            game.state.getBattlefield(game.player1Id).all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
            game.state.getEntity(game.findPermanent("Forest")!!)!!.has<TappedComponent>() shouldBe true
            game.isInGraveyard(1, "Snap") shouldBe true
        }
    }
}
