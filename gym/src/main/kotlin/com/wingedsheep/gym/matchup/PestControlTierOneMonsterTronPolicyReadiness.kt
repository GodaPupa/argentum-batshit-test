package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

data class TierOneMonsterTronPolicyReadiness(
    val protocolId: String = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_MONSTER_TRON_MAIN_SHA256,
    val scope: String = "PREBOARD_POLICY_CALIBRATION_ONLY",
    val policyStatus: String = "SEEDLESS_EXACT_60_POLICY_ACCEPTED",
    val runnerState: TierOneMonsterTronRunnerState = TierOneMonsterTronRunnerState.DISABLED,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
)

object PestControlTierOneMonsterTronPolicyReadiness {
    fun validationErrors(
        readiness: TierOneMonsterTronPolicyReadiness,
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(PestControlTierOneMonsterTronReadiness.validationErrors(TierOneMonsterTronReadiness(), registry))
        if (readiness.protocolId != PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest Control identity mismatch")
        if (readiness.opponentMainSha256 != PEST_MONSTER_TRON_MAIN_SHA256) add("Monster Tron identity mismatch")
        if (readiness.scope != "PREBOARD_POLICY_CALIBRATION_ONLY") add("scope mismatch")
        if (readiness.policyStatus != "SEEDLESS_EXACT_60_POLICY_ACCEPTED") add("policy status mismatch")
        if (readiness.runnerState != TierOneMonsterTronRunnerState.DISABLED) add("runner must remain disabled")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.officialGamesInitialized != 0) add("official games must not be initialized")
        if (readiness.officialActionsSubmitted != 0) add("official actions must remain zero")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
    }

    fun executionActivationErrors(
        readiness: TierOneMonsterTronPolicyReadiness,
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(validationErrors(readiness, registry))
        add("no execution runner is defined")
        add("no official seed vector is frozen")
        add("official Monster Tron games are not authorized")
    }
}
