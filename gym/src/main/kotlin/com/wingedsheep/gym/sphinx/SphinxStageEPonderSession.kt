package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorProposal
import java.nio.file.Path

/**
 * Trusted two-action Ponder lifecycle. State and memory publish only after durable append.
 * Reopening reconstructs accepted visible memory from the verified prestate and recorded input
 * binding. No journal, raw state or recovery input is sent to the actor. Not a whole-game runner.
 */
internal class SphinxStageEPonderSession private constructor(
    initial: GameState,
    private val pilot: SphinxStageEInitializedSeat,
    private val transitions: SphinxStageEAcceptedTransitionJournal,
    private val durable: SphinxStageETrustedTransitionFile,
    private var acceptedCount: Int,
    private var memory: SphinxStageEPonderMemory?,
) {
    var state: GameState = initial
        private set
    private var stopped = false

    fun acceptReorder(input: ActorInput, proposal: ActorProposal) {
        check(!stopped && acceptedCount == 0) { "Ponder reorder already consumed or session stopped" }
        val accepted = transitions.acceptPonderReorder(state, input, input.epoch, pilot, proposal)
        persist(accepted.recordJson)
        state = accepted.state
        memory = accepted.memory
        acceptedCount = 1
    }

    fun decideShuffle(input: ActorInput): SphinxStageEAdapterResult {
        check(!stopped && acceptedCount == 1) { "No live accepted Ponder memory" }
        return pilot.decidePonderShuffle(input, input.epoch, requireNotNull(memory))
    }

    fun acceptShuffle(input: ActorInput, proposal: ActorProposal) {
        check(!stopped && acceptedCount == 1) { "Ponder memory consumed or session stopped" }
        val accepted = transitions.acceptPonderShuffle(
            state, input, input.epoch, pilot, requireNotNull(memory), proposal)
        persist(accepted.recordJson)
        state = accepted.state
        memory = null
        acceptedCount = 2
    }

    private fun persist(record: String) {
        try {
            durable.append(record)
        } catch (failure: Exception) {
            stopped = true
            throw failure
        }
    }

    companion object {
        fun create(path: Path, preState: GameState, pilot: SphinxStageEInitializedSeat,
                   transitions: SphinxStageEAcceptedTransitionJournal) =
            SphinxStageEPonderSession(preState, pilot, transitions,
                SphinxStageETrustedTransitionFile.create(path), 0, null)

        fun reopen(path: Path, preState: GameState, pilot: SphinxStageEInitializedSeat,
                   transitions: SphinxStageEAcceptedTransitionJournal): SphinxStageEPonderSession {
            val durable = SphinxStageETrustedTransitionFile.reopen(path)
            val records = durable.records()
            require(records.size <= 2) { "A Ponder session contains at most two accepted transitions" }
            if (records.isEmpty()) return SphinxStageEPonderSession(
                preState, pilot, transitions, durable, 0, null)
            val recovered = transitions.recoverPonderMemory(preState, records[0].first, pilot)
            val state = if (records.size == 2) transitions.replay(
                recovered.state, records[1].first) else recovered.state
            return SphinxStageEPonderSession(state, pilot, transitions, durable,
                records.size, if (records.size == 1) recovered.memory else null)
        }
    }
}
