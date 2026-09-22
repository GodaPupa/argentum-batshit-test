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
import java.security.MessageDigest

class IndustrialWasteMonoBlueReadinessSmokeTest : FunSpec({
    test("Gate 7 Mono-Blue Terror exact-deck readiness smoke").config(
        enabled = System.getenv("IW_G7_MONO_BLUE_READINESS_SMOKE") == "true",
    ) {
        val repository = monoBlueRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/mono-blue-terror-joan-rubies-2026-09-12.dck")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(opponentPath))
            .joinToString("") { "%02x".format(it) }
        require(digest == MONO_BLUE_DECK_SHA256) { "Gate 7 opponent identity drift: $digest" }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val control = monoBlueParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck"))
        val opponent = monoBlueParseMain(opponentPath)
        listOf(control, opponent).forEach { deck ->
            require(deck.size == 60) { "readiness decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-g7-control",
            industrialBase.copy(
                id = "industrial-waste-g7-control",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )
        val monoBlue = ArenaAgent(
            "mono-blue-terror-g7-readiness",
            AiProfile.PRODUCTION_CANDIDATE_EXPIRING.copy(id = "mono-blue-terror-g7-readiness"),
        )

        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, monoBlue),
            decks = listOf(control, opponent),
            seed = G7_READINESS_FIXTURE_SEED,
            groupId = 1,
            rotation = 0,
            maxTurns = 14,
            maxActions = 4_000,
            skipMulligans = false,
            recordActionStream = true,
        )

        val validDraw = game.drawReason.isEmpty() ||
            game.drawReason.startsWith("maxTurns") ||
            game.drawReason.startsWith("maxActions")
        val report = MonoBlueReadinessSmokeReport(
            schemaVersion = 1,
            evidenceClass = "seed-free-deterministic-readiness-smoke",
            promotionEligible = false,
            fixtureSeed = G7_READINESS_FIXTURE_SEED,
            seedUse = "fixed development fixture; not registered and not matchup evidence",
            opponent = "Mono Blue Terror — Joan Rubies, 2nd, 2026-09-12",
            source = "https://metagame.info/en-us/mtg/tournaments/43-edicion-super-ingenio-ingeniobcn-barcelona-top-8-pauper-2026-09-12",
            deckSha256 = digest,
            completed = game.completed,
            winnerSeat = game.winnerSeat,
            turns = game.turns,
            actions = game.actions,
            drawReason = game.drawReason,
            exception = game.exception,
            illegalActions = game.illegalActions,
            valid = game.exception == null && game.illegalActions.isEmpty() && validDraw,
        )

        val output = root.resolve("results/gate-7-mono-blue-readiness-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(report.actions > 0) { "Gate 7 readiness smoke made no legal progress" }
        check(report.valid) { "Gate 7 readiness smoke failed legal progression" }
    }
})

private const val MONO_BLUE_DECK_SHA256 =
    "f99a01d040e8d0c5d0144db019ca53db7a07bb251c07ac6fb3118f695d3fc3d3"
private const val G7_READINESS_FIXTURE_SEED = 0x1A57_B10EL

private fun monoBlueRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun monoBlueParseMain(path: Path): Deck {
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
private data class MonoBlueReadinessSmokeReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val fixtureSeed: Long,
    val seedUse: String,
    val opponent: String,
    val source: String,
    val deckSha256: String,
    val completed: Boolean,
    val winnerSeat: Int?,
    val turns: Int,
    val actions: Int,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
    val valid: Boolean,
)
