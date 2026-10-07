package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronR1RebindTest : FunSpec({
    val exactSeeds = listOf(
        8509670981736218459L,
        -8564666863904712979L,
        -8259800499619080267L,
        3489680325849498530L,
    )
    val vector = exactSeeds.joinToString("\n", postfix = "\n")

    fun transport(block: String): String = buildString {
        appendLine(MONSTER_TRON_R1_ASSIGNMENT_HEADER)
        exactSeeds.forEachIndexed { index, seed ->
            val pestSeat = if (index < 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
            val monsterSeat = if (index < 2) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO
            val starter =
                if (index % 2 == 0) MonsterTronStartingDeck.PEST_CONTROL
                else MonsterTronStartingDeck.MONSTER_TRON
            appendLine(
                listOf(
                    PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
                    block,
                    (index + 1).toString(),
                    seed.toString(),
                    monsterTronSeedHex(seed),
                    pestSeat.name,
                    monsterSeat.name,
                    starter.name,
                    if (index % 2 == 0) "PLAY" else "DRAW",
                    PEST_CONTROL_V10_HASH,
                    PEST_MONSTER_TRON_MAIN_SHA256,
                    PEST_V2_QUALIFIED_RUNNER,
                ).joinToString(",")
            )
        }
    }

    test("R1 identity seal is exact and construction-only") {
        val inspection = PestControlTierOneMonsterTronR1ExecutionIdentity.inspect()
        inspection.errors shouldBe emptyList()
        inspection.green shouldBe true
        inspection.runnerEnabled shouldBe false
        inspection.a2Active shouldBe false
        inspection.claims shouldBe 0
        inspection.games shouldBe 0
        inspection.actions shouldBe 0
        inspection.outcomes shouldBe 0

        PEST_MONSTER_TRON_R1_ACCEPTED_C2 shouldBe
            "38e834c1275a861e87487195842fb4d98e373ee7"
        PEST_MONSTER_TRON_R1_ACCEPTED_C2_IDENTIFIER shouldBe
            "421a75c0aad541d9604840b1757c790c7f313155a215e55bdc156858514e2659"
        PEST_MONSTER_TRON_R1_BLOCK_ID shouldBe
            "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
        PEST_MONSTER_TRON_R1_VECTOR_SHA256 shouldBe
            "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89"
        PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256 shouldBe
            "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7"
        PEST_MONSTER_TRON_R1_FUTURE_CLAIM_REF shouldBe
            "refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1"
    }

    test("R1 transport decodes exact members and reconstitutes authoritative assignment bytes") {
        val decoded = decodeMonsterTronR1AssignmentText(vector, transport(PEST_MONSTER_TRON_R1_BLOCK_ID))
        decoded.map { it.seed } shouldBe exactSeeds
        decoded.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        decoded.map { it.pestSeat } shouldBe
            listOf(PestSeat.SEAT_ZERO, PestSeat.SEAT_ZERO, PestSeat.SEAT_ONE, PestSeat.SEAT_ONE)
        decoded.map { it.startingDeck } shouldBe
            listOf(
                MonsterTronStartingDeck.PEST_CONTROL,
                MonsterTronStartingDeck.MONSTER_TRON,
                MonsterTronStartingDeck.PEST_CONTROL,
                MonsterTronStartingDeck.MONSTER_TRON,
            )
        monsterTronDigest(
            authoritativeR1Assignments(decoded).toByteArray(Charsets.UTF_8)
        ) shouldBe PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256
    }

    test("retired historical block cannot satisfy the R1 transport") {
        shouldThrow<IllegalArgumentException> {
            decodeMonsterTronR1AssignmentText(
                vector,
                transport(
                    "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4"
                )
            )
        }
    }
    test("validation loader authenticates deterministic frozen R1 archive when supplied") {
        if (System.getenv("PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ZIP") != null) {
            System.getenv("PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ACK") shouldBe
                PEST_MONSTER_TRON_R1_INPUT_VALIDATE_ACK
            val loaded = PestControlTierOneMonsterTronR1InputLoader.loadValidatedFromEnvironment()
            loaded.seeds shouldBe exactSeeds
            loaded.vectorIdentity.orderedVectorSha256 shouldBe PEST_MONSTER_TRON_R1_VECTOR_SHA256
            loaded.vectorIdentity.assignmentCsvSha256 shouldBe PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256
            loaded.vectorIdentity.freezeCommit shouldBe PEST_MONSTER_TRON_R1_FREEZE_COMMIT
        }
    }

    test("R1 execution surface stays distinct from immutable historical smoke identity") {
        PEST_MONSTER_TRON_SMOKE_BLOCK_ID shouldBe
            "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4"
        PEST_MONSTER_TRON_R1_BLOCK_ID shouldBe
            "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
        (PEST_MONSTER_TRON_R1_BLOCK_ID == PEST_MONSTER_TRON_SMOKE_BLOCK_ID) shouldBe false
        MONSTER_TRON_CLAIM_REF shouldBe PEST_MONSTER_TRON_R1_FUTURE_CLAIM_REF
        MONSTER_TRON_OFFICIAL_WORKFLOW shouldBe
            ".github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml"
    }


    test("baseline guard admits reviewed R1 sealing wrappers but still rejects protected source drift") {
        val root = Files.createTempDirectory("monster-tron-r1-baseline-")
        Files.createDirectories(root.resolve("gym"))
        r1FixtureCommand(root, "git", "init", "--quiet")
        val wrappers = listOf(
            "PestControlTierOneMonsterTronOneShotBoundary.kt",
            "PestControlTierOneMonsterTronRunnerSurfacePreflight.kt",
            "PestControlTierOneMonsterTronOperationalStack.kt",
            "PestControlTierOneMonsterTronR1ExecutionIdentity.kt",
            "PestControlTierOneMonsterTronR1InputLoader.kt",
        ).map { "gym/src/main/kotlin/com/wingedsheep/gym/matchup/$it" }
        val protected = "gym/src/main/protected-r1-probe.txt"
        (wrappers + protected).forEach { path ->
            Files.createDirectories(root.resolve(path).parent)
            Files.writeString(root.resolve(path), "baseline\n")
        }
        r1FixtureCommit(root)
        val baseline = r1FixtureCommand(root, "git", "rev-parse", "HEAD").trim()
        wrappers.forEach { Files.writeString(root.resolve(it), "reviewed wrapper delta\n") }
        r1FixtureCommit(root)
        val commands = MonsterTronRepositoryCommands.discover(root.resolve("gym"))
        verifyMonsterTronBaseline(commands, baseline)

        Files.writeString(root.resolve(protected), "unreviewed protected delta\n")
        r1FixtureCommit(root)
        val failure = shouldThrow<MonsterTronRequiredCommandFailure> {
            verifyMonsterTronBaseline(commands, baseline)
        }
        failure.commandLabel shouldBe "ENGINE_BASELINE"
        failure.exitCode shouldBe 1
        failure.sanitizedOutput.contains(protected) shouldBe true
        wrappers.forEach { failure.sanitizedOutput.contains(it) shouldBe false }
    }

})


private fun r1FixtureCommand(root: Path, vararg command: String): String {
    val process = ProcessBuilder(command.toList()).directory(root.toFile()).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "R1 baseline fixture command failed" }
    return text
}

private fun r1FixtureCommit(root: Path) {
    r1FixtureCommand(root, "git", "add", ".")
    r1FixtureCommand(
        root, "git", "-c", "user.name=R1 fixture", "-c", "user.email=r1-fixture@example.invalid",
        "commit", "--quiet", "-m", "R1 baseline fixture",
    )
}
