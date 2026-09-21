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

class IndustrialWasteStabilityGrixisPilotBenchmark : FunSpec({
    test("Industrial Waste Stability A paired Grixis pilot").config(
        enabled = System.getenv("IW_STABILITY_GRIXIS_PILOT") == "true",
    ) {
        val repository = stabilityGrixisRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val industrialDecks = linkedMapOf(
            "control" to stabilityGrixisParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "stability-a" to stabilityGrixisParseMain(root.resolve("challengers/stability-a.dck")),
        )
        val opponent = stabilityGrixisParseMain(
            root.resolve("gauntlet/grixis-affinity-carlos-dc-2026-09-12.dck")
        )
        (industrialDecks.values + opponent).forEach { deck ->
            require(deck.size == 60) { "pilot decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..STABILITY_GRIXIS_SEED_COUNT).map {
            stabilityGrixisSeedFor(STABILITY_GRIXIS_NAMESPACE, it)
        }
        require(stabilityGrixisVectorDigest(seeds) == STABILITY_GRIXIS_VECTOR_SHA256) {
            "Stability A Grixis pilot seed vector drift"
        }
        require(Files.readString(root.resolve("seed-registry.json")).contains(STABILITY_GRIXIS_NAMESPACE)) {
            "Stability A Grixis pilot namespace is not registered"
        }

        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )
        val grixisBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val grixis = ArenaAgent(
            "grixis-gate-6-readiness",
            grixisBase.copy(
                id = "grixis-gate-6-readiness",
                advisorModules = grixisBase.advisorModules + IndustrialWasteGrixisAdvisorModule,
            ),
        )

        val outcomes = buildList {
            seeds.forEachIndexed { seedIndex, seed ->
                industrialDecks.forEach { (deckName, deck) ->
                    repeat(2) { rotation ->
                        val industrialSeat = rotation
                        val observer = IndustrialWasteGoldfishObserver(registry, industrialSeat)
                        val agents = if (industrialSeat == 0) listOf(industrial, grixis) else listOf(grixis, industrial)
                        val decks = if (industrialSeat == 0) listOf(deck, opponent) else listOf(opponent, deck)
                        val game = TableGameRunner.play(
                            registry = registry,
                            setup = TableSetup.HEADS_UP,
                            agents = agents,
                            decks = decks,
                            seed = seed,
                            groupId = seedIndex + 1,
                            rotation = rotation,
                            maxTurns = STABILITY_GRIXIS_MAX_TURNS,
                            maxActions = STABILITY_GRIXIS_MAX_ACTIONS,
                            trainingObserver = observer,
                            skipMulligans = false,
                        )
                        add(
                            StabilityGrixisOutcome(
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

        val summaries = industrialDecks.keys.map { deck ->
            val selected = outcomes.filter { it.deck == deck }
            StabilityGrixisSummary(
                deck = deck,
                games = selected.size,
                industrialWins = selected.count { it.industrialWon },
                opponentWins = selected.count { it.opponentWon },
                draws = selected.count { !it.industrialWon && !it.opponentWon },
                tronByTurn5 = selected.count { it.metrics.tronByTurn5 },
                comboReady = selected.count { it.metrics.comboReadyTurn != null },
                meanMulligans = selected.sumOf { it.metrics.mulligans } / selected.size.toDouble(),
                meanColoredManaFailureTurns = selected.sumOf { it.metrics.coloredManaFailureTurns } /
                    selected.size.toDouble(),
            )
        }

        val report = StabilityGrixisReport(
            schemaVersion = 1,
            evidenceClass = "preboard-matchup-capability-pilot",
            promotionEligible = false,
            namespace = STABILITY_GRIXIS_NAMESPACE,
            seedCount = seeds.size,
            seedVectorSha256 = STABILITY_GRIXIS_VECTOR_SHA256,
            opponent = "Grixis Affinity — Carlos Dc, Top 8, 2026-09-12",
            source = "https://www.mtgtop8.com/event?d=889936&e=90850&f=PAU",
            opponentProfile = "grixis-gate-6-readiness",
            mulligans = "London mulligans enabled for both seats",
            maxTurnsPerSeat = STABILITY_GRIXIS_MAX_TURNS,
            maxActions = STABILITY_GRIXIS_MAX_ACTIONS,
            outcomes = outcomes,
            summaries = summaries,
            valid = invalid.isEmpty(),
        )

        val output = root.resolve("results/gate-6-stability-a-grixis-pilot-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")
        check(invalid.isEmpty()) { "Stability A Grixis pilot found ${invalid.size} invalid outcomes" }
    }
})

private const val STABILITY_GRIXIS_NAMESPACE = "IW-G6-STABILITY-A-GRIXIS-PILOT-V1"
private const val STABILITY_GRIXIS_SEED_COUNT = 2
private const val STABILITY_GRIXIS_VECTOR_SHA256 =
    "8b53d91ae81e4e0194b89cb3381b5bffd8e9c51bbf1472361caed08ab278c1c9"
private const val STABILITY_GRIXIS_MAX_TURNS = 16
private const val STABILITY_GRIXIS_MAX_ACTIONS = 4_000

@Serializable
private data class StabilityGrixisOutcome(
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
private data class StabilityGrixisSummary(
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
private data class StabilityGrixisReport(
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
    val outcomes: List<StabilityGrixisOutcome>,
    val summaries: List<StabilityGrixisSummary>,
    val valid: Boolean,
)

private fun stabilityGrixisRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun stabilityGrixisParseMain(path: Path): Deck {
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

private fun stabilityGrixisSeedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace:$index".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun stabilityGrixisVectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
