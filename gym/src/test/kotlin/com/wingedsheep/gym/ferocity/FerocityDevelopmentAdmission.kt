package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.sdk.core.AttackMode
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime

internal const val FEROCITY_FIRST_CELL = "D2-FIRST-A3-RED"
internal const val FEROCITY_D2_NAMESPACE = "ferocity-recycling/v0.1/D2"
internal const val FEROCITY_RED_ID = "mono_red_madness_mistertwin_20260924"
internal val FEROCITY_D2_LIMITS = FerocityTrialLimits(6000, 150, 300000)
internal val FEROCITY_CANDIDATE_PLAYER = EntityId("ferocity-candidate")
internal val FEROCITY_RED_PLAYER = EntityId("ferocity-red")
private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
private val unallocatedLedgerDigest = FerocityJournalCodec.sha("FEROCITY_RECYCLING/D2/NO_LEDGER_CREATED")

@Serializable
internal data class FerocityAdmissionFile(val path: String, val sha256: String)

@Serializable
internal data class FerocityDevelopmentSource(
    val commit: String,
    /** SHA-256 of the canonical complete compiled-input path -> digest map. */
    val treeSha256: String,
    val compiledInputs: FerocityAdmissionFile,
    val dependencies: Map<String, String>,
    val dependencyFiles: Map<String, FerocityAdmissionFile>,
    val serializerSha256: String,
    val classPath: List<FerocityRuntimePathPin>,
    val javaExecutable: String,
)

@Serializable
internal data class FerocityDevelopmentPolicy(val version: String, val files: Map<String, String>)

@Serializable
internal data class FerocityQualificationGate(
    val id: String,
    val receipt: FerocityAdmissionFile,
    val compiledInputs: FerocityAdmissionFile,
    /** Exactly the named classes whose passing, executed assertions support this gate. */
    val classes: Map<String, Int>,
    val xmlFiles: Map<String, FerocityAdmissionFile>,
    /** Preserved validator and per-class build logs bind the fresh-execution evidence. */
    val validator: FerocityAdmissionFile,
    val logs: Map<String, FerocityAdmissionFile>,
)

@Serializable
internal data class FerocityQualifiedClass(val gateId: String, val className: String)

@Serializable
internal data class FerocityDevelopmentWatchdog(
    val source: FerocityAdmissionFile,
    val acceptance: FerocityAdmissionFile,
    val runReceipt: FerocityAdmissionFile,
    val authorReceipt: FerocityAdmissionFile,
    val processClaim: FerocityAdmissionFile,
    val pythonExecutable: String,
    val pythonExecutableSha256: String,
    val wallSeconds: Int = 300,
    val termGraceSeconds: Int = 5,
)

/** No seed and no ledger hash: this object can be reviewed and pinned before entropy is read. */
@Serializable
internal data class FerocityDevelopmentManifest(
    val schemaVersion: Int = 1,
    val cellId: String = FEROCITY_FIRST_CELL,
    val stage: FerocityTrialStage = FerocityTrialStage.DEVELOPMENT,
    val rulesMode: String = "VERIFIED_TEXT_PRERELEASE",
    val repositoryPath: String,
    val source: FerocityDevelopmentSource,
    val protocolFiles: Map<String, String>,
    val rulesAudit: FerocityAdmissionFile,
    val decks: Map<String, FerocityAdmissionFile>,
    val mainDeckSha256: Map<String, String>,
    val policies: Map<String, FerocityDevelopmentPolicy>,
    val bundle: FerocityAdmissionFile,
    val inlineTokenAdmission: FerocityAdmissionFile,
    val cardDefinitionSha256: Map<String, String>,
    val gates: List<FerocityQualificationGate>,
    /** Card names and mechanic tags map to actually executed, source-bound class receipts. */
    val coverage: Map<String, List<FerocityQualifiedClass>>,
    val watchdog: FerocityDevelopmentWatchdog,
    val limits: FerocityTrialLimits = FEROCITY_D2_LIMITS,
    val allocationIds: List<String> = firstFerocityDevelopmentAllocations().map { it.protocolId },
    val journalDirectory: String,
    /** One fixed directory, reserved exclusively before entropy; no new-root retry interface. */
    val ledgerDirectory: String,
    val supervisorDirectory: String = "ferocity-recycling/evidence/process/development/D2-first-cell",
    val replacementsAuthorized: Int = 0,
)

@Serializable
internal data class FerocityDevelopmentAllocation(val deckId: String, val seat: String, val index: Int) {
    val protocolId: String get() = "FEROCITY_RECYCLING/v0.1/D2/$deckId/$FEROCITY_RED_ID/$seat/$index"
}

@Serializable
internal data class FerocityDevelopmentSeedRow(
    val allocation: FerocityDevelopmentAllocation,
    val gameSeed: Long,
    val candidatePolicySeed: Long,
    val redPolicySeed: Long,
)

