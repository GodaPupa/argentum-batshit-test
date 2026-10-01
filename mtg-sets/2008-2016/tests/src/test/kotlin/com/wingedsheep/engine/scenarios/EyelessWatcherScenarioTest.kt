package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.bfz.cards.EyelessWatcher
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe

/** Deterministic card fixtures, excluded from the Phase 2 sample and its seed allocations. */
class EyelessWatcherScenarioTest : ScenarioTestBase() {
    init {
        test("the entry trigger makes exactly two Scions whose newborn sacrifice mana abilities are immediate") {
            val game = scenario().withPlayers().withRngSeed(0x57415443484552L)
                .withCardInHand(1, "Eyeless Watcher")
                .withLandsOnBattlefield(1, "Forest", 4)
                .build()
            val watcher = game.findCardsInHand(1, "Eyeless Watcher").single()
            game.state.getEntity(watcher)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            game.castSpell(1, "Eyeless Watcher").error shouldBe null
            game.state.stack.size shouldBe 1
            game.findPermanents("Eldrazi Scion") shouldBe emptyList()
            game.state.getEntity(watcher)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null

            game.isOnBattlefield("Eyeless Watcher") shouldBe true
            game.state.projectedState.getPower(watcher) shouldBe 1
            game.state.projectedState.getToughness(watcher) shouldBe 1
            game.state.projectedState.getColors(watcher) shouldBe emptySet()
            game.state.stack.size shouldBe 1
            game.findPermanents("Eldrazi Scion") shouldBe emptyList()
            game.resolveStack()

            val scions = game.findPermanents("Eldrazi Scion")
            scions.size shouldBe 2
            for (scion in scions) {
                val entity = game.state.getEntity(scion)!!
                entity.has<TokenComponent>() shouldBe true
                entity.has<SummoningSicknessComponent>() shouldBe true
                entity.get<CardComponent>()!!.ownerId shouldBe game.player1Id
                entity.get<ControllerComponent>()!!.playerId shouldBe game.player1Id
                game.state.projectedState.getPower(scion) shouldBe 1
                game.state.projectedState.getToughness(scion) shouldBe 1
                game.state.projectedState.getColors(scion) shouldBe emptySet()
                game.state.projectedState.hasSubtype(scion, "Eldrazi") shouldBe true
                game.state.projectedState.hasSubtype(scion, "Scion") shouldBe true
                game.state.projectedState.hasType(scion, "ARTIFACT") shouldBe false
            }

            for ((index, scion) in scions.withIndex()) {
                game.execute(ActivateAbility(game.player1Id, scion, PredefinedTokens.EldraziScion.activatedAbilities.single().id)).error shouldBe null
                game.findPermanents("Eldrazi Scion").size shouldBe 1 - index
                game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.colorless shouldBe index + 1
                game.state.stack.size shouldBe 0
                game.hasPendingDecision() shouldBe false
            }
            game.isOnBattlefield("Eyeless Watcher") shouldBe true
        }

        test("countering the Watcher spell creates no Scions") {
            val game = scenario().withPlayers().withRngSeed(0x57415443484552L)
                .withCardInHand(1, "Eyeless Watcher")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(2, "Counterspell")
                .withLandsOnBattlefield(2, "Island", 2)
                .build()
            game.castSpell(1, "Eyeless Watcher").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Eyeless Watcher").error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Eyeless Watcher") shouldBe true
            game.isOnBattlefield("Eyeless Watcher") shouldBe false
            game.findPermanents("Eldrazi Scion") shouldBe emptyList()
            game.state.getEntity(game.findCardsInGraveyard(1, "Eyeless Watcher").single())!!
                .get<CardComponent>()!!.colors shouldBe emptySet()
            game.state.stack.size shouldBe 0
        }

        test("the entry trigger survives the Watcher dying before that trigger resolves") {
            val game = scenario().withPlayers().withRngSeed(0x57415443484552L)
                .withCardInHand(1, "Eyeless Watcher")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .build()
            game.castSpell(1, "Eyeless Watcher").error shouldBe null
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            val watcher = game.findPermanent("Eyeless Watcher")!!
            game.findPermanents("Eldrazi Scion") shouldBe emptyList()
            game.state.stack.size shouldBe 1
            game.passPriority().error shouldBe null
            game.castSpell(2, "Lightning Bolt", watcher).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Eyeless Watcher") shouldBe true
            game.findPermanents("Eldrazi Scion").size shouldBe 2
            game.findPermanents("Eldrazi Scion").all {
                game.state.getEntity(it)!!.get<ControllerComponent>()!!.playerId == game.player1Id
            } shouldBe true
            game.state.stack.size shouldBe 0
        }

        test("a Watcher returned by Momentary Blink triggers entry without being cast") {
            val game = scenario().withPlayers().withRngSeed(0x57415443484552L)
                .withCardOnBattlefield(1, "Eyeless Watcher")
                .withCardInHand(1, "Momentary Blink")
                .withLandsOnBattlefield(1, "Plains", 2)
                .build()
            game.findPermanents("Eldrazi Scion") shouldBe emptyList()
            game.castSpell(1, "Momentary Blink", game.findPermanent("Eyeless Watcher")!!).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Eyeless Watcher") shouldBe true
            game.findPermanents("Eldrazi Scion").size shouldBe 2
            game.state.stack.size shouldBe 0
        }

        test("a Watcher's death alone does not create Scions") {
            val game = scenario().withPlayers().withRngSeed(0x57415443484552L)
                .withCardOnBattlefield(1, "Eyeless Watcher")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .build()
            game.castSpell(1, "Lightning Bolt", game.findPermanent("Eyeless Watcher")!!).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Eyeless Watcher") shouldBe true
            game.findPermanents("Eldrazi Scion") shouldBe emptyList()
            game.state.stack.size shouldBe 0
        }

        test("devoid is colorless in every stored zone while commander color identity stays green") {
            val game = scenario().withPlayers().withRngSeed(0x57415443484552L)
                .withCardInHand(1, "Eyeless Watcher")
                .withCardInLibrary(1, "Eyeless Watcher")
                .withCardInGraveyard(1, "Eyeless Watcher")
                .withCardInExile(1, "Eyeless Watcher")
                .withCardOnBattlefield(1, "Eyeless Watcher")
                .build()
            EyelessWatcher.colorIdentity shouldBe setOf(Color.GREEN)
            EyelessWatcher.colors shouldBe emptySet()
            for (zone in listOf(Zone.HAND, Zone.LIBRARY, Zone.GRAVEYARD, Zone.EXILE, Zone.BATTLEFIELD)) {
                val watcher = game.state.getZone(ZoneKey(game.player1Id, zone)).single()
                game.state.getEntity(watcher)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            }
            game.state.projectedState.getColors(game.findPermanent("Eyeless Watcher")!!) shouldBe emptySet()
        }
    }
}
