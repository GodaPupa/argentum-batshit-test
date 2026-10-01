package com.wingedsheep.gym.sphinx

import java.nio.file.Files
import java.nio.file.Path

/**
 * Trusted replay codec for synthetic runner fixtures. INITIALIZED and RESULT payloads must
 * be complete canonical replay envelopes (including state/RNG and relevant events), not
 * summaries or policy scores. An engine codec still needs its own exact-source qualification.
 */
internal interface SphinxFixtureReplayCodec<S> {
    fun restoreRecorded(envelope: String): S
    fun canonicalEnvelope(state: S): String
    fun replayRecordedAction(state: S, action: String): S
}

internal data class SphinxFixtureReplayReport(
    val verifiedActions: Int,
    val completeRecordedSequence: Boolean,
    val recordedStop: String?,
    val chainSha256: String,
)

/**
 * Read-only consumer of the fixture write-ahead journal. It restores the recorded envelope,
 * never calls an initializer or pilot, and never replays an intent without a durable RESULT.
 * A complete record sequence is NOT an admitted engine outcome or a complete Stage-E pilot.
 * No source, policy, official allocation, or gameplay authority is conferred by this component.
 */
internal object SphinxStageEFixtureReplay {
    fun <S> verify(
        directory: Path,
        identity: SphinxRunnerFixtureIdentity,
        trustedCodec: SphinxFixtureReplayCodec<S>,
    ): SphinxFixtureReplayReport {
        val before = SphinxStageEWriteAheadJournal.inspect(directory, identity)
        val claimBefore = Files.readAllBytes(directory.resolve("claim.txt"))
        val journalBefore = Files.readAllBytes(directory.resolve("journal.txt"))
        var verified = 0
        fun report(complete: Boolean = false, stop: String? = null) =
            SphinxFixtureReplayReport(verified, complete, stop, before.chainSha256)
        try {
            val entries = before.entries
            val initialized = entries.getOrNull(1)
            if (initialized?.kind != "INITIALIZED") return report()
            var state = trustedCodec.restoreRecorded(initialized.payload)
            require(trustedCodec.canonicalEnvelope(state) == initialized.payload) { "Initial envelope drift" }
            var index = 2
            while (index < entries.size) {
                val entry = entries[index]
                if (entry.kind == "STOP") {
                    require(index == entries.lastIndex && before.structurallyComplete)
                    return report(true, entry.payload)
                }
                require(entry.kind == "ACTION_INTENT")
                val result = entries.getOrNull(index + 1)
                if (result == null) return report() // Do not execute the unmatched action, even once.
                require(result.kind == "RESULT")
                state = trustedCodec.replayRecordedAction(state, entry.payload)
                require(trustedCodec.canonicalEnvelope(state) == result.payload) {
                    "Recorded result drift at action ${verified + 1}"
                }
                verified++
                index += 2
            }
            return report()
        } finally {
            // These checks also run after a decoder/executor exception or a mismatch.
            val after = SphinxStageEWriteAheadJournal.inspect(directory, identity)
            require(after == before &&
                Files.readAllBytes(directory.resolve("claim.txt")).contentEquals(claimBefore) &&
                Files.readAllBytes(directory.resolve("journal.txt")).contentEquals(journalBefore)) {
                "Original fixture evidence changed during read-only replay"
            }
        }
    }
}
