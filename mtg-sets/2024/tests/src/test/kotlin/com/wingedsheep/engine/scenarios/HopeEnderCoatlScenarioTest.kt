package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mh3.cards.HopeEnderCoatl
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/** Exact-card regression fixtures, excluded from the official Phase 2 seed registry and sample. */
class HopeEnderCoatlScenarioTest : ScenarioTestBase() {
    private fun fixture(forests: Int = 3): ScenarioBuilder = scenario().withPlayers().withRngSeed(9250925015L)
        .withCardInHand(1, "Hope-Ender Coatl")
        .withLandsOnBattlefield(1, "Island", 3)
        .withCardInHand(2, "Grizzly Bears")
        .withLandsOnBattlefield(2, "Forest", forests)
        .withActivePlayer(2)

    private fun TestGame.castBears(): EntityId {
        val bears = findCardsInHand(2, "Grizzly Bears").single()
        castSpell(2, "Grizzly Bears").error shouldBe null
        passPriority().error shouldBe null
        return bears
    }

    private fun TestGame.castCoatlTargeting(bears: EntityId) {
        val previousStackSize = state.stack.size
        getLegalActions(1).any {
            it.actionType == "CastSpell" && it.description.contains("Hope-Ender Coatl")
        } shouldBe true
        castSpell(1, "Hope-Ender Coatl").error shouldBe null
        // The only eligible spell is controlled by the opponent. Neither Coatl nor another
        // spell controlled by its caster is a legal target for this cast trigger.
        val choice = getPendingDecision()
        if (choice is ChooseTargetsDecision) {
            choice.playerId shouldBe player1Id
            choice.legalTargets.values.flatten().toSet() shouldBe setOf(bears)
            selectTargets(listOf(bears)).error shouldBe null
        } else {
            choice shouldBe null
        }
        state.stack.size shouldBe previousStackSize + 2
        isOnBattlefield("Hope-Ender Coatl") shouldBe false
    }

    private fun TestGame.resolveUntilDecision() {
        resolveStack().forEach { it.error shouldBe null }
    }

    private fun TestGame.finishResolution() {
        resolveUntilDecision()
        state.stack.isEmpty() shouldBe true
        hasPendingDecision() shouldBe false
    }

