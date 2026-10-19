package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ulg.cards.CloudOfFaeries
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Synthetic card fixtures only; no frozen experimental deck or official seed is used. */
class CloudOfFaeriesScenarioTest : FunSpec({
    val shroudedLand = card("CloudOfFaeries Test Shrouded Land") {
        typeLine = "Land"
        oracleText = "Shroud"
        keywords(Keyword.SHROUD)
    }

    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(CloudOfFaeries, shroudedLand))
        initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun castAndLeaveTriggerOnStack(d: GameTestDriver): EntityId {
        val creature = d.putCardInHand(d.player1, "Cloud of Faeries")
        d.giveMana(d.player1, Color.BLUE, 2)
        d.castSpell(d.player1, creature).error shouldBe null
        d.pendingDecision shouldBe null
        d.state.getBattlefield().contains(creature) shouldBe false

        d.bothPass().error shouldBe null
        d.state.getBattlefield().contains(creature) shouldBe true
        d.state.projectedState.getPower(creature) shouldBe 1
        d.state.projectedState.getToughness(creature) shouldBe 1
        d.state.projectedState.hasKeyword(creature, Keyword.FLYING) shouldBe true
        // The creature has entered and its trigger is on the stack. No lands are chosen yet.
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 1
        return creature
    }

    fun resolveToLandChoice(d: GameTestDriver): SelectCardsDecision {
        d.bothPass().error shouldBe null
        return d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>().also {
            it.playerId shouldBe d.player1
            it.minSelections shouldBe 0
            it.maxSelections shouldBe 2
        }
    }

    test("lands are chosen at trigger resolution and may include both players' shrouded lands") {
        val d = driver()
        val ownShrouded = d.putLandOnBattlefield(d.player1, shroudedLand.name)
        val additionalChosen = List(0) {
            d.putLandOnBattlefield(d.player2, "Island")
        }
        val unchosen = d.putLandOnBattlefield(d.player1, "Island")
        (listOf(ownShrouded, unchosen) + additionalChosen).forEach(d::tapPermanent)

        castAndLeaveTriggerOnStack(d)
        // This land was absent when the creature entered, but exists when the trigger resolves.
        val opposingShrouded = d.putLandOnBattlefield(d.player2, shroudedLand.name)
        d.tapPermanent(opposingShrouded)
        val chosen = listOf(ownShrouded, opposingShrouded) + additionalChosen

        val decision = resolveToLandChoice(d)
        d.state.projectedState.hasKeyword(ownShrouded, Keyword.SHROUD) shouldBe true
        d.state.projectedState.hasKeyword(opposingShrouded, Keyword.SHROUD) shouldBe true
        chosen.forEach { decision.options.contains(it) shouldBe true }
        decision.options.contains(unchosen) shouldBe true
        d.submitCardSelection(d.player1, chosen + unchosen).isSuccess shouldBe false
        d.submitCardSelection(d.player1, chosen).error shouldBe null

        chosen.forEach { d.isTapped(it) shouldBe false }
        d.isTapped(unchosen) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.isEmpty() shouldBe true
    }

    for (selectionCount in listOf(0, 1)) {
        test("choosing $selectionCount lands is legal when more than 2 lands are available") {
            val d = driver()
            val lands = List(3) { d.putLandOnBattlefield(d.player1, "Island") }
            lands.forEach(d::tapPermanent)
            castAndLeaveTriggerOnStack(d)

            resolveToLandChoice(d)
            d.submitCardSelection(d.player1, lands.take(selectionCount)).error shouldBe null

            lands.forEachIndexed { index, land ->
                d.isTapped(land) shouldBe (index >= selectionCount)
            }
            d.pendingDecision shouldBe null
            d.state.stack.isEmpty() shouldBe true
        }
    }

    test("the triggered ability resolves legally when there are no lands") {
        val d = driver()
        castAndLeaveTriggerOnStack(d)
        d.bothPass().error shouldBe null

        d.pendingDecision shouldBe null
        d.state.stack.isEmpty() shouldBe true
    }

    test("the triggered ability still untaps lands after its source leaves the battlefield") {
        val d = driver()
        val lands = List(3) { d.putLandOnBattlefield(d.player2, "Island") }
        lands.forEach(d::tapPermanent)
        val creature = castAndLeaveTriggerOnStack(d)

        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED, 1)
        d.castSpell(d.player1, bolt, listOf(creature)).error shouldBe null
        d.bothPass().error shouldBe null
        d.state.getGraveyard(d.player1).contains(creature) shouldBe true
        d.pendingDecision shouldBe null

        resolveToLandChoice(d)
        d.submitCardSelection(d.player1, lands.take(2)).error shouldBe null

        lands.take(2).forEach { d.isTapped(it) shouldBe false }
        d.isTapped(lands.last()) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.isEmpty() shouldBe true
    }

    test("cycling discards Cloud of Faeries and draws one card without triggering land untaps") {
        val d = driver()
        val land = d.putLandOnBattlefield(d.player1, "Island")
        d.tapPermanent(land)
        val drawnCard = d.putCardOnTopOfLibrary(d.player1, "Island")
        val cloud = d.putCardInHand(d.player1, "Cloud of Faeries")
        val handBeforeCycling = d.getHand(d.player1).size
        d.giveMana(d.player1, Color.BLUE, 2)

        d.submit(CycleCard(d.player1, cloud)).error shouldBe null
        d.state.getGraveyard(d.player1).contains(cloud) shouldBe true
        d.getHand(d.player1).contains(cloud) shouldBe false
        d.bothPass().error shouldBe null

        d.getHand(d.player1).size shouldBe handBeforeCycling
        d.getHand(d.player1).contains(drawnCard) shouldBe true
        d.state.getBattlefield().contains(cloud) shouldBe false
        d.isTapped(land) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.isEmpty() shouldBe true
    }
})

