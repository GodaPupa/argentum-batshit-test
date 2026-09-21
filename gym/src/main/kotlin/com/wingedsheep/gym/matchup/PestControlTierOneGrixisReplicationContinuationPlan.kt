package com.wingedsheep.gym.matchup

const val PEST_GRIXIS_CONTINUATION_FIRST_GAME = 2
const val PEST_GRIXIS_CONTINUATION_LAST_GAME = 12
const val PEST_GRIXIS_CONTINUATION_GAMES = 11
const val PEST_GRIXIS_CONTINUATION_CLASSIFICATION = "SALVAGE_CONTINUATION_11"

object PestControlTierOneGrixisReplicationContinuationPlan {
    fun untouchedSuffix(input: GrixisReplicationExecutionInput): List<GrixisSmokeAssignment> {
        require(input.assignments.size == PEST_GRIXIS_REPLICATION_GAMES)
        require(input.assignments.map { it.gameNumber } == (1..12).toList())
        require(input.assignments.map { it.seed } == input.seeds)
        require(input.vectorIdentity.orderedVectorSha256 == PEST_GRIXIS_REPLICATION_VECTOR_SHA256)
        require(input.vectorIdentity.assignmentCsvSha256 == PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256)
        require(input.vectorIdentity.freezeManifestSha256 == PEST_GRIXIS_REPLICATION_MANIFEST_SHA256)

        val suffix = input.assignments.drop(1)
        require(suffix.size == PEST_GRIXIS_CONTINUATION_GAMES)
        require(suffix.map { it.gameNumber } == (2..12).toList())
        require(suffix.none { it.gameNumber == 1 })
        require(suffix.map { it.seed }.distinct().size == PEST_GRIXIS_CONTINUATION_GAMES)
        require(suffix.none { it.seed == 0L })
        return suffix
    }
}
