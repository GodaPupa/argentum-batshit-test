package com.wingedsheep.gym.ferocity

import kotlinx.serialization.json.*
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fixed noninitializing resource calibration. Repeats schema-valid records, not legal engine
 * submissions. This prefix is synthetic, is never passed to a game/replayer, and is not evidence
 * of any game outcome. The three commands are the entire prospectively bounded process budget.
 */
object FerocityJournalResourceProbe {
    private const val TEMPLATE_SHA256 = "fa4cbc67fc5482d298ddf7e92968349b6eba584c4328c851d2bda86ce0277c5a"
    private const val NAMESPACE = "ferocity-recycling/resource-calibration/v1"
    private const val TRIAL = "synthetic-repeated-records-NOT-GAMEPLAY"
    private const val MINIMUM_TRACE_BYTES = 127L * 1024 * 1024

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 5) { "Expected mode, existing journal root, namespace, trial ID and fixed output directory" }
        val mode = args[0]
        require(mode in setOf("write", "read-single", "read-double"))
        require(ManagementFactory.getRuntimeMXBean().inputArguments == listOf("-Xmx2048m")) {
            "Calibration requires the exact 2 GiB JVM heap flag"
        }
        verifyFerocityInheritedFileSizeLimit()
        val started = System.nanoTime()
        val sourceRoot = Path.of(args[1]).toRealPath()
        val output = Path.of(args[4]).toRealPath()
        val receiptPath = output.resolve("$mode.json")
        require(!Files.exists(receiptPath)) { "A calibration command cannot replace its prior receipt" }
        val sourceJournal = FerocityTrialJournal.journalPath(sourceRoot, args[2], args[3])
        require(ferocityFileSha256(sourceJournal) == TEMPLATE_SHA256) { "Only the predeclared existing fixed Gift trace is admitted" }
        val original = captureFerocityReplayFiles(sourceRoot, args[2], args[3])
        val syntheticRoot = output.resolve("synthetic-prefix")
        var maximumObservedHeap = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        fun sampleHeap() {
            maximumObservedHeap = maxOf(maximumObservedHeap, ManagementFactory.getMemoryMXBean().heapMemoryUsage.used)
        }

        var recordCount = 0
        if (mode == "write") {
            val template = readFerocityJournal(sourceRoot, args[2], args[3])
            require(template.claim.spec.stage == FerocityTrialStage.DETERMINISTIC_FIXTURE)
            val result = template.records.filterIsInstance<FerocityResult>().last()
            val intent = template.records.filterIsInstance<FerocityIntent>().last()
            require(intent.actorInput != null && result.status == FerocitySubmissionStatus.APPLIED)
            val state = FerocityJournalCodec.state(result.afterState)
            require(!state.gameOver)
            // The old fixed seed/pins are copied as provenance; no entropy or initializer is called.
            val spec = template.claim.spec.copy(namespace = NAMESPACE, trialId = TRIAL, allocationId = TRIAL)
            FerocityTrialJournal.claimNew(syntheticRoot, spec).use { writer ->
                writer.append(FerocityHeader(writer.claimSha256, FerocityTrialLimits(),
                    "SYNTHETIC RESOURCE CALIBRATION ONLY; repeated records are not engine submissions", null))
                writer.append(FerocityInitialized(result.afterState, FerocityJournalCodec.events(emptyList()),
                    requireNotNull(template.initialized).playerIds, result.engineStepAfter))
                recordCount = 2
                for (submission in 1..6000) {
                    // Decode each detached copy to avoid unrealistically sharing a single wire
                    // String/ActorInput between all retained records in the writer's heap.
                    val nextIntent = FerocityJournalCodec.json.decodeFromString(FerocityIntent.serializer(),
                        FerocityJournalCodec.json.encodeToString(FerocityIntent.serializer(), intent.copy(
                            submission = submission, engineStepBefore = result.engineStepAfter + submission - 1,
                            beforeState = result.afterState)))
                    val nextResult = FerocityJournalCodec.json.decodeFromString(FerocityResult.serializer(),
                        FerocityJournalCodec.json.encodeToString(FerocityResult.serializer(), result.copy(
                            submission = submission, engineStepAfter = result.engineStepAfter + submission)))
                    fun bytes(index: Int, record: FerocityJournalRecord): Int =
                        (FerocityJournalCodec.envelopeLine(FerocityJournalCodec.envelope(index, "0".repeat(64), record)) + "\n")
                            .toByteArray(Charsets.UTF_8).size
                    val extra = bytes(recordCount, nextIntent).toLong() + bytes(recordCount + 1, nextResult)
                    if (Files.size(writer.journalPath) + extra > FEROCITY_CHILD_FILE_LIMIT_BYTES) break
                    writer.append(nextIntent)
                    writer.append(nextResult)
                    recordCount += 2
                    sampleHeap()
                }
                require(Files.size(writer.journalPath) in MINIMUM_TRACE_BYTES..FEROCITY_CHILD_FILE_LIMIT_BYTES) {
                    "The single calibration attempt did not reach its declared 127–128 MiB shape"
                }
                // Deliberately no END/outcome: these counter-adjusted copies were never executed.
            }
        } else {
            val prior = FerocityJournalCodec.json.parseToJsonElement(Files.readString(output.resolve("write.json"))).jsonObject
            val trace = FerocityTrialJournal.journalPath(syntheticRoot, NAMESPACE, TRIAL)
            require(ferocityFileSha256(trace) == prior.getValue("journalSha256").jsonPrimitive.content)
            require(Files.size(trace) in MINIMUM_TRACE_BYTES..FEROCITY_CHILD_FILE_LIMIT_BYTES)
            val guard = captureFerocityReplayFiles(syntheticRoot, NAMESPACE, TRIAL)
            val first = readFerocityJournal(syntheticRoot, NAMESPACE, TRIAL)
            val second = if (mode == "read-double") readFerocityJournal(syntheticRoot, NAMESPACE, TRIAL) else null
            val working = second ?: first
            sampleHeap()
            var decodedEntities = 0L
            working.records.forEach { record ->
                val payload = when (record) {
                    is FerocityInitialized -> record.state
                    is FerocityIntent -> record.beforeState
                    is FerocityResult -> record.afterState
                    else -> null
                }
                payload?.let {
                    val state = FerocityJournalCodec.state(it)
                    require(FerocityJournalCodec.state(state) == it)
                    decodedEntities += state.entities.size
                    sampleHeap()
                }
            }
            require(decodedEntities > 0 && first.end == null)
            if (second != null) require(first == second) // Both full parsed datasets remain live.
            guard.verifyUnchanged()
            recordCount = working.records.size
        }

        original.verifyUnchanged()
        val trace = FerocityTrialJournal.journalPath(syntheticRoot, NAMESPACE, TRIAL)
        val vmHighWater = Files.readString(Path.of("/proc/self/status")).lineSequence()
            .single { it.startsWith("VmHWM:") }.substringAfter(':').trim()
        val receipt = buildJsonObject {
            put("schema", "ferocity-noninitializing-resource-calibration-v1")
            put("mode", mode); put("processId", ProcessHandle.current().pid())
            put("templateSha256", TEMPLATE_SHA256); put("journalSha256", ferocityFileSha256(trace))
            put("journalBytes", Files.size(trace)); put("records", recordCount)
            put("elapsedNanos", System.nanoTime() - started)
            put("maximumObservedHeapBytes", maximumObservedHeap)
            put("maximumHeapBytes", Runtime.getRuntime().maxMemory()); put("procVmHwm", vmHighWater)
            put("engineInitializations", 0); put("gameplayGames", 0); put("newEntropyDraws", 0)
            put("engineVerified", false); put("syntheticRecordsNeverSubmitted", true)
            put("universalMemoryBound", false)
        }
        ferocityWriteNewJson(receiptPath, JsonObject.serializer(), receipt)
        println(FerocityJournalCodec.canonical(JsonObject.serializer(), receipt))
    }
}
