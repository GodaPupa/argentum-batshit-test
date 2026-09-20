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

class IndustrialWasteGoldfishBenchmark : FunSpec({
    test("Industrial Waste paired metric goldfish").config(
        enabled = System.getenv("IW_GOLDFISH") == "true",
    ) {
        val policyCalibration = System.getenv("IW_POLICY_CALIBRATION") == "true"
        val policyCalibrationV2 = System.getenv("IW_POLICY_CALIBRATION_V2") == "true"
        val policyScreen = System.getenv("IW_POLICY_SCREEN") == "true"
        require(listOf(policyCalibration, policyCalibrationV2, policyScreen).count { it } <= 1) {
            "select only one policy run mode"
        }
        val policyEnabled = policyCalibration || policyCalibrationV2 || policyScreen
        val repository = goldfishRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val decks = linkedMapOf(
            "control" to goldfishParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "pactdoll-a" to goldfishParseMain(root.resolve("challengers/pactdoll-a.dck")),
        )
        decks.forEach { (name, deck) ->
            require(deck.size == 60) { "${name} must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val namespace = if (policyScreen) POLICY_SCREEN_NAMESPACE else SEED_NAMESPACE
        val seedCount = if (policyScreen) POLICY_SCREEN_SEED_COUNT else SEED_COUNT
        val expectedDigest = if (policyScreen) POLICY_SCREEN_VECTOR_SHA256 else SEED_VECTOR_SHA256
        val seeds = (1..seedCount).map { goldfishSeedFor(namespace, it) }
        require(goldfishVectorDigest(seeds) == expectedDigest) { "goldfish seed vector drift" }
        require(Files.readString(root.resolve("seed-registry.json")).contains(namespace)) {
            "goldfish namespace is not registered"
        }

        val controlAgent = ArenaAgents.resolve("v0")
        val industrialAgent = if (policyEnabled) {
            ArenaAgent(
                if (policyCalibrationV2) "industrial-waste-policy-v2" else "industrial-waste-policy-v1",
                AiProfile.LEGACY_V0.copy(
                    id = if (policyCalibrationV2) {
                        "industrial-waste-policy-v2"
                    } else {
                        "industrial-waste-policy-v1"
                    },
                    advisorModules = listOf(IndustrialWasteAdvisorModule),
                    considerAdvisedManaAbilities = policyCalibrationV2,
                ),
            )
        } else controlAgent
        val inert = Deck.of("Forest" to 60)
        val outcomes = buildList {
            seeds.forEachIndexed { index, seed ->
                decks.forEach { (deckName, deck) ->
                    val observer = IndustrialWasteGoldfishObserver(registry)
                    val game = TableGameRunner.play(
                        registry = registry,
                        setup = TableSetup.HEADS_UP,
                        agents = listOf(industrialAgent, controlAgent),
                        decks = listOf(deck, inert),
                        seed = seed,
                        groupId = index + 1,
                        rotation = 0,
                        maxTurns = MAX_TURNS,
                        maxActions = MAX_ACTIONS,
                        trainingObserver = observer,
                        skipMulligans = false,
                    )
                    add(
                        GoldfishOutcome(
                            deck = deckName,
                            pair = index + 1,
                            seed = seed,
                            completed = game.completed,
                            winnerSeat = game.winnerSeat,
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
        val invalid = outcomes.filter { outcome ->
            outcome.exception != null || outcome.illegalActions.isNotEmpty() ||
                (outcome.drawReason.isNotEmpty() && !outcome.drawReason.startsWith("maxTurns"))
        }
        val report = GoldfishReport(
            schemaVersion = 1,
            evidenceClass = when {
                policyScreen -> "diagnostic-engine-policy-screen"
                policyCalibration || policyCalibrationV2 -> "non-promotional-policy-calibration"
                else -> "diagnostic-engine-goldfish"
            },
            promotionEligible = false,
            namespace = namespace,
            seedCount = seeds.size,
            seedVectorSha256 = expectedDigest,
            opponent = "60 Forest; v0 AI; inert capability opponent; " +
                "Industrial seat=${industrialAgent.name}",
            mulligans = "London mulligans enabled for both seats",
            maxTurnsPerSeat = MAX_TURNS,
            maxActions = MAX_ACTIONS,
            outcomes = outcomes,
            summaries = decks.keys.map { deckName -> summarize(deckName, outcomes) },
            valid = invalid.isEmpty(),
        )
        val output = root.resolve(
            if (policyScreen) {
                "results/gate-3-policy-screen-v1.json"
            } else if (policyCalibrationV2) {
                "results/gate-3-policy-calibration-v2.json"
            } else if (policyCalibration) {
                "results/gate-3-policy-calibration-v1.json"
            } else {
                "results/gate-3-goldfish-screen-v1.json"
            }
        )
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")
        check(invalid.isEmpty()) {
            "goldfish screen found ${invalid.size} invalid outcomes; see ${output}"
        }
    }
})

private const val SEED_NAMESPACE = "IW-G3-GOLDFISH-S1"
private const val SEED_COUNT = 12
private const val SEED_VECTOR_SHA256 =
    "99a10b75cd996535fba7bd512c203d5e2287b993c3afe0ab2a0c428dfe22eef3"
private const val POLICY_SCREEN_NAMESPACE = "IW-G3-POLICY-S1"
private const val POLICY_SCREEN_SEED_COUNT = 16
private const val POLICY_SCREEN_VECTOR_SHA256 =
    "72d641cc85c1ca5bb0d260e061b4102405b5b706fd6e7cf22f101cdb5efb0270"
private const val MAX_TURNS = 12
private const val MAX_ACTIONS = 4_000

@Serializable
private data class GoldfishOutcome(
    val deck: String,
    val pair: Int,
    val seed: Long,
    val completed: Boolean,
    val winnerSeat: Int?,
    val turns: Int,
    val actions: Int,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
    val metrics: IndustrialWasteGoldfishMetrics,
)

@Serializable
private data class GoldfishSummary(
    val deck: String,
    val games: Int,
    val mulliganGameRate: Double,
    val meanMulligans: Double,
    val tronByTurn3Rate: Double,
    val tronByTurn4Rate: Double,
    val tronByTurn5Rate: Double,
    val meanColoredManaFailureTurns: Double,
    val retrieverLoopAvailabilityRate: Double,
    val comboReadyRate: Double,
    val lethalRate: Double,
    val meanRedundantPayoffDraws: Double,
    val meanNonInfinitePactdollLifeLoss: Double,
    val meanCombatDamageToOpponent: Double,
)

@Serializable
private data class GoldfishReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val namespace: String,
    val seedCount: Int,
    val seedVectorSha256: String,
    val opponent: String,
    val mulligans: String,
    val maxTurnsPerSeat: Int,
    val maxActions: Int,
    val outcomes: List<GoldfishOutcome>,
    val summaries: List<GoldfishSummary>,
    val valid: Boolean,
)

private fun summarize(deck: String, all: List<GoldfishOutcome>): GoldfishSummary {
    val outcomes = all.filter { it.deck == deck }
    val n = outcomes.size.toDouble()
    fun rate(predicate: (IndustrialWasteGoldfishMetrics) -> Boolean): Double =
        outcomes.count { predicate(it.metrics) } / n
    fun mean(value: (IndustrialWasteGoldfishMetrics) -> Int): Double =
        outcomes.sumOf { value(it.metrics) } / n
    return GoldfishSummary(
        deck = deck,
        games = outcomes.size,
        mulliganGameRate = rate { it.mulligans > 0 },
        meanMulligans = mean { it.mulligans },
        tronByTurn3Rate = rate { it.tronByTurn3 },
        tronByTurn4Rate = rate { it.tronByTurn4 },
        tronByTurn5Rate = rate { it.tronByTurn5 },
        meanColoredManaFailureTurns = mean { it.coloredManaFailureTurns },
        retrieverLoopAvailabilityRate = rate { it.retrieverLoopAvailableTurn != null },
        comboReadyRate = rate { it.comboReadyTurn != null },
        lethalRate = rate { it.lethalTurn != null },
        meanRedundantPayoffDraws = mean { it.redundantPayoffDraws },
        meanNonInfinitePactdollLifeLoss = mean { it.nonInfinitePactdollLifeLoss },
        meanCombatDamageToOpponent = mean { it.combatDamageToOpponent },
    )
}

private fun goldfishRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun goldfishSeedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${namespace}:${index}".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun goldfishVectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun goldfishParseMain(path: Path): Deck {
    var inMain = false
    val cards = buildList {
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            when {
                line == "[main]" -> inMain = true
                line.startsWith("[") -> inMain = false
                inMain && line.isNotEmpty() -> {
                    val split = line.indexOf(' ')
                    require(split > 0) { "Malformed deck line in ${path}: ${line}" }
                    val count = line.substring(0, split).toInt()
                    val name = line.substring(split + 1)
                    repeat(count) { add(name) }
                }
            }
        }
    }
    return Deck(cards)
}
