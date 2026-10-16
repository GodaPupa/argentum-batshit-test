package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * A seed-free, deliberately constructed payment-state regression. No experimental
 * deck, official R1 allocation, or previously executed opening order is initialized.
 * We demonstrate the observed binder defect rather than silently curing any failed game.
 */
class IndustrialWasteExploratoryCropBoulderWitnessTest : FunSpec({
    test("real Boulder green filter route is available but current binder rejects its first step").config(
        enabled = System.getenv("IW_DIAG_CROP_BOULDER") == "true",
    ) {
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set ->
                registerCards(set.cards)
                registerCards(set.basicLands)
            }
            registerCards(PredefinedTokens.allTokens)
            initMirrorMatch(
                Deck.of("Forest" to 40),
                skipMulligans = true,
                startingPlayer = 0,
                seed = 9_250_925_555L,
            )
            passPriorityUntil(Step.PRECOMBAT_MAIN)
            replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
                if (key.zoneType == Zone.HAND) emptyList() else cards
            }))
        }
        val player = driver.activePlayer!!
        val p1 = driver.putPermanentOnBattlefield(player, "Urza's Power Plant")
        val p2 = driver.putPermanentOnBattlefield(player, "Urza's Power Plant")
        val mine = driver.putPermanentOnBattlefield(player, "Urza's Mine")
        val tower = driver.putPermanentOnBattlefield(player, "Urza's Tower")
        val swampA = driver.putPermanentOnBattlefield(player, "Swamp")
        val swampB = driver.putPermanentOnBattlefield(player, "Swamp")
        val boulder = driver.putPermanentOnBattlefield(player, "Giant's Boulder")
        val crop = driver.putCardInHand(player, "Crop Rotation")
        val simulator = GameSimulator(driver.cardRegistry)
        fun legal() = simulator.getLegalActions(driver.state, player)

        // Frozen v2 policy chooses a duplicate Tron piece for Crop Rotation's
        // additional land-sacrifice cost. It will reserve this untapped object.
        val prospective = IndustrialWasteV2PublicActionPolicy.choose(
            driver.state, player, legal(),
        ).shouldBeInstanceOf<CastSpell>()
        prospective.cardId shouldBe crop
        val reservedPlant = prospective.additionalCostPayment!!.sacrificedPermanents.single()
        (reservedPlant in setOf(p1, p2)) shouldBe true
        for (land in listOf(p1, p2, mine, tower, swampA, swampB)) {
            if (land != reservedPlant) {
                driver.replaceState(driver.state.updateEntity(land) { it.with(TappedComponent) })
            }
        }
        // No ordinary green producer remains untapped. A valid complete payment:
        // tap reserved Plant for conditional colorless mana, use Boulder for {G},
        // then sacrifice that same Plant when announcing Crop Rotation.
        val blocked = IndustrialWasteV2PaymentBinder(driver.cardRegistry)
        val initialActions = legal()
        val selection = IndustrialWasteV2PublicActionPolicy.choose(
            driver.state, player, initialActions,
        ).shouldBeInstanceOf<CastSpell>()
        selection.cardId shouldBe crop
        selection.additionalCostPayment!!.sacrificedPermanents.single() shouldBe reservedPlant

        // This assertion documents the present source defect, it is NOT a fix.
        val defect = shouldThrow<IllegalStateException> {
            blocked.choose(driver.state, player, initialActions)
        }
        defect.message shouldBe "Selected action requires explicit mana but no qualified nonconflicting funding action exists"

        val plantAction = legal().single { entry ->
            entry.affordable && entry.isManaAbility &&
                (entry.action as? ActivateAbility)?.sourceId == reservedPlant
        }
        val physicalPlant = IndustrialWasteV2PublicActionPolicy.bind(
            driver.state, player, plantAction,
        ).shouldBeInstanceOf<ActivateAbility>()
        driver.submit(physicalPlant).error shouldBe null

        val filterAction = legal().single { entry ->
            entry.affordable && entry.isManaAbility &&
                (entry.action as? ActivateAbility)?.sourceId == boulder
        }
        val physicalFilter = IndustrialWasteV2PublicActionPolicy.bind(
            driver.state, player, filterAction,
        ).shouldBeInstanceOf<ActivateAbility>()
        physicalFilter.manaColorChoice shouldBe Color.GREEN
        driver.submit(physicalFilter).error shouldBe null

        val cost = IndustrialWasteV2PaymentBinder(driver.cardRegistry)
        cost.choose(driver.state, player, legal()) shouldBe selection
        driver.submit(selection).error shouldBe null
    }
})
