package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck

data class TierOneMonsterTronReadiness(
    val protocolId: String = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
    val opponentPilot: String = "mehanske",
    val opponentEventDate: String = "2026-09-21",
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_MONSTER_TRON_MAIN_SHA256,
    val opponentSideboardSha256: String = PEST_MONSTER_TRON_SIDEBOARD_SHA256,
    val opponentComplete75Sha256: String = PEST_MONSTER_TRON_COMPLETE_75_SHA256,
    val scope: String = "PREBOARD_READINESS_ONLY",
    val mainSupportStatus: String = "SUPPORTED_PREBOARD_60",
    val sideboardStatus: String = "FROZEN_15; IDENTITY_ONLY; NOT_INSTANTIATED",
    val runnerState: TierOneMonsterTronRunnerState = TierOneMonsterTronRunnerState.DISABLED,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
)

object PestControlTierOneMonsterTronReadiness {
    fun mainDeck(): Deck = Deck.of(
        *PestControlTierOneMonsterTronAdmission.mainCounts
            .map { it.key to it.value }
            .toTypedArray()
    )

    fun validationErrors(
        readiness: TierOneMonsterTronReadiness,
        registry: CardRegistry? = null,
    ): List<String> = buildList {
        // The accepted admission gate remains the provenance/hash authority.
        addAll(PestControlTierOneMonsterTronAdmission.validationErrors(TierOneMonsterTronAdmission()))

        if (readiness.protocolId != PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (readiness.opponentPilot != "mehanske") add("opponent pilot mismatch")
        if (readiness.opponentEventDate != "2026-09-21") add("opponent event date mismatch")
        if (readiness.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest Control identity mismatch")
        if (readiness.opponentMainSha256 != PEST_MONSTER_TRON_MAIN_SHA256) add("Monster Tron main hash mismatch")
        if (readiness.opponentSideboardSha256 != PEST_MONSTER_TRON_SIDEBOARD_SHA256) add("Monster Tron sideboard hash mismatch")
        if (readiness.opponentComplete75Sha256 != PEST_MONSTER_TRON_COMPLETE_75_SHA256) {
            add("Monster Tron complete-75 hash mismatch")
        }
        if (readiness.scope != "PREBOARD_READINESS_ONLY") add("scope must remain preboard readiness only")
        if (readiness.mainSupportStatus != "SUPPORTED_PREBOARD_60") add("main support status mismatch")
        if (readiness.sideboardStatus != "FROZEN_15; IDENTITY_ONLY; NOT_INSTANTIATED") {
            add("sideboard status mismatch")
        }
        if (readiness.runnerState != TierOneMonsterTronRunnerState.DISABLED) add("runner must remain disabled")
        if (readiness.officialGamesAuthorized != 0) add("official games are not authorized")
        if (readiness.officialSeedsGenerated != 0) add("official seeds must not exist")
        if (readiness.officialGamesInitialized != 0) add("official games must not be initialized")
        if (readiness.officialActionsSubmitted != 0) add("official actions must remain zero")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")

        registry?.let {
            if (PestControlTierOneMonsterTronAdmission.unresolvedMain(it).isNotEmpty()) {
                add("Monster Tron maindeck support audit drift")
            }
        }
    }

    fun executionActivationErrors(
        readiness: TierOneMonsterTronReadiness,
        registry: CardRegistry,
    ): List<String> = buildList {
        addAll(validationErrors(readiness, registry))
        add("opponent policy calibration is not accepted")
        add("no execution runner is defined")
        add("no official seed vector is frozen")
        add("official Monster Tron games are not authorized")
    }
}
