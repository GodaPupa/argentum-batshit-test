package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

const val PEST_REPLACEMENT_READINESS_BASELINE = "9af6106915e4b41ab1d09d451633b7e09ab64bb4"
const val PEST_SHARDED_PLAN_SCHEMA = "pest-control-preboard-sharded-plan@v1"
const val PEST_SHARD_ARTIFACT_SCHEMA = "pest-control-preboard-shard-artifact@v1"
const val PEST_SHARD_AGGREGATE_SCHEMA = "pest-control-preboard-shard-aggregate@v1"
const val PEST_SHARD_COUNT = 2
const val PEST_GAMES_PER_SHARD = 25
const val PEST_SHARD_TIMEOUT_MINUTES = 240

enum class ShardCompletion { COMPLETED, FAILED, CANCELLED, TIMED_OUT, INCOMPLETE }

enum class ShardedBlockDisposition { PENDING_REVIEW, REJECTED_RETIRE_COMPLETE_VECTOR }

@Serializable
data class ShardedBlockIdentity(
    val protocolId: String,
    val blockId: String,
    val freezeCommit: String,
    val executionSourceCommit: String,
    val executionSourceTree: String,
    val registrySha256: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
    val pestMainSha256: String,
    val pestSideboardSha256: String,
    val pestComplete75Sha256: String,
    val monoRedMainSha256: String,
    val monoRedSideboardSha256: String,
    val monoRedComplete75Sha256: String,
    val expectedGames: Int = 50,
    val experimental: Boolean = true,
)

@Serializable
data class MatchupShardSpec(
    val shardId: String,
    val shardIndex: Int,
    val shardCount: Int,
    val firstGameNumber: Int,
    val lastGameNumber: Int,
    val assignmentCount: Int,
    val assignmentSha256: String,
    val timeoutMinutes: Int,
)

@Serializable
data class ShardedMatchupPlan(
    val schema: String = PEST_SHARDED_PLAN_SCHEMA,
    val readinessBaselineCommit: String = PEST_REPLACEMENT_READINESS_BASELINE,
    val identity: ShardedBlockIdentity,
    val assignments: List<FrozenMatchupAssignment>,
    val shards: List<MatchupShardSpec>,
)

@Serializable
data class GameRuntime(
    val gameNumber: Int,
    val seedHex: String,
    val runtimeMillis: Long,
)

@Serializable
data class MatchupShardRaw(
    val schema: String = PEST_SHARD_ARTIFACT_SCHEMA,
    val identity: ShardedBlockIdentity,
    val shard: MatchupShardSpec,
    val attemptNumber: Int = 1,
    val retryOfArtifactSha256: String? = null,
    val completion: ShardCompletion,
    val failureReason: String? = null,
    val assignments: List<FrozenMatchupAssignment>,
    val attemptedSeeds: List<AttemptedSeed>,
    val games: List<MatchupRawGame>,
    val gameRuntimes: List<GameRuntime>,
    val shardRuntimeMillis: Long,
    val executionLog: List<BlockExecutionLogEntry>,
)

@Serializable
data class MatchupShardAuditInput(
    val identity: ShardedBlockIdentity,
    val shard: MatchupShardSpec,
    val attemptNumber: Int,
    val retryOfArtifactSha256: String?,
    val completion: ShardCompletion,
    val failureReason: String?,
    val assignments: List<FrozenMatchupAssignment>,
    val attemptedSeeds: List<AttemptedSeed>,
    val games: List<MatchupRawGame>,
    val gameRuntimes: List<GameRuntime>,
    val shardRuntimeMillis: Long,
    val executionLog: List<BlockExecutionLogEntry>,
)

