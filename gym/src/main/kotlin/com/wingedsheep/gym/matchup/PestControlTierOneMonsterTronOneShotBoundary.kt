package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

internal const val MONSTER_TRON_ENGINE_BASELINE = "a224ef0008a2b85e2c2c106df9959678782e765d"
internal const val MONSTER_TRON_RULES_SHA256 = "8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca"
internal const val MONSTER_TRON_OFFICIAL_WORKFLOW = ".github/workflows/pest-control-tier-one-monster-tron-official-smoke.yml"
internal const val MONSTER_TRON_CLAIM_REF = "refs/heads/pest-control/official-attempts/monster-tron-smoke-v1"

/**
 * The sole official wrapper takes no seed, assignment, vector identity, or Boolean durability proof
 * from its caller. The pinned loader, authenticated repository claim, clean immutable checkout and
 * fsynced journal establish those facts before the first initialization. Construction CI never
 * enables this entry. A later reviewed activation must pin the fully qualified source commit.
 */
internal object PestControlTierOneMonsterTronOneShotBoundary {
    private val entered = AtomicBoolean(false)

    private class SealedInput(
        val input: MonsterTronOfficialExecutionInput,
        val source: String,
        val receipt: JsonObject,
        val output: Path,
    )

    fun executeFromEnvironment(registry: CardRegistry) {
        check(entered.compareAndSet(false, true)) { "one-shot boundary already entered in this process" }
        val sealed = try {
            loadSealedInput()
        } catch (failure: Exception) {
            // The global claim may already exist, while sealing can fail before an execution
            // directory or a game journal exists. Preserve that failure without inventing an
            // attempt, retry, initialization, action or outcome.
            try {
                recordMonsterTronBoundaryEntryFailure(
                    Path.of(System.getenv("PEST_MONSTER_TRON_OFFICIAL_OUTPUT_DIR")
                        ?: error("official output directory required for entry-failure evidence")).toRealPath(),
                    System.getenv("PEST_MONSTER_TRON_EXECUTION_SOURCE_SHA"), failure,
                )
            } catch (artifactFailure: Exception) {
                failure.addSuppressed(artifactFailure)
            }
            throw failure
        }
        val output = sealed.output
        var primaryFailure: Exception? = null
        try {
            executeSealed(registry, sealed)
        } catch (failure: Exception) {
            primaryFailure = failure
            // An independent no-clobber diagnostic survives a journal transition failure. No
            // exception is swallowed or transformed into a deck result, and no next game starts.
            try {
                durableMonsterTronWrite(output.resolve("fatal-execution-error.json"), jsonBytes(buildJsonObject {
                    put("schema", "pest-monster-tron-fatal-v1")
                    put("execution_source_sha", sealed.source)
                    put("failure", failure.message ?: failure.javaClass.name)
                    put("retry_authorized", false)
                }))
            } catch (artifactFailure: Exception) {
                failure.addSuppressed(artifactFailure)
            }
            throw failure
        } finally {
            try {
                // Inventory is written once, after any fatal diagnostic, so every existing file
                // including partial failure evidence is covered and no digest file is reblessed.
                writeMonsterTronArtifactInventory(output)
            } catch (inventoryFailure: Exception) {
                val earlier = primaryFailure
                if (earlier != null) earlier.addSuppressed(inventoryFailure) else throw inventoryFailure
            }
        }
    }

