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

class IndustrialWasteStabilitySmokeBenchmark : FunSpec({
    test("Stability A engine compatibility smoke").config(
        enabled = System.getenv("IW_STABILITY_SMOKE") == "true",
    ) {
        val repository = stabilityRepositoryRoot()
        val root = repository.resolve("industrial-waste")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val decks = linkedMapOf(
            "control" to stabilityParseMain(root.resolve("control/industrial-waste-v1.0-submitted.dck")),
            "stability-a" to stabilityParseMain(root.resolve("challengers/stability-a.dck")),
        )
        decks.values.forEach { deck ->
            require(deck.size == 60)
            deck.uniqueCards().forEach(registry::requireCard)
        }
        val industrial = ArenaAgent(
            "industrial-waste-policy-v2",
            AiProfile.LEGACY_V0.copy(
                id = "industrial-waste-policy-v2",
                advisorModules = listOf(IndustrialWasteAdvisorModule),
                considerAdvisedManaAbilities = true,
            ),
        )
        val inert = ArenaAgent("stability-smoke-inert", AiProfile.LEGACY_V0)
        val forest = Deck.of("Forest" to 60)
        val outcomes = buildList {
            STABILITY_SMOKE_FIXTURES.forEachIndexed { index, seed ->
                decks.forEach { (name, deck) ->
                    val observer = IndustrialWasteGoldfishObserver(registry, 0)
                    val game = TableGameRunner.play(
                        registry = registry,
                        setup = TableSetup.HEADS_UP,
                        agents = listOf(industrial, inert),
                        decks = listOf(deck, forest),
                        seed = seed,
                        groupId = index + 1,
                        rotation = 0,
                        maxTurns = 12,
                        maxActions = 4_000,
                        trainingObserver = observer,
                        skipMulligans = false,
                    )
                    add(
                        StabilitySmokeOutcome(
                            deck = name,
                            fixture = index + 1,
                            completed = game.completed,
                            drawReason = game.drawReason,
                            exception = game.exception,
                            illegalActions = game.illegalActions,
                            metrics = observer.snapshot(),
                        )
                    )
                }
            }
        }
        val invalid = outcomes.filter {
            it.exception != null || it.illegalActions.isNotEmpty() ||
                (it.drawReason.isNotEmpty() &&
                    !it.drawReason.startsWith("maxTurns") &&
                    !it.drawReason.startsWith("maxActions"))
        }
        val output = root.resolve("results/gate-6-stability-a-engine-smoke-v1.json")
        output.parent?.let { Files.createDirectories(it) }
        Files.writeString(
            output,
            Json { prettyPrint = true }.encodeToString(
                StabilitySmokeReport(
                    schemaVersion = 1,
                    evidenceClass = "seed-free-deterministic-engine-smoke",
                    promotionEligible = false,
                    seedUse = "fixed development fixtures; not registered and not matchup evidence",
                    outcomes = outcomes,
                    valid = invalid.isEmpty(),
                )
            ) + "\n"
        )
        check(invalid.isEmpty()) { "Stability A smoke found ${invalid.size} invalid outcomes" }
    }
})

private val STABILITY_SMOKE_FIXTURES = listOf(0x57AB_1A01L, 0x57AB_1A02L)

@Serializable
private data class StabilitySmokeOutcome(
    val deck: String,
    val fixture: Int,
    val completed: Boolean,
    val drawReason: String,
    val exception: String?,
    val illegalActions: Map<String, Int>,
    val metrics: IndustrialWasteGoldfishMetrics,
)

@Serializable
private data class StabilitySmokeReport(
    val schemaVersion: Int,
    val evidenceClass: String,
    val promotionEligible: Boolean,
    val seedUse: String,
    val outcomes: List<StabilitySmokeOutcome>,
    val valid: Boolean,
)

private fun stabilityRepositoryRoot(): Path {
    var candidate: Path? = Path.of("").toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve("industrial-waste"))) return candidate
        candidate = candidate.parent
    }
    error("Cannot locate repository root")
}

private fun stabilityParseMain(path: Path): Deck {
    var inMain = false
    val cards = buildList {
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trim()
            when {
                line == "[main]" -> inMain = true
                line.startsWith("[") -> inMain = false
                inMain && line.isNotEmpty() -> {
                    val split = line.indexOf(' ')
                    val count = line.substring(0, split).toInt()
                    val name = line.substring(split + 1)
                    repeat(count) { add(name) }
                }
            }
        }
    }
    return Deck(cards)
}
