package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.core.Zone
import kotlinx.serialization.Serializable

/** Fixed construction entropy. It is not an experimental seed and is never eligible for a registry. */
const val NONEXPERIMENTAL_GRIXIS_INITIALIZER_FIXTURE_ENTROPY: Long = 0x4752_4958_4953_0001L

private const val SYNTHETIC_GRIXIS_INITIALIZER_SOURCE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private val SYNTHETIC_GRIXIS_INITIALIZER_IDENTITY = GrixisSmokeVectorIdentity(
    freezeCommit = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    orderedVectorSha256 = "c".repeat(64),
    assignmentCsvSha256 = "d".repeat(64),
    freezeManifestSha256 = "e".repeat(64),
)

@Serializable
data class GrixisOpeningConservation(
    val seat: PestSeat,
    val deckIdentity: String,
    val deckSha256: String,
    val libraryCount: Int,
    val handCount: Int,
    val otherZoneCount: Int,
    val totalOwnedCards: Int,
    val orderedLibrarySha256: String,
)

data class GrixisConstructionFixture(
    val provenance: GrixisSmokeProvenance,
    val environment: GameEnvironment,
    val openingConservation: List<GrixisOpeningConservation>,
    val excludedFromExperimentalEvidence: Boolean = true,
    val excludedFromFutureSeedOverlapRegistry: Boolean = true,
)

/**
 * The only initializer in this gate. It accepts exactly one fixed nonexperimental fixture and has
 * no overload capable of accepting a future frozen smoke assignment.
 */
object PestControlTierOneGrixisInitializationContract {
    fun initializeConstructionFixture(registry: CardRegistry): GrixisConstructionFixture {
        val readinessErrors = PestControlTierOneGrixisReadiness.validationErrors(TierOneGrixisReadiness(), registry)
        require(readinessErrors.isEmpty()) { readinessErrors.joinToString("; ") }
        val assignment = GrixisSmokeAssignment(
            gameNumber = 1,
            seed = NONEXPERIMENTAL_GRIXIS_INITIALIZER_FIXTURE_ENTROPY,
            seedHex = NONEXPERIMENTAL_GRIXIS_INITIALIZER_FIXTURE_ENTROPY.toSeedHex(),
            pestSeat = PestSeat.SEAT_ZERO,
            grixisSeat = PestSeat.SEAT_ONE,
            startingDeck = GrixisStartingDeck.PEST_CONTROL,
        )
        val provenance = PestControlTierOneGrixisGameAdapter.provenance(
            assignment,
            SYNTHETIC_GRIXIS_INITIALIZER_IDENTITY,
            SYNTHETIC_GRIXIS_INITIALIZER_SOURCE,
        )
        val seats = listOf(
            "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            "Pasquale Grixis Affinity" to PestControlTierOneGrixisReadiness.mainDeck(),
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
                Zone.BATTLEFIELD, Zone.GRAVEYARD, Zone.STACK, Zone.EXILE, Zone.COMMAND, Zone.SIDEBOARD,
            ).sumOf { state.getZone(player, it).size }
            val libraryNames = library.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name ?: error("opening library card lacks identity")
            }
            val handNames = hand.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name ?: error("opening hand card lacks identity")
            }
            val expectedCounts = if (index == 0) {
                PestControlPreboardDecks.pestMainCounts
            } else {
                PestControlTierOneGrixisReadiness.mainCounts
            }
            require((libraryNames + handNames).groupingBy { it }.eachCount() == expectedCounts)
            GrixisOpeningConservation(
                seat = if (index == 0) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE,
                deckIdentity = if (index == 0) "PEST_CONTROL_V10" else "PASQUALE_GRIXIS_AFFINITY_60",
                deckSha256 = if (index == 0) PEST_CONTROL_V10_HASH else PEST_GRIXIS_MAIN_SHA256,
                libraryCount = library.size,
                handCount = hand.size,
                otherZoneCount = otherCount,
                totalOwnedCards = library.size + hand.size + otherCount,
                orderedLibrarySha256 = sha256(libraryNames.joinToString("\n", postfix = "\n").toByteArray()),
            )
        }
        require(opening.all { it.libraryCount == 53 && it.handCount == 7 })
        require(opening.all { it.otherZoneCount == 0 && it.totalOwnedCards == 60 })
        return GrixisConstructionFixture(provenance, environment, opening)
    }

    private fun Long.toSeedHex(): String = "0x${toULong().toString(16).padStart(16, '0')}"
}

@Serializable
data class GrixisTelemetryTextTrace(
    val actionSequence: Int,
    val selectedActionJson: String,
    val emittedEventJson: List<String>,
)

@Serializable
data class GrixisTelemetryIndex(
    val affinityAndCostReduction: List<TraceReference> = emptyList(),
    val artifactSacrificeAndDraw: List<TraceReference> = emptyList(),
    val familiarDiscard: List<TraceReference> = emptyList(),
    val toxinAndShaman: List<TraceReference> = emptyList(),
    val removalAndDamage: List<TraceReference> = emptyList(),
    val artifactLands: List<TraceReference> = emptyList(),
    val graveyardAndRecursion: List<TraceReference> = emptyList(),
)

/** Pure index over canonical raw text. It cannot initialize or advance a game. */
object PestControlTierOneGrixisTelemetryContract {
    fun index(actions: List<PriorityAudit>): GrixisTelemetryIndex = indexText(
        actions.map { action ->
            GrixisTelemetryTextTrace(
                actionSequence = action.sequence,
                selectedActionJson = action.selectedAction.toString(),
                emittedEventJson = action.emittedEvents.map { it.toString() },
            )
        }
    )

    fun indexText(traces: List<GrixisTelemetryTextTrace>): GrixisTelemetryIndex {
        require(traces.map { it.actionSequence } == (1..traces.size).toList())
        fun refs(vararg terms: String): List<TraceReference> = buildList {
            traces.forEach { trace ->
                if (terms.any(trace.selectedActionJson::contains)) add(TraceReference(trace.actionSequence))
                trace.emittedEventJson.forEachIndexed { eventIndex, event ->
                    if (terms.any(event::contains)) add(TraceReference(trace.actionSequence, eventIndex))
                }
            }
        }.distinct()
        return GrixisTelemetryIndex(
            affinityAndCostReduction = refs("Myr Enforcer", "Thoughtcast", "Affinity"),
            artifactSacrificeAndDraw = refs(
                "Fanatical Offering", "Reckoner's Bargain", "Makeshift Munitions", "Sacrifice", "CardDrawnEvent",
            ),
            familiarDiscard = refs("Refurbished Familiar", "CardsDiscardedEvent"),
            toxinAndShaman = refs("Toxin Analysis", "Krark-Clan Shaman", "Deathtouch"),
            removalAndDamage = refs("Cast Down", "Galvanic Blast", "Makeshift Munitions", "DamageDealtEvent"),
            artifactLands = refs(
                "Drossforge Bridge", "Great Furnace", "Mistvault Bridge", "Seat of the Synod",
                "Silverbluff Bridge", "Vault of Whispers",
            ),
            graveyardAndRecursion = refs("Blood Fountain", "Nihil Spellbomb", "Graveyard", "Return"),
        )
    }
}
