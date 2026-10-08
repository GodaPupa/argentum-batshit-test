package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*

/**
 * Privileged, read-only correspondence for the events emitted by the fixed synthetic initializer.
 * An independently retained history pin is required. Equality does not authenticate historical
 * origin, seed custody, runtime/registry provenance, or events not emitted by GameInitializer.
 * No writer, recovery, admission, policy call or continuation is provided.
 */
internal object ManualInitializationHistory {
    private val json = PhaseTwoTelemetryAdapter.JSON
    fun encode(identity: ManualFixtureIdentity, specPin: String, tracePin: String,
               initialPin: String, events: List<GameEvent>): ByteArray {
        listOf(specPin, tracePin, initialPin).forEach { require(it.matches(Regex("[0-9a-f]{64}"))) }
        return buildJsonObject {
            put("schema", "manual-excluded-initialization-history-v1")
            put("executionAuthorized", false); put("authenticatedProvenance", false)
            put("identitySha256", ManualInitializationReplay.sha256(identity.bytes()))
            put("specificationSha256", specPin); put("traceSha256", tracePin)
            put("initialStateSha256", initialPin)
            put("events", JsonArray(events.map { json.encodeToJsonElement(GameEvent.serializer(), it) }))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun verify(history: ByteArray, historyPin: String, directory: Path, identity: ManualFixtureIdentity,
               specPin: String, tracePin: String, registry: CardRegistry) {
        val retained = history.copyOf()
        require(retained.size in 1..(16 * 1024 * 1024))
        require(historyPin.matches(Regex("[0-9a-f]{64}")) &&
            ManualInitializationReplay.sha256(retained) == historyPin)
        // Accepted verifier validates original layout, identity, canonical spec, reconstruction,
        // journal and complete semantic action replay. No history is accepted in isolation.
        val trace = ManualBoundActionReplay.verify(directory, identity, specPin, tracePin, registry)
        val specBytes = Files.readAllBytes(directory.resolve("initialization-spec.json"))
        require(ManualInitializationReplay.sha256(specBytes) == specPin)
        val spec = json.decodeFromString(ManualInitializationSpec.serializer(), specBytes.toString(Charsets.UTF_8))
        require(ManualInitializationReplay.encode(spec).contentEquals(specBytes))
        val result = GameInitializer(registry).initializeGame(GameConfig(
            players = spec.players.map { PlayerConfig(it.name, Deck(it.cards), playerId = EntityId(it.playerId),
                commanderCardNames = it.commanders) },
            format = Format.Commander(), startingHandSize = 7, skipMulligans = false,
            useHandSmoother = false, startingPlayerIndex = spec.startingPlayerIndex, seed = spec.seed))
        require(json.encodeToJsonElement(GameState.serializer(), result.state) == trace.initialState)
        require(result.seed == spec.seed && result.playerIds.map { it.value } == spec.players.map { it.playerId })
        require(retained.contentEquals(encode(identity, specPin, tracePin, trace.initialStateSha256, result.events))) {
            "Initialization event history differs from exact reconstructed event order"
        }
        require(ManualBoundActionReplay.verify(directory, identity, specPin, tracePin, registry) == trace)
        require(Files.readAllBytes(directory.resolve("initialization-spec.json")).contentEquals(specBytes))
    }
}
