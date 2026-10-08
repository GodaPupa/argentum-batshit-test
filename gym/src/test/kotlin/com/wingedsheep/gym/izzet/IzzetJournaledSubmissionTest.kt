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
class IzzetJournaledSubmissionTest : FunSpec({
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

    fun pin(dir: java.nio.file.Path) = Files.readString(dir.resolve("initialized.sha256")).trim()
    fun inspect(dir: java.nio.file.Path) = IzzetJournaledSubmission.inspect(dir, identity, pin(dir), registry)
    test("real submission sees forced exact intent and result is durable before return") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val state = initial()
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { state }
        val result = runner.executeBoundary({ choose(it, "PlayLand") }) { before, action ->
            val obj = Json.parseToJsonElement(Files.readString(dir.resolve("0.intent"))).jsonObject
            obj.getValue("sequence").jsonPrimitive.int shouldBe 0
            Files.exists(dir.resolve("0.result")) shouldBe false
            Files.exists(dir.resolve("submission-consumed")) shouldBe true
            inspect(dir) shouldBe "UNRESOLVED_INTENT"
            processor.process(before, action).result
        }
        result.result shouldBe processor.process(state, result.action).result
        Files.exists(dir.resolve("submission-complete")) shouldBe true
        inspect(dir) shouldBe "ACCEPTED"
        shouldThrowAny { runner.executeOnce { choose(it, "PassPriority") } }
    }
    test("production path durably accepts keep mulligan and pass") {
        for ((opening, kind) in listOf(true to "KeepHand", true to "TakeMulligan", false to "PassPriority")) {
            val root = root(); val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial(opening) }
            runner.executeOnce { choose(it, kind) }.result.error shouldBe null
            inspect(root.resolve(identity.attemptId)) shouldBe "ACCEPTED"
        }
    }
    test("injected rejection persists unchanged state and distinct rejected result without retry") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        shouldThrowAny { runner.executeBoundary({ choose(it, "PlayLand") }) { before, _ ->
            ExecutionResult.error(before, "excluded synthetic rejection")
        } }
        inspect(dir) shouldBe "REJECTED"
        shouldThrowAny { runner.executeOnce { choose(it, "PlayLand") } }
    }
    test("malformed rejection remains invalid and cannot promote state") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        shouldThrowAny { runner.executeBoundary({ choose(it, "PlayLand") }) { before, _ ->
            ExecutionResult.error(before.copy(turnNumber = 99), "excluded malformed rejection")
        } }
        inspect(dir) shouldBe "INVALID_REJECTION"
        shouldThrowAny { runner.executeOnce { choose(it, "PlayLand") } }
    }
    test("interruption retains unresolved intent and neither writer nor attempt reopens") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        shouldThrowAny { runner.executeBoundary({ choose(it, "PlayLand") }) { _, _ -> error("synthetic interruption") } }
        inspect(dir) shouldBe "UNRESOLVED_INTENT"
        Files.exists(dir.resolve("0.result")) shouldBe false
        shouldThrowAny { runner.executeOnce { choose(it, "PlayLand") } }
        var calls = 0
        shouldThrowAny { IzzetJournaledSubmission.create(root, identity, registry) { calls++; initial() } }
        calls shouldBe 0
    }
    test("result persistence collision consumes runner and preserves intent") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        Files.createDirectory(dir.resolve("0.result"))
        shouldThrowAny { runner.executeOnce { choose(it, "PlayLand") } }
        Files.exists(dir.resolve("0.intent")) shouldBe true
        Files.exists(dir.resolve("submission-complete")) shouldBe false
        shouldThrowAny { inspect(dir) }
        shouldThrowAny { runner.executeOnce { choose(it, "PassPriority") } }
    }
    test("stale proposal and policy fault consume without engine intent") {
        for (policyFault in listOf(false, true)) {
            val root = root(); val dir = root.resolve(identity.attemptId)
            val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
            shouldThrowAny { runner.executeOnce {
                if (policyFault) error("synthetic policy fault") else IzzetNumberedProposal("0".repeat(64), 0)
            } }
            inspect(dir) shouldBe "NO_SUBMISSION_RECORDED"
            shouldThrowAny { runner.executeOnce { choose(it, "PlayLand") } }
        }
    }
    test("identity initial pin altered action and duplicate journal rows fail inspection") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        runner.executeOnce { choose(it, "PlayLand") }
        shouldThrowAny { IzzetJournaledSubmission.inspect(dir, identity.copy(pins = pins.copy(runtimeSha256 = "9".repeat(64))), pin(dir), registry) }
        shouldThrowAny { IzzetJournaledSubmission.inspect(dir, identity, "0".repeat(64), registry) }
        val original = Files.readString(dir.resolve("0.intent"))
        val obj = Json.parseToJsonElement(original).jsonObject
        Files.writeString(dir.resolve("0.intent"), JsonObject(obj + ("offerId" to JsonPrimitive(Int.MAX_VALUE))).toString())
        shouldThrowAny { inspect(dir) }
        Files.writeString(dir.resolve("0.intent"), original)
        Files.copy(dir.resolve("0.intent"), dir.resolve("1.intent"))
        shouldThrowAny { inspect(dir) }
    }
})
