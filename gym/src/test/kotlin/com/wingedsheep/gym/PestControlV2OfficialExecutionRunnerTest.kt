package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.hours

private const val V2_EXECUTION_ACK = "EXECUTE_FROZEN_V2_OFFICIAL_10_EXACTLY_ONCE"

class PestControlV2OfficialExecutionRunnerTest : FunSpec({
    val mode = System.getenv("PEST_V2_OFFICIAL_MODE")
    test("validate or execute the frozen V2 official block through the fail-closed coordinator").config(
        enabled = mode != null,
        timeout = 12.hours,
    ) {
        require(mode == "VALIDATE_ONLY" || mode == "EXECUTE") { "invalid PEST_V2_OFFICIAL_MODE" }
        val input = Path.of(System.getenv("PEST_V2_OFFICIAL_INPUT_DIR") ?: error("input directory required"))
        val output = Path.of(System.getenv("PEST_V2_OFFICIAL_OUTPUT_DIR") ?: error("output directory required"))
        Files.createDirectories(output)

        val vector = Files.readAllBytes(input.resolve("v2-official-ordered-seeds.txt"))
        val csv = Files.readAllBytes(input.resolve("v2-official-assignments.csv"))
        val manifest = Files.readAllBytes(input.resolve("v2-official-freeze-manifest.json"))
        sha256(manifest) shouldBe PEST_V2_FREEZE_MANIFEST_SHA256
        val preflight = PestControlV2OfficialExecutionPreflight.inspect(
            vector = vector,
            csv = csv,
            qualifiedRunner = PEST_V2_QUALIFIED_RUNNER,
            protocol = PEST_V2_OFFICIAL_PROTOCOL,
            block = PEST_V2_OFFICIAL_BLOCK,
        )
        check(preflight.errors.isEmpty()) { "V2 official preflight failed: ${preflight.errors.joinToString()}" }

        if (mode == "VALIDATE_ONLY") {
            writeForced(
                output.resolve("validation-summary.txt"),
                "status=VALIDATED_UNEXECUTED\nseed_count=${preflight.seeds.size}\noutcome_exposure=0\n".toByteArray(),
            )
        } else {
            System.getenv("PEST_V2_OFFICIAL_ACK") shouldBe V2_EXECUTION_ACK
            val executionCommit = System.getenv("PEST_V2_EXECUTION_COMMIT")
                ?: error("PEST_V2_EXECUTION_COMMIT required")
            val registry = CardRegistry().apply {
                register(PredefinedTokens.allTokens)
                MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
            }
            val attempted = mutableListOf<V2OfficialAttempt>()
            val coordinator = PestControlV2OfficialExecutionCoordinator(
                preflight = preflight,
                persistAttemptBeforeInitialization = { attempt ->
                    attempted += attempt
                    val log = buildString {
                        appendLine("game_number,seed_decimal")
                        attempted.forEach { appendLine("${it.gameNumber},${it.seed}") }
                    }.toByteArray()
                    writeForced(output.resolve("attempted-seeds.csv"), log)
                },
                persistCompletedGame = { assignment, raw ->
                    val game = PROTOCOL_JSON.decodeFromString<MatchupRawGame>(raw.decodeToString())
                    val bundle = PestControlMatchupArtifactCodec.build(game)
                    check(PestControlMatchupArtifactCodec.verify(bundle).isEmpty())
                    val gameDir = output.resolve("games/game-${assignment.game.toString().padStart(2, '0')}")
                    Files.createDirectories(gameDir)
                    writeForced(gameDir.resolve("raw.json"), bundle.rawJson)
                    writeForced(gameDir.resolve("raw.json.gz"), bundle.compressed)
                    writeForced(gameDir.resolve("report.md"), bundle.report)
                    writeForced(gameDir.resolve("manifest.json"), bundle.manifest)
                },
            )
            val outcome = coordinator.execute { assignment ->
                val session = PestControlV2OfficialGameAdapter.initialize(registry, assignment, executionCommit)
                val game = PestControlPreboardProductionDriver.drive(registry, session)
                check(game.terminal?.gameOver == true && game.protocolDefect == null) {
                    "game ${assignment.game} did not produce an admissible terminal"
                }
                PestControlMatchupArtifactCodec.build(game).rawJson
            }
            val summary = renderSummary(outcome)
            val artifactIndex = outcome.artifactIndex(summary)
            val decodedIndex = PROTOCOL_JSON.decodeFromString<V2OfficialArtifactIndex>(artifactIndex.decodeToString())
            check(
                PestControlV2OfficialArtifactContract.validate(
                    decodedIndex,
                    preflight.seeds,
                    outcome.perGameRaw,
                    summary,
                ).isEmpty(),
            ) { "official artifact index failed reconciliation" }
            writeForced(output.resolve("block-summary.csv"), summary)
            writeForced(output.resolve("artifact-index.json"), artifactIndex)
            writeForced(
                output.resolve("execution-status.txt"),
                "state=${outcome.state}\nattempted=${outcome.attempts.size}\nrecorded=${outcome.recordedGames.size}\nfailure=${outcome.failure ?: "none"}\n".toByteArray(),
            )
            check(outcome.state == V2OfficialRunnerState.COMPLETED) { outcome.failure ?: "official block rejected" }
        }
    }
})

private fun renderSummary(outcome: V2OfficialExecutionOutcome): ByteArray = buildString {
    appendLine("game_number,seed_decimal,pest_seat,starting_deck,terminal,winner,turn,action_count,protocol_defect,telemetry_refs")
    outcome.perGameRaw.forEach { raw ->
        val game = PROTOCOL_JSON.decodeFromString<MatchupRawGame>(raw.decodeToString())
        val telemetry = game.indexedTelemetry
        val referenceCount = listOf(
            telemetry.lifeAndDamage,
            telemetry.combat,
            telemetry.stackAndPriority,
            telemetry.zoneChanges,
            telemetry.wardenResearcherMascot,
            telemetry.lifegainAndPayoffConversion,
            telemetry.weatherAndFollow,
            telemetry.carrierThrallAndScion,
            telemetry.removalAndTargets,
            telemetry.manaAndJungleHollow,
            telemetry.burnTargets,
            telemetry.madnessAndDiscardOutlets,
            telemetry.sneakySnackerRecursion,
            telemetry.fireblastAndLavaDart,
            telemetry.meldedMoxite,
            telemetry.guttersnipeAndFlamebreather,
        ).sumOf { it.size }
        appendLine(
            listOf(
                game.provenance.gameNumber,
                game.provenance.seedDecimal,
                game.provenance.pestSeat,
                game.provenance.startingDeck,
                game.terminal?.gameOver,
                game.terminal?.winnerId?.value,
                game.terminal?.turn,
                game.priorityActions.size,
                game.protocolDefect?.kind,
                referenceCount,
            ).joinToString(","),
        )
    }
}.toByteArray()

private fun writeForced(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.parent)
    FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
    ).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}
