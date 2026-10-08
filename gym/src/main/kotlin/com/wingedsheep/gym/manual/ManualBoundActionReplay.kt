package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/**
 * Read-only synthetic evidence verifier. The original spec pin and completed trace pin must be
 * independently retained by the trusted caller. Neither pins nor registry authenticate provenance.
 * Composes accepted initialization reconstruction and telemetry with the existing durable journal;
 * never resumes an unresolved intent, writes a receipt, invokes a policy, or opens a writer.
 */
internal object ManualBoundActionReplay {
    private val json = PhaseTwoTelemetryAdapter.JSON
    private val rootNames = setOf("fixture-identity.txt", "initialization-intent.txt", "initialized.txt",
        "run-intent.txt", "result.bin", "complete.txt", "initialization-spec.json", "initialization-spec.sha256", "transitions")

    fun verify(directory: Path, expected: ManualFixtureIdentity, specPin: String, tracePin: String,
               registry: CardRegistry): PhaseTwoEngineTrace {
        val identity = expected.copy(deckSha256 = expected.deckSha256.toList(), pilotSha256 = expected.pilotSha256.toList())
        require(tracePin.matches(Regex("[0-9a-f]{64}")))
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(directory.fileName.toString() == identity.fixtureId)
        fun snapshot(): Map<String, List<Byte>> {
            require(directory.toRealPath() == directory)
            val names = Files.list(directory).use { it.map { p -> p.fileName.toString() }.toList().toSet() }
            require(names == rootNames) { "Incomplete or unexpected fixture evidence" }
            val journal = directory.resolve("transitions")
            require(Files.isDirectory(journal, NOFOLLOW_LINKS) && journal.toRealPath() == journal)
            val paths = names.filter { it != "transitions" }.map(directory::resolve) +
                Files.list(journal).use { it.toList() }
            return paths.associate { p ->
                require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024 + 100))
                directory.relativize(p).toString() to Files.readAllBytes(p).toList()
            }
        }
        val original = snapshot()
        fun bytes(name: String) = original.getValue(name).toByteArray()
        try {
            require(bytes("fixture-identity.txt").contentEquals(identity.bytes()))
            for ((name, value) in mapOf("initialization-intent.txt" to "INITIALIZE_ONCE\n",
                "initialized.txt" to "TRUSTED_FACTORY_RETURNED\n", "run-intent.txt" to "RUN_ONCE\n"))
                require(bytes(name).contentEquals(value.toByteArray()))
            val raw = bytes("result.bin")
            require(ManualInitializationReplay.sha256(raw) == tracePin) { "Independent trace pin mismatch" }
            require(bytes("complete.txt").contentEquals((tracePin + "\n").toByteArray()))
            val init = ManualBoundInitialization.verify(directory, identity, specPin, registry)
            require(!init.interruptedIntent && init.rawStopClassification != null) { "Unresolved or unstopped journal" }
            val trace = json.decodeFromString(PhaseTwoEngineTrace.serializer(), raw.toString(Charsets.UTF_8))
            require(ManualPhaseTwoStoredFixtureReplay.encode(trace).contentEquals(raw))
            require(trace.schema == "manual-transmission-phase2-engine-telemetry-v1" && !trace.executionAuthorizedByThisComponent)
            require(trace.engineSourceSha == identity.sourceCommit && trace.steps.none { it.failurePhase != null })
            val initialBytes = trace.initialState.toString().toByteArray(Charsets.UTF_8)
            require(ManualInitializationReplay.sha256(initialBytes) == trace.initialStateSha256)
            val stored = ManualTransitionJournal.inspect(directory.resolve("transitions"), identity.bytes(), initialBytes)
            require(!stored.interruptedIntent && stored.transitions.size == trace.steps.size)
            val initial = json.decodeFromJsonElement(GameState.serializer(), trace.initialState)
            val replay = PhaseTwoTelemetryAdapter(initial, ActionProcessor(registry), identity.sourceCommit,
                trace.playerIds.map(::EntityId), trace.manualSeat)
            stored.transitions.forEachIndexed { index, row ->
                val step = trace.steps[index]
                require(step.sequence == index + 1)
                val before = replay.state
                val beforeBytes = json.encodeToJsonElement(GameState.serializer(), before).toString().toByteArray(Charsets.UTF_8)
                val intent = buildJsonObject {
                    put("sequence", index + 1); put("source", identity.sourceCommit)
                    put("before", ManualInitializationReplay.sha256(beforeBytes)); put("action", step.action)
                }.toString().toByteArray(Charsets.UTF_8)
                require(intent.contentEquals(row.action)) { "Missing, altered or reordered action at $index" }
                val action = json.decodeFromJsonElement(GameAction.serializer(), step.action)
                require(json.encodeToJsonElement(GameAction.serializer(), action) == step.action)
                val response = replay.process(action)
                val result = buildJsonObject {
                    put("sequence", index + 1)
                    put("classification", if (response.error == null) "ACCEPTED" else "REJECTED")
                    put("response", json.encodeToJsonElement(ExecutionResult.serializer(), response))
                }.toString().toByteArray(Charsets.UTF_8)
                require(result.contentEquals(row.resultEnvelope)) { "Engine result differs at $index" }
            }
            val expectedStop = when {
                trace.steps.lastOrNull()?.accepted == false -> "ENGINE_REJECTED_ACTION"
                replay.state.gameOver -> "ENGINE_TERMINAL"
                else -> requireNotNull(trace.stopObservation).kind
            }
            require(stored.rawStopClassification == expectedStop)
            trace.stopObservation?.let {
                require(it.data.keys == setOf("reason"))
                replay.stop(it.kind, it.data.getValue("reason").jsonPrimitive.content)
            }
            return replay.finish().also { require(it == trace) { "Trace/transition evidence disagreement" } }
        } finally {
            require(snapshot() == original) { "Evidence changed during verification" }
        }
    }
}
