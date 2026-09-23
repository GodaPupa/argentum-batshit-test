package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.GameEnvironment

data class MonoBlueTerrorAuthorizedOfficialGame(
    val provenance: MonoBlueTerrorSmokeProvenance,
    val environment: GameEnvironment,
)

/**
 * First operational Mono-Blue Terror initialization surface.
 *
 * It may initialize one supplied assignment only after the reviewed execution authorization is
 * green and a durable attempt has already been recorded. It never advances gameplay and exposes no
 * retry, replacement, seed-generation, artifact-loading, evidence-root, or workflow operation.
 */
object PestControlTierOneMonoBlueTerrorAuthorizedInitializer {
    fun initialize(
        registry: CardRegistry,
        assignment: MonoBlueTerrorSmokeAssignment,
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        executionCommit: String,
        durableAttemptRecorded: Boolean,
    ): MonoBlueTerrorAuthorizedOfficialGame {
        val authorization = PestControlTierOneMonoBlueTerrorExecutionAuthorization.inspect()
        require(
            authorization.green &&
                authorization.executionAuthorized &&
                authorization.failClosed
        ) {
            "Mono-Blue Terror execution authorization is not green"
        }
        require(durableAttemptRecorded) {
            "durable attempt marker is required before initialization"
        }
        require(vectorIdentity.orderedVectorSha256 == PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256)
        require(
            executionCommit.length == 40 &&
                executionCommit.all { it in '0'..'9' || it in 'a'..'f' }
        )

        val readinessErrors = PestControlTierOneMonoBlueTerrorReadiness.validationErrors(
            TierOneMonoBlueTerrorReadiness(),
            registry,
        )
        require(readinessErrors.isEmpty()) { readinessErrors.joinToString("; ") }

        val provenance = PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
            assignment = assignment,
            vectorIdentity = vectorIdentity,
            sourceCommit = executionCommit,
        )

        val seats = if (assignment.pestSeat == PestSeat.SEAT_ZERO) {
            listOf(
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
                "Serpico_CC Mono-Blue Terror" to PestControlTierOneMonoBlueTerrorReadiness.mainDeck(),
            )
        } else {
            listOf(
                "Serpico_CC Mono-Blue Terror" to PestControlTierOneMonoBlueTerrorReadiness.mainDeck(),
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            )
        }

        val startingPlayerIndex = seats.indexOfFirst { (name) ->
            (assignment.startingDeck == MonoBlueTerrorStartingDeck.PEST_CONTROL &&
                name.startsWith("Pest")) ||
                (assignment.startingDeck == MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR &&
                    name.startsWith("Serpico"))
        }
        require(startingPlayerIndex in 0..1)

        val environment = GameEnvironment.create(registry)
        environment.reset(
            GameConfig(
                players = seats.map { (name, deck) ->
                    PlayerConfig(name, deck, startingLife = 20)
                },
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = startingPlayerIndex,
                seed = assignment.seed,
            )
        )
        return MonoBlueTerrorAuthorizedOfficialGame(provenance, environment)
    }
}
