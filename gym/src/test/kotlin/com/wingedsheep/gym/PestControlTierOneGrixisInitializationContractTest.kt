package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.GrixisTelemetryTextTrace
import com.wingedsheep.gym.matchup.NONEXPERIMENTAL_GRIXIS_INITIALIZER_FIXTURE_ENTROPY
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_GRIXIS_MAIN_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisInitializationContract
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisTelemetryContract
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** One fixed construction fixture only; no seed file, vector, workflow, runner, or game action. */
class PestControlTierOneGrixisInitializationContractTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("fixed nonexperimental initializer conserves both exact 60-card decks") {
        val fixture = PestControlTierOneGrixisInitializationContract.initializeConstructionFixture(registry)

        fixture.provenance.seed shouldBe NONEXPERIMENTAL_GRIXIS_INITIALIZER_FIXTURE_ENTROPY
        fixture.provenance.pestSeat shouldBe PestSeat.SEAT_ZERO
        fixture.openingConservation.map { it.deckIdentity } shouldBe
            listOf("PEST_CONTROL_V10", "PASQUALE_GRIXIS_AFFINITY_60")
        fixture.openingConservation.map { it.deckSha256 } shouldBe
            listOf(PEST_CONTROL_V10_HASH, PEST_GRIXIS_MAIN_SHA256)
        fixture.openingConservation.map { it.libraryCount } shouldBe listOf(53, 53)
        fixture.openingConservation.map { it.handCount } shouldBe listOf(7, 7)
        fixture.openingConservation.map { it.otherZoneCount } shouldBe listOf(0, 0)
        fixture.openingConservation.map { it.totalOwnedCards } shouldBe listOf(60, 60)
        fixture.environment.state.turnOrder.first() shouldBe fixture.environment.playerIds[0]
        fixture.excludedFromExperimentalEvidence shouldBe true
        fixture.excludedFromFutureSeedOverlapRegistry shouldBe true
    }

    test("Grixis telemetry categories point only to canonical synthetic action and event text") {
        val index = PestControlTierOneGrixisTelemetryContract.indexText(
            listOf(
                GrixisTelemetryTextTrace(1, "Cast Myr Enforcer with Affinity", listOf("CardDrawnEvent Thoughtcast")),
                GrixisTelemetryTextTrace(
                    2,
                    "Cast Toxin Analysis targeting Krark-Clan Shaman",
                    listOf("DamageDealtEvent", "CardsDiscardedEvent Refurbished Familiar"),
                ),
                GrixisTelemetryTextTrace(3, "Activate Blood Fountain", listOf("Return from Graveyard")),
            )
        )

        index.affinityAndCostReduction.map { it.actionSequence } shouldContain 1
        index.artifactSacrificeAndDraw.map { it.actionSequence } shouldContain 1
        index.toxinAndShaman.map { it.actionSequence } shouldContain 2
        index.removalAndDamage.map { it.actionSequence } shouldContain 2
        index.familiarDiscard.map { it.actionSequence } shouldContain 2
        index.graveyardAndRecursion.map { it.actionSequence } shouldContain 3
    }

    test("telemetry rejects missing or reordered canonical action sequence") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisTelemetryContract.indexText(
                listOf(GrixisTelemetryTextTrace(2, "Cast Thoughtcast", emptyList()))
            )
        }
    }
})