@Serializable
internal data class FerocityDevelopmentLedger(
    val schemaVersion: Int = 1,
    val cellId: String = FEROCITY_FIRST_CELL,
    val admissionSha256: String,
    val entropy: String = "java.security.SecureRandom/independent-nextLong/v1",
    val rows: List<FerocityDevelopmentSeedRow>,
)

internal fun firstFerocityDevelopmentAllocations(): List<FerocityDevelopmentAllocation> =
    listOf("A3-F4", "A3-N0").flatMap { deck ->
        listOf("play", "draw").flatMap { seat -> (1..4).map { FerocityDevelopmentAllocation(deck, seat, it) } }
    }

internal val firstDeckSourceHashes = mapOf(
    "A3-F4" to "41d3ea465858649cea3aa21d0ea7d82513c6461a3f7d94c98c089e04c4ea2ad7",
    "A3-N0" to "95aa88b0f66524250588fdb0226e9792b9bef2f8dd0e411809689aec04f73aea",
    FEROCITY_RED_ID to "9880736a719b2758f48b5f0a254a05d870a061146b9135d74be1581d7fd1ea2a",
)
internal val firstDeckMainHashes = mapOf(
    "A3-F4" to "c72947c48bc45f2b31352a662a99e75dfc0f8ede72ecccc69b574a568eae6ba4",
    "A3-N0" to "9aa9d3133f29b5793d66e9941da65d5c74d551f2d670b162f547ad0c1ae79298",
    FEROCITY_RED_ID to "d3427549613095ae1af366d3a060e94c1eb5be1715337133928c35ed7ecfee94",
)
internal val requiredProtocolHashes = mapOf(
    "ferocity-recycling/protocols/ACTIVE_CONTRACT.json" to "8d0551bb3fc5e4a9492540412fec17284bb8311d3501d0baf014da3969cc15d4",
    "ferocity-recycling/PROTOCOL.md" to "69a4018e9831fbd2d163ee441752d387adc4f8af9354b18f88285f4fe9a01699",
    "ferocity-recycling/protocols/RESEARCH_PROTOCOL.md" to "ab663b6b5f3d4761469bb2a9fcfd73a0bffd74efd9bfdb80b2de94640b59cee5",
    "ferocity-recycling/protocols/RECONCILIATION_AMENDMENT.md" to "2829d4623e142cdf427339b4e19f8bc8f96747410eeccb1abe1287986b35458f",
    "ferocity-recycling/protocols/initial-search-manifest.json" to "ef75f5cec859dcc9f765de0feacb7cb7696037f4c7035d4b68b6bdfccee1c87d",
)
internal val requiredFirstCellMechanics = setOf(
    "mechanic:ferocity-complete", "mechanic:shaman-lki-and-priority", "mechanic:toxin-lifelink-clue",
    "mechanic:combat-damage", "mechanic:trigger-ordering", "mechanic:flashback-all-stack-exits",
    "mechanic:madness-payment", "mechanic:blood-discard-draw", "mechanic:nda-return-role-death",
    "mechanic:brew-gift-and-targets", "mechanic:inline-fish-provenance", "mechanic:highway-resolution-plot",
    "runtime:actor-observation", "runtime:journal-replay", "runtime:definition-bundle",
    "runtime:development-admission", "policy:artifact-control", "policy:red-madness",
)
private val firstCellInlineSourcePaths = mapOf(
    "ferocity/inline-source/GiftDsl.kt" to "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/mechanics/GiftDsl.kt",
    "ferocity/inline-source/CreateTokenExecutor.kt" to "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/token/CreateTokenExecutor.kt",
    "ferocity/inline-source/TokenCreationReplacementHelper.kt" to "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/token/TokenCreationReplacementHelper.kt",
    "ferocity/inline-source/StackResolver.kt" to "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt",
)

