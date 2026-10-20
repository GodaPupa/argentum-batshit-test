package com.wingedsheep.ai.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.nio.file.Path

private const val CONTROL_R19 = "experiments/manual-transmission/control/v0.7.decklist.txt"
private const val PUBLISHED_WAN_R19 = "gameplay/manual-v08-wan/CHALLENGER.decklist.txt"
private const val BESTIARY_R19 = "Lifecrafter’s Bestiary"
private const val WAN_R19 = "Wan Shi Tong, Librarian"
private const val COMMANDER_R19 = "Animar, Soul of Elements"

private fun manualR19DeckNames(path: String): List<String> {
    // Gradle :ai:test runs with ai/ as the working directory. Resolve upward to the
    // repository root without depending on developer credentials or workflow-only env.
    val workingDir = Path.of("").toAbsolutePath().normalize()
    val resolved = generateSequence(workingDir) { it.parent }
        .map { it.resolve(path) }
        .firstOrNull { Files.isRegularFile(it) }
        ?: error("R19 decklist not found: $path (from $workingDir)")
    return Files.readAllLines(resolved).flatMap { line ->
        Regex("^(\\d+) (.+)$").matchEntire(line)?.let { m ->
            List(m.groupValues[1].toInt()) { m.groupValues[2] }
        } ?: emptyList()
    }
}

/** Strict pre-initialization comparison only; no GameInitializer or gameplay call. */
class ManualR19MatchedSlotTest : FunSpec({
    test("matched-slot Wan replaces only Bestiary's exact original position") {
        val control = manualR19DeckNames(CONTROL_R19)
        val index = control.indexOf(BESTIARY_R19)
        val matched = ManualR19MatchedSlotHelper.matchedChallengerNames(
            control, BESTIARY_R19, WAN_R19
        )

        control.size shouldBe 100
        control.count { it == COMMANDER_R19 } shouldBe 1
        control.count { it == BESTIARY_R19 } shouldBe 1
        matched.size shouldBe 100
        matched.count { it == WAN_R19 } shouldBe 1
        matched.count { it == BESTIARY_R19 } shouldBe 0
        matched[index] shouldBe WAN_R19
        control.indices.filter { control[it] != matched[it] } shouldBe listOf(index)

        // Exactly 99 common entries including the commander, unchanged *at each index*.
        control.filter { it != BESTIARY_R19 } shouldBe matched.filter { it != WAN_R19 }
        val controlLibrary = control.filter { it != COMMANDER_R19 }
        val matchedLibrary = matched.filter { it != COMMANDER_R19 }
        controlLibrary.size shouldBe 99
        matchedLibrary.size shouldBe 99
        // The two 99-card libraries differ at just one index; 98 shared library cards align.
        controlLibrary.indices.filter { controlLibrary[it] != matchedLibrary[it] }.size shouldBe 1
        controlLibrary.filter { it != BESTIARY_R19 } shouldBe
            matchedLibrary.filter { it != WAN_R19 }
    }

    test("published R18 section-ordered challenger is a negative control") {
        val control = manualR19DeckNames(CONTROL_R19)
        val published = manualR19DeckNames(PUBLISHED_WAN_R19)
        val matched = ManualR19MatchedSlotHelper.matchedChallengerNames(
            control, BESTIARY_R19, WAN_R19
        )

        published.size shouldBe 100
        // Same cards, but a different input ordering. This is the R18 confound.
        published.sorted() shouldBe matched.sorted()
        published.indexOf(WAN_R19) shouldNotBe control.indexOf(BESTIARY_R19)
        published.filter { it != WAN_R19 } shouldNotBe
            control.filter { it != BESTIARY_R19 }
        published shouldNotBe matched
    }

    test("helper refuses missing, nonunique, or already-present substitutions") {
        val control = manualR19DeckNames(CONTROL_R19)
        shouldThrow<IllegalArgumentException> {
            ManualR19MatchedSlotHelper.matchedChallengerNames(control, "Absent Card", WAN_R19)
        }
        shouldThrow<IllegalArgumentException> {
            ManualR19MatchedSlotHelper.matchedChallengerNames(
                control + BESTIARY_R19, BESTIARY_R19, WAN_R19
            )
        }
        shouldThrow<IllegalArgumentException> {
            ManualR19MatchedSlotHelper.matchedChallengerNames(
                control, BESTIARY_R19, COMMANDER_R19
            )
        }
    }
})
