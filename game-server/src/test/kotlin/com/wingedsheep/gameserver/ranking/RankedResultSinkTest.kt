package com.wingedsheep.gameserver.ranking

import com.wingedsheep.gameserver.persistence.RatingHistoryRepository
import com.wingedsheep.gameserver.persistence.RatingHistoryRow
import com.wingedsheep.gameserver.persistence.UserRatingRepository
import com.wingedsheep.gameserver.persistence.UserRatingRow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class RankedResultSinkTest : FunSpec({

    val u1: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val u2: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val u5: UUID = UUID.fromString("00000000-0000-0000-0000-000000000005")
    val u10: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    val u20: UUID = UUID.fromString("00000000-0000-0000-0000-000000000014")

    /** A fresh sink over relaxed mock repos, with both rating rows starting unrated (lazy init). */
    fun fixture(): Triple<JdbcRankedResultSink, MutableList<UserRatingRow>, MutableList<RatingHistoryRow>> {
        val ratings = mockk<UserRatingRepository>()
        val history = mockk<RatingHistoryRepository>()
        val savedRatings = mutableListOf<UserRatingRow>()
        val savedHistory = mutableListOf<RatingHistoryRow>()
        every { ratings.findByUserIdAndMode(any(), any()) } returns null
        every { ratings.save(capture(savedRatings)) } answers { firstArg() }
        every { history.save(capture(savedHistory)) } answers { firstArg() }
        return Triple(JdbcRankedResultSink(ratings, history), savedRatings, savedHistory)
    }

    test("equal new players: winner gains, loser loses the provisional half-K") {
        val (sink, savedRatings, savedHistory) = fixture()
        sink.record(RankedGameResult("g1", RankedMode.LIMITED, playerOneUserId = u1, playerTwoUserId = u2, winnerUserId = u1))

        val winner = savedRatings.first { it.userId == u1 }
        val loser = savedRatings.first { it.userId == u2 }
        // Equal 1200 starts, provisional K=40, expected 0.5 → ±20.
        winner.rating shouldBe (1220.0 plusOrMinus 1e-9)
        loser.rating shouldBe (1180.0 plusOrMinus 1e-9)
        winner.wins shouldBe 1
        winner.gamesPlayed shouldBe 1
        loser.losses shouldBe 1
        winner.peakRating shouldBe (1220.0 plusOrMinus 1e-9)

        savedHistory shouldHaveSize 2
        savedHistory.first { it.userId == u1 }.result shouldBe "WIN"
        savedHistory.first { it.userId == u1 }.delta shouldBe (20.0 plusOrMinus 1e-9)
        savedHistory.first { it.userId == u2 }.result shouldBe "LOSS"
        savedHistory.first { it.userId == u2 }.delta shouldBe (-20.0 plusOrMinus 1e-9)
    }

    test("a draw leaves equal players unchanged and records DRAW for both") {
        val (sink, savedRatings, savedHistory) = fixture()
        sink.record(RankedGameResult("g2", RankedMode.COMMANDER, playerOneUserId = u1, playerTwoUserId = u2, winnerUserId = null))

        savedRatings.forEach { it.rating shouldBe (1200.0 plusOrMinus 1e-9) }
        savedRatings.forEach { it.draws shouldBe 1 }
        savedHistory.map { it.result }.toSet() shouldBe setOf("DRAW")
    }

    test("history rows attribute the opponent and game") {
        val (sink, _, savedHistory) = fixture()
        sink.record(RankedGameResult("game-7", RankedMode.CONSTRUCTED, playerOneUserId = u10, playerTwoUserId = u20, winnerUserId = u20))
        val p1 = savedHistory.first { it.userId == u10 }
        p1.opponentUserId shouldBe u20
        p1.gameId shouldBe "game-7"
        p1.mode shouldBe "CONSTRUCTED"
    }

    test("a game with the same account on both seats is ignored") {
        val ratings = mockk<UserRatingRepository>(relaxed = true)
        val history = mockk<RatingHistoryRepository>(relaxed = true)
        JdbcRankedResultSink(ratings, history)
            .record(RankedGameResult("g3", RankedMode.LIMITED, playerOneUserId = u5, playerTwoUserId = u5, winnerUserId = u5))
        verify(exactly = 0) { ratings.save(any()) }
        verify(exactly = 0) { history.save(any()) }
    }

    test("an existing rating is carried forward, not reset") {
        val ratings = mockk<UserRatingRepository>()
        val history = mockk<RatingHistoryRepository>()
        every { history.save(any()) } answers { firstArg() }
        val saved = mutableListOf<UserRatingRow>()
        // Player 1 is already established at 1800 with 30 games; player 2 is unrated.
        every { ratings.findByUserIdAndMode(u1, "LIMITED") } returns
            UserRatingRow(userId = u1, mode = "LIMITED", rating = 1800.0, gamesPlayed = 30, wins = 20, peakRating = 1820.0)
        every { ratings.findByUserIdAndMode(u2, "LIMITED") } returns null
        every { ratings.save(capture(saved)) } answers { firstArg() }

        JdbcRankedResultSink(ratings, history)
            .record(RankedGameResult("g4", RankedMode.LIMITED, playerOneUserId = u1, playerTwoUserId = u2, winnerUserId = u1))

        val winner = saved.first { it.userId == u1 }
        winner.gamesPlayed shouldBe 31
        winner.wins shouldBe 21
        // Established K=24, favourite beating an unrated peer gains only a little.
        (winner.rating > 1800.0 && winner.rating < 1810.0) shouldBe true
        // New rating is still below the prior peak, so the career high is unchanged.
        winner.peakRating shouldBe (1820.0 plusOrMinus 1e-9)
    }
})
