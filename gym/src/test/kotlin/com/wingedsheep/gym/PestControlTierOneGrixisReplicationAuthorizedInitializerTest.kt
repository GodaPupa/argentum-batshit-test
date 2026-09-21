package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class PestControlTierOneGrixisReplicationAuthorizedInitializerTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    val identity = GrixisSmokeVectorIdentity(
        freezeCommit = "5234db81bc87b6061bc3dfb891544205e66acc93",
        orderedVectorSha256 = PEST_GRIXIS_REPLICATION_VECTOR_SHA256,
        assignmentCsvSha256 = PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256,
        freezeManifestSha256 = PEST_GRIXIS_REPLICATION_MANIFEST_SHA256,
    )

    fun assignment(game: Int): GrixisSmokeAssignment {
        val cell = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()[game - 1]
        val seed = 8_880_000L + game
        return GrixisSmokeAssignment(
            gameNumber = game,
            seed = seed,
            seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
            pestSeat = cell.pestSeat,
            grixisSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
            startingDeck = cell.startingDeck,
        )
    }

    test("replication initializer structurally refuses consumed Game 1") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisReplicationAuthorizedInitializer.initialize(
                registry, assignment(1), identity,
                "80f62d1c87f8897b47ddb408b52d0fa619db7c70", true,
            )
        }
    }

    test("replication initializer accepts untouched Games 2 through 12 with synthetic seeds") {
        (2..12).forEach { game ->
            val initialized = PestControlTierOneGrixisReplicationAuthorizedInitializer.initialize(
                registry, assignment(game), identity,
                "80f62d1c87f8897b47ddb408b52d0fa619db7c70", true,
            )
            initialized.provenance.assignment.gameNumber shouldBe game
            initialized.provenance.vectorIdentity shouldBe identity
        }
    }

    test("replication initializer rejects old four-game smoke identity") {
        val oldIdentity = GrixisSmokeVectorIdentity(
            freezeCommit = "6465548adfa7039ff02edb8834e33318231903f6",
            orderedVectorSha256 = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_GRIXIS_FROZEN_MANIFEST_SHA256,
        )
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisReplicationAuthorizedInitializer.initialize(
                registry, assignment(2), oldIdentity,
                "80f62d1c87f8897b47ddb408b52d0fa619db7c70", true,
            )
        }
    }

    test("replication initializer requires durable attempt before initialization") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisReplicationAuthorizedInitializer.initialize(
                registry, assignment(2), identity,
                "80f62d1c87f8897b47ddb408b52d0fa619db7c70", false,
            )
        }
    }
})
