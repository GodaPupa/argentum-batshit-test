package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.MonoBlueTerrorTelemetryTextTrace
import com.wingedsheep.gym.matchup.NONEXPERIMENTAL_TERROR_INITIALIZER_FIXTURE_ENTROPY
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_MAIN_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorInitializationContract
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorTelemetryContract
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** One fixed construction fixture only; no seed file, vector, workflow, runner, or game action. */
class PestControlTierOneMonoBlueTerrorInitializationContractTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("fixed nonexperimental initializer conserves both exact 60-card decks") {
        val fixture =
            PestControlTierOneMonoBlueTerrorInitializationContract.initializeConstructionFixture(registry)

        fixture.provenance.seed shouldBe NONEXPERIMENTAL_TERROR_INITIALIZER_FIXTURE_ENTROPY
        fixture.provenance.pestSeat shouldBe PestSeat.SEAT_ZERO
        fixture.openingConservation.map { it.deckIdentity } shouldBe
            listOf("PEST_CONTROL_V10", "SERPICO_CC_MONO_BLUE_TERROR_60")
        fixture.openingConservation.map { it.deckSha256 } shouldBe
            listOf(PEST_CONTROL_V10_HASH, PEST_MONO_BLUE_TERROR_MAIN_SHA256)
        fixture.openingConservation.map { it.libraryCount } shouldBe listOf(53, 53)
        fixture.openingConservation.map { it.handCount } shouldBe listOf(7, 7)
        fixture.openingConservation.map { it.otherZoneCount } shouldBe listOf(0, 0)
        fixture.openingConservation.map { it.totalOwnedCards } shouldBe listOf(60, 60)
        fixture.environment.state.turnOrder.first() shouldBe fixture.environment.playerIds[0]
        fixture.excludedFromExperimentalEvidence shouldBe true
        fixture.excludedFromFutureSeedOverlapRegistry shouldBe true
    }

    test("Mono-Blue Terror telemetry categories point only to canonical synthetic action and event text") {
        val index = PestControlTierOneMonoBlueTerrorTelemetryContract.indexText(
            listOf(
                MonoBlueTerrorTelemetryTextTrace(
                    1,
                    "Cast Ponder",
                    listOf("CardDrawnEvent Preordain"),
                ),
                MonoBlueTerrorTelemetryTextTrace(
                    2,
                    "Cast Tolarian Terror with cost reduction",
                    listOf("Mental Note moved cards to Graveyard"),
                ),
                MonoBlueTerrorTelemetryTextTrace(
                    3,
                    "Cast Sleep of the Dead with Escape",
                    listOf("Exile from Graveyard", "Tap target creature"),
                ),
                MonoBlueTerrorTelemetryTextTrace(
                    4,
                    "Cast Counterspell",
                    listOf("Countered spell"),
                ),
                MonoBlueTerrorTelemetryTextTrace(
                    5,
                    "Cast Artful Dodge",
                    listOf("Creature is Unblockable"),
                ),
            )
        )

        index.cantripsAndSetup.map { it.actionSequence } shouldContain 1
        index.threatsAndCostReduction.map { it.actionSequence } shouldContain 2
        index.graveyardAndEscape.map { it.actionSequence } shouldContain 3
        index.tempoAndBounce.map { it.actionSequence } shouldContain 3
        index.permission.map { it.actionSequence } shouldContain 4
        index.evasion.map { it.actionSequence } shouldContain 5
    }

    test("telemetry rejects missing or reordered canonical action sequence") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorTelemetryContract.indexText(
                listOf(MonoBlueTerrorTelemetryTextTrace(2, "Cast Ponder", emptyList()))
            )
        }
    }
})
