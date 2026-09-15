package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

private const val SYNTHETIC_PROTOCOL = "SYNTHETIC_NONEXPERIMENTAL_SHARDING_PROTOCOL"
private const val SYNTHETIC_BLOCK = "SYNTHETIC_NONEXPERIMENTAL_SHARDING_BLOCK"
private const val SYNTHETIC_FREEZE = "1111111111111111111111111111111111111111"
private const val SYNTHETIC_SOURCE = "2222222222222222222222222222222222222222"
private const val SYNTHETIC_TREE = "3333333333333333333333333333333333333333"

/** No test in this file initializes a game, consumes entropy, or uses an experimental seed vector. */
class PestControlMatchupShardingTest : FunSpec({
    test("two contiguous 25-game shards preserve one ordered logical block") {
        val plan = syntheticPlan()

        PestControlMatchupSharding.validatePlan(plan).shouldBeEmpty()
        plan.readinessBaselineCommit shouldBe PEST_REPLACEMENT_READINESS_BASELINE
        plan.shards.map { it.firstGameNumber..it.lastGameNumber } shouldBe listOf(1..25, 26..50)
        plan.shards.map { it.assignmentCount } shouldBe listOf(25, 25)
        plan.shards.map { it.timeoutMinutes } shouldBe listOf(240, 240)
        plan.shards.flatMap { PestControlMatchupSharding.assignmentsFor(plan, it) }
            .map { it.gameNumber } shouldBe (1..50).toList()
        plan.shards.forEach { shard ->
            PestControlMatchupSharding.assignmentSha256(
                PestControlMatchupSharding.assignmentsFor(plan, shard)
            ) shouldBe shard.assignmentSha256
        }
    }

    test("replacement runners remain disabled and enforce exact source tree and one-shot guards") {
        val plan = syntheticPlan()
        val shard = plan.shards.first()

        PestControlReplacementShardRunnerGuard.activationErrors(
            plan, shard.shardId, PestControlReplacementShardRunnerGuard.CONFIGURED_STATE,
            explicitAuthorization = true, isUnitTestProcess = false, attemptNumber = 1,
            outputAlreadyExists = false, checkedOutCommit = SYNTHETIC_SOURCE, checkedOutTree = SYNTHETIC_TREE,
        ).shouldContain("runner is not AUTHORIZED")

        PestControlReplacementShardRunnerGuard.activationErrors(
            plan, shard.shardId, "AUTHORIZED",
            explicitAuthorization = true, isUnitTestProcess = true, attemptNumber = 2,
            outputAlreadyExists = true, checkedOutCommit = "bad", checkedOutTree = "bad",
        ).let { errors ->
            errors.shouldContain("unit tests cannot activate the runner")
            errors.shouldContain("shard retry is forbidden")
            errors.shouldContain("shard output already exists")
            errors.shouldContain("checked-out commit mismatch")
            errors.shouldContain("checked-out tree mismatch")
        }
    }

    test("shard artifacts deterministically bind source tree assignments runtimes and complete audit traces") {
        val plan = syntheticPlan()
        val raw = completedShard(plan, plan.shards.first())
        val first = PestControlMatchupSharding.buildShard(raw)
        val second = PestControlMatchupSharding.buildShard(raw)

        first.rawJson.contentEquals(second.rawJson).shouldBeTrue()
        first.compressed.contentEquals(second.compressed).shouldBeTrue()
        first.manifest.contentEquals(second.manifest).shouldBeTrue()
        first.auditInput.contentEquals(second.auditInput).shouldBeTrue()
        PestControlMatchupSharding.verifyShard(plan, plan.shards.first(), first).second.shouldBeEmpty()
        first.auditInput.decodeToString().contains(SYNTHETIC_TREE).shouldBeTrue()
        first.auditInput.decodeToString().contains("priorityActions").shouldBeTrue()
        first.auditInput.decodeToString().contains("gameRuntimes").shouldBeTrue()
    }

    test("two clean shards reconcile deterministically into exactly 50 globally ordered games") {
        val plan = syntheticPlan()
        val observations = completedObservations(plan)
        val first = PestControlMatchupSharding.reconcile(plan, observations)
        val second = PestControlMatchupSharding.reconcile(plan, observations)

        first.reconciliation.disposition shouldBe ShardedBlockDisposition.PENDING_REVIEW
        first.reconciliation.retireCompleteVector.shouldBeFalse()
        first.reconciliation.errors.shouldBeEmpty()
        first.reconciliation.attemptedSeeds.map { it.gameNumber } shouldBe (1..50).toList()
        first.reconciliation.games.map { it.provenance.gameNumber } shouldBe (1..50).toList()
        first.reconciliation.gameRuntimes.map { it.gameNumber } shouldBe (1..50).toList()
        first.reconciliation.cumulativeShardRuntimeMillis shouldBe 52_500L
        first.reconciliation.parallelWallClockMillis shouldBe 26_250L
        first.rawJson.contentEquals(second.rawJson).shouldBeTrue()
        first.compressed.contentEquals(second.compressed).shouldBeTrue()
        first.manifest.contentEquals(second.manifest).shouldBeTrue()
        PestControlMatchupSharding.verifyAggregate(first).shouldBeEmpty()
    }

    test("missing failed cancelled timed-out and incomplete shards reject and retire the complete vector") {
        val plan = syntheticPlan()
        val complete = completedObservations(plan)
        val cases = listOf(
            complete.take(1),
            complete.mapIndexed { index, item -> if (index == 1) item.copy(workflowCompletion = ShardCompletion.FAILED) else item },
            complete.mapIndexed { index, item -> if (index == 1) item.copy(workflowCompletion = ShardCompletion.CANCELLED) else item },
            complete.mapIndexed { index, item -> if (index == 1) item.copy(workflowCompletion = ShardCompletion.TIMED_OUT) else item },
            complete.mapIndexed { index, item -> if (index == 1) item.copy(workflowCompletion = ShardCompletion.INCOMPLETE, artifact = null) else item },
        )

        cases.forEach { observations ->
            val result = PestControlMatchupSharding.reconcile(plan, observations).reconciliation
            result.disposition shouldBe ShardedBlockDisposition.REJECTED_RETIRE_COMPLETE_VECTOR
            result.retireCompleteVector.shouldBeTrue()
            result.errors.isNotEmpty().shouldBeTrue()
        }
    }

    test("duplicated observation or retry rejects the whole block without partial salvage") {
        val plan = syntheticPlan()
        val complete = completedObservations(plan)
        val duplicated = PestControlMatchupSharding.reconcile(plan, complete + complete.first()).reconciliation
        duplicated.retireCompleteVector.shouldBeTrue()
        duplicated.errors.any { it.contains("observed more than once") }.shouldBeTrue()

        val shard = plan.shards.first()
        val retriedRaw = completedShard(plan, shard).copy(attemptNumber = 2, retryOfArtifactSha256 = "a".repeat(64))
        val retried = complete.toMutableList().also {
            it[0] = it[0].copy(artifact = PestControlMatchupSharding.buildShard(retriedRaw))
        }
        val result = PestControlMatchupSharding.reconcile(plan, retried).reconciliation
        result.retireCompleteVector.shouldBeTrue()
        result.errors.any { it.contains("retries are forbidden") }.shouldBeTrue()
    }

    test("missing duplicated reordered and replaced seeds or games reject the entire block") {
        val plan = syntheticPlan()
        val shard = plan.shards.first()
        val original = completedShard(plan, shard)
        val mutations = listOf(
            original.copy(attemptedSeeds = original.attemptedSeeds.dropLast(1)),
            original.copy(attemptedSeeds = original.attemptedSeeds.toMutableList().also { it[1] = it[0] }),
            original.copy(games = original.games.toMutableList().also { java.util.Collections.swap(it, 0, 1) }),
            original.copy(games = original.games.toMutableList().also {
                it[0] = it[0].copy(provenance = it[0].provenance.copy(seedHex = "0xffffffffffffffff"))
            }),
        )

        mutations.forEach { mutated ->
            val observations = completedObservations(plan).toMutableList().also {
                it[0] = it[0].copy(artifact = PestControlMatchupSharding.buildShard(mutated))
            }
            val result = PestControlMatchupSharding.reconcile(plan, observations).reconciliation
            result.retireCompleteVector.shouldBeTrue()
            result.errors.isNotEmpty().shouldBeTrue()
        }
    }

    test("artifact tampering and wrong shard or source identities are rejected") {
        val plan = syntheticPlan()
        val shard = plan.shards.first()
        val raw = completedShard(plan, shard)
        val bundle = PestControlMatchupSharding.buildShard(raw)

        PestControlMatchupSharding.verifyShard(
            plan, shard, bundle.copy(rawJson = bundle.rawJson + 0)
        ).second.isNotEmpty().shouldBeTrue()
        PestControlMatchupSharding.verifyShard(
            plan, shard, bundle.copy(auditInput = bundle.auditInput + 0)
        ).second.isNotEmpty().shouldBeTrue()
        val wrongSource = raw.copy(identity = raw.identity.copy(executionSourceCommit = "4".repeat(40)))
        PestControlMatchupSharding.verifyShard(
            plan, shard, PestControlMatchupSharding.buildShard(wrongSource)
        ).second.any { it.contains("plan identity") || it.contains("provenance") }.shouldBeTrue()
    }

    test("a shard runtime at or beyond workflow failure cannot be accepted") {
        val plan = syntheticPlan()
        val shard = plan.shards.first()
        val timedOut = completedShard(plan, shard).copy(
            shardRuntimeMillis = (shard.timeoutMinutes * 60_000L) + 1,
            completion = ShardCompletion.TIMED_OUT,
            failureReason = "synthetic timeout",
        )
        val observations = completedObservations(plan).toMutableList().also {
            it[0] = ShardObservation(shard.shardId, ShardCompletion.TIMED_OUT,
                PestControlMatchupSharding.buildShard(timedOut))
        }
        val result = PestControlMatchupSharding.reconcile(plan, observations).reconciliation
        result.retireCompleteVector.shouldBeTrue()
        result.errors.any { it.contains("exceeded its timeout") }.shouldBeTrue()
    }

    test("aggregate hashes detect canonical artifact tampering") {
        val bundle = PestControlMatchupSharding.reconcile(syntheticPlan(), completedObservations(syntheticPlan()))
        PestControlMatchupSharding.verifyAggregate(bundle).shouldBeEmpty()
        PestControlMatchupSharding.verifyAggregate(bundle.copy(rawJson = bundle.rawJson + 0))
            .isNotEmpty().shouldBeTrue()
        PestControlMatchupSharding.verifyAggregate(bundle.copy(compressed = bundle.compressed + 0))
            .isNotEmpty().shouldBeTrue()
    }
})

