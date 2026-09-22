package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.core.Zone
import kotlinx.serialization.Serializable

/** Fixed construction entropy. It is not an experimental seed and is never eligible for a registry. */
const val NONEXPERIMENTAL_TERROR_INITIALIZER_FIXTURE_ENTROPY: Long = 0x7E77_0B1E_0000_0001L

private const val SYNTHETIC_TERROR_INITIALIZER_SOURCE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private val SYNTHETIC_TERROR_INITIALIZER_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    orderedVectorSha256 = "c".repeat(64),
    assignmentCsvSha256 = "d".repeat(64),
    freezeManifestSha256 = "e".repeat(64),
)

@Serializable
data class MonoBlueTerrorOpeningConservation(
    val seat: PestSeat,
    val deckIdentity: String,
    val deckSha256: String,
    val libraryCount: Int,
    val handCount: Int,
    val otherZoneCount: Int,
    val totalOwnedCards: Int,
    val orderedLibrarySha256: String,
)

data class MonoBlueTerrorConstructionFixture(
    val provenance: MonoBlueTerrorSmokeProvenance,
    val environment: GameEnvironment,
    val openingConservation: List<MonoBlueTerrorOpeningConservation>,
    val excludedFromExperimentalEvidence: Boolean = true,
    val excludedFromFutureSeedOverlapRegistry: Boolean = true,
)

/**
 * The only initializer in this gate. It accepts exactly one fixed nonexperimental fixture and has
 * no overload capable of accepting a future frozen smoke assignment.
 */
object PestControlTierOneMonoBlueTerrorInitializationContract {
    fun initializeConstructionFixture(registry: CardRegistry): MonoBlueTerrorConstructionFixture {
        val readinessErrors = PestControlTierOneMonoBlueTerrorReadiness.validationErrors(
            TierOneMonoBlueTerrorReadiness(),
            registry,
        )
        require(readinessErrors.isEmpty()) { readinessErrors.joinToString("; ") }

        val assignment = MonoBlueTerrorSmokeAssignment(
            gameNumber = 1,
            seed = NONEXPERIMENTAL_TERROR_INITIALIZER_FIXTURE_ENTROPY,
            seedHex = NONEXPERIMENTAL_TERROR_INITIALIZER_FIXTURE_ENTROPY.toSeedHex(),
            pestSeat = PestSeat.SEAT_ZERO,
            terrorSeat = PestSeat.SEAT_ONE,
            startingDeck = MonoBlueTerrorStartingDeck.PEST_CONTROL,
        )
        val provenance = PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
            assignment,
            SYNTHETIC_TERROR_INITIALIZER_IDENTITY,
            SYNTHETIC_TERROR_INITIALIZER_SOURCE,
        )
        val seats = listOf(
            "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            "Serpico_CC Mono-Blue Terror" to PestControlTierOneMonoBlueTerrorReadiness.mainDeck(),
        )

        val environment = GameEnvironment.create(registry)
        environment.reset(
            GameConfig(
                players = seats.map { (name, deck) -> PlayerConfig(name, deck, startingLife = 20) },
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = 0,
                seed = assignment.seed,
            )
        )

