package com.wingedsheep.gameserver.ranking

import com.wingedsheep.gameserver.persistence.RatingHistoryRepository
import com.wingedsheep.gameserver.persistence.RatingHistoryRow
import com.wingedsheep.gameserver.persistence.UserRatingRepository
import com.wingedsheep.gameserver.persistence.UserRatingRow
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.math.max

/**
 * A finished, ranking-eligible 1v1 game between two signed-in accounts. The game-over path constructs
 * this only when [com.wingedsheep.gameserver.session.GameSession.ranked] is set and exactly two
 * logged-in human seats are present; everything else (guests, AI, multiplayer, casual) never reaches
 * the sink. [winnerUserId] is one of the two ids, or null for a draw.
 */
data class RankedGameResult(
    val gameId: String,
    val mode: RankedMode,
    val playerOneUserId: UUID,
    val playerTwoUserId: UUID,
    val winnerUserId: UUID?,
)

/**
 * Applies ELO rating changes for finished ranked games. Mirrors [MatchResultSink][com.wingedsheep.gameserver.stats.MatchResultSink]:
 * the game-over path calls this unconditionally and the wired implementation depends on whether
 * accounts are enabled, so [GamePlayHandler][com.wingedsheep.gameserver.handler.GamePlayHandler] stays
 * decoupled from persistence.
 */
interface RankedResultSink {
    fun record(result: RankedGameResult)
}

/** Default: accounts disabled — no ratings are tracked. */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpRankedResultSink : RankedResultSink {
    override fun record(result: RankedGameResult) = Unit
}

/**
 * Accounts enabled: adjust both players' [Elo] rating for the game's mode and append a history row for
 * each. Both updates happen in one transaction. A rating row is created lazily at the starting rating
 * on a player's first ranked game in a mode.
 */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class JdbcRankedResultSink(
    private val userRatings: UserRatingRepository,
    private val ratingHistory: RatingHistoryRepository,
) : RankedResultSink {
    private val logger = LoggerFactory.getLogger(JdbcRankedResultSink::class.java)

    @Transactional
    override fun record(result: RankedGameResult) {
        if (result.playerOneUserId == result.playerTwoUserId) return
        val mode = result.mode.name
        val one = userRatings.findByUserIdAndMode(result.playerOneUserId, mode)
            ?: UserRatingRow(userId = result.playerOneUserId, mode = mode)
        val two = userRatings.findByUserIdAndMode(result.playerTwoUserId, mode)
            ?: UserRatingRow(userId = result.playerTwoUserId, mode = mode)

        val (scoreOne, scoreTwo) = when (result.winnerUserId) {
            null -> 0.5 to 0.5
            result.playerOneUserId -> 1.0 to 0.0
            result.playerTwoUserId -> 0.0 to 1.0
            else -> return // winner isn't one of the two seats — ignore defensively
        }

        val expectedOne = Elo.expectedScore(one.rating, two.rating)
        val newOne = Elo.newRating(one.rating, expectedOne, scoreOne, Elo.kFactor(one.gamesPlayed))
        val newTwo = Elo.newRating(two.rating, 1.0 - expectedOne, scoreTwo, Elo.kFactor(two.gamesPlayed))

        val now = Instant.now()
        apply(one, newOne, scoreOne, opponentRating = two.rating, opponentUserId = two.userId, gameId = result.gameId, now = now)
        apply(two, newTwo, scoreTwo, opponentRating = one.rating, opponentUserId = one.userId, gameId = result.gameId, now = now)
        logger.debug(
            "Ranked {} game {}: user {} {}->{}, user {} {}->{}",
            mode, result.gameId,
            one.userId, one.rating.toInt(), newOne.toInt(),
            two.userId, two.rating.toInt(), newTwo.toInt(),
        )
    }

    private fun apply(
        current: UserRatingRow,
        newRating: Double,
        score: Double,
        opponentRating: Double,
        opponentUserId: UUID,
        gameId: String,
        now: Instant,
    ) {
        val outcome = outcomeOf(score)
        userRatings.save(
            current.copy(
                rating = newRating,
                gamesPlayed = current.gamesPlayed + 1,
                wins = current.wins + if (outcome == "WIN") 1 else 0,
                losses = current.losses + if (outcome == "LOSS") 1 else 0,
                draws = current.draws + if (outcome == "DRAW") 1 else 0,
                peakRating = max(current.peakRating, newRating),
                updatedAt = now,
            )
        )
        ratingHistory.save(
            RatingHistoryRow(
                userId = current.userId,
                mode = current.mode,
                ratingBefore = current.rating,
                ratingAfter = newRating,
                delta = newRating - current.rating,
                result = outcome,
                opponentUserId = opponentUserId,
                opponentRating = opponentRating,
                gameId = gameId,
                createdAt = now,
            )
        )
    }

    private fun outcomeOf(score: Double): String = when {
        score >= 1.0 -> "WIN"
        score <= 0.0 -> "LOSS"
        else -> "DRAW"
    }
}
