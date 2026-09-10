package com.wingedsheep.sdk.model

import kotlinx.serialization.Serializable

/**
 * A pure, immutable, deterministic pseudo-random number generator.
 *
 * Every draw returns a value paired with the *next* [GameRng] — the generator never mutates in
 * place. This is what lets the engine stay a pure function `(GameState, GameAction) -> Result`
 * while still flipping coins and shuffling libraries: the RNG state lives inside [GameState] and is
 * threaded back out with the rest of the new state. Two games started from the same seed and fed
 * the same actions therefore produce byte-identical states — the property replays, MCTS rollouts,
 * and the cross-engine parity harness all rely on.
 *
 * The algorithm is SplitMix64 (Steele, Lea & Flood 2014): a fixed-increment Weyl sequence run
 * through a strong finalizing mix. It is fast, has a full 2^64 period, passes the standard
 * statistical batteries, and — crucially here — is trivially serializable as a single [Long] and
 * supports cheap [split] into independent sub-streams.
 *
 * Do NOT use this for anything security-sensitive; it is not cryptographic.
 */
@Serializable
data class GameRng(val state: Long) {

    /** Returns a uniformly distributed 64-bit value and the advanced generator. */
    fun nextLong(): Pair<Long, GameRng> {
        val newState = state + GOLDEN_GAMMA
        return mix64(newState) to GameRng(newState)
    }

    /**
     * Returns a uniformly distributed value in `[0, bound)` and the advanced generator.
     *
     * Uses rejection sampling on the top 31 bits so the result is unbiased even when [bound] is
     * not a power of two (the same technique as `java.util.Random.nextInt`).
     */
    fun nextInt(bound: Int): Pair<Int, GameRng> {
        require(bound > 0) { "bound must be positive, was $bound" }
        var rng = this
        while (true) {
            val (raw, next) = rng.nextLong()
            rng = next
            val bits = (raw ushr 33).toInt() // 31-bit non-negative
            val value = bits % bound
            // Reject the few high values that would skew the distribution.
            if (bits - value + (bound - 1) >= 0) return value to rng
        }
    }

    /** Returns a fair coin flip and the advanced generator. */
    fun nextBoolean(): Pair<Boolean, GameRng> {
        val (raw, next) = nextLong()
        return ((raw ushr 63) != 0L) to next
    }

    /** Returns a uniformly distributed double in `[0, 1)` and the advanced generator. */
    fun nextDouble(): Pair<Double, GameRng> {
        val (raw, next) = nextLong()
        return ((raw ushr 11) * DOUBLE_UNIT) to next
    }

    /**
     * Returns a new list with [list]'s elements in a uniformly random order and the advanced
     * generator. Implemented as an in-place Fisher–Yates on a copy, so the input is untouched.
     */
    fun <T> shuffle(list: List<T>): Pair<List<T>, GameRng> {
        if (list.size < 2) return list.toList() to this
        val arr = list.toMutableList()
        var rng = this
        for (i in arr.indices.reversed()) {
            if (i == 0) break
            val (j, next) = rng.nextInt(i + 1)
            rng = next
            val tmp = arr[i]
            arr[i] = arr[j]
            arr[j] = tmp
        }
        return arr.toList() to rng
    }

    /**
     * Returns a uniformly chosen element of [list] and the advanced generator.
     * @throws NoSuchElementException if [list] is empty.
     */
    fun <T> pick(list: List<T>): Pair<T, GameRng> {
        if (list.isEmpty()) throw NoSuchElementException("Cannot pick from an empty list")
        val (i, next) = nextInt(list.size)
        return list[i] to next
    }

    /**
     * Forks an independent sub-stream off this generator. Returns `(child, parent')`: the child is
     * seeded from a freshly mixed output so it diverges immediately, and the parent advances past
     * the draw. Use this to give an isolated event (e.g. each player's opening shuffle) its own
     * stream so adding or removing an unrelated random event elsewhere doesn't perturb it.
     */
    fun split(): Pair<GameRng, GameRng> {
        val (childSeed, advanced) = nextLong()
        return GameRng(childSeed) to advanced
    }

    companion object {
        /** The odd "golden ratio" increment for the Weyl sequence (SplitMix64). */
        private const val GOLDEN_GAMMA: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val DOUBLE_UNIT: Double = 1.0 / (1L shl 53)

        /** Construct a generator from a user-supplied seed. */
        fun seeded(seed: Long): GameRng = GameRng(seed)

        /** SplitMix64 finalizing mix: scrambles a Weyl-sequence value into a strong 64-bit output. */
        private fun mix64(z0: Long): Long {
            var z = z0
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }
    }
}
