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

class IndustrialWasteJundPilotBenchmark : FunSpec({
    test("Industrial Waste paired Jund Wildfire capability pilot").config(
        enabled = System.getenv("IW_JUND_PILOT") == "true",
    ) {
        val repository = jundPilotRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/jund-wildfire-manohito-2026-09-15.dck")
        val opponentDigest = jundPilotFileDigest(opponentPath)
        require(opponentDigest == JUND_OPPONENT_SHA256) { "Jund opponent identity drift: $opponentDigest" }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val industrialDecks = linkedMapOf(
            "control" to jundPilotParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "pactdoll-a" to jundPilotParseMain(root.resolve("challengers/pactdoll-a.dck")),
        )
        val opponent = jundPilotParseMain(opponentPath)
        (industrialDecks.values + opponent).forEach { deck ->
            require(deck.size == 60) { "pilot decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..JUND_PILOT_SEED_COUNT).map {
            jundPilotSeedFor(JUND_PILOT_NAMESPACE, it)
        }
        require(jundPilotVectorDigest(seeds) == JUND_PILOT_VECTOR_SHA256) {
            "Jund pilot seed vector drift"
        }
        require(Files.readString(root.resolve("seed-registry.json")).contains(JUND_PILOT_NAMESPACE)) {
            "Jund pilot namespace is not registered"
        }

        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )
        val jundBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val jund = ArenaAgent(
            "jund-wildfire-gate-8-pilot",
            jundBase.copy(
                id = "jund-wildfire-gate-8-pilot",
                advisorModules = jundBase.advisorModules + JundWildfireAdvisorModule,
                considerAdvisedManaAbilities = true,
            ),
        )

        val outcomes = buildList {
            seeds.forEachIndexed { seedIndex, seed ->
                industrialDecks.forEach { (deckName, deck) ->
                    repeat(2) { rotation ->
                        val industrialSeat = rotation
                        val observer = IndustrialWasteGoldfishObserver(registry, industrialSeat)
                        val agents = if (industrialSeat == 0) listOf(industrial, jund) else listOf(jund, industrial)
                        val decks = if (industrialSeat == 0) listOf(deck, opponent) else listOf(opponent, deck)
                        val game = TableGameRunner.play(
                            registry = registry,
                            setup = TableSetup.HEADS_UP,
                            agents = agents,
                            decks = decks,
                            seed = seed,
                            groupId = seedIndex + 1,
                            rotation = rotation,
                            maxTurns = JUND_PILOT_MAX_TURNS,
                            maxActions = JUND_PILOT_MAX_ACTIONS,
                            trainingObserver = observer,
                            skipMulligans = false,
                        )
                        add(
                            JundPilotOutcome(
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
            JundPilotSummary(
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

        val report = JundPilotReport(
            schemaVersion = 1,
            evidenceClass = "preboard-matchup-capability-pilot",
            promotionEligible = false,
            namespace = JUND_PILOT_NAMESPACE,
            seedCount = seeds.size,
            seedVectorSha256 = JUND_PILOT_VECTOR_SHA256,
            opponent = "Jund Wildfire — manohito, Top 4, 2026-09-15",
            source = "https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854110-tournament-270190",
            opponentProfile = "jund-wildfire-gate-8-pilot",
            mulligans = "London mulligans enabled for both seats",
            maxTurnsPerSeat = JUND_PILOT_MAX_TURNS,
            maxActions = JUND_PILOT_MAX_ACTIONS,
            outcomes = outcomes,
            summaries = summaries,
            valid = invalid.isEmpty(),
        )
        val output = root.resolve("results/gate-8-jund-pilot-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")
        check(invalid.isEmpty()) { "Jund pilot found ${invalid.size} invalid outcomes; see $output" }
    }
})

private const val JUND_PILOT_NAMESPACE = "IW-G8-JUND-PILOT-V1"
private const val JUND_PILOT_SEED_COUNT = 2
private const val JUND_PILOT_VECTOR_SHA256 =
    "077c92b6d6d31bf82fc4e2b13d2f1336126f3a080801c69573f9d2d6c987ff7e"
private const val JUND_OPPONENT_SHA256 =
    "b3c5722946b3e32adc0c716a7b08a0688f2ab62b0f6bb87d9d0faec12c09cc29"
private const val JUND_PILOT_MAX_TURNS = 16
private const val JUND_PILOT_MAX_ACTIONS = 4_000

@Serializable
private data class JundPilotOutcome(
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
private data class JundPilotSummary(
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
private data class JundPilotReport(
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
    val outcomes: List<JundPilotOutcome>,
    val summaries: List<JundPilotSummary>,
    val valid: Boolean,
)

private fun jundPilotRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun jundPilotParseMain(path: Path): Deck {
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

private fun jundPilotSeedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace:$index".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun jundPilotVectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun jundPilotFileDigest(path: Path): String =
    MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
