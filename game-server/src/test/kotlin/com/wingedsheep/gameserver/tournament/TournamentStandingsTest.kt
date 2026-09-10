package com.wingedsheep.gameserver.tournament

import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Standings tiebreaker behaviour, per the Magic Tournament Rules order
 * (match points → OMW% → GW% → OGW%). The headline case is the head-to-head *cycle*, which the
 * previous head-to-head comparator could not order consistently (it violated the sort contract).
 */
class TournamentStandingsTest : FunSpec({

    // Drives the full round-robin to completion, deciding each non-bye match with [winnerOf]
    // (null = draw). Byes auto-complete in startNextRound and award no points.
    fun TournamentManager.playOut(winnerOf: (EntityId, EntityId) -> EntityId?) {
        var game = 0
        while (true) {
            val round = startNextRound() ?: break
            for (match in round.matches) {
                if (match.isBye) continue
                val p2 = match.player2Id ?: continue
                val sessionId = "g${game++}"
                match.gameSessionId = sessionId
                reportMatchResult(sessionId, winnerOf(match.player1Id, p2))
            }
        }
    }

    fun pair(p1: EntityId, p2: EntityId, a: EntityId, b: EntityId) =
        (p1 == a && p2 == b) || (p1 == b && p2 == a)

    test("a head-to-head cycle sorts deterministically and never throws") {
        // A beat B, B beat C, C beat A: each 1-1 (3 pts), identical on every metric. The old
        // head-to-head comparator could throw "Comparison method violates its general contract" here.
        val a = EntityId("A"); val b = EntityId("B"); val c = EntityId("C")
        val manager = TournamentManager("lobby", listOf(a to "Alice", b to "Bob", c to "Cara"))

        manager.playOut { p1, p2 ->
            when {
                pair(p1, p2, a, b) -> a
                pair(p1, p2, b, c) -> b
                pair(p1, p2, c, a) -> c
                else -> null
            }
        }

        val standings = manager.getStandings() // must not throw
        standings.map { it.points }.toSet() shouldBe setOf(3)

        val ranked = manager.getRankedStandings()
        ranked shouldNotBe emptyList<RankedStanding>()
        // Truly tied → everyone shares rank 1; followers are reported as TIED.
        ranked.all { it.rank == 1 } shouldBe true
        ranked.drop(1).all { it.tiebreakerReason == TiebreakerReason.TIED } shouldBe true
    }

    test("game-win % breaks a tie that match points and OMW% cannot") {
        // A and B both finish 6 points over 4 matches (equal OMW%), but B drew three and never lost,
        // so B's game-win % (1/1) tops A's (2/4). B must rank above A with reason GW.
        val a = EntityId("A"); val b = EntityId("B")
        val c = EntityId("C"); val d = EntityId("D"); val e = EntityId("E")
        val manager = TournamentManager(
            "lobby",
            listOf(a to "A", b to "B", c to "C", d to "D", e to "E")
        )

        manager.playOut { p1, p2 ->
            when {
                pair(p1, p2, b, a) -> b   // B beats A (B's only win)
                pair(p1, p2, b, c) -> null // B draws C
                pair(p1, p2, b, d) -> null // B draws D
                pair(p1, p2, b, e) -> null // B draws E
                pair(p1, p2, a, c) -> a   // A beats C
                pair(p1, p2, a, d) -> a   // A beats D
                pair(p1, p2, a, e) -> e   // A loses to E
                pair(p1, p2, c, d) -> c
                pair(p1, p2, c, e) -> c
                pair(p1, p2, d, e) -> d
                else -> null
            }
        }

        val info = manager.getStandingsInfo()
        val byName = info.associateBy { it.playerName }

        byName.getValue("A").points shouldBe 6
        byName.getValue("B").points shouldBe 6
        // Equal match points and equal OMW% (same 4-match record, shared opponent pool)…
        byName.getValue("A").omwPercent shouldBe (byName.getValue("B").omwPercent plusOrMinus 1e-9)
        // …but B's game-win % is higher, so B is ranked ahead of A and the break is reported as GW.
        byName.getValue("B").gwPercent shouldBe (1.0 plusOrMinus 1e-9)
        byName.getValue("A").gwPercent shouldBe (0.5 plusOrMinus 1e-9)
        byName.getValue("B").rank shouldBe (byName.getValue("A").rank - 1)
        byName.getValue("A").tiebreakerReason shouldBe TiebreakerReason.GW.name
    }

    test("OMW% floors each opponent's match-win % at 1/3 and excludes byes") {
        // Three players (each round one byes): A 2-0, B 1-1, C 0-2.
        // OMW(A) averages B's MW% (.5) with C's MW% (0 → floored to 1/3).
        val a = EntityId("A"); val b = EntityId("B"); val c = EntityId("C")
        val manager = TournamentManager("lobby", listOf(a to "A", b to "B", c to "C"))

        manager.playOut { p1, p2 ->
            when {
                pair(p1, p2, a, b) -> a
                pair(p1, p2, a, c) -> a
                pair(p1, p2, b, c) -> b
                else -> null
            }
        }

        val info = manager.getStandingsInfo().associateBy { it.playerName }
        // (0.5 + 1/3) / 2 — the floor lifts C's 0% up to 1/3.
        info.getValue("A").omwPercent shouldBe (((0.5 + 1.0 / 3.0) / 2.0) plusOrMinus 1e-9)
    }
})
