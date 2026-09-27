package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.CostHandler
import com.wingedsheep.engine.handlers.actions.spell.CastPaymentProcessor
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.mana.ManaAbilitySideEffectExecutor
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.mechanics.mana.ManaSource
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.ManaSymbol
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * A projection of the canonical automatic payment for a plain hand cast funded entirely by
 * unrestricted blue floating mana and ordinary one-blue basic lands. This is deliberately a
 * named representation domain, not a claim that other payments are unreachable or qualified.
 */
@Serializable
data class ActorBasicBluePayment(
    val status: ActorBasicBluePaymentStatus,
    val reason: String,
    val availableBlueMana: Int? = null,
    val totalMana: Int? = null,
    val blueRemainingAfterPayment: Int? = null,
    val bluePoolAfterPayment: Int? = null,
    val tappedSourceIds: List<EntityId> = emptyList(),
)

@Serializable
enum class ActorBasicBluePaymentStatus { PLANNED, UNPAYABLE, UNSUPPORTED }

/** Trusted projection only. No returned object contains a state, solver, definition or callback. */
internal class ActorBasicBluePaymentProjector(registry: CardRegistry) {
    private val solver = ManaSolver(registry)
    private class NonManaPreviewEffect : IllegalStateException()
    private val payment = CastPaymentProcessor(
        solver,
        CostHandler(),
        // A payment preview must never resolve a draw, random choice or other effect to learn
        // its result. Pure basic-land payment has no such effect; anything else stops here.
        ManaAbilitySideEffectExecutor(registry) { _, _, _ -> throw NonManaPreviewEffect() },
    )

    fun project(state: GameState, offered: LegalAction): ActorBasicBluePayment? {
        val action = offered.action as? CastSpell ?: return null
        fun unsupported(reason: String) = ActorBasicBluePayment(ActorBasicBluePaymentStatus.UNSUPPORTED, reason)
        if (state.pendingDecision != null || action.cardId !in state.getHand(action.playerId)) {
            return unsupported("This projection requires a current hand-cast offer outside a pending decision")
        }
        if (state.getBattlefield().any { state.getEntity(it)?.has<FaceDownComponent>() == true }) {
            return unsupported("Payment projection with a concealed battlefield source needs canonical qualification")
        }
        if (action.copy(targets = emptyList()) != CastSpell(action.playerId, action.cardId) ||
            offered.hasXCost || offered.additionalCostInfo != null || offered.additionalLifeCost != 0 ||
            offered.hasConvoke || offered.hasDelve || offered.hasHarmonize || offered.hasTapForGeneric ||
            offered.tapForPower || offered.requiresForage || offered.modalEnumeration != null ||
            offered.manaCostPerExtraTarget != null || offered.requiresDamageDistribution) {
            return unsupported("The offered cast needs a different payment representation")
        }
        val costText = offered.manaCostString ?: return unsupported("The offer has no current mana cost")
        val cost = ManaCost.parse(costText)
        if (cost.symbols.any { it !is ManaSymbol.Generic && (it !is ManaSymbol.Colored || it.color != Color.BLUE) }) {
            return unsupported("The current cost is outside the generic-and-blue representation")
        }
        val beforePool = state.getEntity(action.playerId)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
        if (!beforePool.isUnrestrictedBlue()) return unsupported("The current pool is not solely unrestricted blue")
        val beforeSources = solver.findAvailableManaSources(state, action.playerId)
        if (!pureBasicBlueSources(state, beforeSources)) {
            return unsupported("The canonical planner exposes a source outside ordinary one-blue basic lands")
        }
        val available = beforePool.blue + beforeSources.size
        // processPayment is the production payment implementation. Its resulting immutable state
        // determines which sources remain and how much blue floats; no independent allocation or
        // second mana-solving implementation is used. This does not execute the spell or its triggers.
        val result = try {
            payment.processPayment(state, action, cost, "actor payment projection", 0)
        } catch (_: NonManaPreviewEffect) {
            return unsupported("The selected mana payment requires a non-mana effect")
        }
        if (result.error != null) {
            return if (offered.affordable) unsupported("The automatic payment disagrees with offered affordability")
            else ActorBasicBluePayment(ActorBasicBluePaymentStatus.UNPAYABLE,
                "The canonical automatic payment cannot pay the current cost", available, cost.cmc)
        }
        val afterPool = result.state.getEntity(action.playerId)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
        val afterSources = solver.findAvailableManaSources(result.state, action.playerId)
        if (!afterPool.isUnrestrictedBlue() || !pureBasicBlueSources(result.state, afterSources)) {
            return unsupported("The resulting payment state is outside the basic-blue representation")
        }
        val remaining = afterPool.blue + afterSources.size
        val beforeIds = beforeSources.map { it.entityId }.toSet()
        val afterIds = afterSources.map { it.entityId }.toSet()
        if (!beforeIds.containsAll(afterIds) || available - cost.cmc != remaining) {
            return unsupported("The actual payment does not preserve the declared basic-blue accounting")
        }
        return ActorBasicBluePayment(
            ActorBasicBluePaymentStatus.PLANNED,
            "Exact canonical automatic payment in the basic-blue representation",
            available, cost.cmc, remaining, afterPool.blue,
            (beforeIds - afterIds).sortedBy { it.value },
        )
    }

    private fun ManaPoolComponent.isUnrestrictedBlue(): Boolean =
        white == 0 && black == 0 && red == 0 && green == 0 && colorless == 0 && restrictedMana.isEmpty()

    private fun pureBasicBlueSources(state: GameState, sources: List<ManaSource>): Boolean =
        sources.map { it.entityId }.distinct().size == sources.size && sources.all { source ->
            source.isBasicLand && source.isLand && !source.isCreature &&
                source.name in setOf("Island", "Snow-Covered Island") &&
                state.getEntity(source.entityId)?.has<FaceDownComponent>() == false &&
                source.producesColors == setOf(Color.BLUE) && !source.producesColorless &&
                source.manaAmount == 1 && source.bonusManaPerTap == 0 && source.bonusManaColorlessPerTap == 0 &&
                !source.bonusManaIsAnyColor && !source.hasPainCost && source.painAmount == 0 &&
                source.colorPainCost.isEmpty() && source.colorlessPainCost == 0 &&
                source.restriction == null && source.colorRestrictions.isEmpty() && source.colorRiders.isEmpty() &&
                !source.hasContextSensitiveAbilities && source.colorActivationManaCost.isEmpty() &&
                !source.requiresSacrifice && source.colorsRequiringSacrifice.isEmpty() && source.tapPermanentsSubCost == null
        }
}
