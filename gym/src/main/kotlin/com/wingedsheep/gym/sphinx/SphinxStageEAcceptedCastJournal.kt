package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Trusted accepted current-cast path; raw state and durable records never enter the pilot. */
internal class SphinxStageEAcceptedCastJournal(
    private val processor: ActionProcessor, registry: CardRegistry,
) {
    private val adapter = ObservationAdapter(registry)
    private val enumerator = LegalActionEnumerator.create(registry)
    private val json = Json {
        serializersModule = engineSerializersModule
        encodeDefaults = true
        allowStructuredMapKeys = true
    }
    @Serializable
    data class Record(
        val inputBindingHash: String, val sourceVersion: String, val trialId: String,
        val actorStep: Long, val policyRngState: Long, val decisionId: String,
        val preStateSha256: String, val actionJson: String, val postStateSha256: String,
    )
    data class Accepted(val state: GameState, val recordJson: String)

    fun accept(state: GameState, input: ActorInput, pilot: SphinxStageEInitializedSeat,
               offerIndex: Int, component: SphinxStageEComponentCall,
               proposal: ActorProposal, durable: SphinxStageETrustedTransitionFile): Accepted {
        input.verifyBinding(input.epoch, pilot.actorId)
        require(state.pendingDecision == null) { "A pending decision cannot be cast through" }
        val current = adapter.build(state, pilot.actorId,
            completeActorLegalActions(state, pilot.actorId, enumerator),
            input.epoch, input.policyRngState)
        require(current.canonicalJson() == input.canonicalJson()) { "Current cast projection drift" }
        val expected = pilot.decideCurrentCast(input, input.epoch, offerIndex, component)
            as? SphinxStageEAdapterResult.Proposed ?: error("Current cast is unqualified")
        require(proposal == expected.proposal) { "Cast differs from bound policy choice" }
        val action = proposal.action as? CastSpell ?: error("Expected physical cast")
        require(action.playerId == pilot.actorId)
        val result = processor.process(state, action).result
        require(result.error == null) { "Engine rejected cast: ${result.error}" }
        val record = Record(input.bindingHash, input.epoch.sourceVersion, input.epoch.trialId,
            input.epoch.step, input.policyRngState, "cast/${input.bindingHash}/$offerIndex",
            hash(state), json.encodeToString(action), hash(result.state))
        val encoded = json.encodeToString(record)
        // Publish the accepted state only after create-only/hash-linked/fsynced persistence.
        durable.append(encoded)
        return Accepted(result.state, encoded)
    }

    fun replay(before: GameState, encoded: String): GameState {
        val record = json.decodeFromString<Record>(encoded)
        require(hash(before) == record.preStateSha256) { "Accepted cast prestate drift" }
        val action = json.decodeFromString<CastSpell>(record.actionJson)
        val result = processor.process(before, action).result
        require(result.error == null && hash(result.state) == record.postStateSha256) {
            "Accepted cast replay drift"
        }
        return result.state
    }

    private fun hash(state: GameState): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(GameState.serializer(), state).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
