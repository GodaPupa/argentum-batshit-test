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

class IndustrialWasteSpyComboReadinessSmokeTest : FunSpec({
    test("Gate 11 Spy Combo exact-deck readiness smoke").config(
        enabled = System.getenv("IW_G11_SPY_READINESS_SMOKE") == "true",
    ) {
        val repository = spyReadinessRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/spy-combo-drinkme-2026-09-16.dck")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(opponentPath))
            .joinToString("") { "%02x".format(it) }
        require(digest == SPY_DECK_SHA256) { "Gate 11 opponent identity drift: $digest" }

        val parsed = spyReadinessParseDeck(opponentPath)
        require(parsed.main.size == 60) { "Gate 11 Spy main must contain exactly 60 cards" }
        require(parsed.sideboard.size == 15) { "Gate 11 Spy sideboard must contain exactly 15 cards" }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val control = spyReadinessParseDeck(
            root.resolve("control/industrial-waste-v1.0-submitted.dck")
        ).main
        val pactdoll = spyReadinessParseDeck(
            root.resolve("challengers/pactdoll-a.dck")
        ).main
        listOf(control, pactdoll, parsed.main).forEach { deck ->
            require(deck.size == 60) { "Gate 11 readiness decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-g11-control",
            industrialBase.copy(
                id = "industrial-waste-g11-control",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )
        val spyBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val spy = ArenaAgent(
            "spy-combo-g11-readiness",
            spyBase.copy(
                id = "spy-combo-g11-readiness",
                advisorModules = spyBase.advisorModules + SpyComboAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, spy),
            decks = listOf(control, parsed.main),
            seed = G11_READINESS_FIXTURE_SEED,
            groupId = 1,
            rotation = 0,
            maxTurns = 16,
            maxActions = 4_000,
            skipMulligans = false,
            recordActionStream = true,
        )

        val validDraw = game.drawReason.isEmpty() ||
            game.drawReason.startsWith("maxTurns") ||
            game.drawReason.startsWith("maxActions")

        val report = SpyReadinessSmokeReport(
            schemaVersion = 1,
            evidenceClass = "seed-free-deterministic-readiness-smoke",
            promotionEligible = false,
            fixtureSeed = G11_READINESS_FIXTURE_SEED,
            seedUse = "fixed development fixture; not registered and not matchup evidence",
            opponent = "Spy Combo — Drinkme, Top 16 (4-2), 2026-09-16",
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

        val output = root.resolve("results/gate-11-spy-combo-readiness-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(report.actions > 0) { "Gate 11 readiness smoke made no legal progress" }
        check(report.valid) { "Gate 11 readiness smoke failed legal progression" }
    }
})

private const val SPY_DECK_SHA256 =
    "d01a41caed140d361fb5bb1a8d5b224f27161a7e4732efc202636029a7a6de2f"
private const val G11_READINESS_FIXTURE_SEED = 0x5A71_11C0L

private data class SpyReadinessDeck(val main: Deck, val sideboard: Deck)

private fun spyReadinessRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun spyReadinessParseDeck(path: Path): SpyReadinessDeck {
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
    return SpyReadinessDeck(Deck(main), Deck(sideboard))
}

@Serializable
private data class SpyReadinessSmokeReport(
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