/** A source-bound verifier result. No constructor accepts a caller's 'passed' boolean. */
internal class VerifiedFerocityDevelopment private constructor(
    val repository: Path,
    val admissionPath: Path,
    val admissionSha256: String,
    val manifest: FerocityDevelopmentManifest,
    private val rawDecks: Map<String, List<String>>,
    private val definitions: FerocityRestoredDefinitions,
    internal val inlineTokens: FerocityInlineTokenAdmission,
    /** Publication metadata; logical source pins remain the original admitted source commit. */
    internal val verificationObservedHead: String,
) {
    val ledgerPath: Path get() = admissionPath(repository, manifest.ledgerDirectory).resolve("ledger.json")
    val journalRoot: Path get() = admissionPath(repository, manifest.journalDirectory)

    internal fun pins(ledgerSha256: String): FerocitySourcePins = manifest.toPins(admissionSha256, ledgerSha256)
    internal fun registry() = definitions.registry

    internal fun config(row: FerocityDevelopmentSeedRow): GameConfig = configuredFirstFerocityGame(
        row, rawDecks.mapValues { (_, names) -> names.map(definitions::resolveDeckName) }
    )

    /** Source/bundle/gate bytes are rechecked before allocating or claiming any real game. */
    fun recheck(): VerifiedFerocityDevelopment = verify(repository, admissionPath, admissionSha256)

    /** Production entropy is created only after every gate and the exclusive reservation succeed. */
    fun allocate(): String {
        recheck()
        val parent = ledgerPath.parent.parent
        if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            require(parent.parent.toRealPath() == parent.parent)
            Files.createDirectory(parent) // Fixed allocations directory only, before any entropy.
            FileChannel.open(parent.parent, StandardOpenOption.READ).use { it.force(true) }
            FileChannel.open(parent, StandardOpenOption.READ).use { it.force(true) }
        }
        return reserveFerocityDevelopmentLedger(ledgerPath.parent, admissionSha256) {
            val entropy = SecureRandom()
            firstFerocityDevelopmentAllocations().map { allocation ->
                FerocityDevelopmentSeedRow(allocation, entropy.nextLong(), entropy.nextLong(), entropy.nextLong())
            }
        }
    }

    internal fun readLedger(expected: String): FerocityDevelopmentLedger {
        val ledger = ferocityReadExactJson(ledgerPath, expected, FerocityDevelopmentLedger.serializer())
        validateFerocityDevelopmentLedger(ledger, admissionSha256)
        return ledger
    }

    companion object {
        fun verify(repository: Path, path: Path, expectedSha256: String): VerifiedFerocityDevelopment {
            val root = repository.toAbsolutePath().normalize().toRealPath()
            require(repository == root && path.isAbsolute && path.normalize() == path && path.toRealPath() == path) {
                "Use the exact canonical repository and admission paths, not aliases or symlinks"
            }
            val manifest = ferocityReadExactJson(path, expectedSha256, FerocityDevelopmentManifest.serializer())
            validateFerocityDevelopmentShape(manifest)
            require(root.toString() == manifest.repositoryPath) { "Another worktree cannot reclaim this admitted cell" }
            listOf(manifest.ledgerDirectory, manifest.journalDirectory, manifest.supervisorDirectory).forEach { relative ->
                var existing = admissionPath(root, relative)
                while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = requireNotNull(existing.parent)
                require(existing.toRealPath() == existing) { "Evidence directory alias or symlink is not admitted" }
            }
            val sourceMap = readStringMap(root, manifest.source.compiledInputs)
            require(sourceMap.isNotEmpty()) { "Empty compiled input identity" }
            require(FerocityJournalCodec.sha(FerocityJournalCodec.canonical(stringMapSerializer, sourceMap)) == manifest.source.treeSha256) {
                "Compiled source tree digest mismatch"
            }
            sourceMap.forEach { (name, sha) -> exactAdmissionBytes(root, FerocityAdmissionFile(name, sha)) }
            val observedHead = verifyFerocitySourcePublication(root, manifest.source.commit, sourceMap.keys)
            manifest.protocolFiles.forEach { (name, sha) -> exactAdmissionBytes(root, FerocityAdmissionFile(name, sha)) }
            exactAdmissionBytes(root, manifest.rulesAudit)
            manifest.source.dependencyFiles.forEach { (key, file) ->
                require(manifest.source.dependencies[key] == file.sha256) { "Dependency reference differs from pins: $key" }
                exactAdmissionBytes(root, file)
            }
            verifyDevelopmentPolicyInputs(root, manifest.policies, sourceMap)
            require(sourceMap["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityJournalCodec.kt"] == manifest.source.serializerSha256) {
                "Serializer pin is not the compiled journal codec"
            }
            val pins = manifest.toPins(expectedSha256, unallocatedLedgerDigest)
            verifyFerocityClassPath(manifest.source.classPath, pins)
            val java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath()
            require(java.toString() == manifest.source.javaExecutable &&
                ferocityFileSha256(java) == manifest.source.dependencies[FEROCITY_JAVA_DEPENDENCY]) {
                "Actual Java executable differs from the qualified runtime"
            }
            val definitions = readFerocityBundle(admissionPath(root, manifest.bundle.path), manifest.bundle.sha256, pins)
            requireFerocityResearchOrigins(definitions.bundle)
            val inlineTokens = ferocityReadExactJson(admissionPath(root, manifest.inlineTokenAdmission.path),
                manifest.inlineTokenAdmission.sha256, FerocityInlineTokenAdmission.serializer())
            verifyFerocityInlineTokenAdmission(inlineTokens, pins, definitions.registry)
            firstCellInlineSourcePaths.forEach { (key, name) ->
                val file = manifest.source.dependencyFiles.getValue(key)
                require(file.path == name && sourceMap[name] == file.sha256) {
                    "Inline-token source must be the actual compiled and qualified runtime file: $key"
                }
            }
            val decks = manifest.decks.mapValues { (id, file) ->
                readFirstFerocityDeck(exactAdmissionJson(root, file), id, manifest.mainDeckSha256.getValue(id))
                    .also { names -> names.forEach(definitions::resolveDeckName) }
            }
            val admittedNames = decks.values.flatten().toSet() + setOf("Clue", "Blood", "Wicked Role")
            require(definitions.registry.allCardNames() == admittedNames) { "Raw bundle is not the exact first-cell definition closure" }
            val required = admittedNames + requiredFirstCellMechanics
            val passed = verifyDevelopmentGates(root, manifest.gates, sourceMap)
            verifyDevelopmentCoverage(manifest.coverage, required, passed)
            verifyDevelopmentWatchdog(root, manifest.watchdog)
            require(gitOutput(root, "rev-parse", "HEAD") == observedHead) { "Publication HEAD changed during verification" }
            return VerifiedFerocityDevelopment(root, path, expectedSha256, manifest, decks, definitions, inlineTokens, observedHead)
        }
    }
}

