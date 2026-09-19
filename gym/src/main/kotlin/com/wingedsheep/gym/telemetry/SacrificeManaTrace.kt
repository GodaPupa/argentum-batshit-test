package com.wingedsheep.gym.telemetry

import com.wingedsheep.engine.core.AbilityActivatedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ManaAddedEvent
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** One explicit sacrifice-for-mana activation and the fate of the mana it produced. */
@Serializable
data class SacrificeManaUse(
    val sourceId: EntityId,
    val sourceName: String,
    val activationTurn: Int,
    val sacrificed: Boolean,
    val manaProduced: Int,
    val manaConsumed: Int,
    val fundedActions: List<String>,
    val unusedMana: Int,
)

/**
 * Correlates existing engine events with the per-source tags already held by [ManaPoolComponent].
 * It changes no game state and introduces no card knowledge.
 *
 * A source-tag decrease accompanied by a [ManaSpentEvent] is consumption. A [SpellCastEvent]
 * supplies exact spell attribution; otherwise the spend event's reason names the funded action
 * where the engine can determine it. A decrease with no spend event is mana lost unused at a
 * step/phase boundary.
 */
class SacrificeManaTrace {
    private data class MutableUse(
        val sourceId: EntityId,
        val sourceName: String,
        val activationTurn: Int,
        var sacrificed: Boolean = false,
        var produced: Int = 0,
        var consumed: Int = 0,
        val funded: MutableList<String> = mutableListOf(),
        var unused: Int = 0,
    )

    private val uses = linkedMapOf<EntityId, MutableUse>()

    fun observe(
        before: GameState,
        after: GameState,
        events: List<GameEvent>,
        playerId: EntityId,
        turn: Int,
    ) {
        val sacrificedIds = events.filterIsInstance<ZoneChangeEvent>()
            .filter(ZoneChangeEvent::wasSacrificed)
            .map(ZoneChangeEvent::entityId)
            .toSet()
        events.filterIsInstance<AbilityActivatedEvent>()
            .filter { it.controllerId == playerId && it.isManaAbility && it.sourceId in sacrificedIds }
            .forEach { event ->
                uses.putIfAbsent(event.sourceId, MutableUse(event.sourceId, event.sourceName, turn, sacrificed = true))
            }

        val producedThisStep = events.filterIsInstance<ManaAddedEvent>()
            .filter { it.playerId == playerId && it.sourceId in uses }
            .groupingBy { requireNotNull(it.sourceId) }
            .fold(0) { total, event -> total + event.total }
        producedThisStep.forEach { (sourceId, amount) -> uses[sourceId]?.produced = uses.getValue(sourceId).produced + amount }

        val beforeBySource = before.getEntity(playerId)?.get<ManaPoolComponent>()?.manaBySource.orEmpty()
        val afterBySource = after.getEntity(playerId)?.get<ManaPoolComponent>()?.manaBySource.orEmpty()
        val spendReasons = events.filterIsInstance<ManaSpentEvent>()
            .filter { it.playerId == playerId && it.total > 0 }
            .map(ManaSpentEvent::reason)
            .distinct()

        for ((sourceId, use) in uses) {
            val available = (beforeBySource[sourceId] ?: 0) + (producedThisStep[sourceId] ?: 0)
            val remaining = afterBySource[sourceId] ?: 0
            val departed = (available - remaining).coerceAtLeast(0)
            if (departed == 0) continue

            val exactSpells = events.filterIsInstance<SpellCastEvent>()
                .filter { it.casterId == playerId && sourceId in it.spentManaSourceIds }
                .map { "Cast ${it.cardName}@T$turn" }
            if (exactSpells.isNotEmpty() || spendReasons.isNotEmpty()) {
                use.consumed += departed
                use.funded += if (exactSpells.isNotEmpty()) exactSpells else spendReasons.map { "$it@T$turn" }
            } else {
                use.unused += departed
            }
        }
    }

    fun snapshot(): List<SacrificeManaUse> = uses.values.map { use ->
        SacrificeManaUse(
            sourceId = use.sourceId,
            sourceName = use.sourceName,
            activationTurn = use.activationTurn,
            sacrificed = use.sacrificed,
            manaProduced = use.produced,
            manaConsumed = use.consumed,
            fundedActions = use.funded.distinct(),
            unusedMana = use.unused,
        )
    }
}
