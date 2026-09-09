package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Paired-counterfactual experiment boundary for Batshit Economics optimization.
 *
 * This deliberately accepts exactly two arms: the permanently frozen control and one candidate.
 * Both arms use the same opponent, seed, play/draw assignment, mulligan policy, agent profile, and
 * event logger because they enter through [playLoggedGame]. No result-dependent retry path exists.
 */
internal class BatshitPairedCounterfactualFramework(
    private val registry: CardRegistry,
    private val control: Deck,
    private val variant: Deck,
    private val declaredDifference: DeclaredDeckDifference,
) {
    init {
        assertOnlyDeclaredDeckDifference(control, variant, declaredDifference)
    }

    fun run(seeds: List<Long>): List<PairedGameResult> {
        require(seeds.isNotEmpty()) { "The paired seed vector must not be empty" }
        require(seeds.distinct().size == seeds.size) { "The paired seed vector contains duplicates" }

        return seeds.mapIndexed { index, seed ->
            val startingPlayerIndex = index % 2
            PairedGameResult(
                pairNumber = index + 1,
                seed = seed,
                startingPlayerIndex = startingPlayerIndex,
                control = playLoggedGame(registry, index + 1, seed, startingPlayerIndex, control),
                variant = playLoggedGame(registry, index + 1, seed, startingPlayerIndex, variant),
            )
        }
    }

    fun writeArtifacts(results: List<PairedGameResult>, outputDirectory: Path) {
        Files.createDirectories(outputDirectory)
        Files.writeString(
            outputDirectory.resolve("paired-results.log"),
            buildString {
                results.forEach { pair ->
                    appendLine("=== PAIR ${pair.pairNumber} seed=${pair.seed} startingPlayerIndex=${pair.startingPlayerIndex} ===")
                    appendLine("--- CONTROL ---")
                    append(pair.control.log)
                    appendLine("--- VARIANT ---")
                    append(pair.variant.log)
                }
            },
        )
        Files.writeString(
            outputDirectory.resolve("paired-results.jsonl"),
            results.joinToString(separator = "\n", postfix = "\n") { pair ->
                "{" +
                    "\"pair\":${pair.pairNumber}," +
                    "\"seed\":${pair.seed}," +
                    "\"startingPlayerIndex\":${pair.startingPlayerIndex}," +
                    "\"control\":${pair.control.toJson()}," +
                    "\"variant\":${pair.variant.toJson()}" +
                    "}"
            },
        )
    }
}

private fun LoggedSmokeGame.toJson(): String =
    "{" +
        "\"completed\":$completed," +
        "\"actions\":$actions," +
        "\"fullTelemetryLog\":\"${log.jsonEscape()}\"" +
        "}"

private fun String.jsonEscape(): String = buildString(length) {
    this@jsonEscape.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}

internal data class PairedGameResult(
    val pairNumber: Int,
    val seed: Long,
    val startingPlayerIndex: Int,
    val control: LoggedSmokeGame,
    val variant: LoggedSmokeGame,
)

internal data class DeclaredDeckDifference(
    val mainboardRemoved: Map<String, Int> = emptyMap(),
    val mainboardAdded: Map<String, Int> = emptyMap(),
    val sideboardRemoved: Map<String, Int> = emptyMap(),
    val sideboardAdded: Map<String, Int> = emptyMap(),
)

