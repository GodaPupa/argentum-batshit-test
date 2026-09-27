package com.wingedsheep.engine.mechanics.library

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.player.LibraryOrderingComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import java.security.MessageDigest

enum class LibraryOrderingCause { GAME_SETUP, MULLIGAN, EFFECT }

/**
 * Single pre-draw shuffle implementation for explicit original-copy experiments.
 * With no player component this is byte-for-byte the old shuffle operation, including RNG
 * advancement. Opted-in calls also consume that same RNG operation before applying their
 * frozen permutation; all subsequent non-library randomness keeps its normal stream.
 *
 * Effect rank v1 is ascending lowercase hex SHA-256 of UTF-8
 * `namespace + LF + decimal(row) + LF + decimal(one-based effect ordinal) + LF + copy label`.
 * Ties use the complete copy label lexicographically. Original IDs, deck family, seat,
 * current zone order, pilot decisions and outcomes are never rank inputs.
 *
 * Only the initializer, London handler and ShuffleLibraryEffect call sites are qualified
 * here. Other specialized shuffle effects need separate integration before admission.
 */
object LibraryOrderingService {
    fun shuffle(state: GameState, player: EntityId, cause: LibraryOrderingCause): GameState {
        val key = ZoneKey(player, Zone.LIBRARY)
        val current = state.getZone(key)
        val (randomOrder, advanced) = state.nextRandom { shuffle(current) }
        val component = state.getEntity(player)?.get<LibraryOrderingComponent>()
            ?: return advanced.reorderZone(key, randomOrder)

        require(current.distinct().size == current.size) { "Library contains a duplicate entity" }
        require(current.all { it in component.originalCopies }) {
            "Ordered library contains a card outside the frozen original-copy domain"
        }
        if (cause != LibraryOrderingCause.EFFECT) {
            require(current.size == component.originalCopies.size) {
                "An opening shuffle requires the complete original library"
            }
        }
        val next: LibraryOrderingComponent
        val labels: List<String>
        when (cause) {
            LibraryOrderingCause.GAME_SETUP -> {
                require(!component.setupComplete && component.mulligansUsed == 0 && component.effectShufflesUsed == 0)
                labels = component.plan.openingOrders.first()
                next = component.copy(setupComplete = true)
            }
            LibraryOrderingCause.MULLIGAN -> {
                require(component.setupComplete && component.effectShufflesUsed == 0)
                val index = component.mulligansUsed + 1
                require(index < component.plan.openingOrders.size) { "No frozen ordering for this mulligan" }
                labels = component.plan.openingOrders[index]
                next = component.copy(mulligansUsed = index)
            }
            LibraryOrderingCause.EFFECT -> {
                require(component.setupComplete)
                val ordinal = Math.addExact(component.effectShufflesUsed, 1)
                labels = current.map { component.originalCopies.getValue(it) }
                    .sortedWith(compareBy<String> { label -> rank(component, ordinal, label) }.thenBy { it })
                next = component.copy(effectShufflesUsed = ordinal)
            }
        }
        val remaining = current.associateBy { component.originalCopies.getValue(it) }
        val ordered = labels.mapNotNull(remaining::get)
        require(ordered.size == current.size) { "Frozen ordering did not cover the current library" }
        return advanced.updateEntity(player) { it.with(next) }.reorderZone(key, ordered)
    }

    private fun rank(component: LibraryOrderingComponent, ordinal: Int, label: String): String {
        val input = "${component.plan.namespace}\n${component.plan.row}\n$ordinal\n$label"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
