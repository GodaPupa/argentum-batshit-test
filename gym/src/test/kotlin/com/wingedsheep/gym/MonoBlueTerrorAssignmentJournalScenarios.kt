package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import java.nio.file.Files
import java.nio.file.Path

private val fixtureSeeds = listOf(9_400_001L, -9_400_002L, Long.MIN_VALUE, Long.MAX_VALUE)
private val fixtureVector = fixtureSeeds.joinToString("\n", postfix = "\n")
private const val fixtureSource = "1111111111111111111111111111111111111111"

private fun fixtureCsv(): String = TERROR_ASSIGNMENT_HEADER + "\n" +
    fixtureSeeds.mapIndexed { index, seed ->
        listOf(
            PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID, PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
            (index + 1).toString(), seed.toString(), "0x${seed.toULong().toString(16).padStart(16, '0')}",
            if (index < 2) "SEAT_ZERO" else "SEAT_ONE",
            if (index < 2) "SEAT_ONE" else "SEAT_ZERO",
            if (index % 2 == 0) "PEST_CONTROL" else "MONO_BLUE_TERROR",
            if (index % 2 == 0) "PLAY" else "DRAW",
            PEST_CONTROL_V10_HASH, PEST_MONO_BLUE_TERROR_MAIN_SHA256,
            PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256, PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256,
            PEST_V2_QUALIFIED_RUNNER,
        ).joinToString(",")
    }.joinToString("\n", postfix = "\n")

