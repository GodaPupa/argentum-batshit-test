package com.wingedsheep.gym.pest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

internal data class PestCurrentPairC2ReplayIdentity(
    val sourceCommit: String,
    val sourceTree: String,
    val pestMainSha256: String,
    val monsterMainSha256: String,
    val observationAdapterBlob: String,
    val actorActionMenuBlob: String,
    val strategistBlob: String,
    val decisionResponderBlob: String,
    val pestProfileBlob: String,
    val monsterPolicyBlob: String,
    val monsterAdvisorBlob: String,
)

internal data class PestCurrentPairC2ReplayIntent(
    val sequence: Int,
    val actor: String,
    val actionJson: String,
    val beforeStateSha256: String,
    val legalActionSha256: String,
)

internal data class PestCurrentPairC2ReplayResult(
    val sequence: Int,
    val actionJson: String,
    val eventJson: List<String>,
    val afterStateSha256: String,
    val terminal: Boolean,
)

internal data class PestCurrentPairC2ReplayInspection(
    val records: Int,
    val terminal: Boolean,
    val journalSha256: String,
)

/**
 * Non-official C2 source/replay journal.
 *
 * It stores caller-supplied deterministic action/event evidence only. It has no GameEnvironment,
 * initializer, seed allocation, game-driving, or outcome-discovery capability.
 */
