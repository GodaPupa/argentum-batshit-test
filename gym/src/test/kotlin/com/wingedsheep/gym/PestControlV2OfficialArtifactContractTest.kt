package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val ARTIFACT_TEST_VECTOR = (1L..10L).map { 7_000_000L + it }

class PestControlV2OfficialArtifactContractTest : FunSpec({
    test("completed artifact index reconciles all ten frozen attempts and records") {
        val attempts = ARTIFACT_TEST_VECTOR.mapIndexed { i, seed -> V2OfficialAttempt(i + 1, seed) }
        val raws = (1..10).map { "synthetic-game-$it\n".toByteArray() }
        val bytes = PestControlV2OfficialArtifactContract.buildIndex(attempts, (1..10).toList(), raws, "COMPLETED")
        val index = PROTOCOL_JSON.decodeFromString<V2OfficialArtifactIndex>(bytes.decodeToString())
        PestControlV2OfficialArtifactContract.validate(index, ARTIFACT_TEST_VECTOR) shouldBe emptyList()
        index.perGameRawSha256 shouldBe raws.map(::sha256)
    }

    test("rejected partial block preserves attempted prefix without inventing records") {
        val attempts = ARTIFACT_TEST_VECTOR.take(3).mapIndexed { i, seed -> V2OfficialAttempt(i + 1, seed) }
        val raws = (1..2).map { "synthetic-game-$it\n".toByteArray() }
        val bytes = PestControlV2OfficialArtifactContract.buildIndex(attempts, listOf(1, 2), raws, "REJECTED")
        val index = PROTOCOL_JSON.decodeFromString<V2OfficialArtifactIndex>(bytes.decodeToString())
        PestControlV2OfficialArtifactContract.validate(index, ARTIFACT_TEST_VECTOR) shouldBe emptyList()
    }

    test("artifact validation fails closed on reorder replacement orphan or false completion") {
        val base = V2OfficialArtifactIndex(
            attemptedGames = listOf(1,2,3),
            attemptedSeeds = ARTIFACT_TEST_VECTOR.take(3),
            recordedGames = listOf(1,2),
            perGameRawSha256 = listOf("a","b"),
            disposition = "REJECTED",
        )
        PestControlV2OfficialArtifactContract.validate(base.copy(attemptedGames=listOf(1,3,2)), ARTIFACT_TEST_VECTOR).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(attemptedSeeds=listOf(1L,2L,999L)), ARTIFACT_TEST_VECTOR).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(recordedGames=listOf(1,2,3,4), perGameRawSha256=listOf("a","b","c","d")), ARTIFACT_TEST_VECTOR).isNotEmpty() shouldBe true
        PestControlV2OfficialArtifactContract.validate(base.copy(disposition="COMPLETED"), ARTIFACT_TEST_VECTOR).isNotEmpty() shouldBe true
    }
})
