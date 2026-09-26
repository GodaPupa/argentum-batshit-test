package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.LibraryOrderingComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.model.Deck
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonNull
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Exact per-allocation initialization input; deck families never enter the public pilot. */
@Serializable
internal data class IndustrialWasteV2AllocationInput(
    val allocationId: String,
    val deckCards: List<String>,
    val namespace: String,
    val row: Int,
    val openingOrders: List<List<String>>,
    val startingPlayer: Int,
    val initializerSeed: Long,
)

/** Exact wire row for every eligible quiet state, including later states on the same own turn. */
@Serializable
internal data class IndustrialWasteV2QuietCheckpointObservation(
    val acceptedActions: Int,
    val stateSha256: String,
    val checkpoint: IndustrialWasteV2CheckpointMana,
)

/** Replay certifies the recorded transitions and observations, never gameplay admission or validity. */
@Serializable
internal data class IndustrialWasteV2AllocationReplay(
    val status: String = "EXACT_ACTION_EVENT_STATE_REPLAY",
    val actions: Int,
    val checkpointReplay: String = "EXACT_EVERY_QUIET_CHECKPOINT_REPLAY",
    val quietCheckpointObservations: Int,
)

/**
 * Durable raw output for one allocation. CREATE_NEW refuses overwrite/retry. Action intent is
 * forced to storage before submission; exact returned events and state are forced after it. A
 * partially written stream remains an invalid preserved attempt, never a retry authority.
 */
internal class IndustrialWasteV2AllocationTrace(private val directory: Path) : AutoCloseable {
    private val channel: FileChannel
    private val paymentChannel: FileChannel
    private val checkpointChannel: FileChannel
    private var index = 0