internal fun validateFerocityDevelopmentShape(m: FerocityDevelopmentManifest) {
    require(m.schemaVersion == 1 && m.cellId == FEROCITY_FIRST_CELL)
    require(m.stage == FerocityTrialStage.DEVELOPMENT && m.rulesMode == "VERIFIED_TEXT_PRERELEASE") {
        "This entry point admits only verified-text prerelease D2 development"
    }
    require(m.source.commit.matches(Regex("[0-9a-f]{40}")))
    requireSha256(m.source.treeSha256)
    require(m.protocolFiles == requiredProtocolHashes) { "Effective reconciled protocol has changed" }
    require(m.decks.keys == firstDeckSourceHashes.keys && m.decks.mapValues { it.value.sha256 } == firstDeckSourceHashes)
    require(m.mainDeckSha256 == firstDeckMainHashes)
    require(m.policies.keys == setOf("artifact-control", "red-madness"))
    m.policies.forEach { (_, policy) ->
        require(policy.version.isNotBlank() && policy.files.isNotEmpty())
        policy.files.values.forEach(::requireSha256)
    }
    val policyNames = m.policies.values.flatMap { it.files.keys }.map { Path.of(it).fileName.toString() }.toSet()
    require(policyNames.containsAll(setOf("ArtifactControlPilot.kt", "RedMadnessPilot.kt", "ActorPublicCards.kt",
        "ActorChoiceSupport.kt", "ArtifactCombatPlanner.kt"))) { "All reviewed pilots and common helpers must be bound" }
    require(m.source.dependencies[FEROCITY_BUNDLE_DEPENDENCY] == m.bundle.sha256)
    require(m.source.dependencies[FEROCITY_INLINE_TOKEN_DEPENDENCY] == m.inlineTokenAdmission.sha256 &&
        m.source.dependencyFiles[FEROCITY_INLINE_TOKEN_DEPENDENCY] == m.inlineTokenAdmission)
    require(m.source.dependencies.keys.containsAll(FEROCITY_INLINE_TOKEN_SOURCE_KEYS))
    require(m.source.dependencies.keys.containsAll(setOf(FEROCITY_BUNDLE_DEPENDENCY, FEROCITY_CLASSPATH_DEPENDENCY, FEROCITY_JAVA_DEPENDENCY)))
    require(m.source.dependencyFiles.keys == m.source.dependencies.keys - setOf(FEROCITY_BUNDLE_DEPENDENCY, FEROCITY_CLASSPATH_DEPENDENCY, FEROCITY_JAVA_DEPENDENCY)) {
        "Every non-runtime dependency needs its exact source artifact"
    }
    require(m.gates.isNotEmpty() && m.gates.map { it.id }.distinct().size == m.gates.size)
    require(m.gates.all { it.id.isNotBlank() && it.classes.isNotEmpty() && it.classes.values.all { n -> n > 0 } &&
        it.xmlFiles.keys == it.classes.keys && it.logs.keys == it.classes.keys })
    requiredDevelopmentClassCounts.forEach { (name, count) ->
        require(m.gates.count { it.classes[name] == count } == 1) { "Required fixed qualification class missing or duplicated: $name" }
    }
    require(m.allocationIds == firstFerocityDevelopmentAllocations().map { it.protocolId }) { "Allocation matrix changed" }
    require(m.replacementsAuthorized == 0 && m.limits == FEROCITY_D2_LIMITS) { "No replacement or cap override is admitted" }
    require(m.watchdog.wallSeconds == 300 && m.watchdog.termGraceSeconds == 5)
    require(Path.of(m.repositoryPath).isAbsolute && Path.of(m.repositoryPath).normalize().toString() == m.repositoryPath)
    require(m.journalDirectory == "ferocity-recycling/evidence/development/D2-first-cell")
    require(m.ledgerDirectory == "ferocity-recycling/allocations/D2-first-cell")
    require(m.supervisorDirectory == "ferocity-recycling/evidence/process/development/D2-first-cell")
}

internal fun validateFerocityDevelopmentLedger(ledger: FerocityDevelopmentLedger, admissionSha256: String) {
    require(ledger.schemaVersion == 1 && ledger.cellId == FEROCITY_FIRST_CELL && ledger.admissionSha256 == admissionSha256)
    require(ledger.entropy == "java.security.SecureRandom/independent-nextLong/v1")
    require(ledger.rows.map { it.allocation } == firstFerocityDevelopmentAllocations()) { "Ledger omits, duplicates or changes an allocation" }
    val streams = ledger.rows.flatMap { listOf(it.gameSeed, it.candidatePolicySeed, it.redPolicySeed) }
    require(streams.size == 48 && streams.distinct().size == streams.size) { "Repeated random stream in the independent first cell" }
}

