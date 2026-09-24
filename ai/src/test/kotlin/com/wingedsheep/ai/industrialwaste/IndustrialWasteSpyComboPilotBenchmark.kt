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
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class IndustrialWasteSpyComboPilotBenchmark : FunSpec({
    test("Industrial Waste paired Spy Combo capability pilot").config(
        enabled = System.getenv("IW_SPY_COMBO_PILOT") == "true",
    ) {
        val repository = spyPilotRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/spy-combo-drinkme-2026-09-16.dck")
        val opponentDigest = spyPilotFileDigest(opponentPath)
        require(opponentDigest == SPY_OPPONENT_SHA256) {
            "Spy Combo opponent identity drift: $opponentDigest"
        }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val industrialDecks = linkedMapOf(
            "control" to spyPilotParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "pactdoll-a" to spyPilotParseMain(root.resolve("challengers/pactdoll-a.dck")),
        )
        val opponent = spyPilotParseMain(opponentPath)
        (industrialDecks.values + opponent).forEach { deck ->
            require(deck.size == 60) { "pilot decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..SPY_PILOT_SEED_COUNT).map { spyPilotSeedFor(SPY_PILOT_NAMESPACE, it) }
        require(seeds == SPY_PILOT_FROZEN_SEEDS) {
            "Spy pilot frozen seed vector drift: $seeds"
        }
        require(spyPilotVectorDigest(seeds) == SPY_PILOT_VECTOR_SHA256) {
            "Spy pilot seed vector digest drift"
        }
        require(Files.readString(root.resolve("seed-registry.json")).contains(SPY_PILOT_NAMESPACE)) {
            "Spy pilot namespace is not registered"
        }

        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )

        val spyBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val spy = ArenaAgent(
            "spy-combo-gate-11-pilot",
            spyBase.copy(
                id = "spy-combo-gate-11-pilot",
                advisorModules = spyBase.advisorModules + SpyComboAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val outcomes = buildList {
            seeds.forEachIndexed { seedIndex, seed ->
                industrialDecks.forEach { (deckName, deck) ->
                    repeat(2) { rotation ->
                        val industrialSeat = rotation
                        val observer = IndustrialWasteGoldfishObserver(registry, industrialSeat)
                        val agents =
                            if (industrialSeat == 0) listOf(industrial, spy) else listOf(spy, industrial)
                        val decks =
                            if (industrialSeat == 0) listOf(deck, opponent) else listOf(opponent, deck)

                        val game = TableGameRunner.play(
                            registry = registry,
                            setup = TableSetup.HEADS_UP,
                            agents = agents,
                            decks = decks,
                            seed = seed,
                            groupId = seedIndex + 1,
                            rotation = rotation,
                            maxTurns = SPY_PILOT_MAX_TURNS,
                            maxActions = SPY_PILOT_MAX_ACTIONS,
                            trainingObserver = observer,
                            skipMulligans = false,
                        )

                        add(
                            SpyPilotOutcome(
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
            outcome.exception != null ||
                outcome.illegalActions.isNotEmpty() ||
                (outcome.drawReason.isNotEmpty() &&
                    !outcome.drawReason.startsWith("maxTurns") &&
                    !outcome.drawReason.startsWith("maxActions"))
        }

        val summaries = industrialDecks.keys.map { deck ->
            val selected = outcomes.filter { it.deck == deck }
            SpyPilotSummary(
                deck = deck,
                games = selected.size,
                industrialWins = selected.count { it.industrialWon },
                opponentWins = selected.count { it.opponentWon },
                draws = selected.count { !it.industrialWon && !it.opponentWon },
                tronByTurn5 = selected.count { it.metrics.tronByTurn5 },
                comboReady = selected.count { it.metrics.comboReadyTurn != null },
                meanMulligans = selected.sumOf { it.metrics.mulligans } / selected.size.toDouble(),
                meanColoredManaFailureTurns =
                    selected.sumOf { it.metrics.coloredManaFailureTurns } / selected.size.toDouble(),
            )
        }

        val report = SpyPilotReport(
            schemaVersion = 1,
            evidenceClass = "preboard-matchup-capability-pilot",
            promotionEligible = false,
            namespace = SPY_PILOT_NAMESPACE,
            seedCount = seeds.size,
            seedVectorSha256 = SPY_PILOT_VECTOR_SHA256,
            opponent = "Spy Combo — Drinkme, Top 16 (4-2), 2026-09-16",
            source = "https://mtgdecks.net/Pauper/mtgo-pauper-challenge-32-12854119-tournament-270270",
            opponentProfile = "spy-combo-gate-11-pilot",
            mulligans = "London mulligans enabled for both seats",
            maxTurnsPerSeat = SPY_PILOT_MAX_TURNS,
            maxActions = SPY_PILOT_MAX_ACTIONS,
            outcomes = outcomes,
            summaries = summaries,
            valid = invalid.isEmpty(),
        )

        val output = root.resolve("results/gate-11-spy-combo-pilot-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(invalid.isEmpty()) {
            "Spy pilot found ${invalid.size} invalid outcomes; see $output"
        }
        check(outcomes.size == 8) { "Spy pilot must produce exactly eight games" }
        check(industrialDecks.keys == setOf("control", "pactdoll-a")) {
            "Gate 11 identity set drifted"
        }
    }
})

private const val SPY_PILOT_NAMESPACE = "IW-G11-SPY-COMBO-PILOT-V1"
private const val SPY_PILOT_SEED_COUNT = 2
private val SPY_PILOT_FROZEN_SEEDS =
    listOf(1152348211386038845L, 7755587151185123565L)
private const val SPY_PILOT_VECTOR_SHA256 =
    "be34b18805d92b3ef418eba43e063942c56a21261325cc2151e9e0d8783cb0f4"
private const val SPY_OPPONENT_SHA256 =
    "d01a41caed140d361fb5bb1a8d5b224f27161a7e4732efc202636029a7a6de2f"
private const val SPY_PILOT_MAX_TURNS = 16
private const val SPY_PILOT_MAX_ACTIONS = 4_000

@Serializable
private data class SpyPilotOutcome(
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
private data class SpyPilotSummary(
    val deck: String,
    val games: Int,
    val industrialWins: Int,
    val opponentWins: Int,
    val draws: Int,
    val tronByTurn5: Int,
    val comboReady: Int,
    val meanMulligans: Double,
    val meanColoredManaFailureTurns: Double,
)

@Serializable
private data class SpyPilotReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val namespace: String,
    val seedCount: Int,
    val seedVectorSha256: String,
    val opponent: String,
    val source: String,
    val opponentProfile: String,
    val mulligans: String,
    val maxTurnsPerSeat: Int,
    val maxActions: Int,
    val outcomes: List<SpyPilotOutcome>,
    val summaries: List<SpyPilotSummary>,
    val valid: Boolean,
)

private fun spyPilotRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun spyPilotParseMain(path: Path): Deck {
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

private fun spyPilotSeedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace:$index".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun spyPilotVectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun spyPilotFileDigest(path: Path): String =
    MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
