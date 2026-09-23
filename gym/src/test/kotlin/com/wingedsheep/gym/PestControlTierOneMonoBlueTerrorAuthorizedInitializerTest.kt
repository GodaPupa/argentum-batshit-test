package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val AUTH_TERROR_SYNTHETIC_VECTOR_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "1".repeat(40),
    orderedVectorSha256 = PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
    assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
    freezeManifestSha256 = PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256,
)

class PestControlTierOneMonoBlueTerrorAuthorizedInitializerTest : FunSpec({
    val registry = fullTerrorAuthorizedRegistry()

    test("authorized initializer respects all four seat and starting-deck cells without advancing gameplay") {
        PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().forEach { cell ->
            val seed = 9_930_000L + cell.gameNumber
            val assignment = MonoBlueTerrorSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
                pestSeat = cell.pestSeat,
                terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) {
                    PestSeat.SEAT_ONE
                } else {
                    PestSeat.SEAT_ZERO
                },
                startingDeck = cell.startingDeck,
            )

            val game = PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
                registry = registry,
                assignment = assignment,
                vectorIdentity = AUTH_TERROR_SYNTHETIC_VECTOR_IDENTITY,
                executionCommit = "2".repeat(40),
                durableAttemptRecorded = true,
            )

            game.provenance.gameNumber shouldBe cell.gameNumber
            game.provenance.seed shouldBe seed
            game.provenance.pestSeat shouldBe cell.pestSeat
            game.provenance.startingDeck shouldBe cell.startingDeck
            game.environment.isTerminal shouldBe false
            game.environment.turnNumber shouldBe 1
            game.environment.playerIds.size shouldBe 2
        }
    }

    test("initializer fails closed before durable attempt evidence") {
        val assignment = terrorAuthorizedSyntheticAssignment(1, 9_940_001L)

        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
                registry,
                assignment,
                AUTH_TERROR_SYNTHETIC_VECTOR_IDENTITY,
                "3".repeat(40),
                durableAttemptRecorded = false,
            )
        }
    }

    test("initializer rejects drifted frozen identity") {
        val assignment = terrorAuthorizedSyntheticAssignment(1, 9_950_001L)

        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
                registry,
                assignment,
                AUTH_TERROR_SYNTHETIC_VECTOR_IDENTITY.copy(
                    orderedVectorSha256 = "f".repeat(64)
                ),
                "4".repeat(40),
                durableAttemptRecorded = true,
            )
        }
    }

    test("initializer rejects malformed execution commit") {
        val assignment = terrorAuthorizedSyntheticAssignment(1, 9_960_001L)

        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
                registry,
                assignment,
                AUTH_TERROR_SYNTHETIC_VECTOR_IDENTITY,
                "not-a-commit",
                durableAttemptRecorded = true,
            )
        }
    }
})

private fun terrorAuthorizedSyntheticAssignment(
    game: Int,
    seed: Long,
): MonoBlueTerrorSmokeAssignment {
    val cell = PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate()
        .single { it.gameNumber == game }
    return MonoBlueTerrorSmokeAssignment(
        gameNumber = game,
        seed = seed,
        seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
        pestSeat = cell.pestSeat,
        terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) {
            PestSeat.SEAT_ONE
        } else {
            PestSeat.SEAT_ZERO
        },
        startingDeck = cell.startingDeck,
    )
}

private fun fullTerrorAuthorizedRegistry(): CardRegistry = CardRegistry().apply {
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}
