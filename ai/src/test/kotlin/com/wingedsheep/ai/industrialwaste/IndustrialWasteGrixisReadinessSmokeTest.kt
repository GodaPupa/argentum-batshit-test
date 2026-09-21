package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaAgent
import com.wingedsheep.ai.arena.TableGameRunner
import com.wingedsheep.ai.arena.TableSetup
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fixed-fixture Gate 6 exact-deck smoke.
 *
 * Diagnostic only: this seed is a development fixture, is never registered, and is not matchup
 * evidence. The test must not be enabled until the Gate 6 six-card capability gate is green.
 */
class IndustrialWasteGrixisReadinessSmokeTest : FunSpec({
    test("Gate 6 Grixis Affinity exact-deck readiness smoke").config(
        enabled = System.getenv("IW_G6_GrixIS_READINESS_SMOKE") == "true",
    ) {
        val repository = grixisRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val control = grixisParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck"))
        val opponent = grixisParseMain(root.resolve("gauntlet/grixis-affinity-carlos-dc-2026-09-12.dck"))
        listOf(control, opponent).forEach { deck ->
            require(deck.size == 60) { "readiness decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )
        val grixis = ArenaAgent(
            "grixis-gate-6-readiness",
            AiProfile.PRODUCTION_CANDIDATE_EXPIRING.copy(id = "grixis-gate-6-readiness"),
        )

        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, grixis),
            decks = listOf(control, opponent),
            seed = G6_READINESS_FIXTURE_SEED,
            groupId = 1,
            rotation = 0,
            maxTurns = 14,
            maxActions = 4_000,
            skipMulligans = false,
        )

        val validDraw = game.drawReason.isEmpty() ||
            game.drawReason.startsWith("maxTurns") ||
            game.drawReason.startsWith("maxActions")
        val report = GrixisReadinessSmokeReport(
            schemaVersion = 1,
            evidenceClass = "seed-free-deterministic-readiness-smoke",
            promotionEligible = false,
            fixtureSeed = G6_READINESS_FIXTURE_SEED,
            seedUse = "fixed development fixture; not registered and not matchup evidence",
            opponent = "Grixis Affinity — Carlos Dc, Top 8, 2026-09-12",
            source = "https://www.mtgtop8.com/event?d=889936&e=90850&f=PAU",
            completed = game.completed,
            winnerSeat = game.winnerSeat,
            turns = game.turns,
            actions = game.actions,
            drawReason = game.drawReason,
            exception = game.exception,
            illegalActions = game.illegalActions,
            valid = game.exception == null && game.illegalActions.isEmpty() && validDraw,
        )

        val output = root.resolve("results/gate-6-grixis-readiness-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(report.actions > 0) { "readiness smoke made no legal progress; see $output" }
        check(report.valid) { "readiness smoke failed legal progression; see $output" }
    }
})

private const val G6_READINESS_FIXTURE_SEED = 0x1A57_AFF1L

private fun grixisRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun grixisParseMain(path: Path): Deck {
    var inMain = false
    val cards = buildList {
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            when {
                line == "[main]" -> inMain = true
                line.startsWith("[") -> inMain = false
                inMain && line.isNotEmpty() -> {
                    val split = line.indexOf(' ')
                    require(split > 0) { "Malformed deck line in $path: $line" }
                    val count = line.substring(0, split).toInt()
                    val name = line.substring(split + 1)
                    repeat(count) { add(name) }
                }
            }
        }
    }
    return Deck(cards)
}

@Serializable
private data class GrixisReadinessSmokeReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val fixtureSeed: Long,
    val seedUse: String,
    val opponent: String,
    val source: String,
    val completed: Boolean,
    val winnerSeat: Int?,
    val turns: Int,
    val actions: Int,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
    val valid: Boolean,
)
