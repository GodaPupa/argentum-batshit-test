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

class IndustrialWasteElvesReadinessSmokeTest : FunSpec({
    test("Gate 10 Elves exact-deck readiness smoke").config(
        enabled = System.getenv("IW_G10_ELVES_READINESS_SMOKE") == "true",
    ) {
        val repository = elvesReadinessRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/elves-mogged-2026-09-19.dck")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(opponentPath))
            .joinToString("") { "%02x".format(it) }
        require(digest == ELVES_DECK_SHA256) { "Gate 10 opponent identity drift: $digest" }

        val parsed = elvesReadinessParseDeck(opponentPath)
        require(parsed.main.size == 60) { "Gate 10 Elves main must contain exactly 60 cards" }
        require(parsed.sideboard.size == 15) { "Gate 10 Elves sideboard must contain exactly 15 cards" }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val control = elvesReadinessParseDeck(
            root.resolve("control/industrial-waste-v1.0-submitted.dck")
        ).main
        listOf(control, parsed.main).forEach { deck ->
            require(deck.size == 60) { "readiness decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-g10-control",
            industrialBase.copy(
                id = "industrial-waste-g10-control",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )
        val elvesBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val elves = ArenaAgent(
            "elves-g10-readiness",
            elvesBase.copy(
                id = "elves-g10-readiness",
                advisorModules = elvesBase.advisorModules + ElvesAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, elves),
            decks = listOf(control, parsed.main),
            seed = G10_READINESS_FIXTURE_SEED,
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

        val report = ElvesReadinessSmokeReport(
            schemaVersion = 1,
            evidenceClass = "seed-free-deterministic-readiness-smoke",
            promotionEligible = false,
            fixtureSeed = G10_READINESS_FIXTURE_SEED,
            seedUse = "fixed development fixture; not registered and not matchup evidence",
            opponent = "Elves — Mogged, Top 4 (5-2), 2026-09-19",
            deckSha256 = digest,
            mainCount = parsed.main.size,
            sideboardCount = parsed.sideboard.size,
            completed = game.completed,
            winnerSeat = game.winnerSeat,
            turns = game.turns,
            actions = game.actions,
            drawReason = game.drawReason,
            exception = game.exception,
            illegalActions = game.illegalActions,
            valid = game.exception == null && game.illegalActions.isEmpty() && validDraw,
        )

        val output = root.resolve("results/gate-10-elves-readiness-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(report.actions > 0) { "Gate 10 readiness smoke made no legal progress" }
        check(report.valid) { "Gate 10 readiness smoke failed legal progression" }
    }
})

private const val ELVES_DECK_SHA256 =
    "01f63d291f90fdd6956a37b5ec9bc6b411bff4d23ea87e4ff96c69be89c19cf6"
private const val G10_READINESS_FIXTURE_SEED = 0x1A57_B10EL

private data class ElvesReadinessDeck(val main: Deck, val sideboard: Deck)

private fun elvesReadinessRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun elvesReadinessParseDeck(path: Path): ElvesReadinessDeck {
    var section = ""
    val main = mutableListOf<String>()
    val sideboard = mutableListOf<String>()
    Files.readAllLines(path).forEach { raw ->
        val line = raw.trim()
        when {
            line == "[main]" -> section = "main"
            line == "[sideboard]" -> section = "sideboard"
            line.startsWith("[") -> section = ""
            section in setOf("main", "sideboard") && line.isNotEmpty() -> {
                val split = line.indexOf(' ')
                require(split > 0) { "Malformed deck line in $path: $line" }
                val count = line.substring(0, split).toInt()
                val name = line.substring(split + 1)
                repeat(count) {
                    if (section == "main") main.add(name) else sideboard.add(name)
                }
            }
        }
    }
    return ElvesReadinessDeck(Deck(main), Deck(sideboard))
}

@Serializable
private data class ElvesReadinessSmokeReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val fixtureSeed: Long,
    val seedUse: String,
    val opponent: String,
    val deckSha256: String,
    val mainCount: Int,
    val sideboardCount: Int,
    val completed: Boolean,
    val winnerSeat: Int?,
    val turns: Int,
    val actions: Int,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
    val valid: Boolean,
)
