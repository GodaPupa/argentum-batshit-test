package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

data class TierOneMonsterTronRunnerContract(
    val protocolId: String = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_MONSTER_TRON_MAIN_SHA256,
    val qualifiedPestRunner: String = PEST_V2_QUALIFIED_RUNNER,
    val pestAiProfile: String = "PRODUCTION_CANDIDATE_EXPIRING",
    val opponentAiProfile: String = "PRODUCTION_CANDIDATE_EXPIRING",
    val mulliganPolicy: String = "ENGINE_AI_LONDON",
    val actionSubmission: String = "EXACTLY_ONE",
    val maxActions: Int = 12_000,
    val maxTurns: Int = 60,
    val maxActionsPerTurn: Int = 500,
    val runnerState: TierOneMonsterTronRunnerState = TierOneMonsterTronRunnerState.DISABLED,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
)

/**
 * Pure contract for the future Monster Tron execution driver.
 *
 * It contains no GameEnvironment, initializer, seed source, action chooser or execution method.
 */
object PestControlTierOneMonsterTronRunnerContract {
    fun validationErrors(
        contract: TierOneMonsterTronRunnerContract = TierOneMonsterTronRunnerContract(),
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(
            PestControlTierOneMonsterTronPolicyReadiness.validationErrors(
                TierOneMonsterTronPolicyReadiness(),
                registry,
            )
        )
        if (contract.protocolId != PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (contract.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest Control identity mismatch")
        if (contract.opponentMainSha256 != PEST_MONSTER_TRON_MAIN_SHA256) add("Monster Tron identity mismatch")
        if (contract.qualifiedPestRunner != PEST_V2_QUALIFIED_RUNNER) add("qualified Pest runner mismatch")
        if (contract.pestAiProfile != "PRODUCTION_CANDIDATE_EXPIRING") add("Pest AI profile mismatch")
        if (contract.opponentAiProfile != "PRODUCTION_CANDIDATE_EXPIRING") add("opponent AI profile mismatch")
        if (contract.mulliganPolicy != "ENGINE_AI_LONDON") add("mulligan policy mismatch")
        if (contract.actionSubmission != "EXACTLY_ONE") add("action submission policy mismatch")
        if (contract.maxActions != 12_000) add("max action ceiling mismatch")
        if (contract.maxTurns != 60) add("max turn ceiling mismatch")
        if (contract.maxActionsPerTurn != 500) add("per-turn action ceiling mismatch")
        if (contract.runnerState != TierOneMonsterTronRunnerState.DISABLED) add("runner must remain disabled")
        if (contract.officialSeedsGenerated != 0) add("official seeds must remain zero")
        if (contract.officialGamesInitialized != 0) add("official games initialized must remain zero")
        if (contract.officialActionsSubmitted != 0) add("official actions must remain zero")
        if (contract.outcomeExposure != 0) add("outcome exposure must remain zero")
    }
}
