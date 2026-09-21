package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.GrixisSmokeArtifactIndex
import com.wingedsheep.gym.matchup.GrixisSmokeAttempt
import com.wingedsheep.gym.matchup.GrixisSmokeVectorIdentity
import com.wingedsheep.gym.matchup.PROTOCOL_JSON
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisArtifactContract
import com.wingedsheep.gym.matchup.sha256
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString

private val SYNTHETIC_ARTIFACT_VECTOR = (1L..4L).map { 9_100_000L + it }
private val SYNTHETIC_ARTIFACT_IDENTITY = GrixisSmokeVectorIdentity(
    freezeCommit = "6".repeat(40),
    orderedVectorSha256 = "7".repeat(64),
    assignmentCsvSha256 = "8".repeat(64),
    freezeManifestSha256 = "9".repeat(64),
)
private val SYNTHETIC_ARTIFACT_SUMMARY = "synthetic-grixis-summary\n".toByteArray()

/** Byte-array fixtures only; no filesystem, entropy source, registry, or game environment. */
class PestControlTierOneGrixisArtifactContractTest : FunSpec({
    test("validated synthetic index reconciles all four attempts and records") {
        val attempts = SYNTHETIC_ARTIFACT_VECTOR.mapIndexed { index, seed ->
            GrixisSmokeAttempt(index + 1, seed)
        }
        val raws = (1..4).map { "synthetic-grixis-game-$it\n".toByteArray() }
        val bytes = PestControlTierOneGrixisArtifactContract.buildIndex(
            SYNTHETIC_ARTIFACT_IDENTITY,
            attempts,
            (1..4).toList(),
            raws,
            SYNTHETIC_ARTIFACT_SUMMARY,
            "VALIDATED",
        )
        val index = PROTOCOL_JSON.decodeFromString<GrixisSmokeArtifactIndex>(bytes.decodeToString())

        PestControlTierOneGrixisArtifactContract.validate(
            index,
            SYNTHETIC_ARTIFACT_IDENTITY,
            SYNTHETIC_ARTIFACT_VECTOR,
            raws,
            SYNTHETIC_ARTIFACT_SUMMARY,
        ) shouldBe emptyList()
    }

    test("rejected prefix remains admissible without invented records") {
        val attempts = SYNTHETIC_ARTIFACT_VECTOR.take(2).mapIndexed { index, seed ->
            GrixisSmokeAttempt(index + 1, seed)
        }
        val raws = listOf("synthetic-grixis-game-1\n".toByteArray())
        val bytes = PestControlTierOneGrixisArtifactContract.buildIndex(
            SYNTHETIC_ARTIFACT_IDENTITY,
            attempts,
            listOf(1),
            raws,
            SYNTHETIC_ARTIFACT_SUMMARY,
            "REJECTED",
        )
        val index = PROTOCOL_JSON.decodeFromString<GrixisSmokeArtifactIndex>(bytes.decodeToString())

        PestControlTierOneGrixisArtifactContract.validate(
            index,
            SYNTHETIC_ARTIFACT_IDENTITY,
            SYNTHETIC_ARTIFACT_VECTOR,
            raws,
            SYNTHETIC_ARTIFACT_SUMMARY,
        ) shouldBe emptyList()
    }

    test("contract rejects reorder replacement orphan tampering and false validation") {
        val raws = listOf("a", "b").map(String::toByteArray)
        val validHashes = raws.map(::sha256)
        val base = GrixisSmokeArtifactIndex(
            freezeCommit = SYNTHETIC_ARTIFACT_IDENTITY.freezeCommit,
            orderedVectorSha256 = SYNTHETIC_ARTIFACT_IDENTITY.orderedVectorSha256,
            assignmentCsvSha256 = SYNTHETIC_ARTIFACT_IDENTITY.assignmentCsvSha256,
            freezeManifestSha256 = SYNTHETIC_ARTIFACT_IDENTITY.freezeManifestSha256,
            attemptedGames = listOf(1, 2, 3),
            attemptedSeeds = SYNTHETIC_ARTIFACT_VECTOR.take(3),
            recordedGames = listOf(1, 2),
            perGameRawSha256 = validHashes,
            summarySha256 = sha256(SYNTHETIC_ARTIFACT_SUMMARY),
            disposition = "REJECTED",
        )
        fun errors(index: GrixisSmokeArtifactIndex, raw: List<ByteArray> = raws, summary: ByteArray = SYNTHETIC_ARTIFACT_SUMMARY) =
            PestControlTierOneGrixisArtifactContract.validate(
                index,
                SYNTHETIC_ARTIFACT_IDENTITY,
                SYNTHETIC_ARTIFACT_VECTOR,
                raw,
                summary,
            )

        errors(base.copy(freezeCommit = "a".repeat(40))).isNotEmpty() shouldBe true
        errors(base.copy(freezeManifestSha256 = "b".repeat(64))).isNotEmpty() shouldBe true
        errors(base.copy(attemptedGames = listOf(1, 3, 2))).isNotEmpty() shouldBe true
        errors(base.copy(attemptedGames = listOf(1, 2))).isNotEmpty() shouldBe true
        errors(base.copy(attemptedSeeds = listOf(9_100_001L, 9_100_002L, 999L))).isNotEmpty() shouldBe true
        errors(base.copy(recordedGames = listOf(1, 2, 3, 4))).isNotEmpty() shouldBe true
        errors(base.copy(disposition = "VALIDATED")).isNotEmpty() shouldBe true
        errors(base, summary = SYNTHETIC_ARTIFACT_SUMMARY + 0).isNotEmpty() shouldBe true
        errors(base, raw = listOf("tampered", "b").map(String::toByteArray)).isNotEmpty() shouldBe true
    }
})
