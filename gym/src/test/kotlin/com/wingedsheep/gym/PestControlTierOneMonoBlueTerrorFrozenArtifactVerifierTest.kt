package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic bytes only. These tests do not download an official vector or initialize a game. */
class PestControlTierOneMonoBlueTerrorFrozenArtifactVerifierTest : FunSpec({
    val members = linkedMapOf(
        "a.txt" to "fixture-a".toByteArray(),
        "b.txt" to "fixture-b".toByteArray(),
    )
    val pins = members.mapValues { terrorFrozenDigest(it.value) }
    val good = terrorBindingArchive(members)

    fun inspectSynthetic(bytes: ByteArray, expected: Map<String, String> = pins) =
        inspectTerrorPinnedArchive(bytes, terrorFrozenDigest(bytes), expected)

    test("synthetic pinned archive passes without granting execution") {
        val before = good.copyOf()
        val result = inspectSynthetic(good)
        result.errors shouldBe emptyList()
        result.verified shouldBe true
        result.memberSha256 shouldBe pins
        result.executionAuthorized shouldBe false
        good.contentEquals(before) shouldBe true
    }

    test("outer digest mismatch stops before decompression") {
        val result = inspectTerrorPinnedArchive(good, "0".repeat(64), pins)
        result.verified shouldBe false
        result.memberSha256 shouldBe emptyMap()
        result.errors.shouldContain("archive hash mismatch")
    }

    test("inner substitution fails even with matching caller supplied synthetic archive digest") {
        val changed = terrorBindingArchive(members + ("b.txt" to "changed".toByteArray()))
        val result = inspectSynthetic(changed)
        result.verified shouldBe false
        result.errors.shouldContain("member hash mismatch: b.txt")
    }

    test("wrong inner digest fails") {
        inspectSynthetic(good, pins + ("a.txt" to "0".repeat(64))).verified shouldBe false
    }

    test("missing member fails") {
        inspectSynthetic(terrorBindingArchive(members - "b.txt")).verified shouldBe false
    }

    test("unexpected member fails") {
        val result = inspectSynthetic(terrorBindingArchive(members + ("c.txt" to byteArrayOf(1))))
        result.errors.shouldContain("unexpected archive member")
    }

    test("path traversal name fails without filesystem extraction") {
        val result = inspectSynthetic(terrorBindingArchive(members + ("../escape" to byteArrayOf(1))))
        result.errors.shouldContain("unexpected archive member")
    }

    test("directory entry fails") {
        val result = inspectSynthetic(terrorBindingArchive(members + ("sub/" to byteArrayOf())))
        result.errors.shouldContain("unexpected archive member")
    }

    test("duplicate archive name fails") {
        val duplicate = good.copyOf()
        val oldName = "b.txt".toByteArray()
        val newName = "a.txt".toByteArray()
        for (offset in 0..duplicate.size - oldName.size) {
            if (oldName.indices.all { duplicate[offset + it] == oldName[it] }) {
                newName.copyInto(duplicate, offset)
            }
        }
        inspectSynthetic(duplicate).errors.shouldContain("duplicate archive member")
    }

    test("malformed ZIP and empty input fail") {
        inspectSynthetic("not-a-zip".toByteArray()).verified shouldBe false
        PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier.inspect(byteArrayOf()).verified shouldBe false
    }

    test("expanded size cap fails closed") {
        val oversized = terrorBindingArchive(
            mapOf("a.txt" to ByteArray(TERROR_FROZEN_MAX_EXPANDED_BYTES + 1)),
        )
        val result = inspectSynthetic(oversized)
        result.errors.shouldContain("malformed archive or expanded byte limit exceeded")
    }

    test("compressed size cap fails before hashing or unpacking") {
        val result = inspectSynthetic(ByteArray(TERROR_FROZEN_MAX_ARCHIVE_BYTES + 1))
        result.verified shouldBe false
        result.archiveSha256 shouldBe ""
        result.memberSha256 shouldBe emptyMap()
    }

    test("public verifier cannot substitute fixture digests for frozen production pins") {
        val result = PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier.inspect(good)
        result.verified shouldBe false
        result.errors.shouldContain("archive hash mismatch")
        result.executionAuthorized shouldBe false
    }

    test("public verifier exposes only inspect") {
        val methods = PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }.toSet()
        methods shouldBe setOf("inspect")
    }
})

private fun terrorBindingArchive(entries: Map<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name).apply { time = 0L })
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