    private fun loadSealedInput(): SealedInput {
        fun env(name: String) = System.getenv(name) ?: error("$name required")
        require(env("PEST_MONSTER_TRON_OFFICIAL_MODE") == "EXECUTE")
        require(env("GITHUB_REPOSITORY") == "GodaPupa/argentum-batshit-test")
        require(env("GITHUB_RUN_ATTEMPT") == "1") { "workflow reruns are forbidden" }
        require(env("GITHUB_EVENT_NAME") == "workflow_dispatch")
        require(env("GITHUB_WORKFLOW_REF").startsWith(
            "GodaPupa/argentum-batshit-test/$MONSTER_TRON_OFFICIAL_WORKFLOW@refs/heads/"
        ))
        val source = env("PEST_MONSTER_TRON_EXECUTION_SOURCE_SHA")
        require(source.matches(Regex("[0-9a-f]{40}")) && source != "0".repeat(40))
        val commands = MonsterTronRepositoryCommands.discover()
        require(commands.required("CHECKOUT_IDENTITY", listOf("git", "rev-parse", "HEAD")).trim() == source)
        require(commands.required("CLEAN_CHECKOUT", listOf("git", "status", "--porcelain", "--untracked-files=all")).isBlank()) {
            "execution checkout is not clean"
        }
        verifyMonsterTronBaseline(commands)
        val input = PestControlTierOneMonsterTronOfficialExecutionInputLoader
            .loadForAuthorizedExecutionFromEnvironment()
        require(monsterTronSealedInputErrors(input).isEmpty())
        val rules = Files.readAllBytes(Path.of(env("PEST_MONSTER_TRON_RULES_ARCHIVE")))
        require(monsterTronDigest(rules) == MONSTER_TRON_RULES_SHA256)
        require(rules.toString(Charsets.UTF_8).contains("These rules are effective as of September 25, 2026."))
        require(!LocalDate.now(ZoneOffset.UTC).isBefore(LocalDate.of(2026, 9, 25)))

        val receiptPath = Path.of(env("PEST_MONSTER_TRON_CLAIM_RECEIPT")).toRealPath()
        val receipt = Json.parseToJsonElement(Files.readString(receiptPath)).jsonObject
        require(monsterTronClaimReceiptErrors(receipt, source, env("GITHUB_RUN_ID"), env("GITHUB_WORKFLOW_SHA")).isEmpty())
        // Read-only API authentication of canonical ref, commit, tree, blob and exact payload.
        // A locally invented receipt cannot authorize initialization.
        commands.required("AUTHENTICATE_DURABLE_CLAIM", listOf(
            "python3", "scripts/pest-monster-tron-one-shot-claim.py",
            "--verify-receipt", receiptPath.toString(), "--source-sha", source,
            "--run-id", env("GITHUB_RUN_ID"), "--run-attempt", "1",
        ))
        val outputRoot = Path.of(env("PEST_MONSTER_TRON_OFFICIAL_OUTPUT_DIR")).toRealPath()
        val output = outputRoot.resolve("execution")
        Files.createDirectory(output)
        forceMonsterTronEvidenceDirectory(outputRoot)
        durableMonsterTronWrite(output.resolve("claim-receipt.json"), Files.readAllBytes(receiptPath))
        durableMonsterTronWrite(output.resolve("effective-rules.txt"), rules)
        return SealedInput(input, source, receipt, output)
    }

