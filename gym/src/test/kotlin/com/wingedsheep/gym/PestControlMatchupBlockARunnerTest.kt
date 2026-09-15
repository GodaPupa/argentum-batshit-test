package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.hours

private const val EXECUTION_ACK = "I_UNDERSTAND_BLOCK_A_SEEDS_ARE_CONSUMED_ON_ATTEMPT"
private const val CSV_RESOURCE =
    "pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-block-a-seeds.csv"

/**
 * The sole experimental Block A runner. It is compiled in ordinary CI but disabled by default.
 * Its activation line is the only production-readiness line authorized to change for execution.
 */
class PestControlMatchupBlockARunnerTest : FunSpec({
    val explicitlyAuthorized = System.getenv("PEST_BLOCK_A_EXECUTE") == "true" &&
        System.getenv("PEST_BLOCK_A_EXECUTION_ACK") == EXECUTION_ACK
    val enabled = PestControlMatchupBlockARunner.activationAllowed(
        configuredState = PestControlMatchupBlockARunner.CONFIGURED_STATE,
        explicitEnvironmentAuthorization = explicitlyAuthorized,
        isUnitTestProcess = !explicitlyAuthorized,
    )

    test("execute frozen Pest Control versus Mono Red Madness Block A exactly once").config(
        enabled = enabled,
        timeout = 12.hours,
    ) {
        val projectRoot = Path.of("").toAbsolutePath()
        val csvPath = projectRoot.resolve("gym/src/test/resources/$CSV_RESOURCE")
        val vectorPath = projectRoot.resolve("docs/experiments/pest-control/matchup-block-a-ordered-seeds.txt")
        val registryPath = projectRoot.resolve("docs/experiments/pest-control/matchup-block-a-seed-registry.csv")
        val freezeManifestPath = projectRoot.resolve("docs/experiments/pest-control/matchup-block-a-seed-freeze-manifest.json")
        val outputDirectory = projectRoot.resolve("build/reports/pest-control-matchup-block-a")
        check(!Files.exists(outputDirectory)) { "Block A output directory already exists; refusing replay" }

        val csvBytes = Files.readAllBytes(csvPath)
        val assignments = PestControlMatchupBlockCodec.parseFrozenCsv(csvBytes)
        val preflightErrors = PestControlMatchupBlockARunner.activationErrors(
            assignments = assignments,
            sourceCommit = PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT,
            protocolId = PEST_MONO_RED_PREBOARD_PROTOCOL_ID,
            blockId = PEST_MATCHUP_BLOCK_A_ID,
            vectorSha256 = sha256(Files.readAllBytes(vectorPath)),
            csvSha256 = sha256(csvBytes),
            manifestSha256 = sha256(Files.readAllBytes(freezeManifestPath)),
            configuredState = PestControlMatchupBlockARunner.CONFIGURED_STATE,
        ).toMutableList()
        if (sha256(Files.readAllBytes(registryPath)) != PEST_MATCHUP_BLOCK_A_REGISTRY_SHA256) {
            preflightErrors += "seed-registry hash mismatch"
        }
        val executionCommit = System.getenv("PEST_BLOCK_A_EXECUTION_COMMIT")
            ?: error("PEST_BLOCK_A_EXECUTION_COMMIT is required")
        if (gitHead(projectRoot) != executionCommit) preflightErrors += "checked-out commit differs from execution commit"
        check(preflightErrors.isEmpty()) { "Block A activation refused: ${preflightErrors.joinToString()}" }

        Files.createDirectories(outputDirectory)
        val durableLogPath = outputDirectory.resolve("block-a-execution.log")
        val log = AppendOnlyBlockExecutionLog()
        fun append(
            state: BlockRunnerState,
            event: String,
            assignment: FrozenMatchupAssignment? = null,
        ) {
            val entry = log.append(state, event, assignment?.gameNumber, assignment?.seedDecimal)
            val line = buildString {
                append(entry.sequence).append('\t').append(entry.runnerState).append('\t').append(entry.event)
                entry.gameNumber?.let { append('\t').append("game=").append(it) }
                entry.seedDecimal?.let { append('\t').append("seed=").append(it) }
                appendLine()
            }
            Files.writeString(
                durableLogPath,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.SYNC,
            )
        }

        val registry = fullRegistry()
        val attempted = mutableListOf<AttemptedSeed>()
        val games = mutableListOf<MatchupRawGame>()
        append(BlockRunnerState.AUTHORIZED, "all frozen identities and activation guards verified")

        try {
            assignments.forEach { assignment ->
                append(BlockRunnerState.STARTED, "seed marked attempted before game initialization", assignment)
                attempted += AttemptedSeed(assignment.gameNumber, assignment.seedDecimal, assignment.seedHex)
                val provenance = MatchupProvenance(
                    sourceCommit = executionCommit,
                    pestSeat = assignment.pestSeat,
                    startingDeck = assignment.startingPlayer,
                    environment = MatchupEnvironmentIdentity.current(),
                    entropyClassification = "FROZEN_EXPERIMENTAL_VECTOR",
                    blockId = PEST_MATCHUP_BLOCK_A_ID,
                    freezeCommit = PEST_MATCHUP_BLOCK_A_FREEZE_COMMIT,
                    executionCommit = executionCommit,
                    orderedVectorSha256 = PEST_MATCHUP_BLOCK_A_VECTOR_SHA256,
                    assignmentCsvSha256 = PEST_MATCHUP_BLOCK_A_CSV_SHA256,
                    freezeManifestSha256 = PEST_MATCHUP_BLOCK_A_FREEZE_MANIFEST_SHA256,
                    gameNumber = assignment.gameNumber,
                    seedDecimal = assignment.seedDecimal,
                    seedHex = assignment.seedHex,
                )
                val session = PestControlPreboardSession.experimental(
                    registry,
                    provenance,
                    "${PEST_MATCHUP_BLOCK_A_ID}_GAME_${assignment.gameNumber.toString().padStart(2, '0')}",
                    assignment.seedDecimal,
                )
                playGame(session, registry)
                val game = session.rawGame()
                check(game.terminal?.gameOver == true && game.protocolDefect == null) {
                    "game ${assignment.gameNumber} did not reach a legitimate terminal"
                }
                games += game
                append(BlockRunnerState.STARTED, "legitimate terminal recorded", assignment)
            }

            append(BlockRunnerState.COMPLETED, "all 50 frozen games reached legitimate terminals")
            val completed = MatchupBlockRaw(
                executionCommit = executionCommit,
                runnerState = BlockRunnerState.COMPLETED,
                disposition = BlockDisposition.PENDING_GATE_7_REVIEW,
                assignments = assignments,
                attemptedSeeds = attempted,
                games = games,
                executionLog = log.entries,
            )
            writeArtifacts(outputDirectory, PestControlMatchupBlockCodec.build(completed))
            PestControlMatchupBlockCodec.verify(PestControlMatchupBlockCodec.build(completed)) shouldBe emptyList()
        } catch (failure: Throwable) {
            append(BlockRunnerState.REJECTED, "${failure::class.simpleName}: ${failure.message}", assignments.getOrNull(attempted.size - 1))
            val rejected = MatchupBlockRaw(
                executionCommit = executionCommit,
                runnerState = BlockRunnerState.REJECTED,
                disposition = BlockDisposition.REJECTED,
                rejectionReason = "${failure::class.simpleName}: ${failure.message}",
                assignments = assignments,
                attemptedSeeds = attempted,
                games = games,
                executionLog = log.entries,
            )
            writeArtifacts(outputDirectory, PestControlMatchupBlockCodec.build(rejected))
            throw failure
        }
    }
})

