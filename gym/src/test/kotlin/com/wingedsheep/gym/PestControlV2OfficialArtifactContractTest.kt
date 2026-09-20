package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val ARTIFACT_TEST_VECTOR = (1L..10L).map { 7_000_000L + it }
private val ARTIFACT_TEST_SUMMARY = "synthetic-summary\n".toByteArray()

class PestControlV2OfficialArtifactContractTest : FunSpec({
    test("completed artifact index reconciles all ten frozen attempts and records") {
        val attempts = ARTIFACT_TEST_VECTOR.mapIndexed { i, seed -> V2OfficialAttempt(i + 1, seed) }
        val raws = (1..10).map { "synthetic-game-$it\n".toByteArray() }
        val bytes = PestControlV2OfficialArtifactContract.buildIndex(
            attempts, (1..10).toList(), raws, ARTIFACT_TEST_SUMMARY, "COMPLETED",
        )
        val index = PROTOCOL_JSON.decodeFromString<V2OfficialArtifactIndex>(bytes.decodeToString())
        PestControlV2OfficialArtifactContract.validate(index, ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY) shouldBe emptyList()
        index.perGameRawSha256 shouldBe raws.map(::sha256)
    }

    test("rejected partial block preserves attempted prefix without inventing records") {
        val attempts = ARTIFACT_TEST_VECTOR.take(3).mapIndexed { i, seed -> V2OfficialAttempt(i + 1, seed) }
        val raws = (1..2).map { "synthetic-game-$it\n".toByteArray() }
        val bytes = PestControlV2OfficialArtifactContract.buildIndex(
            attempts, listOf(1, 2), raws, ARTIFACT_TEST_SUMMARY, "REJECTED",
        )
        val index = PROTOCOL_JSON.decodeFromString<V2OfficialArtifactIndex>(bytes.decodeToString())
        PestControlV2OfficialArtifactContract.validate(index, ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY) shouldBe emptyList()
    }

    test("artifact validation fails closed on reorder replacement orphan or false completion") {
        val base = V2OfficialArtifactIndex(
            attemptedGames = listOf(1,2,3),
            attemptedSeeds = ARTIFACT_TEST_VECTOR.take(3),
            recordedGames = listOf(1,2),
            perGameRawSha256 = listOf("a","b"),
            summarySha256 = sha256(ARTIFACT_TEST_SUMMARY),
            disposition = "REJECTED",
        )
        val raws = listOf("a", "b").map(String::toByteArray)
        val validHashes = raws.map(::sha256)
        PestControlV2OfficialArtifactContract.validate(base.copy(attemptedGames=listOf(1,3,2), perGameRawSha256=validHashes), ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(attemptedSeeds=listOf(1L,2L,999L), perGameRawSha256=validHashes), ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(recordedGames=listOf(1,2,3,4), perGameRawSha256=listOf("a","b","c","d")), ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(disposition="COMPLETED", perGameRawSha256=validHashes), ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(perGameRawSha256=validHashes), ARTIFACT_TEST_VECTOR, raws, ARTIFACT_TEST_SUMMARY + 0).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(perGameRawSha256=validHashes), ARTIFACT_TEST_VECTOR, listOf("tampered", "b").map(String::toByteArray), ARTIFACT_TEST_SUMMARY).isNotEmpty() shouldBe true
    }
})