    private fun executeSealed(registry: CardRegistry, sealed: SealedInput) {
        val input = sealed.input
        val output = sealed.output
        PestControlPreboardDecks.verifyFrozenIdentities()
        require(PestControlPreboardDecks.pestMainCounts.keys.all { registry.getCard(it) != null })
        require(PestControlTierOneMonsterTronExecutionAuthorization.inspect().let {
            it.green && it.executionAuthorized && it.failClosed
        })
        require(PestControlTierOneMonsterTronReadiness.validationErrors(TierOneMonsterTronReadiness(), registry).isEmpty())
        durableMonsterTronWrite(output.resolve("preexecution.json"), jsonBytes(buildJsonObject {
            put("schema", "pest-monster-tron-one-shot-preexecution-v1")
            put("protocol_id", PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID)
            put("block_id", PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
            put("execution_source_sha", sealed.source)
            put("engine_baseline_sha", MONSTER_TRON_ENGINE_BASELINE)
            put("vector_sha256", input.vectorIdentity.orderedVectorSha256)
            put("archive_sha256", input.archiveSha256)
            put("rules_sha256", MONSTER_TRON_RULES_SHA256)
            put("starting_life", 20)
            put("expected_games", 4)
            put("max_actions", 12000)
            put("max_turns", 60)
            put("max_actions_per_turn", 500)
            put("mulligans", "LONDON_WITHOUT_HAND_SMOOTHER")
            put("replication_rule", "FOUR_VALID_GAMES_REGARDLESS_OF_WINS_THEN_SEPARATE_FRESH_TWELVE_FREEZE")
        }))
        val evidenceRoot = output.resolve("durable-evidence")
        Files.createDirectory(evidenceRoot)
        forceMonsterTronEvidenceDirectory(output)
        val journal = MonsterTronDurableAttempts.createNew(evidenceRoot, input.assignments, sealed.source)
        val actuallyInitialized = mutableListOf<Int>()
        val coordinator = PestControlTierOneMonsterTronAuthorizedExecutionCoordinator(
            input.assignments, input.vectorIdentity,
            persistAttemptBeforeInitialization = { attempt ->
                val member = input.assignments.single { it.gameNumber == attempt.gameNumber }
                require(member.seed == attempt.seed)
                journal.recordAttempt(attempt.gameNumber)
            },
            persistInitializationEntry = { assignment ->
                require(assignment == input.assignments[assignment.gameNumber - 1])
                journal.recordInitializationEntry(assignment.gameNumber)
            },
            persistCompletedGame = { assignment, raw ->
                // Preserve complete raw before a journal transition can fail.
                durableMonsterTronWrite(output.resolve("game-${assignment.gameNumber}.raw.json"), raw)
                journal.recordResult(assignment.gameNumber, raw)
            },
        )
        val outcome = coordinator.execute { assignment ->
            require(assignment == input.assignments[assignment.gameNumber - 1])
            require(journal.events().takeLast(2).map { it.gameNumber to it.type } == listOf(
                assignment.gameNumber to MonsterTronCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                assignment.gameNumber to MonsterTronCoordinatorEventType.INITIALIZATION_ENTERED,
            ))
            val actionRoot = output.resolve("game-${assignment.gameNumber}-actions")
            Files.createDirectory(actionRoot)
            forceMonsterTronEvidenceDirectory(output)
            val game = PestControlTierOneMonsterTronAuthorizedInitializer.initialize(
                registry, assignment, input.vectorIdentity, sealed.source, durableAttemptRecorded = true,
            )
            actuallyInitialized += assignment.gameNumber
            durableMonsterTronWrite(output.resolve("game-${assignment.gameNumber}.initialization.json"), jsonBytes(buildJsonObject {
                put("game_number", assignment.gameNumber)
                put("pest_seat", assignment.pestSeat.index)
                put("monster_tron_seat", assignment.monsterTronSeat.index)
                putJsonArray("player_ids") { game.environment.playerIds.forEach { add(it.value) } }
            }))
            PestControlTierOneMonsterTronProductionDriver.encode(
                PestControlTierOneMonsterTronProductionDriver.drive(registry, game, evidenceSink = { phase, trace ->
                    durableMonsterTronWrite(
                        actionRoot.resolve("${trace.sequence.toString().padStart(5, '0')}-${phase.name}.json"),
                        (PROTOCOL_JSON.encodeToString(trace) + "\n").toByteArray(Charsets.UTF_8),
                    )
                })
            )
        }
        var rejectionJournalFailure: String? = null
        if (outcome.disposition == MonsterTronCoordinatorDisposition.REJECTED) {
            outcome.failedGameRaw?.let { durableMonsterTronWrite(output.resolve("failed-game.raw.json"), it) }
            try {
                journal.reject(outcome.attempts.lastOrNull()?.gameNumber ?: 1,
                    "OFFICIAL_EXECUTION_FAILURE", outcome.failedGameRaw)
            } catch (failure: Exception) {
                rejectionJournalFailure = failure.message ?: failure.javaClass.name
            }
        }
        durableMonsterTronWrite(output.resolve("coordinator-events.txt"), journal.events().joinToString("\n", postfix = "\n") {
            "${it.gameNumber}|${it.type.name}"
        }.toByteArray(Charsets.UTF_8))
        val summary = buildJsonObject {
            put("schema", "pest-monster-tron-one-shot-summary-v1")
            put("protocol_id", PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID)
            put("block_id", PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
            put("execution_source_sha", sealed.source)
            put("engine_baseline_sha", MONSTER_TRON_ENGINE_BASELINE)
            put("claim_commit_sha", sealed.receipt.getValue("claim_commit_sha"))
            put("vector_sha256", input.vectorIdentity.orderedVectorSha256)
            put("assignments_sha256", input.vectorIdentity.assignmentCsvSha256)
            put("freeze_source_sha", input.vectorIdentity.freezeCommit)
            put("rules_sha256", MONSTER_TRON_RULES_SHA256)
            put("disposition", outcome.disposition.name)
            putJsonArray("attempted_games") { outcome.attempts.forEach { add(it.gameNumber) } }
            putJsonArray("initialization_entries") { outcome.initializedGames.forEach { add(it) } }
            putJsonArray("initialized_games") { actuallyInitialized.forEach { add(it) } }
            putJsonArray("recorded_games") { outcome.recordedGames.forEach { add(it) } }
            put("failure", outcome.failure?.let(::JsonPrimitive) ?: JsonNull)
            put("rejection_journal_failure", rejectionJournalFailure?.let(::JsonPrimitive) ?: JsonNull)
            put("rerolls", 0)
            put("replacements", 0)
            put("seed_regeneration", 0)
            put("replication_authorized_by_this_runner", false)
        }
        durableMonsterTronWrite(output.resolve("summary.json"), jsonBytes(summary))
        check(outcome.disposition == MonsterTronCoordinatorDisposition.VALIDATED && rejectionJournalFailure == null) {
            "official block rejected; preserve claim and all evidence; no retry: ${outcome.failure}; journal=$rejectionJournalFailure"
        }
        check(outcome.recordedGames == listOf(1, 2, 3, 4) && actuallyInitialized == listOf(1, 2, 3, 4))
    }
}

/** Strict official-boundary binding; synthetic construction primitives cannot satisfy this. */
internal fun monsterTronSealedInputErrors(input: MonsterTronOfficialExecutionInput): List<String> = buildList {
    if (input.archiveSha256 != PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256) add("archive mismatch")
    if (input.vectorIdentity != MonsterTronSmokeVectorIdentity(
        PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE, PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
        PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256, PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
    )) add("freeze identity mismatch")
    if (input.seeds != input.assignments.map { it.seed }) add("assignment membership mismatch")
    if (input.assignments.map { it.gameNumber } != listOf(1, 2, 3, 4)) add("assignment sequence mismatch")
    if (monsterTronDigest(input.seeds.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)) !=
        PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256) add("actual vector digest mismatch")
    input.assignments.zip(PestControlTierOneMonsterTronSmokeHarness.cellTemplate()).forEach { (row, cell) ->
        if (row.gameNumber != cell.gameNumber || row.pestSeat != cell.pestSeat || row.startingDeck != cell.startingDeck ||
            row.monsterTronSeat == row.pestSeat || row.seedHex != monsterTronSeedHex(row.seed)) add("cell mismatch")
    }
}

internal fun monsterTronClaimReceiptErrors(receipt: JsonObject, source: String, runId: String, workflowSha: String): List<String> = buildList {
    val expected = mapOf(
        "schema" to "pest-monster-tron-exclusive-claim-receipt-v1", "block_id" to PEST_MONSTER_TRON_SMOKE_BLOCK_ID,
        "claim_ref" to MONSTER_TRON_CLAIM_REF, "execution_source_sha" to source,
        "engine_baseline_sha" to MONSTER_TRON_ENGINE_BASELINE, "workflow_source_sha" to workflowSha,
        "workflow_run_id" to runId, "workflow_run_attempt" to "1", "reserved_games" to "4",
        "vector_sha256" to PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
        "archive_sha256" to PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256,
        "assignments_sha256" to PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256,
        "freeze_source_sha" to PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE,
        "claim_confirmed" to "true", "execution_allowed" to "false",
    )
    expected.forEach { (key, value) -> if ((receipt[key] as? JsonPrimitive)?.content != value) add("claim $key mismatch") }
    listOf("claim_commit_sha", "claim_tree_sha", "claim_blob_sha", "execution_source_tree_sha").forEach { key ->
        if ((receipt[key] as? JsonPrimitive)?.content?.matches(Regex("[0-9a-f]{40}")) != true) add("claim $key invalid")
    }
    if ((receipt["claim_payload_sha256"] as? JsonPrimitive)?.content?.matches(Regex("[0-9a-f]{64}")) != true) add("claim payload digest invalid")
}

/** The explicit root and top-anchored paths also cover invocations by a gym-module test JVM. */
internal fun verifyMonsterTronBaseline(
    commands: MonsterTronRepositoryCommands,
    baseline: String = MONSTER_TRON_ENGINE_BASELINE,
) {
    require(baseline.matches(Regex("[0-9a-f]{40}")))
    // The wrapper, guard and added write-through trace instrumentation are separately qualified at
    // execution source C. All core engine, card, deck and pilot behavior remains the accepted base.
    // Only path names are diagnostic evidence: never print source contents from the baseline diff.
    commands.required("ENGINE_BASELINE", listOf("git", "diff", "--name-only", "--exit-code", baseline, "HEAD", "--",
        ":(top)mtg-sdk", ":(top)rules-engine", ":(top)ai/src/main", ":(top)mtg-sets", ":(top)gym/src/main",
        ":(top,exclude)gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOneShotBoundary.kt",
        ":(top,exclude)gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronRunnerSurfacePreflight.kt",
        ":(top,exclude)gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt"))
}

private const val MONSTER_TRON_COMMAND_CAPTURE_BYTES = 16384
private const val MONSTER_TRON_COMMAND_DIAGNOSTIC_CHARS = 8192

/** Contains only bounded sanitized evidence, never raw arguments, environment, output or causes. */
internal class MonsterTronRequiredCommandFailure(
    val commandLabel: String,
    val exitCode: Int?,
    val sanitizedOutput: String,
    val outputTruncated: Boolean,
) : IllegalStateException("required preexecution command failed: $commandLabel; exit=${exitCode ?: "NOT_STARTED"}; " +
    "output_truncated=$outputTruncated; output=$sanitizedOutput")

/** One command context is discovered once and then shared by every boundary subprocess. */
internal class MonsterTronRepositoryCommands private constructor(
    val root: Path,
    private val redactionValues: Set<String>,
) {
    companion object {
        fun discover(
            launchDirectory: Path = Path.of("").toAbsolutePath(),
            additionalRedactionValues: Set<String> = emptySet(),
        ): MonsterTronRepositoryCommands {
            val secrets = monsterTronEnvironmentSecrets() + additionalRedactionValues
            val launch = launchDirectory.toRealPath()
            val root = Path.of(requiredMonsterTronCommand(launch, "REPOSITORY_ROOT",
                listOf("git", "rev-parse", "--show-toplevel"), secrets).trim()).toRealPath()
            check(Files.isDirectory(root) && launch.startsWith(root)) { "launch directory is outside discovered repository" }
            return MonsterTronRepositoryCommands(root, secrets)
        }
    }

    fun required(label: String, command: List<String>): String =
        requiredMonsterTronCommand(root, label, command, redactionValues)
}

private fun monsterTronEnvironmentSecrets(): Set<String> = System.getenv().filterKeys {
    Regex("TOKEN|SECRET|PASSWORD|CREDENTIAL|PRIVATE_KEY", RegexOption.IGNORE_CASE).containsMatchIn(it)
}.values.filter(String::isNotEmpty).toSet()

private fun sanitizeMonsterTronCommandText(text: String, secrets: Set<String>, captureTruncated: Boolean): String {
    // A byte cap can split the final UTF-8 code point. Discard only its replacement marker
    // before matching credential prefixes; otherwise that marker could conceal a partial secret.
    val captured = if (captureTruncated) text.trimEnd('\uFFFD') else text
    var safe = captured
    // Compare all prefixes against the original captured text. Replacing one short overlapping
    // prefix first must never prevent a longer secret from being redacted.
    if (captureTruncated) {
        var suffixLength = 0
        secrets.filter(String::isNotEmpty).forEach { secret ->
            val maximum = minOf(secret.length - 1, captured.length)
            for (size in maximum downTo suffixLength + 1) {
                if (captured.endsWith(secret.take(size))) {
                    suffixLength = size
                    break
                }
            }
        }
        if (suffixLength > 0) safe = captured.dropLast(suffixLength) + "[REDACTED]"
    }
    secrets.filter(String::isNotEmpty).sortedByDescending(String::length).forEach { safe = safe.replace(it, "[REDACTED]") }
    safe = safe.replace(Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer|token)\\s+)\\S+"), "$1[REDACTED]")
        .replace(Regex("(?:gh[pousr]_|github_pat_)[A-Za-z0-9_]+"), "[REDACTED]")
        .replace(Regex("(https?://)[^/\\s:@]+:[^/\\s@]+@"), "$1[REDACTED]@")
    // Remove control characters (including terminal escape controls), retaining readable whitespace.
    safe = safe.filter { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }
    return safe.take(MONSTER_TRON_COMMAND_DIAGNOSTIC_CHARS)
}

