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
class IzzetRejectedReplayEvidenceTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val processor = ActionProcessor(registry)
    val pins = IzzetSourcePins("7b79018e6631e9a9ce3c9fbaaebd591fd71ba5b3", "2".repeat(64), "3".repeat(64), "4".repeat(64))
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

    fun collect(dir: java.nio.file.Path, i: String = filePin(dir, "0.intent"), r: String = filePin(dir, "0.result")) =
        IzzetReplayEvidence.collect(dir, identity, pin(dir), i, r, registry)
    fun verify(b: ByteArray, dir: java.nio.file.Path, i: String, r: String) =
        IzzetReplayEvidence.verify(b, dir, identity, pin(dir), i, r, registry)
    fun submitted(opening: Boolean = false, kind: String = "PlayLand"): java.nio.file.Path {
        val root = root()
        IzzetJournaledSubmission.create(root, identity, registry) { initial(opening) }.executeOnce { choose(it, kind) }
        return root.resolve(identity.attemptId)
    }
    fun sink() = Files.createTempDirectory("izzet-excluded-collector-").toRealPath()
    fun publish(sink: java.nio.file.Path, dir: java.nio.file.Path, b: ByteArray = collect(dir)) =
        IzzetStoredReplayEvidence.publish(sink, dir, identity, pin(dir), filePin(dir, "0.intent"),
            filePin(dir, "0.result"), b, hash(b), registry)
    fun read(sink: java.nio.file.Path, dir: java.nio.file.Path, i: String, r: String, e: String) =
        IzzetStoredReplayEvidence.verify(sink.resolve(identity.attemptId), dir, identity, pin(dir), i, r, e, registry)

    // Intentionally inconsistent excluded prestate: resolution of a nonexistent stack entity.
    // This is not a reachable game, strategic scenario, admitted opponent, or forged result.
    fun rejectingState(): GameState {
        val s = initial()
        return s.copy(stack = listOf(com.wingedsheep.sdk.model.EntityId("excluded-missing-stack")),
            priorityPassedBy = s.turnOrder.drop(1).toSet())
    }
    fun rejected(): java.nio.file.Path {
        val root = root(); val before = rejectingState()
        val direct = processor.process(before, PassPriority(requireNotNull(before.priorityPlayerId))).result
        direct.error shouldBe "Stack item not found: ${before.stack.single()}"
        direct.state shouldBe before
        direct.events.isEmpty() shouldBe true
        val owner = IzzetJournaledSubmission.create(root, identity, registry) { before }
        shouldThrowAny { owner.executeOnce { choose(it, "PassPriority") } }
        val dir = root.resolve(identity.attemptId)
        // The production owner (not a fake submit seam) must have recorded the genuine response.
        val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
        val result = json.parseToJsonElement(Files.readString(dir.resolve("0.result"))).jsonObject
        result["classification"] shouldBe JsonPrimitive("REJECTED")
        json.decodeFromJsonElement(ExecutionResult.serializer(), result.getValue("response")) shouldBe direct
        Files.exists(dir.resolve("submission-complete")) shouldBe true
        Files.exists(dir.resolve("submission-fault")) shouldBe true
        shouldThrowAny { owner.executeOnce { choose(it, "PassPriority") } }
        return dir
    }
    fun rewriteResult(dir: java.nio.file.Path, change: (JsonObject) -> JsonObject) {
        val obj = Json.parseToJsonElement(Files.readString(dir.resolve("0.result"))).jsonObject
        val b = change(obj).toString().toByteArray()
        Files.write(dir.resolve("0.result"), b)
        Files.writeString(dir.resolve("submission-complete"), hash(b) + "\n")
    }
    test("genuine production engine rejection replays exactly without state or event promotion") {
        val dir = rejected()
        inspect(dir) shouldBe "REJECTED"
        replay(dir) shouldBe "REJECTED"
    }
    test("genuine rejected collector persists and verifies without converting rejection to acceptance") {
        val dir = rejected(); val b = collect(dir)
        Json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject["classification"] shouldBe JsonPrimitive("REJECTED")
        val sink = sink(); val destination = publish(sink, dir, b)
        read(sink, dir, filePin(dir, "0.intent"), filePin(dir, "0.result"), hash(b))
        Files.readAllBytes(destination.resolve("evidence.json")).toList() shouldBe b.toList()
        shouldThrowAny { publish(sink, dir, b) }
    }
    test("resealed changed error message fails real semantic rejection replay") {
        val dir = rejected()
        rewriteResult(dir) { obj ->
            val response = obj.getValue("response").jsonObject
            JsonObject(obj + ("response" to JsonObject(response + ("error" to JsonPrimitive("invented rejection")))))
        }
        val original = Files.readAllBytes(dir.resolve("0.result")).toList()
        shouldThrowAny { replay(dir) }
        shouldThrowAny { collect(dir) }
        Files.readAllBytes(dir.resolve("0.result")).toList() shouldBe original
    }
    test("rejected collector cannot be relabeled accepted even with a recomputed collector hash") {
        val dir = rejected(); val b = collect(dir)
        val obj = Json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject
        val bad = JsonObject(obj + ("classification" to JsonPrimitive("ACCEPTED"))).toString().toByteArray()
        val sink = sink()
        shouldThrowAny { publish(sink, dir, bad) }
        Files.exists(sink.resolve(identity.attemptId).resolve("complete.sha256")) shouldBe false
        shouldThrowAny { publish(sink, dir, b) }
    }
    test("missing rejected result defeats stored collector and cannot authorize resubmission") {
        val dir = rejected(); val b = collect(dir); val sink = sink()
        val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result")
        publish(sink, dir, b)
        Files.delete(dir.resolve("0.result"))
        shouldThrowAny { read(sink, dir, i, r, hash(b)) }
        shouldThrowAny { IzzetJournaledSubmission.create(dir.parent, identity, registry) { rejectingState() } }
        Files.exists(dir.resolve("0.result")) shouldBe false
    }
    test("policy harness failure has no engine result and cannot count as genuine rejection") {
        val root = root()
        val owner = IzzetJournaledSubmission.create(root, identity, registry) { initial() }
        shouldThrowAny { owner.executeOnce { error("excluded policy harness failure") } }
        val dir = root.resolve(identity.attemptId)
        inspect(dir) shouldBe "NO_SUBMISSION_RECORDED"
        Files.exists(dir.resolve("0.intent")) shouldBe false
        Files.exists(dir.resolve("0.result")) shouldBe false
        shouldThrowAny { collect(dir) }
        shouldThrowAny { owner.executeOnce { choose(it, "PassPriority") } }
    }
})
