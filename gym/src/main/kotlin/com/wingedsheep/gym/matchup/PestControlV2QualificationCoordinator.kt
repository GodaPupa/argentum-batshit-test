package com.wingedsheep.gym.matchup

/**
 * Binds the accepted V2 qualification freeze to the generic two-shard artifact contract.
 * Construction is pure; authorization and gameplay are deliberately absent from this object.
 */
object PestControlV2QualificationCoordinator {
    fun buildSealedPlan(
        vector: ByteArray,
        csv: ByteArray,
        manifest: ByteArray,
        shardAllocation: ByteArray,
        executionSourceCommit: String,
        executionSourceTree: String,
    ): ShardedMatchupPlan {
        val preflight = PestControlV2QualificationExecutionPreflight.inspect(
            vector = vector,
            csv = csv,
            manifest = manifest,
            shardAllocation = shardAllocation,
        )
        require(preflight.errors.isEmpty()) { "qualification preflight failed: ${preflight.errors.joinToString()}" }
        require(executionSourceCommit.isLowerHex(40) && executionSourceTree.isLowerHex(40))
        val freeze = PEST_V2_QUALIFICATION_FREEZE_IDENTITY
        val identity = ShardedBlockIdentity(
            protocolId = PEST_V2_OFFICIAL_PROTOCOL,
            blockId = PEST_V2_QUALIFICATION_BLOCK,
            freezeCommit = freeze.freezeCommit,
            executionSourceCommit = executionSourceCommit,
            executionSourceTree = executionSourceTree,
            registrySha256 = freeze.registrySha256,
            orderedVectorSha256 = freeze.orderedVectorSha256,
            assignmentCsvSha256 = freeze.assignmentCsvSha256,
            freezeManifestSha256 = freeze.freezeManifestSha256,
            pestMainSha256 = PEST_CONTROL_V10_HASH,
            pestSideboardSha256 = PEST_CONTROL_V10_SIDEBOARD_HASH,
            pestComplete75Sha256 = PEST_CONTROL_V10_75_HASH,
            monoRedMainSha256 = SOTERX_MONO_RED_MAIN_HASH,
            monoRedSideboardSha256 = SOTERX_MONO_RED_SIDEBOARD_HASH,
            monoRedComplete75Sha256 = SOTERX_MONO_RED_75_HASH,
        )
        val assignments = preflight.assignments.map { assignment ->
            FrozenMatchupAssignment(
                protocolId = PEST_V2_OFFICIAL_PROTOCOL,
                blockId = PEST_V2_QUALIFICATION_BLOCK,
                gameNumber = assignment.game,
                seedDecimal = assignment.seed,
                seedHex = assignment.seedHex,
                pestSeat = assignment.pestSeat,
                monoRedSeat = assignment.redSeat,
                startingPlayer = assignment.starter,
                pestPlayDraw = assignment.playDraw,
                pestMainSha256 = PEST_CONTROL_V10_HASH,
                pestSideboardSha256 = PEST_CONTROL_V10_SIDEBOARD_HASH,
                pestComplete75Sha256 = PEST_CONTROL_V10_75_HASH,
                monoRedMainSha256 = SOTERX_MONO_RED_MAIN_HASH,
                monoRedSideboardSha256 = SOTERX_MONO_RED_SIDEBOARD_HASH,
                monoRedComplete75Sha256 = SOTERX_MONO_RED_75_HASH,
                gate4SourceCommit = PEST_V2_QUALIFIED_RUNNER,
            )
        }
        return PestControlMatchupSharding.plan(
            identity = identity,
            assignments = assignments,
            readinessBaselineCommit = PEST_V2_QUALIFICATION_READINESS_COMMIT,
        ).also { plan ->
            check(PestControlMatchupSharding.validatePlan(plan).isEmpty()) {
                "qualification shard plan failed: ${PestControlMatchupSharding.validatePlan(plan).joinToString()}"
            }
        }
    }

    private fun String.isLowerHex(length: Int): Boolean =
        this.length == length && all { it in '0'..'9' || it in 'a'..'f' }
}

object PestControlV2QualificationShardRunnerGuard {
    const val CONFIGURED_STATE = "AUTHORIZED"

    fun activationErrors(
        plan: ShardedMatchupPlan,
        shardId: String,
        configuredState: String = CONFIGURED_STATE,
        explicitAuthorization: Boolean,
        isUnitTestProcess: Boolean,
        attemptNumber: Int,
        outputAlreadyExists: Boolean,
        checkedOutCommit: String,
        checkedOutTree: String,
    ): List<String> = buildList {
        addAll(PestControlMatchupSharding.validatePlan(plan))
        val freeze = PEST_V2_QUALIFICATION_FREEZE_IDENTITY
        if (plan.identity.protocolId != PEST_V2_OFFICIAL_PROTOCOL ||
            plan.identity.blockId != PEST_V2_QUALIFICATION_BLOCK ||
            plan.identity.freezeCommit != freeze.freezeCommit ||
            plan.identity.registrySha256 != freeze.registrySha256 ||
            plan.identity.orderedVectorSha256 != freeze.orderedVectorSha256 ||
            plan.identity.assignmentCsvSha256 != freeze.assignmentCsvSha256 ||
            plan.identity.freezeManifestSha256 != freeze.freezeManifestSha256
        ) add("qualification identity mismatch")
        if (plan.readinessBaselineCommit != PEST_V2_QUALIFICATION_READINESS_COMMIT) {
            add("qualification readiness baseline mismatch")
        }
        if (plan.assignments.any { it.gate4SourceCommit != PEST_V2_QUALIFIED_RUNNER }) {
            add("qualified runner binding mismatch")
        }
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
