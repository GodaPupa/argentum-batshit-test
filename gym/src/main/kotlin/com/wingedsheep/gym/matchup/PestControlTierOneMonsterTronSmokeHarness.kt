package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONSTER_TRON_SMOKE_BLOCK_ID =
    "${PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID}_NONEXPERIMENTAL_SMOKE_4"
const val PEST_MONSTER_TRON_SMOKE_GAMES = 4

enum class MonsterTronStartingDeck { PEST_CONTROL, MONSTER_TRON }

data class MonsterTronSmokeCell(
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val startingDeck: MonsterTronStartingDeck,
)

data class MonsterTronSmokeHarnessReadiness(
    val protocolId: String = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
    val blockId: String = PEST_MONSTER_TRON_SMOKE_BLOCK_ID,
    val expectedGames: Int = PEST_MONSTER_TRON_SMOKE_GAMES,
    val cells: List<MonsterTronSmokeCell> = PestControlTierOneMonsterTronSmokeHarness.cellTemplate(),
    val state: String = "DISABLED",
    val classification: String = "NONEXPERIMENTAL_HARNESS_CONSTRUCTION_ONLY",
    val vectorIdentityPresent: Boolean = false,
    val officialSeedsGenerated: Int = 0,
    val officialGamesAuthorized: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
)

/** Seedless, disabled declaration of the prospective four-cell Monster Tron smoke. */
object PestControlTierOneMonsterTronSmokeHarness {
    fun cellTemplate(): List<MonsterTronSmokeCell> = listOf(
        MonsterTronSmokeCell(1, PestSeat.SEAT_ZERO, MonsterTronStartingDeck.PEST_CONTROL),
        MonsterTronSmokeCell(2, PestSeat.SEAT_ZERO, MonsterTronStartingDeck.MONSTER_TRON),
        MonsterTronSmokeCell(3, PestSeat.SEAT_ONE, MonsterTronStartingDeck.PEST_CONTROL),
        MonsterTronSmokeCell(4, PestSeat.SEAT_ONE, MonsterTronStartingDeck.MONSTER_TRON),
    )

    fun validationErrors(
        readiness: MonsterTronSmokeHarnessReadiness = MonsterTronSmokeHarnessReadiness(),
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(PestControlTierOneMonsterTronRunnerContract.validationErrors(registry = registry))
        if (readiness.protocolId != PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.blockId != PEST_MONSTER_TRON_SMOKE_BLOCK_ID) add("smoke block mismatch")
        if (readiness.expectedGames != PEST_MONSTER_TRON_SMOKE_GAMES) add("smoke must contain four games")
        if (readiness.cells != cellTemplate()) add("smoke cell template mismatch")
        if (readiness.cells.map { it.gameNumber } != (1..4).toList()) add("smoke game order mismatch")
        if (readiness.cells.count { it.pestSeat == PestSeat.SEAT_ZERO } != 2 ||
            readiness.cells.count { it.pestSeat == PestSeat.SEAT_ONE } != 2
        ) add("smoke seat allocation must be 2/2")
        if (readiness.cells.count { it.startingDeck == MonsterTronStartingDeck.PEST_CONTROL } != 2 ||
            readiness.cells.count { it.startingDeck == MonsterTronStartingDeck.MONSTER_TRON } != 2
        ) add("smoke starting-deck allocation must be 2/2")
        val joint = readiness.cells.groupingBy { it.pestSeat to it.startingDeck }.eachCount()
        if (joint.values.toSet() != setOf(1) || joint.size != 4) {
            add("each seat x starting-deck cell must appear exactly once")
        }
        if (readiness.state != "DISABLED") add("smoke harness must remain disabled")
        if (readiness.classification != "NONEXPERIMENTAL_HARNESS_CONSTRUCTION_ONLY") {
            add("smoke classification mismatch")
        }
        if (readiness.vectorIdentityPresent) add("official smoke vector must remain absent")
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.officialGamesInitialized != 0) add("official games initialized must remain zero")
        if (readiness.officialActionsSubmitted != 0) add("official actions must remain zero")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
    }
}
