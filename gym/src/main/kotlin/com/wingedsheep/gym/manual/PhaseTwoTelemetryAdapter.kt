package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable
data class PhaseTwoObservation(val kind: String, val data: JsonObject)

@Serializable
data class PhaseTwoEngineStep(
    val sequence: Int,
    val action: JsonObject,
    val beforeStateSha256: String,
    val afterStateSha256: String?,
    val accepted: Boolean?,
    val error: String?,
    val engineEvents: JsonArray?,
    val failurePhase: String?,
    val observations: List<PhaseTwoObservation>,
)

@Serializable
data class PhaseTwoEngineTrace(
    val schema: String = "manual-transmission-phase2-engine-telemetry-v1",
    val engineSourceSha: String,
    val manualSeat: Int,
    val playerIds: List<String>,
    val initialState: JsonObject,
    val initialStateSha256: String,
    val initialObservations: List<PhaseTwoObservation>,
    val steps: List<PhaseTwoEngineStep>,
    val stopObservation: PhaseTwoObservation?,
    val executionAuthorizedByThisComponent: Boolean = false,
)

/**
 * Trusted offline evidence adapter, never a pilot view or gameplay authorization.
 *
 * It owns the ActionProcessor boundary so rejected actions cannot be presented as accepted ones.
 * Commander identity is bound once to the initializer's designated entities, not a later name or
 * copy. Personal turns come from PlayerTurnsTakenComponent, including extra turns and eliminated
 * seats. Raw states and engine events are private audit material and must not reach a pilot.
 *
 * This collects the mandatory action/elimination streams plus mulligans, commander casts, Animar
 * departures, clocks and terminal events. Strategic/causal metrics remain unavailable. A future
 * guarded runner must bind hardware, policies, source, allocation authority and durable attempts.
 */
