package com.wingedsheep.gym.izzet

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.security.MessageDigest

/** Literal storage callbacks only: no engine, official seed or opponent package. */
class IzzetSyntheticAttemptTest : FunSpec({
    val pins = IzzetSourcePins("1".repeat(40), "2".repeat(64), "3".repeat(64), "4".repeat(64))
    val identity = IzzetSyntheticAttemptIdentity("izzet-synthetic-barrier", pins)
    fun root() = Files.createTempDirectory("izzet-attempt-").toRealPath()
    test("identity and intent exist before initializer and initial bytes are durable before return") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        var calls = 0
        val result = IzzetSyntheticAttempt.initializeOnce(root, identity, {
            calls++
            Files.readAllBytes(dir.resolve("identity.json")).toList() shouldBe identity.bytes().toList()
            Files.readString(dir.resolve("initialization-intent")) shouldBe "INITIALIZE_ONCE\n"
            Files.exists(dir.resolve("initialized.sha256")) shouldBe false
            "literal initial envelope"
        }, { it.toByteArray() })
        calls shouldBe 1
        Files.readString(dir.resolve("initial.bin")) shouldBe result
        val digest = MessageDigest.getInstance("SHA-256").digest(result.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        Files.readString(dir.resolve("initialized.sha256")) shouldBe digest + "\n"
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { calls++; result }, { it.toByteArray() }) }
        calls shouldBe 1
    }
    test("initializer exception preserves attempt intent and permanently prevents reentry") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        var calls = 0
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce<String>(root, identity,
            { calls++; error("synthetic interruption") }, { it.toByteArray() }) }
        Files.exists(dir.resolve("identity.json")) shouldBe true
        Files.exists(dir.resolve("initialization-intent")) shouldBe true
        Files.exists(dir.resolve("fault")) shouldBe true
        Files.exists(dir.resolve("initial.bin")) shouldBe false
        Files.exists(dir.resolve("initialized.sha256")) shouldBe false
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { calls++; "retry" }, { it.toByteArray() }) }
        calls shouldBe 1
    }
    test("encoder failure preserves intent without false initialization completion") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { "returned" }, { error("codec failed") }) }
        Files.exists(dir.resolve("fault")) shouldBe true
        Files.exists(dir.resolve("initialized.sha256")) shouldBe false
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { "retry" }, { it.toByteArray() }) }
    }
    test("empty and oversized initial envelopes consume their own attempts") {
        for (bytes in listOf(byteArrayOf(), ByteArray(16 * 1024 * 1024 + 1))) {
            val root = root()
            shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { bytes }, { it }) }
            Files.exists(root.resolve(identity.attemptId).resolve("initialized.sha256")) shouldBe false
            shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { "retry" }, { it.toByteArray() }) }
        }
    }
    test("result write collision preserves original bytes and cannot report initialized") {
        val root = root(); val dir = root.resolve(identity.attemptId)
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, {
            Files.writeString(dir.resolve("initial.bin"), "preserved collision")
            "new"
        }, { it.toByteArray() }) }
        Files.readString(dir.resolve("initial.bin")) shouldBe "preserved collision"
        Files.exists(dir.resolve("initialized.sha256")) shouldBe false
        Files.exists(dir.resolve("fault")) shouldBe true
    }
    test("source deck policy runtime and attempt identities have distinct exact durable bindings") {
        val identities = listOf(identity, identity.copy(attemptId = "izzet-synthetic-other"),
            identity.copy(pins = pins.copy(sourceCommit = "5".repeat(40))),
            identity.copy(pins = pins.copy(deckSha256 = "6".repeat(64))),
            identity.copy(pins = pins.copy(policySha256 = "7".repeat(64))),
            identity.copy(pins = pins.copy(runtimeSha256 = "8".repeat(64))))
        identities.map { it.bytes().toList() }.distinct().size shouldBe identities.size
        for (id in identities) {
            val root = root()
            IzzetSyntheticAttempt.initializeOnce(root, id, { "fixture" }, { it.toByteArray() })
            Files.readAllBytes(root.resolve(id.attemptId).resolve("identity.json")).toList() shouldBe id.bytes().toList()
        }
    }
    test("invalid or escaping attempt identity cannot reach initializer") {
        val root = root(); var calls = 0
        for (id in listOf("../escape", "official-attempt", "izzet-synthetic-a/b", "izzet-synthetic-")) {
            shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity.copy(attemptId = id),
                { calls++; "forbidden" }, { it.toByteArray() }) }
        }
        calls shouldBe 0
        Files.list(root).use { it.count() } shouldBe 0L
    }
    test("orphaned reservation cannot be reopened and later caller mutation cannot alter stored bytes") {
        val root = root()
        Files.createDirectory(root.resolve(identity.attemptId))
        var calls = 0
        shouldThrowAny { IzzetSyntheticAttempt.initializeOnce(root, identity, { calls++; "retry" }, { it.toByteArray() }) }
        calls shouldBe 0
        val fresh = Files.createTempDirectory("izzet-attempt-fresh-").toRealPath()
        val bytes = "detached".toByteArray()
        IzzetSyntheticAttempt.initializeOnce(fresh, identity, { bytes }, { it })
        bytes.fill(0)
        Files.readString(fresh.resolve(identity.attemptId).resolve("initial.bin")) shouldBe "detached"
    }
})
