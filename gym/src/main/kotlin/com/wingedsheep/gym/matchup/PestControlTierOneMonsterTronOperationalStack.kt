package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.ExactlyOneSubmissionResult
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.ZipInputStream

const val PEST_MONSTER_TRON_EXECUTION_INPUT_VALIDATE_ACK =
    "LOAD_FROZEN_TIER_ONE_MONSTER_TRON_4_FOR_VALIDATION_ONLY"
const val PEST_MONSTER_TRON_EXECUTION_INPUT_EXECUTE_ACK =
    "AUTOMATIC_ONE_SHOT_MONSTER_TRON_EXECUTION"

internal const val MONSTER_TRON_FROZEN_MAX_ARCHIVE_BYTES = 65_536
internal const val MONSTER_TRON_FROZEN_MAX_EXPANDED_BYTES = 32_768
internal const val MONSTER_TRON_ASSIGNMENT_HEADER =
    "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,monster_tron_seat," +
        "starting_deck,pest_play_draw,pest_main_sha256,monster_tron_main_sha256,qualified_runner"

data class MonsterTronSmokeAssignment(
    val gameNumber: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val monsterTronSeat: PestSeat,
    val startingDeck: MonsterTronStartingDeck,
)

data class MonsterTronSmokeVectorIdentity(
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
)

@Serializable
data class MonsterTronSmokeProvenance(
    val protocolId: String,
    val blockId: String,
    val sourceCommit: String,
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
    val pestMainSha256: String,
    val monsterTronMainSha256: String,
    val monsterTronComplete75Sha256: String,
    val gameNumber: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val monsterTronSeat: PestSeat,
    val startingDeck: MonsterTronStartingDeck,
    val classification: String = "NONEXPERIMENTAL_SMOKE_VECTOR",
)

data class MonsterTronFrozenArtifactInspection(
    val errors: List<String>,
    val archiveSha256: String,
    val memberSha256: Map<String, String>,
) {
    val verified: Boolean get() = errors.isEmpty()
}

internal fun monsterTronFrozenMemberPins(): Map<String, String> = linkedMapOf(
    "ordered-seeds.txt" to PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
    "assignments.csv" to PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256,
    "freeze-manifest.json" to PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
    "quarantined-vector.json" to PEST_MONSTER_TRON_FROZEN_SMOKE_QUARANTINE_SHA256,
    "artifacts.sha256" to PEST_MONSTER_TRON_FROZEN_SMOKE_CHECKSUMS_SHA256,
)

object PestControlTierOneMonsterTronFrozenArtifactVerifier {
    fun inspect(archive: ByteArray): MonsterTronFrozenArtifactInspection =
        inspectMonsterTronPinnedArchive(
            archive = archive,
            expectedArchiveSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256,
            expectedMembers = monsterTronFrozenMemberPins(),
        )
}

internal fun inspectMonsterTronPinnedArchive(
    archive: ByteArray,
    expectedArchiveSha256: String,
    expectedMembers: Map<String, String>,
): MonsterTronFrozenArtifactInspection {
    if (archive.isEmpty() || archive.size > MONSTER_TRON_FROZEN_MAX_ARCHIVE_BYTES) {
        return MonsterTronFrozenArtifactInspection(
            listOf("archive size outside accepted bounds"),
            "",
            emptyMap(),
        )
    }
    val snapshot = archive.copyOf()
    val archiveHash = monsterTronDigest(snapshot)
    if (archiveHash != expectedArchiveSha256) {
        return MonsterTronFrozenArtifactInspection(
            listOf("archive hash mismatch"),
            archiveHash,
            emptyMap(),
        )
    }

    val observed = linkedMapOf<String, String>()
    val errors = mutableListOf<String>()
    var expandedBytes = 0
    try {
        ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in expectedMembers) {
                    errors += "unexpected archive member"
                    break
                }
                if (entry.name in observed) {
                    errors += "duplicate archive member"
                    break
                }
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(4096)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    expandedBytes += count
                    if (expandedBytes > MONSTER_TRON_FROZEN_MAX_EXPANDED_BYTES) {
                        throw IOException("expanded byte limit exceeded")
                    }
                    digest.update(buffer, 0, count)
                }
                val memberHash = digest.digest().monsterTronHex()
                observed[entry.name] = memberHash
                if (memberHash != expectedMembers.getValue(entry.name)) {
                    errors += "member hash mismatch: ${entry.name}"
                }
                zip.closeEntry()
            }
        }
    } catch (_: IOException) {
        errors += "malformed archive or expanded byte limit exceeded"
    } catch (_: IllegalArgumentException) {
        errors += "malformed archive entry encoding"
    }
    if (observed.keys != expectedMembers.keys) errors += "archive member set mismatch"
    return MonsterTronFrozenArtifactInspection(errors.distinct(), archiveHash, observed.toMap())
}

