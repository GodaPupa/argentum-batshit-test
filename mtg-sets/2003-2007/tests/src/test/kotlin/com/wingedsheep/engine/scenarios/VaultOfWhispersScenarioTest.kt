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
import com.wingedsheep.mtg.sets.definitions.mrd.cards.VaultOfWhispers
import com.wingedsheep.sdk.core.Color
import io.kotest.matchers.shouldBe

/** Fixed regression seed only; excluded from the Industrial v2 sampled seed registry. */
class VaultOfWhispersScenarioTest : ScenarioTestBase() {
    init {
        test("playing Vault produces an untapped colorless artifact land with immediate black mana") {
            val game = scenario().withPlayers().withRngSeed(9250925003L)
                .withCardInHand(1, "Vault of Whispers")
                .build()
            val vault = game.findCardsInHand(1, "Vault of Whispers").single()
            game.state.getEntity(vault)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            VaultOfWhispers.colorIdentity shouldBe setOf(Color.BLACK)
            val played = game.execute(PlayLand(game.player1Id, vault))
            played.error shouldBe null
            played.events.filterIsInstance<LandPlayedEvent>().size shouldBe 1
            played.events.filterIsInstance<SpellCastEvent>() shouldBe emptyList()
            game.state.projectedState.hasType(vault, "ARTIFACT") shouldBe true
            game.state.projectedState.hasType(vault, "LAND") shouldBe true
            game.state.projectedState.getColors(vault) shouldBe emptySet()
            game.state.getEntity(vault)!!.has<TappedComponent>() shouldBe false
            game.state.stack.size shouldBe 0

            game.execute(ActivateAbility(game.player1Id, vault, VaultOfWhispers.activatedAbilities.single().id)).error shouldBe null

            val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
            pool.black shouldBe 1
            pool.total shouldBe 1
            pool.colorless shouldBe 0
            game.state.getEntity(vault)!!.has<TappedComponent>() shouldBe true
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("Vault cannot be cast and its land play consumes the one available land drop") {
            val game = scenario().withPlayers().withRngSeed(9250925003L)
                .withCardInHand(1, "Vault of Whispers")
                .withCardInHand(1, "Swamp")
                .build()
            val vault = game.findCardsInHand(1, "Vault of Whispers").single()
            val swamp = game.findCardsInHand(1, "Swamp").single()
            val beforeCast = game.state
            game.castSpell(1, "Vault of Whispers").isSuccess shouldBe false
            game.state shouldBe beforeCast

            game.execute(PlayLand(game.player1Id, vault)).error shouldBe null
            game.state.getEntity(game.player1Id)!!.get<LandDropsComponent>()!!.remaining shouldBe 0
            val afterFirstLand = game.state
            game.execute(PlayLand(game.player1Id, swamp)).isSuccess shouldBe false

            game.state shouldBe afterFirstLand
            game.isInHand(1, "Swamp") shouldBe true
            game.isOnBattlefield("Vault of Whispers") shouldBe true
            game.state.stack.size shouldBe 0
        }

        test("Naturalize can destroy a played Vault because it is an artifact") {
            val game = scenario().withPlayers().withRngSeed(9250925003L)
                .withCardInHand(1, "Vault of Whispers")
                .withCardInHand(1, "Naturalize")
                .withLandsOnBattlefield(1, "Forest", 2)
                .build()
            val vault = game.findCardsInHand(1, "Vault of Whispers").single()
            game.execute(PlayLand(game.player1Id, vault)).error shouldBe null
            game.castSpell(1, "Naturalize", vault).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Vault of Whispers") shouldBe false
            game.findCardsInGraveyard(1, "Vault of Whispers") shouldBe listOf(vault)
            game.state.getEntity(vault)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            game.isInGraveyard(1, "Naturalize") shouldBe true
            game.state.stack.size shouldBe 0
        }
    }
}
