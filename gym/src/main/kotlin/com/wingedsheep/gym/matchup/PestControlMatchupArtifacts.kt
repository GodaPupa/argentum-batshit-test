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
