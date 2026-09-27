package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.LandPlayedEvent
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LandDropsComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mrd.cards.TreeOfTales
import com.wingedsheep.sdk.core.Color
import io.kotest.matchers.shouldBe

/** Fixed regression seed only; excluded from the Industrial v2 sampled seed registry. */
class TreeOfTalesScenarioTest : ScenarioTestBase() {
    init {
        test("playing Tree produces an untapped colorless artifact land with immediate green mana") {
            val game = scenario().withPlayers().withRngSeed(9250925003L)
                .withCardInHand(1, "Tree of Tales")
                .build()
            val tree = game.findCardsInHand(1, "Tree of Tales").single()
            game.state.getEntity(tree)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            TreeOfTales.colorIdentity shouldBe setOf(Color.GREEN)
            val played = game.execute(PlayLand(game.player1Id, tree))
            played.error shouldBe null
            played.events.filterIsInstance<LandPlayedEvent>().size shouldBe 1
            played.events.filterIsInstance<SpellCastEvent>() shouldBe emptyList()
            game.state.projectedState.hasType(tree, "ARTIFACT") shouldBe true
            game.state.projectedState.hasType(tree, "LAND") shouldBe true
            game.state.projectedState.getColors(tree) shouldBe emptySet()
            game.state.getEntity(tree)!!.has<TappedComponent>() shouldBe false
            game.state.stack.size shouldBe 0

            game.execute(ActivateAbility(game.player1Id, tree, TreeOfTales.activatedAbilities.single().id)).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.green shouldBe 1
            pool.total shouldBe 1
            pool.colorless shouldBe 0
            game.state.getEntity(tree)!!.has<TappedComponent>() shouldBe true
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("Tree cannot be cast and its land play consumes the one available land drop") {
            val game = scenario().withPlayers().withRngSeed(9250925003L)
                .withCardInHand(1, "Tree of Tales")
                .withCardInHand(1, "Forest")
                .build()
            val tree = game.findCardsInHand(1, "Tree of Tales").single()
            val forest = game.findCardsInHand(1, "Forest").single()
            val beforeCast = game.state
            game.castSpell(1, "Tree of Tales").isSuccess shouldBe false
            game.state shouldBe beforeCast

            game.execute(PlayLand(game.player1Id, tree)).error shouldBe null
            game.state.getEntity(game.player1Id)!!.get<LandDropsComponent>()!!.remaining shouldBe 0
            val afterFirstLand = game.state
            game.execute(PlayLand(game.player1Id, forest)).isSuccess shouldBe false

            game.state shouldBe afterFirstLand
            game.isInHand(1, "Forest") shouldBe true
            game.isOnBattlefield("Tree of Tales") shouldBe true
            game.state.stack.size shouldBe 0
        }

        test("Naturalize can destroy a played Tree because it is an artifact") {
            val game = scenario().withPlayers().withRngSeed(9250925003L)
                .withCardInHand(1, "Tree of Tales")
                .withCardInHand(1, "Naturalize")
                .withLandsOnBattlefield(1, "Forest", 2)
                .build()
            val tree = game.findCardsInHand(1, "Tree of Tales").single()
            game.execute(PlayLand(game.player1Id, tree)).error shouldBe null
            game.castSpell(1, "Naturalize", tree).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Tree of Tales") shouldBe false
            game.findCardsInGraveyard(1, "Tree of Tales") shouldBe listOf(tree)
            game.state.getEntity(tree)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            game.isInGraveyard(1, "Naturalize") shouldBe true
            game.state.stack.size shouldBe 0
        }
    }
}
