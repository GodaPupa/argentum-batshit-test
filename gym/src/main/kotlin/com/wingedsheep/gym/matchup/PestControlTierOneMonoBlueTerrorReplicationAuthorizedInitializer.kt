package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.GameEnvironment

object PestControlTierOneMonoBlueTerrorReplicationAuthorizedInitializer {
    fun initialize(
        registry: CardRegistry,
        assignment: MonoBlueTerrorSmokeAssignment,
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        executionCommit: String,
        durableAttemptRecorded: Boolean,
    ): MonoBlueTerrorAuthorizedOfficialGame {
        val authorization =
            PestControlTierOneMonoBlueTerrorReplicationExecutionAuthorization.inspect()
        require(authorization.green && authorization.executionAuthorized) {
            "Mono-Blue Terror replication execution is not authorized"
        }
        require(durableAttemptRecorded) {
            "durable replication attempt marker is required before initialization"
        }
        require(vectorIdentity.freezeCommit == PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT)
        require(
            vectorIdentity.orderedVectorSha256 ==
                PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256
        )
        require(
            vectorIdentity.assignmentCsvSha256 ==
                PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256
        )
        require(
            vectorIdentity.freezeManifestSha256 ==
                PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256
        )
        require(
            executionCommit.length == 40 &&
                executionCommit.all { it in '0'..'9' || it in 'a'..'f' } &&
                executionCommit != "0".repeat(40)
        )

        val cells =
            PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells()
        require(assignment.gameNumber in 1..PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
        val expectedCell = cells[assignment.gameNumber - 1]
        require(assignment.pestSeat == expectedCell.pestSeat)
        require(assignment.startingDeck == expectedCell.startingDeck)
        require(assignment.terrorSeat != assignment.pestSeat)
        require(assignment.seed != 0L)
        require(assignment.seedHex == terrorAssignmentSeedHex(assignment.seed))

        val readinessErrors = PestControlTierOneMonoBlueTerrorReadiness.validationErrors(
            TierOneMonoBlueTerrorReadiness(),
            registry,
        )
        require(readinessErrors.isEmpty()) { readinessErrors.joinToString("; ") }

        val provenance = MonoBlueTerrorSmokeProvenance(
            protocolId = PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
            blockId = PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID,
            sourceCommit = executionCommit,
            acceptedReadinessCommit = PEST_MONO_BLUE_TERROR_READINESS_COMMIT,
            freezeCommit = vectorIdentity.freezeCommit,
            orderedVectorSha256 = vectorIdentity.orderedVectorSha256,
            assignmentCsvSha256 = vectorIdentity.assignmentCsvSha256,
            freezeManifestSha256 = vectorIdentity.freezeManifestSha256,
            pestMainSha256 = PEST_CONTROL_V10_HASH,
            terrorMainSha256 = PEST_MONO_BLUE_TERROR_MAIN_SHA256,
            terrorComplete75Sha256 = PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256,
            gameNumber = assignment.gameNumber,
            seed = assignment.seed,
            seedHex = assignment.seedHex,
            pestSeat = assignment.pestSeat,
            terrorSeat = assignment.terrorSeat,
            startingDeck = assignment.startingDeck,
            classification = "PRIMARY_REPLICATION_12",
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
