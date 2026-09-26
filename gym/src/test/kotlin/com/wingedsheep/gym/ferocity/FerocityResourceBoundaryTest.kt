package com.wingedsheep.gym.ferocity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class FerocityResourceBoundaryTest : FunSpec({
    test("KR01 exact kernel file limits are required and malformed or unbounded facts fail") {
        val exact = "Max file size             134217728            134217728            bytes\n"
        parseFerocityFileSizeLimit(exact) shouldBe FerocityFileSizeLimit(FEROCITY_CHILD_FILE_LIMIT_BYTES, FEROCITY_CHILD_FILE_LIMIT_BYTES)
        requireFerocityFileSizeLimit(parseFerocityFileSizeLimit(exact))
        listOf(
            "Max file size 134217727 134217728 bytes\n",
            "Max file size 134217728 134217729 bytes\n",
            "Max file size unlimited unlimited bytes\n",
            "Max file size 134217728 unlimited bytes\n",
        ).forEach { text ->
            shouldThrow<IllegalArgumentException> { requireFerocityFileSizeLimit(parseFerocityFileSizeLimit(text)) }
        }
        listOf("", exact + exact, "Max file size 134217728 134217728 objects\n",
            "Max file size -1 134217728 bytes\n").forEach { text ->
            shouldThrow<IllegalArgumentException> { parseFerocityFileSizeLimit(text) }
        }
    }

    test("KR02 streaming guards retain exact claim allocation and journal bytes and reject substitution") {
        val root = Files.createTempDirectory("ferocity-resource-fixed-guard-").toRealPath()
        val hash = "0".repeat(64)
        val pins = FerocitySourcePins("0".repeat(40), hash, mapOf("fixed" to hash), mapOf("fixed" to hash),
            mapOf("fixed" to hash), mapOf("fixed" to hash), hash, hash, hash, hash)
        val spec = FerocityTrialSpec("ferocity-recycling/resource-fixed/v1", "guard", "guard",
            FerocityTrialStage.DETERMINISTIC_FIXTURE, 0, mapOf("fixed" to 0), pins)
        FerocityTrialJournal.claimNew(root, spec).use { writer ->
            writer.append(FerocityHeader(writer.claimSha256, FerocityTrialLimits(),
                "Noninitializing file guard fixture; no game or gameplay record", null))
        }
        val guard = captureFerocityReplayFiles(root, spec.namespace, spec.trialId)
        guard.claim.spec shouldBe spec
        guard.files.size shouldBe 3
        guard.verifyUnchanged()
        guard.files.forEachIndexed { index, file ->
            val before = Files.readAllBytes(file.path)
            Files.write(file.path, before + byteArrayOf(32))
            shouldThrow<IllegalArgumentException> { guard.verifyUnchanged() }
            Files.write(file.path, before)
            guard.verifyUnchanged()
            // Preserve the original elsewhere and substitute a same-byte symlink. Even identical
            // target contents do not authorize a different path identity.
            val held = file.path.resolveSibling("held-$index.before")
            Files.move(file.path, held)
            try {
                Files.createSymbolicLink(file.path, held)
                shouldThrow<IllegalArgumentException> { guard.verifyUnchanged() }
            } finally {
                Files.delete(file.path)
                Files.move(held, file.path)
            }
            guard.verifyUnchanged()
        }
        readFerocityJournal(root, spec.namespace, spec.trialId).initialized shouldBe null
        readFerocityJournal(root, spec.namespace, spec.trialId).end shouldBe null
    }
})