private fun fixtureAssignments() = decodeTerrorAssignmentText(fixtureVector, fixtureCsv())
private fun mustReject(action: () -> Unit) {
    check(runCatching(action).exceptionOrNull() is Exception) { "invalid operation was accepted" }
}
private fun changeColumn(column: Int): String {
    val rows = fixtureCsv().trimEnd().lines().toMutableList()
    val fields = rows[1].split(',').toMutableList()
    fields[column] = "SUBSTITUTED"
    rows[1] = fields.joinToString(",")
    return rows.joinToString("\n", postfix = "\n")
}
private fun withEvidence(action: (Path, Path, MonoBlueTerrorDurableAttempts) -> Unit) {
    val root = Files.createTempDirectory("terror-attempt-fixture-")
    try {
        val journal = MonoBlueTerrorDurableAttempts.createNew(root, fixtureAssignments(), fixtureSource)
        action(root, root.resolve(PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID), journal)
    } finally {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
}

/** Fixed synthetic values only. Cases never initialize an engine or invoke production entropy. */
internal fun terrorAssignmentJournalCases(): Map<String, () -> Unit> = linkedMapOf<String, () -> Unit>().apply {
    put("canonical assignments preserve order and signed Long boundaries") {
        val rows = fixtureAssignments()
        check(rows.map { it.seed } == fixtureSeeds)
        check(rows.map { it.gameNumber } == listOf(1, 2, 3, 4))
        mustReject { (rows as MutableList).clear() }
    }
    for (column in 0..13) {
        put("assignment column $column substitution rejected") {
            mustReject { decodeTerrorAssignmentText(fixtureVector, changeColumn(column)) }
        }
    }
    val invalidVectors = listOf(
        "0\n-9400002\n${Long.MIN_VALUE}\n${Long.MAX_VALUE}\n",
        "9400001\n9400001\n${Long.MIN_VALUE}\n${Long.MAX_VALUE}\n",
        "+9400001\n-9400002\n${Long.MIN_VALUE}\n${Long.MAX_VALUE}\n",
        "09400001\n-9400002\n${Long.MIN_VALUE}\n${Long.MAX_VALUE}\n",
        "9223372036854775808\n-9400002\n${Long.MIN_VALUE}\n${Long.MAX_VALUE}\n",
        fixtureVector.dropLast(1), fixtureVector.replace("\n", "\r\n"), fixtureVector + "\n",
    )
    invalidVectors.forEachIndexed { index, text ->
        put("invalid vector encoding $index rejected") {
            mustReject { decodeTerrorAssignmentText(text, fixtureCsv()) }
        }
    }
    put("reordered and truncated assignment rows rejected") {
        val rows = fixtureCsv().trimEnd().lines()
        mustReject { decodeTerrorAssignmentText(fixtureVector, (listOf(rows[0], rows[2], rows[1]) + rows.drop(3)).joinToString("\n", postfix = "\n")) }
        mustReject { decodeTerrorAssignmentText(fixtureVector, rows.dropLast(1).joinToString("\n", postfix = "\n")) }
    }
    put("invalid UTF-8 is not silently repaired") {
        mustReject { strictTerrorUtf8(byteArrayOf(0xc3.toByte(), 0x28)) }
    }
    put("synthetic unpinned archive is rejected") {
        mustReject { PestControlTierOneMonoBlueTerrorAssignmentDecoder.decode(byteArrayOf(1, 2, 3)) }
    }
    put("four-game journal persists each ordered transition and result") {
        withEvidence { _, dir, journal ->
            check(Files.readString(dir.resolve("claim.txt")).contains("executionSourceCommit=$fixtureSource\n"))
            for (game in 1..4) {
                journal.recordAttempt(game)
                check(Files.exists(dir.resolve("game-$game.attempt")))
                journal.recordInitializationEntry(game)
                val raw = "SYNTHETIC_RESULT_$game\n".toByteArray()
                journal.recordResult(game, raw)
                check(Files.readAllBytes(dir.resolve("game-$game.raw")).contentEquals(raw))
                check(Files.readString(dir.resolve("game-$game.record")).contains(terrorFrozenDigest(raw)))
            }
            check(journal.events().size == 12)
            check(journal.events().map { it.gameNumber } == (1..4).flatMap { listOf(it, it, it) })
            check(journal.events().map { it.type } == (1..4).flatMap {
                listOf(MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
                    MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
                    MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN)
            })
            mustReject { journal.recordAttempt(1) }
        }
    }
    put("initialization before attempt permanently closes object") {
        withEvidence { _, dir, journal ->
            mustReject { journal.recordInitializationEntry(1) }
            check(!Files.exists(dir.resolve("game-1.initialization-entry")))
            mustReject { journal.recordAttempt(1) }
        }
    }
    put("skipped game is rejected before an attempt is written") {
        withEvidence { _, dir, journal ->
            mustReject { journal.recordAttempt(2) }
            check(!Files.exists(dir.resolve("game-2.attempt")))
        }
    }
    put("duplicate attempt cannot overwrite evidence") {
        withEvidence { _, dir, journal ->
            journal.recordAttempt(1)
            val before = Files.readAllBytes(dir.resolve("game-1.attempt"))
            mustReject { journal.recordAttempt(1) }
            check(Files.readAllBytes(dir.resolve("game-1.attempt")).contentEquals(before))
            mustReject { journal.recordInitializationEntry(1) }
        }
    }
    put("existing block claim prevents restart after interruption") {
        withEvidence { root, _, journal ->
            journal.recordAttempt(1)
            mustReject { MonoBlueTerrorDurableAttempts.createNew(root, fixtureAssignments(), fixtureSource) }
        }
    }
    put("injected partial result write retains raw bytes and blocks continuation") {
        withEvidence { root, dir, journal ->
            journal.recordAttempt(1)
            journal.recordInitializationEntry(1)
            Files.writeString(dir.resolve("game-1.record"), "SYNTHETIC_EXISTING_RECEIPT")
            mustReject { journal.recordResult(1, "SYNTHETIC_PARTIAL_RESULT".toByteArray()) }
            check(Files.readString(dir.resolve("game-1.raw")) == "SYNTHETIC_PARTIAL_RESULT")
            check(Files.readString(dir.resolve("game-1.record")) == "SYNTHETIC_EXISTING_RECEIPT")
            check(journal.events().size == 2)
            mustReject { journal.recordAttempt(2) }
            mustReject { MonoBlueTerrorDurableAttempts.createNew(root, fixtureAssignments(), fixtureSource) }
        }
    }
    put("terminal rejection preserves attempted prefix") {
        withEvidence { _, dir, journal ->
            journal.recordAttempt(1)
            journal.reject(1, "SYNTHETIC_INITIALIZATION_FAILURE")
            check(Files.exists(dir.resolve("rejected.txt")))
            check(journal.events().last().type == MonoBlueTerrorCoordinatorEventType.REJECTED)
            mustReject { journal.recordInitializationEntry(1) }
        }
    }
    put("empty result cannot become a completed record") {
        withEvidence { _, dir, journal ->
            journal.recordAttempt(1)
            journal.recordInitializationEntry(1)
            mustReject { journal.recordResult(1, byteArrayOf()) }
            check(!Files.exists(dir.resolve("game-1.record")))
        }
    }
}
