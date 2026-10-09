package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

@Serializable
internal data class IzzetInitializationPlayer(
    val name: String, val playerId: String, val cards: List<String>, val deckSha256: String,
)

/** Complete input for ONE profile: two 60-basic-card Standard seats, seven cards, pre-mulligan,
 * no smoothing/teams/commanders/printing pins, 20 life, explicit player IDs/start index/seed.
 * Source/runtime/policy/deck labels are fixture declarations, not authenticated provenance.
 */
@Serializable
internal data class IzzetInitializationSpecification(
    val schema: String = "IZZET_EXCLUDED_BASIC_INITIALIZATION_V1",
    val executionAuthorized: Boolean = false,
    val authenticatedProvenance: Boolean = false,
    val fixtureDeclarationsOnly: Boolean = true,
    val identityJson: String,
    val seed: Long,
    val startingPlayerIndex: Int,
    val players: List<IzzetInitializationPlayer>,
)

/** Additive synthetic initialization composition. No action, journal, collector or pilot call.
 * A separate spec namespace preserves the accepted attempt file layout. Existing slots never
 * reopen, even on failure. Local force calls do not establish power-loss or nonrollback custody.
 */
internal object IzzetBoundInitialization {
    private val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    private val specNames = setOf("identity.json", "specification.json", "specification.sha256", "specification.complete")
    private val initialNames = setOf("identity.json", "initialization-intent", "initial.bin", "initialized.sha256")
    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun deckPin(cards: List<String>): String = sha256(JsonArray(cards.map(::JsonPrimitive)).toString().toByteArray(Charsets.UTF_8))
    fun encode(s: IzzetInitializationSpecification): ByteArray =
        json.encodeToString(IzzetInitializationSpecification.serializer(), s).toByteArray(Charsets.UTF_8)
    private fun stateBytes(s: GameState) = json.encodeToString(GameState.serializer(), s).toByteArray(Charsets.UTF_8)

    /** Returns only the original initial-state digest for independent retention, never live state. */
    fun initializeOnce(root: Path, specRoot: Path, identity: IzzetSyntheticAttemptIdentity,
                       specification: ByteArray, specPin: String, registry: CardRegistry): String {
        val bytes = specification.copyOf()
        val spec = validate(bytes, specPin, identity)
        for (r in listOf(root, specRoot)) {
            require(r.isAbsolute && r.normalize() == r && r.toRealPath() == r && Files.isDirectory(r, NOFOLLOW_LINKS))
        }
        require(!root.startsWith(specRoot) && !specRoot.startsWith(root))
        val sd = specRoot.resolve(identity.attemptId)
        Files.createDirectory(sd)
        try {
            force(specRoot)
            val initial = IzzetSyntheticAttempt.initializeOnce(root, identity, {
                // Accepted barrier has already forced attempt identity and initialization intent.
                // Force the full canonical specification before invoking the real initializer.
                write(sd.resolve("identity.json"), identity.bytes())
                write(sd.resolve("specification.json"), bytes)
                write(sd.resolve("specification.sha256"), (specPin + "\n").toByteArray())
                write(sd.resolve("specification.complete"), (specPin + "\n").toByteArray())
                initialize(spec, registry)
            }, ::stateBytes)
            val pin = sha256(stateBytes(initial))
            verify(sd, root.resolve(identity.attemptId), identity, specPin, pin, registry)
            return pin
        } catch (failure: Throwable) {
            try { write(sd.resolve("fault"), (failure.javaClass.name + "\n").toByteArray()) }
            catch (recording: Throwable) { failure.addSuppressed(recording) }
            throw failure
        }
    }

