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

class IndustrialWasteMonsterTronReadinessSmokeTest : FunSpec({
    test("Gate 9 Monster Tron exact-deck readiness smoke").config(
        enabled = System.getenv("IW_G9_MONSTER_TRON_READINESS_SMOKE") == "true",
    ) {
        val repository = monsterTronReadinessRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/monster-tron-pinoio-cosmico-2026-09-15.dck")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(opponentPath))
            .joinToString("") { "%02x".format(it) }
        require(digest == MONSTER_TRON_DECK_SHA256) {
            "Gate 9 opponent identity drift: $digest"
        }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val control = monsterTronReadinessParseMain(
            root.resolve("control/industrial-waste-v1.0-submitted.dck")
        )
        val opponent = monsterTronReadinessParseMain(opponentPath)
        listOf(control, opponent).forEach { deck ->
            require(deck.size == 60) { "readiness decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val industrialBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val industrial = ArenaAgent(
            "industrial-waste-g9-control",
            industrialBase.copy(
                id = "industrial-waste-g9-control",
                advisorModules = industrialBase.advisorModules + IndustrialWasteAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val monsterBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val monster = ArenaAgent(
            "monster-tron-g9-readiness",
            monsterBase.copy(
                id = "monster-tron-g9-readiness",
                advisorModules = monsterBase.advisorModules + MonsterTronAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val game = TableGameRunner.play(
            registry = registry,
            setup = TableSetup.HEADS_UP,
            agents = listOf(industrial, monster),
            decks = listOf(control, opponent),
            seed = G9_READINESS_FIXTURE_SEED,
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

        val report = MonsterTronReadinessSmokeReport(
            schemaVersion = 1,
            evidenceClass = "seed-free-deterministic-readiness-smoke",
            promotionEligible = false,
            fixtureSeed = G9_READINESS_FIXTURE_SEED,
            seedUse = "fixed development fixture; not registered and not matchup evidence",
            opponent = "Monster Tron — PinoIo_Cosmico, 2nd, 2026-09-15",
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

        val output = root.resolve("results/gate-9-monster-tron-readiness-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(report.actions > 0) { "Gate 9 readiness smoke made no legal progress" }
        check(report.valid) { "Gate 9 readiness smoke failed legal progression" }
    }
})

private const val MONSTER_TRON_DECK_SHA256 =
    "4d358e76a3566ed096299550f7400c9d63c79c2e715cd74c372faa38960428cc"
private const val G9_READINESS_FIXTURE_SEED = 0x1A57_B109L

private fun monsterTronReadinessRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun monsterTronReadinessParseMain(path: Path): Deck {
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
private data class MonsterTronReadinessSmokeReport(
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
