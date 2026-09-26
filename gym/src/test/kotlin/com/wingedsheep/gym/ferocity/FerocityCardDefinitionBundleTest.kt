package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.research.ferocity.FerocityOfTheHuntPrerelease
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Sixteen finite persistence/runtime fixtures. Synthetic admission values never authorize research. */
class FerocityCardDefinitionBundleTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val fixtureSeed = 2026092651L
    private val digest = FerocityJournalCodec.sha("definition-bundle-fixed-fixture-not-research-admission")
    private val sourceCommit = "2e7e78653ed1c7e56fc609b993a79b68ecca33c6"
    private val fixture = card("Ferocity Exact Bundle Activation Fixture") {
        manaCost = "{1}"
        typeLine = "Creature — Construct"
        power = 1
        toughness = 1
        activatedAbility { cost = Costs.Tap; effect = Effects.GainLife(1) }
    }
    private val evidenceRoot by lazy {
        var repo = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(repo.resolve("settings.gradle.kts"))) repo = requireNotNull(repo.parent)
        val base = repo.resolve("ferocity-recycling/evidence/card-definition-bundle")
        Files.createDirectories(base)
        Files.createTempDirectory(base, "fixed-").also { println("FEROCITY_BUNDLE_FIXED_EVIDENCE=$it") }
    }
    private val classPath by lazy { currentFerocityClassPath().map(::captureFerocityRuntimePath) }
    private val javaExecutable by lazy { Path.of(System.getProperty("java.home"), "bin", "java").toRealPath() }

    init {
        cardRegistry.register(fixture)

        test("exact archived definition preserves generated abilities and every serialized field") {
            val bundle = makeBundle()
            val restored = restoreFerocityDefinitions(bundle, pins(bundle))
            val actual = restored.registry.requireCard(fixture.name)
            FerocityJournalCodec.card(actual) shouldBe FerocityJournalCodec.card(fixture)
            actual.script.activatedAbilities.single().id shouldBe fixture.script.activatedAbilities.single().id
            val before = FerocityJournalCodec.card(actual)
            repeat(19) { AbilityId.generate() }
            FerocityJournalCodec.card(restoreFerocityDefinitions(bundle, pins(bundle)).registry.requireCard(fixture.name)) shouldBe before
        }

        test("all natural and canonical printing aliases match after explicitly ordered variants") {
            val first = fixture.copy(name = "Ferocity Bundle Printing Fixture", setCode = "FX",
                metadata = fixture.metadata.copy(collectorNumber = "1"))
            val second = first.copy(metadata = first.metadata.copy(collectorNumber = "2"))
            val bundle = makeBundle(listOf(first, second))
            val restored = restoreFerocityDefinitions(bundle, pins(bundle))
            restored.registry.requireCard(first.name) shouldBe second
            restored.registry.requireCard("${first.name}#FX-1") shouldBe first
            restored.registry.requireCard("${first.name}#FX-2") shouldBe second
            bundle.registryBindings.size shouldBe 3
        }

        test("external spelling alias resolves explicitly without manufacturing another card") {
            val named = fixture.copy(name = "Ferocity Lórien Alias Fixture")
            val alias = "Ferocity Lorien Alias Fixture"
            val bundle = makeBundle(listOf(named), mapOf(alias to named.name))
            val restored = restoreFerocityDefinitions(bundle, pins(bundle))
            restored.resolveDeckName(alias) shouldBe named.name
            restored.registry.getCard(alias) shouldBe null
            restored.registry.allCardNames() shouldBe setOf(named.name)
            shouldThrow<IllegalArgumentException> { restored.resolveDeckName("Unlisted spelling") }
        }

        test("predefined Clue Blood Treasure and Wicked Role are exact sources and missing tokens never fall back") {
            val tokens = listOf(PredefinedTokens.Clue, PredefinedTokens.Blood, PredefinedTokens.Treasure, PredefinedTokens.WickedRole)
            val bundle = makeBundle(tokens, kind = FerocityDefinitionOriginKind.PREDEFINED_TOKEN)
            val restored = restoreFerocityDefinitions(bundle, pins(bundle))
            tokens.forEach { token -> FerocityJournalCodec.card(restored.registry.requireCard(token.name)) shouldBe FerocityJournalCodec.card(token) }
            restored.registry.getCard("Food") shouldBe null
            restored.registry.getCard(fixture.name) shouldBe null
            shouldThrow<IllegalArgumentException> { restored.registry.requireCard("Food") }
        }

        test("shared prerelease Ferocity is archived without a duplicate definition or generated ID replacement") {
            val bundle = makeBundle(listOf(FerocityOfTheHuntPrerelease), kind = FerocityDefinitionOriginKind.VERIFIED_TEXT_PRERELEASE)
            val restored = restoreFerocityDefinitions(bundle, pins(bundle))
            val actual = restored.registry.requireCard("Ferocity of the Hunt")
            FerocityJournalCodec.card(actual) shouldBe FerocityJournalCodec.card(FerocityOfTheHuntPrerelease)
            actual.script.triggeredAbilities.single().id shouldBe FerocityOfTheHuntPrerelease.script.triggeredAbilities.single().id
            bundle.definitionsInRegistrationOrder.single().origin.kind shouldBe FerocityDefinitionOriginKind.VERIFIED_TEXT_PRERELEASE
        }

        test("changed raw definition bytes or digests reject without normalization") {
            val bundle = makeBundle()
            val entry = bundle.definitionsInRegistrationOrder.single()
            listOf(entry.definition.copy(wireJson = entry.definition.wireJson + " "),
                entry.definition.copy(canonicalSha256 = "0".repeat(64))).forEach { broken ->
                shouldThrow<IllegalArgumentException> {
                    restoreFerocityDefinitions(bundle.copy(definitionsInRegistrationOrder = listOf(entry.copy(definition = broken))), pins(bundle))
                }
            }
        }

        test("unknown bundle fields origin enum and effect subtype reject rather than default") {
            val bundle = makeBundle()
            val base = FerocityJournalCodec.json.encodeToJsonElement(FerocityDefinitionBundle.serializer(), bundle).jsonObject
            val unknown = JsonObject(base + ("unknownFutureField" to JsonPrimitive(true)))
            val dir = directory("unknown")
            val file = dir.resolve("unknown.json")
            val fileHash = ferocityWriteNewJson(file, JsonElement.serializer(), unknown)
            shouldThrow<Exception> { readFerocityBundle(file, fileHash, pins(bundle, fileHash)) }
            val wire = FerocityJournalCodec.canonical(FerocityDefinitionBundle.serializer(), bundle)
            shouldThrow<Exception> {
                FerocityJournalCodec.json.decodeFromString(FerocityDefinitionBundle.serializer(), wire.replace("DETERMINISTIC_FIXTURE", "UNKNOWN_SOURCE_KIND"))
            }
            val entry = bundle.definitionsInRegistrationOrder.single()
            val cardJson = FerocityJournalCodec.json.parseToJsonElement(entry.definition.wireJson).jsonObject
            val script = cardJson.getValue("script").jsonObject
            val changedCard = JsonObject(cardJson + ("script" to JsonObject(script +
                ("spellEffect" to JsonObject(mapOf("type" to JsonPrimitive("UnqualifiedFutureEffect")))))))
            val changed = FerocityJournalCodec.payload(JsonElement.serializer(), changedCard)
            shouldThrow<Exception> {
                restoreFerocityDefinitions(bundle.copy(definitionsInRegistrationOrder = listOf(entry.copy(definition = changed))), pins(bundle))
            }
        }

        test("unadmitted or mismatched definition source dependencies reject") {
            val bundle = makeBundle()
            val original = pins(bundle)
            val source = bundle.definitionsInRegistrationOrder.single().origin.dependencyKey
            shouldThrow<IllegalArgumentException> {
                restoreFerocityDefinitions(bundle, original.copy(dependencySha256 = original.dependencySha256 - source))
            }
            shouldThrow<IllegalArgumentException> {
                restoreFerocityDefinitions(bundle, original.copy(dependencySha256 = original.dependencySha256 + (source to "0".repeat(64))))
            }
        }

        test("unknown bundle schema or different source tree commit and serializer reject") {
            val bundle = makeBundle()
            listOf(bundle.copy(schemaVersion = 2), bundle.copy(sourceCommit = "0".repeat(40)),
                bundle.copy(sourceTreeSha256 = "0".repeat(64)), bundle.copy(serializerSha256 = "0".repeat(64))).forEach { wrong ->
                shouldThrow<IllegalArgumentException> { restoreFerocityDefinitions(wrong, pins(bundle)) }
            }
        }

        test("missing extra conflicting and ambiguous lookup bindings reject") {
            val bundle = makeBundle()
            val original = pins(bundle)
            shouldThrow<IllegalArgumentException> { restoreFerocityDefinitions(bundle.copy(registryBindings = emptyMap()), original) }
            val extra = bundle.copy(registryBindings = bundle.registryBindings + ("Invented alias" to digest))
            shouldThrow<IllegalArgumentException> { restoreFerocityDefinitions(extra, pins(extra)) }
            val conflict = bundle.copy(registryBindings = mapOf(fixture.name to "0".repeat(64)))
            shouldThrow<IllegalArgumentException> { restoreFerocityDefinitions(conflict, pins(conflict)) }
            shouldThrow<IllegalArgumentException> { makeBundle(aliases = mapOf(fixture.name to fixture.name)) }
            shouldThrow<IllegalArgumentException> { makeBundle(aliases = mapOf("Alias" to "Another alias", "Another alias" to fixture.name)) }
        }

        test("exclusive archive and receipt writes cannot overwrite existing accepted bytes") {
            val dir = directory("exclusive")
            val bundle = makeBundle()
            val file = dir.resolve("bundle.json")
            val hash = writeFerocityBundle(file, bundle)
            val before = Files.readAllBytes(file).toList()
            shouldThrow<Exception> { writeFerocityBundle(file, bundle.copy(schemaVersion = 2)) }
            Files.readAllBytes(file).toList() shouldBe before
            ferocityFileSha256(file) shouldBe hash
            val output = dir.resolve("receipt.json")
            ferocityWriteNew(output, "fixed existing evidence".toByteArray())
            shouldThrow<Exception> { ferocityWriteNew(output, "replacement".toByteArray()) }
            Files.readString(output) shouldBe "fixed existing evidence"
        }

        test("inline token and undeclared double face expansion fail without synthetic registry cards") {
            shouldThrow<IllegalArgumentException> { makeBundle(listOf(fixture.copy(name = "token:Fish"))) }
            shouldThrow<IllegalArgumentException> { makeBundle(listOf(fixture.copy(backFace = fixture.copy(name = "Unadmitted Back Face")))) }
            shouldThrow<IllegalArgumentException> { makeBundle(listOf(fixture.copy(meldResult = true))) }
            val bundle = makeBundle()
            val restored = restoreFerocityDefinitions(bundle, pins(bundle))
            restored.registry.getCard("token:Fish") shouldBe null
            shouldThrow<IllegalArgumentException> { restored.resolveDeckName("token:Fish") }
        }

        test("runtime classpath artifact or manifest mismatch rejects before replay") {
            val bundle = makeBundle()
            val exact = pins(bundle, paths = classPath)
            val changed = classPath.toMutableList().also { it[0] = it[0].copy(sha256 = "0".repeat(64)) }
            shouldThrow<IllegalArgumentException> { verifyFerocityClassPath(changed, exact) }
            val rehashedManifest = exact.copy(dependencySha256 = exact.dependencySha256 +
                (FEROCITY_CLASSPATH_DEPENDENCY to ferocityClassPathDigest(changed)))
            shouldThrow<IllegalArgumentException> { verifyFerocityClassPath(changed, rehashedManifest) }
        }

        test("external definition pins and whole bundle file digest are independently enforced") {
            val dir = directory("external-pins")
            val bundle = makeBundle()
            val file = dir.resolve("bundle.json")
            val hash = writeFerocityBundle(file, bundle)
            val admitted = pins(bundle, hash)
            shouldThrow<IllegalArgumentException> { readFerocityBundle(file, "0".repeat(64), admitted) }
            shouldThrow<IllegalArgumentException> { readFerocityBundle(file, hash,
                admitted.copy(cardDefinitionSha256 = mapOf(fixture.name to "0".repeat(64)))) }
            Files.writeString(file, " ", java.nio.file.StandardOpenOption.APPEND)
            shouldThrow<IllegalArgumentException> { readFerocityBundle(file, hash, admitted) }
        }

        test("fresh JVM after different AbilityId initialization replays one complete real activation exactly") {
            val data = recordedActivation("fresh-valid")
            val journalPath = FerocityTrialJournal.journalPath(data.root, data.spec.namespace, data.spec.trialId)
            val before = Files.readAllBytes(journalPath).toList()
            val (requestFile, requestHash) = writeRequest(data)
            val exit = child(data.root, requestFile, requestHash)
            exit shouldBe 0
            val resultFile = Path.of(data.request.outputPath)
            val receipt = ferocityReadExactJson(resultFile, ferocityFileSha256(resultFile), FerocityFreshReplayReceipt.serializer())
            receipt.processId shouldNotBe ProcessHandle.current().pid()
            receipt.definitionBindings shouldBe data.bundle.registryBindings
            receipt.replayStatus shouldBe FerocityReplayStatus.VERIFIED_UNRESOLVED.name
            receipt.verifiedSubmissions shouldBe 1
            receipt.recordedEndReason shouldBe FerocityStopReason.CAP_ACTIONS.name
            receipt.recordedWinner shouldBe null
            receipt.newGameplayGames shouldBe 0
            receipt.runtimeInputsUnchanged shouldBe true
            val marker = Files.readAllLines(data.root.resolve("child.stdout")).single { it.startsWith("FIXTURE_ABILITY_COUNTER=") }.substringAfter('=')
            marker shouldNotBe fixture.script.activatedAbilities.single().id.value
            Files.readAllBytes(journalPath).toList() shouldBe before
        }

        test("fresh JVM rejects changed bundle without a replay receipt new claim or journal rewrite") {
            val data = recordedActivation("fresh-corrupt")
            val journalPath = FerocityTrialJournal.journalPath(data.root, data.spec.namespace, data.spec.trialId)
            val before = Files.readAllBytes(journalPath).toList()
            val (requestFile, requestHash) = writeRequest(data)
            Files.writeString(Path.of(data.request.bundlePath), " ", java.nio.file.StandardOpenOption.APPEND)
            child(data.root, requestFile, requestHash) shouldNotBe 0
            Files.exists(Path.of(data.request.outputPath)) shouldBe false
            Files.readAllBytes(journalPath).toList() shouldBe before
            Files.list(data.root.resolve("allocations")).use { it.count() } shouldBe 1L
        }
    }

    private fun directory(label: String): Path = evidenceRoot.resolve(label).also { Files.createDirectory(it) }
    private fun makeBundle(cards: List<CardDefinition> = listOf(fixture), aliases: Map<String, String> = emptyMap(),
                       kind: FerocityDefinitionOriginKind = FerocityDefinitionOriginKind.DETERMINISTIC_FIXTURE): FerocityDefinitionBundle =
        captureFerocityDefinitions(cards.map { it to FerocityDefinitionOrigin(kind, "ferocity/card-source/fixed-$kind", digest) },
            sourceCommit, digest, digest, aliases)

    private fun pins(bundle: FerocityDefinitionBundle, fileHash: String = digest,
                     paths: List<FerocityRuntimePathPin>? = null): FerocitySourcePins {
        val dependencies = bundle.definitionsInRegistrationOrder.associate { it.origin.dependencyKey to it.origin.sourceSha256 }.toMutableMap()
        dependencies[FEROCITY_BUNDLE_DEPENDENCY] = fileHash
        if (paths != null) dependencies[FEROCITY_CLASSPATH_DEPENDENCY] = ferocityClassPathDigest(paths)
        dependencies[FEROCITY_JAVA_DEPENDENCY] = ferocityFileSha256(javaExecutable)
        return FerocitySourcePins(sourceCommit, digest, dependencies, mapOf("fixed-deck" to digest), mapOf("fixed-pilot" to digest),
            bundle.registryBindings, digest, digest, digest, digest)
    }

    private data class ReplayFixture(val root: Path, val bundle: FerocityDefinitionBundle, val spec: FerocityTrialSpec,
                                     val request: FerocityFreshReplayRequest)

    private fun recordedActivation(label: String): ReplayFixture {
        val root = directory(label)
        val bundle = makeBundle()
        val bundlePath = root.resolve("definitions.json")
        val bundleHash = writeFerocityBundle(bundlePath, bundle)
        val pins = pins(bundle, bundleHash, classPath)
        val spec = FerocityTrialSpec("ferocity-recycling/deterministic-fixtures/definition-bundle-v1", label, "fixed-$label",
            FerocityTrialStage.DETERMINISTIC_FIXTURE, fixtureSeed, mapOf(p1.value to 8101L, p2.value to 8202L), pins)
        val state = scenario().withPlayers().withRngSeed(fixtureSeed).withCardOnBattlefield(1, fixture.name).build().state
        val pilot = FerocityPilot { input ->
            val action = input.legalActions.single { it.action is ActivateAbility && !it.isManaAbility }.action
            ActorProposal(input.bindingHash, action, input.policyRngState)
        }
        val summary = FerocityTrialRunner(cardRegistry, pins) { 0L }.runRestoredFixture(root, spec,
            FerocityTrialLimits(1, 10, 1000), mapOf(p1 to pilot, p2 to pilot), "Fixed raw-definition separate-JVM activation replay") {
            FerocityFixtureStart(state, listOf(p1, p2))
        }
        summary.reason shouldBe FerocityStopReason.CAP_ACTIONS
        summary.submittedActions shouldBe 1
        val request = FerocityFreshReplayRequest(1, bundlePath.toString(), bundleHash, pins, classPath, javaExecutable.toString(),
            root.toString(), spec.namespace, spec.trialId, root.resolve("fresh-replay-receipt.json").toString())
        return ReplayFixture(root, bundle, spec, request)
    }

    private fun writeRequest(data: ReplayFixture): Pair<Path, String> {
        val path = data.root.resolve("replay-request.json")
        return path to ferocityWriteNewJson(path, FerocityFreshReplayRequest.serializer(), data.request)
    }

    private fun child(root: Path, requestFile: Path, requestHash: String): Int {
        val command = listOf(javaExecutable.toString(), "-cp", classPath.joinToString(java.io.File.pathSeparator) { it.path },
            "com.wingedsheep.gym.ferocity.FerocityFreshReplayFixtureMain", requestFile.toString(), requestHash)
        ferocityWriteNew(root.resolve("child-command.json"), FerocityJournalCodec.canonical(
            ListSerializer(String.serializer()), command).toByteArray())
        val process = ProcessBuilder(command).directory(root.toFile()).redirectOutput(root.resolve("child.stdout").toFile())
            .redirectError(root.resolve("child.stderr").toFile()).start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            require(process.waitFor(5, TimeUnit.SECONDS)) { "Fixed replay child did not terminate after forced stop" }
            error("Fixed replay child exceeded its predeclared 30-second limit; files preserved at $root")
        }
        ferocityWriteNew(root.resolve("child-exit.txt"), process.exitValue().toString().toByteArray())
        return process.exitValue()
    }
}

/** Deliberately changes only this new process's allocator before archive restoration. */
object FerocityFreshReplayFixtureMain {
    @JvmStatic
    fun main(args: Array<String>) {
        repeat(257) { AbilityId.generate() }
        println("FIXTURE_ABILITY_COUNTER=${AbilityId.generate().value}")
        FerocityFreshReplayMain.main(args)
    }
}
