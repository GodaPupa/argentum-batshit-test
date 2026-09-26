package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.research.ferocity.FerocityOfTheHuntPrerelease
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Fourteen fixed admission/initialization cases. Synthetic receipts test rejection logic only. */
class FerocityDevelopmentAdmissionTest : FunSpec({
    fun repository(): Path {
        var path = Path.of("").toAbsolutePath().normalize()
        while (!Files.isDirectory(path.resolve("ferocity-recycling/decks"))) {
            path = requireNotNull(path.parent) { "Cannot locate fixed source decks" }
        }
        return path
    }
    val root by lazy { repository() }
    val paths = mapOf(
        "A3-F4" to "ferocity-recycling/decks/candidates/A3-F4.json",
        "A3-N0" to "ferocity-recycling/decks/comparators/A3-N0.json",
        FEROCITY_RED_ID to "ferocity-recycling/decks/benchmarks/$FEROCITY_RED_ID.json",
    )
    fun deckJson(id: String) = exactAdmissionJson(root, FerocityAdmissionFile(paths.getValue(id), firstDeckSourceHashes.getValue(id)))
    fun decks() = paths.mapValues { (id, _) -> readFirstFerocityDeck(deckJson(id), id, firstDeckMainHashes.getValue(id)) }
    fun fixedRows() = firstFerocityDevelopmentAllocations().mapIndexed { i, allocation ->
        FerocityDevelopmentSeedRow(allocation, 202609266000L + i * 3, 202609266001L + i * 3, 202609266002L + i * 3)
    }
    fun bytes(root: Path, name: String, text: String): FerocityAdmissionFile {
        val path = root.resolve(name)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return FerocityAdmissionFile(name, FerocityJournalCodec.sha(text))
    }
    fun fixtureManifest(): FerocityDevelopmentManifest {
        val z = "0".repeat(64)
        val sources = listOf("ArtifactControlPilot.kt", "RedMadnessPilot.kt", "ActorPublicCards.kt", "ActorChoiceSupport.kt", "ArtifactCombatPlanner.kt")
            .associate { "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/$it" to z }
        val classes = requiredDevelopmentClassCounts
        val inline = FerocityAdmissionFile("inline-token.json", z)
        val inlineDependencies = FEROCITY_INLINE_TOKEN_SOURCE_KEYS.associateWith { FerocityAdmissionFile("fixed-inline-${it.substringAfterLast('/')}", z) } +
            (FEROCITY_INLINE_TOKEN_DEPENDENCY to inline)
        return FerocityDevelopmentManifest(
            repositoryPath = "/fixed/repo",
            source = FerocityDevelopmentSource("0".repeat(40), z, FerocityAdmissionFile("inputs.json", z),
                mapOf(FEROCITY_BUNDLE_DEPENDENCY to z, FEROCITY_CLASSPATH_DEPENDENCY to z, FEROCITY_JAVA_DEPENDENCY to z) +
                    inlineDependencies.mapValues { z },
                inlineDependencies, z, listOf(FerocityRuntimePathPin("/fixed/classpath", false, z, emptyMap())), "/fixed/java"),
            protocolFiles = requiredProtocolHashes,
            rulesAudit = FerocityAdmissionFile("ferocity-recycling/RULES_LEGALITY_AUDIT.md", z),
            decks = paths.mapValues { (id, path) -> FerocityAdmissionFile(path, firstDeckSourceHashes.getValue(id)) },
            mainDeckSha256 = firstDeckMainHashes,
            policies = mapOf("artifact-control" to FerocityDevelopmentPolicy("fixed-contract-fixture", sources),
                "red-madness" to FerocityDevelopmentPolicy("fixed-contract-fixture", sources)),
            bundle = FerocityAdmissionFile("bundle.json", z), inlineTokenAdmission = inline,
            cardDefinitionSha256 = mapOf("fixture" to z),
            gates = listOf(FerocityQualificationGate("fixed-schema-fixture", FerocityAdmissionFile("receipt.json", z),
                FerocityAdmissionFile("inputs.json", z), classes, classes.mapValues { FerocityAdmissionFile("${it.key}.xml", z) },
                FerocityAdmissionFile("validator-attempt.py", z), classes.mapValues { FerocityAdmissionFile("${it.key}.log", z) })),
            coverage = emptyMap(),
            watchdog = FerocityDevelopmentWatchdog(FerocityAdmissionFile("watchdog.py", z), FerocityAdmissionFile("acceptance.json", z),
                FerocityAdmissionFile("run.json", z), FerocityAdmissionFile("author.json", z),
                FerocityAdmissionFile("fixed-process-claim.json", z), "/fixed/python", z),
            journalDirectory = "ferocity-recycling/evidence/development/D2-first-cell", ledgerDirectory = "ferocity-recycling/allocations/D2-first-cell",
        )
    }
    data class GateFixture(val root: Path, val gate: FerocityQualificationGate, val inputs: Map<String, String>)
    fun gateFixture(status: String = "PASS", guard: Boolean = true, skipped: Int = 0, xmlFailure: Boolean = false,
        freshFields: Boolean = true, timestamp: String = "2026-09-26T00:00:30Z", logStatus: String = "",
        validatorMismatch: Boolean = false): GateFixture {
        val dir = Files.createTempDirectory("ferocity-admission-fixed-gate-")
        val code = bytes(dir, "src/ContractFixture.kt", "// Fixed verifier fixture, never compiled gameplay.\n")
        val sourceMap = mapOf(code.path to code.sha256)
        val input = bytes(dir, "inputs.json", FerocityJournalCodec.canonical(MapSerializer(String.serializer(), String.serializer()), sourceMap))
        val xml = bytes(dir, "ContractFixture.xml", """<testsuite name="fixed.ContractFixture" timestamp="$timestamp" tests="1" failures="${if (xmlFailure) 1 else 0}" errors="0" skipped="$skipped"><testcase name="a fixed assertion" classname="fixed.ContractFixture">${if (xmlFailure) "<failure message=\"preserved failure\"/>" else ""}${if (skipped > 0) "<skipped/>" else ""}</testcase></testsuite>""")
        val validator = bytes(dir, "validator-attempt.py", "# Fixed receipt-verifier input, never an actual qualification runner.\n")
        val log = bytes(dir, "build.log", "> Task :gym:test${if (logStatus.isEmpty()) "" else " $logStatus"}\n")
        val totals = buildJsonObject { put("tests", 1); put("failures", 0); put("errors", 0); put("skipped", skipped) }
        val zero = buildJsonObject { listOf("tests", "failures", "errors", "skipped").forEach { put(it, 0) } }
        val stage = buildJsonObject {
            put("status", status); put("exit_status", 0); put("test_task_executed", true)
            put("module", "gym"); put("log_sha256", log.sha256)
            put("observed_test_task_statuses", JsonArray(listOf(JsonPrimitive(logStatus))))
            put("started_at_utc", "2026-09-26T00:00:00+00:00"); put("finished_at_utc", "2026-09-26T00:01:00+00:00")
            put("xml_capture_or_parse_errors", JsonArray(emptyList()))
            put("class_totals", buildJsonObject { put("ContractFixture", totals) })
            put("class_sources", buildJsonObject { put("ContractFixture", buildJsonObject { put("path", code.path); put("sha256", code.sha256) }) })
            put("test_xml", JsonArray(listOf(buildJsonObject { put("name", "TEST-fixed.ContractFixture.xml"); put("sha256", xml.sha256) })))
            if (freshFields) {
                put("test_task_has_execution_marker", true)
                put("raw_xml_totals", totals); put("fresh_test_task_totals", totals); put("unaccepted_xml_totals", zero)
                put("class_fresh_test_task_totals", buildJsonObject { put("ContractFixture", totals) })
                // Deliberately copied true even for negative timestamps: the verifier must read XML itself.
                put("xml_freshness", JsonArray(listOf(buildJsonObject {
                    put("name", "TEST-fixed.ContractFixture.xml"); put("timestamp", timestamp)
                    put("within_invocation_window", true); put("totals", totals)
                })))
            }
        }
        val receipt = bytes(dir, "receipt.json", buildJsonObject {
            put("schema", "ferocity-recycling-real-engine-qualification-v1"); put("status", status)
            put("compiled_inputs_unchanged", guard)
            put("compiled_inputs_before_sha256", input.sha256); put("compiled_inputs_after_sha256", input.sha256)
            put("archived_runner_sha256", validator.sha256)
            put("runner_sha256", if (validatorMismatch) "f".repeat(64) else validator.sha256)
            put("stages", JsonArray(listOf(stage)))
        }.toString())
        return GateFixture(dir, FerocityQualificationGate("fixed-logic", receipt, input, mapOf("ContractFixture" to 1),
            mapOf("ContractFixture" to xml), validator, mapOf("ContractFixture" to log)), sourceMap)
    }
    fun assertInitial(seat: String): Pair<CardRegistry, com.wingedsheep.engine.core.GameConfig> {
        val sourceDecks = decks()
        val registry = CardRegistry()
        val selected = sourceDecks.values.flatten().toSet() - FerocityOfTheHuntPrerelease.name
        registerFirstCellCanonicalCards(registry, selected)
        registry.register(FerocityOfTheHuntPrerelease)
        val row = fixedRows().first { it.allocation.deckId == "A3-F4" && it.allocation.seat == seat }
        val config = configuredFirstFerocityGame(row, sourceDecks)
        config.players.map { it.deck.cards.size } shouldBe listOf(60, 60)
        config.startingHandSize shouldBe 7
        config.skipMulligans shouldBe false
        config.useHandSmoother shouldBe false
        config.teams shouldBe null
        config.players.all { it.commanderCardName == null } shouldBe true
        val initialized = GameInitializer(registry).initializeGame(config)
        initialized.seed shouldBe row.gameSeed
        initialized.state.activePlayerId shouldBe if (seat == "play") FEROCITY_CANDIDATE_PLAYER else FEROCITY_RED_PLAYER
        initialized.state.turnNumber shouldBe 1
        initialized.playerIds shouldBe listOf(FEROCITY_CANDIDATE_PLAYER, FEROCITY_RED_PLAYER)
        initialized.playerIds.forEachIndexed { index, id ->
            initialized.state.getEntity(id)!!.get<LifeTotalComponent>()!!.life shouldBe 20
            initialized.state.getHand(id).size shouldBe 7
            initialized.state.getLibrary(id).size shouldBe 53
            val mulligan = initialized.state.getEntity(id)!!.get<MulliganStateComponent>()!!
            mulligan.hasKept shouldBe false
            mulligan.mulligansTaken shouldBe 0
            mulligan.freeMulligan shouldBe false
            val inventory = (initialized.state.getHand(id) + initialized.state.getLibrary(id))
                .map { initialized.state.getEntity(it)!!.get<CardComponent>()!!.name }.groupingBy { it }.eachCount()
            inventory shouldBe config.players[index].deck.cards.groupingBy { it }.eachCount()
        }
        println("FIXED_D2_ADMISSION_INITIALIZATION seat=$seat seed=${row.gameSeed}; no development trial claimed")
        return registry to config
    }

    test("01 exact archived main sixties initialize the candidate on the play") {
        decks().keys shouldBe setOf("A3-F4", "A3-N0", FEROCITY_RED_ID)
        assertInitial("play")
    }
    test("02 the draw allocation uses real seven-card London setup and the explicit opponent starter") {
        val (registry, config) = assertInitial("draw")
        val hash = "0".repeat(64)
        val pins = FerocitySourcePins("0".repeat(40), hash, mapOf("fixed" to hash), mapOf("fixed" to hash),
            mapOf("fixed" to hash), registry.allCardNames().associateWith { FerocityJournalCodec.card(registry.requireCard(it)).canonicalSha256 },
            hash, hash, hash, hash)
        val spec = FerocityTrialSpec("ferocity-recycling/fixed/admission", "explicit-draw-seat", "explicit-draw-seat",
            FerocityTrialStage.DETERMINISTIC_FIXTURE, requireNotNull(config.seed),
            mapOf(FEROCITY_CANDIDATE_PLAYER.value to 71L, FEROCITY_RED_PLAYER.value to 72L), pins)
        val keep = FerocityPilot { input -> ActorProposal(input.bindingHash,
            com.wingedsheep.engine.core.KeepHand(input.actorId), input.policyRngState) }
        val dir = Files.createTempDirectory("ferocity-admission-fixed-draw-journal-")
        val result = FerocityTrialRunner(registry, pins).runConfiguredFixture(dir, spec, config,
            FerocityTrialLimits(1, 150, 300000), mapOf(FEROCITY_CANDIDATE_PLAYER to keep, FEROCITY_RED_PLAYER to keep))
        result.reason shouldBe FerocityStopReason.CAP_ACTIONS
        result.submittedActions shouldBe 1
        val journal = readFerocityJournal(dir, spec.namespace, spec.trialId)
        val start = requireNotNull(journal.initialized)
        start.playerIds shouldBe listOf(FEROCITY_CANDIDATE_PLAYER, FEROCITY_RED_PLAYER)
        FerocityJournalCodec.state(start.state).turnOrder shouldBe listOf(FEROCITY_RED_PLAYER, FEROCITY_CANDIDATE_PLAYER)
        journal.claim.spec.stage shouldBe FerocityTrialStage.DETERMINISTIC_FIXTURE
    }
    test("03 the sixteen allocation identities preserve the protocol namespace without aliases") {
        val rows = firstFerocityDevelopmentAllocations()
        rows.size shouldBe 16
        rows.map { it.protocolId }.distinct().size shouldBe 16
        rows.groupingBy { it.deckId to it.seat }.eachCount().values.toSet() shouldBe setOf(4)
        val m = fixtureManifest()
        validateFerocityDevelopmentShape(m)
        val ledger = FerocityDevelopmentLedger(admissionSha256 = "0".repeat(64), rows = fixedRows())
        ledger.rows.forEach { row ->
            val spec = FerocityTrialSpec(FEROCITY_D2_NAMESPACE, row.allocation.protocolId, row.allocation.protocolId,
                FerocityTrialStage.DEVELOPMENT, row.gameSeed, mapOf(FEROCITY_CANDIDATE_PLAYER.value to row.candidatePolicySeed,
                    FEROCITY_RED_PLAYER.value to row.redPolicySeed), m.toPins("0".repeat(64), "1".repeat(64)))
            spec.trialId shouldBe spec.allocationId
            spec.namespace shouldBe "ferocity-recycling/v0.1/D2"
            spec.allocationId.startsWith("FEROCITY_RECYCLING/v0.1/D2/") shouldBe true
        }
    }
    test("04 evaluation confirmation altered cells and replacement allocations are refused") {
        val m = fixtureManifest()
        listOf(FerocityTrialStage.EVALUATION, FerocityTrialStage.CONFIRMATION, FerocityTrialStage.DETERMINISTIC_FIXTURE).forEach { stage ->
            shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(stage = stage)) }
        }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(cellId = "D3")) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(replacementsAuthorized = 1)) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(allocationIds = m.allocationIds + m.allocationIds.first())) }
    }
    test("05 changed source bytes and a different qualification source map fail exact identity") {
        val f = gateFixture()
        verifyDevelopmentGates(f.root, listOf(f.gate), f.inputs) shouldBe setOf(FerocityQualifiedClass("fixed-logic", "ContractFixture"))
        shouldThrow<IllegalArgumentException> { verifyDevelopmentGates(f.root, listOf(f.gate), f.inputs.mapValues { "f".repeat(64) }) }
        val code = f.inputs.entries.single()
        Files.writeString(f.root.resolve(code.key), "changed source")
        shouldThrow<IllegalArgumentException> { exactAdmissionBytes(f.root, FerocityAdmissionFile(code.key, code.value)) }
        val repo = Files.createTempDirectory("ferocity-admission-fixed-publication-").toRealPath()
        fun git(vararg args: String): String {
            val process = ProcessBuilder(listOf("git", "-C", repo.toString(), "-c", "user.name=Ferocity Fixed Fixture",
                "-c", "user.email=ferocity-fixed-fixture@example.invalid", "-c", "commit.gpgSign=false",
                "-c", "core.hooksPath=/dev/null") + args).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }
            return output.trim()
        }
        git("init", "--initial-branch=fixture")
        val rule = "rules-engine/src/main/kotlin/fixed/Rules.kt"
        val original = "// Fixed publication graph; this file is never compiled.\n"
        bytes(repo, rule, original)
        bytes(repo, "evidence.json", "{\"scope\":\"fixed\"}\n")
        git("add", "."); git("commit", "-m", "fixed source M")
        val sourceCommit = git("rev-parse", "HEAD")
        Files.writeString(repo.resolve("evidence.json"), "{\"scope\":\"fixed evidence-only N\"}\n")
        git("add", "."); git("commit", "-m", "fixed evidence-only publication N")
        verifyFerocitySourcePublication(repo, sourceCommit, setOf(rule)) shouldBe git("rev-parse", "HEAD")
        git("checkout", "-b", "transient-engine-edit")
        Files.writeString(repo.resolve(rule), "// A real changed input, later reverted.\n")
        shouldThrow<IllegalArgumentException> { verifyFerocitySourcePublication(repo, sourceCommit, setOf(rule)) }
        git("add", "."); git("commit", "-m", "fixed disallowed compiled change")
        Files.writeString(repo.resolve(rule), original)
        git("add", "."); git("commit", "-m", "fixed reverted compiled change")
        val nonAncestor = git("rev-parse", "HEAD")
        git("checkout", "fixture")
        shouldThrow<IllegalArgumentException> { verifyFerocitySourcePublication(repo, nonAncestor, setOf(rule)) }
        git("merge", "--no-ff", "transient-engine-edit", "-m", "fixed merge with reverted engine history")
        git("diff", "--name-only", sourceCommit, "HEAD", "--", rule) shouldBe ""
        shouldThrow<IllegalArgumentException> { verifyFerocitySourcePublication(repo, sourceCommit, setOf(rule)) }
    }
    test("06 failed skipped stale-source and forged-green XML receipts cannot qualify") {
        listOf(gateFixture(status = "FAIL"), gateFixture(guard = false), gateFixture(skipped = 1), gateFixture(xmlFailure = true),
            gateFixture(freshFields = false), gateFixture(timestamp = "2026-09-25T00:00:30Z"),
            gateFixture(timestamp = "2026-09-26T00:00:30"), gateFixture(logStatus = "FROM-CACHE"),
            gateFixture(logStatus = "UP-TO-DATE"), gateFixture(validatorMismatch = true)).forEach { f ->
            shouldThrow<IllegalArgumentException> { verifyDevelopmentGates(f.root, listOf(f.gate), f.inputs) }
        }
        val f = gateFixture()
        Files.delete(f.root.resolve(f.gate.xmlFiles.getValue("ContractFixture").path))
        shouldThrow<IllegalArgumentException> { verifyDevelopmentGates(f.root, listOf(f.gate), f.inputs) }
    }
    test("07 altered deck counts names duplicate entries and source identities are refused") {
        val json = deckJson("A3-F4")
        val rows = json.getValue("main").jsonArray
        val first = rows.first().jsonObject
        val changed = JsonObject(json + ("main" to JsonArray(listOf(JsonObject(first + ("count" to JsonPrimitive(5)))) + rows.drop(1))))
        shouldThrow<IllegalArgumentException> { readFirstFerocityDeck(changed, "A3-F4", firstDeckMainHashes.getValue("A3-F4")) }
        val duplicate = JsonObject(json + ("main" to JsonArray(rows + rows.first())))
        shouldThrow<IllegalArgumentException> { readFirstFerocityDeck(duplicate, "A3-F4", firstDeckMainHashes.getValue("A3-F4")) }
        val renamed = JsonObject(json + ("main" to JsonArray(listOf(JsonObject(first + ("name" to JsonPrimitive("Deadly Dispute")))) + rows.drop(1))))
        shouldThrow<IllegalArgumentException> { readFirstFerocityDeck(renamed, "A3-F4", firstDeckMainHashes.getValue("A3-F4")) }
        val m = fixtureManifest()
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(decks = m.decks + ("A3-F4" to m.decks.getValue("A3-F4").copy(sha256 = "f".repeat(64))))) }
    }
    test("08 policy helper dependency and serializer source mismatches cannot enter the verified pool") {
        val dir = Files.createTempDirectory("ferocity-admission-fixed-policy-")
        val code = bytes(dir, "src/Policy.kt", "// Fixed policy-identity assertion; not a pilot.\n")
        val policy = mapOf("fixed" to FerocityDevelopmentPolicy("fixture", mapOf(code.path to code.sha256)))
        verifyDevelopmentPolicyInputs(dir, policy, mapOf(code.path to code.sha256))
        shouldThrow<IllegalArgumentException> { verifyDevelopmentPolicyInputs(dir, policy, mapOf(code.path to "e".repeat(64))) }
        val m = fixtureManifest()
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(source = m.source.copy(dependencies = m.source.dependencies + (FEROCITY_BUNDLE_DEPENDENCY to "d".repeat(64))))) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(source = m.source.copy(dependencies = m.source.dependencies + ("ferocity/card-source/unbound" to "d".repeat(64))))) }
    }
    test("09 missing inline-token and replay qualification cannot be represented as a completed admission") {
        val m = fixtureManifest()
        val qualified = FerocityQualifiedClass("fixed", "RealTokenBoundary")
        val coverage = requiredFirstCellMechanics.associateWith { listOf(qualified) }
        verifyDevelopmentCoverage(coverage, requiredFirstCellMechanics, setOf(qualified))
        shouldThrow<IllegalArgumentException> {
            verifyDevelopmentCoverage(coverage - "mechanic:inline-fish-provenance", requiredFirstCellMechanics, setOf(qualified))
        }
        shouldThrow<IllegalArgumentException> { verifyDevelopmentCoverage(coverage, requiredFirstCellMechanics, emptySet()) }
        val source = FerocityDefinitionOrigin(FerocityDefinitionOriginKind.DETERMINISTIC_FIXTURE, "ferocity/card-source/fixed", "0".repeat(64))
        val definition = FerocityArchivedDefinition(FerocityJournalCodec.card(FerocityOfTheHuntPrerelease), source)
        val bundle = FerocityDefinitionBundle(1, "0".repeat(40), "0".repeat(64), "0".repeat(64), listOf(definition), emptyMap(), emptyMap())
        shouldThrow<IllegalArgumentException> { requireFerocityResearchOrigins(bundle) }
        requireFerocityResearchOrigins(bundle.copy(definitionsInRegistrationOrder = listOf(definition.copy(
            origin = source.copy(kind = FerocityDefinitionOriginKind.VERIFIED_TEXT_PRERELEASE)))))
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(gates = emptyList())) }
        val gate = m.gates.single()
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(m.copy(gates = listOf(gate.copy(classes = gate.classes - "FerocityCardDefinitionBundleTest", xmlFiles = gate.xmlFiles - "FerocityCardDefinitionBundleTest", logs = gate.logs - "FerocityCardDefinitionBundleTest")))) }
        val dir = Files.createTempDirectory("ferocity-admission-fixed-token-")
        val path = dir.resolve("admission.json")
        val digest = ferocityWriteNewJson(path, FerocityDevelopmentManifest.serializer(), m)
        // Synthetic shape has no real qualified inputs: even the well-formed document cannot create a capability.
        shouldThrow<Exception> { VerifiedFerocityDevelopment.verify(dir, path, digest) }
        Files.exists(dir.resolve(m.ledgerDirectory)) shouldBe false
    }
    test("10 wrong admission hash unknown fields and duplicate JSON fields fail before admission") {
        val dir = Files.createTempDirectory("ferocity-admission-fixed-schema-")
        val m = fixtureManifest()
        val path = dir.resolve("admission.json")
        val digest = ferocityWriteNewJson(path, FerocityDevelopmentManifest.serializer(), m)
        shouldThrow<IllegalArgumentException> { ferocityReadExactJson(path, "f".repeat(64), FerocityDevelopmentManifest.serializer()) }
        val raw = Files.readString(path)
        val unknown = "{\"stageOverride\":\"CONFIRMATION\"," + raw.drop(1)
        val extra = dir.resolve("unknown.json"); Files.writeString(extra, unknown)
        shouldThrow<Exception> { ferocityReadExactJson(extra, FerocityJournalCodec.sha(unknown), FerocityDevelopmentManifest.serializer()) }
        val duplicate = "{\"schemaVersion\":1," + raw.drop(1)
        val dup = dir.resolve("duplicate.json"); Files.writeString(dup, duplicate)
        shouldThrow<Exception> { ferocityReadExactJson(dup, FerocityJournalCodec.sha(duplicate), FerocityDevelopmentManifest.serializer()) }
        ferocityReadExactJson(path, digest, FerocityDevelopmentManifest.serializer()) shouldBe m
    }
    test("11 rejected verification creates neither entropy reservation ledger nor trial claim") {
        val dir = Files.createTempDirectory("ferocity-admission-fixed-no-entropy-")
        val m = fixtureManifest()
        val path = dir.resolve("admission.json")
        ferocityWriteNewJson(path, FerocityDevelopmentManifest.serializer(), m)
        shouldThrow<Exception> { FerocityDevelopmentCli.main(arrayOf("allocate", dir.toString(), path.toString(), "f".repeat(64))) }
        Files.exists(dir.resolve(m.ledgerDirectory)) shouldBe false
        Files.exists(dir.resolve(m.journalDirectory)) shouldBe false
        Files.list(dir).use { it.count() } shouldBe 1L
    }
    test("12 the durable ledger reserves once before stream generation and binds without self-reference") {
        val dir = Files.createTempDirectory("ferocity-admission-fixed-ledger-").resolve("cell")
        val admission = "a".repeat(64)
        var calls = 0
        val hash = reserveFerocityDevelopmentLedger(dir, admission) {
            calls++
            Files.isRegularFile(dir.resolve("ALLOCATION_INTENT.json")) shouldBe true
            Files.exists(dir.resolve("ledger.json")) shouldBe false
            fixedRows()
        }
        calls shouldBe 1
        val original = Files.readString(dir.resolve("ledger.json"))
        val ledger = ferocityReadExactJson(dir.resolve("ledger.json"), hash, FerocityDevelopmentLedger.serializer())
        validateFerocityDevelopmentLedger(ledger, admission)
        original.contains("seedLedgerSha256") shouldBe false
        original.contains("cardDefinitionSha256") shouldBe false
        shouldThrow<Exception> { reserveFerocityDevelopmentLedger(dir, admission) { calls++; fixedRows() } }
        calls shouldBe 1
        Files.readString(dir.resolve("ledger.json")) shouldBe original
    }
    test("13 changed or duplicate ledger rows streams and admission identities are rejected without reroll") {
        val admission = "a".repeat(64)
        val ledger = FerocityDevelopmentLedger(admissionSha256 = admission, rows = fixedRows())
        validateFerocityDevelopmentLedger(ledger, admission)
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentLedger(ledger, "b".repeat(64)) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentLedger(ledger.copy(rows = ledger.rows.dropLast(1)), admission) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentLedger(ledger.copy(rows = ledger.rows.dropLast(1) + ledger.rows.first()), admission) }
        val collided = ledger.rows.mapIndexed { i, row -> if (i == 1) row.copy(gameSeed = ledger.rows.first().gameSeed) else row }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentLedger(ledger.copy(rows = collided), admission) }
        val dir = Files.createTempDirectory("ferocity-admission-fixed-collision-").resolve("cell")
        shouldThrow<IllegalArgumentException> { reserveFerocityDevelopmentLedger(dir, admission) { collided } }
        Files.isRegularFile(dir.resolve("ledger.json")) shouldBe true
        Files.isRegularFile(dir.resolve("ALLOCATION_FAILURE.json")) shouldBe true
        shouldThrow<Exception> { reserveFerocityDevelopmentLedger(dir, admission) { error("No reroll may happen") } }
    }
    test("14 the CLI refuses unsupervised incomplete changed-stage and cap-override commands") {
        val hash = "a".repeat(64)
        val row = firstFerocityDevelopmentAllocations().first().protocolId
        val valid = arrayOf("run-one", "/fixed/repo", "/fixed/admission.json", hash, hash, row, "/fixed/supervisor/claim.json")
        parseFerocityDevelopmentCommand(valid).allocationId shouldBe row
        shouldThrow<IllegalArgumentException> { parseFerocityDevelopmentCommand(valid.dropLast(1).toTypedArray()) }
        shouldThrow<IllegalArgumentException> { parseFerocityDevelopmentCommand(valid + arrayOf("--max-actions", "1")) }
        shouldThrow<IllegalArgumentException> { parseFerocityDevelopmentCommand(valid.copyOf().also { it[0] = "confirmation" }) }
        shouldThrow<IllegalArgumentException> { parseFerocityDevelopmentCommand(valid.copyOf().also { it[5] = row.replace("/D2/", "/E/") }) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(fixtureManifest().copy(limits = FerocityTrialLimits(1, 150, 300000))) }
        shouldThrow<IllegalArgumentException> { validateFerocityDevelopmentShape(fixtureManifest().copy(watchdog = fixtureManifest().watchdog.copy(wallSeconds = 301))) }
        runFixedDevelopmentSupervisorHandshake(root, fixtureManifest())
    }
})

