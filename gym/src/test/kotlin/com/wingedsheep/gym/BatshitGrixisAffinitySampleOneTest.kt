package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

/**
 * Opt-in 100-game preboard experimental sample for frozen Batshit Variant C versus the
 * provenance-locked Pasquale Grixis Affinity maindeck. Gameplay behavior is frozen at 42b7e2d.
 */
class BatshitGrixisAffinitySampleOneTest : FunSpec({
    val enabled = System.getenv("BATSHIT_GRIXIS_AFFINITY_SAMPLE_1") == "true"

    test("Sample 1 decks, assignments, and new seed vector are frozen") {
        val batshit = variantC(batshitDeck())
        batshit.cards.size shouldBe 60
        batshit.cards.count { it == "Village Rites" } shouldBe 2
        batshit.cards.count { it == "Shambling Ghast" } shouldBe 4
        grixisAffinitySmokeDeck().cards.size shouldBe 60

        val rows = readSampleOneSeeds(sampleOneSeedPath())
        rows.size shouldBe 100
        rows.map { it.seed }.distinct().size shouldBe 100
        rows.map { it.assignment } shouldBe List(100) { if (it % 2 == 0) "Batshit plays" else "Batshit draws" }
        val allOtherSeeds = allOtherSeedCsvValues(sampleOneSeedPath()) + historicalLiteralSeeds
        rows.none { it.seed in allOtherSeeds }.shouldBeTrue()
    }

    test("100 frozen-seed preboard Grixis Affinity Sample 1 games").config(
        enabled = enabled,
        timeout = 180.minutes,
    ) {
        val rows = readSampleOneSeeds(sampleOneSeedPath())
        val registry = fullRegistry()
        val batshit = variantC(batshitDeck())
        val affinity = grixisAffinitySmokeDeck()
        val reports = rows.mapIndexed { index, row ->
            check(row.assignment == if (index % 2 == 0) "Batshit plays" else "Batshit draws")
            playLoggedGame(
                registry = registry,
                gameNumber = index + 1,
                seed = row.seed,
                startingPlayerIndex = index % 2,
                batshitDeck = batshit,
                opponentDeck = affinity,
                opponentName = "Grixis Affinity",
                opponentLabel = "Affinity",
            )
        }
        val summaries = reports.mapIndexed { index, report ->
            val row = rows[index]
            AffinitySmokeSummary.from(
                index + 1,
                AffinitySmokeSeedRow(index + 1, row.assignment, row.hexSeed, row.seed),
                report,
            )
        }

        val output = Path.of("build", "reports", "batshit-grixis-affinity-sample-1")
        Files.createDirectories(output)
        Files.copy(sampleOneSeedPath(), output.resolve(sampleOneSeedPath().fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(output.resolve("raw-traces.log"), rawSampleReport(rows, reports))
        Files.writeString(output.resolve("games.jsonl"), summaries.joinToString("\n") { it.toJson() } + "\n")
        Files.writeString(output.resolve("summary.json"), aggregateJson(summaries, reports))
        val human = humanSampleReport(rows, summaries, reports)
        Files.writeString(output.resolve("sample-report.md"), human)
        println(human)

        reports.size shouldBe 100
        reports.forEach { report ->
            report.completed.shouldBeTrue()
            report.actions shouldBeGreaterThan 0
            assertTriggerSummaryMatchesRawEvents(report.log)
            assertRawPlayAndCastLinesAreTurnStamped(report.log)
            assertAffinityCostTelemetry(report.log)
            assertGalvanicBlastTelemetry(report.log)
        }
    }
})

private data class SampleOneSeedRow(val game: Int, val assignment: String, val hexSeed: String, val seed: Long)

private fun sampleOneSeedPath() = Path.of("src", "test", "resources", "batshit-grixis-affinity-sample-1-seeds.csv")

private fun readSampleOneSeeds(path: Path): List<SampleOneSeedRow> = Files.readAllLines(path)
    .drop(1).filter(String::isNotBlank).map { line ->
        val fields = line.split(',')
        check(fields.size == 4) { "Malformed Sample 1 seed row: $line" }
        SampleOneSeedRow(fields[0].toInt(), fields[1], fields[2], fields[3].toLong())
    }

private fun allOtherSeedCsvValues(current: Path): Set<Long> = Files.list(current.parent).use { paths ->
    paths.filter { it != current && it.fileName.toString().contains("seed") && it.fileName.toString().endsWith(".csv") }
        .toList().flatMap { path ->
            Files.readAllLines(path).drop(1).filter(String::isNotBlank).mapNotNull {
                it.substringAfterLast(',').toLongOrNull()
            }
        }.toSet()
}

private val historicalLiteralSeeds = setOf(
    0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
    0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
    0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
    0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
    0x0906_6DF1_0204_FD56L,
)

private fun rawSampleReport(rows: List<SampleOneSeedRow>, reports: List<LoggedSmokeGame>) = buildString {
    appendLine("BATSHIT VARIANT C VS PROVENANCE-LOCKED GRIXIS AFFINITY — PREBOARD SAMPLE #1")
    appendLine("Argentum-agent self-play under frozen gameplay configuration 42b7e2d")
    appendLine("Not a real-world matchup win rate")
    appendLine("Frozen seeds: ${rows.joinToString { it.seed.toString() }}")
    appendLine()
    reports.forEach { append(it.log) }
}

private fun humanSampleReport(
    rows: List<SampleOneSeedRow>,
    summaries: List<AffinitySmokeSummary>,
    reports: List<LoggedSmokeGame>,
): String = buildString {
    val wins = summaries.count { it.winner == "Batshit Economics" }
    val losses = summaries.size - wins
    val (lo, hi) = wilson(wins, summaries.size)
    val play = summaries.filter { it.assignment == "Batshit plays" }
    val draw = summaries.filter { it.assignment == "Batshit draws" }
    val turns = summaries.map { it.endingTurn }.sorted()
    val allLines = reports.flatMap { it.log.lineSequence().toList() }

    fun count(text: String) = allLines.count { text in it }
    fun actionCount(text: String) = allLines.count { it.startsWith("T") && text in it }
    fun histogram(values: List<Any>) = values.groupingBy { it }.eachCount().entries
        .sortedBy { it.key.toString() }.joinToString { "${it.key}:${it.value}" }.ifEmpty { "none" }
    fun summaryPair(prefix: String): Pair<Int, Int> = reports.map { report ->
        val value = report.log.lineSequence().first { it.startsWith(prefix) }.substringAfter(": ")
        value.substringBefore('/').toInt() to value.substringAfter('/').toInt()
    }.fold(0 to 0) { total, value -> total.first + value.first to total.second + value.second }
    fun targets(action: String) = allLines.filter { it.startsWith("T") && action in it && "targets=[" in it }
        .map { it.substringAfter("targets=[").substringBefore(']') }
    fun sacrifices(action: String) = allLines.filter { it.startsWith("T") && action in it && "sacrifice=[" in it }
        .map { it.substringAfter("sacrifice=[").substringBefore(']') }
    fun featureRecord(name: String, predicate: (String) -> Boolean): String {
        val present = summaries.indices.filter { predicate(reports[it].log) }
        val featureWins = present.count { summaries[it].winner == "Batshit Economics" }
        return "$name $featureWins-${present.size - featureWins} in ${present.size} games"
    }

    val glasswright = reports.map { report ->
        val value = report.log.lineSequence().first { it.startsWith("Glasswright entries/resets:") }.substringAfter(": ")
        value.substringBefore('/').toInt() to value.substringAfter('/').toInt()
    }.fold(0 to 0) { a, b -> a.first + b.first to a.second + b.second }
    val flame = summaryPair("Flamebreather triggers/damage:")
    val bats = summaryPair("Mirkwood Bats creation/sacrifice triggers:")
    val ghastOptions = ghastModes(reports)
    val blastLines = allLines.filter { it.startsWith("T") && "Affinity cast Galvanic Blast" in it }
    val blastDamage = allLines.filter { "EVENT damage Galvanic Blast" in it }.sumOf {
        it.substringAfter("EVENT damage Galvanic Blast ").substringBefore(' ').toInt()
    }
    val affinityBoards = allLines.mapNotNull { Regex("BOARD Affinity artifacts=(\\d+).*access=U=(true|false),B=(true|false),R=(true|false)").find(it) }
    val discounts = allLines.mapNotNull { Regex("affinity\\[artifacts=(\\d+),printedGeneric=(\\d+),effectiveGeneric=(\\d+),effectiveCmc=(\\d+)]").find(it) }
    val totalDiscount = discounts.sumOf { it.groupValues[2].toInt() - it.groupValues[3].toInt() }

    appendLine("# Grixis Affinity Preboard Sample #1")
    appendLine()
    appendLine("**Argentum-agent self-play under the frozen configuration; not a real-world matchup win rate.**")
    appendLine()
    appendLine("Gameplay baseline: `42b7e2d159c2e5e0587ee1ae474e58b634916827`; Variant C and the provenance-locked Pasquale maindeck unchanged.")
    appendLine()
    appendLine("## Result and execution")
    appendLine()
    appendLine("- Overall Batshit record: **$wins-$losses**; observed proportion ${pct(wins, summaries.size)}, 95% Wilson interval ${pct(lo)}–${pct(hi)}.")
    appendLine("- On the play: ${play.count { it.winner == "Batshit Economics" }}-${play.count { it.winner != "Batshit Economics" }}; on the draw: ${draw.count { it.winner == "Batshit Economics" }}-${draw.count { it.winner != "Batshit Economics" }}.")
    appendLine("- Ending-turn distribution: ${histogram(turns)}; median ${"%.1f".format((turns[49] + turns[50]) / 2.0)}; range ${turns.first()}–${turns.last()}.")
    appendLine("- Mulligans, Batshit: ${histogram(summaries.map { it.batshitMulligans })}; Affinity: ${histogram(summaries.map { it.affinityMulligans })}.")
    appendLine("- Seed vector: 100 unique, frozen before play, 50/50 alternating assignment, zero overlap with every repository seed vector and historical literal seed set.")
    appendLine()
    appendLine("## Batshit telemetry")
    appendLine()
    appendLine("- Glasswright entries/resets: ${glasswright.first}/${glasswright.second}; Craft casts: ${actionCount("cast Craft with Pride")}.")
    appendLine("- Flamebreather created triggers/resolved damage: ${flame.first}/${flame.second}.")
    appendLine("- Bats creation/sacrifice triggers: ${bats.first}/${bats.second}; resolved Bats life loss: ${mirkwoodBatsDamage(reports)}.")
    appendLine("- Ghast deaths/modes: ${count("EVENT trigger controller=Batshit Shambling Ghast")}; -1/-1 ${ghastOptions.first}, Treasure ${ghastOptions.second}; Ghast Treasures created ${ghastOptions.second}.")
    appendLine("- Casts — Rites ${actionCount("Batshit cast Village Rites")}, Offering ${actionCount("Batshit cast Fanatical Offering")}, NDAA ${actionCount("Batshit cast Not Dead After All")}, Unearth ${actionCount("Batshit cast Unearth")}, Munitions ${actionCount("Batshit cast Makeshift Munitions")}; Munitions activations ${actionCount("Batshit activate Makeshift Munitions")}.")
    appendLine("- Cast Down targets: ${histogram(targets("Batshit cast Cast Down"))}.")
    appendLine()
    appendLine("## Affinity telemetry")
    appendLine()
    appendLine("- Threat deployment: Myr Enforcer ${actionCount("Affinity cast Myr Enforcer")}; Utrom Monitor ${actionCount("Affinity cast Utrom Monitor")}; Refurbished Familiar ${actionCount("Affinity cast Refurbished Familiar")}.")
    appendLine("- Affinity casts: ${discounts.size}; total generic discount $totalDiscount; resulting CMC distribution ${histogram(discounts.map { it.groupValues[4].toInt() })}.")
    appendLine("- Metalcraft board-snapshot uptime: ${affinityBoards.count { it.groupValues[1].toInt() >= 3 }}/${affinityBoards.size}; Blast metalcraft ${blastLines.count { "active=true" in it }}/${blastLines.size}.")
    appendLine("- Galvanic Blast: ${blastLines.size} casts; targets ${histogram(blastLines.map { it.substringAfter("targets=[").substringBefore(']') })}; resolved damage $blastDamage.")
    appendLine("- Familiar ETB triggers ${count("EVENT trigger controller=Affinity Refurbished Familiar")}; Batshit discards ${count("EVENT discard Batshit")}.")
    appendLine("- Bargain casts ${actionCount("Affinity cast Reckoner's Bargain")}; sacrifices ${histogram(sacrifices("Affinity cast Reckoner's Bargain"))}; life gained ${sumLifeGain(reports, "Reckoner's Bargain")}.")
    appendLine("- Wellspring ETB/death draw triggers: ${count("EVENT trigger controller=Affinity Ichor Wellspring: a card or permanent would enter")}/${count("EVENT trigger controller=Affinity Ichor Wellspring: a card or permanent would die")}.")
    appendLine("- Spellbomb activations ${actionCount("Affinity activate Nihil Spellbomb")}; targets ${histogram(targets("Affinity activate Nihil Spellbomb"))}; conditional-draw triggers ${count("EVENT trigger controller=Affinity Nihil Spellbomb")}.")
    appendLine("- Shaman activations ${actionCount("Affinity activate Krark-Clan Shaman")}; sacrifices ${histogram(sacrifices("Affinity activate Krark-Clan Shaman"))}.")
    appendLine("- Artifact-land plays: ${histogram(artifactLandPlays(allLines))}.")
    appendLine("- Color-access snapshots: U ${affinityBoards.count { it.groupValues[2] == "true" }}/${affinityBoards.size}, B ${affinityBoards.count { it.groupValues[3] == "true" }}/${affinityBoards.size}, R ${affinityBoards.count { it.groupValues[4] == "true" }}/${affinityBoards.size}.")
    appendLine()
    appendLine("## Descriptive correlates")
    appendLine()
    listOf(
        featureRecord("Craft observed:") { "Batshit cast Craft with Pride" in it },
        featureRecord("Flamebreather trigger observed:") { "EVENT trigger controller=Batshit Kessig Flamebreather" in it },
        featureRecord("Bats trigger observed:") { "EVENT trigger controller=Batshit Mirkwood Bats" in it },
        featureRecord("Ghast death observed:") { "EVENT trigger controller=Batshit Shambling Ghast" in it },
        featureRecord("Myr Enforcer deployed:") { "Affinity cast Myr Enforcer" in it },
        featureRecord("Metalcraft Blast observed:") { "Affinity cast Galvanic Blast" in it && "active=true" in it },
        featureRecord("Spellbomb activated:") { "Affinity activate Nihil Spellbomb" in it },
        featureRecord("Shaman activated:") { "Affinity activate Krark-Clan Shaman" in it },
    ).forEach { appendLine("- $it (Batshit W-L when present).") }
    appendLine("- Proximate ending causes: ${histogram(summaries.map { endingCause(it.proximateEnding) })}.")
    appendLine()
    appendLine("## Per-game index")
    appendLine()
    appendLine("| Game | Seed | B play/draw | Mulligans B/A | Winner | Turn | Final life B/A | Proximate ending |")
    appendLine("|---:|---:|:---:|:---:|:---|---:|:---:|:---|")
    summaries.forEach { game ->
        appendLine("| ${game.game} | ${game.seed} | ${if (game.assignment == "Batshit plays") "play" else "draw"} | ${game.batshitMulligans}/${game.affinityMulligans} | ${game.winner} | ${game.endingTurn} | ${game.batshitLife}/${game.affinityLife} | ${game.proximateEnding.replace("|", "\\|")} |")
    }
    appendLine()
    appendLine("## Audit gate")
    appendLine()
    appendLine("Automated invariants passed. Strategic findings require complete-block manual trace review before acceptance.")
}

private fun aggregateJson(summaries: List<AffinitySmokeSummary>, reports: List<LoggedSmokeGame>): String {
    val wins = summaries.count { it.winner == "Batshit Economics" }
    val (lo, hi) = wilson(wins, summaries.size)
    return """{"configuration":"42b7e2d159c2e5e0587ee1ae474e58b634916827","label":"Argentum-agent self-play under the frozen configuration","games":${summaries.size},"batshit_wins":$wins,"affinity_wins":${summaries.size - wins},"wilson_95":{"low":$lo,"high":$hi},"completed":${reports.count { it.completed }},"invariants":"pending_manual_audit"}""" + "\n"
}

private fun wilson(wins: Int, total: Int): Pair<Double, Double> {
    val z = 1.959963984540054
    val p = wins.toDouble() / total
    val denominator = 1 + z.pow(2) / total
    val center = (p + z.pow(2) / (2 * total)) / denominator
    val margin = z * sqrt((p * (1 - p) + z.pow(2) / (4 * total)) / total) / denominator
    return center - margin to center + margin
}

private fun pct(value: Double) = "%.1f%%".format(value * 100)
private fun pct(wins: Int, total: Int) = pct(wins.toDouble() / total)

private fun ghastModes(reports: List<LoggedSmokeGame>): Pair<Int, Int> {
    var minus = 0
    var treasure = 0
    reports.forEach { report ->
        var awaiting = false
        report.log.lineSequence().forEach { line ->
            if ("EVENT trigger controller=Batshit Shambling Ghast" in line) awaiting = true
            if (awaiting && "ChooseOptionDecision" in line && "optionIndex=" in line) {
                if (line.substringAfter("optionIndex=").substringBefore(')').toInt() == 0) minus++ else treasure++
                awaiting = false
            }
        }
    }
    return minus to treasure
}

private fun mirkwoodBatsDamage(reports: List<LoggedSmokeGame>) = reports.flatMap { it.log.lineSequence().toList() }
    .filter { "EVENT damage Mirkwood Bats" in it }.sumOf {
        it.substringAfter("EVENT damage Mirkwood Bats ").substringBefore(' ').toInt()
    }

private fun sumLifeGain(reports: List<LoggedSmokeGame>, source: String) = reports.flatMap { it.log.lineSequence().toList() }
    .filter { "LIFE_GAIN source=$source" in it }.sumOf {
        val change = Regex("(\\d+)->(\\d+)").find(it) ?: return@sumOf 0
        change.groupValues[2].toInt() - change.groupValues[1].toInt()
    }

private val ARTIFACT_LANDS = setOf(
    "Drossforge Bridge", "Great Furnace", "Mistvault Bridge", "Seat of the Synod",
    "Silverbluff Bridge", "Vault of Whispers",
)

private fun artifactLandPlays(lines: List<String>) = lines.filter { it.startsWith("T") && "Affinity play " in it }
    .map { it.substringAfter("Affinity play ") }.filter { it in ARTIFACT_LANDS }

private fun endingCause(line: String): String = when {
    "Galvanic Blast" in line -> "Galvanic Blast"
    "Makeshift Munitions" in line -> "Makeshift Munitions"
    "Mirkwood Bats" in line -> "Mirkwood Bats"
    "Kessig Flamebreather" in line -> "Flamebreather"
    "block" in line || "attack" in line -> "combat"
    else -> "other"
}