@Serializable
data class MatchupShardArtifactManifest(
    val schema: String,
    val protocolId: String,
    val blockId: String,
    val shardId: String,
    val executionSourceCommit: String,
    val executionSourceTree: String,
    val assignmentSha256: String,
    val planSha256: String,
    val completion: ShardCompletion,
    val rawJsonSha256: String,
    val compressedSha256: String,
    val auditInputSha256: String,
    val compression: String = "gzip/deflate; mtime=0; xfl=0; os=255",
)

data class MatchupShardArtifactBundle(
    val rawJson: ByteArray,
    val compressed: ByteArray,
    val manifest: ByteArray,
    val auditInput: ByteArray,
)

@Serializable
private data class ShardPlanBinding(
    val identity: ShardedBlockIdentity,
    val shard: MatchupShardSpec,
)

data class ShardObservation(
    val shardId: String,
    val workflowCompletion: ShardCompletion,
    val artifact: MatchupShardArtifactBundle?,
)

@Serializable
data class ReconciledShard(
    val shardId: String,
    val shardIndex: Int,
    val workflowCompletion: ShardCompletion,
    val rawJsonSha256: String?,
    val compressedSha256: String?,
    val manifestSha256: String?,
    val auditInputSha256: String?,
    val assignmentSha256: String,
    val attemptedSeedCount: Int,
    val gameCount: Int,
    val shardRuntimeMillis: Long?,
)

@Serializable
data class ShardedBlockReconciliation(
    val schema: String = PEST_SHARD_AGGREGATE_SCHEMA,
    val plan: ShardedMatchupPlan,
    val disposition: ShardedBlockDisposition,
    val retireCompleteVector: Boolean,
    val errors: List<String>,
    val shards: List<ReconciledShard>,
    val attemptedSeeds: List<AttemptedSeed>,
    val games: List<MatchupRawGame>,
    val gameRuntimes: List<GameRuntime>,
    val cumulativeShardRuntimeMillis: Long,
    val parallelWallClockMillis: Long,
)

@Serializable
data class ShardedBlockAggregateManifest(
    val schema: String,
    val protocolId: String,
    val blockId: String,
    val executionSourceCommit: String,
    val executionSourceTree: String,
    val disposition: ShardedBlockDisposition,
    val retireCompleteVector: Boolean,
    val planSha256: String,
    val rawJsonSha256: String,
    val compressedSha256: String,
    val compression: String = "gzip/deflate; mtime=0; xfl=0; os=255",
)

data class ShardedBlockAggregateBundle(
    val reconciliation: ShardedBlockReconciliation,
    val rawJson: ByteArray,
    val compressed: ByteArray,
    val manifest: ByteArray,
)

object PestControlMatchupSharding {
    fun plan(
        identity: ShardedBlockIdentity,
        assignments: List<FrozenMatchupAssignment>,
        readinessBaselineCommit: String = PEST_REPLACEMENT_READINESS_BASELINE,
    ): ShardedMatchupPlan {
        val shards = assignments.chunked(PEST_GAMES_PER_SHARD).mapIndexed { index, rows ->
            MatchupShardSpec(
                shardId = "SHARD_${(index + 1).toString().padStart(2, '0')}_OF_02",
                shardIndex = index + 1,
                shardCount = PEST_SHARD_COUNT,
                firstGameNumber = rows.firstOrNull()?.gameNumber ?: 0,
                lastGameNumber = rows.lastOrNull()?.gameNumber ?: 0,
                assignmentCount = rows.size,
                assignmentSha256 = assignmentSha256(rows),
                timeoutMinutes = PEST_SHARD_TIMEOUT_MINUTES,
            )
        }
        return ShardedMatchupPlan(
            readinessBaselineCommit = readinessBaselineCommit,
            identity = identity,
            assignments = assignments,
            shards = shards,
        )
    }

    fun assignmentsFor(plan: ShardedMatchupPlan, shard: MatchupShardSpec): List<FrozenMatchupAssignment> =
        plan.assignments.filter { it.gameNumber in shard.firstGameNumber..shard.lastGameNumber }

