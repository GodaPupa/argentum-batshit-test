package com.wingedsheep.gym.ferocity

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

internal data class FerocityReplayFileIdentity(
    val path: Path,
    val size: Long,
    val sha256: String,
    val fileKey: Any?,
)

/** Only fixed file identities and a small claim are retained; journal payloads are never parsed here. */
internal data class FerocityReplayFileGuard(
    val claim: FerocityClaim,
    val files: List<FerocityReplayFileIdentity>,
) {
    fun verifyUnchanged() {
        require(files.map { hashFerocityReplayFile(it.path) } == files) {
            "Original claim, allocation or journal changed during fresh replay"
        }
    }
}

private fun hashFerocityReplayFile(path: Path): FerocityReplayFileIdentity {
    require(path.isAbsolute && path.normalize() == path && path.toRealPath() == path) {
        "Replay inputs must use exact canonical paths without substituted symlinks"
    }
    val before = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    require(before.isRegularFile) { "Replay input is not a regular file" }
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    val after = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    require(before.fileKey() == after.fileKey() && before.size() == after.size() &&
        before.lastModifiedTime() == after.lastModifiedTime() && path.toRealPath() == path) {
        "Replay input changed during streaming hash"
    }
    return FerocityReplayFileIdentity(path, after.size(), digest.digest().joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }, after.fileKey())
}

internal fun captureFerocityReplayFiles(root: Path, namespace: String, trialId: String): FerocityReplayFileGuard {
    val claimPath = FerocityTrialJournal.claimPath(root, namespace, trialId)
    require(Files.size(claimPath) in 1..(4L * 1024 * 1024)) { "Oversized replay claim" }
    val claimText = Files.readString(claimPath)
    val claim = FerocityJournalCodec.readClaim(claimText)
    require(claim.spec.namespace == namespace && claim.spec.trialId == trialId) { "Claim identity mismatch" }
    val allocationKey = FerocityJournalCodec.sha("${namespace.length}:$namespace${claim.spec.allocationId}")
    val allocationPath = root.toAbsolutePath().normalize().resolve("allocations/$allocationKey.claim")
    val paths = listOf(claimPath, allocationPath, FerocityTrialJournal.journalPath(root, namespace, trialId))
    val files = paths.map(::hashFerocityReplayFile)
    require(files.first().sha256 == FerocityJournalCodec.sha(claimText)) { "Claim changed before input capture" }
    // The sole authoritative strict journal reader independently verifies the reservation content,
    // record schemas and complete hash chain during FerocityTrialReplay.verify.
    return FerocityReplayFileGuard(claim, files)
}
