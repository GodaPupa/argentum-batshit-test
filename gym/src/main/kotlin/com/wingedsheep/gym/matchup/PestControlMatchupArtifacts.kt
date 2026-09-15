package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

@Serializable
data class MatchupArtifactManifest(
    val protocolId: String,
    val schema: String,
    val sourceCommit: String,
    val pestControlMainSha256: String = PEST_CONTROL_V10_HASH,
    val pestControlSideboardSha256: String = PEST_CONTROL_V10_SIDEBOARD_HASH,
    val pestControlComplete75Sha256: String = PEST_CONTROL_V10_75_HASH,
    val monoRedMainSha256: String = SOTERX_MONO_RED_MAIN_HASH,
    val monoRedSideboardSha256: String = SOTERX_MONO_RED_SIDEBOARD_HASH,
    val monoRedComplete75Sha256: String = SOTERX_MONO_RED_75_HASH,
    val rawJsonSha256: String,
    val compressedSha256: String,
    val reportSha256: String,
    val compression: String = "gzip/deflate; mtime=0; xfl=0; os=255",
)

data class MatchupArtifactBundle(
    val rawJson: ByteArray,
    val compressed: ByteArray,
    val report: ByteArray,
    val manifest: ByteArray,
)

object PestControlMatchupArtifactCodec {
    fun build(game: MatchupRawGame): MatchupArtifactBundle {
        val raw = (PROTOCOL_JSON.encodeToString(game) + "\n").toByteArray()
        val compressed = deterministicGzip(raw)
        val report = renderReport(raw).toByteArray()
        val manifestRecord = MatchupArtifactManifest(
            protocolId = game.provenance.protocolId,
            schema = game.provenance.schema,
            sourceCommit = game.provenance.sourceCommit,
            pestControlMainSha256 = game.provenance.pestControlMainSha256,
            pestControlSideboardSha256 = game.provenance.pestControlSideboardSha256,
            pestControlComplete75Sha256 = game.provenance.pestControlComplete75Sha256,
            monoRedMainSha256 = game.provenance.monoRedMainSha256,
            monoRedSideboardSha256 = game.provenance.monoRedSideboardSha256,
            monoRedComplete75Sha256 = game.provenance.monoRedComplete75Sha256,
            rawJsonSha256 = sha256(raw),
            compressedSha256 = sha256(compressed),
            reportSha256 = sha256(report),
        )
        val manifest = (PROTOCOL_JSON.encodeToString(manifestRecord) + "\n").toByteArray()
        return MatchupArtifactBundle(raw, compressed, report, manifest)
    }

