package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours

class PestControlTierOneGrixisProductionDriverTest : FunSpec({
    test("synthetic authorized Pest versus Grixis game reaches a clean terminal").config(timeout = 2.hours) {
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val seed = 9_970_001L
        val assignment = GrixisSmokeAssignment(
            gameNumber = 1,
            seed = seed,
            seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
            pestSeat = PestSeat.SEAT_ZERO,
            grixisSeat = PestSeat.SEAT_ONE,
            startingDeck = GrixisStartingDeck.PEST_CONTROL,
        )
        val identity = GrixisSmokeVectorIdentity(
            freezeCommit = "6".repeat(40),
            orderedVectorSha256 = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_GRIXIS_FROZEN_MANIFEST_SHA256,
        )
        val game = PestControlTierOneGrixisAuthorizedInitializer.initialize(
            registry = registry,
            assignment = assignment,
            vectorIdentity = identity,
            executionCommit = "7".repeat(40),
            durableAttemptRecorded = true,
        )
        val raw = PestControlTierOneGrixisProductionDriver.drive(registry, game)

        raw.terminal?.gameOver shouldBe true
        raw.actions.isNotEmpty() shouldBe true
        raw.actions.map { it.sequence } shouldBe (1..raw.actions.size).toList()
        raw.actions.all { it.accepted && it.rejectionReason == null } shouldBe true
        raw.provenance.gameNumber shouldBe 1
        PestControlTierOneGrixisProductionDriver.encode(raw).isNotEmpty() shouldBe true
    }
})
