package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mh3.cards.EldraziRepurposer
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe

/** Deterministic card fixtures, excluded from Phase 2 capability or primary sampled games. */
class EldraziRepurposerScenarioTest : ScenarioTestBase() {
    init {
        test("the self-cast trigger creates a Spawn before the body and its newborn mana ability is immediate") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardInHand(1, "Eldrazi Repurposer")
                .withLandsOnBattlefield(1, "Forest", 3)
                .build()
            val repurposer = game.findCardsInHand(1, "Eldrazi Repurposer").single()
            game.castSpell(1, "Eldrazi Repurposer").error shouldBe null
            game.state.getEntity(repurposer)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            game.state.stack.size shouldBe 2
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null

            game.isOnBattlefield("Eldrazi Repurposer") shouldBe false
            game.state.stack.size shouldBe 1
            val spawn = game.findPermanents("Eldrazi Spawn").single()
            val entity = game.state.getEntity(spawn)!!
            entity.has<TokenComponent>() shouldBe true
            entity.has<SummoningSicknessComponent>() shouldBe true
            entity.get<CardComponent>()!!.ownerId shouldBe game.player1Id
            entity.get<ControllerComponent>()!!.playerId shouldBe game.player1Id
            game.state.projectedState.getPower(spawn) shouldBe 0
            game.state.projectedState.getToughness(spawn) shouldBe 1
            game.state.projectedState.getColors(spawn) shouldBe emptySet()
            game.state.projectedState.hasSubtype(spawn, "Eldrazi") shouldBe true
            game.state.projectedState.hasSubtype(spawn, "Spawn") shouldBe true
            game.state.projectedState.hasType(spawn, "ARTIFACT") shouldBe false

            game.execute(ActivateAbility(game.player1Id, spawn, PredefinedTokens.EldraziSpawn.activatedAbilities.single().id)).error shouldBe null
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.colorless shouldBe 1
            game.state.stack.size shouldBe 1
            game.hasPendingDecision() shouldBe false
            game.resolveStack()

            game.isOnBattlefield("Eldrazi Repurposer") shouldBe true
            game.state.projectedState.getPower(repurposer) shouldBe 3
            game.state.projectedState.getToughness(repurposer) shouldBe 3
            game.state.projectedState.getColors(repurposer) shouldBe emptySet()
            // Entry is not another token trigger.
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.state.stack.size shouldBe 0
        }

        test("countering the body leaves its already-triggered cast ability to create exactly one Spawn") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardInHand(1, "Eldrazi Repurposer")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(2, "Counterspell")
                .withLandsOnBattlefield(2, "Island", 2)
                .build()
            game.castSpell(1, "Eldrazi Repurposer").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Eldrazi Repurposer").error shouldBe null
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null

            game.isInGraveyard(1, "Eldrazi Repurposer") shouldBe true
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.state.stack.size shouldBe 1
            game.resolveStack()

            game.isOnBattlefield("Eldrazi Repurposer") shouldBe false
            game.findPermanents("Eldrazi Spawn").size shouldBe 1
            game.state.stack.size shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("the Repurposer dying creates exactly one additional death-trigger Spawn") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .build()
            game.castSpell(1, "Lightning Bolt", game.findPermanent("Eldrazi Repurposer")!!).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Eldrazi Repurposer") shouldBe true
            val spawn = game.findPermanents("Eldrazi Spawn").single()
            game.state.projectedState.getPower(spawn) shouldBe 0
            game.state.projectedState.getToughness(spawn) shouldBe 1
            game.state.getEntity(spawn)!!.get<CardComponent>()!!.ownerId shouldBe game.player1Id
            game.state.getEntity(spawn)!!.get<ControllerComponent>()!!.playerId shouldBe game.player1Id
            game.state.stack.size shouldBe 0
        }

        test("blinking a Repurposer neither casts it nor kills it and creates no Spawn") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .withCardInHand(1, "Momentary Blink")
                .withLandsOnBattlefield(1, "Plains", 2)
                .build()
            game.castSpell(1, "Momentary Blink", game.findPermanent("Eldrazi Repurposer")!!).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Eldrazi Repurposer") shouldBe true
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.state.stack.size shouldBe 0
        }

        for ((spell, land, destination) in listOf(
            Triple("Unsummon", "Island", Zone.HAND),
            Triple("Swords to Plowshares", "Plains", Zone.EXILE)
        )) {
            test("$spell moves Repurposer to $destination without a death trigger") {
                val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                    .withCardOnBattlefield(1, "Eldrazi Repurposer")
                    .withCardInHand(1, spell)
                    .withLandsOnBattlefield(1, land, 1)
                    .build()
                val repurposer = game.findPermanent("Eldrazi Repurposer")!!
                game.castSpell(1, spell, repurposer).error shouldBe null
                game.resolveStack()

                game.state.getZone(ZoneKey(game.player1Id, destination)) shouldBe listOf(repurposer)
                game.state.getEntity(repurposer)!!.get<CardComponent>()!!.colors shouldBe emptySet()
                game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
                game.state.stack.size shouldBe 0
            }
        }

        test("casting another creature does not trigger a Repurposer already on the battlefield") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 2)
                .build()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.state.stack.size shouldBe 1
            game.resolveStack()

            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.state.stack.size shouldBe 0
        }

        test("another creature dying does not trigger the Repurposer's own death ability") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .build()
            game.castSpell(1, "Lightning Bolt", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Eldrazi Repurposer") shouldBe true
            game.findPermanents("Eldrazi Spawn") shouldBe emptyList()
            game.state.stack.size shouldBe 0
        }

        test("devoid is colorless across zones with green color identity and no printed reach") {
            val game = scenario().withPlayers().withRngSeed(0x524550555250L)
                .withCardInHand(1, "Eldrazi Repurposer")
                .withCardInLibrary(1, "Eldrazi Repurposer")
                .withCardInGraveyard(1, "Eldrazi Repurposer")
                .withCardInExile(1, "Eldrazi Repurposer")
                .withCardOnBattlefield(1, "Eldrazi Repurposer")
                .build()
            EldraziRepurposer.colorIdentity shouldBe setOf(Color.GREEN)
            EldraziRepurposer.colors shouldBe emptySet()
            (Keyword.REACH in EldraziRepurposer.keywords) shouldBe false
            for (zone in listOf(Zone.HAND, Zone.LIBRARY, Zone.GRAVEYARD, Zone.EXILE, Zone.BATTLEFIELD)) {
                val repurposer = game.state.getZone(ZoneKey(game.player1Id, zone)).single()
                game.state.getEntity(repurposer)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            }
            game.state.projectedState.getColors(game.findPermanent("Eldrazi Repurposer")!!) shouldBe emptySet()
        }
    }
}
