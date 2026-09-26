package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Production command surface: verify, allocate the one fixed cell, or run exactly one row under
 * the qualified external watchdog. No stage, deck, pilot, initializer, seed or cap override exists.
 */
object FerocityDevelopmentCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val command = parseFerocityDevelopmentCommand(args)
        val admitted = VerifiedFerocityDevelopment.verify(command.repository, command.admissionPath, command.admissionSha256)
        when (command.operation) {
            "verify" -> println(buildJsonObject {
                put("status", "VERIFIED_INPUTS_NO_ENTROPY_NO_GAME")
                put("admissionSha256", admitted.admissionSha256)
                put("cellId", FEROCITY_FIRST_CELL)
                put("allocations", 16)
                put("logicalSourceCommit", admitted.manifest.source.commit)
                put("verificationObservedHead", admitted.verificationObservedHead)
            })
            "allocate" -> println(buildJsonObject {
                put("status", "LEDGER_DURABLE_NO_GAME")
                put("ledgerSha256", admitted.allocate())
                put("logicalSourceCommit", admitted.manifest.source.commit)
                put("verificationObservedHead", admitted.verificationObservedHead)
            })
            "run-one" -> {
                val run = FerocityDevelopmentRun.admit(command, admitted)
                val summary = FerocityTrialRunner(run.registry, run.spec.pins,
                    inlineTokenAdmission = run.inlineTokenAdmission).runAdmittedDevelopment(run)
                // Source/ledger/supervisor mutation makes the process fail, preserving its original
                // journal; an END is not accepted merely because an in-process loop reached it.
                run.recheck()
                println(buildJsonObject {
                    put("status", "RECORDED_REQUIRES_FRESH_REPLAY")
                    put("allocationId", summary.trialId)
                    put("stopReason", summary.reason.name)
                    put("submittedActions", summary.submittedActions)
                    put("journalPath", summary.journalPath.toString())
                    put("logicalSourceCommit", admitted.manifest.source.commit)
                    put("verificationObservedHead", admitted.verificationObservedHead)
                })
            }
            else -> error("Unreachable command")
        }
    }
}

internal data class FerocityDevelopmentCommand(
    val operation: String,
    val repository: Path,
    val admissionPath: Path,
    val admissionSha256: String,
    val ledgerSha256: String? = null,
    val allocationId: String? = null,
    val supervisorClaimPath: Path? = null,
)

internal fun parseFerocityDevelopmentCommand(args: Array<String>): FerocityDevelopmentCommand {
    require(args.isNotEmpty() && args[0] in setOf("verify", "allocate", "run-one")) { "Unknown development command" }
    require(args.size == if (args[0] == "run-one") 7 else 4) {
        "Expected operation, absolute repository, admission path and SHA; run-one additionally requires ledger SHA, exact allocation ID and supervisor claim"
    }
    val root = Path.of(args[1])
    val admission = Path.of(args[2])
    require(root.isAbsolute && admission.isAbsolute)
    requireSha256(args[3])
    if (args[0] != "run-one") return FerocityDevelopmentCommand(args[0], root, admission, args[3])
    requireSha256(args[4])
    require(args[5] in firstFerocityDevelopmentAllocations().map { it.protocolId }) { "Only one of the original sixteen D2 allocations is admitted" }
    val claim = Path.of(args[6])
    require(claim.isAbsolute)
    return FerocityDevelopmentCommand(args[0], root, admission, args[3], args[4], args[5], claim)
}

