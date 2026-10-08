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
class IzzetReplayEvidenceTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val processor = ActionProcessor(registry)
    val pins = IzzetSourcePins("bdee5c704b5d6fcf26535ab0aa16e6ae9cf9b2c3", "2".repeat(64), "3".repeat(64), "4".repeat(64))
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
    test("all four admitted synthetic action schemas produce exact reference-only projections") {
        for ((opening, kind) in listOf(false to "PlayLand", true to "KeepHand", true to "TakeMulligan", false to "PassPriority")) {
            val dir = submitted(opening, kind); val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result")
            val b = collect(dir, i, r); verify(b, dir, i, r)
            val obj = Json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject
            obj.keys shouldBe setOf("schema", "executionAuthorized", "authenticatedProvenance", "identitySha256",
                "initialStateSha256", "originalIntentSha256", "originalResultSha256", "sequence", "classification")
            obj.getValue("classification").jsonPrimitive.content shouldBe "ACCEPTED"
            obj.getValue("sequence").jsonPrimitive.int shouldBe 0
            obj.getValue("executionAuthorized").jsonPrimitive.boolean shouldBe false
            obj.getValue("authenticatedProvenance").jsonPrimitive.boolean shouldBe false
            collect(dir, i, r).toList() shouldBe b.toList()
        }
    }
    test("changed original references identity sequence classification and authority reject") {
        val dir = submitted(); val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result")
        val obj = Json.parseToJsonElement(collect(dir, i, r).toString(Charsets.UTF_8)).jsonObject
        for (key in obj.keys) shouldThrowAny {
            verify(JsonObject(obj + (key to JsonPrimitive("substituted"))).toString().toByteArray(), dir, i, r)
        }
    }
    test("previous projection cannot conceal unresolved unsealed or duplicate journal evidence") {
        for (kind in listOf("unresolved", "unsealed", "duplicate")) {
            val dir = submitted(); val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result"); val b = collect(dir, i, r)
            when (kind) {
                "unresolved" -> Files.delete(dir.resolve("0.result"))
                "unsealed" -> Files.delete(dir.resolve("submission-complete"))
                else -> Files.copy(dir.resolve("0.intent"), dir.resolve("1.intent"))
            }
            shouldThrowAny { verify(b, dir, i, r) }
            if (kind == "unresolved") Files.exists(dir.resolve("0.result")) shouldBe false
        }
    }
    test("locally resealed false result is refused before any collector bytes can be returned") {
        val dir = submitted()
        val obj = Json.parseToJsonElement(Files.readString(dir.resolve("0.result"))).jsonObject
        val response = obj.getValue("response").jsonObject
        val changed = JsonObject(obj + ("response" to JsonObject(response + ("events" to JsonArray(emptyList()))))).toString()
        Files.writeString(dir.resolve("0.result"), changed)
        Files.writeString(dir.resolve("submission-complete"), hash(changed.toByteArray()) + "\n")
        inspect(dir) shouldBe "ACCEPTED"
        shouldThrowAny { collect(dir) }
        Files.readString(dir.resolve("0.result")) shouldBe changed
    }
    test("independent original pins and source identity cannot be replaced at collection") {
        val dir = submitted(); val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result")
        shouldThrowAny { collect(dir, "0".repeat(64), r) }
        shouldThrowAny { collect(dir, i, "0".repeat(64)) }
        shouldThrowAny { IzzetReplayEvidence.collect(dir, identity.copy(pins = pins.copy(sourceCommit = "9".repeat(40))),
            pin(dir), i, r, registry) }
    }
    test("noncanonical duplicate and private fields reject with original bytes preserved") {
        val dir = submitted(); val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result"); val b = collect(dir, i, r)
        val obj = Json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject
        val raw = b.toString(Charsets.UTF_8)
        for (bad in listOf(" " + raw, raw.dropLast(1) + ",\"sequence\":0}",
            JsonObject(obj + ("privateState" to JsonPrimitive("forged"))).toString())) {
            shouldThrowAny { verify(bad.toByteArray(), dir, i, r) }
        }
        collect(dir, i, r).toList() shouldBe b.toList()
    }
})