    init {
        Files.createDirectory(directory)
        forceDirectory(directory.parent)
        channel = FileChannel.open(directory.resolve("transitions.jsonl"),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        paymentChannel = FileChannel.open(directory.resolve("payment-intents.jsonl"),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        checkpointChannel = FileChannel.open(directory.resolve("quiet-checkpoint-observations.jsonl"),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        forceDirectory(directory)
    }

    fun write(name: String, bytes: String) {
        FileChannel.open(directory.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { file ->
            val buffer = ByteBuffer.wrap(bytes.toByteArray(Charsets.UTF_8))
            while (buffer.hasRemaining()) file.write(buffer)
            file.force(true)
            forceDirectory(directory)
        }
    }

    private fun forceDirectory(path: Path) {
        FileChannel.open(path, StandardOpenOption.READ).use { it.force(true) }
    }

    private fun append(line: String) {
        val buffer = ByteBuffer.wrap((line + "\n").toByteArray(Charsets.UTF_8))
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }

    fun before(action: GameAction) {
        index++
        append("{\"type\":\"ACTION_DECLARED\",\"index\":$index,\"action\":" +
            CODEC.encodeToString(GameAction.serializer(), action) + "}")
    }

    fun after(events: List<GameEvent>, state: GameState, error: String?) {
        val eventBytes = CODEC.encodeToString(ListSerializer(GameEvent.serializer()), events)
        val stateBytes = CODEC.encodeToString(GameState.serializer(), state)
        append("{\"type\":\"ACTION_RETURNED\",\"index\":$index,\"events\":$eventBytes," +
            "\"stateSha256\":\"${sha256(stateBytes)}\",\"error\":" +
            CODEC.encodeToString(kotlinx.serialization.serializer<String?>(), error) + "}")
    }

    fun payment(record: IndustrialWasteV2PaymentIntentRecord) {
        val bytes = CODEC.encodeToString(IndustrialWasteV2PaymentIntentRecord.serializer(), record) + "\n"
        val buffer = ByteBuffer.wrap(bytes.toByteArray(Charsets.UTF_8))
        while (buffer.hasRemaining()) paymentChannel.write(buffer)
        paymentChannel.force(true)
    }

    fun checkpoint(checkpoint: IndustrialWasteV2CheckpointMana, acceptedActions: Int, state: GameState) {
        check(acceptedActions == index) { "Quiet observation/action journal index mismatch" }
        val stateBytes = CODEC.encodeToString(GameState.serializer(), state)
        val bytes = CODEC.encodeToString(IndustrialWasteV2QuietCheckpointObservation.serializer(),
            IndustrialWasteV2QuietCheckpointObservation(acceptedActions, sha256(stateBytes), checkpoint)) + "\n"
        val buffer = ByteBuffer.wrap(bytes.toByteArray(Charsets.UTF_8))
        while (buffer.hasRemaining()) checkpointChannel.write(buffer)
        checkpointChannel.force(true)
    }

    override fun close() {
        checkpointChannel.close()
        paymentChannel.close()
        channel.close()
    }

    companion object {
        val CODEC = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
            encodeDefaults = true
        }
        fun sha256(bytes: String): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/**
 * One initializer/full-horizon composition for excluded qualification and admitted official input.
 * Only the independent guard can construct an official admission. Serializing a request or receipt
 * alone is not permission to initialize a corpus member.
 */
internal object IndustrialWasteV2AllocationRunner {
    fun runExcluded(input: IndustrialWasteV2AllocationInput, directory: Path): IndustrialWasteV2FullHorizonRunner.Result {
        require(input.namespace.startsWith("IW_V2_R1_SYNTHETIC_")) {
            "Only an explicitly excluded capability namespace is admitted"
        }
        require(input.namespace != "IW_V2_R1_ORDERINGS_2026_09_25")
        require(input.allocationId.startsWith("SYNTHETIC_")) { "An official allocation id is not admitted" }
        return runPrepared(input, directory, null)
    }

    fun runOfficial(admission: IndustrialWasteV2OfficialAdmission, directory: Path): IndustrialWasteV2FullHorizonRunner.Result =
        runPrepared(admission.input, directory, admission)

    private fun runPrepared(input: IndustrialWasteV2AllocationInput, directory: Path,
        admission: IndustrialWasteV2OfficialAdmission?): IndustrialWasteV2FullHorizonRunner.Result {
        require(input.row > 0 && input.startingPlayer in 0..1)
        require(input.deckCards.size == 60)
        require(input.openingOrders.size == 4)
        val counts = mutableMapOf<String, Int>()
        val labels = input.deckCards.map { name -> "$name#${counts.merge(name, 1, Int::plus)}" }.toSet()
        require(input.openingOrders.all { it.size == 60 && it.toSet() == labels })

        val codec = IndustrialWasteV2AllocationTrace.CODEC
        IndustrialWasteV2AllocationTrace(directory).use { trace ->
            trace.write("input.json", codec.encodeToString(IndustrialWasteV2AllocationInput.serializer(), input))
            // This durable declaration is written before any real initializer call.
            trace.write("attempt-before-initialization.json",
                "{\"allocationId\":\"${input.allocationId}\",\"initialized\":false,\"official\":${admission != null}}")
            try {
                val driver = GameTestDriver().apply {
                    MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
                    registerCards(PredefinedTokens.allTokens)
                    initGame(
                        deck1 = Deck(input.deckCards), deck2 = Deck.of("Forest" to 60),
                        skipMulligans = false, startingLife = 20, startingPlayer = input.startingPlayer,
                        seed = input.initializerSeed,
                        libraryOrdering1 = LibraryOrderingPlan(input.namespace, input.row, input.openingOrders),
                    )
                }
                trace.write("initial-state.json", codec.encodeToString(GameState.serializer(), driver.state))
                trace.write("initial-events.json", codec.encodeToString(ListSerializer(GameEvent.serializer()), driver.events.toList()))
                val result = IndustrialWasteV2FullHorizonRunner(
                    driver, driver.player1, driver.player2,
                    beforeSubmission = trace::before,
                    afterSubmission = { returned -> trace.after(returned.events, returned.newState, returned.error) },
                    paymentIntentRecord = trace::payment,
                    checkpointObservation = trace::checkpoint,
                    officialAdmission = admission,
                ).run()
                trace.write("execution-status.json", codec.encodeToString(IndustrialWasteV2ExecutionStatus.serializer(), result.status))
                trace.write("event-metrics.json", codec.encodeToString(IndustrialWasteV2EventMetrics.serializer(), result.eventMetrics))
                trace.write("checkpoints.json", codec.encodeToString(ListSerializer(IndustrialWasteV2CheckpointMana.serializer()), result.checkpoints))
                trace.write("actions.json", codec.encodeToString(ListSerializer(GameAction.serializer()), result.actions))
                trace.write("final-state.json", codec.encodeToString(GameState.serializer(), driver.state))
                if (result.status.status in setOf(IndustrialWasteV2StopStatus.EXCEPTION,
                        IndustrialWasteV2StopStatus.REJECTED_ACTION, IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY)) {
                    trace.write("failure.txt", result.status.diagnostic ?: "Unresolved real-engine attempt")
                } else {
                    val replay = verifyReplay(directory)
                    trace.write("replay.json", codec.encodeToString(IndustrialWasteV2AllocationReplay.serializer(), replay))
                }
                return result
            } catch (failure: Throwable) {
                trace.write("failure.txt", "${failure::class.qualifiedName}: ${failure.message}\n${failure.stackTraceToString()}")
                throw failure
            }
        }
    }

    /** Restore the recorded initialization snapshot, never initialize or sample another game. */
    internal fun verifyReplay(directory: Path): IndustrialWasteV2AllocationReplay {
        val codec = IndustrialWasteV2AllocationTrace.CODEC
        val input = codec.decodeFromString(IndustrialWasteV2AllocationInput.serializer(),
            Files.readString(directory.resolve("input.json")))
        val replay = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
            registerCards(PredefinedTokens.allTokens)
            replaceState(codec.decodeFromString(GameState.serializer(), Files.readString(directory.resolve("initial-state.json"))))
        }
        // The snapshot carries the measured seat's real ordering contract. A replay driver was
        // deliberately never initialized, so its convenience player1/player2 fields are unset.
        val measuredPlayer = replay.state.turnOrder.single { player ->
            replay.state.getEntity(player)?.get<LibraryOrderingComponent>()?.plan?.let {
                it.namespace == input.namespace && it.row == input.row && it.openingOrders == input.openingOrders
            } == true
        }
        val originalCopies = requireNotNull(replay.state.getEntity(measuredPlayer)
            ?.get<LibraryOrderingComponent>()).originalCopies
        val simulator = GameSimulator(replay.cardRegistry)
        val observations = Files.readAllLines(directory.resolve("quiet-checkpoint-observations.jsonl"))
            .map { codec.decodeFromString(IndustrialWasteV2QuietCheckpointObservation.serializer(), it) }
        check(observations.all { it.acceptedActions >= 0 }) { "Negative quiet observation index" }
        check(observations.zipWithNext().all { (left, right) -> left.acceptedActions < right.acceptedActions }) {
            "Quiet observations must retain their complete strict action order"
        }
        var observationIndex = 0
        val firstPerTurn = linkedMapOf<Int, IndustrialWasteV2CheckpointMana>()
        fun verifyQuietObservation(state: GameState, acceptedActions: Int) {
            if (!state.isIndustrialWasteV2QuietCheckpoint(measuredPlayer)) return
            val recorded = observations.getOrNull(observationIndex++)
                ?: error("Missing eligible quiet observation after action $acceptedActions")
            check(recorded.acceptedActions == acceptedActions) { "Quiet observation index does not match actual eligible state" }
            check(recorded.stateSha256 == IndustrialWasteV2AllocationTrace.sha256(
                codec.encodeToString(GameState.serializer(), state))) { "Quiet observation state digest mismatch" }
            val actual = IndustrialWasteV2CheckpointManaClassifier.classify(
                state = state, player = measuredPlayer, originalCopies = originalCopies,
                legalActions = simulator.getLegalActions(state, measuredPlayer), cardRegistry = replay.cardRegistry,
            )
            check(recorded.checkpoint == actual) { "Quiet checkpoint values differ from exact semantic replay" }
            firstPerTurn.putIfAbsent(actual.ownTurn, actual)
        }
        verifyQuietObservation(replay.state, 0)
        val actions = codec.decodeFromString(ListSerializer(GameAction.serializer()), Files.readString(directory.resolve("actions.json")))
        val transitions = Files.readAllLines(directory.resolve("transitions.jsonl")).map { codec.parseToJsonElement(it).jsonObject }
        check(transitions.size == actions.size * 2) { "Incomplete submitted/returned action journal" }
        actions.forEachIndexed { index, action ->
            val declared = transitions[index * 2]
            check(declared.getValue("type").jsonPrimitive.content == "ACTION_DECLARED")
            check(declared.getValue("index").jsonPrimitive.int == index + 1)
            check(declared.getValue("action") == codec.parseToJsonElement(codec.encodeToString(GameAction.serializer(), action)))
            val returned = replay.submit(action)
            check(returned.error == null) { "Captured replay action was rejected" }
            val recorded = transitions[index * 2 + 1]
            check(recorded.getValue("type").jsonPrimitive.content == "ACTION_RETURNED")
            check(recorded.getValue("index").jsonPrimitive.int == index + 1)
            check(recorded.getValue("error") == JsonNull) { "An accepted replay cannot carry a recorded error" }
            check(recorded.getValue("stateSha256").jsonPrimitive.content ==
                IndustrialWasteV2AllocationTrace.sha256(codec.encodeToString(GameState.serializer(), returned.newState)))
            check(recorded.getValue("events") == codec.parseToJsonElement(
                codec.encodeToString(ListSerializer(GameEvent.serializer()), returned.events)))
            verifyQuietObservation(returned.newState, index + 1)
        }
        check(codec.encodeToString(GameState.serializer(), replay.state) == Files.readString(directory.resolve("final-state.json")))
        check(observationIndex == observations.size) { "Extra quiet observations without an eligible accepted state" }
        val compatibility = codec.decodeFromString(ListSerializer(IndustrialWasteV2CheckpointMana.serializer()),
            Files.readString(directory.resolve("checkpoints.json")))
        check(compatibility == firstPerTurn.values.toList()) { "Legacy checkpoint view is not the first observation per own turn" }
        return IndustrialWasteV2AllocationReplay(actions = actions.size, quietCheckpointObservations = observationIndex)
    }
}
