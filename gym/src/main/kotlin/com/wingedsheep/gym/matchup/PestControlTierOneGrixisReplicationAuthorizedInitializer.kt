package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.GameEnvironment

/**
 * Replication-specific initializer.
 *
 * This surface accepts only the frozen twelve-game replication identity. It deliberately does not
 * broaden or modify the original four-game smoke initializer.
 */
object PestControlTierOneGrixisReplicationAuthorizedInitializer {
    fun initialize(
        registry: CardRegistry,
        assignment: GrixisSmokeAssignment,
        vectorIdentity: GrixisSmokeVectorIdentity,
        executionCommit: String,
        durableAttemptRecorded: Boolean,
    ): GrixisAuthorizedOfficialGame {
        require(durableAttemptRecorded) { "durable attempt marker is required before initialization" }
        require(vectorIdentity.orderedVectorSha256 == PEST_GRIXIS_REPLICATION_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_GRIXIS_REPLICATION_MANIFEST_SHA256)
        require(vectorIdentity.freezeCommit == "5234db81bc87b6061bc3dfb891544205e66acc93")
        require(executionCommit.length == 40 && executionCommit.all { it in '0'..'9' || it in 'a'..'f' })
        require(assignment.gameNumber in 2..12) {
            "replication Game 1 is consumed/incomplete and cannot be initialized again"
        }

        val expected = PestControlTierOneGrixisReplicationExecutionInputLoader
            .replicationCells()[assignment.gameNumber - 1]
        require(assignment.pestSeat == expected.pestSeat)
        require(assignment.startingDeck == expected.startingDeck)
        require(assignment.grixisSeat != assignment.pestSeat)

        val readinessErrors = PestControlTierOneGrixisReadiness.validationErrors(TierOneGrixisReadiness(), registry)
        require(readinessErrors.isEmpty()) { readinessErrors.joinToString("; ") }

        val provenance = PestControlTierOneGrixisGameAdapter.provenance(
            assignment = assignment,
            vectorIdentity = vectorIdentity,
            sourceCommit = executionCommit,
        )
        val seats = if (assignment.pestSeat == PestSeat.SEAT_ZERO) {
            listOf(
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
                "Pasquale Grixis Affinity" to PestControlTierOneGrixisReadiness.mainDeck(),
            )
        } else {
            listOf(
                "Pasquale Grixis Affinity" to PestControlTierOneGrixisReadiness.mainDeck(),
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            )
        }
        val startingPlayerIndex = seats.indexOfFirst { (name) ->
            (assignment.startingDeck == GrixisStartingDeck.PEST_CONTROL && name.startsWith("Pest")) ||
                (assignment.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY && name.startsWith("Pasquale"))
        }
        require(startingPlayerIndex in 0..1)

        val environment = GameEnvironment.create(registry)
        environment.reset(
            GameConfig(
                players = seats.map { (name, deck) -> PlayerConfig(name, deck, startingLife = 20) },
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = startingPlayerIndex,
                seed = assignment.seed,
            )
        )
        return GrixisAuthorizedOfficialGame(provenance, environment)
    }
}