    init {
        test("flash permits an opponent-turn cast and its separate cast trigger offers exactly one mana before the body") {
            val game = fixture().build()
            val coatl = game.findCardsInHand(1, "Hope-Ender Coatl").single()
            val bears = game.castBears()
            game.castCoatlTargeting(bears)
            game.state.getEntity(coatl)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            game.resolveUntilDecision()
            val offer = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            offer.playerId shouldBe game.player2Id
            offer.prompt shouldContain "{1}"
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe false
            game.answerYesNo(false).error shouldBe null
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe false
            game.state.stack.size shouldBe 1
            // The active player receives priority after the complete triggered-ability resolution.
            game.state.priorityPlayerId shouldBe game.player2Id
            game.finishResolution()
            game.state.projectedState.getPower(coatl) shouldBe 2
            game.state.projectedState.getToughness(coatl) shouldBe 2
            game.state.projectedState.hasKeyword(coatl, Keyword.FLYING) shouldBe true
            game.state.projectedState.getColors(coatl) shouldBe emptySet()
        }

        test("one spare Forest pays the one-mana tax and saves the opponent's spell") {
            val game = fixture().build()
            game.castCoatlTargeting(game.castBears())
            game.resolveUntilDecision()
            val offer = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            offer.playerId shouldBe game.player2Id
            offer.prompt shouldContain "{1}"
            game.answerYesNo(true).error shouldBe null
            game.submitManaSourcesAutoPay().error shouldBe null
            game.findPermanents("Forest").count { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe 3
            game.state.getEntity(game.player2Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0
            game.state.priorityPlayerId shouldBe game.player2Id
            game.finishResolution()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe false
        }

        test("with no remaining mana the cast trigger counters the spell without leaving an unpayable decision") {
            val game = fixture(forests = 2).build()
            game.castCoatlTargeting(game.castBears())
            game.finishResolution()
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe true
        }

        test("countering Coatl's body leaves its cast trigger to counter the opponent's spell") {
            val game = fixture(forests = 2)
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).build()
            game.castCoatlTargeting(game.castBears())
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Hope-Ender Coatl").error shouldBe null
            game.finishResolution()
            game.isInGraveyard(1, "Hope-Ender Coatl") shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe false
            game.isOnBattlefield("Grizzly Bears") shouldBe false
        }

        test("blinking a Coatl while an opponent's spell is pending does not create a cast trigger") {
            val game = scenario().withPlayers().withRngSeed(9250925015L)
                .withCardOnBattlefield(1, "Hope-Ender Coatl")
                .withCardInHand(1, "Momentary Blink").withLandsOnBattlefield(1, "Plains", 2)
                .withCardInHand(2, "Grizzly Bears").withLandsOnBattlefield(2, "Forest", 2)
                .withActivePlayer(2).build()
            game.castBears()
            game.castSpell(1, "Momentary Blink", game.findPermanent("Hope-Ender Coatl")!!).error shouldBe null
            game.finishResolution()
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe false
        }

        test("an own Lightning Bolt on the stack is excluded from the cast trigger's legal target choices") {
            val game = fixture().withCardInHand(1, "Lightning Bolt")
                .withCardOnBattlefield(1, "Mountain").build()
            val bears = game.castBears()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.castCoatlTargeting(bears)
            game.resolveUntilDecision()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player2Id
            game.answerYesNo(false).error shouldBe null
            game.finishResolution()
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe true
            game.getLifeTotal(2) shouldBe 17
            game.isInGraveyard(1, "Lightning Bolt") shouldBe true
        }

        test("Coatl can be cast and resolve when there is no opposing spell to target") {
            val game = scenario().withPlayers().withRngSeed(9250925015L)
                .withCardInHand(1, "Hope-Ender Coatl").withLandsOnBattlefield(1, "Island", 3).build()
            game.getLegalActions(1).any {
                it.actionType == "CastSpell" && it.description.contains("Hope-Ender Coatl")
            } shouldBe true
            game.castSpell(1, "Hope-Ender Coatl").error shouldBe null
            game.hasPendingDecision() shouldBe false
            game.state.stack.size shouldBe 1
            game.finishResolution()
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe true
        }

        test("a Coatl already on the battlefield does not trigger when its controller casts another creature") {
            val game = scenario().withPlayers().withRngSeed(9250925015L)
                .withCardOnBattlefield(1, "Hope-Ender Coatl")
                .withCardInHand(1, "Grizzly Bears").withLandsOnBattlefield(1, "Forest", 2).build()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.state.stack.size shouldBe 1
            game.finishResolution()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.isOnBattlefield("Hope-Ender Coatl") shouldBe true
        }

        test("devoid is colorless in every stored zone while Commander color identity remains blue") {
            val game = scenario().withPlayers().withRngSeed(9250925015L)
                .withCardInHand(1, "Hope-Ender Coatl").withCardInLibrary(1, "Hope-Ender Coatl")
                .withCardInGraveyard(1, "Hope-Ender Coatl").withCardInExile(1, "Hope-Ender Coatl")
                .withCardOnBattlefield(1, "Hope-Ender Coatl").build()
            HopeEnderCoatl.colorIdentity shouldBe setOf(Color.BLUE)
            HopeEnderCoatl.colors shouldBe emptySet()
            for (zone in listOf(Zone.HAND, Zone.LIBRARY, Zone.GRAVEYARD, Zone.EXILE, Zone.BATTLEFIELD)) {
                val coatl = game.state.getZone(ZoneKey(game.player1Id, zone)).single()
                game.state.getEntity(coatl)!!.get<CardComponent>()!!.colors shouldBe emptySet()
            }
        }
    }
}
