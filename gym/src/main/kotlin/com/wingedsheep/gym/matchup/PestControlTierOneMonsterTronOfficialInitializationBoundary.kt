package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.core.Zone

const val NONEXPERIMENTAL_MONSTER_TRON_INITIALIZER_FIXTURE_ENTROPY: Long =
    0x6D74_726F_6E00_0001L
const val PEST_MONSTER_TRON_DISABLED_INITIALIZER_CONSTRUCTION_SHA256 =
    "bab0cb5ba9473473ce5576c28535f62cfe49dc28d4c73e6432f0769fbfd1f1d7"
const val PEST_MONSTER_TRON_OFFICIAL_INITIALIZATION_BLOCKER_SHA256 =
    "6eefe83b06fc89c4d4214724ee31983659e1b199cc9ef2b27cdc0240925abb34"

private data class MonsterTronConstructionFixture(
    val environment: GameEnvironment,
    val opening: List<MonsterTronOpeningConservation>,
)

data class MonsterTronOpeningConservation(
    val seat: PestSeat,
    val deckIdentity: String,
    val deckSha256: String,
    val libraryCount: Int,
    val handCount: Int,
    val otherZoneCount: Int,
    val totalOwnedCards: Int,
)

private data class MonsterTronDisabledInitializerConstructionProof(val sha256: String)

data class MonsterTronOfficialInitializationBoundaryResult(
    val contractErrors: List<String>,
    val activationBlockers: List<String>,
    val blockerSha256: String,
    val constructionValidationSha256: String?,
    val officialInitializerImplemented: Boolean = true,
    val officialInitializerEnabled: Boolean = false,
    val disabledConstructionFixturesInitialized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val failClosed: Boolean
        get() = contractErrors.isEmpty() &&
            officialInitializerImplemented &&
            !officialInitializerEnabled &&
            activationBlockers.contains("official initializer is disabled")
}

/**
 * Private construction-only initializer. It accepts no caller-provided seed, assignment, vector,
 * authorization or execution commit and never returns its GameEnvironment outside this file.
 */
private object PestControlTierOneMonsterTronDisabledOfficialInitializer {
    fun validateConstruction(registry: CardRegistry): MonsterTronDisabledInitializerConstructionProof {
        val fixture = initialize(registry)
        val opening = fixture.opening.sortedBy { it.seat.ordinal }
        require(
            opening.map {
                listOf(
                    it.seat.name,
                    it.deckIdentity,
                    it.deckSha256,
                    it.libraryCount,
                    it.handCount,
                    it.otherZoneCount,
                    it.totalOwnedCards,
                ).joinToString("|")
            } == listOf(
                "SEAT_ZERO|PEST_CONTROL_V10|$PEST_CONTROL_V10_HASH|53|7|0|60",
                "SEAT_ONE|MEHANSKE_MONSTER_TRON_60|$PEST_MONSTER_TRON_MAIN_SHA256|53|7|0|60",
            )
        )

        val seedHex =
            "0x${NONEXPERIMENTAL_MONSTER_TRON_INITIALIZER_FIXTURE_ENTROPY.toULong().toString(16).padStart(16, '0')}"
        val proofBytes = listOf(
            "pest-control-tier-one-monster-tron-disabled-official-initializer-v1",
            "seedHex=$seedHex",
            "protocolId=$PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "seat0=PEST_CONTROL_V10|$PEST_CONTROL_V10_HASH|53|7|0|60",
            "seat1=MEHANSKE_MONSTER_TRON_60|$PEST_MONSTER_TRON_MAIN_SHA256|53|7|0|60",
            "excludedFromExperimentalEvidence=true",
            "excludedFromFutureSeedOverlapRegistry=true",
            "submittedActions=0",
        ).joinToString("\n", postfix = "\n").toByteArray()

        return MonsterTronDisabledInitializerConstructionProof(sha256(proofBytes))
    }

    private fun initialize(registry: CardRegistry): MonsterTronConstructionFixture {
        val contractErrors = PestControlTierOneMonsterTronRunnerContract.validationErrors(registry = registry)
        require(contractErrors.isEmpty()) { contractErrors.joinToString("; ") }

        val seats = listOf(
            "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            "mehanske Monster Tron" to PestControlTierOneMonsterTronReadiness.mainDeck(),
        )
        val environment = GameEnvironment.create(registry)
        environment.reset(
            GameConfig(
                players = seats.map { (name, deck) -> PlayerConfig(name, deck, startingLife = 20) },
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = 0,
                seed = NONEXPERIMENTAL_MONSTER_TRON_INITIALIZER_FIXTURE_ENTROPY,
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

            val names = (library + hand).map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name
                    ?: error("opening card lacks identity")
            }
            val expected = if (index == 0) {
                PestControlPreboardDecks.pestMainCounts
            } else {
                PestControlTierOneMonsterTronAdmission.mainCounts
            }
            require(names.groupingBy { it }.eachCount() == expected)

            MonsterTronOpeningConservation(
                seat = if (index == 0) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE,
                deckIdentity = if (index == 0) "PEST_CONTROL_V10" else "MEHANSKE_MONSTER_TRON_60",
                deckSha256 = if (index == 0) PEST_CONTROL_V10_HASH else PEST_MONSTER_TRON_MAIN_SHA256,
                libraryCount = library.size,
                handCount = hand.size,
                otherZoneCount = otherCount,
                totalOwnedCards = library.size + hand.size + otherCount,
            )
        }
        require(opening.all { it.libraryCount == 53 && it.handCount == 7 })
        require(opening.all { it.otherZoneCount == 0 && it.totalOwnedCards == 60 })
        return MonsterTronConstructionFixture(environment, opening)
    }
}

/** Public validation boundary. There is deliberately no public environment-returning method. */
object PestControlTierOneMonsterTronOfficialInitializationBoundary {
    fun inspect(registry: CardRegistry): MonsterTronOfficialInitializationBoundaryResult {
        val contractErrors = mutableListOf<String>()
        contractErrors += PestControlTierOneMonsterTronSmokeHarness.validationErrors(registry = registry)

        val proof = runCatching {
            PestControlTierOneMonsterTronDisabledOfficialInitializer.validateConstruction(registry)
        }.onFailure {
            contractErrors += "disabled initializer construction validation failed"
        }.getOrNull()

        if (proof != null && proof.sha256 != PEST_MONSTER_TRON_DISABLED_INITIALIZER_CONSTRUCTION_SHA256) {
            contractErrors += "disabled initializer construction hash mismatch"
        }

        val blockers = listOf(
            "smoke harness is disabled",
            "official seed vector is absent",
            "official assignment is absent",
            "execution commit is absent",
            "durable attempt marker is absent",
            "official initializer is disabled",
        )
        val blockerSha256 =
            sha256(blockers.joinToString("\n", postfix = "\n").toByteArray())
        if (blockerSha256 != PEST_MONSTER_TRON_OFFICIAL_INITIALIZATION_BLOCKER_SHA256) {
            contractErrors += "canonical activation blocker hash mismatch"
        }

        return MonsterTronOfficialInitializationBoundaryResult(
            contractErrors = contractErrors.distinct(),
            activationBlockers = blockers,
            blockerSha256 = blockerSha256,
            constructionValidationSha256 = proof?.sha256,
            disabledConstructionFixturesInitialized = if (proof == null) 0 else 1,
        )
    }
}