internal fun assertOnlyDeclaredDeckDifference(
    control: Deck,
    variant: Deck,
    declared: DeclaredDeckDifference,
) {
    check(control.commander == variant.commander) { "The experiment may not change the commander" }
    check(control.commanderPrinting == variant.commanderPrinting) {
        "The experiment may not change the commander printing"
    }
    check(control.cardEntries == variant.cardEntries) {
        "Printing-pinned mainboard entries are outside the declared name-only deck difference"
    }

    fun counts(cards: List<String>): Map<String, Int> =
        cards.groupingBy { it }.eachCount().toSortedMap()

    fun sideboardCounts(cards: List<com.wingedsheep.sdk.model.CardEntry>): Map<String, Int> =
        cards.groupingBy { it.name }.eachCount().toSortedMap()

    fun actualDelta(
        controlCounts: Map<String, Int>,
        variantCounts: Map<String, Int>,
    ): Pair<Map<String, Int>, Map<String, Int>> {
        val names = controlCounts.keys + variantCounts.keys
        val removed = names.mapNotNull { name ->
            (controlCounts.getOrDefault(name, 0) - variantCounts.getOrDefault(name, 0))
                .takeIf { it > 0 }?.let { name to it }
        }.toMap().toSortedMap()
        val added = names.mapNotNull { name ->
            (variantCounts.getOrDefault(name, 0) - controlCounts.getOrDefault(name, 0))
                .takeIf { it > 0 }?.let { name to it }
        }.toMap().toSortedMap()
        return removed to added
    }

    fun normalized(label: String, values: Map<String, Int>): Map<String, Int> {
        require(values.values.all { it > 0 }) { "$label counts must all be positive" }
        return values.toSortedMap()
    }

    val (mainRemoved, mainAdded) = actualDelta(counts(control.cards), counts(variant.cards))
    val (sideRemoved, sideAdded) = actualDelta(sideboardCounts(control.sideboard), sideboardCounts(variant.sideboard))

    check(mainRemoved == normalized("mainboardRemoved", declared.mainboardRemoved)) {
        "Mainboard removals $mainRemoved do not equal declared removals ${declared.mainboardRemoved}"
    }
    check(mainAdded == normalized("mainboardAdded", declared.mainboardAdded)) {
        "Mainboard additions $mainAdded do not equal declared additions ${declared.mainboardAdded}"
    }
    check(sideRemoved == normalized("sideboardRemoved", declared.sideboardRemoved)) {
        "Sideboard removals $sideRemoved do not equal declared removals ${declared.sideboardRemoved}"
    }
    check(sideAdded == normalized("sideboardAdded", declared.sideboardAdded)) {
        "Sideboard additions $sideAdded do not equal declared additions ${declared.sideboardAdded}"
    }
}

class BatshitPairedCounterfactualFrameworkTest : FunSpec({
    test("undeclared card-list differences fail before games run") {
        val control = Deck(cards = List(60) { "Fixture Control Card" })
        val undeclaredVariant = control.copy(
            cards = control.cards.toMutableList().also { cards ->
                cards.remove("Fixture Control Card")
                cards.add("Fixture Variant Card")
            },
        )

        shouldThrow<IllegalStateException> {
            BatshitPairedCounterfactualFramework(
                registry = fullRegistry(),
                control = control,
                variant = undeclaredVariant,
                declaredDifference = DeclaredDeckDifference(),
            )
        }
    }

    test("exactly declared card-list difference is accepted") {
        val control = Deck(cards = List(60) { "Fixture Control Card" })
        val variant = control.copy(
            cards = control.cards.toMutableList().also { cards ->
                cards.remove("Fixture Control Card")
                cards.add("Fixture Variant Card")
            },
        )

        BatshitPairedCounterfactualFramework(
            registry = fullRegistry(),
            control = control,
            variant = variant,
            declaredDifference = DeclaredDeckDifference(
                mainboardRemoved = mapOf("Fixture Control Card" to 1),
                mainboardAdded = mapOf("Fixture Variant Card" to 1),
            ),
        )
    }

    test("identical lists reproduce identical paired results and telemetry").config(timeout = 15.minutes) {
        val seeds = Files.readAllLines(
            Path.of("src", "test", "resources", "batshit-paired-framework-test-seeds.csv")
        ).drop(1).filter(String::isNotBlank).map { it.substringAfter(',').toLong() }
        val control = batshitDeck()
        val pairs = BatshitPairedCounterfactualFramework(
            registry = fullRegistry(),
            control = control,
            variant = control,
            declaredDifference = DeclaredDeckDifference(),
        ).run(seeds)

        pairs shouldHaveSize 2
        pairs.map(PairedGameResult::startingPlayerIndex) shouldBe listOf(0, 1)
        pairs.forEach { pair ->
            pair.control shouldBe pair.variant
        }

        val output = Files.createTempDirectory("paired-counterfactual-test")
        BatshitPairedCounterfactualFramework(
            registry = fullRegistry(),
            control = control,
            variant = control,
            declaredDifference = DeclaredDeckDifference(),
        ).writeArtifacts(pairs, output)
        Files.readString(output.resolve("paired-results.log")).contains("--- CONTROL ---") shouldBe true
        Files.readAllLines(output.resolve("paired-results.jsonl")) shouldHaveSize 2
    }
})
