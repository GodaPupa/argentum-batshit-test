package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.core.Zone
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.Comparator

const val PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_SHA256 =
    "675d27e529b5d25bc08ce06df402f5d3d5d0ecf4df375cfe0cbb44aa61b41662"
const val PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_STATUS =
    "SYNTHETIC_ENGINE_EVIDENCE_REHEARSED_EXECUTION_NOT_AUTHORIZED"

data class MonoBlueTerrorDisabledEngineEvidenceInspection(
    val errors: List<String>,
    val rehearsalSha256: String,
    val status: String,
    val publicationIndexSha256: String,
    val attemptOrder: List<Int>,
    val initializationOrder: List<Int>,
    val recordOrder: List<Int>,
    val syntheticEngineInitializations: Int,
    val syntheticEvidenceBundlesPublished: Int,
    val publicationFileCount: Int,
    val temporaryRootsRemoved: Int,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeExposure: Int = 0,
    val runnerEnabled: Boolean = false,
    val initializerEnabled: Boolean = false,
    val executionAuthorized: Boolean = false,
) {
    val green: Boolean get() = errors.isEmpty()
    val failClosed: Boolean get() = green && !runnerEnabled && !initializerEnabled && !executionAuthorized
}

private data class TerrorEngineEvidenceRehearsal(
    val errors: List<String>,
    val publicationIndexSha256: String,
    val attemptOrder: List<Int>,
    val initializationOrder: List<Int>,
    val recordOrder: List<Int>,
    val syntheticEngineInitializations: Int,
    val publicationFileCount: Int,
    val temporaryRootsRemoved: Int,
)

private val TERROR_ENGINE_REHEARSAL_SEEDS = listOf(
    0x7E77_0B1E_1000_0001L,
    0x7E77_0B1E_1000_0002L,
    0x7E77_0B1E_1000_0003L,
    0x7E77_0B1E_1000_0004L,
)

private object PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceRehearsal {
    fun rehearse(registry: CardRegistry): TerrorEngineEvidenceRehearsal {
        val errors = mutableListOf<String>()
        var publicationIndexSha256 = ""
        var attemptOrder = emptyList<Int>()
        var initializationOrder = emptyList<Int>()
        var recordOrder = emptyList<Int>()
        var syntheticEngineInitializations = 0
        var publicationFileCount = 0
        var temporaryRootsRemoved = 0

        val root = Files.createTempDirectory("pest-terror-engine-evidence-rehearsal-")
        try {
            val assignments = syntheticAssignments()
            val journal = MonoBlueTerrorDurableAttempts.createNew(
                evidenceRoot = root,
                assignments = assignments,
                executionSourceCommit = "f".repeat(40),
            )

            assignments.forEach { assignment ->
                journal.recordAttempt(assignment.gameNumber)
                journal.recordInitializationEntry(assignment.gameNumber)
                val opening = initializeSyntheticCell(registry, assignment)
                syntheticEngineInitializations++
                journal.recordResult(
                    assignment.gameNumber,
                    syntheticResultBytes(assignment, opening),
                )
            }

            val ledger = PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(
                journal.events(),
                MonoBlueTerrorCoordinatorDisposition.VALIDATED,
            )
            errors += ledger.errors.map { "coordinator: $it" }
            attemptOrder = ledger.attemptedGames
            initializationOrder = ledger.initializedGames
            recordOrder = ledger.recordedGames

            val publication = publishSyntheticEvidence(root.resolve(PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID))
            publicationIndexSha256 = publication.first
            publicationFileCount = publication.second
        } catch (failure: Exception) {
            errors += "synthetic engine/evidence rehearsal failed: ${failure::class.simpleName}"
        } finally {
            runCatching {
                Files.walk(root).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
                }
                temporaryRootsRemoved = 1
            }.onFailure {
                errors += "synthetic evidence cleanup failed"
            }
        }