    fun validatePlan(plan: ShardedMatchupPlan): List<String> = buildList {
        if (plan.schema != PEST_SHARDED_PLAN_SCHEMA) add("plan schema mismatch")
        val expectedReadiness = if (plan.identity.blockId == PEST_V2_QUALIFICATION_BLOCK) {
            PEST_V2_QUALIFICATION_READINESS_COMMIT
        } else {
            PEST_REPLACEMENT_READINESS_BASELINE
        }
        if (plan.readinessBaselineCommit != expectedReadiness) add("readiness baseline mismatch")
        if (plan.identity.expectedGames != 50) add("logical block must contain exactly 50 games")
        if (plan.assignments.size != plan.identity.expectedGames) add("assignment count mismatch")
        if (plan.assignments.map { it.gameNumber } != (1..plan.identity.expectedGames).toList()) {
            add("assignments are missing, duplicated, or reordered")
        }
        if (plan.assignments.map { it.seedDecimal }.distinct().size != plan.assignments.size) {
            add("assignment seeds are duplicated")
        }
        if (plan.assignments.any { it.protocolId != plan.identity.protocolId || it.blockId != plan.identity.blockId }) {
            add("assignment protocol or block identity mismatch")
        }
        plan.assignments.forEachIndexed { index, assignment ->
            if (assignment.seedDecimal == 0L ||
                assignment.seedHex != "0x${assignment.seedDecimal.toULong().toString(16).padStart(16, '0')}"
            ) add("assignment ${index + 1} seed encoding mismatch")
            if (assignment.pestSeat == assignment.monoRedSeat) add("assignment ${index + 1} seat collision")
            val expectedPlayDraw = if (assignment.startingPlayer == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW"
            if (assignment.pestPlayDraw != expectedPlayDraw) add("assignment ${index + 1} play/draw mismatch")
            if (assignment.pestMainSha256 != plan.identity.pestMainSha256 ||
                assignment.pestSideboardSha256 != plan.identity.pestSideboardSha256 ||
                assignment.pestComplete75Sha256 != plan.identity.pestComplete75Sha256 ||
                assignment.monoRedMainSha256 != plan.identity.monoRedMainSha256 ||
                assignment.monoRedSideboardSha256 != plan.identity.monoRedSideboardSha256 ||
                assignment.monoRedComplete75Sha256 != plan.identity.monoRedComplete75Sha256
            ) add("assignment ${index + 1} deck identity mismatch")
        }
        if (plan.assignments.count { it.pestPlayDraw == "PLAY" } != 25 ||
            plan.assignments.count { it.pestPlayDraw == "DRAW" } != 25
        ) add("play/draw allocation is not 25/25")
        if (plan.assignments.count { it.pestSeat == PestSeat.SEAT_ZERO } != 25 ||
            plan.assignments.count { it.pestSeat == PestSeat.SEAT_ONE } != 25
        ) add("Pest seat allocation is not 25/25")
        val jointCounts = plan.assignments.groupingBy { it.pestSeat to it.pestPlayDraw }.eachCount().values.sorted()
        if (jointCounts != listOf(12, 12, 13, 13)) add("joint seat/play-draw allocation is not 12/12/13/13")
        if (plan.shards.size != PEST_SHARD_COUNT) add("plan must contain exactly two shards")
        val expectedRanges = listOf(1..25, 26..50)
        plan.shards.forEachIndexed { index, shard ->
            val expected = expectedRanges.getOrNull(index)
            if (shard.shardIndex != index + 1 || shard.shardCount != PEST_SHARD_COUNT ||
                shard.shardId != "SHARD_${(index + 1).toString().padStart(2, '0')}_OF_02"
            ) add("shard ${index + 1} identity mismatch")
            if (expected == null || shard.firstGameNumber != expected.first || shard.lastGameNumber != expected.last ||
                shard.assignmentCount != PEST_GAMES_PER_SHARD
            ) add("shard ${index + 1} is not its contiguous 25-game range")
            val rows = assignmentsFor(plan, shard)
            if (rows.size != PEST_GAMES_PER_SHARD || assignmentSha256(rows) != shard.assignmentSha256) {
                add("shard ${index + 1} assignment hash mismatch")
            }
            if (shard.timeoutMinutes != PEST_SHARD_TIMEOUT_MINUTES || shard.timeoutMinutes >= 360) {
                add("shard ${index + 1} timeout must remain below six hours")
            }
        }
        val assignedNumbers = plan.shards.flatMap { assignmentsFor(plan, it).map(FrozenMatchupAssignment::gameNumber) }
        if (assignedNumbers != (1..50).toList()) add("every frozen assignment must appear in exactly one shard")
        addAll(validateIdentity(plan.identity))
    }

    fun buildShard(raw: MatchupShardRaw): MatchupShardArtifactBundle {
        val rawJson = canonical(raw)
        val compressed = PestControlMatchupArtifactCodec.deterministicGzip(rawJson)
        val auditInput = canonical(
            MatchupShardAuditInput(
                raw.identity, raw.shard, raw.attemptNumber, raw.retryOfArtifactSha256, raw.completion,
                raw.failureReason, raw.assignments, raw.attemptedSeeds, raw.games, raw.gameRuntimes,
                raw.shardRuntimeMillis, raw.executionLog,
            )
        )
        val manifest = MatchupShardArtifactManifest(
            schema = raw.schema,
            protocolId = raw.identity.protocolId,
            blockId = raw.identity.blockId,
            shardId = raw.shard.shardId,
            executionSourceCommit = raw.identity.executionSourceCommit,
            executionSourceTree = raw.identity.executionSourceTree,
            assignmentSha256 = raw.shard.assignmentSha256,
            planSha256 = planSha256(raw.identity, raw.shard),
            completion = raw.completion,
            rawJsonSha256 = sha256(rawJson),
            compressedSha256 = sha256(compressed),
            auditInputSha256 = sha256(auditInput),
        )
        return MatchupShardArtifactBundle(rawJson, compressed, canonical(manifest), auditInput)
    }

    fun verifyShard(
        plan: ShardedMatchupPlan,
        expectedShard: MatchupShardSpec,
        bundle: MatchupShardArtifactBundle,
    ): Pair<MatchupShardRaw?, List<String>> {
        val errors = mutableListOf<String>()
        val raw = runCatching { PROTOCOL_JSON.decodeFromString<MatchupShardRaw>(bundle.rawJson.decodeToString()) }
            .getOrElse { return null to listOf("raw shard JSON cannot be decoded: ${it.message}") }
        val manifest = runCatching {
            PROTOCOL_JSON.decodeFromString<MatchupShardArtifactManifest>(bundle.manifest.decodeToString())
        }.getOrElse { return raw to listOf("shard manifest cannot be decoded: ${it.message}") }
        if (sha256(bundle.rawJson) != manifest.rawJsonSha256) errors += "raw shard JSON hash mismatch"
        if (sha256(bundle.compressed) != manifest.compressedSha256) errors += "compressed shard hash mismatch"
        if (sha256(bundle.auditInput) != manifest.auditInputSha256) errors += "shard audit-input hash mismatch"
        if (!bundle.compressed.contentEquals(PestControlMatchupArtifactCodec.deterministicGzip(bundle.rawJson))) {
            errors += "compressed shard bytes are not canonical"
        }
        val expectedAudit = canonical(
            MatchupShardAuditInput(
                raw.identity, raw.shard, raw.attemptNumber, raw.retryOfArtifactSha256, raw.completion,
                raw.failureReason, raw.assignments, raw.attemptedSeeds, raw.games, raw.gameRuntimes,
                raw.shardRuntimeMillis, raw.executionLog,
            )
        )
        if (!bundle.auditInput.contentEquals(expectedAudit)) errors += "shard audit input is not derived from raw JSON"
        if (raw.schema != PEST_SHARD_ARTIFACT_SCHEMA || manifest.schema != raw.schema) errors += "shard schema mismatch"
        if (raw.identity != plan.identity || raw.shard != expectedShard) errors += "shard plan identity mismatch"
        if (manifest.protocolId != plan.identity.protocolId || manifest.blockId != plan.identity.blockId ||
            manifest.shardId != expectedShard.shardId ||
            manifest.executionSourceCommit != plan.identity.executionSourceCommit ||
            manifest.executionSourceTree != plan.identity.executionSourceTree ||
            manifest.assignmentSha256 != expectedShard.assignmentSha256 || manifest.completion != raw.completion
        ) errors += "shard manifest provenance mismatch"
        if (manifest.planSha256 != planSha256(plan.identity, expectedShard)) {
            errors += "shard manifest plan hash mismatch"
        }
        errors += validateShard(plan, expectedShard, raw)
        return raw to errors
    }

    fun reconcile(
        plan: ShardedMatchupPlan,
        observations: List<ShardObservation>,
    ): ShardedBlockAggregateBundle {
        val errors = validatePlan(plan).toMutableList()
        val knownIds = plan.shards.map { it.shardId }.toSet()
        observations.filter { it.shardId !in knownIds }.forEach { errors += "unknown shard ${it.shardId}" }
        observations.groupingBy { it.shardId }.eachCount().filterValues { it != 1 }.keys.sorted().forEach {
            errors += "shard $it was observed more than once; retries and duplicates are forbidden"
        }

        val decoded = mutableListOf<Pair<MatchupShardSpec, MatchupShardRaw>>()
        val summaries = plan.shards.map { shard ->
            val matching = observations.filter { it.shardId == shard.shardId }
            val observation = matching.singleOrNull()
            if (observation == null) {
                errors += "shard ${shard.shardId} is missing"
                ReconciledShard(shard.shardId, shard.shardIndex, ShardCompletion.INCOMPLETE,
                    null, null, null, null, shard.assignmentSha256, 0, 0, null)
            } else {
                if (observation.workflowCompletion != ShardCompletion.COMPLETED) {
                    errors += "shard ${shard.shardId} workflow ended ${observation.workflowCompletion}"
                }
                val artifact = observation.artifact
                if (artifact == null) {
                    errors += "shard ${shard.shardId} has no complete artifact"
                    ReconciledShard(shard.shardId, shard.shardIndex, observation.workflowCompletion,
                        null, null, null, null, shard.assignmentSha256, 0, 0, null)
                } else {
                    val (raw, shardErrors) = verifyShard(plan, shard, artifact)
                    errors += shardErrors.map { "${shard.shardId}: $it" }
                    if (raw != null) decoded += shard to raw
                    ReconciledShard(
                        shard.shardId, shard.shardIndex, observation.workflowCompletion,
                        sha256(artifact.rawJson), sha256(artifact.compressed), sha256(artifact.manifest),
                        sha256(artifact.auditInput), shard.assignmentSha256,
                        raw?.attemptedSeeds?.size ?: 0, raw?.games?.size ?: 0, raw?.shardRuntimeMillis,
                    )
                }
            }
        }

        val orderedRaw = decoded.sortedBy { it.first.shardIndex }.map { it.second }
        val attempts = orderedRaw.flatMap { it.attemptedSeeds }
        val games = orderedRaw.flatMap { it.games }
        val runtimes = orderedRaw.flatMap { it.gameRuntimes }
        val expectedAttempts = plan.assignments.map { AttemptedSeed(it.gameNumber, it.seedDecimal, it.seedHex) }
        if (attempts != expectedAttempts) errors += "global attempted seeds are missing, duplicated, reordered, retried, or replaced"
        if (games.mapNotNull { it.provenance.gameNumber } != (1..50).toList()) {
            errors += "global games are missing, duplicated, or reordered"
        }
        if (games.mapNotNull { it.provenance.seedHex } != plan.assignments.map { it.seedHex }) {
            errors += "global game seeds were replaced or reordered"
        }
        if (runtimes.map { it.gameNumber } != (1..50).toList()) errors += "global per-game runtimes are incomplete or reordered"

        val finalErrors = errors.distinct()
        val rejected = finalErrors.isNotEmpty()
        val reconciliation = ShardedBlockReconciliation(
            plan = plan,
            disposition = if (rejected) ShardedBlockDisposition.REJECTED_RETIRE_COMPLETE_VECTOR
            else ShardedBlockDisposition.PENDING_REVIEW,
            retireCompleteVector = rejected,
            errors = finalErrors,
            shards = summaries,
            attemptedSeeds = attempts,
            games = games,
            gameRuntimes = runtimes,
            cumulativeShardRuntimeMillis = orderedRaw.sumOf { it.shardRuntimeMillis },
            parallelWallClockMillis = orderedRaw.maxOfOrNull { it.shardRuntimeMillis } ?: 0,
        )
        val rawJson = canonical(reconciliation)
        val compressed = PestControlMatchupArtifactCodec.deterministicGzip(rawJson)
        val manifest = ShardedBlockAggregateManifest(
            schema = reconciliation.schema,
            protocolId = plan.identity.protocolId,
            blockId = plan.identity.blockId,
            executionSourceCommit = plan.identity.executionSourceCommit,
            executionSourceTree = plan.identity.executionSourceTree,
            disposition = reconciliation.disposition,
            retireCompleteVector = reconciliation.retireCompleteVector,
            planSha256 = sha256(canonical(plan)),
            rawJsonSha256 = sha256(rawJson),
            compressedSha256 = sha256(compressed),
        )
        return ShardedBlockAggregateBundle(reconciliation, rawJson, compressed, canonical(manifest))
    }

    fun verifyAggregate(bundle: ShardedBlockAggregateBundle): List<String> {
        val decoded = runCatching {
            PROTOCOL_JSON.decodeFromString<ShardedBlockReconciliation>(bundle.rawJson.decodeToString())
        }.getOrElse { return listOf("aggregate raw JSON cannot be decoded: ${it.message}") }
        val manifest = runCatching {
            PROTOCOL_JSON.decodeFromString<ShardedBlockAggregateManifest>(bundle.manifest.decodeToString())
        }.getOrElse { return listOf("aggregate manifest cannot be decoded: ${it.message}") }
        return buildList {
            if (decoded != bundle.reconciliation) add("aggregate object differs from canonical raw JSON")
            if (sha256(bundle.rawJson) != manifest.rawJsonSha256) add("aggregate raw JSON hash mismatch")
            if (sha256(bundle.compressed) != manifest.compressedSha256) add("aggregate compressed hash mismatch")
            if (!bundle.compressed.contentEquals(PestControlMatchupArtifactCodec.deterministicGzip(bundle.rawJson))) {
                add("aggregate compressed bytes are not canonical")
            }
            if (manifest.schema != decoded.schema || manifest.protocolId != decoded.plan.identity.protocolId ||
                manifest.blockId != decoded.plan.identity.blockId ||
                manifest.executionSourceCommit != decoded.plan.identity.executionSourceCommit ||
                manifest.executionSourceTree != decoded.plan.identity.executionSourceTree ||
                manifest.disposition != decoded.disposition ||
                manifest.retireCompleteVector != decoded.retireCompleteVector ||
                manifest.planSha256 != sha256(canonical(decoded.plan))
            ) add("aggregate manifest provenance mismatch")
        }
    }

    fun assignmentSha256(assignments: List<FrozenMatchupAssignment>): String = sha256(canonical(assignments))

    private fun validateIdentity(identity: ShardedBlockIdentity): List<String> = buildList {
        if (identity.protocolId.isBlank() || identity.blockId.isBlank()) add("protocol and block identities are required")
        if (!identity.freezeCommit.isHex(40) || !identity.executionSourceCommit.isHex(40) ||
            !identity.executionSourceTree.isHex(40)
        ) add("commit and tree identities must be full Git object IDs")
        listOf(identity.registrySha256, identity.orderedVectorSha256, identity.assignmentCsvSha256,
            identity.freezeManifestSha256, identity.pestMainSha256, identity.pestSideboardSha256,
            identity.pestComplete75Sha256, identity.monoRedMainSha256, identity.monoRedSideboardSha256,
            identity.monoRedComplete75Sha256).forEach {
            if (!it.isHex(64)) add("artifact identities must be SHA-256 values")
        }
    }

    private fun validateShard(
        plan: ShardedMatchupPlan,
        expectedShard: MatchupShardSpec,
        raw: MatchupShardRaw,
    ): List<String> = buildList {
        val expectedAssignments = assignmentsFor(plan, expectedShard)
        if (raw.attemptNumber != 1 || raw.retryOfArtifactSha256 != null) add("shard retries are forbidden")
        if (raw.assignments != expectedAssignments || assignmentSha256(raw.assignments) != expectedShard.assignmentSha256) {
            add("shard assignments are missing, duplicated, reordered, or replaced")
        }
        val expectedAttempts = expectedAssignments.map { AttemptedSeed(it.gameNumber, it.seedDecimal, it.seedHex) }
        if (raw.attemptedSeeds != expectedAttempts) add("shard attempted seeds do not exactly match its assignment set")
        if (raw.games.size != expectedShard.assignmentCount) add("shard game count is incomplete")
        raw.games.forEachIndexed { index, game ->
            val assignment = expectedAssignments.getOrNull(index)
            if (assignment == null || game.provenance.gameNumber != assignment.gameNumber ||
                game.provenance.seedDecimal != assignment.seedDecimal || game.provenance.seedHex != assignment.seedHex ||
                game.provenance.pestSeat != assignment.pestSeat || game.provenance.startingDeck != assignment.startingPlayer
            ) add("shard game ${index + 1} assignment mismatch")
            if (game.provenance.protocolId != plan.identity.protocolId || game.provenance.blockId != plan.identity.blockId ||
                game.provenance.freezeCommit != plan.identity.freezeCommit ||
                game.provenance.executionCommit != plan.identity.executionSourceCommit ||
                game.provenance.sourceCommit != plan.identity.executionSourceCommit ||
                game.provenance.orderedVectorSha256 != plan.identity.orderedVectorSha256 ||
                game.provenance.assignmentCsvSha256 != plan.identity.assignmentCsvSha256 ||
                game.provenance.freezeManifestSha256 != plan.identity.freezeManifestSha256 ||
                game.provenance.pestControlMainSha256 != plan.identity.pestMainSha256 ||
                game.provenance.pestControlSideboardSha256 != plan.identity.pestSideboardSha256 ||
                game.provenance.pestControlComplete75Sha256 != plan.identity.pestComplete75Sha256 ||
                game.provenance.monoRedMainSha256 != plan.identity.monoRedMainSha256 ||
                game.provenance.monoRedSideboardSha256 != plan.identity.monoRedSideboardSha256 ||
                game.provenance.monoRedComplete75Sha256 != plan.identity.monoRedComplete75Sha256
            ) add("shard game ${assignment?.gameNumber ?: index + 1} provenance mismatch")
            if (plan.identity.experimental) {
                if (game.fixtureIsNonexperimental || game.excludedFromFutureSeedOverlapRegistry ||
                    game.provenance.entropyClassification != "FROZEN_EXPERIMENTAL_VECTOR"
                ) add("experimental shard game ${assignment?.gameNumber ?: index + 1} has fixture provenance")
            } else if (!game.fixtureIsNonexperimental || !game.excludedFromFutureSeedOverlapRegistry ||
                game.provenance.entropyClassification != "NONEXPERIMENTAL_SHARDING_FIXTURE"
            ) add("synthetic shard game ${assignment?.gameNumber ?: index + 1} lacks fixture provenance")
            if (game.terminal?.gameOver != true || game.protocolDefect != null ||
                game.priorityActions.any { !it.accepted || it.fallbackUsed }
            ) add("shard game ${assignment?.gameNumber ?: index + 1} is not a clean terminal")
        }
        if (raw.gameRuntimes.map { it.gameNumber } != expectedAssignments.map { it.gameNumber } ||
            raw.gameRuntimes.map { it.seedHex } != expectedAssignments.map { it.seedHex } ||
            raw.gameRuntimes.any { it.runtimeMillis <= 0 }
        ) add("per-game runtime records are incomplete or mismatched")
        if (raw.shardRuntimeMillis < raw.gameRuntimes.sumOf { it.runtimeMillis } || raw.shardRuntimeMillis <= 0) {
            add("shard runtime is invalid")
        }
        if (raw.shardRuntimeMillis > expectedShard.timeoutMinutes * 60_000L) add("shard exceeded its timeout")
        if (raw.executionLog.map { it.sequence } != (1..raw.executionLog.size).toList()) {
            add("shard execution log is not append-only and contiguous")
        }
        val loggedAttempts = raw.executionLog.filter { it.event == "seed marked attempted before game initialization" }
            .map { AttemptedSeed(it.gameNumber ?: -1, it.seedDecimal ?: 0, expectedAssignments
                .firstOrNull { assignment -> assignment.gameNumber == it.gameNumber }?.seedHex ?: "") }
        if (loggedAttempts != expectedAttempts) add("shard execution log does not record every seed attempt exactly once")
        if (raw.executionLog.firstOrNull()?.runnerState != BlockRunnerState.AUTHORIZED ||
            raw.executionLog.lastOrNull()?.runnerState != BlockRunnerState.COMPLETED
        ) add("shard execution log lacks authorized and completed boundaries")
        if (raw.completion != ShardCompletion.COMPLETED || raw.failureReason != null) {
            add("shard did not complete cleanly")
        }
    }

    private inline fun <reified T> canonical(value: T): ByteArray =
        (PROTOCOL_JSON.encodeToString(value) + "\n").toByteArray()

    private fun planSha256(identity: ShardedBlockIdentity, shard: MatchupShardSpec): String =
        sha256(canonical(ShardPlanBinding(identity, shard)))

    private fun String.isHex(length: Int): Boolean = this.length == length && all { it in '0'..'9' || it in 'a'..'f' }
}

object PestControlReplacementShardRunnerGuard {
    const val CONFIGURED_STATE = "DISABLED"

    fun activationErrors(
        plan: ShardedMatchupPlan,
        shardId: String,
        configuredState: String,
        explicitAuthorization: Boolean,
        isUnitTestProcess: Boolean,
        attemptNumber: Int,
        outputAlreadyExists: Boolean,
        checkedOutCommit: String,
        checkedOutTree: String,
    ): List<String> = buildList {
        addAll(PestControlMatchupSharding.validatePlan(plan))
        if (configuredState != "AUTHORIZED") add("runner is not AUTHORIZED")
        if (!explicitAuthorization) add("explicit execution acknowledgement is missing")
        if (isUnitTestProcess) add("unit tests cannot activate the runner")
        if (attemptNumber != 1) add("shard retry is forbidden")
        if (outputAlreadyExists) add("shard output already exists")
        if (plan.shards.none { it.shardId == shardId }) add("unknown shard identity")
        if (checkedOutCommit != plan.identity.executionSourceCommit) add("checked-out commit mismatch")
        if (checkedOutTree != plan.identity.executionSourceTree) add("checked-out tree mismatch")
    }
}
