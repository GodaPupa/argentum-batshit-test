package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest

/** Exact, independently pinned SYNTHETIC initialization input. No entropy or seed allocation. */
@Serializable
internal data class ManualReplayPlayer(
    val name: String, val playerId: String, val cards: List<String>, val commanders: List<String>,
)

@Serializable
internal data class ManualInitializationSpec(
    val schema: String = "MANUAL_SYNTHETIC_INITIALIZATION_V1",
    val fixtureIdentitySha256: String,
    val seed: Long,
    val startingPlayerIndex: Int,
    val manualSeat: Int,
    val players: List<ManualReplayPlayer>,
)

/** This result asserts initial-state equality only; transition contents have NOT been replayed. */
internal data class ManualInitializationCheck(
    val initialStateSha256: String,
    val storedTransitionCount: Int,
    val interruptedIntent: Boolean,
    val rawStopClassification: String?,
)

/**
 * Trusted read-only verifier over already recorded synthetic journal material. The expected
 * specification hash must come from independent source-bound evidence, not from the same payload.
 * Deck/policy/runtime claims in the fixture identity still require external authentication.
 * Fixed profile: four-seat Commander, seven cards, pre-mulligan, no smoothing/teams/printing pins.
 * No journal writer, action processor, pilot, official claim or retry path is exposed here.
 */
internal object ManualInitializationReplay {
    private val JSON = Json { encodeDefaults = true; explicitNulls = true }
    fun encode(spec: ManualInitializationSpec): ByteArray =
        JSON.encodeToString(ManualInitializationSpec.serializer(), spec).toByteArray(Charsets.UTF_8)
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun verify(journalDirectory: Path, specification: ByteArray, expectedSpecificationSha256: String,
               expectedIdentity: ManualFixtureIdentity, registry: CardRegistry): ManualInitializationCheck {
        val bytes = specification.copyOf()
        val identity = expectedIdentity.bytes()
        require(bytes.size in 1..(1024 * 1024))
        require(expectedSpecificationSha256.matches(Regex("[0-9a-f]{64}")))
        require(sha256(bytes) == expectedSpecificationSha256) { "Initialization specification drift" }
        val spec = JSON.decodeFromString(ManualInitializationSpec.serializer(), bytes.toString(Charsets.UTF_8))
        require(encode(spec).contentEquals(bytes)) { "Noncanonical initialization specification" }
        require(spec.schema == "MANUAL_SYNTHETIC_INITIALIZATION_V1")
        require(spec.fixtureIdentitySha256 == sha256(identity)) { "Fixture identity drift" }
        require(spec.players.size == 4 && spec.manualSeat in 0..3 && spec.startingPlayerIndex in 0..3)
        require(spec.players.map { it.playerId }.distinct().size == 4)
        spec.players.forEach {
            require(it.name.isNotBlank() && it.playerId.isNotBlank())
            require(it.commanders.size in 1..2 && it.commanders.distinct().size == it.commanders.size)
            require(it.cards.size + it.commanders.size == 100)
            require((it.cards + it.commanders).all(String::isNotBlank))
        }
        val replayed = GameInitializer(registry).initializeGame(GameConfig(
            players = spec.players.map { PlayerConfig(it.name, Deck(it.cards), playerId = EntityId(it.playerId),
                commanderCardNames = it.commanders) },
            format = Format.Commander(), startingHandSize = 7, skipMulligans = false,
            useHandSmoother = false, startingPlayerIndex = spec.startingPlayerIndex, seed = spec.seed,
        ))
        require(replayed.seed == spec.seed && replayed.playerIds.map { it.value } == spec.players.map { it.playerId })
        // The existing telemetry constructor verifies fresh setup and the designated Manual seat.
        val adapter = PhaseTwoTelemetryAdapter(replayed.state, ActionProcessor(registry),
            expectedIdentity.sourceCommit, replayed.playerIds, spec.manualSeat)
        val initial = adapter.journalInitialEnvelope()
        val stored = ManualTransitionJournal.inspect(journalDirectory, identity, initial)
        return ManualInitializationCheck(sha256(initial), stored.transitions.size,
            stored.interruptedIntent, stored.rawStopClassification)
    }
}
