package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

object PestControlV2OfficialGameAdapter {
    fun provenance(
        assignment: V2OfficialAssignment,
        executionCommit: String,
    ): MatchupProvenance {
        require(executionCommit.matches(Regex("[0-9a-f]{40}"))) { "execution commit must be a full lowercase SHA" }
        require(assignment.game in 1..PEST_V2_EXPECTED_GAMES)
        require(assignment.seed != 0L)
        require(assignment.seedHex == "0x${assignment.seed.toULong().toString(16).padStart(16, '0')}")
        return MatchupProvenance(
            sourceCommit = executionCommit,
            pestSeat = assignment.pestSeat,
            startingDeck = assignment.starter,
            environment = MatchupEnvironmentIdentity.current(),
            entropyClassification = "FROZEN_EXPERIMENTAL_VECTOR",
            blockId = PEST_V2_OFFICIAL_BLOCK,
            freezeCommit = PEST_V2_FREEZE_COMMIT,
            executionCommit = executionCommit,
            orderedVectorSha256 = PEST_V2_ORDERED_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_V2_ASSIGNMENT_CSV_SHA256,
            freezeManifestSha256 = PEST_V2_FREEZE_MANIFEST_SHA256,
            gameNumber = assignment.game,
            seedDecimal = assignment.seed,
            seedHex = assignment.seedHex,
        )
    }

    fun initialize(
        registry: CardRegistry,
        assignment: V2OfficialAssignment,
        executionCommit: String,
    ): PestControlPreboardSession = PestControlPreboardSession.experimental(
        registry = registry,
        provenance = provenance(assignment, executionCommit),
        recordId = "PEST_CONTROL_V2_OFFICIAL_GAME_${assignment.game.toString().padStart(2, '0')}",
        seed = assignment.seed,
    )
}
