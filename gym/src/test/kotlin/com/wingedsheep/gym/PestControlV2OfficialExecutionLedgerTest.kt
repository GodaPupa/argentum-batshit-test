package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PestControlV2OfficialExecutionLedger
import com.wingedsheep.gym.matchup.V2OfficialRunnerState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

private val V2_SYNTHETIC_FROZEN_VECTOR = (1L..10L).map { 8_000_000L + it }

class PestControlV2OfficialExecutionLedgerTest : FunSpec({
    test("official ledger consumes immutable vector exactly once and in order") {
        val ledger = PestControlV2OfficialExecutionLedger(V2_SYNTHETIC_FROZEN_VECTOR)
        ledger.state shouldBe V2OfficialRunnerState.SEALED
        ledger.authorize(emptyList())
        V2_SYNTHETIC_FROZEN_VECTOR.forEachIndexed { index, seed ->
            val attempt = ledger.markNextAttempted()
            attempt.gameNumber shouldBe index + 1
            attempt.seed shouldBe seed
        }
        ledger.attempts.map { it.seed } shouldBe V2_SYNTHETIC_FROZEN_VECTOR
        ledger.complete()
        ledger.state shouldBe V2OfficialRunnerState.COMPLETED
        shouldThrow<IllegalStateException> { ledger.markNextAttempted() }
    }

    test("preflight failure cannot authorize official execution") {
        val ledger = PestControlV2OfficialExecutionLedger(V2_SYNTHETIC_FROZEN_VECTOR)
        shouldThrow<IllegalStateException> { ledger.authorize(listOf("synthetic mismatch")) }
        ledger.state shouldBe V2OfficialRunnerState.SEALED
        ledger.attempts shouldBe emptyList()
    }

    test("partial block cannot complete and no reroll or replacement route exists") {
        val ledger = PestControlV2OfficialExecutionLedger(V2_SYNTHETIC_FROZEN_VECTOR)
        ledger.authorize(emptyList())
        ledger.markNextAttempted()
        shouldThrow<IllegalStateException> { ledger.complete() }
        ledger.rerollAllowed() shouldBe false
        ledger.replacementAllowed() shouldBe false
        ledger.reject()
        ledger.state shouldBe V2OfficialRunnerState.REJECTED
        shouldThrow<IllegalStateException> { ledger.markNextAttempted() }
    }

    test("invalid vector cannot construct a ledger") {
        shouldThrow<IllegalArgumentException> { PestControlV2OfficialExecutionLedger(List(10) { 1L }) }
        shouldThrow<IllegalArgumentException> { PestControlV2OfficialExecutionLedger((1L..9L).toList()) }
        shouldThrow<IllegalArgumentException> { PestControlV2OfficialExecutionLedger((0L..9L).toList()) }
    }
})
