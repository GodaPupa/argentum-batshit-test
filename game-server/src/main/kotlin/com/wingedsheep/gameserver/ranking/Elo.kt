package com.wingedsheep.gameserver.ranking

import kotlin.math.pow

/**
 * The three ranked queues a signed-in player carries a separate rating in. Mirrors how MTG Arena
 * splits ranked into Limited vs Constructed (we add Commander as its own queue). The mode of a
 * finished game is derived from its lobby's format at creation time — see `Ranked.modeFor*`.
 */
enum class RankedMode { LIMITED, CONSTRUCTED, COMMANDER }

/**
 * A display band derived purely from a player's ELO, except for [PROVISIONAL] which depends on games
 * played. Arena hides its MMR behind tiers; here the tier is an honest function of the visible rating
 * (so a player can always see exactly why they are in a band), with a placement window up front where
 * the rating is still settling and no band is shown yet.
 */
enum class RatingTier(val displayName: String) {
    PROVISIONAL("Provisional"),
    BRONZE("Bronze"),
    SILVER("Silver"),
    GOLD("Gold"),
    PLATINUM("Platinum"),
    DIAMOND("Diamond"),
    MYTHIC("Mythic"),
}

/**
 * Standard ELO, calibrated to feel like chess.com's numbers: new ratings start at [STARTING_RATING]
 * (1200, as on chess.com) and a roughly even game shifts an established rating by about ±10.
 * While a player has fewer than [PROVISIONAL_GAMES] games in a mode their rating moves at the higher
 * [K_PROVISIONAL] (so placement converges quickly), then settles to [K_ESTABLISHED] — the
 * "depends on how many games you've played" part of a rank, applied to a real ELO number.
 *
 * Pure and side-effect free so the math is unit-tested directly; the sink handles persistence.
 */
object Elo {
    const val STARTING_RATING: Double = 1200.0
    const val PROVISIONAL_GAMES: Int = 10
    const val K_PROVISIONAL: Double = 40.0
    const val K_ESTABLISHED: Double = 20.0

    /** Probability that a player rated [rating] beats one rated [opponentRating] (the ELO logistic). */
    fun expectedScore(rating: Double, opponentRating: Double): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    /** Higher movement during the placement window, then the steady-state factor. */
    fun kFactor(gamesPlayed: Int): Double =
        if (gamesPlayed < PROVISIONAL_GAMES) K_PROVISIONAL else K_ESTABLISHED

    /**
     * New rating after a game with [score] (1.0 win, 0.5 draw, 0.0 loss) against an opponent the player
     * was [expected] to beat with that probability, using the player's current [kFactor].
     */
    fun newRating(rating: Double, expected: Double, score: Double, kFactor: Double): Double =
        rating + kFactor * (score - expected)

    /** Display tier: no band until placement is complete, then a pure function of the rating. */
    fun tier(rating: Double, gamesPlayed: Int): RatingTier {
        if (gamesPlayed < PROVISIONAL_GAMES) return RatingTier.PROVISIONAL
        // Bands sit around chess.com-style numbers: a new player settles into Gold (~1200) and
        // climbs from there; Mythic is the 2000+ master tier.
        return when {
            rating < 1000.0 -> RatingTier.BRONZE
            rating < 1200.0 -> RatingTier.SILVER
            rating < 1400.0 -> RatingTier.GOLD
            rating < 1600.0 -> RatingTier.PLATINUM
            rating < 2000.0 -> RatingTier.DIAMOND
            else -> RatingTier.MYTHIC
        }
    }
}