    fun verify(bundle: MatchupArtifactBundle): List<String> {
        val errors = mutableListOf<String>()
        val manifest = runCatching {
            PROTOCOL_JSON.decodeFromString<MatchupArtifactManifest>(bundle.manifest.decodeToString())
        }.getOrElse { return listOf("manifest is not valid canonical JSON: ${it.message}") }
        if (sha256(bundle.rawJson) != manifest.rawJsonSha256) errors += "raw JSON hash mismatch"
        if (sha256(bundle.compressed) != manifest.compressedSha256) errors += "compressed hash mismatch"
        if (sha256(bundle.report) != manifest.reportSha256) errors += "report hash mismatch"
        if (!bundle.compressed.contentEquals(deterministicGzip(bundle.rawJson))) errors += "compressed bytes are not canonical"
        if (!bundle.report.contentEquals(renderReport(bundle.rawJson).toByteArray())) errors += "report is not derived from raw JSON"
        val game = runCatching { PROTOCOL_JSON.decodeFromString<MatchupRawGame>(bundle.rawJson.decodeToString()) }
            .getOrElse { errors += "raw JSON cannot be decoded: ${it.message}"; null }
        if (game != null) {
            if (game.provenance.protocolId != manifest.protocolId) errors += "manifest protocol mismatch"
            if (game.provenance.sourceCommit != manifest.sourceCommit) errors += "manifest source commit mismatch"
            if (game.provenance.pestControlMainSha256 != PEST_CONTROL_V10_HASH) errors += "Pest maindeck identity mismatch"
            if (game.provenance.pestControlSideboardSha256 != PEST_CONTROL_V10_SIDEBOARD_HASH) errors += "Pest sideboard identity mismatch"
            if (game.provenance.pestControlComplete75Sha256 != PEST_CONTROL_V10_75_HASH) errors += "Pest complete-75 identity mismatch"
            if (game.provenance.monoRedMainSha256 != SOTERX_MONO_RED_MAIN_HASH) errors += "Mono Red maindeck identity mismatch"
            if (game.provenance.monoRedSideboardSha256 != SOTERX_MONO_RED_SIDEBOARD_HASH) errors += "Mono Red sideboard identity mismatch"
            if (game.provenance.monoRedComplete75Sha256 != SOTERX_MONO_RED_75_HASH) errors += "Mono Red complete-75 identity mismatch"
            if (game.provenance.pestControlMainSha256 != manifest.pestControlMainSha256) errors += "manifest Pest maindeck mismatch"
            if (game.provenance.pestControlSideboardSha256 != manifest.pestControlSideboardSha256) errors += "manifest Pest sideboard mismatch"
            if (game.provenance.pestControlComplete75Sha256 != manifest.pestControlComplete75Sha256) errors += "manifest Pest complete-75 mismatch"
            if (game.provenance.monoRedMainSha256 != manifest.monoRedMainSha256) errors += "manifest Mono Red maindeck mismatch"
            if (game.provenance.monoRedSideboardSha256 != manifest.monoRedSideboardSha256) errors += "manifest Mono Red sideboard mismatch"
            if (game.provenance.monoRedComplete75Sha256 != manifest.monoRedComplete75Sha256) errors += "manifest Mono Red complete-75 mismatch"
            if (game.provenance.matchResult != PREBOARD_MATCH_RESULT) errors += "invalid preboard match result"
            if (!game.fixtureIsNonexperimental || !game.excludedFromFutureSeedOverlapRegistry) {
                errors += "fixture is not excluded from experimental seed registries"
            }
        }
        return errors
    }

    fun renderReport(rawJson: ByteArray): String {
        val game = PROTOCOL_JSON.decodeFromString<MatchupRawGame>(rawJson.decodeToString())
        return buildString {
            appendLine("# Pest Control v1.0 vs SoterX Mono Red Madness — deterministic protocol fixture")
            appendLine()
            appendLine("- Protocol: `${game.provenance.protocolId}`")
            appendLine("- Schema: `${game.provenance.schema}`")
            appendLine("- Source commit: `${game.provenance.sourceCommit}`")
            appendLine("- Pest maindeck SHA-256: `${game.provenance.pestControlMainSha256}`")
            appendLine("- Pest sideboard SHA-256: `${game.provenance.pestControlSideboardSha256}`")
            appendLine("- Pest complete-75 SHA-256: `${game.provenance.pestControlComplete75Sha256}`")
            appendLine("- Mono Red maindeck SHA-256: `${game.provenance.monoRedMainSha256}`")
            appendLine("- Mono Red sideboard SHA-256: `${game.provenance.monoRedSideboardSha256}`")
            appendLine("- Mono Red complete-75 SHA-256: `${game.provenance.monoRedComplete75Sha256}`")
            appendLine("- Scope: ${game.provenance.scope}")
            appendLine("- Match result: `${game.provenance.matchResult}`")
            appendLine("- Fixture: `${game.fixtureId}` (nonexperimental; excluded from seed overlap)")
            appendLine("- Pest seat: ${game.provenance.pestSeat}; starts: ${game.provenance.startingDeck}")
            appendLine("- Recorded exact-one actions: ${game.priorityActions.size}")
            appendLine("- Mulligan records: ${game.mulligans.size}")
            appendLine("- Protocol defect: ${game.protocolDefect ?: "none"}")
            appendLine("- Terminal: ${game.terminal ?: "not reached by this fixture"}")
        }
    }

    /** RFC 1952 stream with an invariant header and raw DEFLATE body. */
    fun deterministicGzip(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val body = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) body.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        val crc = CRC32().apply { update(input) }.value.toInt()
        val trailer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(crc).putInt(input.size).array()
        return byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0, 0, 0, 0, 0, 0xff.toByte()) +
            body.toByteArray() + trailer
    }
}
