package com.wingedsheep.gym

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/** Deterministic fixtures never cross the sealed official entry or create a repository claim. */
class PestControlTierOneMonsterTronOneShotBoundaryTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
    }

    test("claimed official labels cannot make synthetic assignments eligible") {
        val assignments = oneShotFixtureAssignments()
        val input = MonsterTronOfficialExecutionInput(oneShotFixtureIdentity(), assignments.map { it.seed }, assignments,
            PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256)
        monsterTronSealedInputErrors(input) shouldBe listOf("actual vector digest mismatch")
        monsterTronSealedInputErrors(input.copy(vectorIdentity = oneShotFixtureIdentity().copy(freezeCommit = "f".repeat(40))))
            .contains("freeze identity mismatch") shouldBe true
        monsterTronSealedInputErrors(input.copy(seeds = assignments.map { it.seed }.reversed()))
            .contains("assignment membership mismatch") shouldBe true
    }

    test("receipt binds source workflow run and exact four-game reservation") {
        val receipt = oneShotFixtureReceipt()
        monsterTronClaimReceiptErrors(receipt, "1".repeat(40), "123", "2".repeat(40)) shouldBe emptyList()
        listOf("execution_source_sha", "workflow_source_sha", "workflow_run_id", "workflow_run_attempt",
            "reserved_games", "vector_sha256", "freeze_source_sha", "claim_confirmed", "execution_allowed").forEach { field ->
            val altered = JsonObject(receipt + (field to JsonPrimitive("changed")))
            monsterTronClaimReceiptErrors(altered, "1".repeat(40), "123", "2".repeat(40)).isNotEmpty() shouldBe true
        }
    }

    test("durable intent precedes processor and result follows it including an engine exception") {
        val game = oneShotFixtureGame(registry)
        val traces = mutableListOf<MonsterTronOfficialActionTrace>()
        val order = mutableListOf<String>()
        val root = Files.createTempDirectory("excluded-monster-tron-action-evidence-")
        shouldThrow<IllegalStateException> {
            PestControlTierOneMonsterTronProductionDriver.submitExactlyOne(
                game.environment, KeepHand(game.environment.playerIds.first()), traces,
                evidenceSink = { phase, trace ->
                    order += phase.name
                    durableMonsterTronWrite(root.resolve("${phase.name}.json"),
                        PROTOCOL_JSON.encodeToString(trace).toByteArray(Charsets.UTF_8))
                },
                submit = { order += "PROCESSOR"; error("EXCLUDED_ENGINE_EXCEPTION") },
            )
        }
        order shouldBe listOf("INTENT", "PROCESSOR", "RESULT")
        traces.single().executionError shouldBe "EXCLUDED_ENGINE_EXCEPTION"
        Json.parseToJsonElement(Files.readString(root.resolve("INTENT.json"))).jsonObject["accepted"] shouldBe JsonPrimitive(false)
        Json.parseToJsonElement(Files.readString(root.resolve("RESULT.json"))).jsonObject["executionError"] shouldBe JsonPrimitive("EXCLUDED_ENGINE_EXCEPTION")
        shouldThrow<FileAlreadyExistsException> { durableMonsterTronWrite(root.resolve("INTENT.json"), byteArrayOf(1)) }
    }

    test("failed durable intent prevents the processor from seeing the action") {
        val game = oneShotFixtureGame(registry)
        var submissions = 0
        val traces = mutableListOf<MonsterTronOfficialActionTrace>()
        shouldThrow<IllegalStateException> {
            PestControlTierOneMonsterTronProductionDriver.submitExactlyOne(
                game.environment, KeepHand(game.environment.playerIds.first()), traces,
                evidenceSink = { _, _ -> error("EXCLUDED_STORAGE_FAILURE") },
                submit = { submissions++; error("must not execute") },
            )
        }
        submissions shouldBe 0
        traces shouldBe emptyList()
    }

    test("excluded exact-deck action transcript replays identical state and events") {
        val game = oneShotFixtureGame(registry)
        val replay = game.environment.fork()
        val observed = mutableListOf<MonsterTronOfficialActionTrace>()
        val failure = shouldThrow<MonsterTronGameplayFailure> {
            PestControlTierOneMonsterTronProductionDriver.drive(registry, game, maxActions = 12,
                evidenceSink = { phase, trace -> if (phase == MonsterTronActionEvidencePhase.RESULT) observed += trace })
        }
        failure.raw.actions shouldBe observed
        observed.isNotEmpty() shouldBe true
        observed.forEach { trace ->
            val action = PROTOCOL_JSON.decodeFromJsonElement(GameAction.serializer(), trace.selectedAction)
            (replay.stepExactlyOne(action) is ExactlyOneSubmissionResult.Rejected) shouldBe false
            replay.lastStepEvents.map { PROTOCOL_JSON.encodeToJsonElement(com.wingedsheep.engine.core.GameEvent.serializer(), it) }
                .shouldBe(trace.emittedEvents)
        }
        replay.state shouldBe game.environment.state
        replay.state.gameOver shouldBe false
        replay.state.winnerId shouldBe null
    }

    test("claim helper help resolves from gym through the canonical repository root without authentication") {
        val repository = MonsterTronRepositoryCommands.discover().root
        val commands = MonsterTronRepositoryCommands.discover(repository.resolve("gym"))
        commands.root shouldBe repository
        commands.required("FIXTURE_CWD", listOf("python3", "-c", "import os; print(os.getcwd())")).trim() shouldBe repository.toString()
        val help = commands.required("CLAIM_HELP_ONLY", listOf("python3", "scripts/pest-monster-tron-one-shot-claim.py", "--help"))
        help.contains("--verify-receipt") shouldBe true
        help.contains("--source-sha") shouldBe true
    }

    test("root scoped baseline detects protected sources while preserving only the three wrapper exclusions") {
        val root = Files.createTempDirectory("excluded-monster-tron-git-scope-")
        Files.createDirectories(root.resolve("gym"))
        fixtureCommand(root, "git", "init", "--quiet")
        val protected = listOf("mtg-sdk/probe.txt", "rules-engine/probe.txt", "ai/src/main/probe.txt",
            "mtg-sets/probe.txt", "gym/src/main/probe.txt")
        val excluded = listOf("PestControlTierOneMonsterTronOneShotBoundary.kt",
            "PestControlTierOneMonsterTronRunnerSurfacePreflight.kt", "PestControlTierOneMonsterTronOperationalStack.kt")
            .map { "gym/src/main/kotlin/com/wingedsheep/gym/matchup/$it" }
        (protected + excluded).forEach { path ->
            Files.createDirectories(root.resolve(path).parent)
            Files.writeString(root.resolve(path), "baseline\n")
        }
        fixtureCommit(root)
        val commands = MonsterTronRepositoryCommands.discover(root.resolve("gym"))
        val baseline = commands.required("FIXTURE_BASELINE", listOf("git", "rev-parse", "HEAD")).trim()
        excluded.forEach { Files.writeString(root.resolve(it), "separately qualified wrapper\n") }
        fixtureCommit(root)
        verifyMonsterTronBaseline(commands, baseline)
        protected.forEach { Files.writeString(root.resolve(it), "changed protected source\n") }
        fixtureCommit(root)
        val failure = shouldThrow<MonsterTronRequiredCommandFailure> { verifyMonsterTronBaseline(commands, baseline) }
        failure.commandLabel shouldBe "ENGINE_BASELINE"
        failure.exitCode shouldBe 1
        protected.forEach { failure.sanitizedOutput.contains(it) shouldBe true }
        excluded.forEach { failure.sanitizedOutput.contains(it) shouldBe false }
        failure.sanitizedOutput.contains("changed protected source") shouldBe false
    }

    test("subprocess failure preserves exit stdout and stderr but redacts credentials and omits raw arguments") {
        val secret = "EXCLUDED_SECRET_VALUE_0123456789"
        val commands = MonsterTronRepositoryCommands.discover(additionalRedactionValues = setOf(secret))
        val failure = shouldThrow<MonsterTronRequiredCommandFailure> {
            commands.required("EXCLUDED_FAILURE", listOf("python3", "-c",
                "import sys; print('stdout ${secret}', flush=True); print('Authorization: Bearer EXCLUDED_HEADER_VALUE', flush=True); print('https://fixture-user:EXCLUDED_URL_CREDENTIAL@example.invalid', flush=True); print('stderr diagnostic', file=sys.stderr); sys.exit(17)",
                "EXCLUDED_ARG_MUST_NOT_BE_LOGGED"))
        }
        failure.exitCode shouldBe 17
        failure.sanitizedOutput.contains("stdout [REDACTED]") shouldBe true
        failure.sanitizedOutput.contains("stderr diagnostic") shouldBe true
        failure.message!!.contains(secret) shouldBe false
        failure.message!!.contains("EXCLUDED_HEADER_VALUE") shouldBe false
        failure.message!!.contains("EXCLUDED_URL_CREDENTIAL") shouldBe false
        failure.message!!.contains("EXCLUDED_ARG_MUST_NOT_BE_LOGGED") shouldBe false
        failure.cause shouldBe null
        val output = Files.createTempDirectory("excluded-monster-tron-entry-evidence-")
        recordMonsterTronBoundaryEntryFailure(output, "1".repeat(40), failure)
        val path = output.resolve("boundary-entry-error.json")
        val bytes = Files.readAllBytes(path)
        val evidence = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        evidence.getValue("phase") shouldBe JsonPrimitive("SEALED_INPUT_VALIDATION")
        evidence.getValue("command").jsonObject.getValue("exit_code") shouldBe JsonPrimitive(17)
        listOf("per_game_attempts_recorded", "initialization_entries", "official_games_initialized",
            "official_actions_submitted", "outcomes").forEach { evidence.getValue(it) shouldBe JsonPrimitive(0) }
        listOf("canonical_claim_changed", "retry_authorized", "replacement_authorized").forEach {
            evidence.getValue(it) shouldBe JsonPrimitive(false)
        }
        Files.exists(output.resolve("execution")) shouldBe false
        bytes.toString(Charsets.UTF_8).contains(secret) shouldBe false
        shouldThrow<FileAlreadyExistsException> { recordMonsterTronBoundaryEntryFailure(output, "2".repeat(40), failure) }
        Files.readAllBytes(path).toList() shouldBe bytes.toList()
    }

    test("bounded diagnostics redact a secret crossing the byte capture boundary and drain excess output") {
        val secret = "EXCLUDED_LONG_SECRET_" + "Ω".repeat(20000)
        // A short overlapping prefix is deliberately visited before the long credential.
        val commands = MonsterTronRepositoryCommands.discover(additionalRedactionValues = linkedSetOf("Ωsuffix", secret))
        val failure = shouldThrow<MonsterTronRequiredCommandFailure> {
            commands.required("EXCLUDED_LONG_FAILURE", listOf("python3", "-c", "import sys; print(sys.argv[1]); sys.exit(23)", secret))
        }
        failure.exitCode shouldBe 23
        failure.outputTruncated shouldBe true
        failure.sanitizedOutput shouldBe "[REDACTED]"
        failure.message!!.contains("EXCLUDED_LONG_SECRET_") shouldBe false
        (failure.sanitizedOutput.length <= 8192) shouldBe true
    }

    test("truncated successful output fails closed and a process start failure does not expose argv") {
        val commands = MonsterTronRepositoryCommands.discover()
        val truncated = shouldThrow<MonsterTronRequiredCommandFailure> {
            commands.required("EXCLUDED_TRUNCATED_SUCCESS", listOf("python3", "-c", "print('x' * 20000)"))
        }
        truncated.exitCode shouldBe 0
        truncated.outputTruncated shouldBe true
        truncated.sanitizedOutput.length shouldBe 8192
        val missing = shouldThrow<MonsterTronRequiredCommandFailure> {
            commands.required("EXCLUDED_MISSING_PROGRAM", listOf("/nonexistent/EXCLUDED_ARG_MUST_NOT_BE_LOGGED"))
        }
        missing.exitCode shouldBe null
        missing.sanitizedOutput shouldBe "process start failed (IOException)"
        missing.message!!.contains("EXCLUDED_ARG_MUST_NOT_BE_LOGGED") shouldBe false
    }

    val zip = System.getenv("PEST_MONSTER_TRON_EXECUTION_INPUT_ZIP")
    val ack = System.getenv("PEST_MONSTER_TRON_EXECUTION_INPUT_ACK")
    test("pinned official bytes satisfy sealed binding without initialization").config(enabled = zip != null || ack != null) {
        val input = PestControlTierOneMonsterTronOfficialExecutionInputLoader.loadValidatedFromEnvironment()
        monsterTronSealedInputErrors(input) shouldBe emptyList()
        input.assignments.size shouldBe 4
        println("sealed_input=VERIFIED; official_initializations=0; official_actions=0; outcomes=0")
    }
})

