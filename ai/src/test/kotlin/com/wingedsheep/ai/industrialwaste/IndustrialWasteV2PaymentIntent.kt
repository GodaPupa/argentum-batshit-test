package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.mana.IntrinsicManaAbilities
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import com.wingedsheep.sdk.scripting.effects.AddManaOfChoiceEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlinx.serialization.Serializable

@Serializable
internal data class IndustrialWasteV2PaymentIntent(
    val id: Int,
    val selectedAction: GameAction,
    val manaCost: String,
    val reservedObjects: List<ObjectRef>,
)

@Serializable
internal data class IndustrialWasteV2PaymentIntentRecord(
    val stage: String,
    val intent: IndustrialWasteV2PaymentIntent,
    val fundingAction: GameAction? = null,
)

/**
 * Implements the existing selected action's payment without rescoring after a funding sacrifice.
 * This is a positive executable witness only, never a maximum-mana or negative metric proof.
 * Fixed mana abilities can fund the retained action, including floating a selected sacrifice's
 * mana before announcing that sacrifice. Paid filters first require their cost in the real pool;
 * the canonical ordinary solver verifies every paid filter funding step before its output.
 * A shape without a witnessed funding step remains an execution fault.
 */
internal class IndustrialWasteV2PaymentBinder(
    private val registry: CardRegistry,
    private val record: (IndustrialWasteV2PaymentIntentRecord) -> Unit = {},
) {
    private val solver = ManaSolver(registry)
    private var serial = 0
    private var pending: IndustrialWasteV2PaymentIntent? = null

    fun choose(state: GameState, player: EntityId, legal: List<LegalAction>): GameAction {
        val retained = pending
        val selected = if (retained == null) {
            IndustrialWasteV2PublicActionPolicy.chooseBound(state, player, legal)
        } else {
            check(retained.reservedObjects.all(state::isCurrentObject)) { "Selected payment intent lost an original object" }
            val entry = legal.firstOrNull { shape(it.action) == shape(retained.selectedAction) && it.manaCostString == retained.manaCost }
            if (entry == null) {
                check(state.stack.isNotEmpty()) { "Selected payment intent is no longer offered" }
                return legal.map { it.action }.filterIsInstance<PassPriority>().single()
            }
            check(targets(retained.selectedAction).all { it in entry.validTargets.orEmpty() }) {
                "Selected payment intent target became illegal"
            }
            check(materials(retained.selectedAction).all { it in entry.additionalCostInfo?.validSacrificeTargets.orEmpty() }) {
                "Selected payment intent sacrifice became illegal"
            }
            entry to retained.selectedAction
        }
        val (entry, action) = selected
        val costText = entry.manaCostString ?: return action
        check(!entry.hasXCost) { "Unqualified variable payment shape" }
        val cost = ManaCost.parse(costText)
        if (autoPayable(state, player, action, cost)) {
            if (retained != null) record(IndustrialWasteV2PaymentIntentRecord("SUBMIT_SELECTED_ACTION", retained))
            pending = null
            return action
        }
        val intent = retained ?: IndustrialWasteV2PaymentIntent(++serial, action, costText,
            (listOfNotNull(source(action)) + targets(action) + materials(action)).distinct()
                .map { requireNotNull(state.objectRef(it)) { "Selected intent lacks original object identity" } })
            .also { record(IndustrialWasteV2PaymentIntentRecord("SELECTED_BEFORE_FUNDING", it)); pending = it }
        val reserved = intent.reservedObjects.map { it.entityId }.toSet()
        val pool = pool(state, player)
        val remaining = pool.payPartial(cost).remainingCost.cmc
        val candidates = legal.filter { it.affordable && it.isManaAbility }.mapNotNull { offered ->
            val funding = IndustrialWasteV2PublicActionPolicy.bind(state, player, offered) as? ActivateAbility
                ?: return@mapNotNull null
            if (shape(funding) == shape(action)) return@mapNotNull null
            val fundingAbility = ability(state, funding) ?: return@mapNotNull null
            if (!fundingAbility.isManaAbility || offered.hasXCost) return@mapNotNull null
            val consumed = materials(funding).toSet() + if (sacrificesSelf(fundingAbility.cost)) setOf(funding.sourceId) else emptySet()
            if (consumed.any { it in reserved }) return@mapNotNull null
            // Tapping a material before sacrificing it is permitted; consuming it twice is not.
            // A selected activation's own tap is also reserved for that selected activation.
            if (hasTap(fundingAbility.cost) && action is ActivateAbility && action.sourceId == funding.sourceId &&
                ability(state, action)?.let { hasTap(it.cost) } == true) return@mapNotNull null
            val fundingCost = offered.manaCostString?.let(ManaCost::parse) ?: ManaCost.ZERO
            val paidPool = pool.pay(fundingCost) ?: return@mapNotNull null
            val fundedPool = fixedOutput(fundingAbility, funding, paidPool) ?: return@mapNotNull null
            var augmented = state.updateEntity(player) { entity -> entity.with(fundedPool.component()) }
            if (hasTap(fundingAbility.cost)) augmented = augmented.updateEntity(funding.sourceId) { it.with(TappedComponent) }
            // Funding must help this exact selected cost, including its colored requirement.
            // It never becomes a speculative mana activation or a claim that colorless pays a pip.
            if (!autoPayable(augmented, player, action, cost, consumed) &&
                pool(augmented, player).payPartial(cost).remainingCost.cmc >= remaining) return@mapNotNull null
            Triple(IndustrialWasteV2PublicActionPolicy.score(state, player, offered, funding), funding, offered)
        }
        val funding = candidates.sortedWith(compareByDescending<Triple<Int, ActivateAbility, LegalAction>> { it.first }
            .thenBy { it.second.toString() }).firstOrNull()?.second
            ?: error("Selected action requires explicit mana but no qualified nonconflicting funding action exists")
        record(IndustrialWasteV2PaymentIntentRecord("FUNDING_ACTION", intent, funding))
        return funding
    }

    private fun autoPayable(state: GameState, player: EntityId, action: GameAction, cost: ManaCost,
                            additionallyExcluded: Set<EntityId> = emptySet()): Boolean {
        val remaining = pool(state, player).payPartial(cost).remainingCost
        if (remaining.isEmpty()) return true
        // Cast execution pays these sacrifices before its ordinary mana payment. They cannot
        // simultaneously appear in the automatic source witness; float their mana explicitly first.
        val excluded = materials(action).toSet() + additionallyExcluded + if (action is ActivateAbility) {
            val ability = ability(state, action) ?: return false
            if (hasTap(ability.cost) || sacrificesSelf(ability.cost)) setOf(action.sourceId) else emptySet()
        } else emptySet()
        val solution = solver.solve(state, player, remaining, excludeSources = excluded) ?: return false
        // The received canonical solver proves paid-filter costs in order. It still excludes
        // explicit sacrifices and the final action's reserved resources from automatic payment.
        return true
    }

    private fun ability(state: GameState, action: ActivateAbility): ActivatedAbility? {
        val card = state.getEntity(action.sourceId)?.get<CardComponent>() ?: return null
        return registry.getCard(card.cardDefinitionId)?.script?.activatedAbilities?.firstOrNull { it.id == action.abilityId }
            ?: state.grantedActivatedAbilities.firstOrNull { it.entityId == action.sourceId && it.ability.id == action.abilityId }?.ability
            ?: IntrinsicManaAbilities.forEntity(state, state.projectedState, action.sourceId).firstOrNull { it.id == action.abilityId }
    }

    private fun fixedOutput(ability: ActivatedAbility, action: ActivateAbility, pool: ManaPool): ManaPool? = when (val effect = ability.effect) {
        is AddColorlessManaEffect -> (effect.amount as? DynamicAmount.Fixed)?.amount
            ?.takeIf { it > 0 && effect.restriction == null }?.let(pool::addColorless)
        is AddManaEffect -> (effect.amount as? DynamicAmount.Fixed)?.amount
            ?.takeIf { it > 0 && effect.restriction == null && effect.riders.isEmpty() }?.let { pool.add(effect.color, it) }
        is AddManaOfChoiceEffect -> (effect.amount as? DynamicAmount.Fixed)?.amount
            ?.takeIf { it > 0 && effect.restriction == null && effect.riders.isEmpty() && action.manaColorChoice != null }
            ?.let { pool.add(requireNotNull(action.manaColorChoice), it) }
        else -> null
    }

    private fun ManaPool.component() = ManaPoolComponent(white, blue, black, red, green, colorless)

    private fun pool(state: GameState, player: EntityId): ManaPool {
        val p = requireNotNull(state.getEntity(player)?.get<ManaPoolComponent>())
        check(p.restrictedMana.isEmpty()) { "Restricted mana is outside the four frozen R1 lists and passive fixture" }
        return ManaPool(p.white, p.blue, p.black, p.red, p.green, p.colorless)
    }

    private fun source(action: GameAction): EntityId? = when (action) {
        is ActivateAbility -> action.sourceId
        is CastSpell -> action.cardId
        else -> null
    }

    private fun materials(action: GameAction): List<EntityId> = when (action) {
        is ActivateAbility -> action.costPayment?.sacrificedPermanents.orEmpty()
        is CastSpell -> action.additionalCostPayment?.sacrificedPermanents.orEmpty()
        else -> emptyList()
    }

    private fun targets(action: GameAction): List<EntityId> = (when (action) {
        is ActivateAbility -> action.targets
        is CastSpell -> action.targets
        else -> emptyList()
    }).map { target -> when (target) {
        is ChosenTarget.Card -> target.cardId
        is ChosenTarget.Permanent -> target.entityId
        is ChosenTarget.Player -> target.playerId
        is ChosenTarget.Spell -> target.spellEntityId
    } }

    private fun shape(action: GameAction): GameAction = when (action) {
        is ActivateAbility -> action.copy(targets = emptyList(), costPayment = null, manaColorChoice = null)
        is CastSpell -> action.copy(targets = emptyList(), additionalCostPayment = null)
        else -> action
    }

    private fun hasTap(cost: AbilityCost): Boolean = cost is AbilityCost.Tap ||
        cost is AbilityCost.Composite && cost.costs.any(::hasTap)

    private fun sacrificesSelf(cost: AbilityCost): Boolean = cost is AbilityCost.SacrificeSelf ||
        cost is AbilityCost.Composite && cost.costs.any(::sacrificesSelf)
}
