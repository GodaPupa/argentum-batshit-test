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

class IndustrialWasteJundReadinessSmokeTest : FunSpec({
    test("Gate 8 Jund Wildfire exact-deck readiness smoke").config(
        enabled = System.getenv("IW_G8_JUND_READINESS_SMOKE") == "true",
    ) {
        val repository = jundReadinessRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/jund-wildfire-manohito-2026-09-15.dck")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(opponentPath))
            .joinToString("") { "%02x".format(it) }
        require(digest == JUND_DECK_SHA256) { "Gate 8 opponent identity drift: $digest" }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val control = jundReadinessParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck"))
        val opponent = jundReadinessParseMain(opponentPath)
        listOf(control, opponent).forEach { deck ->
            require(deck.size == 60) { "readiness decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-g8-control",
            industrialBase.copy(
                id = "industrial-waste-g8-control",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )
        val jundBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val jund = ArenaAgent(
            "jund-wildfire-g8-readiness",
            jundBase.copy(
                id = "jund-wildfire-g8-readiness",
                advisorModules = jundBase.advisorModules + JundWildfireAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, jund),
            decks = listOf(control, opponent),
            seed = G8_READINESS_FIXTURE_SEED,
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
        val report = JundReadinessSmokeReport(
            schemaVersion = 1,
            evidenceClass = "seed-free-deterministic-readiness-smoke",
            promotionEligible = false,
            fixtureSeed = G8_READINESS_FIXTURE_SEED,
            seedUse = "fixed development fixture; not registered and not matchup evidence",
            opponent = "Jund Wildfire — manohito, Top 4, 2026-09-15",
            source = "https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854110-tournament-270190",
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

        val output = root.resolve("results/gate-8-jund-readiness-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(report.actions > 0) { "Gate 8 readiness smoke made no legal progress" }
        check(report.valid) { "Gate 8 readiness smoke failed legal progression" }
    }
})

private const val JUND_DECK_SHA256 =
    "b3c5722946b3e32adc0c716a7b08a0688f2ab62b0f6bb87d9d0faec12c09cc29"
private const val G8_READINESS_FIXTURE_SEED = 0x1A57_B10DL

private fun jundReadinessRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun jundReadinessParseMain(path: Path): Deck {
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
private data class JundReadinessSmokeReport(
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