internal fun readFirstFerocityDeck(json: JsonObject, id: String, expectedMain: String): List<String> {
    require(json.getValue("id").jsonPrimitive.content == id)
    val entries = json.getValue("main").jsonArray.map { entry ->
        val row = entry.jsonObject
        require(row.keys == setOf("name", "count"))
        val name = row.getValue("name").jsonPrimitive.content
        val count = row.getValue("count").jsonPrimitive.int
        require(name.isNotBlank() && count in 1..60 && '\n' !in name && '\r' !in name)
        name to count
    }
    require(entries.map { it.first }.distinct().size == entries.size && entries.sumOf { it.second } == 60)
    require(FerocityJournalCodec.sha(entries.sortedBy { it.first }.joinToString("") { "${it.second} ${it.first}\n" }) == expectedMain)
    return entries.flatMap { (name, count) -> List(count) { name } }
}

internal fun configuredFirstFerocityGame(row: FerocityDevelopmentSeedRow, decks: Map<String, List<String>>): GameConfig {
    require(row.allocation in firstFerocityDevelopmentAllocations())
    require(decks.keys == firstDeckMainHashes.keys && decks.values.all { it.size == 60 })
    return GameConfig(
        players = listOf(
            PlayerConfig(row.allocation.deckId, Deck(decks.getValue(row.allocation.deckId)), 20, FEROCITY_CANDIDATE_PLAYER),
            PlayerConfig(FEROCITY_RED_ID, Deck(decks.getValue(FEROCITY_RED_ID)), 20, FEROCITY_RED_PLAYER),
        ),
        startingHandSize = 7, skipMulligans = false, useHandSmoother = false, handSmootherCandidates = 3,
        startingPlayerIndex = if (row.allocation.seat == "play") 0 else 1,
        format = Format.Standard, attackMode = AttackMode.MULTIPLE, teams = null, seed = row.gameSeed,
    )
}

internal fun FerocityDevelopmentManifest.toPins(admission: String, ledger: String) = FerocitySourcePins(
    source.commit, source.treeSha256, source.dependencies, mainDeckSha256,
    policies.mapValues { (_, policy) -> FerocityJournalCodec.sha(FerocityJournalCodec.canonical(FerocityDevelopmentPolicy.serializer(), policy)) },
    cardDefinitionSha256, source.serializerSha256,
    FerocityJournalCodec.sha(FerocityJournalCodec.canonical(stringMapSerializer, protocolFiles)), admission, ledger,
)

internal fun admissionPath(root: Path, relative: String): Path {
    require(relative.isNotBlank() && !Path.of(relative).isAbsolute && '\\' !in relative)
    require(relative.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe admission path" }
    return root.resolve(relative).normalize().also { require(it.startsWith(root)) }
}

internal fun exactAdmissionBytes(root: Path, file: FerocityAdmissionFile): ByteArray {
    requireSha256(file.sha256)
    val path = admissionPath(root, file.path)
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) in 0..(32L * 1024 * 1024))
    require(path.toRealPath() == path) { "Symbolic admission input is not permitted" }
    val bytes = Files.readAllBytes(path)
    require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) } == file.sha256) { "Admission input bytes changed: ${file.path}" }
    return bytes
}

internal fun exactAdmissionJson(root: Path, file: FerocityAdmissionFile): JsonObject =
    FerocityJournalCodec.json.parseToJsonElement(exactAdmissionBytes(root, file).toString(Charsets.UTF_8)).jsonObject

private fun readStringMap(root: Path, file: FerocityAdmissionFile): Map<String, String> =
    exactAdmissionJson(root, file).mapValues { (_, value) -> value.jsonPrimitive.content.also(::requireSha256) }

private fun gitOutput(root: Path, vararg args: String): String {
    val process = ProcessBuilder(listOf("git", "-C", root.toString()) + args).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    require(process.waitFor() == 0) { "Cannot verify source repository identity" }
    return output.trimEnd()
}

/**
 * Source M may have later evidence-only publication commits N. Inspect every reachable
 * intervening commit, including each merge-parent diff, so a changed-then-reverted engine
 * file is rejected even when the final M..HEAD tree comparison would be empty.
 */