private fun requiredMonsterTronCommand(
    root: Path,
    label: String,
    command: List<String>,
    secrets: Set<String>,
): String {
    require(label.matches(Regex("[A-Z0-9_]+"))) { "command evidence label must be a fixed identifier" }
    val process = try {
        ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start()
    } catch (failure: Exception) {
        throw MonsterTronRequiredCommandFailure(label, null,
            "process start failed (${failure.javaClass.simpleName})", false)
    }
    val capture = ByteArrayOutputStream(MONSTER_TRON_COMMAND_CAPTURE_BYTES)
    var truncated = false
    process.inputStream.use { stream ->
        val buffer = ByteArray(4096)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            val retained = minOf(count, MONSTER_TRON_COMMAND_CAPTURE_BYTES - capture.size())
            if (retained > 0) capture.write(buffer, 0, retained)
            if (retained < count) truncated = true
            // Drain the rest even when the evidence cap is reached so a noisy child cannot block.
        }
    }
    val exit = process.waitFor()
    val output = capture.toString(Charsets.UTF_8)
    // Truncated success is refused: incomplete output cannot prove a checkout or authorization.
    if (exit != 0 || truncated) {
        throw MonsterTronRequiredCommandFailure(label, exit,
            sanitizeMonsterTronCommandText(output, secrets, truncated), truncated || output.length > MONSTER_TRON_COMMAND_DIAGNOSTIC_CHARS)
    }
    return output
}

