package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_READINESS_COMMIT = "c9d435d429089562701fe57f80c9207198d05911"
const val PEST_MONO_BLUE_TERROR_READINESS_CI_RUN_ID = 35750852947L
const val PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID =
    "${PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID}_NONEXPERIMENTAL_SMOKE_4"
const val PEST_MONO_BLUE_TERROR_SMOKE_GAMES = 4

enum class MonoBlueTerrorSmokeHarnessState { DISABLED, AUTHORIZED }

data class MonoBlueTerrorSmokeCell(
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val startingDeck: MonoBlueTerrorStartingDeck,
)

enum class MonoBlueTerrorStartingDeck { PEST_CONTROL, MONO_BLUE_TERROR }

data class MonoBlueTerrorSmokeVectorIdentity(
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
)

data class MonoBlueTerrorSmokeHarnessReadiness(
    val protocolId: String = PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
    val blockId: String = PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
    val acceptedReadinessCommit: String = PEST_MONO_BLUE_TERROR_READINESS_COMMIT,
    val acceptedReadinessCiRunId: Long = PEST_MONO_BLUE_TERROR_READINESS_CI_RUN_ID,
    val expectedGames: Int = PEST_MONO_BLUE_TERROR_SMOKE_GAMES,
    val cells: List<MonoBlueTerrorSmokeCell> = PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate(),
    val vectorIdentity: MonoBlueTerrorSmokeVectorIdentity? = null,
    val state: MonoBlueTerrorSmokeHarnessState = MonoBlueTerrorSmokeHarnessState.DISABLED,
    val classification: String = "NONEXPERIMENTAL_HARNESS_VALIDATION_ONLY",
    val officialSeedsGenerated: Int = 0,
    val officialGamesAuthorized: Int = 0,
    val outcomeExposure: Int = 0,
)

/**
 * Pure validation boundary for a future four-game Mono-Blue Terror smoke.
 *
 * This object deliberately has no game initializer, execution method, entropy source, artifact
 * writer, workflow, or seed-freeze operation. The provenance-only adapter has no initializer, so
 * this harness still cannot initialize or execute a game.
 */
object PestControlTierOneMonoBlueTerrorSmokeHarness {
    fun cellTemplate(): List<MonoBlueTerrorSmokeCell> = listOf(
        MonoBlueTerrorSmokeCell(1, PestSeat.SEAT_ZERO, MonoBlueTerrorStartingDeck.PEST_CONTROL),
        MonoBlueTerrorSmokeCell(2, PestSeat.SEAT_ZERO, MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR),
        MonoBlueTerrorSmokeCell(3, PestSeat.SEAT_ONE, MonoBlueTerrorStartingDeck.PEST_CONTROL),
        MonoBlueTerrorSmokeCell(4, PestSeat.SEAT_ONE, MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR),
    )

    fun validationErrors(
        readiness: MonoBlueTerrorSmokeHarnessReadiness,
        registry: CardRegistry? = null,
    ): List<String> = buildList {
        addAll(
            PestControlTierOneMonoBlueTerrorReadiness.validationErrors(
                TierOneMonoBlueTerrorReadiness(),
                registry,
            )
        )
        if (readiness.protocolId != PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.blockId != PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID) add("smoke block mismatch")
        if (readiness.acceptedReadinessCommit != PEST_MONO_BLUE_TERROR_READINESS_COMMIT) {
            add("accepted readiness commit mismatch")
        }
        if (readiness.acceptedReadinessCiRunId != PEST_MONO_BLUE_TERROR_READINESS_CI_RUN_ID) {
            add("accepted readiness CI mismatch")
        }
        if (readiness.expectedGames != PEST_MONO_BLUE_TERROR_SMOKE_GAMES) {
            add("smoke must contain four games")
        }
        if (readiness.cells != cellTemplate()) add("smoke cell template mismatch")
        if (readiness.cells.map { it.gameNumber } != (1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES).toList()) {
            add("smoke game numbers must be exact and ordered")
        }
        if (readiness.cells.count { it.pestSeat == PestSeat.SEAT_ZERO } != 2 ||
            readiness.cells.count { it.pestSeat == PestSeat.SEAT_ONE } != 2
        ) add("smoke seat allocation must be 2/2")
        if (readiness.cells.count { it.startingDeck == MonoBlueTerrorStartingDeck.PEST_CONTROL } != 2 ||
            readiness.cells.count { it.startingDeck == MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR } != 2
        ) add("smoke play/draw allocation must be 2/2")
        if (readiness.vectorIdentity != null) {
            add("smoke vector must remain absent during harness construction")
        }
        if (readiness.state != MonoBlueTerrorSmokeHarnessState.DISABLED) {
            add("smoke harness must remain disabled")
        }
        if (readiness.classification != "NONEXPERIMENTAL_HARNESS_VALIDATION_ONLY") {
            add("smoke classification mismatch")
        }
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
    }

    fun activationErrors(
        readiness: MonoBlueTerrorSmokeHarnessReadiness,
        registry: CardRegistry,
        explicitAuthorization: Boolean,
        isUnitTestProcess: Boolean,
        attemptNumber: Int,
        priorOutputExists: Boolean,
    ): List<String> = buildList {
        addAll(validationErrors(readiness, registry))
        if (readiness.state != MonoBlueTerrorSmokeHarnessState.AUTHORIZED) {
            add("smoke harness is not AUTHORIZED")
        }
        if (readiness.vectorIdentity == null) add("smoke vector is not frozen")
        if (!explicitAuthorization) add("explicit smoke authorization is missing")
        if (isUnitTestProcess) add("unit tests cannot activate the smoke harness")
        if (attemptNumber != 1) add("smoke retry is forbidden")
        if (priorOutputExists) add("smoke output already exists")
        add("game adapter has no official initialization method")
        add("no execution method is defined")
    }
}