        return TerrorEngineEvidenceRehearsal(
            errors = errors.distinct(),
            publicationIndexSha256 = publicationIndexSha256,
            attemptOrder = attemptOrder,
            initializationOrder = initializationOrder,
            recordOrder = recordOrder,
            syntheticEngineInitializations = syntheticEngineInitializations,
            publicationFileCount = publicationFileCount,
            temporaryRootsRemoved = temporaryRootsRemoved,
        )
    }

    private fun syntheticAssignments(): List<MonoBlueTerrorSmokeAssignment> =
        PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().mapIndexed { index, cell ->
            val terrorSeat = when (cell.pestSeat) {
                PestSeat.SEAT_ZERO -> PestSeat.SEAT_ONE
                PestSeat.SEAT_ONE -> PestSeat.SEAT_ZERO
            }
            val seed = TERROR_ENGINE_REHEARSAL_SEEDS[index]
            MonoBlueTerrorSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = terrorAssignmentSeedHex(seed),
                pestSeat = cell.pestSeat,
                terrorSeat = terrorSeat,
                startingDeck = cell.startingDeck,
            )
        }

    private fun initializeSyntheticCell(
        registry: CardRegistry,
        assignment: MonoBlueTerrorSmokeAssignment,
    ): List<MonoBlueTerrorOpeningConservation> {
        val seatZeroIsPest = assignment.pestSeat == PestSeat.SEAT_ZERO
        val seats = if (seatZeroIsPest) {
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
        val pestSeatIndex = if (assignment.pestSeat == PestSeat.SEAT_ZERO) 0 else 1
        val terrorSeatIndex = 1 - pestSeatIndex
        val startingPlayerIndex = when (assignment.startingDeck) {
            MonoBlueTerrorStartingDeck.PEST_CONTROL -> pestSeatIndex
            MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR -> terrorSeatIndex
        }

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

        val opening = environment.playerIds.mapIndexed { index, player ->
            val seat = if (index == 0) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
            val isPest = seat == assignment.pestSeat
            val state = environment.state
            val library = state.getLibrary(player)
            val hand = state.getHand(player)
            val otherCount = listOf(
                Zone.BATTLEFIELD,
                Zone.GRAVEYARD,
                Zone.STACK,
                Zone.EXILE,
                Zone.COMMAND,
                Zone.SIDEBOARD,
            ).sumOf { state.getZone(player, it).size }
            val libraryNames = library.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name
                    ?: error("opening library card lacks identity")
            }
            val handNames = hand.map { id ->
                state.getEntity(id)?.get<CardComponent>()?.name
                    ?: error("opening hand card lacks identity")
            }
            val expectedCounts = if (isPest) {
                PestControlPreboardDecks.pestMainCounts
            } else {
                PestControlTierOneMonoBlueTerrorReadiness.mainCounts
            }
            require((libraryNames + handNames).groupingBy { it }.eachCount() == expectedCounts)

            MonoBlueTerrorOpeningConservation(
                seat = seat,
                deckIdentity = if (isPest) "PEST_CONTROL_V10" else "SERPICO_CC_MONO_BLUE_TERROR_60",
                deckSha256 = if (isPest) PEST_CONTROL_V10_HASH else PEST_MONO_BLUE_TERROR_MAIN_SHA256,
                libraryCount = library.size,
                handCount = hand.size,
                otherZoneCount = otherCount,
                totalOwnedCards = library.size + hand.size + otherCount,
                orderedLibrarySha256 = sha256(
                    libraryNames.joinToString("\n", postfix = "\n").toByteArray()
                ),
            )
        }
        require(opening.all { it.libraryCount == 53 && it.handCount == 7 })
        require(opening.all { it.otherZoneCount == 0 && it.totalOwnedCards == 60 })
        return opening
    }

    private fun syntheticResultBytes(
        assignment: MonoBlueTerrorSmokeAssignment,
        opening: List<MonoBlueTerrorOpeningConservation>,
    ): ByteArray = buildList {
        add("schema=terror-synthetic-engine-evidence-result-v1")
        add("game=${assignment.gameNumber}")
        add("pestSeat=${assignment.pestSeat.name}")
        add("startingDeck=${assignment.startingDeck.name}")
        opening.sortedBy { it.seat.ordinal }.forEach { conservation ->
            add(
                listOf(
                    conservation.seat.name,
                    conservation.deckIdentity,
                    conservation.deckSha256,
                    conservation.libraryCount,
                    conservation.handCount,
                    conservation.otherZoneCount,
                    conservation.totalOwnedCards,
                    conservation.orderedLibrarySha256,
                ).joinToString("|")
            )
        }
        add("submittedActions=0")
    }.joinToString("\n", postfix = "\n").toByteArray()

    private fun publishSyntheticEvidence(blockDirectory: Path): Pair<String, Int> {
        val expected = buildSet {
            add("claim.txt")
            (1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES).forEach { game ->
                add("game-$game.attempt")
                add("game-$game.initialization-entry")
                add("game-$game.raw")
                add("game-$game.record")
            }
        }
        val observed = Files.list(blockDirectory).use { paths ->
            paths.filter(Files::isRegularFile)
                .map { it.fileName.toString() }
                .toList()
                .toSet()
        }
        require(observed == expected) { "synthetic evidence member set mismatch" }

        val indexBytes = expected.sorted().joinToString("\n", postfix = "\n") { name ->
            "$name|${sha256(Files.readAllBytes(blockDirectory.resolve(name)))}"
        }.toByteArray(Charsets.UTF_8)
        writeSyntheticPublication(blockDirectory.resolve("publication.index"), indexBytes)
        require(Files.readAllBytes(blockDirectory.resolve("publication.index")).contentEquals(indexBytes))
        return sha256(indexBytes) to expected.size
    }

    private fun writeSyntheticPublication(path: Path, bytes: ByteArray) {
        FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        FileChannel.open(path.parent, READ).use { it.force(true) }
    }
}

