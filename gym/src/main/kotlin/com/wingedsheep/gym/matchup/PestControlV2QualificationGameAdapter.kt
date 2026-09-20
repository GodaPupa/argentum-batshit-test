package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

object PestControlV2QualificationGameAdapter {
    fun provenance(
        assignment: FrozenMatchupAssignment,
        executionCommit: String,
    ): MatchupProvenance {
        require(executionCommit.matches(Regex("[0-9a-f]{40}")))
        require(assignment.protocolId == PEST_V2_OFFICIAL_PROTOCOL)
        require(assignment.blockId == PEST_V2_QUALIFICATION_BLOCK)
        require(assignment.gameNumber in 1..PEST_V2_QUALIFICATION_GAMES)
        require(assignment.seedDecimal != 0L)
        require(assignment.seedHex == "0x${assignment.seedDecimal.toULong().toString(16).padStart(16, '0')}")
        require(assignment.gate4SourceCommit == PEST_V2_QUALIFIED_RUNNER)
        return MatchupProvenance(
            protocolId = PEST_V2_OFFICIAL_PROTOCOL,
            sourceCommit = executionCommit,
            pestSeat = assignment.pestSeat,
            startingDeck = assignment.startingPlayer,
            environment = MatchupEnvironmentIdentity.current(),
            entropyClassification = "FROZEN_EXPERIMENTAL_VECTOR",
            blockId = PEST_V2_QUALIFICATION_BLOCK,
            freezeCommit = PEST_V2_QUALIFICATION_FREEZE_IDENTITY.freezeCommit,
            executionCommit = executionCommit,
            orderedVectorSha256 = PEST_V2_QUALIFICATION_FREEZE_IDENTITY.orderedVectorSha256,
            assignmentCsvSha256 = PEST_V2_QUALIFICATION_FREEZE_IDENTITY.assignmentCsvSha256,
            freezeManifestSha256 = PEST_V2_QUALIFICATION_FREEZE_IDENTITY.freezeManifestSha256,
            gameNumber = assignment.gameNumber,
            seedDecimal = assignment.seedDecimal,
            seedHex = assignment.seedHex,
        )
    }

    fun initialize(
        registry: CardRegistry,
        assignment: FrozenMatchupAssignment,
        executionCommit: String,
    ): PestControlPreboardSession = PestControlPreboardSession.experimental(
        registry = registry,
        provenance = provenance(assignment, executionCommit),
        recordId = "PEST_CONTROL_V2_QUALIFICATION_GAME_${assignment.gameNumber.toString().padStart(2, '0')}",
        seed = assignment.seedDecimal,
    )
}