/** Case14's real child. It can verify process facts but has no D2 capability or gameplay entry. */
object FerocityDevelopmentSupervisorFixture {
    @JvmStatic
    fun main(args: Array<String>) {
        val command = parseFerocityDevelopmentCommand(args)
        val m = ferocityReadExactJson(command.admissionPath, command.admissionSha256, FerocityDevelopmentManifest.serializer())
        val context = FerocityDevelopmentSupervisorContext(command.repository, m,
            admissionPath(command.repository, m.journalDirectory),
            admissionPath(command.repository, m.ledgerDirectory).resolve("ledger.json"),
            FerocityDevelopmentSupervisorFixture::class.java.name)
        verifyDevelopmentSupervisorHandshake(command, context)
        checkFixedSupervisorProcessFacts()
        val claimPath = requireNotNull(command.supervisorClaimPath)
        val original = Files.readAllBytes(claimPath)
        val claim = FerocityJournalCodec.json.parseToJsonElement(original.toString(Charsets.UTF_8)).jsonObject
        val spec = claim.getValue("spec").jsonObject
        fun mustReject(label: String, changed: JsonObject) {
            val text = FerocityJournalCodec.canonical(JsonElement.serializer(), changed)
            // Only this temporary, non-game fixture alters its own claim. Preserve every negative
            // byte sequence, then restore the original before the qualified watchdog finalizes.
            Files.writeString(claimPath.parent.resolve("fixed-negative-$label.json"), text)
            try {
                Files.writeString(claimPath, text)
                val rejected = runCatching { verifyDevelopmentSupervisorHandshake(command, context) }.exceptionOrNull()
                check(rejected is IllegalArgumentException) { "Tampered $label unexpectedly passed: $rejected" }
            } finally {
                Files.write(claimPath, original)
            }
        }
        mustReject("spec-hash", JsonObject(claim + ("spec_sha256" to JsonPrimitive("0".repeat(64)))))
        fun changedSpec(value: JsonObject): JsonObject = JsonObject(claim + mapOf(
            "spec" to value, "spec_sha256" to JsonPrimitive(FerocityJournalCodec.sha(
                FerocityJournalCodec.canonical(JsonElement.serializer(), value)))))
        val argv = spec.getValue("argv").jsonArray.toMutableList().also { it[1] = JsonPrimitive("-Xmx1024m") }
        mustReject("argv", changedSpec(JsonObject(spec + ("argv" to JsonArray(argv)))))
        mustReject("wall", changedSpec(JsonObject(spec + ("wall_seconds" to JsonPrimitive(301)))))
        mustReject("python", JsonObject(claim + ("python_executable_sha256" to JsonPrimitive("f".repeat(64)))))
        verifyDevelopmentSupervisorHandshake(command, context)
        check(!Files.exists(FerocityTrialJournal.journalPath(context.journalRoot, FEROCITY_D2_NAMESPACE, command.allocationId!!)))
        println("FIXED_SUPERVISOR_HANDSHAKE_PASSED valid=1 rejected=4 gameplay=0")
    }
}