/** Only the checked manifest+ledger+watchdog route can construct the runner's D2 capability. */
internal class FerocityDevelopmentRun private constructor(
    private val command: FerocityDevelopmentCommand,
    private val admitted: VerifiedFerocityDevelopment,
    private val row: FerocityDevelopmentSeedRow,
) {
    val root: Path get() = admitted.journalRoot
    val spec: FerocityTrialSpec get() = FerocityTrialSpec(
        FEROCITY_D2_NAMESPACE, row.allocation.protocolId, row.allocation.protocolId, FerocityTrialStage.DEVELOPMENT,
        row.gameSeed, mapOf(FEROCITY_CANDIDATE_PLAYER.value to row.candidatePolicySeed, FEROCITY_RED_PLAYER.value to row.redPolicySeed),
        admitted.pins(requireNotNull(command.ledgerSha256)),
    )
    val registry get() = admitted.registry()
    val config: GameConfig get() = admitted.config(row)
    val limits: FerocityTrialLimits get() = admitted.manifest.limits
    val inlineTokenAdmission: FerocityInlineTokenAdmission get() = admitted.inlineTokens

    /** Fixed factories are trusted code. Neither pilot receives this capability or its source pins. */
    fun pilots(): Map<EntityId, FerocityPilot> {
        val artifact = ArtifactControlPilot()
        val red = RedMadnessPilot()
        return mapOf(FEROCITY_CANDIDATE_PLAYER to FerocityPilot(artifact::choose), FEROCITY_RED_PLAYER to FerocityPilot(red::choose))
    }

    fun recheck() {
        admitted.recheck()
        require(admitted.readLedger(requireNotNull(command.ledgerSha256)).rows.single {
            it.allocation.protocolId == command.allocationId
        } == row) { "Allocated stream changed" }
        verifyDevelopmentSupervisor(command, admitted)
    }

    companion object {
        fun admit(command: FerocityDevelopmentCommand, admitted: VerifiedFerocityDevelopment): FerocityDevelopmentRun {
            require(command.operation == "run-one" && command.admissionSha256 == admitted.admissionSha256 &&
                command.admissionPath == admitted.admissionPath && command.repository.toRealPath() == admitted.repository)
            val ledger = admitted.readLedger(requireNotNull(command.ledgerSha256))
            val row = ledger.rows.single { it.allocation.protocolId == command.allocationId }
            val result = FerocityDevelopmentRun(command, admitted, row)
            result.recheck()
            return result
        }
    }
}

/** Bind the actual watchdog's pre-launch durable claim, its exact child argv and fixed wall cap. */
internal fun verifyDevelopmentSupervisor(command: FerocityDevelopmentCommand, admitted: VerifiedFerocityDevelopment) {
    verifyDevelopmentSupervisorHandshake(command, FerocityDevelopmentSupervisorContext(
        admitted.repository, admitted.manifest, admitted.journalRoot, admitted.ledgerPath, FerocityDevelopmentCli::class.java.name))
}

/** Expected process facts only: this value cannot create a verified admission or run capability. */
internal data class FerocityDevelopmentSupervisorContext(
    val repository: Path,
    val manifest: FerocityDevelopmentManifest,
    val journalRoot: Path,
    val ledgerPath: Path,
    val jvmEntrypoint: String,
)

