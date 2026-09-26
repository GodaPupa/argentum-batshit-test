package com.wingedsheep.gym.ferocity

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class FerocityFreshReplayRequest(
    val schemaVersion: Int = 1,
    val bundlePath: String,
    val bundleSha256: String,
    val admittedPins: FerocitySourcePins,
    val classPath: List<FerocityRuntimePathPin>,
    val javaExecutable: String,
    val journalRoot: String,
    val namespace: String,
    val trialId: String,
    val outputPath: String,
    val inlineTokenAdmission: FerocityInlineTokenAdmission? = null,
)

@Serializable
internal data class FerocityFreshReplayReceipt(
    val schemaVersion: Int = 1,
    val processId: Long,
    val requestSha256: String,
    val bundleSha256: String,
    val classPathSha256: String,
    val sourceCommit: String,
    val sourceTreeSha256: String,
    val javaVersion: String,
    val javaExecutableSha256: String,
    val definitionBindings: Map<String, String>,
    val replayStatus: String,
    val verifiedSubmissions: Int,
    val unresolvedIntent: Int?,
    val recordedEndReason: String?,
    val recordedWinner: String?,
    val finalJournalChainSha256: String,
    val runtimeInputsUnchanged: Boolean,
    val newGameplayGames: Int = 0,
    val inlineTokenAdmissionSha256: String? = null,
    val inlineTokenProofCount: Int = 0,
    val inlineTokenProofsSha256: String? = null,
)

/** Separate-JVM entry: immutable exact replay only. Never initializes, claims, retunes or pilots a trial. */
object FerocityFreshReplayMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected absolute replay request path and its exact SHA-256" }
        val result = replayFreshFerocity(Path.of(args[0]), args[1])
        println(FerocityJournalCodec.canonical(FerocityFreshReplayReceipt.serializer(), result))
    }
}

internal fun replayFreshFerocity(requestPath: Path, requestSha256: String): FerocityFreshReplayReceipt {
    val request = ferocityReadExactJson(requestPath, requestSha256, FerocityFreshReplayRequest.serializer())
    require(request.schemaVersion == 1) { "Unsupported fresh replay request schema" }
    val output = Path.of(request.outputPath)
    require(output.isAbsolute && !Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
        "Replay receipt must use a new absolute output path"
    }
    val bundlePath = Path.of(request.bundlePath)
    val journalRoot = Path.of(request.journalRoot)
    require(journalRoot.isAbsolute)
    val executable = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath()
    require(executable.toString() == request.javaExecutable &&
        ferocityFileSha256(executable) == request.admittedPins.dependencySha256[FEROCITY_JAVA_DEPENDENCY]) {
        "Java executable is not the admitted runtime dependency"
    }
    verifyFerocityClassPath(request.classPath, request.admittedPins)
    val definitions = readFerocityBundle(bundlePath, request.bundleSha256, request.admittedPins)
    val originalFiles = captureFerocityReplayFiles(journalRoot, request.namespace, request.trialId)
    if (originalFiles.claim.spec.stage != FerocityTrialStage.DETERMINISTIC_FIXTURE) {
        require(definitions.bundle.definitionsInRegistrationOrder.none {
            it.origin.kind == FerocityDefinitionOriginKind.DETERMINISTIC_FIXTURE
        }) { "Deterministic fixture definitions cannot be admitted to research samples" }
    }
    val replayer = FerocityTrialReplay(definitions.registry, request.admittedPins, request.inlineTokenAdmission)
    val report = replayer.verify(journalRoot, request.namespace, request.trialId)
    // A process may have inspected mutable external files. Check exact runtime/bundle/request
    // bytes again before writing any accepted replay receipt; preserve failures without retry.
    verifyFerocityClassPath(request.classPath, request.admittedPins)
    require(ferocityFileSha256(executable) == request.admittedPins.dependencySha256[FEROCITY_JAVA_DEPENDENCY]) {
        "Java executable changed during replay"
    }
    ferocityReadExactJson(requestPath, requestSha256, FerocityFreshReplayRequest.serializer())
    readFerocityBundle(bundlePath, request.bundleSha256, request.admittedPins)
    originalFiles.verifyUnchanged()
    val receipt = FerocityFreshReplayReceipt(1, ProcessHandle.current().pid(), requestSha256, request.bundleSha256,
        ferocityClassPathDigest(request.classPath), request.admittedPins.sourceCommit, request.admittedPins.sourceTreeSha256,
        System.getProperty("java.runtime.version"), ferocityFileSha256(executable), definitions.bundle.registryBindings,
        report.status.name, report.verifiedSubmissions, report.unresolvedIntent, report.recordedEndReason?.name,
        report.recordedWinner?.value, report.finalChainSha256, true, 0,
        request.inlineTokenAdmission?.let(::ferocityInlineTokenAdmissionSha256), replayer.verifiedInlineTokenProofs.size,
        request.inlineTokenAdmission?.let { FerocityJournalCodec.sha(FerocityJournalCodec.canonical(
            kotlinx.serialization.builtins.ListSerializer(FerocityInlineTokenProof.serializer()), replayer.verifiedInlineTokenProofs)) })
    ferocityWriteNewJson(output, FerocityFreshReplayReceipt.serializer(), receipt)
    return receipt
}
