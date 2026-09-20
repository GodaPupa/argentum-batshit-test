package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaAgents
import com.wingedsheep.ai.arena.TableGameRunner
import com.wingedsheep.ai.arena.TableSetup
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

/**
 * Cheap executable compatibility screen for the two Gate-1 survivors.
 *
 * This is deliberately not promotion evidence: the opponent is inert, mulligans are skipped, and
 * the stock AI has no Industrial Waste combo policy. Its only job is to prove that both exact
 * maindecks can traverse real Argentum games without an exception or rejected action before a
 * larger and more expensive goldfish harness is justified.
 */
class IndustrialWasteSmokeBenchmark : FunSpec({

    test("Industrial Waste engine smoke").config(
        enabled = System.getProperty("benchmark") == "true",
    ) {
        val repository = findRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val output = repository.resolve("industrial-waste/results/gate-2-engine-smoke-v1.json")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val decks = linkedMapOf(
            "control" to parseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "pactdoll-a" to parseMain(root.resolve("challengers/pactdoll-a.dck")),
        )
        decks.forEach { (name, deck) ->
            require(deck.size == 60) { "${name} must contain exactly 60 maindeck cards" }
            deck.uniqueCards().forEach(registry::requireCard)
        }

        val seeds = (1..SEED_COUNT).map { seedFor(SEED_NAMESPACE, it) }
        require(vectorDigest(seeds) == SEED_VECTOR_SHA256) { "engine-smoke seed vector drift" }
        require(Files.readString(root.resolve("seed-registry.json")).contains(SEED_NAMESPACE)) {
            "engine-smoke namespace is not registered"
        }

        val agent = ArenaAgents.resolve("v0")
        val inert = Deck.of("Forest" to 60)
        val outcomes = buildList {
            seeds.forEachIndexed { index, seed ->
                decks.forEach { (deckName, deck) ->
                    val game = TableGameRunner.play(
                        registry = registry,
                        setup = TableSetup.HEADS_UP,
                        agents = listOf(agent, agent),
                        decks = listOf(deck, inert),
                        seed = seed,
                        groupId = index + 1,
                        rotation = 0,
                        maxTurns = 12,
                        maxActions = 4_000,
                    )
                    add(
                        SmokeOutcome(
                            deck = deckName,
                            pair = index + 1,
                            seed = seed,
                            completed = game.completed,
                            winnerSeat = game.winnerSeat,
                            turns = game.turns,
                            actions = game.actions,
                            seat0Life = game.lifeBySeat[0],
                            seat1Life = game.lifeBySeat[1],
                            drawReason = game.drawReason,
                            exception = game.exception,
                            illegalActions = game.illegalActions,
                        )
                    )
                }
            }
        }
        val invalid = outcomes.filter { it.exception != null || it.illegalActions.isNotEmpty() }
        val report = SmokeReport(
            schemaVersion = 1,
            evidenceClass = "diagnostic-engine-smoke",
            promotionEligible = false,
            namespace = SEED_NAMESPACE,
            seedCount = seeds.size,
            seedVectorSha256 = SEED_VECTOR_SHA256,
            opponent = "60 Forest; v0 AI; inert capability opponent",
            mulligans = "skipped",
            maxTurnsPerSeat = 12,
            maxActions = 4_000,
            outcomes = outcomes,
            valid = invalid.isEmpty(),
        )
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(report) + "\n")
        check(invalid.isEmpty()) {
            "engine smoke found ${invalid.size} crash/rejection outcomes; see ${output}"
        }
    }
})

private const val SEED_NAMESPACE = "IW-G2-ENGINE-SMOKE-V1"
private const val SEED_COUNT = 4
private const val SEED_VECTOR_SHA256 =
    "677f86d5b15e990e887f11a25c05392bf52e11ae87e677c738712954d2d3d327"

private fun findRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root containing industrial-waste")
}

private fun seedFor(namespace: String, index: Int): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("${namespace}:${index}".toByteArray(Charsets.UTF_8))
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.and(Long.MAX_VALUE)
        .let { it.takeIf { value -> value != 0L } ?: 1L }
}

private fun vectorDigest(seeds: List<Long>): String =
    MessageDigest.getInstance("SHA-256")
        .digest(seeds.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun parseMain(path: Path): Deck {
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

@Serializable
private data class SmokeOutcome(
    val deck: String,
    val pair: Int,
    val seed: Long,
    val completed: Boolean,
    val winnerSeat: Int?,
    val turns: Int,
    val actions: Int,
    val seat0Life: Int,
    val seat1Life: Int,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
)

@Serializable
private data class SmokeReport(
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
    val outcomes: List<SmokeOutcome>,
    val valid: Boolean,
)