internal object PestControlTierOneMonsterTronAssignmentDecoder {
    fun decode(archive: ByteArray): List<MonsterTronSmokeAssignment> {
        require(archive.size in 1..MONSTER_TRON_FROZEN_MAX_ARCHIVE_BYTES)
        val snapshot = archive.copyOf()
        val inspected = PestControlTierOneMonsterTronFrozenArtifactVerifier.inspect(snapshot)
        require(inspected.verified) { inspected.errors.joinToString("; ") }

        val members = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "ordered-seeds.txt" || entry.name == "assignments.csv") {
                    members[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return decodeMonsterTronAssignmentText(
            strictMonsterTronUtf8(members.getValue("ordered-seeds.txt")),
            strictMonsterTronUtf8(members.getValue("assignments.csv")),
        )
    }
}

internal fun strictMonsterTronUtf8(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (failure: java.nio.charset.CharacterCodingException) {
    throw IllegalArgumentException("invalid UTF-8", failure)
}

internal fun decodeMonsterTronAssignmentText(
    vectorText: String,
    assignmentText: String,
): List<MonsterTronSmokeAssignment> {
    fun lines(text: String): List<String> {
        require(text.endsWith("\n") && '\r' !in text && '\u0000' !in text) {
            "noncanonical LF text"
        }
        return text.dropLast(1).split('\n')
    }

    val vector = lines(vectorText)
    require(vector.size == PEST_MONSTER_TRON_SMOKE_GAMES)
    val seeds = vector.map { token ->
        val seed = token.toLongOrNull()
        require(seed != null && seed != 0L && seed.toString() == token) {
            "invalid canonical nonzero signed seed"
        }
        seed
    }
    require(seeds.distinct().size == PEST_MONSTER_TRON_SMOKE_GAMES)

    val rows = lines(assignmentText)
    require(rows.size == PEST_MONSTER_TRON_SMOKE_GAMES + 1)
    require(rows.first() == MONSTER_TRON_ASSIGNMENT_HEADER)
    val assignments = rows.drop(1).mapIndexed { index, line ->
        require('"' !in line)
        val fields = line.split(',')
        require(fields.size == 12) { "assignment column count mismatch" }

        val pestSeat = if (index < 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
        val tronSeat = if (index < 2) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO
        val starter = if (index % 2 == 0) {
            MonsterTronStartingDeck.PEST_CONTROL
        } else {
            MonsterTronStartingDeck.MONSTER_TRON
        }
        val expected = listOf(
            PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
            PEST_MONSTER_TRON_SMOKE_BLOCK_ID,
            (index + 1).toString(),
            seeds[index].toString(),
            monsterTronSeedHex(seeds[index]),
            pestSeat.name,
            tronSeat.name,
            starter.name,
            if (index % 2 == 0) "PLAY" else "DRAW",
            PEST_CONTROL_V10_HASH,
            PEST_MONSTER_TRON_MAIN_SHA256,
            PEST_V2_QUALIFIED_RUNNER,
        )
        require(fields == expected) { "assignment identity mismatch at game ${index + 1}" }
        MonsterTronSmokeAssignment(
            gameNumber = index + 1,
            seed = seeds[index],
            seedHex = fields[4],
            pestSeat = pestSeat,
            monsterTronSeat = tronSeat,
            startingDeck = starter,
        )
    }
    return Collections.unmodifiableList(assignments)
}

internal fun monsterTronSeedHex(seed: Long): String =
    "0x${seed.toULong().toString(16).padStart(16, '0')}"

object PestControlTierOneMonsterTronGameAdapter {
    fun provenance(
        assignment: MonsterTronSmokeAssignment,
        vectorIdentity: MonsterTronSmokeVectorIdentity,
        sourceCommit: String,
    ): MonsterTronSmokeProvenance {
        require(sourceCommit.isMonsterTronLowerHex(40))
        require(vectorIdentity.freezeCommit.isMonsterTronLowerHex(40))
        require(vectorIdentity.orderedVectorSha256.isMonsterTronLowerHex(64))
        require(vectorIdentity.assignmentCsvSha256.isMonsterTronLowerHex(64))
        require(vectorIdentity.freezeManifestSha256.isMonsterTronLowerHex(64))
        require(assignment.gameNumber in 1..PEST_MONSTER_TRON_SMOKE_GAMES)
        require(assignment.seed != 0L)
        require(assignment.seedHex == monsterTronSeedHex(assignment.seed))
        require(assignment.pestSeat != assignment.monsterTronSeat)

        val expectedCell = PestControlTierOneMonsterTronSmokeHarness.cellTemplate()
            .single { it.gameNumber == assignment.gameNumber }
        require(assignment.pestSeat == expectedCell.pestSeat)
        require(assignment.startingDeck == expectedCell.startingDeck)

        return MonsterTronSmokeProvenance(
            protocolId = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
            blockId = PEST_MONSTER_TRON_SMOKE_BLOCK_ID,
            sourceCommit = sourceCommit,
            freezeCommit = vectorIdentity.freezeCommit,
            orderedVectorSha256 = vectorIdentity.orderedVectorSha256,
            assignmentCsvSha256 = vectorIdentity.assignmentCsvSha256,
            freezeManifestSha256 = vectorIdentity.freezeManifestSha256,
            pestMainSha256 = PEST_CONTROL_V10_HASH,
            monsterTronMainSha256 = PEST_MONSTER_TRON_MAIN_SHA256,
            monsterTronComplete75Sha256 = PEST_MONSTER_TRON_COMPLETE_75_SHA256,
            gameNumber = assignment.gameNumber,
            seed = assignment.seed,
            seedHex = assignment.seedHex,
            pestSeat = assignment.pestSeat,
            monsterTronSeat = assignment.monsterTronSeat,
            startingDeck = assignment.startingDeck,
        )
    }
}

data class MonsterTronOfficialExecutionInput(
    val vectorIdentity: MonsterTronSmokeVectorIdentity,
    val seeds: List<Long>,
    val assignments: List<MonsterTronSmokeAssignment>,
    val archiveSha256: String,
)

object PestControlTierOneMonsterTronOfficialExecutionInputLoader {
    fun loadValidatedFromEnvironment(): MonsterTronOfficialExecutionInput {
        require(
            System.getenv("PEST_MONSTER_TRON_EXECUTION_INPUT_ACK") ==
                PEST_MONSTER_TRON_EXECUTION_INPUT_VALIDATE_ACK
        ) { "exact Monster Tron validation acknowledgement is required" }
        return loadFromEnvironment()
    }

    fun loadForAuthorizedExecutionFromEnvironment(): MonsterTronOfficialExecutionInput {
        require(
            System.getenv("PEST_MONSTER_TRON_EXECUTION_INPUT_ACK") ==
                PEST_MONSTER_TRON_EXECUTION_INPUT_EXECUTE_ACK
        ) { "exact Monster Tron execution acknowledgement is required" }
        return loadFromEnvironment()
    }

    private fun loadFromEnvironment(): MonsterTronOfficialExecutionInput {
        val path = Path.of(
            System.getenv("PEST_MONSTER_TRON_EXECUTION_INPUT_ZIP")
                ?: error("PEST_MONSTER_TRON_EXECUTION_INPUT_ZIP required")
        )
        require(Files.isRegularFile(path)) { "official frozen ZIP is absent" }
        return loadValidatedArchive(Files.readAllBytes(path))
    }

    internal fun loadValidatedArchive(archive: ByteArray): MonsterTronOfficialExecutionInput {
        val snapshot = archive.copyOf()
        val inspection = PestControlTierOneMonsterTronFrozenArtifactVerifier.inspect(snapshot)
        require(inspection.verified) {
            "frozen artifact verification failed: ${inspection.errors.joinToString()}"
        }
        require(inspection.archiveSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256)

        val assignments = PestControlTierOneMonsterTronAssignmentDecoder.decode(snapshot)
        require(assignments.size == PEST_MONSTER_TRON_SMOKE_GAMES)
        require(assignments.map { it.gameNumber } == (1..PEST_MONSTER_TRON_SMOKE_GAMES).toList())
        val seeds = assignments.map { it.seed }
        require(seeds.distinct().size == seeds.size && seeds.none { it == 0L })
        require(
            monsterTronDigest(
                seeds.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
            ) == PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256
        )

        val identity = MonsterTronSmokeVectorIdentity(
            freezeCommit = PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE,
            orderedVectorSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
        )

        assignments.zip(PestControlTierOneMonsterTronSmokeHarness.cellTemplate())
            .forEach { (assignment, cell) ->
                require(assignment.gameNumber == cell.gameNumber)
                require(assignment.pestSeat == cell.pestSeat)
                require(assignment.startingDeck == cell.startingDeck)
                require(assignment.monsterTronSeat != assignment.pestSeat)
            }

        return MonsterTronOfficialExecutionInput(
            vectorIdentity = identity,
            seeds = seeds.toList(),
            assignments = assignments.toList(),
            archiveSha256 = inspection.archiveSha256,
        )
    }
}

data class MonsterTronAuthorizedOfficialGame(
    val provenance: MonsterTronSmokeProvenance,
    val environment: GameEnvironment,
)

object PestControlTierOneMonsterTronAuthorizedInitializer {
    fun initialize(
        registry: CardRegistry,
        assignment: MonsterTronSmokeAssignment,
        vectorIdentity: MonsterTronSmokeVectorIdentity,
        executionCommit: String,
        durableAttemptRecorded: Boolean,
    ): MonsterTronAuthorizedOfficialGame {
        val authorization = PestControlTierOneMonsterTronExecutionAuthorization.inspect()
        require(authorization.green && authorization.executionAuthorized && authorization.failClosed) {
            "Monster Tron execution authorization is not green"
        }
        require(durableAttemptRecorded) { "durable attempt marker is required before initialization" }
        require(vectorIdentity.orderedVectorSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256)
        require(executionCommit.isMonsterTronLowerHex(40) && executionCommit != "0".repeat(40))

        val readinessErrors = PestControlTierOneMonsterTronReadiness.validationErrors(
            TierOneMonsterTronReadiness(),
            registry,
        )
        require(readinessErrors.isEmpty()) { readinessErrors.joinToString("; ") }

        val provenance = PestControlTierOneMonsterTronGameAdapter.provenance(
            assignment,
            vectorIdentity,
            executionCommit,
        )

        val seats = if (assignment.pestSeat == PestSeat.SEAT_ZERO) {
            listOf(
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
                "mehanske Monster Tron" to PestControlTierOneMonsterTronReadiness.mainDeck(),
            )
        } else {
            listOf(
                "mehanske Monster Tron" to PestControlTierOneMonsterTronReadiness.mainDeck(),
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            )
        }
        val startingPlayerIndex = seats.indexOfFirst { (name) ->
            (assignment.startingDeck == MonsterTronStartingDeck.PEST_CONTROL &&
                name.startsWith("Pest")) ||
                (assignment.startingDeck == MonsterTronStartingDeck.MONSTER_TRON &&
                    name.startsWith("mehanske"))
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
        return MonsterTronAuthorizedOfficialGame(provenance, environment)
    }
}

enum class MonsterTronCoordinatorDisposition { VALIDATED, REJECTED }
enum class MonsterTronCoordinatorEventType {
    ATTEMPT_DURABLY_RECORDED,
    INITIALIZATION_ENTERED,
    RECORD_DURABLY_WRITTEN,
    REJECTED,
}

data class MonsterTronCoordinatorEvent(
    val gameNumber: Int,
    val type: MonsterTronCoordinatorEventType,
)

data class MonsterTronSmokeAttempt(val gameNumber: Int, val seed: Long)

internal class MonsterTronDurableAttempts private constructor(
    private val directory: Path,
    private val assignments: List<MonsterTronSmokeAssignment>,
) {
    private enum class Stage { READY, ATTEMPT, INITIALIZATION_ENTRY, TERMINAL }

    private var stage = Stage.READY
    private var nextGame = 1
    private val ledger = mutableListOf<MonsterTronCoordinatorEvent>()

    @Synchronized
    fun events(): List<MonsterTronCoordinatorEvent> = ledger.toList()

    @Synchronized
    fun recordAttempt(game: Int) = transition {
        requireStage(game, Stage.READY)
        val assignment = assignments[game - 1]
        persist(
            "game-$game.attempt",
            (
                "game=$game\nseed=${assignment.seed}\nseedHex=${assignment.seedHex}\n" +
                    "pestSeat=${assignment.pestSeat.name}\n" +
                    "startingDeck=${assignment.startingDeck.name}\n"
                ).toByteArray(Charsets.UTF_8)
        )
        ledger += MonsterTronCoordinatorEvent(
            game,
            MonsterTronCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
        )
        stage = Stage.ATTEMPT
    }

    @Synchronized
    fun recordInitializationEntry(game: Int) = transition {
        requireStage(game, Stage.ATTEMPT)
        persist("game-$game.initialization-entry", "game=$game\n".toByteArray())
        ledger += MonsterTronCoordinatorEvent(
            game,
            MonsterTronCoordinatorEventType.INITIALIZATION_ENTERED,
        )
        stage = Stage.INITIALIZATION_ENTRY
    }

    @Synchronized
    fun recordResult(game: Int, raw: ByteArray) = transition {
        requireStage(game, Stage.INITIALIZATION_ENTRY)
        require(raw.isNotEmpty())
        val snapshot = raw.copyOf()
        persist("game-$game.raw", snapshot)
        persist(
            "game-$game.record",
            "game=$game\nrawSha256=${monsterTronDigest(snapshot)}\n".toByteArray()
        )
        ledger += MonsterTronCoordinatorEvent(
            game,
            MonsterTronCoordinatorEventType.RECORD_DURABLY_WRITTEN,
        )
        nextGame++
        stage = if (nextGame > PEST_MONSTER_TRON_SMOKE_GAMES) Stage.TERMINAL else Stage.READY
    }

    @Synchronized
    fun reject(game: Int, reasonCode: String, failedRaw: ByteArray? = null) = transition {
        check(stage != Stage.TERMINAL && game == nextGame)
        require(reasonCode.matches(Regex("[A-Z][A-Z0-9_]{0,127}")))
        val snapshot = failedRaw?.copyOf()
        if (snapshot != null) {
            require(snapshot.isNotEmpty())
            persist("game-$game.failed.raw", snapshot)
        }
        persist(
            "rejected.txt",
            (
                "game=$game\nreason=$reasonCode\n" +
                    "failedRawSha256=${snapshot?.let(::monsterTronDigest) ?: ""}\n"
                ).toByteArray()
        )
        ledger += MonsterTronCoordinatorEvent(game, MonsterTronCoordinatorEventType.REJECTED)
        stage = Stage.TERMINAL
    }

    private fun requireStage(game: Int, expected: Stage) {
        check(stage == expected && game == nextGame && game in 1..PEST_MONSTER_TRON_SMOKE_GAMES)
    }

    private inline fun transition(operation: () -> Unit) {
        try {
            operation()
        } catch (failure: Exception) {
            stage = Stage.TERMINAL
            throw failure
        }
    }

    private fun persist(name: String, bytes: ByteArray) =
        writeMonsterTronEvidence(directory.resolve(name), bytes)

    companion object {
        fun createNew(
            evidenceRoot: Path,
            assignments: List<MonsterTronSmokeAssignment>,
            executionSourceCommit: String,
        ): MonsterTronDurableAttempts {
            require(executionSourceCommit.isMonsterTronLowerHex(40))
            require(executionSourceCommit != "0".repeat(40))
            val snapshot = assignments.toList()
            require(snapshot.size == PEST_MONSTER_TRON_SMOKE_GAMES)
            require(snapshot.map { it.gameNumber } == (1..PEST_MONSTER_TRON_SMOKE_GAMES).toList())
            require(snapshot.map { it.seed }.distinct().size == PEST_MONSTER_TRON_SMOKE_GAMES)
            require(snapshot.none { it.seed == 0L })
            snapshot.zip(PestControlTierOneMonsterTronSmokeHarness.cellTemplate()).forEach {
                    (row, cell) ->
                require(row.seedHex == monsterTronSeedHex(row.seed))
                require(row.gameNumber == cell.gameNumber)
                require(row.pestSeat == cell.pestSeat)
                require(row.startingDeck == cell.startingDeck)
                require(row.monsterTronSeat != row.pestSeat)
            }

            val parent = evidenceRoot.toRealPath()
            require(Files.isDirectory(parent))
            val directory = parent.resolve(PEST_MONSTER_TRON_SMOKE_BLOCK_ID)
            Files.createDirectory(directory)
            forceMonsterTronDirectory(parent)
            val vectorHash = monsterTronDigest(
                snapshot.joinToString("\n", postfix = "\n") { it.seed.toString() }
                    .toByteArray(Charsets.UTF_8)
            )
            writeMonsterTronEvidence(
                directory.resolve("claim.txt"),
                (
                    "schema=monster-tron-durable-attempts-v1\n" +
                        "blockId=$PEST_MONSTER_TRON_SMOKE_BLOCK_ID\n" +
                        "executionSourceCommit=$executionSourceCommit\n" +
                        "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER\n" +
                        "orderedVectorSha256=$vectorHash\n" +
                        "expectedGames=$PEST_MONSTER_TRON_SMOKE_GAMES\n"
                    ).toByteArray()
            )
            return MonsterTronDurableAttempts(directory, snapshot)
        }
    }
}

data class MonsterTronAuthorizedExecutionOutcome(
    val disposition: MonsterTronCoordinatorDisposition,
    val attempts: List<MonsterTronSmokeAttempt>,
    val initializedGames: List<Int>,
    val recordedGames: List<Int>,
    val perGameRaw: List<ByteArray>,
    val failure: String? = null,
    val failedGameRaw: ByteArray? = null,
)

class PestControlTierOneMonsterTronAuthorizedExecutionCoordinator(
    private val assignments: List<MonsterTronSmokeAssignment>,
    private val vectorIdentity: MonsterTronSmokeVectorIdentity,
    private val persistAttemptBeforeInitialization: (MonsterTronSmokeAttempt) -> Unit,
    private val persistInitializationEntry: (MonsterTronSmokeAssignment) -> Unit,
    private val persistCompletedGame: (MonsterTronSmokeAssignment, ByteArray) -> Unit,
) {
    fun execute(
        runGame: (MonsterTronSmokeAssignment) -> ByteArray,
    ): MonsterTronAuthorizedExecutionOutcome {
        val authorization = PestControlTierOneMonsterTronExecutionAuthorization.inspect()
        require(authorization.green && authorization.executionAuthorized)
        validateAssignments()

        val attempts = mutableListOf<MonsterTronSmokeAttempt>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val raws = mutableListOf<ByteArray>()
        return try {
            assignments.forEach { assignment ->
                val attempt = MonsterTronSmokeAttempt(assignment.gameNumber, assignment.seed)
                persistAttemptBeforeInitialization(attempt)
                attempts += attempt

                persistInitializationEntry(assignment)
                initialized += assignment.gameNumber

                val raw = runGame(assignment)
                require(raw.isNotEmpty())
                persistCompletedGame(assignment, raw)
                recorded += assignment.gameNumber
                raws += raw.copyOf()
            }
            check(attempts.map { it.gameNumber } == (1..PEST_MONSTER_TRON_SMOKE_GAMES).toList())
            check(initialized == attempts.map { it.gameNumber })
            check(recorded == initialized)
            MonsterTronAuthorizedExecutionOutcome(
                disposition = MonsterTronCoordinatorDisposition.VALIDATED,
                attempts = attempts.toList(),
                initializedGames = initialized.toList(),
                recordedGames = recorded.toList(),
                perGameRaw = raws.map(ByteArray::copyOf),
            )
        } catch (failure: Exception) {
            MonsterTronAuthorizedExecutionOutcome(
                disposition = MonsterTronCoordinatorDisposition.REJECTED,
                attempts = attempts.toList(),
                initializedGames = initialized.toList(),
                recordedGames = recorded.toList(),
                perGameRaw = raws.map(ByteArray::copyOf),
                failure = failure.message ?: failure::class.simpleName ?: "unknown execution failure",
                failedGameRaw = (failure as? MonsterTronGameplayFailure)?.raw?.let {
                    PestControlTierOneMonsterTronProductionDriver.encode(it)
                },
            )
        }
    }

    private fun validateAssignments() {
        require(assignments.size == PEST_MONSTER_TRON_SMOKE_GAMES)
        require(assignments.map { it.gameNumber } == (1..PEST_MONSTER_TRON_SMOKE_GAMES).toList())
        require(assignments.map { it.seed }.distinct().size == PEST_MONSTER_TRON_SMOKE_GAMES)
        require(assignments.none { it.seed == 0L })
        require(vectorIdentity.orderedVectorSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256)
        assignments.zip(PestControlTierOneMonsterTronSmokeHarness.cellTemplate()).forEach {
                (assignment, cell) ->
            require(assignment.gameNumber == cell.gameNumber)
            require(assignment.pestSeat == cell.pestSeat)
            require(assignment.startingDeck == cell.startingDeck)
            require(assignment.seedHex == monsterTronSeedHex(assignment.seed))
        }
    }
}

@Serializable
data class MonsterTronOfficialActionTrace(
    val sequence: Int,
    val turn: Int,
    val actingPlayerId: EntityId,
    val pendingDecisionType: String?,
    val selectedAction: JsonElement,
    val emittedEvents: List<JsonElement>,
    val accepted: Boolean,
    val rejectionReason: String? = null,
    val executionError: String? = null,
)

@Serializable
data class MonsterTronOfficialTerminal(
    val gameOver: Boolean,
    val winnerId: EntityId?,
    val turn: Int,
)

@Serializable
data class MonsterTronOfficialRawGame(
    val provenance: MonsterTronSmokeProvenance,
    val actions: List<MonsterTronOfficialActionTrace>,
    val mulliganActionCount: Int,
    val terminal: MonsterTronOfficialTerminal?,
    val failure: String? = null,
)

/** A rejected execution retains its observed actions and state without manufacturing an outcome. */
class MonsterTronGameplayFailure(
    val raw: MonsterTronOfficialRawGame,
    cause: Exception,
) : IllegalStateException(raw.failure, cause)

object PestControlTierOneMonsterTronProductionDriver {
    internal fun profileFor(provenance: MonsterTronSmokeProvenance, playerIndex: Int): AiProfile {
        require(playerIndex in 0..1)
        return if (playerIndex == provenance.monsterTronSeat.index) {
            PestMonsterTronPolicy.profile
        } else {
            AiProfile.PRODUCTION_CANDIDATE_EXPIRING
        }
    }

    fun drive(
        registry: CardRegistry,
        officialGame: MonsterTronAuthorizedOfficialGame,
        maxActions: Int = 12_000,
        maxTurns: Int = 60,
        maxActionsPerTurn: Int = 500,
        evidenceSink: (MonsterTronActionEvidencePhase, MonsterTronOfficialActionTrace) -> Unit = { _, _ -> },
    ): MonsterTronOfficialRawGame {
        val environment = officialGame.environment
        val traces = mutableListOf<MonsterTronOfficialActionTrace>()
        var mulliganActionCount = 0
        fun snapshot(failure: String? = null) = MonsterTronOfficialRawGame(
            provenance = officialGame.provenance,
            actions = traces.toList(),
            mulliganActionCount = mulliganActionCount,
            terminal = MonsterTronOfficialTerminal(
                gameOver = environment.state.gameOver,
                winnerId = environment.state.winnerId,
                turn = environment.state.turnNumber,
            ),
            failure = failure,
        )

        try {
            val controllers = environment.playerIds.associateWith { player ->
                EngineAiPlayerController(registry, player, gameStateProvider = { environment.state })
            }

            driveMulligans(environment, environment.playerIds, controllers) { action ->
                try {
                    submitExactlyOne(environment, action, traces, evidenceSink = evidenceSink)
                } finally {
                    mulliganActionCount = traces.size
                }
            }

            val agents = environment.playerIds.associateWith { player ->
                AIPlayer.create(
                    registry,
                    player,
                    profileFor(officialGame.provenance, environment.playerIds.indexOf(player)),
                )
            }
            var lastTurn = environment.turnNumber
            var actionsThisTurn = 0
            while (!environment.isTerminal) {
                check(traces.size < maxActions) { "reached $maxActions actions" }
                check(environment.turnNumber <= maxTurns) { "reached turn ${environment.turnNumber}" }
                if (environment.turnNumber != lastTurn) {
                    lastTurn = environment.turnNumber
                    actionsThisTurn = 0
                }
                check(++actionsThisTurn <= maxActionsPerTurn) {
                    "more than $maxActionsPerTurn exact-one actions on turn $lastTurn"
                }

                val state = environment.state
                val decision = state.pendingDecision
                val acting = decision?.playerId ?: state.priorityPlayerId
                    ?: error("no pending decision or priority holder")
                val agent = agents.getValue(acting)
                val action = if (decision != null) {
                    SubmitDecision(acting, agent.respondToDecision(state, decision))
                } else {
                    agent.chooseAction(state)
                }
                submitExactlyOne(environment, action, traces, evidenceSink = evidenceSink)
            }

            val raw = snapshot()
            check(raw.terminal?.gameOver == true)
            check(raw.actions.all { it.accepted && it.rejectionReason == null })
            return raw
        } catch (failure: Exception) {
            throw MonsterTronGameplayFailure(
                snapshot(failure.message ?: failure::class.simpleName ?: "unknown execution failure"),
                failure,
            )
        }
    }

    fun encode(raw: MonsterTronOfficialRawGame): ByteArray =
        (PROTOCOL_JSON.encodeToString(raw) + "\n").toByteArray()

    private fun driveMulligans(
        environment: GameEnvironment,
        players: List<EntityId>,
        controllers: Map<EntityId, EngineAiPlayerController>,
        submit: (GameAction) -> Unit,
    ) {
        var transitions = 0
        for (player in environment.state.turnOrder) {
            val controller = controllers.getValue(player)
            while (true) {
                check(++transitions <= 30) { "London mulligan did not settle" }
                val current = environment.state
                val component = current.getEntity(player)
                    ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>()
                    ?: error("player lacks MulliganStateComponent")
                if (component.hasKept) break
                val hand = current.getHand(player)
                val keep = controller.decideMulligan(
                    MulliganInfo(
                        hand = hand,
                        mulliganCount = component.mulligansTaken,
                        cardsToPutOnBottom = component.cardsToBottom,
                        cards = monsterTronCardSummaries(current, hand),
                        isOnThePlay = current.turnOrder.first() == player,
                    )
                )
                submit(if (keep) KeepHand(player) else TakeMulligan(player))
            }
        }
        for (player in players) {
            val current = environment.state
            val component = current.getEntity(player)
                ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>()
                ?: continue
            if (component.cardsToBottom == 0) continue
            val hand = current.getHand(player)
            val bottom = controllers.getValue(player).chooseBottomCards(
                BottomCardsInfo(hand, component.cardsToBottom, monsterTronCardSummaries(current, hand))
            )
            submit(BottomCards(player, bottom))
        }
    }

    internal fun submitExactlyOne(
        environment: GameEnvironment,
        action: GameAction,
        traces: MutableList<MonsterTronOfficialActionTrace>,
        evidenceSink: (MonsterTronActionEvidencePhase, MonsterTronOfficialActionTrace) -> Unit = { _, _ -> },
        submit: (GameAction) -> ExactlyOneSubmissionResult = environment::stepExactlyOne,
    ) {
        val before = environment.state
        val acting = action.playerId
        val intent = MonsterTronOfficialActionTrace(
            sequence = traces.size + 1,
            turn = before.turnNumber,
            actingPlayerId = acting,
            pendingDecisionType = before.pendingDecision?.let { it::class.simpleName },
            selectedAction = PROTOCOL_JSON.encodeToJsonElement(GameAction.serializer(), action),
            emittedEvents = emptyList(),
            accepted = false,
        )
        // A durable INTENT is not proof of submission or acceptance. It survives a process exit
        // inside the engine; RESULT is recorded separately only after the call returns or throws.
        evidenceSink(MonsterTronActionEvidencePhase.INTENT, intent)
        val result = try {
            submit(action)
        } catch (failure: Exception) {
            traces += MonsterTronOfficialActionTrace(
                sequence = traces.size + 1,
                turn = before.turnNumber,
                actingPlayerId = acting,
                pendingDecisionType = before.pendingDecision?.let { it::class.simpleName },
                selectedAction = PROTOCOL_JSON.encodeToJsonElement(GameAction.serializer(), action),
                emittedEvents = emptyList(),
                accepted = false,
                executionError = failure.message ?: failure::class.simpleName ?: "unknown engine failure",
            )
            evidenceSink(MonsterTronActionEvidencePhase.RESULT, traces.last())
            throw failure
        }
        val rejected = result as? ExactlyOneSubmissionResult.Rejected
        traces += MonsterTronOfficialActionTrace(
            sequence = traces.size + 1,
            turn = before.turnNumber,
            actingPlayerId = acting,
            pendingDecisionType = before.pendingDecision?.let { it::class.simpleName },
            selectedAction = PROTOCOL_JSON.encodeToJsonElement(GameAction.serializer(), action),
            emittedEvents = environment.lastStepEvents.map {
                PROTOCOL_JSON.encodeToJsonElement(
                    com.wingedsheep.engine.core.GameEvent.serializer(),
                    it,
                )
            },
            accepted = rejected == null,
            rejectionReason = rejected?.reason,
        )
        evidenceSink(MonsterTronActionEvidencePhase.RESULT, traces.last())
        check(rejected == null) { "rejected official action: ${rejected?.reason}" }
    }
}

enum class MonsterTronActionEvidencePhase { INTENT, RESULT }

private fun monsterTronCardSummaries(
    state: GameState,
    ids: List<EntityId>,
): Map<EntityId, CardSummary> = ids.associateWith { id ->
    val card = state.getEntity(id)?.get<CardComponent>()
    CardSummary(
        name = card?.name ?: id.value,
        manaCost = card?.manaCost?.toString(),
        typeLine = card?.typeLine?.toString(),
        power = card?.baseStats?.basePower,
        toughness = card?.baseStats?.baseToughness,
        oracleText = card?.oracleText,
    )
}

private fun String.isMonsterTronLowerHex(length: Int): Boolean =
    this.length == length && all { it in '0'..'9' || it in 'a'..'f' }

internal fun monsterTronDigest(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).monsterTronHex()

private fun ByteArray.monsterTronHex(): String =
    joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

private fun writeMonsterTronEvidence(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    forceMonsterTronDirectory(path.parent)
}

private fun forceMonsterTronDirectory(directory: Path) {
    FileChannel.open(directory, READ).use { it.force(true) }
}
