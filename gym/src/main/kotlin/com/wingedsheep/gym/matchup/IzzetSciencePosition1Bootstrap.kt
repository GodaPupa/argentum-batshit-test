package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.InitializationResult
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Format
import java.security.MessageDigest

const val VETERAN_BEASTRIDER_FROZEN_SHA256 =
    "c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be"

/** The adapter's assignment is relative to Izzet, whose stable seat is always zero. */
internal enum class IzzetPosition1Assignment(val wireName: String, val startingPlayerIndex: Int) {
    PLAY("play", 0),
    DRAW("draw", 1);

    companion object {
        fun parse(value: String): IzzetPosition1Assignment =
            entries.singleOrNull { it.wireName == value }
                ?: throw IllegalArgumentException("invalid Izzet play/draw assignment")
    }
}

/**
 * Real-engine initialization seam for the frozen pair. This does not issue execution authority.
 * The accepted Phase-33 adapter remains responsible for authorization, durable attempt marking,
 * exclusive seed consumption and engine-source binding before a future official caller enters it.
 *
 * Preparation is independent of experimental randomness: it checks both frozen files, the local
 * deck translation, and every registry identity before a seed supplier can be called. It never
 * substitutes cards, generates a seed, smooths an opening hand, or keeps a hand automatically.
 */
internal object IzzetSciencePosition1Bootstrap {
    val pdhFormat = Format.Commander(startingLife = 30, commanderDamageThreshold = 16)

    /** Builds the exact configuration without initializing a game or reading a seed. */
    fun configuration(
        controlBytes: ByteArray,
        opponentBytes: ByteArray,
        assignment: String,
    ): GameConfig {
        val frozenControl = controlBytes.copyOf()
        val frozenOpponent = opponentBytes.copyOf()
        require(sha256(frozenControl) == IZZET_SCIENCE_V07_CONTROL_SHA256) {
            "Izzet v0.7 frozen control bytes changed"
        }
        require(sha256(frozenOpponent) == VETERAN_BEASTRIDER_FROZEN_SHA256) {
            "Veteran Beastrider frozen opponent bytes changed"
        }
        val izzet = IzzetScienceVeteranBeastriderEngineReadiness.izzetCounts
        val veteran = IzzetScienceVeteranBeastriderEngineReadiness.veteranCounts
        require(cardCounts(frozenControl) == izzet + (IZZET_SCIENCE_COMMANDER to 1)) {
            "Izzet runtime deck differs from its frozen file"
        }
        require(cardCounts(frozenOpponent) == veteran + (VETERAN_BEASTRIDER_COMMANDER to 1)) {
            "Veteran runtime deck differs from its frozen file"
        }
        require(izzet.values.sum() == 99 && veteran.values.sum() == 99) {
            "both frozen mainboards must contain exactly 99 cards"
        }
        val seat = IzzetPosition1Assignment.parse(assignment)
        return GameConfig(
            players = listOf(
                PlayerConfig(
                    name = "Izzet Science v0.7",
                    deck = IzzetScienceVeteranBeastriderEngineReadiness.izzetDeck(),
                    startingLife = 30,
                    commanderCardName = IZZET_SCIENCE_COMMANDER,
                ),
                PlayerConfig(
                    name = "Veteran Beastrider",
                    deck = IzzetScienceVeteranBeastriderEngineReadiness.veteranDeck(),
                    startingLife = 30,
                    commanderCardName = VETERAN_BEASTRIDER_COMMANDER,
                ),
            ),
            startingHandSize = 7,
            skipMulligans = false,
            useHandSmoother = false,
            startingPlayerIndex = seat.startingPlayerIndex,
            format = pdhFormat,
            seed = null,
        )
    }

    /** May run before entering Phase 33; a missing registry identity must not burn an assignment. */
    fun prepare(
        registry: CardRegistry,
        controlBytes: ByteArray,
        opponentBytes: ByteArray,
        assignment: String,
    ): GameConfig {
        val config = configuration(controlBytes, opponentBytes, assignment)
        val unresolved = IzzetScienceVeteranBeastriderEngineReadiness.unresolvedCardIdentities(registry)
        require(unresolved.isEmpty()) {
            "frozen-pair initialization blocked: ${unresolved.joinToString("; ")}"
        }
        return config
    }

    /**
     * Dormant production path; the incomplete frozen registry currently rejects before seed access.
     * This is a component, not a standalone official runner or a capability-admission receipt.
     * The future bridge must also call [prepare] before entering the seed-revealing adapter.
     */
    fun initialize(
        registry: CardRegistry,
        controlBytes: ByteArray,
        opponentBytes: ByteArray,
        assignment: String,
        revealAuthorizedSeed: () -> Long,
    ): InitializationResult {
        val config = prepare(registry, controlBytes, opponentBytes, assignment)
        val seed = revealAuthorizedSeed()
        require(seed != 0L) { "official seed must be nonzero" }
        return GameInitializer(registry).initializeGame(config.copy(seed = seed))
    }

    private fun cardCounts(bytes: ByteArray): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        val row = Regex("^([1-9][0-9]*) (.+)$")
        bytes.toString(Charsets.UTF_8).lineSequence().forEach { line ->
            row.matchEntire(line)?.let { match ->
                val count = match.groupValues[1].toInt()
                val name = match.groupValues[2]
                require(name !in counts) { "duplicate frozen card row: $name" }
                counts[name] = count
            }
        }
        return counts
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
