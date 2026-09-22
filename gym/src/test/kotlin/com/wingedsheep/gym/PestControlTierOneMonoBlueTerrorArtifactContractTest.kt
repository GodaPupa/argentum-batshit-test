package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeArtifactIndex
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeAttempt
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeVectorIdentity
import com.wingedsheep.gym.matchup.PROTOCOL_JSON
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorArtifactContract
import com.wingedsheep.gym.matchup.sha256
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString

private val SYNTHETIC_TERROR_ARTIFACT_VECTOR = (1L..4L).map { 9_300_000L + it }
private val SYNTHETIC_TERROR_ARTIFACT_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "6".repeat(40),
    orderedVectorSha256 = "7".repeat(64),
    assignmentCsvSha256 = "8".repeat(64),
    freezeManifestSha256 = "9".repeat(64),
)
private val SYNTHETIC_TERROR_ARTIFACT_SUMMARY = "synthetic-terror-summary\n".toByteArray()

/** Byte-array fixtures only; no filesystem, entropy source, registry, or game environment. */
class PestControlTierOneMonoBlueTerrorArtifactContractTest : FunSpec({
    test("validated synthetic index reconciles all four attempts and records") {
        val attempts = SYNTHETIC_TERROR_ARTIFACT_VECTOR.mapIndexed { index, seed ->
            MonoBlueTerrorSmokeAttempt(index + 1, seed)
        }
        val raws = (1..4).map { "synthetic-terror-game-$it\n".toByteArray() }
        val bytes = PestControlTierOneMonoBlueTerrorArtifactContract.buildIndex(
            SYNTHETIC_TERROR_ARTIFACT_IDENTITY,
            attempts,
            (1..4).toList(),
            raws,
            SYNTHETIC_TERROR_ARTIFACT_SUMMARY,
            "VALIDATED",
        )
        val index = PROTOCOL_JSON.decodeFromString<MonoBlueTerrorSmokeArtifactIndex>(bytes.decodeToString())

        PestControlTierOneMonoBlueTerrorArtifactContract.validate(
            index,
            SYNTHETIC_TERROR_ARTIFACT_IDENTITY,
            SYNTHETIC_TERROR_ARTIFACT_VECTOR,
            raws,
            SYNTHETIC_TERROR_ARTIFACT_SUMMARY,
        ) shouldBe emptyList()
    }

    test("rejected prefix remains admissible without invented records") {
        val attempts = SYNTHETIC_TERROR_ARTIFACT_VECTOR.take(2).mapIndexed { index, seed ->
            MonoBlueTerrorSmokeAttempt(index + 1, seed)
        }
        val raws = listOf("synthetic-terror-game-1\n".toByteArray())
        val bytes = PestControlTierOneMonoBlueTerrorArtifactContract.buildIndex(
            SYNTHETIC_TERROR_ARTIFACT_IDENTITY,
            attempts,
            listOf(1),
            raws,
            SYNTHETIC_TERROR_ARTIFACT_SUMMARY,
            "REJECTED",
        )
        val index = PROTOCOL_JSON.decodeFromString<MonoBlueTerrorSmokeArtifactIndex>(bytes.decodeToString())

        PestControlTierOneMonoBlueTerrorArtifactContract.validate(
            index,
            SYNTHETIC_TERROR_ARTIFACT_IDENTITY,
            SYNTHETIC_TERROR_ARTIFACT_VECTOR,
            raws,
            SYNTHETIC_TERROR_ARTIFACT_SUMMARY,
        ) shouldBe emptyList()
    }

    test("contract rejects reorder replacement orphan tampering and false validation") {
        val raws = listOf("a", "b").map(String::toByteArray)
        val validHashes = raws.map(::sha256)
        val base = MonoBlueTerrorSmokeArtifactIndex(
            freezeCommit = SYNTHETIC_TERROR_ARTIFACT_IDENTITY.freezeCommit,
            orderedVectorSha256 = SYNTHETIC_TERROR_ARTIFACT_IDENTITY.orderedVectorSha256,
            assignmentCsvSha256 = SYNTHETIC_TERROR_ARTIFACT_IDENTITY.assignmentCsvSha256,
            freezeManifestSha256 = SYNTHETIC_TERROR_ARTIFACT_IDENTITY.freezeManifestSha256,
            attemptedGames = listOf(1, 2, 3),
            attemptedSeeds = SYNTHETIC_TERROR_ARTIFACT_VECTOR.take(3),
            recordedGames = listOf(1, 2),
            perGameRawSha256 = validHashes,
            summarySha256 = sha256(SYNTHETIC_TERROR_ARTIFACT_SUMMARY),
            disposition = "REJECTED",
        )
        fun errors(
            index: MonoBlueTerrorSmokeArtifactIndex,
            raw: List<ByteArray> = raws,
            summary: ByteArray = SYNTHETIC_TERROR_ARTIFACT_SUMMARY,
        ) = PestControlTierOneMonoBlueTerrorArtifactContract.validate(
            index,
            SYNTHETIC_TERROR_ARTIFACT_IDENTITY,
            SYNTHETIC_TERROR_ARTIFACT_VECTOR,
            raw,
            summary,
        )

        errors(base.copy(freezeCommit = "a".repeat(40))).isNotEmpty() shouldBe true
        errors(base.copy(freezeManifestSha256 = "b".repeat(64))).isNotEmpty() shouldBe true
        errors(base.copy(attemptedGames = listOf(1, 3, 2))).isNotEmpty() shouldBe true
        errors(base.copy(attemptedGames = listOf(1, 2))).isNotEmpty() shouldBe true
        errors(base.copy(attemptedSeeds = listOf(9_300_001L, 9_300_002L, 999L))).isNotEmpty() shouldBe true
        errors(base.copy(recordedGames = listOf(1, 2, 3, 4))).isNotEmpty() shouldBe true
        errors(base.copy(disposition = "VALIDATED")).isNotEmpty() shouldBe true
        errors(base, summary = SYNTHETIC_TERROR_ARTIFACT_SUMMARY + 0).isNotEmpty() shouldBe true
        errors(base, raw = listOf("tampered", "b").map(String::toByteArray)).isNotEmpty() shouldBe true
    }
})
