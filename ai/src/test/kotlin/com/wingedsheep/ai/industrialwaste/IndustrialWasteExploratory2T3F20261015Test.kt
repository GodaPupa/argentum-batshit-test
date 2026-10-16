package com.wingedsheep.ai.industrialwaste

import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Isolated, nonofficial synthetic engine smoke. Not an official R1 allocation, 
 * a new R1 candidate family, a competitive matchup, or a deck promotion test.
 * Uses only existing runExcluded create-once journals and exact replay.
 */
class IndustrialWasteExploratory2T3F20261015Test : FunSpec({
    test("paired excluded engine full-horizon smoke and exact replay").config(
        enabled = System.getenv("IW_EXPLORATORY_2T3F") == "true",
    ) {
        val namespace = "IW_V2_R1_SYNTHETIC_2T3F_20261015_ONLY"
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("industrial-waste/v2/candidates")) }
        val out = root.resolve("exploratory/industrial-waste-2t3f-20261015/runs")
        Files.createDirectories(out)
        val summary = out.resolve("summary.tsv")
        check(!Files.exists(summary)) { "Create-once exploratory result already exists" }
        Files.writeString(summary,
            "allocation\tdeck\trow\tstarting_seat\tstatus\tsubmitted\taccepted\tmulligans" +
            "\tfull_tron_turn\tloop_turn\tactual_lethal_turn\tfoundry_certificate_turn" +
            "\tconversion_turn\tcolored_failure_checkpoints\ttotal_stranded_checkpoints" +
            "\tunresolved_checkpoints\tcards_seen_t8\texact_replay\n",
            StandardOpenOption.CREATE_NEW)

        fun append(line: String) =
            Files.writeString(summary, line + "\n", StandardOpenOption.APPEND)
        fun load(relative: String): List<String> {
            val lines = Files.readAllLines(root.resolve(relative))
            var inMain = false
            val names = mutableListOf<String>()
            for (line in lines) {
                if (line.startsWith("[")) {
                    inMain = line == "[main]"
                    continue
                }
                if (!inMain || line.isBlank()) continue
                val row = Regex("([1-9][0-9]*) (.+)").matchEntire(line)
                    ?: error("Malformed main deck line: $line")
                repeat(row.groupValues[1].toInt()) { names += row.groupValues[2] }
            }
            check(names.size == 60) { "Deck must contain exactly 60 cards" }
            return names
        }
        fun counts(cards: List<String>) = cards.groupingBy { it }.eachCount()
        fun digest(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        fun orders(deck: List<String>, row: Int): List<List<String>> {
            val copies = mutableMapOf<String, Int>()
            val labels = deck.map { card ->
                val copy = (copies[card] ?: 0) + 1
                copies[card] = copy
                "$card#$copy"
            }
            check(labels.toSet().size == 60)
            return List(4) { shuffle ->
                labels.sortedWith(compareBy<String> {
                    digest("$namespace|$row|$shuffle|$it")
                }.thenBy { it })
            }
        }

        val control = load("industrial-waste/control/industrial-waste-v1.0-submitted.dck")
        val challenger = load("exploratory/industrial-waste-2t3f-20261015/challenger-2t3f.dck")
        val oldCounts = counts(control)
        val newCounts = counts(challenger)
        check(newCounts.keys == oldCounts.keys)
        check(newCounts.all { (name, count) ->
            count == when (name) {
                "Pactdoll Terror" -> 2
                "Golem Foundry" -> 3
                else -> oldCounts[name]
            }
        }) { "Challenger must differ solely by -2 Terror +2 Foundry" }
        check(oldCounts["Pactdoll Terror"] == 4 && oldCounts["Golem Foundry"] == 1)

        val arms = listOf("control" to control, "challenger_2t3f" to challenger)
        val failures = mutableListOf<String>()
        var scheduled = 0
        for (row in listOf(31501, 31502)) {
            for (seat in 0..1) {
                for ((name, deck) in arms) {
                    scheduled += 1
                    val id = "SYNTHETIC_2T3F_" + row + "_" + seat + "_" + name
                    val input = IndustrialWasteV2AllocationInput(
                        allocationId = id,
                        deckCards = deck,
                        namespace = namespace,
                        row = row,
                        openingOrders = orders(deck, row),
                        startingPlayer = seat,
                        initializerSeed = 8_202_610_150L + (row - 31501) * 10 + seat,
                    )
                    val directory = out.resolve(id)
                    try {
                        val result = IndustrialWasteV2AllocationRunner.runExcluded(input, directory)
                        val status = result.status
                        val metrics = result.eventMetrics
                        val replayed = Files.exists(directory.resolve("replay.json"))
                        val faulty = status.status.name in setOf(
                            "EXCEPTION", "REJECTED_ACTION", "UNRESOLVED_TELEMETRY",
                        ) || result.checkpoints.any { it.unresolved } || !replayed
                        append(listOf(
                            id, name, row, seat,
                            status.status.name, status.submittedActions, status.acceptedActions,
                            metrics.mulligans, metrics.fullTronTurn ?: "",
                            metrics.demonstratedLoopReadyTurn ?: "", metrics.actualLethalTurn ?: "",
                            metrics.certifiedFutureConversionTurn ?: "", metrics.deterministicConversionTurn ?: "",
                            result.checkpoints.count { it.coloredManaFailure },
                            result.checkpoints.count { it.totalManaStranded },
                            result.checkpoints.count { it.unresolved },
                            metrics.cardsSeenByTurn[8] ?: "", replayed,
                        ).joinToString("\t"))
                        if (faulty) failures += id
                    } catch (failure: Throwable) {
                        append(listOf(id, name, row, seat, "UNCAUGHT_FAILURE",
                            failure::class.simpleName ?: "Throwable").joinToString("\t"))
                        throw failure
                    }
                }
            }
        }
        check(scheduled == 8)
        check(failures.isEmpty()) {
            "Nonofficial engine/replay/checkpoint failures, no replacements permitted: $failures"
        }
    }
})
