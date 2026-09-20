package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaAgent
import com.wingedsheep.ai.arena.ArenaAgents
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
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class IndustrialWasteMatchupPilotBenchmark : FunSpec({
    test("Industrial Waste paired Madness Burn pilot").config(
        enabled = System.getenv("IW_MATCHUP_PILOT") == "true" ||
            System.getenv("IW_MATCHUP_REPLICATION") == "true",
    ) {
        val replication = System.getenv("IW_MATCHUP_REPLICATION") == "true"
        val namespace = if (replication) REPLICATION_NAMESPACE else PILOT_NAMESPACE
        val seedCount = if (replication) REPLICATION_SEED_COUNT else PILOT_SEED_COUNT
        val expectedDigest = if (replication) REPLICATION_VECTOR_SHA256 else PILOT_VECTOR_SHA256
        val repository = matchupRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val industrialDecks = linkedMapOf(
            "control" to matchupParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "pactdoll-a" to matchupParseMain(root.resolve("challengers/pactdoll-a.dck")),
        )
        val opponent = matchupParseMain(
            root.resolve("gauntlet/madness-burn-canevazzi-2026-09-12.dck")
        )
        (industrialDecks.values + opponent).forEach { deck ->
            require(deck.size == 60) { "pilot decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..seedCount).map { matchupSeedFor(namespace, it) }
        require(matchupVectorDigest(seeds) == expectedDigest) { "matchup seed vector drift" }
        require(Files.readString(root.resolve("seed-registry.json")).contains(namespace)) {
            "matchup namespace is not registered"
        }

        val stock = ArenaAgents.resolve("v0")
        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )
        val outcomes = buildList {
            seeds.forEachIndexed { seedIndex, seed ->
                industrialDecks.forEach { (deckName, deck) ->
                    repeat(2) { rotation ->
                        val industrialSeat = rotation
                        val observer = IndustrialWasteGoldfishObserver(registry, industrialSeat)
                        val agents = if (industrialSeat == 0) {
                            listOf(industrial, stock)
                        } else {
                            listOf(stock, industrial)
                        }
                        val decks = if (industrialSeat == 0) {
                            listOf(deck, opponent)
                        } else {
                            listOf(opponent, deck)
                        }
                        val game = TableGameRunner.play(
                            registry = registry,
                            setup = TableSetup.HEADS_UP,
                            agents = agents,
                            decks = decks,
                            seed = seed,
                            groupId = seedIndex + 1,
                            rotation = rotation,
                            maxTurns = MAX_TURNS,
                            maxActions = MAX_ACTIONS,
                            trainingObserver = observer,
                            skipMulligans = false,
                        )
                        add(
                            MatchupPilotOutcome(
                                deck = deckName,
                                pair = seedIndex + 1,
                                seed = seed,
                                industrialSeat = industrialSeat,
                                completed = game.completed,
                                winnerSeat = game.winnerSeat,
                                industrialWon = game.winnerSeat == industrialSeat,
                                opponentWon = game.winnerSeat == 1 - industrialSeat,
                                turns = game.turns,
                                actions = game.actions,
                                drawReason = game.drawReason,
                                exception = game.exception,
                                illegalActions = game.illegalActions,
                                metrics = observer.snapshot(),
                            )
                        )
                    }
                }
            }
        }
        val invalid = outcomes.filter { outcome ->
            outcome.exception != null || outcome.illegalActions.isNotEmpty() ||
                (outcome.drawReason.isNotEmpty() &&
                    !outcome.drawReason.startsWith("maxTurns") &&
                    !outcome.drawReason.startsWith("maxActions"))
        }
        val report = MatchupPilotReport(
            schemaVersion = 1,
            evidenceClass = if (replication) {
                "preboard-matchup-screen"
            } else {
                "preboard-matchup-capability-pilot"
            },
            promotionEligible = false,
            namespace = namespace,
            seedCount = seeds.size,
            seedVectorSha256 = expectedDigest,
            opponent = "Madness Burn — Davide Canevazzi, 43rd Super Ingenio, 2026-09-12",
            source = "https://www.mtgtop8.com/event?d=889937&e=90850&f=PAU",
            mulligans = "London mulligans enabled for both seats",
            maxTurnsPerSeat = MAX_TURNS,
            maxActions = MAX_ACTIONS,
            outcomes = outcomes,
            summaries = industrialDecks.keys.map { name -> matchupSummary(name, outcomes) },
            valid = invalid.isEmpty(),
        )
        val output = root.resolve(
            if (replication) {
                "results/gate-4-madness-burn-replication-v1.json"
            } else {
                "results/gate-4-madness-burn-pilot-v1.json"
            }
        )
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")
        check(invalid.isEmpty()) { "matchup pilot found ${invalid.size} invalid outcomes; see $output" }
    }
})

private const val PILOT_NAMESPACE = "IW-G4-MADNESS-BURN-PILOT-V1"
private const val PILOT_SEED_COUNT = 4
private const val PILOT_VECTOR_SHA256 =
    "6b4ef63509e4bf4edfa1863791487b6e6cb27fee2c4b437f164a41ebba8f5c7e"
private const val REPLICATION_NAMESPACE = "IW-G4-MADNESS-BURN-R1"
private const val REPLICATION_SEED_COUNT = 8
private const val REPLICATION_VECTOR_SHA256 =
    "011e5fdfb0d05340968c4df3fd4bde6628a1a5fc9364ebc045d1a62daafec7ef"
private const val MAX_TURNS = 16
private const val MAX_ACTIONS = 4_000

@Serializable
private data class MatchupPilotOutcome(
    val deck: String,
    val pair: Int,
    val seed: Long,
    val industrialSeat: Int,
    val completed: Boolean,
    val winnerSeat: Int?,
    val industrialWon: Boolean,
    val opponentWon: Boolean,
    val turns: Int,
    val actions: Int,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
    val metrics: IndustrialWasteGoldfishMetrics,
)

@Serializable
private data class MatchupPilotSummary(
    val deck: String,
    val games: Int,
    val industrialWins: Int,
    val opponentWins: Int,
    val draws: Int,
    val onPlayWins: Int,
    val onDrawWins: Int,
    val tronByTurn5: Int,
    val comboReady: Int,
    val meanMulligans: Double,
    val meanColoredManaFailureTurns: Double,
)

@Serializable
private data class MatchupPilotReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val namespace: String,
    val seedCount: Int,
    val seedVectorSha256: String,
    val opponent: String,
    val source: String,
    val mulligans: String,
    val maxTurnsPerSeat: Int,
    val maxActions: Int,
    val outcomes: List<MatchupPilotOutcome>,
    val summaries: List<MatchupPilotSummary>,
    val valid: Boolean,
)

private fun matchupSummary(deck: String, all: List<MatchupPilotOutcome>): MatchupPilotSummary {
    val outcomes = all.filter { it.deck == deck }
    val n = outcomes.size.toDouble()
    return MatchupPilotSummary(
        deck = deck,
        games = outcomes.size,
        industrialWins = outcomes.count { it.industrialWon },
        opponentWins = outcomes.count { it.opponentWon },
        draws = outcomes.count { !it.industrialWon && !it.opponentWon },
        onPlayWins = outcomes.count { it.industrialSeat == 0 && it.industrialWon },
        onDrawWins = outcomes.count { it.industrialSeat == 1 && it.industrialWon },
        tronByTurn5 = outcomes.count { it.metrics.tronByTurn5 },
        comboReady = outcomes.count { it.metrics.comboReadyTurn != null },
        meanMulligans = outcomes.sumOf { it.metrics.mulligans } / n,
        meanColoredManaFailureTurns = outcomes.sumOf { it.metrics.coloredManaFailureTurns } / n,
    )
}

private fun matchupRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun matchupParseMain(path: Path): Deck {
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

private fun matchupSeedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace:$index".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun matchupVectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