internal fun verifyFerocitySourcePublication(root: Path, sourceCommit: String, compiledPaths: Set<String>): String {
    val observedHead = gitOutput(root, "rev-parse", "HEAD")
    gitOutput(root, "merge-base", "--is-ancestor", sourceCommit, observedHead)
    val commits = gitOutput(root, "rev-list", "$sourceCommit..$observedHead").lineSequence().filter { it.isNotBlank() }
    commits.forEach { commit ->
        val changed = gitOutput(root, "diff-tree", "--root", "-m", "--no-commit-id", "--name-only", "--no-renames", "-r", "-z", commit)
            .split('\u0000').filter { it.isNotEmpty() }
        require(changed.none { isFerocityCompiledInput(it, compiledPaths) }) {
            "Intervening publication commit changes a compiled/build input: $commit"
        }
    }
    val entries = gitOutput(root, "status", "--porcelain=v1", "-z", "--untracked-files=all").split('\u0000')
    var index = 0
    while (index < entries.size) {
        val entry = entries[index++]
        if (entry.isEmpty()) continue
        require(entry.length >= 4)
        val names = mutableListOf(entry.drop(3))
        if (entry.take(2).any { it == 'R' || it == 'C' }) names += entries[index++]
        require(names.none { isFerocityCompiledInput(it, compiledPaths) }) { "Uncommitted compiled source or build input is not admitted" }
    }
    return observedHead
}

private fun isFerocityCompiledInput(name: String, known: Set<String>): Boolean =
    name in known || ("/src/" in name && (name.endsWith(".kt") || name.endsWith(".java") || "/resources/" in name)) ||
        name.endsWith(".gradle.kts") || name.endsWith(".gradle") || name.startsWith("gradle/") ||
        name in setOf("gradle.properties", "gradlew", "gradlew.bat", "justfile", "scripts/test-class", "scripts/gradle-locked")

internal fun verifyDevelopmentGates(root: Path, gates: List<FerocityQualificationGate>, sourceMap: Map<String, String>): Set<FerocityQualifiedClass> {
    val passed = mutableSetOf<FerocityQualifiedClass>()
    gates.forEach { gate ->
        val receipt = exactAdmissionJson(root, gate.receipt)
        require(receipt.getValue("schema").jsonPrimitive.content == "ferocity-recycling-real-engine-qualification-v1")
        require(receipt.getValue("status").jsonPrimitive.content == "PASS" && receipt.getValue("compiled_inputs_unchanged").jsonPrimitive.boolean)
        exactAdmissionBytes(root, gate.validator)
        require(receipt.getValue("archived_runner_sha256").jsonPrimitive.content == gate.validator.sha256 &&
            receipt.getValue("runner_sha256").jsonPrimitive.content == gate.validator.sha256) {
            "Qualification validator differs from its preserved source"
        }
        require(receipt.getValue("compiled_inputs_before_sha256").jsonPrimitive.content == gate.compiledInputs.sha256 &&
            receipt.getValue("compiled_inputs_after_sha256").jsonPrimitive.content == gate.compiledInputs.sha256)
        val compiled = readStringMap(root, gate.compiledInputs)
        require(compiled.isNotEmpty() && compiled.all { (name, sha) -> sourceMap[name] == sha }) { "Qualification source version differs" }
        val stages = receipt.getValue("stages").jsonArray.map { it.jsonObject }
        gate.classes.forEach { (name, count) ->
            val stage = stages.single { name in it.getValue("class_totals").jsonObject }
            require(stage.keys.containsAll(setOf("test_task_has_execution_marker", "xml_freshness", "raw_xml_totals",
                "fresh_test_task_totals", "unaccepted_xml_totals", "class_fresh_test_task_totals"))) {
                "Qualification predates the corrected task and XML timestamp guard"
            }
            require(stage.getValue("status").jsonPrimitive.content == "PASS" &&
                stage.getValue("exit_status").jsonPrimitive.int == 0 && stage.getValue("test_task_executed").jsonPrimitive.boolean &&
                stage.getValue("test_task_has_execution_marker").jsonPrimitive.boolean)
            require(stage.getValue("xml_capture_or_parse_errors").jsonArray.isEmpty())
            val log = gate.logs.getValue(name)
            require(stage.getValue("log_sha256").jsonPrimitive.content == log.sha256)
            val logText = exactAdmissionBytes(root, log).toString(Charsets.UTF_8)
            val task = ":" + stage.getValue("module").jsonPrimitive.content.replace('/', ':') + ":test"
            val statuses = Regex("^> Task " + Regex.escape(task) + "(?: ([^\\r\\n]*))?\\r?$", RegexOption.MULTILINE)
                .findAll(logText).map { it.groupValues[1] }.toList()
            require(statuses.isNotEmpty() && statuses.all { it.isEmpty() } && statuses ==
                stage.getValue("observed_test_task_statuses").jsonArray.map { it.jsonPrimitive.content }) {
                "The preserved log does not show an actually executed passing test task"
            }
            val rawTotals = stage.getValue("raw_xml_totals").jsonObject
            val totalKeys = setOf("tests", "failures", "errors", "skipped")
            require(rawTotals.keys == totalKeys && rawTotals.getValue("tests").jsonPrimitive.int > 0)
            (totalKeys - "tests").forEach { require(rawTotals.getValue(it).jsonPrimitive.int == 0) }
            require(rawTotals == stage.getValue("fresh_test_task_totals").jsonObject)
            val unaccepted = stage.getValue("unaccepted_xml_totals").jsonObject
            require(unaccepted.keys == totalKeys && unaccepted.values.all { it.jsonPrimitive.int == 0 })
            val totals = stage.getValue("class_totals").jsonObject.getValue(name).jsonObject
            require(totals == stage.getValue("class_fresh_test_task_totals").jsonObject.getValue(name).jsonObject)
            require(totals.getValue("tests").jsonPrimitive.int == count)
            listOf("failures", "errors", "skipped").forEach { require(totals.getValue(it).jsonPrimitive.int == 0) }
            val source = stage.getValue("class_sources").jsonObject.getValue(name).jsonObject
            require(compiled[source.getValue("path").jsonPrimitive.content] == source.getValue("sha256").jsonPrimitive.content)
            // The independently reviewed source-bound receipt names its preserved original XML.
            val xml = stage.getValue("test_xml").jsonArray.map { it.jsonObject }.single {
                it.getValue("name").jsonPrimitive.content.endsWith(".$name.xml")
            }
            val xmlRef = gate.xmlFiles.getValue(name)
            require(xmlRef.sha256 == xml.getValue("sha256").jsonPrimitive.content)
            val timestamp = verifyAdmissionXml(exactAdmissionBytes(root, xmlRef), name, count)
            val freshness = stage.getValue("xml_freshness").jsonArray.map { it.jsonObject }.single {
                it.getValue("name") == xml.getValue("name")
            }
            require(freshness.getValue("within_invocation_window").jsonPrimitive.boolean &&
                freshness.getValue("timestamp").jsonPrimitive.content == timestamp &&
                freshness.getValue("totals").jsonObject == totals)
            val started = admissionTimestamp(stage.getValue("started_at_utc").jsonPrimitive.content)
            val finished = admissionTimestamp(stage.getValue("finished_at_utc").jsonPrimitive.content)
            val recorded = admissionTimestamp(timestamp)
            require(started <= recorded && recorded <= finished) {
                "Preserved XML timestamp is outside the actual invocation; restored output is not fresh evidence"
            }
            passed += FerocityQualifiedClass(gate.id, name)
        }
    }
    return passed
}

