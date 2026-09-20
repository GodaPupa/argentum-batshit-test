package com.wingedsheep.gym.matchup

const val PEST_V2_OFFICIAL_PROTOCOL = PEST_MONO_RED_PREBOARD_PROTOCOL_ID
const val PEST_V2_OFFICIAL_BLOCK = "${PEST_V2_OFFICIAL_PROTOCOL}_V2_OFFICIAL_10"
const val PEST_V2_QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
const val PEST_V2_ORDERED_VECTOR_SHA256 = "c88d45352f531a08a484004f35ffbcc44ef08331f2c7844e5c11449e02c2d104"
const val PEST_V2_ASSIGNMENT_CSV_SHA256 = "0df747d1928753b92b7fa812fbeccdd3f639490a26a9fdab99a9d203b3e9857f"
const val PEST_V2_EXPECTED_GAMES = 10

enum class V2OfficialRunnerState { SEALED, AUTHORIZED, STARTED, COMPLETED, REJECTED }

data class V2OfficialAttempt(val gameNumber: Int, val seed: Long)

class PestControlV2OfficialExecutionLedger(
    private val frozenSeeds: List<Long>,
) {
    init {
        require(frozenSeeds.size == PEST_V2_EXPECTED_GAMES)
        require(frozenSeeds.distinct().size == PEST_V2_EXPECTED_GAMES)
        require(frozenSeeds.none { it == 0L })
    }

    private val mutableAttempts = mutableListOf<V2OfficialAttempt>()
    val attempts: List<V2OfficialAttempt> get() = mutableAttempts.toList()
    var state: V2OfficialRunnerState = V2OfficialRunnerState.SEALED
        private set

    fun authorize(preflightErrors: List<String>) {
        check(state == V2OfficialRunnerState.SEALED)
        check(preflightErrors.isEmpty()) { "V2 official preflight failed: ${preflightErrors.joinToString()}" }
        state = V2OfficialRunnerState.AUTHORIZED
    }

    /**
     * Must be called and durably persisted before experimental session initialization.
     * The next seed is determined solely by the immutable frozen vector prefix.
     */
    fun markNextAttempted(): V2OfficialAttempt {
        check(state == V2OfficialRunnerState.AUTHORIZED || state == V2OfficialRunnerState.STARTED)
        check(mutableAttempts.size < frozenSeeds.size) { "all official seeds already attempted" }
        val position = mutableAttempts.size
        val attempt = V2OfficialAttempt(position + 1, frozenSeeds[position])
        mutableAttempts += attempt
        state = V2OfficialRunnerState.STARTED
        return attempt
    }

    fun complete() {
        check(state == V2OfficialRunnerState.STARTED)
        check(mutableAttempts.size == frozenSeeds.size) { "partial official block cannot complete" }
        state = V2OfficialRunnerState.COMPLETED
    }

    fun reject() {
        check(state != V2OfficialRunnerState.COMPLETED)
        state = V2OfficialRunnerState.REJECTED
    }

    fun replacementAllowed(): Boolean = false
    fun rerollAllowed(): Boolean = false
}
