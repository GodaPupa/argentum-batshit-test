package com.wingedsheep.gym.matchup

const val PEST_V2_QUALIFICATION_BLOCK = "${PEST_V2_OFFICIAL_PROTOCOL}_V2_QUALIFICATION_50"
const val PEST_V2_ACCEPTED_CALIBRATION_COMMIT = "c4f02ff2c19bd256a356e1f97a18d762484ecb7d"
const val PEST_V2_ACCEPTED_CALIBRATION_RUN_ID = 35525262024L
const val PEST_V2_ACCEPTED_CALIBRATION_ARTIFACT_ID = 10610310479L
const val PEST_V2_ACCEPTED_CALIBRATION_ARTIFACT_DIGEST =
    "sha256:577ecf33bbbd6fa2b9967867228b6eabb81b6b2c5590f2c0670f5d3536f80a2b"
const val PEST_V2_QUALIFICATION_GAMES = 50
const val PEST_V2_QUALIFICATION_SHARDS = 2
const val PEST_V2_QUALIFICATION_GAMES_PER_SHARD = 25
const val PEST_V2_QUALIFICATION_SHARD_TIMEOUT_MINUTES = 240

enum class V2QualificationRunnerState { DISABLED, AUTHORIZED }

data class V2QualificationShardTemplate(
    val shardId: String,
    val firstGameNumber: Int,
    val lastGameNumber: Int,
    val expectedGames: Int,
    val timeoutMinutes: Int,
)

data class V2QualificationAcceptanceCriteria(
    val expectedGames: Int = PEST_V2_QUALIFICATION_GAMES,
    val requiredTerminalGames: Int = PEST_V2_QUALIFICATION_GAMES,
    val maximumProtocolDefects: Int = 0,
    val maximumRejectedActions: Int = 0,
    val maximumFallbackActions: Int = 0,
    val requireExactFrozenOrder: Boolean = true,
    val requireNoRetryOrReplacement: Boolean = true,
    val requirePostExecutionTraceAudit: Boolean = true,
    val performanceResultControlsIntegrityDisposition: Boolean = false,
)

data class V2QualificationFreezeIdentity(
    val freezeCommit: String,
    val registrySha256: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
)

data class V2QualificationReadiness(
    val protocolId: String = PEST_V2_OFFICIAL_PROTOCOL,
    val blockId: String = PEST_V2_QUALIFICATION_BLOCK,
    val qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    val acceptedCalibrationCommit: String = PEST_V2_ACCEPTED_CALIBRATION_COMMIT,
    val acceptedCalibrationRunId: Long = PEST_V2_ACCEPTED_CALIBRATION_RUN_ID,
    val acceptedCalibrationArtifactId: Long = PEST_V2_ACCEPTED_CALIBRATION_ARTIFACT_ID,
    val acceptedCalibrationArtifactDigest: String = PEST_V2_ACCEPTED_CALIBRATION_ARTIFACT_DIGEST,
    val expectedGames: Int = PEST_V2_QUALIFICATION_GAMES,
    val pestPlayGames: Int = 25,
    val pestDrawGames: Int = 25,
    val pestSeatZeroGames: Int = 25,
    val pestSeatOneGames: Int = 25,
    val jointCellCounts: List<Int> = listOf(12, 12, 13, 13),
    val shards: List<V2QualificationShardTemplate> = PestControlV2QualificationReadiness.shardTemplates(),
    val criteria: V2QualificationAcceptanceCriteria = V2QualificationAcceptanceCriteria(),
    val freezeIdentity: V2QualificationFreezeIdentity? = null,
    val configuredRunnerState: V2QualificationRunnerState = V2QualificationRunnerState.DISABLED,
)

