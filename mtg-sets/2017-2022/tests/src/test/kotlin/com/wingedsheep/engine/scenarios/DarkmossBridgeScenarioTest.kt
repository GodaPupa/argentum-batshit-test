package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mh2.cards.DarkmossBridge
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import io.kotest.matchers.shouldBe

/** Exact artifact-land fixtures, excluded from the v2 structural corpus. */
class DarkmossBridgeScenarioTest : ScenarioTestBase() {
    init {
        test("playing Bridge enters tapped and does not briefly offer mana") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardInHand(1, "Darkmoss Bridge").build()
            val bridge = game.findCardsInHand(1, "Darkmoss Bridge").single()
            game.execute(PlayLand(game.player1Id, bridge)).error shouldBe null
            game.state.getEntity(bridge)!!.has<TappedComponent>() shouldBe true
            val card = game.state.getEntity(bridge)!!.get<CardComponent>()!!
            card.typeLine.isArtifact shouldBe true
            card.typeLine.isLand shouldBe true
            card.colors shouldBe emptySet()
            card.baseKeywords.contains(Keyword.INDESTRUCTIBLE) shouldBe true
            game.state.stack.size shouldBe 0
            val before = game.state
            for (ability in DarkmossBridge.activatedAbilities) {
                game.execute(ActivateAbility(game.player1Id, bridge, ability.id)).isSuccess shouldBe false
                game.state shouldBe before
            }
        }

        for ((index, color) in listOf(Color.BLACK, Color.GREEN).withIndex()) {
            test("an untapped Bridge adds exactly one $color immediately") {
                val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                    .withCardOnBattlefield(1, "Darkmoss Bridge").build()
                val bridge = game.findPermanent("Darkmoss Bridge")!!
                game.execute(ActivateAbility(game.player1Id, bridge, DarkmossBridge.activatedAbilities[index].id)).error shouldBe null
                val pool = game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!
                pool.getAmount(color) shouldBe 1
                pool.total shouldBe 1
                game.state.stack.size shouldBe 0
                game.hasPendingDecision() shouldBe false
                game.state.getEntity(bridge)!!.has<TappedComponent>() shouldBe true
            }
        }

        test("Naturalize can target the artifact land but cannot destroy it") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(2, "Darkmoss Bridge")
                .withCardInHand(1, "Naturalize").withLandsOnBattlefield(1, "Forest", 2).build()
            val bridge = game.findPermanent("Darkmoss Bridge")!!
            game.castSpell(1, "Naturalize", bridge).error shouldBe null
            game.resolveStack()
            game.state.getBattlefield().contains(bridge) shouldBe true
            game.isInGraveyard(2, "Darkmoss Bridge") shouldBe false
            game.isInGraveyard(1, "Naturalize") shouldBe true
        }

        test("indestructible does not prevent paying Insight's artifact sacrifice") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(1, "Darkmoss Bridge", tapped = true)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Eviscerator's Insight")
                .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Myr Retriever").build()
            val library = game.state.getLibrary(game.player1Id).toList()
            game.castSpellWithAdditionalSacrifice(1, "Eviscerator's Insight", "Darkmoss Bridge").error shouldBe null
            game.isInGraveyard(1, "Darkmoss Bridge") shouldBe true
            game.handSize(1) shouldBe 0
            game.resolveStack()
            game.state.getHand(game.player1Id) shouldBe library
            game.isOnBattlefield("Darkmoss Bridge") shouldBe false
        }

        test("Revoke Existence exiles Bridge despite indestructible") {
            val game = scenario().withPlayers().withRngSeed(9_250_925_003L)
                .withCardOnBattlefield(2, "Darkmoss Bridge")
                .withCardInHand(1, "Revoke Existence").withLandsOnBattlefield(1, "Plains", 2).build()
            val bridge = game.findPermanent("Darkmoss Bridge")!!
            game.castSpell(1, "Revoke Existence", bridge).error shouldBe null
            game.resolveStack()
            game.state.getBattlefield().contains(bridge) shouldBe false
            game.state.getExile(game.player2Id).contains(bridge) shouldBe true
            game.isInGraveyard(2, "Darkmoss Bridge") shouldBe false
        }
    }
}