/** Existing case14 also rejects contradictory process facts; no synthetic fact reaches admission. */
private fun checkFixedSupervisorProcessFacts() {
    val facts = readFerocitySupervisorProcessFacts()
    val available = captureFerocityJvmProcessFacts()
    validateFerocityJvmProcessFacts(facts, available)
    val unavailable = FerocityJvmProcessFacts(facts.child.identity.namespacePids.last(), null, null, null, null, null)
    validateFerocityJvmProcessFacts(facts, unavailable)
    fun rejects(block: () -> Unit) {
        check(runCatching(block).exceptionOrNull() is IllegalArgumentException) { "Contradictory process facts were accepted" }
    }
    rejects { validateFerocityJvmProcessFacts(facts, unavailable.copy(pid = unavailable.pid + 1)) }
    rejects { validateFerocityProcessOwnership(facts.copy(child = facts.child.copy(
        identity = facts.child.identity.copy(parentPid = facts.child.identity.pid)))) }
    rejects { validateFerocityProcessOwnership(facts.copy(parent = facts.parent.copy(
        identity = facts.parent.identity.copy(userIds = facts.parent.identity.userIds.map { it + 1 })))) }
    rejects { requireSameFerocitySupervisorProcesses(facts, facts.copy(child = facts.child.copy(
        identity = facts.child.identity.copy(startTicks = facts.child.identity.startTicks + 1)))) }
    rejects { requireSameFerocitySupervisorProcesses(facts, facts.copy(parent = facts.parent.copy(
        argv = facts.parent.argv + "unapproved-argument"))) }
    // Present but conflicting JVM fields must fail even when the independent proc facts are good.
    rejects { validateFerocityJvmProcessFacts(facts, unavailable.copy(command = facts.parent.executable)) }
    rejects { validateFerocityJvmProcessFacts(facts, unavailable.copy(arguments = facts.child.argv.drop(1) + "unapproved-argument")) }
    rejects { validateFerocityJvmProcessFacts(facts, unavailable.copy(parentPid = facts.parent.identity.namespacePids.last() + 1)) }
    println("FIXED_PROCESS_FACTS_PASSED valid_proc_and_optional_java=1 rejected=8 gameplay=0")
}