private fun verifyDevelopmentWatchdog(root: Path, watchdog: FerocityDevelopmentWatchdog) {
    exactAdmissionBytes(root, watchdog.source)
    val accepted = exactAdmissionJson(root, watchdog.acceptance)
    val run = exactAdmissionJson(root, watchdog.runReceipt)
    val author = exactAdmissionJson(root, watchdog.authorReceipt)
    val claim = exactAdmissionJson(root, watchdog.processClaim)
    val acceptedDirectory = requireNotNull(Path.of(watchdog.acceptance.path).parent)
    require(accepted.getValue("files").jsonArray.any { entry ->
        val file = entry.jsonObject
        acceptedDirectory.resolve(file.getValue("path").jsonPrimitive.content).toString() == watchdog.processClaim.path &&
            file.getValue("sha256").jsonPrimitive.content == watchdog.processClaim.sha256
    }) { "Watchdog process identity is not among the accepted preserved fixtures" }
    require(claim.getValue("schema_version").jsonPrimitive.int == 1 &&
        claim.getValue("scope").jsonPrimitive.content == "PROCESS_SUPERVISION_ONLY")
    require(claim.getValue("supervisor_source_sha256").jsonPrimitive.content == watchdog.source.sha256)
    require(claim.getValue("python_executable").jsonPrimitive.content == watchdog.pythonExecutable &&
        claim.getValue("python_executable_sha256").jsonPrimitive.content == watchdog.pythonExecutableSha256) {
        "The current supervisor Python differs from its actual qualified process identity"
    }
    require(run.getValue("author_receipt_sha256").jsonPrimitive.content == watchdog.authorReceipt.sha256)
    require(author.getValue("owned_files").jsonArray.any { entry ->
        val file = entry.jsonObject
        file.getValue("path").jsonPrimitive.content == watchdog.source.path &&
            file.getValue("sha256").jsonPrimitive.content == watchdog.source.sha256
    }) { "Watchdog source is not the tested author boundary" }
    require(accepted.getValue("run_receipt_sha256").jsonPrimitive.content == watchdog.runReceipt.sha256)
    require(accepted.getValue("source_guard_unchanged").jsonPrimitive.boolean && run.getValue("source_guard_unchanged").jsonPrimitive.boolean)
    require(run.getValue("returncode").jsonPrimitive.int == 0)
    val totals = accepted.getValue("fixed_process_cases").jsonObject
    require(totals.getValue("executed").jsonPrimitive.int == 9 && totals.getValue("passed").jsonPrimitive.int == 9)
    listOf("failed", "errors", "skipped").forEach { require(totals.getValue(it).jsonPrimitive.int == 0) }
    require(ferocityFileSha256(Path.of(watchdog.pythonExecutable)) == watchdog.pythonExecutableSha256)
}

