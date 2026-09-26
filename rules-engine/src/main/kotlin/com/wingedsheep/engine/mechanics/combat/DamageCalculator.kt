package com.wingedsheep.engine.mechanics.combat

import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.engine.state.components.combat.AttackerOrderComponent
import com.wingedsheep.engine.state.components.combat.BlockedComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.combat.DamageAssignmentOrderComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.events.DamageType
import com.wingedsheep.sdk.scripting.PreventDamage
import com.wingedsheep.sdk.scripting.events.RecipientFilter

/**
 * Calculates legal full-power default combat damage assignments (CR 510).
 *
 * Key rules:
 * - CR 510.1a: A source assigns its full available damage when it has recipients.
 * - CR 510.1c/d: Damage can be freely divided among eligible creatures.
 * - CR 702.2c: A positive deathtouch assignment is lethal for assignment purposes.
 * - CR 702.19b: Trample requires lethal assignment to blockers before a defender drain.
 */
class DamageCalculator(
    private val cardRegistry: CardRegistry? = null,
) {

    private val predicateEvaluator = PredicateEvaluator()

    /**
     * Result of calculating lethal damage for a creature.
     */
    data class LethalDamageInfo(
        val creatureId: EntityId,
        val toughness: Int,
        val damageAlreadyMarked: Int,
        val lethalAmount: Int,
        val sourceHasDeathtouch: Boolean
    )

    /**
     * Result of auto-calculating damage distribution.
     */
    data class DamageDistribution(
        /** Map of target (creature or player) to damage amount */
        val assignments: Map<EntityId, Int>,
        /** Total damage assigned */
        val totalAssigned: Int,
        /** Damage that couldn't be assigned (shouldn't happen normally) */
        val unassignedDamage: Int
    )

    /**
     * Calculate the minimum damage needed to be considered "lethal" for a creature.
     *
     * For trample (CR 702.19b), damage is lethal if it equals or exceeds toughness minus
     * damage already marked, OR if the source has deathtouch (any nonzero amount).
     *
     * @param state Current game state
     * @param creatureId The creature receiving damage
     * @param sourceId The source dealing damage (to check for deathtouch)
     * @return LethalDamageInfo with calculated values
     */
    fun calculateLethalDamage(
        state: GameState,
        creatureId: EntityId,
        sourceId: EntityId
    ): LethalDamageInfo {
        val creatureContainer = state.getEntity(creatureId)
        val damageMarked = creatureContainer?.get<DamageComponent>()?.amount ?: 0

        // Use projected values for toughness (includes floating effects like +4/+4)
        val projected = state.projectedState
        val toughness = projected.getToughness(creatureId) ?: 0

        // Check if source has deathtouch (using projected keywords)
        val hasDeathtouch = projected.hasKeyword(sourceId, Keyword.DEATHTOUCH)

        // With deathtouch, 1 damage is lethal. Otherwise, need to reach toughness.
        val lethalAmount = if (hasDeathtouch) {
            1
        } else {
            (toughness - damageMarked).coerceAtLeast(1)
        }

        return LethalDamageInfo(
            creatureId = creatureId,
            toughness = toughness,
            damageAlreadyMarked = damageMarked,
            lethalAmount = lethalAmount,
            sourceHasDeathtouch = hasDeathtouch
        )
    }

    /**
     * Calculate a suggested complete damage distribution for an attacker.
     *
     * This heuristic favors lethal damage in presentation order. It is not a restriction on
     * player choices: CR 510.1c permits free division among eligible blockers.
     *
     * @param state Current game state
     * @param attackerId The attacking creature
     * @return DamageDistribution with assignments to blockers (and player if trample)
     */
    fun calculateAutoDamageDistribution(
        state: GameState,
        attackerId: EntityId
    ): DamageDistribution {
        val attackerContainer = state.getEntity(attackerId)
            ?: return DamageDistribution(emptyMap(), 0, 0)

        val attackerCard = attackerContainer.get<CardComponent>()
            ?: return DamageDistribution(emptyMap(), 0, 0)

        // Use projected values for power and keywords (includes floating effects like +4/+4)
        val projected = state.projectedState
        val attackerPower = CombatDamageUtils.getAssignedCombatDamage(state, projected, attackerId, cardRegistry)
        if (attackerPower <= 0) {
            return DamageDistribution(emptyMap(), 0, 0)
        }

        val hasTrample = projected.hasKeyword(attackerId, Keyword.TRAMPLE)

        // Get blockers in the stable default order, filtering out departed blockers.
        val blockedComponent = attackerContainer.get<BlockedComponent>()
        val orderedBlockers = (attackerContainer.get<DamageAssignmentOrderComponent>()?.orderedBlockers
            ?: blockedComponent?.blockerIds
            ?: emptyList()).filter { it in state.getBattlefield() }

        if (orderedBlockers.isEmpty()) {
            // Unblocked - this shouldn't be called for unblocked attackers
            return DamageDistribution(emptyMap(), 0, attackerPower)
        }

        val assignments = mutableMapOf<EntityId, Int>()
        var remainingPower = attackerPower

        // Assign lethal damage to each blocker in order, accounting for damage prevention.
        // The default assignment includes extra damage to overcome prevention effects
        // (e.g., Daunting Defender preventing 1 damage to Clerics) so that the default
        // actually kills the blocker, matching what a player would do in a physical game.
        // Per CR 510.1c, without trample ALL damage must be assigned to blockers.
        // The last blocker receives all remaining damage (not just lethal).
        for ((index, blockerId) in orderedBlockers.withIndex()) {
            if (remainingPower <= 0) break

            val isLastBlocker = index == orderedBlockers.size - 1
            val lethalInfo = calculateLethalDamage(state, blockerId, attackerId)
            val preventionAmount = estimateDamagePrevention(state, projected, blockerId)
            val effectiveLethal = lethalInfo.lethalAmount + preventionAmount
            val damageToAssign = if (isLastBlocker && !hasTrample) {
                // Last blocker without trample gets all remaining damage
                remainingPower
            } else {
                minOf(remainingPower, effectiveLethal)
            }

            assignments[blockerId] = damageToAssign
            remainingPower -= damageToAssign
        }

        // Handle trample - excess damage goes to defending player
        if (hasTrample && remainingPower > 0) {
            // Get the defending player from AttackingComponent
            val attackingComponent = attackerContainer.get<com.wingedsheep.engine.state.components.combat.AttackingComponent>()
            if (attackingComponent != null) {
                assignments[attackingComponent.defenderId] = remainingPower
                remainingPower = 0
            }
        }

        return DamageDistribution(
            assignments = assignments,
            totalAssigned = attackerPower - remainingPower,
            unassignedDamage = remainingPower
        )
    }

    /**
     * Check if an attacker requires manual damage assignment.
     *
     * Manual assignment is needed when:
     * - Attacker has trample and is blocked (player can choose split)
     * - Attacker has multiple eligible blockers, regardless of whether its damage can be lethal
     * - User preference is set to always manually assign
     */
    fun requiresManualAssignment(
        state: GameState,
        attackerId: EntityId,
        userPreference: Boolean = false
    ): Boolean {
        if (userPreference) return true

        val attackerContainer = state.getEntity(attackerId) ?: return false
        attackerContainer.get<CardComponent>() ?: return false

        val blockedComponent = attackerContainer.get<BlockedComponent>()
        val blockerIds = blockedComponent?.blockerIds?.filter { it in state.getBattlefield() } ?: return false
        if (blockerIds.isEmpty()) return false

        // Use projected values for keywords and power (includes floating effects like +4/+4)
        val projected = state.projectedState

        // Single blocker without trample = no choice (all damage goes to that one blocker).
        if (blockerIds.size <= 1 && !projected.hasKeyword(attackerId, Keyword.TRAMPLE)) {
            return false
        }

        // Trample (choose how much spills over) or 2+ blockers always present a choice: the
        // controller may freely divide damage among blockers (CR 510.1c), including multiple
        // nonlethal amounts. Surface the board whenever there is more than one way to assign.
        return true
    }

    /**
     * Auto-calculate damage distribution for a blocker blocking multiple attackers.
     *
     * Similar to [calculateAutoDamageDistribution] but for the reverse case: a single
     * blocker dividing its damage among multiple attackers it's blocking.
     *
     * Uses [AttackerOrderComponent] as a stable default order. The heuristic favors lethal damage,
     * with the last attacker receiving all remaining damage. CR 510.1d permits the player to
     * choose any complete division among those attackers instead. [pendingDamage] lets the
     * heuristic account for other blockers' suggested assignments.
     *
     * @param state Current game state
     * @param blockerId The blocking creature
     * @param pendingDamage Damage already being assigned to each creature by other sources
     *        in this same combat damage step
     * @return DamageDistribution with assignments to attackers
     */
    fun calculateBlockerDamageDistribution(
        state: GameState,
        blockerId: EntityId,
        pendingDamage: Map<EntityId, Int> = emptyMap()
    ): DamageDistribution {
        val blockerContainer = state.getEntity(blockerId)
            ?: return DamageDistribution(emptyMap(), 0, 0)

        blockerContainer.get<CardComponent>()
            ?: return DamageDistribution(emptyMap(), 0, 0)

        val projected = state.projectedState
        val blockerPower = CombatDamageUtils.getAssignedCombatDamage(state, projected, blockerId, cardRegistry)
        if (blockerPower <= 0) {
            return DamageDistribution(emptyMap(), 0, 0)
        }

        val blockingComponent = blockerContainer.get<BlockingComponent>()
            ?: return DamageDistribution(emptyMap(), 0, 0)

        // Get attackers in stable default order, filtering out departed attackers.
        val orderedAttackers = (blockerContainer.get<AttackerOrderComponent>()?.orderedAttackers
            ?: blockingComponent.blockedAttackerIds).filter { it in state.getBattlefield() }

        if (orderedAttackers.isEmpty()) {
            return DamageDistribution(emptyMap(), 0, blockerPower)
        }

        val assignments = mutableMapOf<EntityId, Int>()
        var remainingPower = blockerPower

        for ((index, attackerId) in orderedAttackers.withIndex()) {
            if (remainingPower <= 0) break

            val isLastAttacker = index == orderedAttackers.size - 1
            val lethalInfo = calculateLethalDamage(state, attackerId, blockerId)
            val preventionAmount = estimateDamagePrevention(state, projected, attackerId)
            // The default heuristic accounts for concurrent damage from other sources.
            val alreadyPending = pendingDamage[attackerId] ?: 0
            val effectiveLethal = (lethalInfo.lethalAmount + preventionAmount - alreadyPending).coerceAtLeast(0)
            val damageToAssign = if (isLastAttacker) {
                // Last attacker gets all remaining damage
                remainingPower
            } else {
                minOf(remainingPower, effectiveLethal)
            }

            assignments[attackerId] = damageToAssign
            remainingPower -= damageToAssign
        }

        return DamageDistribution(
            assignments = assignments,
            totalAssigned = blockerPower - remainingPower,
            unassignedDamage = remainingPower
        )
    }

    /**
     * Get lethal thresholds for each blocker. These constrain trample overflow; they are not
     * minimum amounts for ordinary division among creatures.
     */
    fun getMinimumAssignments(
        state: GameState,
        attackerId: EntityId
    ): Map<EntityId, Int> {
        val attackerContainer = state.getEntity(attackerId) ?: return emptyMap()

        val orderedBlockers = (attackerContainer.get<DamageAssignmentOrderComponent>()?.orderedBlockers
            ?: attackerContainer.get<BlockedComponent>()?.blockerIds
            ?: return emptyMap()).filter { it in state.getBattlefield() }

        val minimums = mutableMapOf<EntityId, Int>()

        for (blockerId in orderedBlockers) {
            val lethalInfo = calculateLethalDamage(state, blockerId, attackerId)
            minimums[blockerId] = lethalInfo.lethalAmount
        }

        return minimums
    }

    /**
     * Estimate how much damage prevention would apply to a creature.
     *
     * Scans the battlefield for ReplacementEffectSourceComponent containing
     * PreventDamage effects that match the target creature.
     * This is used to adjust the default damage assignment so that "lethal"
     * accounts for prevention (e.g., Daunting Defender prevents 1 to Clerics).
     */
    private fun estimateDamagePrevention(
        state: GameState,
        projected: ProjectedState,
        targetId: EntityId
    ): Int {
        var totalPrevention = 0

        for (entityId in state.getBattlefield()) {
            val container = state.getEntity(entityId) ?: continue
            val replacementComponent = container.get<ReplacementEffectSourceComponent>() ?: continue
            val sourceControllerId = container.get<ControllerComponent>()?.playerId ?: continue

            for (effect in replacementComponent.replacementEffects) {
                if (effect !is PreventDamage) continue

                val damageEvent = effect.appliesTo
                if (damageEvent !is com.wingedsheep.sdk.scripting.EventPattern.DamageEvent) continue

                // This is called during combat, so combat damage type always matches
                val damageTypeMatches = when (damageEvent.damageType) {
                    is DamageType.Any -> true
                    is DamageType.Combat -> true  // We're estimating for combat
                    is DamageType.NonCombat -> false
                }
                if (!damageTypeMatches) continue

                val recipientMatches = when (val recipient = damageEvent.recipient) {
                    is RecipientFilter.Self -> targetId == entityId
                    is RecipientFilter.EnchantedCreature, is RecipientFilter.EquippedCreature -> {
                        val attachedTo = container.get<AttachedToComponent>()?.targetId
                        targetId == attachedTo
                    }
                    is RecipientFilter.Matching -> {
                        val context = PredicateContext(controllerId = sourceControllerId)
                        predicateEvaluator.matches(state, projected, targetId, recipient.filter, context)
                    }
                    is RecipientFilter.CreatureYouControl -> {
                        val isCreature = projected.isCreature(targetId)
                        val isControlled = projected.getController(targetId) == sourceControllerId
                        isCreature && isControlled
                    }
                    is RecipientFilter.Any -> true
                    else -> false
                }

                if (recipientMatches) {
                    totalPrevention += effect.amount ?: 0
                }
            }
        }

        return totalPrevention
    }

    private fun getCreatureName(state: GameState, creatureId: EntityId): String {
        return state.getEntity(creatureId)?.get<CardComponent>()?.name ?: "creature"
    }
}
