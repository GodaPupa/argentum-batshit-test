package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonPrimitive

/** Parameter-free synthetic fixtures only. No Izzet strategy or opponent package is represented. */
class IzzetSyntheticSingleSubmissionTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val processor = ActionProcessor(registry)
    val pins = IzzetSourcePins("1".repeat(40), "2".repeat(64), "3".repeat(64), "4".repeat(64))
    fun initial(opening: Boolean = false): GameState =
        GameInitializer(registry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Synthetic actor", Deck(List(60) { "Forest" })),
                PlayerConfig("Excluded synthetic seat", Deck(List(60) { "Island" }))),
            startingPlayerIndex = 0, skipMulligans = !opening, useHandSmoother = false,
            seed = 0x495A5A45545052L)).state.let {
                if (opening) it else it.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
            }

    fun root() = Files.createTempDirectory("izzet-single-submission-").toRealPath()
    val identity = IzzetSyntheticAttemptIdentity("izzet-synthetic-single", pins)
    fun choose(menu: IzzetPilotMenu, kind: String) = IzzetNumberedProposal(menu.windowSha256,
        menu.offers.first { it.actionKind == kind }.id)

    test("durable barrier precedes initializer and engine submission uses its exact committed state") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        lateinit var original: GameState
        val runner = IzzetSyntheticSingleSubmission.create(root, identity, registry) {
            Files.readAllBytes(dir.resolve("identity.json")).toList() shouldBe identity.bytes().toList()
            Files.readString(dir.resolve("initialization-intent")) shouldBe "INITIALIZE_ONCE\n"
            initial().also { original = it }
        }
        Files.exists(dir.resolve("initialized.sha256")) shouldBe true
        val stored = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            .decodeFromString(GameState.serializer(), Files.readString(dir.resolve("initial.bin")))
        stored shouldBe original
        val submission = runner.executeOnce { choose(it, "PlayLand") }
        submission.action.shouldBeInstanceOf<PlayLand>()
        submission.result shouldBe processor.process(stored, submission.action).result
        submission.result.error shouldBe null
        shouldThrowAny { runner.executeOnce { choose(it, "PassPriority") } }
    }
    test("keep mulligan and pass remain the other admitted single submissions") {
        for ((opening, kind) in listOf(true to "KeepHand", true to "TakeMulligan", false to "PassPriority")) {
            val runner = IzzetSyntheticSingleSubmission.create(root(), identity, registry) { initial(opening) }
            runner.executeOnce { choose(it, kind) }.result.error shouldBe null
        }
    }
    test("wrong window and unknown offer consume the runner before any engine submission") {
        for (proposal in listOf(IzzetNumberedProposal("0".repeat(64), 0), null)) {
            val runner = IzzetSyntheticSingleSubmission.create(root(), identity, registry) { initial() }
            shouldThrowAny { runner.executeOnce { proposal ?: IzzetNumberedProposal(it.windowSha256, Int.MAX_VALUE) } }
            shouldThrowAny { runner.executeOnce { choose(it, "PlayLand") } }
        }
    }
    test("policy exception consumes the runner without issuing a fallback") {
        val runner = IzzetSyntheticSingleSubmission.create(root(), identity, registry) { initial() }
        var calls = 0
        shouldThrowAny { runner.executeOnce { calls++; error("synthetic policy fault") } }
        shouldThrowAny { runner.executeOnce { calls++; choose(it, "PassPriority") } }
        calls shouldBe 1
    }
    test("unsupported action projection fails before policy and cannot be reopened") {
        val s = initial()
        val action = PlayLand(requireNotNull(s.priorityPlayerId), s.getHand(requireNotNull(s.priorityPlayerId)).first())
        val state = processor.process(s, action).result.state
        val runner = IzzetSyntheticSingleSubmission.create(root(), identity, registry) { state }
        var calls = 0
        shouldThrowAny { runner.executeOnce { calls++; choose(it, "PassPriority") } }
        shouldThrowAny { runner.executeOnce { calls++; choose(it, "PassPriority") } }
        calls shouldBe 0
    }
    test("policy sees only masked menu and canonical action binding survives edited detached offers") {
        val s = initial(); val actor = requireNotNull(s.priorityPlayerId)
        val other = s.turnOrder.single { it != actor }
        val runner = IzzetSyntheticSingleSubmission.create(root(), identity, registry) { s }
        val result = runner.executeOnce { menu ->
            val text = menu.maskedObservation + menu.offers.joinToString { it.canonicalAction }
            for (id in s.getHand(other) + s.getLibrary(other) + s.getLibrary(actor))
                text.contains(JsonPrimitive(id.value).toString()) shouldBe false
            text.contains("\"rng\"") shouldBe false
            val edited = menu.copy(offers = menu.offers.map { it.copy(canonicalAction = "illegal", actionKind = "CastSpell") })
            edited.offers.all { it.canonicalAction == "illegal" } shouldBe true
            choose(menu, "PlayLand")
        }
        result.action.shouldBeInstanceOf<PlayLand>()
        result.result.error shouldBe null
    }
    test("existing attempt refuses factory and initializer exception cannot yield a runner") {
        val root = root(); var calls = 0
        shouldThrowAny { IzzetSyntheticSingleSubmission.create(root, identity, registry) { calls++; error("init failed") } }
        shouldThrowAny { IzzetSyntheticSingleSubmission.create(root, identity, registry) { calls++; initial() } }
        calls shouldBe 1
        Files.exists(root.resolve(identity.attemptId).resolve("fault")) shouldBe true
        Files.exists(root.resolve(identity.attemptId).resolve("initialized.sha256")) shouldBe false
    }
})
