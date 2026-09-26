package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Frozen-list metric semantics, not new card qualification or official corpus games. */
class IndustrialWasteV2ActivatedCheckpointTest : FunSpec({
    fun game() = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        initMirrorMatch(Deck.of("Forest" to 40), seed = 9_250_925_108L)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
    }

    listOf("Blood Fountain" to 4, "Dross Skullbomb" to 3).forEach { (name, amount) ->
        test("$name recovery with legal target and enough colorless mana records a colored failure") {
            val driver = game()
            val player = driver.player1
            repeat(amount) { driver.putPermanentOnBattlefield(player, "Urza's Mine") }
            val source = driver.putPermanentOnBattlefield(player, name)
            driver.putCardInGraveyard(player, "Myr Retriever")
            val metric = IndustrialWasteV2CheckpointManaClassifier.classify(
                driver.state, player, mapOf(source to "$name#1"),
                GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player), driver.cardRegistry,
            )
            metric.coloredManaFailure shouldBe true
            metric.totalManaStranded shouldBe false
            metric.activatedAbilityCoverageComplete shouldBe true
            metric.relevantActivatedAbilities.single().status shouldBe
                IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
            metric.unresolved shouldBe false
        }
        test("$name with no graveyard creature is not a relevant colored recovery failure") {
            val driver = game()
            val player = driver.player1
            repeat(amount) { driver.putPermanentOnBattlefield(player, "Urza's Mine") }
            val source = driver.putPermanentOnBattlefield(player, name)
            val metric = IndustrialWasteV2CheckpointManaClassifier.classify(
                driver.state, player, mapOf(source to "$name#1"),
                GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player), driver.cardRegistry,
            )
            metric.coloredManaFailure shouldBe false
            metric.relevantActivatedAbilities.single().status shouldBe
                IndustrialWasteV2CheckpointCardStatus.NO_LEGAL_TARGET
        }
        test("$name recovery payable with a black source is executable") {
            val driver = game()
            val player = driver.player1
            repeat(amount - 1) { driver.putPermanentOnBattlefield(player, "Urza's Mine") }
            driver.putPermanentOnBattlefield(player, "Swamp")
            val source = driver.putPermanentOnBattlefield(player, name)
            driver.putCardInGraveyard(player, "Myr Retriever")
            val metric = IndustrialWasteV2CheckpointManaClassifier.classify(
                driver.state, player, mapOf(source to "$name#1"),
                GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player), driver.cardRegistry,
            )
            metric.coloredManaFailure shouldBe false
            metric.relevantActivatedAbilities.single().status shouldBe
                IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
        }
    }
    test("tapped Fountain does not become a color failure when its non-mana cost is unavailable") {
        val driver = game()
        val player = driver.player1
        repeat(4) { driver.putPermanentOnBattlefield(player, "Urza's Mine") }
        val source = driver.putPermanentOnBattlefield(player, "Blood Fountain")
        driver.putCardInGraveyard(player, "Myr Retriever")
        driver.replaceState(driver.state.updateEntity(source) { it.with(TappedComponent) })
        val metric = IndustrialWasteV2CheckpointManaClassifier.classify(
            driver.state, player, mapOf(source to "Blood Fountain#1"),
            GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player), driver.cardRegistry,
        )
        metric.coloredManaFailure shouldBe false
        metric.relevantActivatedAbilities.single().status shouldBe
            IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY
    }
})
