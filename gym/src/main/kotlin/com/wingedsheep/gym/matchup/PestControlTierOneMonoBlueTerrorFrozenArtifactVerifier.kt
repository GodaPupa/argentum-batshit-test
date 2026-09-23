package com.wingedsheep.gym.matchup

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

const val PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256 =
    "ca508c842886fff2af7db1c966fbedbb8ae801796c5043e26b22def056c724ea"
const val PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256 =
    "7950f90c94e8b92cd04ffcbfe31ba4682351d45b5f19b05ef6d606410bf92129"
const val PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256 =
    "4a68f7a9bdbcbf7fd2b58caca86da6cd0c9868a2f8726eee40ee251e2758b5e3"
const val PEST_MONO_BLUE_TERROR_FROZEN_QUARANTINE_SHA256 =
    "6f9e1dddf46eb005a1404e2a7319c85ed431b368a6787af2b9bfe2383263d3f0"
const val PEST_MONO_BLUE_TERROR_FROZEN_CHECKSUMS_SHA256 =
    "051eb0a0e0e42c3a1ac9fbac8a0663c9d3843aae8886b106f30c0e0679c4ac75"
const val PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256 =
    "bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961"

internal const val TERROR_FROZEN_MAX_ARCHIVE_BYTES = 65_536
internal const val TERROR_FROZEN_MAX_EXPANDED_BYTES = 32_768

internal fun terrorFrozenMemberPins(): Map<String, String> = linkedMapOf(
    "ordered-seeds.txt" to PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
    "assignments.csv" to PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
    "freeze-manifest.json" to PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256,
    "quarantined-vector.json" to PEST_MONO_BLUE_TERROR_FROZEN_QUARANTINE_SHA256,
    "artifacts.sha256" to PEST_MONO_BLUE_TERROR_FROZEN_CHECKSUMS_SHA256,
)

data class MonoBlueTerrorFrozenArtifactInspection(
    val errors: List<String>,
    val archiveSha256: String,
    val memberSha256: Map<String, String>,
) {
    val verified: Boolean get() = errors.isEmpty()
    val executionAuthorized: Boolean get() = false
}

/** Read-only byte verification. Returns digests, never seeds, assignments, or a game session. */
object PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier {
    fun inspect(archive: ByteArray): MonoBlueTerrorFrozenArtifactInspection =
        inspectTerrorPinnedArchive(
            archive,
            PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256,
            terrorFrozenMemberPins(),
        )
}

/** Internal pure ZIP primitive; synthetic tests supply their own nonexperimental digest pins. */
internal fun inspectTerrorPinnedArchive(
    archive: ByteArray,
    expectedArchiveSha256: String,
    expectedMembers: Map<String, String>,
): MonoBlueTerrorFrozenArtifactInspection {
    if (archive.isEmpty() || archive.size > TERROR_FROZEN_MAX_ARCHIVE_BYTES) {
        return MonoBlueTerrorFrozenArtifactInspection(
            listOf("archive size outside accepted bounds"), "", emptyMap(),
        )
    }
    val snapshot = archive.copyOf()
    val archiveHash = terrorFrozenDigest(snapshot)
    if (archiveHash != expectedArchiveSha256) {
        return MonoBlueTerrorFrozenArtifactInspection(
            listOf("archive hash mismatch"), archiveHash, emptyMap(),
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
                    if (expandedBytes > TERROR_FROZEN_MAX_EXPANDED_BYTES) {
                        throw IOException("expanded byte limit exceeded")
                    }
                    digest.update(buffer, 0, count)
                }
                val memberHash = digest.digest().terrorHex()
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
    return MonoBlueTerrorFrozenArtifactInspection(
        errors.distinct(), archiveHash, observed.toMap(),
    )
}

internal fun terrorFrozenDigest(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).terrorHex()

private fun ByteArray.terrorHex(): String =
    joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
