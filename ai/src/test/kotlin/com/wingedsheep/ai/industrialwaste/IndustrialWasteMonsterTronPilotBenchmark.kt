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

class IndustrialWasteMonsterTronPilotBenchmark : FunSpec({
    test("Industrial Waste paired Monster Tron capability pilot").config(
        enabled = System.getenv("IW_MONSTER_TRON_PILOT") == "true",
    ) {
        val repository = monsterTronPilotRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val opponentPath = root.resolve("gauntlet/monster-tron-pinoio-cosmico-2026-09-15.dck")
        val opponentDigest = monsterTronPilotFileDigest(opponentPath)
        require(opponentDigest == MONSTER_TRON_OPPONENT_SHA256) {
            "Monster Tron opponent identity drift: $opponentDigest"
        }

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val industrialDecks = linkedMapOf(
            "control" to monsterTronPilotParseMain(
                root.resolve("control/industrial-waste-v1.0-submitted.dck")
            ),
            "pactdoll-a" to monsterTronPilotParseMain(
                root.resolve("challengers/pactdoll-a.dck")
            ),
        )
        val opponent = monsterTronPilotParseMain(opponentPath)
        (industrialDecks.values + opponent).forEach { deck ->
            require(deck.size == 60) { "pilot decks must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..MONSTER_TRON_PILOT_SEED_COUNT).map {
            monsterTronPilotSeedFor(MONSTER_TRON_PILOT_NAMESPACE, it)
        }
        require(seeds == MONSTER_TRON_PILOT_FROZEN_SEEDS) {
            "Monster Tron pilot frozen seed vector drift: $seeds"
        }
        require(monsterTronPilotVectorDigest(seeds) == MONSTER_TRON_PILOT_VECTOR_SHA256) {
            "Monster Tron pilot seed vector digest drift"
        }
        require(Files.readString(root.resolve("seed-registry.json")).contains(MONSTER_TRON_PILOT_NAMESPACE)) {
            "Monster Tron pilot namespace is not registered"
        }

        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )

        val monsterBase = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        val monster = ArenaAgent(
            "monster-tron-gate-9-pilot",
            monsterBase.copy(
                id = "monster-tron-gate-9-pilot",
                advisorModules = monsterBase.advisorModules + MonsterTronAdvisorModule,
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
                            if (industrialSeat == 0) listOf(industrial, monster) else listOf(monster, industrial)
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
                            maxTurns = MONSTER_TRON_PILOT_MAX_TURNS,
                            maxActions = MONSTER_TRON_PILOT_MAX_ACTIONS,
                            trainingObserver = observer,
                            skipMulligans = false,
                        )

                        add(
                            MonsterTronPilotOutcome(
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
            MonsterTronPilotSummary(
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

        val report = MonsterTronPilotReport(
            schemaVersion = 1,
            evidenceClass = "preboard-matchup-capability-pilot",
            promotionEligible = false,
            namespace = MONSTER_TRON_PILOT_NAMESPACE,
            seedCount = seeds.size,
            seedVectorSha256 = MONSTER_TRON_PILOT_VECTOR_SHA256,
            opponent = "Monster Tron — PinoIo_Cosmico, 2nd, 2026-09-15",
            source = "https://mtgdecks.net/Pauper/mtgo-pauper-challenge-16-12854110-tournament-270190",
            opponentProfile = "monster-tron-gate-9-pilot",
            mulligans = "London mulligans enabled for both seats",
            maxTurnsPerSeat = MONSTER_TRON_PILOT_MAX_TURNS,
            maxActions = MONSTER_TRON_PILOT_MAX_ACTIONS,
            outcomes = outcomes,
            summaries = summaries,
            valid = invalid.isEmpty(),
        )

        val output = root.resolve("results/gate-9-monster-tron-pilot-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")

        check(invalid.isEmpty()) {
            "Monster Tron pilot found ${invalid.size} invalid outcomes; see $output"
        }
        check(outcomes.size == 8) { "Monster Tron pilot must produce exactly eight games" }
        check(industrialDecks.keys == setOf("control", "pactdoll-a")) {
            "Gate 9 identity set drifted"
        }
    }
})

private const val MONSTER_TRON_PILOT_NAMESPACE = "IW-G9-MONSTER-TRON-PILOT-V1"
private const val MONSTER_TRON_PILOT_SEED_COUNT = 2
private val MONSTER_TRON_PILOT_FROZEN_SEEDS =
    listOf(4887770374418762850L, 3072597163935011335L)
private const val MONSTER_TRON_PILOT_VECTOR_SHA256 =
    "0513dfbac2da94356299f289c8ea7de1746c3ae3dab6371e4368a996b20177a4"
private const val MONSTER_TRON_OPPONENT_SHA256 =
    "4d358e76a3566ed096299550f7400c9d63c79c2e715cd74c372faa38960428cc"
private const val MONSTER_TRON_PILOT_MAX_TURNS = 16
private const val MONSTER_TRON_PILOT_MAX_ACTIONS = 4_000

@Serializable
private data class MonsterTronPilotOutcome(
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
private data class MonsterTronPilotSummary(
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
private data class MonsterTronPilotReport(
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
    val outcomes: List<MonsterTronPilotOutcome>,
    val summaries: List<MonsterTronPilotSummary>,
    val valid: Boolean,
)

private fun monsterTronPilotRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun monsterTronPilotParseMain(path: Path): Deck {
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

private fun monsterTronPilotSeedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$namespace:$index".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun monsterTronPilotVectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun monsterTronPilotFileDigest(path: Path): String =
    MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