class PhaseTwoTelemetryAdapter(
    initialState: GameState,
    private val processor: ActionProcessor,
    private val engineSourceSha: String,
    playerIds: List<EntityId>,
    private val manualSeat: Int,
) {
    private val playerIds = playerIds.toList()
    private val initialStateJson = encodeState(initialState)
    private val commanders: Map<EntityId, String>
    private val animarId: EntityId
    private val initialObservations: List<PhaseTwoObservation>
    private val steps = mutableListOf<PhaseTwoEngineStep>()
    private var stopObservation: PhaseTwoObservation? = null
    private var stopped = false
    private var sealed = false

    /** Trusted engine/replay state. A policy must receive a separately qualified masked view. */
    var state: GameState = initialState
        private set

    init {
        require(engineSourceSha.matches(Regex("[0-9a-f]{40}"))) { "Exact engine source SHA required" }
        require(playerIds.size == 4 && playerIds.distinct().size == 4 && manualSeat in 0..3)
        require(initialState.turnOrder.toSet() == playerIds.toSet()) { "Four-player roster mismatch" }
        require(!initialState.gameOver && initialState.turnNumber == 0) { "Fresh pre-mulligan state required" }
        commanders = playerIds.flatMap { player ->
            val registry = requireNotNull(initialState.getEntity(player)?.get<CommanderRegistryComponent>())
            require(registry.commanderIds.size in 1..2) { "Exact designated commanders required" }
            registry.commanderIds.map { id ->
                id to requireNotNull(initialState.getEntity(id)?.get<CardComponent>()).name
            }
        }.toMap()
        val manualCommanders = initialState.requireEntity(playerIds[manualSeat])
            .get<CommanderRegistryComponent>()!!.commanderIds
        animarId = manualCommanders.single { commanders[it] == "Animar, Soul of Elements" }
        initialObservations = listOf(window(initialState))
    }

    fun process(action: GameAction): ExecutionResult = processBoundary(action) { before, submitted ->
        processor.process(before, submitted).result
    }

    // The internal seam permits deterministic engine/extractor fault injection. Public execution
    // always calls the owned ActionProcessor above; a pilot cannot supply fabricated results.
    internal fun processBoundary(
        action: GameAction,
        transition: (GameState, GameAction) -> ExecutionResult,
    ): ExecutionResult {
        check(!sealed && !stopped) { "Trace already terminal or sealed" }
        // An exception must never leave this adapter reusable, even if serialization itself fails.
        stopped = true
        val before = state
        val beforeHash = digest(encodeState(before))
        val sequence = steps.size + 1
        val traceRef = "engine-step:$sequence"
        val actionJson = JSON.encodeToJsonElement(GameAction.serializer(), action).jsonObject
        var result: ExecutionResult? = null
        try {
            require(action.playerId in playerIds) { "Action actor absent from roster" }
            val actual = transition(before, action)
            result = actual
            val observations = collect(before, action, actual, actionJson, traceRef)
            steps += PhaseTwoEngineStep(sequence, actionJson, beforeHash, digest(encodeState(actual.state)),
                actual.error == null, actual.error, encodeEvents(actual.events), null, observations)
            state = actual.state
            stopped = actual.error != null || actual.state.gameOver
            return actual
        } catch (failure: Exception) {
            val actual = result
            val phase = if (actual == null) "ENGINE" else "COLLECTOR"
            val error = "${failure.javaClass.name}: ${failure.message}"
            val observed = mutableListOf<PhaseTwoObservation>()
            if (actual?.error == null && actual != null) observed += acceptedAction(action, actionJson, traceRef)
            observed += observation("INTEGRITY_FAILURE") {
                put("reason", "${phase}_EXCEPTION: $error")
                put("engine_trace_ref", traceRef)
            }
            steps += PhaseTwoEngineStep(sequence, actionJson, beforeHash,
                actual?.let { runCatching { digest(encodeState(it.state)) }.getOrNull() },
                actual?.let { it.error == null }, error,
                actual?.let { runCatching { encodeEvents(it.events) }.getOrNull() }, phase, observed)
            state = actual?.state ?: before
            throw failure
        }
    }

    private fun acceptedAction(action: GameAction, actionJson: JsonObject, traceRef: String) =
        observation("ACTION_ACCEPTED") {
            put("player_id", action.playerId.value)
            put("action", actionJson)
            put("engine_trace_ref", traceRef)
        }

    private fun collect(
        before: GameState, action: GameAction, result: ExecutionResult,
        actionJson: JsonObject, traceRef: String,
    ): List<PhaseTwoObservation> {
        val observations = mutableListOf<PhaseTwoObservation>()
        val accepted = result.error == null
        if (!accepted) {
            require(result.state == before && result.events.isEmpty()) { "Rejected engine action mutated state" }
            observations += observation("INTEGRITY_FAILURE") {
                put("reason", "ENGINE_REJECTED_ACTION: ${result.error}")
                put("engine_trace_ref", traceRef)
            }
        } else {
            observations += observation("ACTION_ACCEPTED") {
                put("player_id", action.playerId.value)
                put("action", actionJson)
                put("engine_trace_ref", traceRef)
            }
            if (action is TakeMulligan) {
                val old = requireNotNull(before.getEntity(action.playerId)?.get<MulliganStateComponent>())
                val next = requireNotNull(result.state.getEntity(action.playerId)?.get<MulliganStateComponent>())
                require(next.mulligansTaken == old.mulligansTaken + 1) { "Accepted mulligan count mismatch" }
                observations += observation("MULLIGAN_TAKEN") {
                    put("player_id", action.playerId.value)
                    put("free", old.freeMulligan && old.mulligansTaken == 0)
                }
            }
            var animarOnBattlefield = animarId in before.getBattlefield()
            for (event in result.events) {
                when (event) {
                    is SpellCastEvent -> commanders[event.spellEntityId]?.let { identity ->
                        observations += observation("COMMANDER_CAST") {
                            put("player_id", event.casterId.value)
                            put("commander_identity", identity)
                        }
                    }
                    is ZoneChangeEvent -> if (event.entityId == animarId) {
                        if (event.fromZone == Zone.BATTLEFIELD) {
                            observations += observation("ANIMAR_REMOVED") {
                                put("player_id", playerIds[manualSeat].value)
                                put("commander_identity", "Animar, Soul of Elements")
                                put("destination", event.toZone.name)
                            }
                        }
                        animarOnBattlefield = event.toZone == Zone.BATTLEFIELD
                    }
                    is PlayerLeftGameEvent -> if (
                        event.playerId == playerIds[manualSeat] && animarOnBattlefield && !result.state.hasEntity(animarId)
                    ) {
                        observations += observation("ANIMAR_REMOVED") {
                            put("player_id", playerIds[manualSeat].value)
                            put("commander_identity", "Animar, Soul of Elements")
                            put("destination", "LEFT_GAME")
                        }
                        animarOnBattlefield = false
                    }
                    is PlayerLostEvent -> observations += observation("PLAYER_ELIMINATED") {
                        put("player_id", event.playerId.value)
                        put("reason", event.reason.name)
                    }
                    else -> Unit
                }
            }
            observations += window(result.state)
            val ends = result.events.filterIsInstance<GameEndedEvent>()
            require(ends.size <= 1) { "Multiple terminal engine events" }
            require(result.state.gameOver == ends.isNotEmpty()) { "Terminal state/event mismatch" }
            ends.singleOrNull()?.let { event ->
                val winnerId = event.winnerId
                require(result.state.winnerId == winnerId) { "Terminal winner mismatch" }
                observations += when {
                    winnerId != null -> observation("GAME_WON") {
                        require(winnerId in playerIds)
                        put("winner", winnerId.value)
                        put("engine_trace_ref", traceRef)
                    }
                    event.reason == GameEndReason.DRAW -> observation("RULES_DRAW") {
                        put("rule_basis", "Engine GameEndedEvent.DRAW")
                        put("engine_trace_ref", traceRef)
                    }
                    else -> observation("INTEGRITY_FAILURE") {
                        // INFINITE_LOOP can originate at the engine's stabilization limit. It is
                        // not admitted as a rules draw without separately proving a mandatory loop.
                        put("reason", "UNQUALIFIED_ENGINE_DRAW_CLASSIFICATION: ${event.reason}")
                        put("engine_trace_ref", traceRef)
                    }
                }
            }
        }
        return observations
    }

    /** Resource limits never synthesize a winner, loss or rules draw. */
    fun stop(kind: String, reason: String) {
        check(!sealed && !stopped) { "Trace already terminal or sealed" }
        require(kind in setOf("RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE") && reason.isNotBlank())
        stopObservation = observation(kind) { put("reason", reason) }
        stopped = true
    }

    fun finish(): PhaseTwoEngineTrace {
        check(stopped && !sealed) { "A terminal or explicit resource/integrity boundary is required" }
        sealed = true
        return PhaseTwoEngineTrace(engineSourceSha = engineSourceSha, manualSeat = manualSeat,
            playerIds = playerIds.map { it.value }, initialState = initialStateJson,
            initialStateSha256 = digest(initialStateJson), initialObservations = initialObservations,
            steps = steps.toList(), stopObservation = stopObservation)
    }

    private fun window(snapshot: GameState): PhaseTwoObservation = observation("WINDOW") {
        put("turn", snapshot.turnNumber)
        put("personal_rounds", buildJsonObject {
            for (player in playerIds) {
                val turns = requireNotNull(snapshot.getEntity(player)?.get<PlayerTurnsTakenComponent>()) {
                    "Missing authoritative per-player turn counter"
                }.count
                require(turns >= 0)
                put(player.value, turns)
            }
        })
    }

    companion object {
        val JSON = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
        }

        private fun observation(kind: String, body: JsonObjectBuilder.() -> Unit) =
            PhaseTwoObservation(kind, buildJsonObject(body))

        private fun encodeEvents(events: List<GameEvent>) =
            JsonArray(events.map { JSON.encodeToJsonElement(GameEvent.serializer(), it) })

        private fun encodeState(state: GameState) = JSON.encodeToJsonElement(GameState.serializer(), state).jsonObject

        private fun digest(value: JsonObject): String = MessageDigest.getInstance("SHA-256")
            .digest(JSON.encodeToString(JsonObject.serializer(), value).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        /** Re-execute exact accepted and rejected action bytes; compare every derived observation. */
        fun replay(trace: PhaseTwoEngineTrace, processor: ActionProcessor): PhaseTwoEngineTrace {
            require(trace.schema == "manual-transmission-phase2-engine-telemetry-v1")
            require(!trace.executionAuthorizedByThisComponent)
            require(trace.steps.none { it.failurePhase != null }) {
                "Engine/collector exception evidence is retained as invalid and cannot certify gameplay replay"
            }
            require(digest(trace.initialState) == trace.initialStateSha256) { "Initial state digest mismatch" }
            val initial = JSON.decodeFromJsonElement(GameState.serializer(), trace.initialState)
            val replay = PhaseTwoTelemetryAdapter(initial, processor, trace.engineSourceSha,
                trace.playerIds.map(::EntityId), trace.manualSeat)
            require(replay.initialObservations == trace.initialObservations) { "Initial collector mismatch" }
            trace.steps.forEachIndexed { index, expected ->
                require(expected.sequence == index + 1) { "Skipped or reordered trace step" }
                replay.process(JSON.decodeFromJsonElement(GameAction.serializer(), expected.action))
                require(replay.steps.last() == expected) { "Engine replay or typed extraction mismatch at ${index + 1}" }
            }
            trace.stopObservation?.let { observation ->
                require(observation.data.keys == setOf("reason"))
                replay.stop(observation.kind, observation.data.getValue("reason").jsonPrimitive.content)
            }
            return replay.finish().also { require(it == trace) { "Final replay trace mismatch" } }
        }
    }
}