private fun syntheticPlan(): ShardedMatchupPlan {
    val identity = ShardedBlockIdentity(
        protocolId = SYNTHETIC_PROTOCOL,
        blockId = SYNTHETIC_BLOCK,
        freezeCommit = SYNTHETIC_FREEZE,
        executionSourceCommit = SYNTHETIC_SOURCE,
        executionSourceTree = SYNTHETIC_TREE,
        registrySha256 = "a".repeat(64),
        orderedVectorSha256 = "b".repeat(64),
        assignmentCsvSha256 = "c".repeat(64),
        freezeManifestSha256 = "d".repeat(64),
        pestMainSha256 = PEST_CONTROL_V10_HASH,
        pestSideboardSha256 = PEST_CONTROL_V10_SIDEBOARD_HASH,
        pestComplete75Sha256 = PEST_CONTROL_V10_75_HASH,
        monoRedMainSha256 = SOTERX_MONO_RED_MAIN_HASH,
        monoRedSideboardSha256 = SOTERX_MONO_RED_SIDEBOARD_HASH,
        monoRedComplete75Sha256 = SOTERX_MONO_RED_75_HASH,
        experimental = false,
    )
    return PestControlMatchupSharding.plan(identity, syntheticShardAssignments())
}

private fun syntheticShardAssignments(): List<FrozenMatchupAssignment> = (1..50).map { number ->
    val pestSeat = if (number <= 25) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
    val starting = if (number % 2 == 0) StartingDeck.PEST_CONTROL else StartingDeck.MONO_RED_MADNESS
    FrozenMatchupAssignment(
        protocolId = SYNTHETIC_PROTOCOL,
        blockId = SYNTHETIC_BLOCK,
        gameNumber = number,
        seedDecimal = 8_000_000L + number,
        seedHex = "0x${(8_000_000L + number).toULong().toString(16).padStart(16, '0')}",
        pestSeat = pestSeat,
        monoRedSeat = if (pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
        startingPlayer = starting,
        pestPlayDraw = if (starting == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW",
        pestMainSha256 = PEST_CONTROL_V10_HASH,
        pestSideboardSha256 = PEST_CONTROL_V10_SIDEBOARD_HASH,
        pestComplete75Sha256 = PEST_CONTROL_V10_75_HASH,
        monoRedMainSha256 = SOTERX_MONO_RED_MAIN_HASH,
        monoRedSideboardSha256 = SOTERX_MONO_RED_SIDEBOARD_HASH,
        monoRedComplete75Sha256 = SOTERX_MONO_RED_75_HASH,
        gate4SourceCommit = "synthetic-nonexperimental-sharding-readiness",
    )
}

private fun completedObservations(plan: ShardedMatchupPlan): List<ShardObservation> = plan.shards.map { shard ->
    ShardObservation(shard.shardId, ShardCompletion.COMPLETED,
        PestControlMatchupSharding.buildShard(completedShard(plan, shard)))
}

private fun completedShard(plan: ShardedMatchupPlan, shard: MatchupShardSpec): MatchupShardRaw {
    val assignments = PestControlMatchupSharding.assignmentsFor(plan, shard)
    val games = assignments.map { assignment -> syntheticGame(plan, assignment) }
    val runtimes = assignments.map { GameRuntime(it.gameNumber, it.seedHex, 1_000L) }
    return MatchupShardRaw(
        identity = plan.identity,
        shard = shard,
        completion = ShardCompletion.COMPLETED,
        assignments = assignments,
        attemptedSeeds = assignments.map { AttemptedSeed(it.gameNumber, it.seedDecimal, it.seedHex) },
        games = games,
        gameRuntimes = runtimes,
        shardRuntimeMillis = 26_250L,
        executionLog = buildList {
            add(BlockExecutionLogEntry(1, BlockRunnerState.AUTHORIZED, "synthetic shard authorization"))
            assignments.forEachIndexed { index, assignment ->
                add(BlockExecutionLogEntry(
                    index + 2,
                    BlockRunnerState.STARTED,
                    "seed marked attempted before game initialization",
                    assignment.gameNumber,
                    assignment.seedDecimal,
                ))
            }
            add(BlockExecutionLogEntry(size + 1, BlockRunnerState.COMPLETED, "synthetic shard completion"))
        },
    )
}

private fun syntheticGame(
    plan: ShardedMatchupPlan,
    assignment: FrozenMatchupAssignment,
): MatchupRawGame = MatchupRawGame(
    provenance = MatchupProvenance(
        protocolId = plan.identity.protocolId,
        sourceCommit = plan.identity.executionSourceCommit,
        pestSeat = assignment.pestSeat,
        startingDeck = assignment.startingPlayer,
        environment = MatchupEnvironmentIdentity("synthetic", "synthetic", "synthetic", "synthetic", "und", "UTC"),
        entropyClassification = "NONEXPERIMENTAL_SHARDING_FIXTURE",
        blockId = plan.identity.blockId,
        freezeCommit = plan.identity.freezeCommit,
        executionCommit = plan.identity.executionSourceCommit,
        orderedVectorSha256 = plan.identity.orderedVectorSha256,
        assignmentCsvSha256 = plan.identity.assignmentCsvSha256,
        freezeManifestSha256 = plan.identity.freezeManifestSha256,
        gameNumber = assignment.gameNumber,
        seedDecimal = assignment.seedDecimal,
        seedHex = assignment.seedHex,
    ),
    fixtureId = "SYNTHETIC_NONEXPERIMENTAL_SHARD_${assignment.gameNumber}",
    fixtureIsNonexperimental = true,
    excludedFromFutureSeedOverlapRegistry = true,
    openingZones = emptyList(),
    mulligans = emptyList(),
    priorityActions = emptyList(),
    terminal = TerminalAudit(true, EntityId("synthetic-winner"), "synthetic terminal", 1, "synthetic"),
)