/** One real supervised process inside existing case14, with no engine initialization or seeds. */
private fun runFixedDevelopmentSupervisorHandshake(repository: Path, fixture: FerocityDevelopmentManifest) {
    val evidenceParent = repository.resolve("ferocity-recycling/evidence/fixed/development-admission")
    Files.createDirectories(evidenceParent)
    val dir = Files.createTempDirectory(evidenceParent, "supervisor-handshake-").toRealPath()
    val watchdogSource = repository.resolve("ferocity-recycling/tools/trial_watchdog.py")
    ferocityFileSha256(watchdogSource) shouldBe "3c48f16ac3db1921fe572a9d10c30c22b5665fe0a5fcf66a0bae66d4d6cc233e"
    val watchdog = dir.resolve("watchdog.py")
    Files.copy(watchdogSource, watchdog)
    val python = System.getenv("CODEX_PRIMARY_RUNTIME_PYTHON")?.takeIf { it.isNotBlank() }?.let { Path.of(it).toRealPath() }
        ?: run {
            val discovery = ProcessBuilder("python3", "-c", "import os, sys; print(os.path.realpath(sys.executable))")
                .redirectErrorStream(true).start()
            try {
                check(discovery.waitFor(5, TimeUnit.SECONDS)) { "Cannot resolve the fixed fixture Python executable" }
                val result = discovery.inputStream.bufferedReader().readText().trim()
                check(discovery.exitValue() == 0) { result }
                Path.of(result).toRealPath()
            } finally { if (discovery.isAlive) discovery.destroyForcibly() }
        }
    // The portable fixed process case captures this actual binary. Production independently
    // requires the exact binary named and hashed in the accepted nine-case watchdog receipts.
    val javaPath = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath()
    val javaSha = ferocityFileSha256(javaPath)
    val classPath = currentFerocityClassPath()
    val bundle = dir.resolve("bundle.json")
    Files.writeString(bundle, "{\"scope\":\"FIXED_SUPERVISOR_INPUT_NO_CARD_DEFINITIONS\"}")
    val ledger = admissionPath(dir, fixture.ledgerDirectory).resolve("ledger.json")
    Files.createDirectories(ledger.parent)
    Files.writeString(ledger, "{\"scope\":\"FIXED_SUPERVISOR_INPUT_NO_GAME_SEEDS\"}")
    // This synthetic shape never passes VerifiedFerocityDevelopment. The process-only verifier
    // uses its real executable/argv/input paths; zero classpath digests are not qualification.
    val m = fixture.copy(repositoryPath = dir.toString(),
        source = fixture.source.copy(javaExecutable = javaPath.toString(),
            classPath = classPath.map { FerocityRuntimePathPin(it.toString(), Files.isDirectory(it), "0".repeat(64), emptyMap()) },
            dependencies = fixture.source.dependencies + (FEROCITY_JAVA_DEPENDENCY to javaSha)),
        bundle = FerocityAdmissionFile("bundle.json", ferocityFileSha256(bundle)),
        watchdog = fixture.watchdog.copy(source = FerocityAdmissionFile("watchdog.py", ferocityFileSha256(watchdog)),
            pythonExecutable = python.toString(), pythonExecutableSha256 = ferocityFileSha256(python)))
    val admission = dir.resolve("admission.json")
    val admissionSha = ferocityWriteNewJson(admission, FerocityDevelopmentManifest.serializer(), m)
    val ledgerSha = ferocityFileSha256(ledger)
    val row = firstFerocityDevelopmentAllocations().first().protocolId
    val runId = "D2-" + FerocityJournalCodec.sha(row).take(24)
    val output = admissionPath(dir, m.supervisorDirectory)
    val claimPath = output.resolve(runId).resolve("claim.json")
    val argv = listOf(javaPath.toString(), "-Xmx2048m", "-cp", classPath.joinToString(java.io.File.pathSeparator),
        FerocityDevelopmentSupervisorFixture::class.java.name, "run-one", dir.toString(), admission.toString(),
        admissionSha, ledgerSha, row, claimPath.toString())
    val spec = buildJsonObject {
        put("schema_version", 1); put("run_id", runId); put("argv", JsonArray(argv.map(::JsonPrimitive)))
        put("cwd", dir.toString()); put("wall_seconds", 300); put("term_grace_seconds", 5)
        put("journal_path", FerocityTrialJournal.journalPath(admissionPath(dir, m.journalDirectory), FEROCITY_D2_NAMESPACE, row).toString())
        put("supervisor_sha256", m.watchdog.source.sha256); put("inspection_limit_bytes", 32 * 1024 * 1024)
        put("pinned_files", JsonObject(mapOf(javaPath.toString() to javaSha, admission.toString() to admissionSha,
            ledger.toString() to ledgerSha, bundle.toString() to m.bundle.sha256).mapValues { JsonPrimitive(it.value) }))
    }
    val specPath = dir.resolve("watchdog-spec.json")
    Files.writeString(specPath, FerocityJournalCodec.canonical(JsonElement.serializer(), spec))
    val supervisorLog = dir.resolve("supervisor.log")
    val process = ProcessBuilder(python.toString(), watchdog.toString(), "--spec", specPath.toString(), "--output-root", output.toString())
        .directory(dir.toFile()).redirectErrorStream(true).redirectOutput(supervisorLog.toFile()).start()
    try {
        check(process.waitFor(45, TimeUnit.SECONDS)) { "Fixed supervisor handshake timed out; preserved at $dir" }
        check(process.exitValue() == 0) { "Fixed supervisor handshake failed: ${Files.readString(supervisorLog)}; preserved at $dir" }
    } finally {
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }
    val childOutput = Files.readString(claimPath.parent.resolve("stdout.log"))
    check("FIXED_SUPERVISOR_HANDSHAKE_PASSED valid=1 rejected=4 gameplay=0" in childOutput) { childOutput }
    check("FIXED_PROCESS_FACTS_PASSED valid_proc_and_optional_java=1 rejected=8 gameplay=0" in childOutput) { childOutput }
    val result = FerocityJournalCodec.json.parseToJsonElement(Files.readString(supervisorLog).trim()).jsonObject
    result.getValue("returncode").jsonPrimitive.int shouldBe 0
    result.getValue("input_pins_unchanged").jsonPrimitive.boolean shouldBe true
    result.getValue("game_outcome") shouldBe JsonNull
    result.getValue("new_gameplay_games").jsonPrimitive.int shouldBe 0
    Files.exists(admissionPath(dir, m.journalDirectory)) shouldBe false
    println("FIXED_SUPERVISOR_HANDSHAKE_EVIDENCE $dir; no development allocation or trial journal")
}
