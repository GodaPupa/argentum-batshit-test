package com.wingedsheep.gym.pest

/**
 * Prospective deterministic partition geometry for a future reviewed Phase-B aggregate original.
 *
 * This object does not execute the raw controller, load accepted truth, create evidence, or grant
 * execution authority. It only maps the frozen global zero-based row ordinal to one fixed shard.
 */
internal object PestPhaseBShardGeometry {
    const val SHARD_COUNT = 32
    const val GLOBAL_ROWS = 34_912_840L
    const val GLOBAL_PLAN_SHA256 = "bcc577b1596c7b37b7f14f0c70d68fefafcf569de3b74f561a0b8317e6e1cc71"

    fun shardFor(globalOrdinal: Long): Int {
        require(globalOrdinal in 0 until GLOBAL_ROWS)
        return (globalOrdinal % SHARD_COUNT).toInt()
    }

    fun expectedRows(shardIndex: Int): Long {
        require(shardIndex in 0 until SHARD_COUNT)
        val base = GLOBAL_ROWS / SHARD_COUNT
        val remainder = (GLOBAL_ROWS % SHARD_COUNT).toInt()
        return base + if (shardIndex < remainder) 1 else 0
    }

    fun accepts(globalOrdinal: Long, shardIndex: Int): Boolean =
        shardFor(globalOrdinal) == shardIndex
}