private fun oneShotFixtureAssignments() = PestControlTierOneMonsterTronSmokeHarness.cellTemplate().map { cell ->
    val seed = 0x6d74726f6e010000L + cell.gameNumber
    MonsterTronSmokeAssignment(cell.gameNumber, seed, monsterTronSeedHex(seed), cell.pestSeat,
        if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO, cell.startingDeck)
}

private fun oneShotFixtureIdentity() = MonsterTronSmokeVectorIdentity(
    PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE, PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
    PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256, PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
)

private fun oneShotFixtureGame(registry: CardRegistry) = PestControlTierOneMonsterTronAuthorizedInitializer.initialize(
    registry, oneShotFixtureAssignments().first(), oneShotFixtureIdentity(), "1".repeat(40), true,
)

private fun oneShotFixtureReceipt() = buildJsonObject {
    put("schema", "pest-monster-tron-exclusive-claim-receipt-v1")
    put("block_id", PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
    put("claim_ref", MONSTER_TRON_CLAIM_REF)
    put("execution_source_sha", "1".repeat(40))
    put("engine_baseline_sha", MONSTER_TRON_ENGINE_BASELINE)
    put("workflow_source_sha", "2".repeat(40))
    put("workflow_run_id", 123)
    put("workflow_run_attempt", 1)
    put("reserved_games", 4)
    put("vector_sha256", PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256)
    put("archive_sha256", PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256)
    put("assignments_sha256", PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256)
    put("freeze_source_sha", PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE)
    put("claim_confirmed", true)
    put("execution_allowed", false)
    listOf("claim_commit_sha", "claim_tree_sha", "claim_blob_sha", "execution_source_tree_sha").forEach { put(it, "3".repeat(40)) }
    put("claim_payload_sha256", "4".repeat(64))
}

/** This isolated temporary repository contains text probes only, never an official source or seed. */
private fun fixtureCommand(root: Path, vararg command: String): String {
    val process = ProcessBuilder(command.toList()).directory(root.toFile()).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "excluded fixture setup failed" }
    return text
}

private fun fixtureCommit(root: Path) {
    fixtureCommand(root, "git", "add", ".")
    fixtureCommand(root, "git", "-c", "user.name=Excluded fixture", "-c", "user.email=excluded-fixture@example.invalid",
        "commit", "--quiet", "-m", "Excluded deterministic command-scope fixture")
}
