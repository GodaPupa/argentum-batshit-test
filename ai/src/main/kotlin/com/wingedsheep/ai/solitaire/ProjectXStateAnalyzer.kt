package com.wingedsheep.ai.solitaire

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Symbolic terminal/progress facts used by the Project X solitaire runner. */
data class ProjectXOutcome(
    val completeInfiniteEngine: Boolean,
    val arbitrarilyLargeCarrionFeeder: Boolean,
    val arbitraryLife: Boolean,
    val nobleDeterministicLethal: Boolean,
    val feederCombatLethalThisTurn: Boolean,
    val secondaryWitnessLoop: Boolean,
) {
    val immediateDeterministicLethal: Boolean
        get() = nobleDeterministicLethal || feederCombatLethalThisTurn
}

enum class SecondaryWitnessStep {
    PRODUCE_GREEN_WITH_BIRCHLORE,
    CAST_RECURRABLE_GREEN_ELF,
    RESOLVE_NETTLE_UNTAP,
    TARGET_WITNESS_WITH_IVY,
    FLOAT_NEXT_GREEN_WITH_NETTLE_AND_ENTRANT,
    SACRIFICE_ENTRANT_TO_FEEDER,
    RESOLVE_IVY_COUNTER_ON_WITNESS,
    RETURN_ENTRANT_WITH_WITNESS,
}

/** Public-information analysis; it never depends on library order except for an explicit tutor. */
class ProjectXStateAnalyzer {
    val primaryRoles: Set<String> = setOf(CARRION_FEEDER, SAFEHOLD_ELITE, IVY_LANE_DENIZEN)

    fun outcome(state: GameState, playerId: EntityId): ProjectXOutcome {
        val battlefield = namesIn(state, playerId, Zone.BATTLEFIELD)
        val primary = primaryRoles.all(battlefield::contains) && eliteCanPersist(state, playerId)
        val secondary = secondaryWitnessLoopAvailable(state, playerId)
        val unboundedSacrificeLoop = primary || secondary
        val feeder = permanent(state, playerId, CARRION_FEEDER)
        val feederCanAttack = feeder != null &&
            state.isActiveTurnFor(playerId) &&
            state.step in PRECOMBAT_WINDOWS &&
            state.getEntity(feeder)?.has<SummoningSicknessComponent>() != true &&
            state.getEntity(feeder)?.has<TappedComponent>() != true

        return ProjectXOutcome(
            completeInfiniteEngine = primary,
            arbitrarilyLargeCarrionFeeder = unboundedSacrificeLoop,
            arbitraryLife = unboundedSacrificeLoop && ESSENCE_WARDEN in battlefield,
            nobleDeterministicLethal = unboundedSacrificeLoop && FALKENRATH_NOBLE in battlefield,
            // A blank goldfish opponent cannot block. This is deliberately separate from Noble:
            // an unbounded power claim is not damage, and summoning sickness/postcombat timing matter.
            feederCombatLethalThisTurn = unboundedSacrificeLoop && feederCanAttack,
            secondaryWitnessLoop = secondary,
        )
    }

    fun missingPrimaryRoles(state: GameState, playerId: EntityId): Set<String> {
        val available = namesIn(state, playerId, Zone.BATTLEFIELD) + namesIn(state, playerId, Zone.HAND)
        return primaryRoles - available
    }

    fun secondaryWitnessSequence(state: GameState, playerId: EntityId): List<SecondaryWitnessStep>? =
        if (secondaryWitnessLoopAvailable(state, playerId)) SecondaryWitnessStep.entries else null

    fun battlefieldNames(state: GameState, playerId: EntityId): Set<String> =
        namesIn(state, playerId, Zone.BATTLEFIELD)

    fun handNames(state: GameState, playerId: EntityId): Set<String> =
        namesIn(state, playerId, Zone.HAND)

    fun graveyardNames(state: GameState, playerId: EntityId): Set<String> =
        namesIn(state, playerId, Zone.GRAVEYARD)

    fun libraryNames(state: GameState, playerId: EntityId): Set<String> =
        namesIn(state, playerId, Zone.LIBRARY)

    fun name(state: GameState, entityId: EntityId): String? =
        state.getEntity(entityId)?.get<CardComponent>()?.name

    fun permanent(state: GameState, playerId: EntityId, name: String): EntityId? =
        state.controlledBattlefield(playerId).firstOrNull { this.name(state, it) == name }

    fun isUntapped(state: GameState, entityId: EntityId): Boolean =
        state.getEntity(entityId)?.has<TappedComponent>() == false

    fun isElf(state: GameState, entityId: EntityId): Boolean =
        "Elf" in state.projectedState.getSubtypes(entityId)

    private fun namesIn(state: GameState, playerId: EntityId, zone: Zone): Set<String> =
        state.getZone(playerId, zone).mapNotNull { name(state, it) }.toSet()

    private fun eliteCanPersist(state: GameState, playerId: EntityId): Boolean {
        val elite = permanent(state, playerId, SAFEHOLD_ELITE) ?: return false
        return (state.getEntity(elite)?.get<CountersComponent>()
            ?.getCount(CounterType.MINUS_ONE_MINUS_ONE) ?: 0) == 0
    }

    /**
     * The secondary loop uses one existing Nettle plus the one-mana green Elf being recurred:
     * tap two Elves for {G}, cast the Elf (untapping Nettle), let it enter, tap Nettle + the
     * entrant for the next {G}, sacrifice the entrant while Ivy's trigger is pending, then put
     * Ivy's counter on Witness so Witness returns that entrant. No arbitrary iterations are run.
     */
    private fun secondaryWitnessLoopAvailable(state: GameState, playerId: EntityId): Boolean {
        val battlefield = battlefieldNames(state, playerId)
        val required = setOf(
            CARRION_FEEDER, IVY_LANE_DENIZEN, EVOLUTION_WITNESS,
            BIRCHLORE_RANGERS, NETTLE_SENTINEL,
        )
        if (!required.all(battlefield::contains)) return false
        val recyclableInHand = state.getHand(playerId).any { id ->
            val card = state.getEntity(id)?.get<CardComponent>() ?: return@any false
            card.isCreature && card.manaValue == 1 && card.colors.any { it.name == "GREEN" } &&
                card.typeLine.subtypes.any { it.value == "Elf" }
        }
        val untappedElves = state.controlledBattlefield(playerId).count { isElf(state, it) && isUntapped(state, it) }
        return recyclableInHand && untappedElves >= 2
    }

    companion object {
        const val CARRION_FEEDER = "Carrion Feeder"
        const val SAFEHOLD_ELITE = "Safehold Elite"
        const val IVY_LANE_DENIZEN = "Ivy Lane Denizen"
        const val WIREWOOD_HERALD = "Wirewood Herald"
        const val EVOLUTION_WITNESS = "Evolution Witness"
        const val NETTLE_SENTINEL = "Nettle Sentinel"
        const val BIRCHLORE_RANGERS = "Birchlore Rangers"
        const val FALKENRATH_NOBLE = "Falkenrath Noble"
        const val ESSENCE_WARDEN = "Essence Warden"
        const val QUIRION_RANGER = "Quirion Ranger"
        const val WINDING_WAY = "Winding Way"
        const val LEAD_THE_STAMPEDE = "Lead the Stampede"

        private val PRECOMBAT_WINDOWS = setOf(
            Step.PRECOMBAT_MAIN,
            Step.BEGIN_COMBAT,
            Step.DECLARE_ATTACKERS,
        )
    }
}