/** The receipt's original XML is checked too; a copied PASS field is insufficient. */
internal fun verifyAdmissionXml(bytes: ByteArray, className: String, count: Int): String {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    val suite = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    require(suite.tagName == "testsuite" && suite.getAttribute("name").endsWith(".$className"))
    require(suite.getAttribute("tests").toInt() == count)
    listOf("failures", "errors", "skipped").forEach { require(suite.getAttribute(it).toInt() == 0) }
    require(suite.getElementsByTagName("testcase").length == count)
    listOf("failure", "error", "skipped").forEach { require(suite.getElementsByTagName(it).length == 0) }
    return suite.getAttribute("timestamp").also { admissionTimestamp(it) }
}

private fun admissionTimestamp(value: String): Instant = runCatching { OffsetDateTime.parse(value).toInstant() }
    .getOrElse { throw IllegalArgumentException("Qualification timestamp requires an explicit time zone: $value", it) }

internal val requiredDevelopmentClassCounts = mapOf(
    "FerocityObservationBoundaryTest" to 28, "FerocityStackSourceObservationTest" to 12,
    "FerocityTriggerOrderObservationTest" to 6,
    "FerocityTrialJournalTest" to 20, "FerocityCardDefinitionBundleTest" to 16,
    "FerocityInlineTokenProvenanceTest" to 12,
    "ArtifactControlPolicyTest" to 24, "RedMadnessPilotScenarioTest" to 24,
    "FerocityDevelopmentAdmissionTest" to 14,
)

internal fun verifyDevelopmentPolicyInputs(root: Path, policies: Map<String, FerocityDevelopmentPolicy>, sourceMap: Map<String, String>) {
    policies.values.forEach { policy -> policy.files.forEach { (name, sha) ->
        require(sourceMap[name] == sha) { "Policy/helper was not among exact compiled inputs: $name" }
        exactAdmissionBytes(root, FerocityAdmissionFile(name, sha))
    } }
}

/** Called only after full verification in production; fixed fixtures supply a counting provider. */
internal fun reserveFerocityDevelopmentLedger(
    directory: Path,
    admissionSha256: String,
    provideStreams: () -> List<FerocityDevelopmentSeedRow>,
): String {
    requireSha256(admissionSha256)
    require(directory.isAbsolute && Files.isDirectory(directory.parent))
    require(directory.parent.toRealPath() == directory.parent)
    Files.createDirectory(directory) // Existing or partial cells cannot invoke the provider again.
    FileChannel.open(directory.parent, StandardOpenOption.READ).use { it.force(true) }
    ferocityWriteNew(directory.resolve("ALLOCATION_INTENT.json"), buildJsonObject {
        put("admissionSha256", admissionSha256)
        put("cellId", FEROCITY_FIRST_CELL)
        put("allocations", JsonArray(firstFerocityDevelopmentAllocations().map { JsonPrimitive(it.protocolId) }))
        put("entropyCalls", 48)
        put("replacementsAuthorized", 0)
    }.toString().toByteArray(Charsets.UTF_8))
    try {
        val ledger = FerocityDevelopmentLedger(admissionSha256 = admissionSha256, rows = provideStreams())
        // Preserve colliding/invalid generated rows before rejecting; a broken allocation is not rerolled.
        val digest = ferocityWriteNewJson(directory.resolve("ledger.json"), FerocityDevelopmentLedger.serializer(), ledger)
        validateFerocityDevelopmentLedger(ledger, admissionSha256)
        return digest
    } catch (error: Exception) {
        try {
            ferocityWriteNew(directory.resolve("ALLOCATION_FAILURE.json"), buildJsonObject {
                put("type", error.javaClass.name)
                put("message", error.message)
                put("status", "UNRESOLVED_ALLOCATION_PRESERVE_NO_RETRY")
            }.toString().toByteArray(Charsets.UTF_8))
        } catch (writeFailure: Exception) {
            error.addSuppressed(writeFailure) // An evidence disk failure must not erase the original fault.
        }
        throw error
    }
}

internal fun requireFerocityResearchOrigins(bundle: FerocityDefinitionBundle) {
    bundle.definitionsInRegistrationOrder.forEach { entry ->
        val card = FerocityJournalCodec.restore(com.wingedsheep.sdk.model.CardDefinition.serializer(), entry.definition)
        val expected = when (card.name) {
            "Ferocity of the Hunt" -> FerocityDefinitionOriginKind.VERIFIED_TEXT_PRERELEASE
            "Clue", "Blood", "Wicked Role" -> FerocityDefinitionOriginKind.PREDEFINED_TOKEN
            else -> FerocityDefinitionOriginKind.QUALIFIED_CANONICAL
        }
        require(entry.origin.kind == expected) { "Definition provenance is unqualified or mislabeled: ${card.name}" }
    }
}

internal fun verifyDevelopmentCoverage(
    coverage: Map<String, List<FerocityQualifiedClass>>,
    required: Set<String>,
    passed: Set<FerocityQualifiedClass>,
) {
    require(coverage.keys == required) { "First-cell coverage is incomplete or changes the admitted pool" }
    coverage.forEach { (capability, refs) ->
        require(refs.isNotEmpty() && refs.distinct().size == refs.size && refs.all { it in passed }) {
            "Coverage lacks executed passing assertions: $capability"
        }
    }
}