/**
 * Inspection-only facade. It uses fixed nonexperimental assignments, temporary evidence roots, and
 * real engine initialization, but exposes no seed, path, environment, initializer, runner, or action.
 */
object PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceGate {
    fun inspect(registry: CardRegistry): MonoBlueTerrorDisabledEngineEvidenceInspection {
        val rehearsal = PestControlTierOneMonoBlueTerrorDisabledEngineEvidenceRehearsal.rehearse(registry)
        val errors = rehearsal.errors.toMutableList()
        if (rehearsal.attemptOrder != listOf(1, 2, 3, 4)) errors += "attempt order mismatch"
        if (rehearsal.initializationOrder != listOf(1, 2, 3, 4)) errors += "initialization order mismatch"
        if (rehearsal.recordOrder != listOf(1, 2, 3, 4)) errors += "record order mismatch"
        if (rehearsal.syntheticEngineInitializations != 4) errors += "synthetic engine initialization count mismatch"
        if (rehearsal.publicationFileCount != 17) errors += "publication source file count mismatch"
        if (rehearsal.temporaryRootsRemoved != 1) errors += "temporary root cleanup mismatch"
        if (rehearsal.publicationIndexSha256.length != 64) errors += "publication index hash missing"

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-disabled-engine-evidence-v1",
            "status=$PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_STATUS",
            "protocolId=$PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID",
            "syntheticEngineInitializations=4",
            "syntheticEvidenceBundlesPublished=1",
            "attemptOrder=${rehearsal.attemptOrder.joinToString(",")}",
            "initializationOrder=${rehearsal.initializationOrder.joinToString(",")}",
            "recordOrder=${rehearsal.recordOrder.joinToString(",")}",
            "publicationFileCount=${rehearsal.publicationFileCount}",
            "temporaryRootsRemoved=${rehearsal.temporaryRootsRemoved}",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "outcomeExposure=0",
            "runnerEnabled=false",
            "initializerEnabled=false",
            "executionAuthorized=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val rehearsalSha256 = sha256(proofBytes)
        if (rehearsalSha256 != PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_SHA256) {
            errors += "disabled engine/evidence proof mismatch"
        }

        return MonoBlueTerrorDisabledEngineEvidenceInspection(
            errors = errors.distinct(),
            rehearsalSha256 = rehearsalSha256,
            status = PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_STATUS,
            publicationIndexSha256 = rehearsal.publicationIndexSha256,
            attemptOrder = rehearsal.attemptOrder,
            initializationOrder = rehearsal.initializationOrder,
            recordOrder = rehearsal.recordOrder,
            syntheticEngineInitializations = rehearsal.syntheticEngineInitializations,
            syntheticEvidenceBundlesPublished = if (rehearsal.publicationIndexSha256.length == 64) 1 else 0,
            publicationFileCount = rehearsal.publicationFileCount,
            temporaryRootsRemoved = rehearsal.temporaryRootsRemoved,
        )
    }
}
