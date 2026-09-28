package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Trusted-side candidate for the accepted Ponder reorder transition. No engine state or receipt
 * enters the pilot: it receives only the separately sealed ActorInput and the accepted memory.
 * The caller must durably retain [Accepted.recordJson] with the raw game attempt journal.
 * This is not a complete Stage-E runner, replay/admission receipt, or official allocation.
 */
internal class SphinxStageEAcceptedTransitionJournal(
    private val processor: ActionProcessor, registry: CardRegistry,
) {
    private val adapter = ObservationAdapter(registry)
    private val enumerator = LegalActionEnumerator.create(registry)
    @Serializable
    data class Record(
        val inputBindingHash: String,
        val sourceVersion: String,
        val trialId: String,
        val actorStep: Long,
        val decisionId: String,
        val preStateSha256: String,
        val actionJson: String,
        val postStateSha256: String,
    )

    data class Accepted(
        val state: GameState,
        val memory: SphinxStageEPonderMemory,
        val recordJson: String,
    )

    private val json = Json {
        serializersModule = engineSerializersModule
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    fun acceptPonderReorder(
        state: GameState,
        input: ActorInput,
        epoch: ActorEpoch,
        pilot: SphinxStageEInitializedSeat,
        proposal: ActorProposal,
    ): Accepted {
        input.verifyBinding(epoch, pilot.actorId)
        val question = input.decision as? ReorderLibraryDecision
            ?: error("Expected the actor's Ponder reorder question")
        val raw = state.pendingDecision as? ReorderLibraryDecision
            ?: error("Current engine has no Ponder reorder suspension")
        // The actor's question deliberately sanitizes source metadata and card summaries, so
        // structural equality with the raw suspension is neither valid nor safe. Reproject the
        // current state and require byte-identical sealed actor input instead.
        val current = adapter.build(state, pilot.actorId,
            completeActorLegalActions(state, pilot.actorId, enumerator),
            epoch, input.policyRngState)
        require(current.canonicalJson() == input.canonicalJson() &&
            raw.id == question.id && raw.playerId == pilot.actorId &&
            raw.context.sourceId == question.context.sourceId &&
            raw.cards.toSet() == question.cards.toSet() &&
            question.context.sourceName == "Ponder") {
            "Current engine projection differs from the sealed Ponder question"
        }
        require(proposal.inputBindingHash == input.bindingHash) { "Stale Ponder proposal" }
        val action = proposal.action as? SubmitDecision
            ?: error("Ponder reorder must submit the typed response")
        require(action.playerId == pilot.actorId && action.response.decisionId == question.id)
        val preHash = stateHash(state)
        val result = processor.process(state, action).result
        require(result.error == null) { "Engine rejected the Ponder reorder: ${result.error}" }
        val continuation = result.state.pendingDecision as? YesNoDecision
            ?: error("Accepted Ponder reorder did not produce the shuffle continuation")
        require(continuation.playerId == pilot.actorId &&
            continuation.context.sourceId == question.context.sourceId &&
            continuation.context.sourceName == "Ponder" &&
            continuation.id != question.id) { "Ponder continuation belongs to another source or actor" }
        val memory = pilot.rememberAcceptedPonderReorder(input, epoch, proposal)
        val record = Record(input.bindingHash, epoch.sourceVersion, epoch.trialId, epoch.step,
            question.id, preHash, json.encodeToString(action), stateHash(result.state))
        return Accepted(result.state, memory, json.encodeToString(record))
    }

    /** Replays the same accepted physical action from the recorded prestate; fail closed on drift. */
    fun replay(preState: GameState, recordJson: String): GameState {
        val record = json.decodeFromString<Record>(recordJson)
        require(stateHash(preState) == record.preStateSha256) { "Ponder prestate drift" }
        val action = json.decodeFromString<SubmitDecision>(record.actionJson)
        require(preState.pendingDecision?.id == record.decisionId &&
            preState.pendingDecision?.playerId == action.playerId &&
            action.response.decisionId == record.decisionId)
        val result = processor.process(preState, action).result
        require(result.error == null && stateHash(result.state) == record.postStateSha256) {
            "Ponder accepted-transition replay drift"
        }
        return result.state
    }

    private fun stateHash(state: GameState): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(GameState.serializer(), state).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
