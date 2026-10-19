package com.wingedsheep.ai.engine

/**
 * Deck-slot challenger only. Shares the untouched R5 decision policy; the companion
 * gameplay harness loads gameplay/manual-v08-wan/CHALLENGER.decklist.txt at GameConfig.
 * Never mutates v0.7, R5 baseline, or AiProfile.CURRENT.
 */
object ManualTransmissionV08WanCandidate {
    val profile: AiProfile = ManualTransmissionR5Candidate.profile.copy(
        id = "manual-v08-wan-shi-tong-opt-in"
    )
}