    /** Read-only deterministic reconstruction, not snapshot restoration. No state is returned. */
    fun verify(specDirectory: Path, initialDirectory: Path, identity: IzzetSyntheticAttemptIdentity,
               specPin: String, initialPin: String, registry: CardRegistry) {
        require(specDirectory != initialDirectory)
        require(specDirectory.fileName.toString() == identity.attemptId && initialDirectory.fileName.toString() == identity.attemptId)
        val originalSpec = snapshot(specDirectory, specNames)
        val originalState = snapshot(initialDirectory, initialNames)
        fun b(m: Map<String, List<Byte>>, key: String) = m.getValue(key).toByteArray()
        try {
            require(b(originalSpec, "identity.json").contentEquals(identity.bytes()))
            require(b(originalState, "identity.json").contentEquals(identity.bytes()))
            val spec = validate(b(originalSpec, "specification.json"), specPin, identity)
            for (name in listOf("specification.sha256", "specification.complete"))
                require(b(originalSpec, name).contentEquals((specPin + "\n").toByteArray()))
            require(b(originalState, "initialization-intent").contentEquals("INITIALIZE_ONCE\n".toByteArray()))
            require(initialPin.matches(Regex("[0-9a-f]{64}")) && sha256(b(originalState, "initial.bin")) == initialPin)
            require(b(originalState, "initialized.sha256").contentEquals((initialPin + "\n").toByteArray()))
            // No decoding/copying of initial.bin to obtain the replay state: only spec + registry.
            val reconstructed = stateBytes(initialize(spec, registry))
            require(reconstructed.contentEquals(b(originalState, "initial.bin"))) { "Initialization reconstruction differs" }
        } finally {
            require(snapshot(specDirectory, specNames) == originalSpec && snapshot(initialDirectory, initialNames) == originalState) {
                "Original initialization evidence changed during verification"
            }
        }
    }

    private fun validate(bytes: ByteArray, pin: String, identity: IzzetSyntheticAttemptIdentity): IzzetInitializationSpecification {
        require(bytes.size in 1..(1024 * 1024) && pin.matches(Regex("[0-9a-f]{64}")) && sha256(bytes) == pin)
        val s = json.decodeFromString(IzzetInitializationSpecification.serializer(), bytes.toString(Charsets.UTF_8))
        require(encode(s).contentEquals(bytes)) { "Noncanonical specification" }
        require(s.schema == "IZZET_EXCLUDED_BASIC_INITIALIZATION_V1" && !s.executionAuthorized &&
            !s.authenticatedProvenance && s.fixtureDeclarationsOnly)
        require(s.identityJson.toByteArray(Charsets.UTF_8).contentEquals(identity.bytes()))
        require(s.players.size == 2 && s.startingPlayerIndex in 0..1 && s.players.map { it.playerId }.distinct().size == 2)
        s.players.forEach {
            require(it.name.isNotBlank() && it.playerId.isNotBlank() && it.cards.size == 60)
            require(it.cards.all { name -> name == "Forest" || name == "Island" }) { "Outside excluded basic profile" }
            require(it.deckSha256 == deckPin(it.cards))
        }
        require(s.players.first().deckSha256 == identity.pins.deckSha256)
        return s
    }
    private fun initialize(s: IzzetInitializationSpecification, registry: CardRegistry): GameState {
        val result = GameInitializer(registry).initializeGame(GameConfig(
            players = s.players.map { PlayerConfig(it.name, Deck(it.cards), startingLife = 20, playerId = EntityId(it.playerId)) },
            format = Format.Standard, startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
            startingPlayerIndex = s.startingPlayerIndex, seed = s.seed))
        require(result.seed == s.seed && result.playerIds.map { it.value } == s.players.map { it.playerId })
        return result.state
    }
    private fun snapshot(directory: Path, names: Set<String>): Map<String, List<Byte>> {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        require(Files.list(directory).use { it.map { p -> p.fileName.toString() }.toList().toSet() } == names)
        return names.associateWith { name ->
            val p = directory.resolve(name)
            require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024))
            Files.readAllBytes(p).toList()
        }
    }
    private fun force(directory: Path) { FileChannel.open(directory, READ).use { it.force(true) } }
    private fun write(p: Path, b: ByteArray) {
        FileChannel.open(p, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use {
            val buffer = ByteBuffer.wrap(b)
            while (buffer.hasRemaining()) it.write(buffer)
            it.force(true)
        }
        force(p.parent)
    }
}
