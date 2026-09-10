package com.wingedsheep.gym

import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

/**
 * Opt-in ten-game preboard readiness smoke for frozen Batshit Variant C versus the
 * provenance-locked Pasquale Grixis Affinity maindeck. This is validation, not matchup evidence.
 */
class BatshitGrixisAffinitySmokeTest : FunSpec({
    val enabled = System.getenv("BATSHIT_GRIXIS_AFFINITY_SMOKE") == "true"

    test("frozen decks and seed vector are exact and disjoint") {
        val batshit = variantC(batshitDeck())
        batshit.cards.size shouldBe 60
        batshit.cards.count { it == "Village Rites" } shouldBe 2
        batshit.cards.count { it == "Shambling Ghast" } shouldBe 4

        val affinity = grixisAffinitySmokeDeck()
        affinity.cards.size shouldBe 60
        affinity.cards.groupingBy { it }.eachCount().entries.map { it.key to it.value }
            .shouldContainExactlyInAnyOrder(GRIXIS_AFFINITY_COUNTS.entries.map { it.key to it.value })

        val seedPath = affinitySmokeSeedPath()
        val rows = readAffinitySmokeSeeds(seedPath)
        rows.size shouldBe 10
        rows.map { it.seed }.distinct().size shouldBe 10
        rows.map { it.assignment } shouldBe List(10) { index ->
            if (index % 2 == 0) "Batshit plays" else "Batshit draws"
        }
        rows.none { it.seed in previouslyUsedAffinitySmokeSeeds(seedPath) }.shouldBeTrue()
    }

    test("10 frozen-seed preboard Grixis Affinity readiness games").config(
        enabled = enabled,
        timeout = 45.minutes,
    ) {
        val registry = fullRegistry()
        val seedPath = affinitySmokeSeedPath()
        val rows = readAffinitySmokeSeeds(seedPath)
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
            AffinitySmokeSummary.from(index + 1, rows[index], report)
        }
        val output = Path.of("build", "reports", "batshit-grixis-affinity-smoke")
        Files.createDirectories(output)
        Files.copy(seedPath, output.resolve(seedPath.fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(output.resolve("raw-traces.log"), buildRawReport(rows, reports))
        Files.writeString(output.resolve("games.jsonl"), summaries.joinToString("\n") { it.toJson() } + "\n")
        Files.writeString(output.resolve("smoke-report.md"), buildHumanReport(rows, summaries))
        println(buildHumanReport(rows, summaries))

        reports.size shouldBe 10
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

internal data class AffinitySmokeSeedRow(
    val game: Int,
    val assignment: String,
    val hexSeed: String,
    val seed: Long,
)

private fun affinitySmokeSeedPath(): Path = Path.of(
    "src", "test", "resources", "batshit-grixis-affinity-smoke-seeds.csv"
)

private fun readAffinitySmokeSeeds(path: Path): List<AffinitySmokeSeedRow> = Files.readAllLines(path)
    .drop(1).filter(String::isNotBlank).map { line ->
        val fields = line.split(',')
        check(fields.size == 4) { "Malformed Affinity smoke seed row: $line" }
        AffinitySmokeSeedRow(fields[0].toInt(), fields[1], fields[2], fields[3].toLong())
    }

private fun previouslyUsedAffinitySmokeSeeds(current: Path): Set<Long> {
    val csvSeeds = Files.list(current.parent).use { paths ->
        paths.filter { it != current && it.name.contains("seed") && it.name.endsWith(".csv") }
            .toList().flatMap { path ->
                Files.readAllLines(path).drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }
            }.toSet()
    }
    return csvSeeds + setOf(
        0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
        0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
        0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
        0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
        0x0906_6DF1_0204_FD56L,
    )
}

private val GRIXIS_AFFINITY_COUNTS = linkedMapOf(
    "Drossforge Bridge" to 3, "Great Furnace" to 2, "Mistvault Bridge" to 3,
    "Seat of the Synod" to 3, "Silverbluff Bridge" to 2, "Swamp" to 1,
    "Vault of Whispers" to 4, "Krark-Clan Shaman" to 2, "Myr Enforcer" to 4,
    "Refurbished Familiar" to 4, "Utrom Monitor" to 3, "Cast Down" to 3,
    "Fanatical Offering" to 2, "Galvanic Blast" to 4, "Reckoner's Bargain" to 4,
    "Thoughtcast" to 4, "Toxin Analysis" to 2, "Blood Fountain" to 3,
    "Ichor Wellspring" to 4, "Makeshift Munitions" to 1, "Nihil Spellbomb" to 2,
)

internal fun grixisAffinitySmokeDeck(): Deck = Deck.of(
    *GRIXIS_AFFINITY_COUNTS.entries.map { it.key to it.value }.toTypedArray()
)

private val PERMANENT_DEVELOPMENT = setOf(
    "Goblin Glasswright", "Kessig Flamebreather", "Mirkwood Bats", "Shambling Ghast",
    "Voldaren Epicure", "Makeshift Munitions", "Krark-Clan Shaman", "Myr Enforcer",
    "Refurbished Familiar", "Utrom Monitor", "Blood Fountain", "Ichor Wellspring", "Nihil Spellbomb",
)

internal data class AffinitySmokeSummary(
    val game: Int,
    val seed: Long,
    val hexSeed: String,
    val assignment: String,
    val winner: String,
    val endingTurn: Int,
    val batshitLife: Int,
    val affinityLife: Int,
    val batshitMulligans: Int,
    val affinityMulligans: Int,
    val batshitFirstDevelopment: String,
    val affinityFirstDevelopment: String,
    val proximateEnding: String,
    val telemetry: Map<String, List<String>>,
) {
    fun toJson(): String = buildString {
        append('{')
        append("\"game\":$game,\"seed\":$seed,\"hex_seed\":${hexSeed.json()},")
        append("\"play_draw\":${assignment.json()},\"winner\":${winner.json()},\"ending_turn\":$endingTurn,")
        append("\"final_life\":{\"batshit\":$batshitLife,\"affinity\":$affinityLife},")
        append("\"mulligans\":{\"batshit\":$batshitMulligans,\"affinity\":$affinityMulligans},")
        append("\"first_meaningful_development\":{\"batshit\":${batshitFirstDevelopment.json()},")
        append("\"affinity\":${affinityFirstDevelopment.json()}},")
        append("\"proximate_game_end\":${proximateEnding.json()},\"telemetry\":{")
        append(telemetry.entries.joinToString(",") { (key, values) ->
            "${key.json()}:[${values.joinToString(",") { it.json() }}]"
        })
        append("}}")
    }

    companion object {
        fun from(game: Int, row: AffinitySmokeSeedRow, report: LoggedSmokeGame): AffinitySmokeSummary {
            val lines = report.log.lineSequence().toList()
            fun evidence(vararg needles: String): List<String> = lines.filter { line ->
                needles.any(line::contains)
            }.map(String::trim)
            fun firstDevelopment(label: String): String = lines.firstOrNull { line ->
                line.startsWith("T") && line.contains(" $label cast ") &&
                    PERMANENT_DEVELOPMENT.any { line.contains("cast $it") }
            }?.trim() ?: "none"
            fun summary(prefix: String): List<String> = lines.filter { it.startsWith(prefix) }

            return AffinitySmokeSummary(
                game = game,
                seed = row.seed,
                hexSeed = row.hexSeed,
                assignment = row.assignment,
                winner = report.winner,
                endingTurn = report.endingTurn,
                batshitLife = report.batshitLife,
                affinityLife = report.opponentLife,
                batshitMulligans = report.batshitMulligans,
                affinityMulligans = report.opponentMulligans,
                batshitFirstDevelopment = firstDevelopment("Batshit"),
                affinityFirstDevelopment = firstDevelopment("Affinity"),
                proximateEnding = lines.firstOrNull { it.startsWith("Proximate last meaningful decision:") }
                    ?.substringAfter(": ") ?: "none",
                telemetry = linkedMapOf(
                    "glasswright_entries_crafts_resets" to summary("Glasswright entries/resets:"),
                    "flamebreather_created_resolved_damage" to summary("Flamebreather triggers/damage:"),
                    "bats_creation_sacrifice_and_life_loss" to (
                        summary("Mirkwood Bats creation/sacrifice triggers:") +
                            evidence("source=Mirkwood Bats")
                    ),
                    "ghast_modes_and_treasure" to evidence("Shambling Ghast", "Treasure"),
                    "rites_offering_ndaa_unearth_munitions" to evidence(
                        "Village Rites", "Fanatical Offering", "Not Dead After All", "Unearth",
                        "Makeshift Munitions"
                    ),
                    "cast_down_targets" to evidence("cast Cast Down"),
                    "affinity_artifact_counts" to evidence("BOARD Affinity artifacts="),
                    "affinity_discounts_and_costs" to evidence(" affinity["),
                    "galvanic_blast_metalcraft" to evidence("Galvanic Blast"),
                    "refurbished_familiar" to evidence("Refurbished Familiar"),
                    "utrom_monitor" to evidence("Utrom Monitor"),
                    "reckoners_bargain" to evidence("Reckoner's Bargain"),
                    "ichor_wellspring" to evidence("Ichor Wellspring"),
                    "nihil_spellbomb" to evidence("Nihil Spellbomb"),
                    "krark_clan_shaman" to evidence("Krark-Clan Shaman"),
                    "artifact_land_sequencing_and_color_access" to (
                        evidence("play Drossforge Bridge", "play Great Furnace", "play Mistvault Bridge",
                            "play Seat of the Synod", "play Silverbluff Bridge", "play Vault of Whispers") +
                            evidence("BOARD Affinity artifacts=")
                    ),
                    "large_affinity_threats" to evidence("Myr Enforcer", "Utrom Monitor"),
                    "stranded_batshit_resources" to summary("Stranded Batshit Rites/Unearth/NDAA:"),
                ),
            )
        }
    }
}

internal fun assertAffinityCostTelemetry(log: String) {
    val pattern = Regex(
        "affinity\\[artifacts=(\\d+),printedGeneric=(\\d+),effectiveGeneric=(\\d+),effectiveCmc=(\\d+)]"
    )
    log.lineSequence().filter { " affinity[" in it }.forEach { line ->
        val match = pattern.find(line) ?: error("Malformed affinity cost telemetry: $line")
        val (artifacts, printedGeneric, effectiveGeneric, effectiveCmc) = match.destructured
        val expectedGeneric = (printedGeneric.toInt() - artifacts.toInt()).coerceAtLeast(0)
        effectiveGeneric.toInt() shouldBe expectedGeneric
        val coloredPips = if ("Myr Enforcer" in line) 0 else 1
        effectiveCmc.toInt() shouldBe expectedGeneric + coloredPips
    }
}

internal fun assertGalvanicBlastTelemetry(log: String) {
    val pattern = Regex("EVENT damage Galvanic Blast (\\d+) -> .* controllerArtifacts=(\\d+)")
    log.lineSequence().filter { "EVENT damage Galvanic Blast" in it }.forEach { line ->
        val match = pattern.find(line) ?: error("Malformed Galvanic Blast telemetry: $line")
        val (damage, artifacts) = match.destructured
        damage.toInt() shouldBe if (artifacts.toInt() >= 3) 4 else 2
    }
}

private fun buildRawReport(rows: List<AffinitySmokeSeedRow>, reports: List<LoggedSmokeGame>): String = buildString {
    appendLine("BATSHIT VARIANT C VS GRIXIS AFFINITY — PREBOARD READINESS SMOKE")
    appendLine("Validated laboratory head: abdb87f")
    appendLine("Argentum agent self-play validation only; not matchup evidence")
    appendLine("Frozen seeds: ${rows.joinToString { it.seed.toString() }}")
    appendLine()
    reports.forEach { append(it.log) }
}

private fun buildHumanReport(rows: List<AffinitySmokeSeedRow>, summaries: List<AffinitySmokeSummary>): String = buildString {
    appendLine("# Batshit Variant C vs Grixis Affinity — 10-game preboard smoke")
    appendLine()
    appendLine("Validation smoke only. The observed record is not matchup evidence or a matchup win percentage.")
    appendLine()
    appendLine("Validated laboratory head: `abdb87f`; normal London mulligans; frozen decks and policy.")
    appendLine()
    appendLine("Frozen seed vector: `${rows.joinToString { it.seed.toString() }}`")
    appendLine()
    appendLine("| Game | Seed | Batshit | Mulligans B/A | Winner | Turn | Final life B/A | First B development | First A development |")
    appendLine("|---:|---:|:---:|:---:|:---|---:|:---:|:---|:---|")
    summaries.forEach { game ->
        appendLine(
            "| ${game.game} | ${game.seed} | ${if (game.assignment == "Batshit plays") "play" else "draw"} | " +
                "${game.batshitMulligans}/${game.affinityMulligans} | ${game.winner} | ${game.endingTurn} | " +
                "${game.batshitLife}/${game.affinityLife} | ${game.batshitFirstDevelopment} | ${game.affinityFirstDevelopment} |"
        )
    }
    appendLine()
    appendLine("## Per-game telemetry index")
    summaries.forEach { game ->
        appendLine()
        appendLine("### Game ${game.game} — seed ${game.seed}")
        appendLine()
        appendLine("- Proximate ending: ${game.proximateEnding}")
        game.telemetry.forEach { (key, evidence) ->
            appendLine("- $key: ${if (evidence.isEmpty()) "none observed" else evidence.joinToString(" | ")}")
        }
    }
    appendLine()
    appendLine("## Audit gate")
    appendLine()
    appendLine("Automated rules/state/telemetry invariants passed. Strategic categories require trace review before acceptance.")
}

private fun String.json(): String = buildString {
    append('"')
    this@json.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