private fun playGame(session: PestControlPreboardSession, registry: CardRegistry) {
    val players = session.environment.playerIds
    val mulligans = players.associateWith { player ->
        EngineAiPlayerController(registry, player, gameStateProvider = { session.environment.state })
    }
    session.driveValidatedLondonMulligans(mulligans)
    val agents = players.associateWith { player -> AIPlayer.create(registry, player, AiProfile.PRODUCTION_CANDIDATE_EXPIRING) }
    var lastTurn = session.environment.turnNumber
    var actionsThisTurn = 0
    while (!session.environment.isTerminal) {
        session.enforceLimits(maxActions = 12_000, maxTurns = 60)
        if (session.environment.turnNumber != lastTurn) {
            lastTurn = session.environment.turnNumber
            actionsThisTurn = 0
        }
        if (++actionsThisTurn > 500) session.reject(ProtocolDefectKind.WEDGE, "more than 500 exact-one actions on turn $lastTurn")
        val state = session.environment.state
        val decision = state.pendingDecision
        val acting = decision?.playerId ?: state.priorityPlayerId
            ?: session.reject(ProtocolDefectKind.WEDGE, "no pending decision or priority holder")
        val agent = agents.getValue(acting)
        val action: GameAction = if (decision != null) {
            SubmitDecision(acting, agent.respondToDecision(state, decision))
        } else {
            agent.chooseAction(state)
        }
        session.submit(action, fallbackUsed = false)
    }
}

private fun fullRegistry(): CardRegistry = CardRegistry().apply {
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}

private fun writeArtifacts(directory: Path, bundle: MatchupBlockArtifactBundle) {
    val artifacts = linkedMapOf(
        "block-a-raw.json" to bundle.rawJson,
        "block-a-raw.json.gz" to bundle.compressed,
        "block-a-report.md" to bundle.report,
        "block-a-execution-manifest.json" to bundle.executionManifest,
        "block-a-execution.log" to bundle.executionLog,
        "block-a-acceptance-audit-input.json" to bundle.acceptanceAuditInput,
    )
    artifacts.forEach { (name, bytes) -> Files.write(directory.resolve(name), bytes) }
    Files.writeString(
        directory.resolve("block-a-artifact-sha256.txt"),
        artifacts.entries.joinToString(separator = "\n", postfix = "\n") { (name, bytes) -> "${sha256(bytes)}  $name" },
    )
}

private fun gitHead(root: Path): String {
    val process = ProcessBuilder("git", "rev-parse", "HEAD").directory(root.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(process.waitFor() == 0) { "git rev-parse failed: $output" }
    return output
}
