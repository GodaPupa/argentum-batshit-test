package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.Closeable
import java.nio.file.Path
import java.security.MessageDigest

/** Complete trusted ENGINE envelope, not a masked pilot view or an outcome certificate. */
@Serializable
internal data class SphinxEngineFixtureEnvelope(
    val schema: String = "sphinx-engine-fixture-envelope-v1",
    val sourceCommit: String,
    val initialSeed: Long,
    val playerIds: List<EntityId>,
    val submissions: Int,
    // Includes full GameState (and its RNG), ordered events, rejection/pause and trigger flags.
    val result: ExecutionResult,
)

/** Actual engine codec for the existing fixture replay reader. No initializer is used in replay. */
internal class SphinxEngineFixtureCodec(
    private val registry: CardRegistry,
    private val expectedSource: String,
) : SphinxFixtureReplayCodec<SphinxEngineFixtureEnvelope> {
    init { require(expectedSource.matches(Regex("[0-9a-f]{40}"))) }
    private val processor = ActionProcessor(registry)

    override fun restoreRecorded(envelope: String): SphinxEngineFixtureEnvelope =
        JSON.decodeFromString(SphinxEngineFixtureEnvelope.serializer(), envelope).also {
            validate(it)
            require(canonicalEnvelope(it) == envelope) { "Noncanonical or incomplete engine envelope" }
        }

    override fun canonicalEnvelope(state: SphinxEngineFixtureEnvelope): String {
        validate(state)
        return canonical(JSON.encodeToJsonElement(SphinxEngineFixtureEnvelope.serializer(), state))
    }

    override fun replayRecordedAction(state: SphinxEngineFixtureEnvelope, action: String): SphinxEngineFixtureEnvelope {
        validate(state)
        check(state.result.error == null && !state.result.state.gameOver) { "Stopped engine cannot submit again" }
        val decoded = JSON.decodeFromString(GameAction.serializer(), action)
        require(canonicalAction(decoded) == action) { "Noncanonical action" }
        require(decoded.playerId in state.playerIds) { "Action actor outside initialized roster" }
        check(state.submissions < Int.MAX_VALUE)
        val actual = processor.process(state.result.state, decoded).result
        // Rejected results remain fully recorded, not changed into successful transitions.
        return state.copy(submissions = state.submissions + 1, result = actual)
    }

    fun canonicalAction(action: GameAction): String = canonical(JSON.encodeToJsonElement(GameAction.serializer(), action))

    private fun validate(value: SphinxEngineFixtureEnvelope) {
        require(value.schema == "sphinx-engine-fixture-envelope-v1" && value.sourceCommit == expectedSource)
        require(value.submissions >= 0 && value.playerIds.size == 2 && value.playerIds.distinct().size == 2)
        require(value.result.state.turnOrder.all { it in value.playerIds })
    }

    companion object {
        private val JSON = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
            ignoreUnknownKeys = false
        }
        private fun ordered(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.toSortedMap().mapValues { ordered(it.value) })
            is JsonArray -> JsonArray(value.map(::ordered))
            else -> value
        }
        private fun canonical(value: JsonElement): String = ordered(value).toString()

        /** Pins the full submitted fixture Deck representation, including its order and sideboard. */
        fun fixtureDeckSha256(deck: Deck): String = MessageDigest.getInstance("SHA-256")
            .digest(canonical(JSON.encodeToJsonElement(Deck.serializer(), deck)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

/**
 * Trusted two-seat fixture owner. Uses the real GameInitializer/ActionProcessor behind the
 * already-reviewed write-ahead collector; pilots must never receive this object or its state.
 * No main/CLI, official namespace or automatic loop is provided. Source/runtime/pilot admission
 * and complete actor coverage remain separate. These are fixture Deck hashes, not Stage-E CSV pins.
 */
internal class SphinxStageEEngineFixture private constructor(
    private val journal: SphinxStageEWriteAheadJournal,
    private val codec: SphinxEngineFixtureCodec,
    private var current: SphinxEngineFixtureEnvelope,
) : Closeable {
    private var consumed = false

    @Synchronized
    fun submitTrusted(action: GameAction): SphinxEngineFixtureEnvelope {
        check(!consumed)
        try {
            val raw = codec.canonicalAction(action)
            var next: SphinxEngineFixtureEnvelope? = null
            journal.submit(raw) {
                codec.replayRecordedAction(current, raw).also { next = it }.let(codec::canonicalEnvelope)
            }
            current = requireNotNull(next)
            if (current.result.error != null) {
                journal.finish("FAULT")
                consumed = true
            } else if (current.result.state.gameOver) {
                journal.finish("ENGINE_TERMINAL")
                consumed = true
            }
            return current
        } catch (failure: Throwable) {
            consumed = true
            throw failure
        }
    }

    @Synchronized
    fun stopFixture(reason: String) {
        check(!consumed)
        require(reason in setOf("CAPABILITY_STOP", "RESOURCE_STOP"))
        consumed = true
        journal.finish(reason)
    }

    @Synchronized
    override fun close() { consumed = true; journal.close() }

    companion object {
        fun create(
            root: Path,
            identity: SphinxRunnerFixtureIdentity,
            registry: CardRegistry,
            config: GameConfig,
        ): SphinxStageEEngineFixture {
            val seed = requireNotNull(config.seed) { "Explicit prospective fixture seed required; never draw fresh entropy" }
            val startingSeat = requireNotNull(config.startingPlayerIndex) { "Explicit starting seat required" }
            require(config.players.size == 2 && startingSeat in 0..1)
            require(!config.useHandSmoother)
            require(SphinxEngineFixtureCodec.fixtureDeckSha256(config.players[0].deck) == identity.ownDeckSha256)
            require(SphinxEngineFixtureCodec.fixtureDeckSha256(config.players[1].deck) == identity.opponentDeckSha256)
            val codec = SphinxEngineFixtureCodec(registry, identity.sourceCommit)
            val journal = SphinxStageEWriteAheadJournal.createFixture(root, identity)
            try {
                var envelope: SphinxEngineFixtureEnvelope? = null
                journal.initialize {
                    val initialized = GameInitializer(registry).initializeGame(config)
                    require(initialized.seed == seed && initialized.playerIds.distinct().size == 2)
                    val value = SphinxEngineFixtureEnvelope(sourceCommit = identity.sourceCommit,
                        initialSeed = seed, playerIds = initialized.playerIds.toList(), submissions = 0,
                        result = ExecutionResult(initialized.state, initialized.events.toList()))
                    envelope = value
                    codec.canonicalEnvelope(value)
                }
                return SphinxStageEEngineFixture(journal, codec, requireNotNull(envelope))
            } catch (failure: Throwable) {
                try { journal.close() } catch (closing: Throwable) { failure.addSuppressed(closing) }
                throw failure
            }
        }
    }
}