object PestControlV2QualificationReadiness {
    fun shardTemplates(): List<V2QualificationShardTemplate> = listOf(
        V2QualificationShardTemplate(
            shardId = "SHARD_01_OF_02",
            firstGameNumber = 1,
            lastGameNumber = 25,
            expectedGames = PEST_V2_QUALIFICATION_GAMES_PER_SHARD,
            timeoutMinutes = PEST_V2_QUALIFICATION_SHARD_TIMEOUT_MINUTES,
        ),
        V2QualificationShardTemplate(
            shardId = "SHARD_02_OF_02",
            firstGameNumber = 26,
            lastGameNumber = 50,
            expectedGames = PEST_V2_QUALIFICATION_GAMES_PER_SHARD,
            timeoutMinutes = PEST_V2_QUALIFICATION_SHARD_TIMEOUT_MINUTES,
        ),
    )

    fun validationErrors(readiness: V2QualificationReadiness): List<String> = buildList {
        if (readiness.protocolId != PEST_V2_OFFICIAL_PROTOCOL) add("protocol mismatch")
        if (readiness.blockId != PEST_V2_QUALIFICATION_BLOCK) add("block mismatch")
        if (readiness.qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) add("qualified runner mismatch")
        if (readiness.acceptedCalibrationCommit != PEST_V2_ACCEPTED_CALIBRATION_COMMIT) {
            add("accepted calibration commit mismatch")
        }
        if (readiness.acceptedCalibrationRunId != PEST_V2_ACCEPTED_CALIBRATION_RUN_ID ||
            readiness.acceptedCalibrationArtifactId != PEST_V2_ACCEPTED_CALIBRATION_ARTIFACT_ID ||
            readiness.acceptedCalibrationArtifactDigest != PEST_V2_ACCEPTED_CALIBRATION_ARTIFACT_DIGEST
        ) add("accepted calibration artifact mismatch")
        if (readiness.expectedGames != PEST_V2_QUALIFICATION_GAMES) add("qualification block must contain 50 games")
        if (readiness.pestPlayGames != 25 || readiness.pestDrawGames != 25) add("play/draw allocation must be 25/25")
        if (readiness.pestSeatZeroGames != 25 || readiness.pestSeatOneGames != 25) add("seat allocation must be 25/25")
        if (readiness.jointCellCounts.sorted() != listOf(12, 12, 13, 13)) add("joint cells must be 12/12/13/13")
        if (readiness.shards != shardTemplates()) add("shard templates mismatch")
        if (readiness.criteria != V2QualificationAcceptanceCriteria()) add("acceptance criteria mismatch")
        if (readiness.configuredRunnerState != V2QualificationRunnerState.DISABLED) {
            add("readiness runner must remain DISABLED")
        }
        readiness.freezeIdentity?.let { freeze ->
            if (!freeze.freezeCommit.isLowerHex(40)) add("freeze commit is invalid")
            if (!freeze.registrySha256.isLowerHex(64)) add("registry hash is invalid")
            if (!freeze.orderedVectorSha256.isLowerHex(64)) add("vector hash is invalid")
            if (!freeze.assignmentCsvSha256.isLowerHex(64)) add("assignment CSV hash is invalid")
            if (!freeze.freezeManifestSha256.isLowerHex(64)) add("freeze manifest hash is invalid")
        }
    }

    fun activationErrors(
        readiness: V2QualificationReadiness,
        explicitAuthorization: Boolean,
        isUnitTestProcess: Boolean,
        attemptNumber: Int,
        priorOutputExists: Boolean,
    ): List<String> = buildList {
        addAll(validationErrors(readiness))
        if (readiness.configuredRunnerState != V2QualificationRunnerState.AUTHORIZED) add("runner is not AUTHORIZED")
        if (readiness.freezeIdentity == null) add("fresh qualification freeze is not attached")
        if (!explicitAuthorization) add("explicit execution acknowledgement is missing")
        if (isUnitTestProcess) add("unit tests cannot activate the runner")
        if (attemptNumber != 1) add("qualification retry is forbidden")
        if (priorOutputExists) add("qualification output already exists")
    }

    private fun String.isLowerHex(length: Int): Boolean =
        this.length == length && all { it in '0'..'9' || it in 'a'..'f' }
}