internal class PestCurrentPairC2ReplayJournal private constructor(
    private val directory: Path,
    private val channel: FileChannel,
    private val identity: PestCurrentPairC2ReplayIdentity,
) : AutoCloseable {
    private var nextSequence = 1
    private var pending: PestCurrentPairC2ReplayIntent? = null
    private var terminalSeen = false
    private var closed = false
    private val digest = MessageDigest.getInstance("SHA-256")

    @Synchronized
    fun recordIntent(intent: PestCurrentPairC2ReplayIntent) {
        check(!closed && !terminalSeen && pending == null)
        require(intent.sequence == nextSequence)
        require(intent.actor.isNotBlank())
        requireSha(intent.beforeStateSha256)
        requireSha(intent.legalActionSha256)
        requireCanonicalJson(intent.actionJson)
        append("INTENT", buildJsonObject {
            put("sequence", intent.sequence)
            put("actor", intent.actor)
            put("action", intent.actionJson)
            put("before_state_sha256", intent.beforeStateSha256)
            put("legal_action_sha256", intent.legalActionSha256)
        })
        pending = intent
    }

    @Synchronized
    fun recordResult(result: PestCurrentPairC2ReplayResult) {
        check(!closed && !terminalSeen)
        val intent = checkNotNull(pending) { "RESULT requires a durable INTENT" }
        require(result.sequence == intent.sequence)
        require(result.actionJson == intent.actionJson) { "RESULT action differs from INTENT" }
        requireCanonicalJson(result.actionJson)
        result.eventJson.forEach(::requireCanonicalJson)
        requireSha(result.afterStateSha256)
        append("RESULT", buildJsonObject {
            put("sequence", result.sequence)
            put("action", result.actionJson)
            put("events", buildJsonArray { result.eventJson.forEach { add(JsonPrimitive(it)) } })
            put("after_state_sha256", result.afterStateSha256)
            put("terminal", result.terminal)
        })
        pending = null
        terminalSeen = result.terminal
        nextSequence++
    }

    @Synchronized
    fun finish(): Path {
        check(!closed)
        check(pending == null) { "incomplete INTENT/RESULT pair" }
        check(terminalSeen) { "fresh replay qualification must terminate explicitly" }
        channel.force(true)
        val journalPath = directory.resolve("journal.txt")
        val raw = Files.readAllBytes(journalPath)
        val observed = sha256(raw)
        check(observed == digest.digest().hex())
        val summary = directory.resolve("summary.json")
        writeNew(summary, (buildJsonObject {
            put("schema", "pest-current-pair-c2-source-replay-summary-v1")
            put("authority", "NON_OFFICIAL_SOURCE_REPLAY_QUALIFICATION_ONLY")
            put("records", nextSequence - 1)
            put("terminal", true)
            put("journal_sha256", observed)
            put("official_counters_delta", 0)
        }.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
        forceDirectory(directory)
        return summary
    }

    override fun close() {
        if (!closed) {
            closed = true
            channel.force(true)
            channel.close()
        }
    }

    private fun append(kind: String, payload: JsonObject) {
        val line = "$kind|$payload\n".toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.wrap(line)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
        digest.update(line)
    }

    companion object {
        fun create(root: Path, runId: String, identity: PestCurrentPairC2ReplayIdentity): PestCurrentPairC2ReplayJournal {
            require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
            require(Files.isDirectory(root, NOFOLLOW_LINKS))
            require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")))
            validateIdentity(identity)
            val directory = root.resolve(runId)
            Files.createDirectory(directory)
            forceDirectory(root)
            writeNew(directory.resolve("package.json"), (identityJson(identity).toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            val channel = FileChannel.open(directory.resolve("journal.txt"), CREATE_NEW, WRITE)
            channel.force(true)
            forceDirectory(directory)
            return PestCurrentPairC2ReplayJournal(directory, channel, identity)
        }

        fun replay(directory: Path, expectedIdentity: PestCurrentPairC2ReplayIdentity): PestCurrentPairC2ReplayInspection {
            require(directory.toRealPath() == directory)
            validateIdentity(expectedIdentity)
            val packageJson = Json.parseToJsonElement(Files.readString(directory.resolve("package.json"))).jsonObject
            require(packageJson == identityJson(expectedIdentity)) { "current-pair package identity mismatch" }
            val journalRaw = Files.readAllBytes(directory.resolve("journal.txt"))
            val lines = journalRaw.toString(StandardCharsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.toList()
            require(lines.size % 2 == 0 && lines.isNotEmpty()) { "incomplete journal" }
            var expectedSequence = 1
            var terminal = false
            for (index in lines.indices step 2) {
                val (ik, ip) = split(lines[index])
                val (rk, rp) = split(lines[index + 1])
                require(ik == "INTENT" && rk == "RESULT")
                val intent = Json.parseToJsonElement(ip).jsonObject
                val result = Json.parseToJsonElement(rp).jsonObject
                require(intent.getValue("sequence").jsonPrimitive.content.toInt() == expectedSequence)
                require(result.getValue("sequence").jsonPrimitive.content.toInt() == expectedSequence)
                require(result.getValue("action").jsonPrimitive.content == intent.getValue("action").jsonPrimitive.content)
                requireCanonicalJson(intent.getValue("action").jsonPrimitive.content)
                result.getValue("events").jsonArray.forEach { requireCanonicalJson(it.jsonPrimitive.content) }
                requireSha(intent.getValue("before_state_sha256").jsonPrimitive.content)
                requireSha(intent.getValue("legal_action_sha256").jsonPrimitive.content)
                requireSha(result.getValue("after_state_sha256").jsonPrimitive.content)
                val isTerminal = result.getValue("terminal").jsonPrimitive.content.toBooleanStrict()
                require(!terminal)
                terminal = isTerminal
                if (terminal) require(index + 2 == lines.size) { "terminal result must be final" }
                expectedSequence++
            }
            require(terminal) { "replay did not reach declared terminal record" }
            val observedDigest = sha256(journalRaw)
            val summary = Json.parseToJsonElement(Files.readString(directory.resolve("summary.json"))).jsonObject
            require(summary.getValue("schema").jsonPrimitive.content == "pest-current-pair-c2-source-replay-summary-v1")
            require(summary.getValue("authority").jsonPrimitive.content == "NON_OFFICIAL_SOURCE_REPLAY_QUALIFICATION_ONLY")
            require(summary.getValue("records").jsonPrimitive.content.toInt() == expectedSequence - 1)
            require(summary.getValue("terminal").jsonPrimitive.content.toBooleanStrict())
            require(summary.getValue("journal_sha256").jsonPrimitive.content == observedDigest)
            require(summary.getValue("official_counters_delta").jsonPrimitive.content.toInt() == 0)
            return PestCurrentPairC2ReplayInspection(expectedSequence - 1, true, observedDigest)
        }

        private fun split(line: String): Pair<String, String> {
            val at = line.indexOf('|')
            require(at > 0 && at < line.lastIndex)
            return line.substring(0, at) to line.substring(at + 1)
        }

        private fun identityJson(identity: PestCurrentPairC2ReplayIdentity): JsonObject = buildJsonObject {
            put("schema", "pest-current-pair-c2-source-replay-package-v1")
            put("authority", "NON_OFFICIAL_SOURCE_REPLAY_QUALIFICATION_ONLY")
            put("source_commit", identity.sourceCommit)
            put("source_tree", identity.sourceTree)
            put("pest_main_sha256", identity.pestMainSha256)
            put("monster_main_sha256", identity.monsterMainSha256)
            put("observation_adapter_blob", identity.observationAdapterBlob)
            put("actor_action_menu_blob", identity.actorActionMenuBlob)
            put("strategist_blob", identity.strategistBlob)
            put("decision_responder_blob", identity.decisionResponderBlob)
            put("pest_profile_blob", identity.pestProfileBlob)
            put("monster_policy_blob", identity.monsterPolicyBlob)
            put("monster_advisor_blob", identity.monsterAdvisorBlob)
            put("official_counters_delta", 0)
        }

        private fun validateIdentity(identity: PestCurrentPairC2ReplayIdentity) {
            requireHex(identity.sourceCommit, 40)
            requireHex(identity.sourceTree, 40)
            requireSha(identity.pestMainSha256)
            requireSha(identity.monsterMainSha256)
            listOf(
                identity.observationAdapterBlob, identity.actorActionMenuBlob, identity.strategistBlob,
                identity.decisionResponderBlob, identity.pestProfileBlob, identity.monsterPolicyBlob,
                identity.monsterAdvisorBlob,
            ).forEach { requireHex(it, 40) }
        }

        private fun requireCanonicalJson(raw: String) {
            require(raw.isNotBlank())
            Json.parseToJsonElement(raw)
        }

        private fun requireSha(raw: String) = requireHex(raw, 64)

        private fun requireHex(raw: String, size: Int) {
            require(raw.length == size && raw.all { it in '0'..'9' || it in 'a'..'f' })
        }

        private fun writeNew(path: Path, bytes: ByteArray) {
            FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        }

        private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }

        private fun sha256(raw: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(raw).hex()

        private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