internal fun recordMonsterTronBoundaryEntryFailure(outputRoot: Path, source: String?, failure: Exception) {
    require(Files.isDirectory(outputRoot)) { "entry failure requires the existing artifact directory" }
    durableMonsterTronWrite(outputRoot.resolve("boundary-entry-error.json"), jsonBytes(buildJsonObject {
        put("schema", "pest-monster-tron-boundary-entry-failure-v1")
        put("block_id", PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
        put("execution_source_sha", source?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }?.let(::JsonPrimitive) ?: JsonNull)
        put("phase", "SEALED_INPUT_VALIDATION")
        put("failure_type", failure.javaClass.name)
        put("failure", sanitizeMonsterTronCommandText(failure.message ?: failure.javaClass.name,
            monsterTronEnvironmentSecrets(), false))
        if (failure is MonsterTronRequiredCommandFailure) {
            putJsonObject("command") {
                put("label", failure.commandLabel)
                put("exit_code", failure.exitCode?.let(::JsonPrimitive) ?: JsonNull)
                put("output", failure.sanitizedOutput)
                put("output_truncated", failure.outputTruncated)
            }
        }
        put("per_game_attempts_recorded", 0)
        put("initialization_entries", 0)
        put("official_games_initialized", 0)
        put("official_actions_submitted", 0)
        put("outcomes", 0)
        put("canonical_claim_changed", false)
        put("retry_authorized", false)
        put("replacement_authorized", false)
    }))
}

private fun jsonBytes(value: JsonObject): ByteArray = (value.toString() + "\n").toByteArray(Charsets.UTF_8)

internal fun durableMonsterTronWrite(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    forceMonsterTronEvidenceDirectory(path.parent)
}

private fun forceMonsterTronEvidenceDirectory(path: Path) {
    FileChannel.open(path, READ).use { it.force(true) }
}

private fun writeMonsterTronArtifactInventory(root: Path) {
    val entries = Files.walk(root).use { paths ->
        paths.filter(Files::isRegularFile).sorted().toList().map { path ->
            "${monsterTronDigest(Files.readAllBytes(path))}  ${root.relativize(path)}"
        }
    }
    durableMonsterTronWrite(root.resolve("artifacts.sha256"), entries.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
}
