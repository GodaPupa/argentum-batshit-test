package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/** Excluded deterministic fixtures only; this class does not load the official frozen vector. */
class PestControlTierOneMonsterTronFailureEvidenceTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("production uses the qualified Monster Tron profile in either opponent seat") {
        failureFixtureAssignments().forEach { assignment ->
            val provenance = PestControlTierOneMonsterTronGameAdapter.provenance(
                assignment, failureFixtureIdentity(), "1".repeat(40),
            )
            PestControlTierOneMonsterTronProductionDriver.profileFor(
                provenance, assignment.monsterTronSeat.index,
            ) shouldBe PestMonsterTronPolicy.profile
            PestControlTierOneMonsterTronProductionDriver.profileFor(
                provenance, assignment.pestSeat.index,
            ) shouldBe AiProfile.PRODUCTION_CANDIDATE_EXPIRING
            PestMonsterTronPolicy.profile.id shouldBe "pest-monster-tron-policy-audit"
        }
    }

    test("an action cap retains the observed transcript without a synthetic terminal outcome") {
        val assignment = failureFixtureAssignments().first()
        val game = PestControlTierOneMonsterTronAuthorizedInitializer.initialize(
            registry, assignment, failureFixtureIdentity(), "1".repeat(40), true,
        )
        val failure = shouldThrow<MonsterTronGameplayFailure> {
            PestControlTierOneMonsterTronProductionDriver.drive(registry, game, maxActions = 0)
        }
        failure.raw.failure shouldBe "reached 0 actions"
        failure.raw.actions.isNotEmpty() shouldBe true
        failure.raw.actions.map { it.sequence } shouldBe (1..failure.raw.actions.size).toList()
        failure.raw.mulliganActionCount shouldBe failure.raw.actions.size
        failure.raw.terminal?.gameOver shouldBe false
        failure.raw.terminal?.winnerId shouldBe null
        failure.raw.provenance.gameNumber shouldBe 1
    }

    test("an engine exception records the attempted action with no stale events") {
        val game = PestControlTierOneMonsterTronAuthorizedInitializer.initialize(
            registry, failureFixtureAssignments().first(), failureFixtureIdentity(), "1".repeat(40), true,
        )
        val traces = mutableListOf<MonsterTronOfficialActionTrace>()
        val action = KeepHand(game.environment.playerIds.first())
        shouldThrow<IllegalStateException> {
            PestControlTierOneMonsterTronProductionDriver.submitExactlyOne(
                game.environment, action, traces,
            ) { error("EXCLUDED_ENGINE_EXCEPTION") }
        }
        traces.size shouldBe 1
        traces.single().sequence shouldBe 1
        traces.single().accepted shouldBe false
        traces.single().executionError shouldBe "EXCLUDED_ENGINE_EXCEPTION"
        traces.single().rejectionReason shouldBe null
        traces.single().emittedEvents shouldBe emptyList()
        traces.single().selectedAction.toString().contains("KeepHand") shouldBe true
    }

    test("coordinator retains rejected game bytes and never advances to another assignment") {
        val assignments = failureFixtureAssignments()
        val attempted = mutableListOf<Int>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val coordinator = PestControlTierOneMonsterTronAuthorizedExecutionCoordinator(
            assignments, failureFixtureIdentity(),
            persistAttemptBeforeInitialization = { attempted += it.gameNumber },
            persistInitializationEntry = { initialized += it.gameNumber },
            persistCompletedGame = { a, _ -> recorded += a.gameNumber },
        )
        val raw = failureFixtureRaw(assignments.first())
        val outcome = coordinator.execute { throw MonsterTronGameplayFailure(raw, IllegalStateException("cap")) }
        outcome.disposition shouldBe MonsterTronCoordinatorDisposition.REJECTED
        attempted shouldBe listOf(1)
        initialized shouldBe listOf(1)
        recorded shouldBe emptyList()
        outcome.perGameRaw shouldBe emptyList()
        outcome.failedGameRaw?.toList() shouldBe PestControlTierOneMonsterTronProductionDriver.encode(raw).toList()
    }

    test("durable claim cannot be reopened and out-of-order entry permanently fails closed") {
        val root = Files.createTempDirectory("excluded-monster-tron-claim-")
        val journal = MonsterTronDurableAttempts.createNew(root, failureFixtureAssignments(), "1".repeat(40))
        shouldThrow<FileAlreadyExistsException> {
            MonsterTronDurableAttempts.createNew(root, failureFixtureAssignments(), "1".repeat(40))
        }
        shouldThrow<IllegalStateException> { journal.recordInitializationEntry(1) }
        shouldThrow<IllegalStateException> { journal.recordAttempt(1) }
        journal.events() shouldBe emptyList()
        val directory = root.resolve(PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
        Files.exists(directory.resolve("claim.txt")) shouldBe true
        Files.exists(directory.resolve("game-1.attempt")) shouldBe false
    }

    test("durable rejection preserves raw bytes and digest and forbids the next game") {
        val root = Files.createTempDirectory("excluded-monster-tron-rejection-")
        val assignments = failureFixtureAssignments()
        val journal = MonsterTronDurableAttempts.createNew(root, assignments, "1".repeat(40))
        val raw = PestControlTierOneMonsterTronProductionDriver.encode(failureFixtureRaw(assignments.first()))
        journal.recordAttempt(1)
        journal.recordInitializationEntry(1)
        journal.reject(1, "EXCLUDED_RESOURCE_CAP", raw)
        val directory = root.resolve(PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
        Files.readAllBytes(directory.resolve("game-1.failed.raw")).toList() shouldBe raw.toList()
        Files.readString(directory.resolve("rejected.txt")) shouldBe
            "game=1\nreason=EXCLUDED_RESOURCE_CAP\nfailedRawSha256=${monsterTronDigest(raw)}\n"
        Files.exists(directory.resolve("game-1.record")) shouldBe false
        journal.events().map { it.type } shouldBe listOf(
            MonsterTronCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
            MonsterTronCoordinatorEventType.INITIALIZATION_ENTERED,
            MonsterTronCoordinatorEventType.REJECTED,
        )
        shouldThrow<IllegalStateException> { journal.recordAttempt(2) }
    }

    test("durable write failure preserves existing bytes and cannot be reported as a completed game") {
        val root = Files.createTempDirectory("excluded-monster-tron-write-")
        val journal = MonsterTronDurableAttempts.createNew(root, failureFixtureAssignments(), "1".repeat(40))
        journal.recordAttempt(1)
        journal.recordInitializationEntry(1)
        val directory = root.resolve(PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
        Files.writeString(directory.resolve("game-1.raw"), "existing evidence\n")
        shouldThrow<FileAlreadyExistsException> { journal.recordResult(1, "replacement".toByteArray()) }
        Files.readString(directory.resolve("game-1.raw")) shouldBe "existing evidence\n"
        Files.exists(directory.resolve("game-1.record")) shouldBe false
        shouldThrow<IllegalStateException> { journal.recordAttempt(2) }
        shouldThrow<IllegalStateException> { journal.reject(1, "DURABILITY_FAILURE") }
        journal.events().none { it.type == MonsterTronCoordinatorEventType.RECORD_DURABLY_WRITTEN } shouldBe true
    }
})

private fun failureFixtureIdentity() = MonsterTronSmokeVectorIdentity(
    PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE,
    PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
    PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256,
    PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
)

private fun failureFixtureAssignments() = (1..4).map { game ->
    val seed = 0x6d74726f6e010000L + game
    MonsterTronSmokeAssignment(
        game, seed, monsterTronSeedHex(seed),
        if (game <= 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE,
        if (game <= 2) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
        if (game % 2 == 1) MonsterTronStartingDeck.PEST_CONTROL else MonsterTronStartingDeck.MONSTER_TRON,
    )
}

private fun failureFixtureRaw(assignment: MonsterTronSmokeAssignment) = MonsterTronOfficialRawGame(
    provenance = PestControlTierOneMonsterTronGameAdapter.provenance(
        assignment, failureFixtureIdentity(), "1".repeat(40),
    ),
    actions = emptyList(),
    mulliganActionCount = 0,
    terminal = MonsterTronOfficialTerminal(gameOver = false, winnerId = null, turn = 1),
    failure = "EXCLUDED_RESOURCE_CAP",
)