        val opening = environment.playerIds.mapIndexed { index, player ->
            val state = environment.state
            val library = state.getLibrary(player)
            val hand = state.getHand(player)
            val otherCount = listOf(
                Zone.BATTLEFIELD,
                Zone.GRAVEYARD,
                Zone.STACK,
                Zone.EXILE,
                Zone.COMMAND,
                Zone.SIDEBOARD,
            ).sumOf { state.getZone(player, it).size }

            val libraryNames = library.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name
                    ?: error("opening library card lacks identity")
            }
            val handNames = hand.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name
                    ?: error("opening hand card lacks identity")
            }
            val expectedCounts = if (index == 0) {
                PestControlPreboardDecks.pestMainCounts
            } else {
                PestControlTierOneMonoBlueTerrorReadiness.mainCounts
            }
            require((libraryNames + handNames).groupingBy { it }.eachCount() == expectedCounts)

            MonoBlueTerrorOpeningConservation(
                seat = if (index == 0) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE,
                deckIdentity = if (index == 0) "PEST_CONTROL_V10" else "SERPICO_CC_MONO_BLUE_TERROR_60",
                deckSha256 = if (index == 0) PEST_CONTROL_V10_HASH else PEST_MONO_BLUE_TERROR_MAIN_SHA256,
                libraryCount = library.size,
                handCount = hand.size,
                otherZoneCount = otherCount,
                totalOwnedCards = library.size + hand.size + otherCount,
                orderedLibrarySha256 = sha256(
                    libraryNames.joinToString("\n", postfix = "\n").toByteArray()
                ),
            )
        }

        require(opening.all { it.libraryCount == 53 && it.handCount == 7 })
        require(opening.all { it.otherZoneCount == 0 && it.totalOwnedCards == 60 })

        return MonoBlueTerrorConstructionFixture(provenance, environment, opening)
    }

    private fun Long.toSeedHex(): String = "0x${toULong().toString(16).padStart(16, '0')}"
}

@Serializable
data class MonoBlueTerrorTelemetryTextTrace(
    val actionSequence: Int,
    val selectedActionJson: String,
    val emittedEventJson: List<String>,
)

@Serializable
data class MonoBlueTerrorTelemetryIndex(
    val cantripsAndSetup: List<TraceReference> = emptyList(),
    val graveyardAndEscape: List<TraceReference> = emptyList(),
    val threatsAndCostReduction: List<TraceReference> = emptyList(),
    val permission: List<TraceReference> = emptyList(),
    val tempoAndBounce: List<TraceReference> = emptyList(),
    val evasion: List<TraceReference> = emptyList(),
    val landsAndCycling: List<TraceReference> = emptyList(),
)

/** Pure index over canonical raw text. It cannot initialize or advance a game. */
object PestControlTierOneMonoBlueTerrorTelemetryContract {
    fun index(actions: List<PriorityAudit>): MonoBlueTerrorTelemetryIndex = indexText(
        actions.map { action ->
            MonoBlueTerrorTelemetryTextTrace(
                actionSequence = action.sequence,
                selectedActionJson = action.selectedAction.toString(),
                emittedEventJson = action.emittedEvents.map { it.toString() },
            )
        }
    )

    fun indexText(traces: List<MonoBlueTerrorTelemetryTextTrace>): MonoBlueTerrorTelemetryIndex {
        require(traces.map { it.actionSequence } == (1..traces.size).toList())

        fun refs(vararg terms: String): List<TraceReference> = buildList {
            traces.forEach { trace ->
                if (terms.any(trace.selectedActionJson::contains)) {
                    add(TraceReference(trace.actionSequence))
                }
                trace.emittedEventJson.forEachIndexed { eventIndex, event ->
                    if (terms.any(event::contains)) {
                        add(TraceReference(trace.actionSequence, eventIndex))
                    }
                }
            }
        }.distinct()

        return MonoBlueTerrorTelemetryIndex(
            cantripsAndSetup = refs(
                "Ponder", "Preordain", "Brainstorm", "Mental Note", "Thought Scour",
                "Lórien Revealed", "CardDrawnEvent", "Mill",
            ),
            graveyardAndEscape = refs(
                "Sleep of the Dead", "Escape", "Graveyard", "Exile", "Mental Note", "Thought Scour",
            ),
            threatsAndCostReduction = refs("Cryptic Serpent", "Tolarian Terror", "CostReduction", "costs less"),
            permission = refs("Counterspell", "Dispel", "Countered", "CounterSpell"),
            tempoAndBounce = refs("Deem Inferior", "Sleep of the Dead", "Tap", "PutOnLibrary"),
            evasion = refs("Artful Dodge", "Unblockable", "can't be blocked"),
            landsAndCycling = refs("Island", "Snow-Covered Island", "Lórien Revealed", "Islandcycling"),
        )
    }
}