/** The fixed process fixture exercises this identical verifier under the real qualified watchdog. */
internal fun verifyDevelopmentSupervisorHandshake(command: FerocityDevelopmentCommand, expected: FerocityDevelopmentSupervisorContext) {
    val m = expected.manifest
    val path = requireNotNull(command.supervisorClaimPath)
    val runId = "D2-" + FerocityJournalCodec.sha(requireNotNull(command.allocationId)).take(24)
    val supervisorRoot = admissionPath(expected.repository, m.supervisorDirectory)
    require(path == supervisorRoot.resolve(runId).resolve("claim.json")) { "A different supervisor root cannot replay an allocation" }
    require(path.toRealPath() == path && Files.size(path) in 1..(4L * 1024 * 1024))
    val claim = FerocityJournalCodec.json.parseToJsonElement(Files.readString(path)).jsonObject
    require(claim.getValue("schema_version").jsonPrimitive.int == 1 &&
        claim.getValue("scope").jsonPrimitive.content == "PROCESS_SUPERVISION_ONLY")
    require(claim.getValue("supervisor_source_sha256").jsonPrimitive.content == m.watchdog.source.sha256)
    require(claim.getValue("python_executable").jsonPrimitive.content == m.watchdog.pythonExecutable &&
        claim.getValue("python_executable_sha256").jsonPrimitive.content == m.watchdog.pythonExecutableSha256)
    val spec = claim.getValue("spec").jsonObject
    require(spec.keys == setOf("schema_version", "run_id", "argv", "cwd", "wall_seconds", "term_grace_seconds",
        "journal_path", "pinned_files", "supervisor_sha256", "inspection_limit_bytes"))
    require(spec.getValue("schema_version").jsonPrimitive.int == 1 && spec.getValue("run_id").jsonPrimitive.content == runId)
    require(claim.getValue("spec_sha256").jsonPrimitive.content ==
        FerocityJournalCodec.sha(FerocityJournalCodec.canonical(JsonElement.serializer(), spec))) { "Supervisor claim spec hash differs" }
    require(spec.getValue("supervisor_sha256").jsonPrimitive.content == m.watchdog.source.sha256)
    require(spec.getValue("cwd").jsonPrimitive.content == expected.repository.toString())
    require(spec.getValue("wall_seconds").jsonPrimitive.double == m.watchdog.wallSeconds.toDouble() &&
        spec.getValue("term_grace_seconds").jsonPrimitive.double == m.watchdog.termGraceSeconds.toDouble())
    val expectedArgv = listOf(m.source.javaExecutable, "-Xmx2048m", "-cp",
        m.source.classPath.joinToString(java.io.File.pathSeparator) { it.path }, expected.jvmEntrypoint,
        "run-one", expected.repository.toString(), command.admissionPath.toString(), command.admissionSha256,
        requireNotNull(command.ledgerSha256), requireNotNull(command.allocationId), path.toString())
    require(spec.getValue("argv").jsonArray.map { it.jsonPrimitive.content } == expectedArgv) {
        "Supervisor did not reserve this exact single-game command"
    }
    val ownedProcesses = readFerocitySupervisorProcessFacts()
    validateFerocityJvmProcessFacts(ownedProcesses, captureFerocityJvmProcessFacts())
    require(ownedProcesses.child.executable == m.source.javaExecutable &&
        ownedProcesses.child.executableSha256 == m.source.dependencies.getValue(FEROCITY_JAVA_DEPENDENCY)) {
        "Actual JVM executable differs from the qualified binary"
    }
    require(ownedProcesses.child.argv == expectedArgv) { "Actual JVM arguments differ from supervision" }
    require(ownedProcesses.parent.executable == m.watchdog.pythonExecutable &&
        ownedProcesses.parent.executableSha256 == m.watchdog.pythonExecutableSha256 &&
        ownedProcesses.parent.argv.first() == m.watchdog.pythonExecutable) { "Actual parent executable differs from the qualified Python" }
    val parentArgs = ownedProcesses.parent.argv.drop(1)
    require(parentArgs.size == 5 && parentArgs[0] == admissionPath(expected.repository, m.watchdog.source.path).toString() &&
        parentArgs[1] == "--spec" && parentArgs[3] == "--output-root" && parentArgs[4] == supervisorRoot.toString()) {
        "The qualified supervisor and fixed evidence root must own this child process"
    }
    val liveSpecPath = Path.of(parentArgs[2])
    require(liveSpecPath.isAbsolute && liveSpecPath.toRealPath() == liveSpecPath && Files.size(liveSpecPath) <= 4L * 1024 * 1024)
    require(FerocityJournalCodec.json.parseToJsonElement(Files.readString(liveSpecPath)).jsonObject == spec) {
        "Supervisor launch specification changed"
    }
    val expectedJournal = FerocityTrialJournal.journalPath(expected.journalRoot, FEROCITY_D2_NAMESPACE, command.allocationId)
    require(spec.getValue("journal_path").jsonPrimitive.content == expectedJournal.toString())
    val pins = spec.getValue("pinned_files").jsonObject.mapValues { it.value.jsonPrimitive.content }
    val expectedFiles = mapOf(
        m.source.javaExecutable to m.source.dependencies.getValue(FEROCITY_JAVA_DEPENDENCY),
        command.admissionPath.toString() to command.admissionSha256,
        expected.ledgerPath.toString() to command.ledgerSha256,
        admissionPath(expected.repository, m.bundle.path).toString() to m.bundle.sha256,
    )
    require(expectedFiles.all { (name, sha) -> pins[name] == sha }) { "Supervisor omits an immutable run input" }
    pins.forEach { (name, sha) -> require(ferocityFileSha256(Path.of(name)) == sha) }
    val finalProcesses = readFerocitySupervisorProcessFacts()
    validateFerocityJvmProcessFacts(finalProcesses, captureFerocityJvmProcessFacts())
    requireSameFerocitySupervisorProcesses(ownedProcesses, finalProcesses)
}
