package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val AUTH_SYNTHETIC_VECTOR_IDENTITY = GrixisSmokeVectorIdentity(
    freezeCommit = "1".repeat(40),
    orderedVectorSha256 = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
    assignmentCsvSha256 = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
    freezeManifestSha256 = PEST_GRIXIS_FROZEN_MANIFEST_SHA256,
)

class PestControlTierOneGrixisAuthorizedInitializerTest : FunSpec({
    val registry = fullRegistry()

    test("authorized initializer respects seat and starting-deck assignment without advancing gameplay") {
        PestControlTierOneGrixisSmokeHarness.cellTemplate().forEach { cell ->
            val seed = 9_930_000L + cell.gameNumber
            val assignment = GrixisSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
                pestSeat = cell.pestSeat,
                grixisSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
            val game = PestControlTierOneGrixisAuthorizedInitializer.initialize(
                registry = registry,
                assignment = assignment,
                vectorIdentity = AUTH_SYNTHETIC_VECTOR_IDENTITY,
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

    test("initializer fails closed before a durable attempt marker") {
        val assignment = GrixisSmokeAssignment(
            gameNumber = 1,
            seed = 9_940_001L,
            seedHex = "0x${9_940_001L.toULong().toString(16).padStart(16, '0')}",
            pestSeat = PestSeat.SEAT_ZERO,
            grixisSeat = PestSeat.SEAT_ONE,
            startingDeck = GrixisStartingDeck.PEST_CONTROL,
        )
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisAuthorizedInitializer.initialize(
                registry,
                assignment,
                AUTH_SYNTHETIC_VECTOR_IDENTITY,
                "3".repeat(40),
                durableAttemptRecorded = false,
            )
        }
    }

    test("initializer rejects drifted frozen identity") {
        val assignment = GrixisSmokeAssignment(
            gameNumber = 1,
            seed = 9_950_001L,
            seedHex = "0x${9_950_001L.toULong().toString(16).padStart(16, '0')}",
            pestSeat = PestSeat.SEAT_ZERO,
            grixisSeat = PestSeat.SEAT_ONE,
            startingDeck = GrixisStartingDeck.PEST_CONTROL,
        )
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisAuthorizedInitializer.initialize(
                registry,
                assignment,
                AUTH_SYNTHETIC_VECTOR_IDENTITY.copy(orderedVectorSha256 = "f".repeat(64)),
                "4".repeat(40),
                durableAttemptRecorded = true,
            )
        }
    }
})

private fun fullRegistry(): CardRegistry = CardRegistry().apply {
    register(PredefinedTokens.allTokens)
    MtgSetCatalog.all.forEach { set ->
        register(set.cards)
        register(set.basicLands)
    }
}
