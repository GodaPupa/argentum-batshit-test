package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_READINESS_COMMIT = "d1b55bfe4b4accff025d20fc1fc9b99913338585"
const val PEST_GRIXIS_READINESS_CI_RUN_ID = 35544872664L
const val PEST_GRIXIS_SMOKE_BLOCK_ID = "${PEST_GRIXIS_PREBOARD_PROTOCOL_ID}_NONEXPERIMENTAL_SMOKE_4"
const val PEST_GRIXIS_SMOKE_GAMES = 4

enum class GrixisSmokeHarnessState { DISABLED, AUTHORIZED }

data class GrixisSmokeCell(
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
)

enum class GrixisStartingDeck { PEST_CONTROL, GRIXIS_AFFINITY }

data class GrixisSmokeVectorIdentity(
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
)

data class GrixisSmokeHarnessReadiness(
    val protocolId: String = PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
    val blockId: String = PEST_GRIXIS_SMOKE_BLOCK_ID,
    val acceptedReadinessCommit: String = PEST_GRIXIS_READINESS_COMMIT,
    val acceptedReadinessCiRunId: Long = PEST_GRIXIS_READINESS_CI_RUN_ID,
    val expectedGames: Int = PEST_GRIXIS_SMOKE_GAMES,
    val cells: List<GrixisSmokeCell> = PestControlTierOneGrixisSmokeHarness.cellTemplate(),
    val vectorIdentity: GrixisSmokeVectorIdentity? = null,
    val state: GrixisSmokeHarnessState = GrixisSmokeHarnessState.DISABLED,
    val classification: String = "NONEXPERIMENTAL_HARNESS_VALIDATION_ONLY",
    val officialSeedsGenerated: Int = 0,
    val officialGamesAuthorized: Int = 0,
    val outcomeExposure: Int = 0,
)

/**
 * Pure validation boundary for a future four-game smoke. This object deliberately has no game
 * initializer, execution method, entropy source, or artifact writer. It cannot initialize a game.
 */
object PestControlTierOneGrixisSmokeHarness {
    fun cellTemplate(): List<GrixisSmokeCell> = listOf(
        GrixisSmokeCell(1, PestSeat.SEAT_ZERO, GrixisStartingDeck.PEST_CONTROL),
        GrixisSmokeCell(2, PestSeat.SEAT_ZERO, GrixisStartingDeck.GRIXIS_AFFINITY),
        GrixisSmokeCell(3, PestSeat.SEAT_ONE, GrixisStartingDeck.PEST_CONTROL),
        GrixisSmokeCell(4, PestSeat.SEAT_ONE, GrixisStartingDeck.GRIXIS_AFFINITY),
    )

    fun validationErrors(
        readiness: GrixisSmokeHarnessReadiness,
        registry: CardRegistry? = null,
    ): List<String> = buildList {
        addAll(PestControlTierOneGrixisReadiness.validationErrors(TierOneGrixisReadiness(), registry))
        if (readiness.protocolId != PEST_GRIXIS_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.blockId != PEST_GRIXIS_SMOKE_BLOCK_ID) add("smoke block mismatch")
        if (readiness.acceptedReadinessCommit != PEST_GRIXIS_READINESS_COMMIT) {
            add("accepted readiness commit mismatch")
        }
        if (readiness.acceptedReadinessCiRunId != PEST_GRIXIS_READINESS_CI_RUN_ID) {
            add("accepted readiness CI mismatch")
        }
        if (readiness.expectedGames != PEST_GRIXIS_SMOKE_GAMES) add("smoke must contain four games")
        if (readiness.cells != cellTemplate()) add("smoke cell template mismatch")
        if (readiness.cells.map { it.gameNumber } != (1..PEST_GRIXIS_SMOKE_GAMES).toList()) {
            add("smoke game numbers must be exact and ordered")
        }
        if (readiness.cells.count { it.pestSeat == PestSeat.SEAT_ZERO } != 2 ||
            readiness.cells.count { it.pestSeat == PestSeat.SEAT_ONE } != 2
        ) add("smoke seat allocation must be 2/2")
        if (readiness.cells.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } != 2 ||
            readiness.cells.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } != 2
        ) add("smoke play/draw allocation must be 2/2")
        if (readiness.vectorIdentity != null) add("smoke vector must remain absent during harness construction")
        if (readiness.state != GrixisSmokeHarnessState.DISABLED) add("smoke harness must remain disabled")
        if (readiness.classification != "NONEXPERIMENTAL_HARNESS_VALIDATION_ONLY") {
            add("smoke classification mismatch")
        }
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
    }

    fun activationErrors(
        readiness: GrixisSmokeHarnessReadiness,
        registry: CardRegistry,
        explicitAuthorization: Boolean,
        isUnitTestProcess: Boolean,
        attemptNumber: Int,
        priorOutputExists: Boolean,
    ): List<String> = buildList {
        addAll(validationErrors(readiness, registry))
        if (readiness.state != GrixisSmokeHarnessState.AUTHORIZED) add("smoke harness is not AUTHORIZED")
        if (readiness.vectorIdentity == null) add("smoke vector is not frozen")
        if (!explicitAuthorization) add("explicit smoke authorization is missing")
        if (isUnitTestProcess) add("unit tests cannot activate the smoke harness")
        if (attemptNumber != 1) add("smoke retry is forbidden")
        if (priorOutputExists) add("smoke output already exists")
        add("game adapter has no official initialization method")
        add("no execution method is defined")
    }
}
