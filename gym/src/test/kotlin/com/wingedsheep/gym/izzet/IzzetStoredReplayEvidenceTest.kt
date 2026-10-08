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
class IzzetStoredReplayEvidenceTest : FunSpec({
    val registry = CardRegistry().apply { register(TestCards.all) }
    val processor = ActionProcessor(registry)
    val pins = IzzetSourcePins("7046722633a7a8075234b199eb7f8234d1d05bd7", "2".repeat(64), "3".repeat(64), "4".repeat(64))
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
    test("four bounded real action schemas retain exact durable collector and refuse duplicate publication") {
        for ((opening, kind) in listOf(false to "PlayLand", true to "KeepHand", true to "TakeMulligan", false to "PassPriority")) {
            val dir = submitted(opening, kind); val sink = sink(); val b = collect(dir)
            val destination = publish(sink, dir, b)
            Files.readAllBytes(destination.resolve("evidence.json")).toList() shouldBe b.toList()
            read(sink, dir, filePin(dir, "0.intent"), filePin(dir, "0.result"), hash(b))
            shouldThrowAny { publish(sink, dir, b) }
        }
    }
    test("semantic failure retains consumed reservation and forbids valid resubmission") {
        val dir = submitted(); val sink = sink()
        shouldThrowAny { publish(sink, dir, "{}".toByteArray()) }
        val destination = sink.resolve(identity.attemptId)
        Files.exists(destination.resolve("request.json")) shouldBe true
        Files.exists(destination.resolve("evidence.json")) shouldBe false
        shouldThrowAny { publish(sink, dir) }
    }
    test("incomplete publication cannot certify or reopen and retains exact payload") {
        val dir = submitted(); val sink = sink(); val b = collect(dir)
        val destination = publish(sink, dir, b)
        Files.delete(destination.resolve("complete.sha256"))
        shouldThrowAny { read(sink, dir, filePin(dir, "0.intent"), filePin(dir, "0.result"), hash(b)) }
        shouldThrowAny { publish(sink, dir, b) }
        Files.readAllBytes(destination.resolve("evidence.json")).toList() shouldBe b.toList()
    }
    test("tampered bytes extra files and symlinks fail closed under storage reader") {
        for (kind in listOf("bytes", "extra", "symlink")) {
            val dir = submitted(); val sink = sink(); val b = collect(dir); val destination = publish(sink, dir, b)
            when (kind) {
                "bytes" -> Files.writeString(destination.resolve("evidence.json"), "{}")
                "extra" -> Files.writeString(destination.resolve("extra"), "x")
                else -> {
                    val target = Files.createTempFile("izzet-collector-copy", ".json")
                    Files.write(target, b); Files.delete(destination.resolve("evidence.json"))
                    Files.createSymbolicLink(destination.resolve("evidence.json"), target)
                }
            }
            shouldThrowAny { read(sink, dir, filePin(dir, "0.intent"), filePin(dir, "0.result"), hash(b)) }
        }
    }
    test("stored completion cannot conceal unresolved original intent") {
        val dir = submitted(); val sink = sink(); val b = collect(dir)
        val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result")
        val destination = publish(sink, dir, b)
        Files.delete(dir.resolve("0.result"))
        shouldThrowAny { read(sink, dir, i, r, hash(b)) }
        Files.exists(dir.resolve("0.result")) shouldBe false
        Files.readAllBytes(destination.resolve("evidence.json")).toList() shouldBe b.toList()
    }
    test("external reference and identity drift fail without rewriting records") {
        val dir = submitted(); val sink = sink(); val b = collect(dir); val destination = publish(sink, dir, b)
        val i = filePin(dir, "0.intent"); val r = filePin(dir, "0.result")
        for ((badI, badR, badE) in listOf(Triple("0".repeat(64), r, hash(b)),
            Triple(i, "0".repeat(64), hash(b)), Triple(i, r, "0".repeat(64))))
            shouldThrowAny { read(sink, dir, badI, badR, badE) }
        shouldThrowAny { IzzetStoredReplayEvidence.verify(destination, dir,
            identity.copy(pins = pins.copy(runtimeSha256 = "9".repeat(64))), pin(dir), i, r, hash(b), registry) }
        read(sink, dir, i, r, hash(b))
    }
})
