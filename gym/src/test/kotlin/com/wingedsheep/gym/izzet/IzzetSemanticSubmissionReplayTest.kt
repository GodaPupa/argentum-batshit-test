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
class IzzetSemanticSubmissionReplayTest : FunSpec({
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
    fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun filePin(dir: java.nio.file.Path, name: String) = hash(Files.readAllBytes(dir.resolve(name)))
    fun replay(dir: java.nio.file.Path, intent: String = filePin(dir, "0.intent"), result: String = filePin(dir, "0.result")) =
        IzzetSemanticSubmissionReplay.verify(dir, identity, pin(dir), intent, result, registry)

    test("real land keep mulligan and pass replay full exact engine responses") {
        for ((opening, kind) in listOf(false to "PlayLand", true to "KeepHand", true to "TakeMulligan", false to "PassPriority")) {
            val root = root(); val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial(opening) }
            runner.executeOnce { choose(it, kind) }
            replay(root.resolve(identity.attemptId)) shouldBe "ACCEPTED"
        }
    }
    test("self consistent accepted response forgery fails semantic replay even with newly computed pins") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        IzzetJournaledSubmission.create(root, identity, registry) { initial() }.executeOnce { choose(it, "PlayLand") }
        val obj = Json.parseToJsonElement(Files.readString(dir.resolve("0.result"))).jsonObject
        val response = obj.getValue("response").jsonObject
        val changed = JsonObject(obj + ("response" to JsonObject(response + ("events" to JsonArray(emptyList()))))).toString()
        Files.writeString(dir.resolve("0.result"), changed)
        Files.writeString(dir.resolve("submission-complete"), hash(changed.toByteArray()) + "\n")
        inspect(dir) shouldBe "ACCEPTED"
        shouldThrowAny { replay(dir) }
        Files.readString(dir.resolve("0.result")) shouldBe changed
    }
    test("injected rejection cannot masquerade as the real engine response") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        shouldThrowAny { runner.executeBoundary({ choose(it, "PlayLand") }) { before, _ ->
            ExecutionResult.error(before, "synthetic injected rejection")
        } }
        inspect(dir) shouldBe "REJECTED"
        shouldThrowAny { replay(dir) }
    }
    test("independently retained intent result state and identity pins reject drift") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        IzzetJournaledSubmission.create(root, identity, registry) { initial() }.executeOnce { choose(it, "PlayLand") }
        val intent = filePin(dir, "0.intent"); val result = filePin(dir, "0.result")
        shouldThrowAny { replay(dir, "0".repeat(64), result) }
        shouldThrowAny { replay(dir, intent, "0".repeat(64)) }
        shouldThrowAny { IzzetSemanticSubmissionReplay.verify(dir, identity, "0".repeat(64), intent, result, registry) }
        shouldThrowAny { IzzetSemanticSubmissionReplay.verify(dir, identity.copy(pins = pins.copy(policySha256 = "9".repeat(64))),
            pin(dir), intent, result, registry) }
    }
    test("unresolved unsealed and duplicate evidence fail closed without repairing or resubmitting") {
        for (kind in listOf("unresolved", "unsealed", "duplicate")) {
            val root = root(); val dir = root.resolve(identity.attemptId)
            val runner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
            runner.executeOnce { choose(it, "PlayLand") }
            val intent = filePin(dir, "0.intent"); val result = filePin(dir, "0.result")
            when (kind) {
                "unresolved" -> { Files.delete(dir.resolve("0.result")); Files.delete(dir.resolve("submission-complete")) }
                "unsealed" -> Files.delete(dir.resolve("submission-complete"))
                else -> Files.copy(dir.resolve("0.intent"), dir.resolve("1.intent"))
            }
            shouldThrowAny { replay(dir, intent, result) }
            shouldThrowAny { runner.executeOnce { choose(it, "PassPriority") } }
            if (kind == "unresolved") Files.exists(dir.resolve("0.result")) shouldBe false
        }
    }
    test("accepted result carrying a later fault is not certified") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        IzzetJournaledSubmission.create(root, identity, registry) { initial() }.executeOnce { choose(it, "PlayLand") }
        Files.writeString(dir.resolve("submission-fault"), "synthetic interruption\n")
        inspect(dir) shouldBe "FAULT_AFTER_RESULT"
        shouldThrowAny { replay(dir) }
    }
})
