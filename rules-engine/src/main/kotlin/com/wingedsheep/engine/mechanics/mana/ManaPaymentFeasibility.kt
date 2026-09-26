package com.wingedsheep.engine.mechanics.mana

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.utils.CastPermissionUtils
import com.wingedsheep.engine.mechanics.SummoningSicknessRules
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.ClassLevelComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.TextReplacementComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.ManaSymbol
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.conditions.AllConditions
import com.wingedsheep.sdk.scripting.conditions.AnyCondition
import com.wingedsheep.sdk.scripting.conditions.Condition
import com.wingedsheep.sdk.scripting.conditions.Exists
import com.wingedsheep.sdk.scripting.conditions.NotCondition
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import com.wingedsheep.sdk.scripting.effects.AddManaOfChoiceEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.Gate
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.ManaColorSet

/**
 * A fixed, already calculated payment. This query does not choose a spell, targets or a policy line.
 *
 * [excludedManaEntities] cannot activate OR be consumed by another mana ability. In contrast,
 * [boundFinalSacrifices] reserves a nonmana material: it may first tap for mana but cannot be spent
 * as mana-ability fodder. Null enumerates every legal payment of [finalSacrificeCost]; a non-null
 * list preserves the caller's already selected material exactly. Only a single fixed-count
 * sacrifice atom is currently supported; other nonmana costs must be checked by their own engine
 * validator before this query. No library, draw or future trigger resolution is a resource.
 */
data class ManaPaymentRequest(
    val cost: ManaCost,
    val context: SpellPaymentContext? = null,
    val excludedManaEntities: Set<EntityId> = emptySet(),
    val finalSacrificeCost: CostAtom.Sacrifice? = null,
    val boundFinalSacrifices: List<EntityId>? = null,
    val costSourceId: EntityId? = null,
    val maxSearchStates: Int = 100_000,
    /**
     * Optional execution-policy constraint for a funding ability's one-permanent sacrifice.
     * The caller receives only legal material IDs and must select one of them, or decline.
     * No callback is supplied for exhaustive menu/metric queries. A constrained search without
     * a witness returns Unsupported; it can never prove general resource impossibility.
     */
    val selectFundingSacrifice: ((ManaFundingMaterialOptions) -> EntityId?)? = null,
) {
    init { require(maxSearchStates > 0) }
}

/** Public identity-only choice boundary; no hidden state or tactical scoring belongs to this API. */
data class ManaFundingMaterialOptions(
    val sourceId: EntityId,
    val abilityId: AbilityId,
    val eligibleMaterials: List<EntityId>,
)

/** Pool vectors omit fungible provenance tags; actual execution retains those through the handler. */
data class ManaFundingStep(
    val action: ActivateAbility,
    val poolBefore: ManaPool,
    val poolAfter: ManaPool,
)

sealed interface ManaPaymentFeasibilityResult {
    data class Payable(
        val funding: List<ManaFundingStep>,
        val finalSacrifices: List<EntityId>,
        val poolBeforeFinalPayment: ManaPool,
        val poolAfterFinalPayment: ManaPool,
        val statesExamined: Int,
    ) : ManaPaymentFeasibilityResult

    /** Every supported resource order, production color, generic spend and final material failed. */
    data class Impossible(val statesExamined: Int) : ManaPaymentFeasibilityResult

    /** A search cap, unknown mechanic or an unexpressible witness must never become a negative. */
    data class Unsupported(val reasons: Set<String>, val statesExamined: Int) : ManaPaymentFeasibilityResult
}

/**
 * Read-only, finite resource search for fixed mana payments. Unlike auto-pay, an explicit witness
 * may sacrifice permanents. Nothing in this class submits those actions or makes that choice for
 * a player. Every accepted step names the real ability and uses FromPool, with its activation
 * funding spent BEFORE its output appears.
 *
 * Supported grammar: ordinary fixed colored/colorless mana; unrestricted any-color production;
 * tap, self-sacrifice, or one matching permanent sacrifice; optional generic activation mana;
 * and conditions over public battlefield types/names (including the Tron conjunction). Each
 * activation must consume a tap or sacrifice resource, so no branch can loop indefinitely.
 * Continuous/replacement modifiers, restricted floating mana and other grammar return UNSUPPORTED.
 * The same query can therefore be used for an exact colored cost and its all-generic equivalent
 * without summing competing sacrifice resources independently.
 */
class ManaPaymentFeasibility(private val registry: CardRegistry) {
    private val predicates = PredicateEvaluator()
    private val conditions = ConditionEvaluator()
    private val permissions = CastPermissionUtils(registry, predicates, conditions)

    private data class CostShape(val generic: Int, val taps: Boolean, val sacrificesSelf: Boolean,
        val sacrifice: CostAtom.Sacrifice?)
    private data class Production(val color: Color?, val amount: Int)
    private data class Mode(val source: EntityId, val ability: ActivatedAbility, val cost: CostShape)
    private data class NodeKey(val tapped: Set<EntityId>, val consumed: Set<EntityId>,
        val pool: ManaPool, val expressible: Boolean)

    fun assess(state: GameState, playerId: EntityId, request: ManaPaymentRequest): ManaPaymentFeasibilityResult {
        val reasons = linkedSetOf<String>()
        if (!fixedCost(request.cost)) reasons += "payment has a variable, hybrid or life-paid symbol"
        val finalCost = request.finalSacrificeCost
        if (finalCost != null && (finalCost.count != 1 || finalCost.distinctNames || !publicFilter(finalCost.filter))) {
            reasons += "final sacrifice is outside the single public permanent grammar"
        }
        val initialComponent = state.getEntity(playerId)?.get<ManaPoolComponent>()
            ?: return ManaPaymentFeasibilityResult.Unsupported(setOf("payer has no mana pool"), 0)
        if (initialComponent.restrictedMana.isNotEmpty()) reasons += "restricted floating mana"
        if (reasons.isNotEmpty()) return ManaPaymentFeasibilityResult.Unsupported(reasons, 0)
        val initialPool = ManaPool(initialComponent.white, initialComponent.blue, initialComponent.black,
            initialComponent.red, initialComponent.green, initialComponent.colorless)
        val initialBattlefield = state.getBattlefield().toSet()
        val finalOptions = when {
            finalCost == null -> if (request.boundFinalSacrifices.isNullOrEmpty()) listOf(emptyList()) else emptyList()
            else -> {
                val legal = matching(state, playerId, finalCost.filter, request.costSourceId,
                    if (finalCost.excludeSelf) request.costSourceId else null)
                request.boundFinalSacrifices?.let { bound ->
                    if (bound.size == 1 && bound.single() in legal) listOf(bound) else emptyList()
                } ?: legal.map { listOf(it) }
            }
        }
        if (finalOptions.isEmpty()) return if (request.selectFundingSacrifice == null)
            ManaPaymentFeasibilityResult.Impossible(0)
        else ManaPaymentFeasibilityResult.Unsupported(setOf("no legal final material in a policy-constrained query"), 0)
        // A fully floated payment needs no activation semantics, even beside an unused capability.
        initialPool.pay(request.cost, request.context)?.let { after ->
            return ManaPaymentFeasibilityResult.Payable(emptyList(), finalOptions.first(), initialPool, after, 0)
        }
        if (state.floatingEffects.isNotEmpty() || state.grantedStaticAbilities.isNotEmpty() ||
            state.grantedReplacementEffects.isNotEmpty()) {
            return ManaPaymentFeasibilityResult.Unsupported(setOf("continuous or replacement modifier surface"), 0)
        }
        for (id in initialBattlefield) {
            val container = state.getEntity(id) ?: continue
            if (container.has<FaceDownComponent>()) continue
            val card = container.get<CardComponent>() ?: continue
            val definition = registry.getCard(card.cardDefinitionId)
            if (definition == null) {
                // Generic CreateToken uses TokenComponent plus runtime grants (possibly none).
                // Its absent registry entry is not evidence of a missing printed mana ability.
                if (!container.has<TokenComponent>()) return ManaPaymentFeasibilityResult.Unsupported(
                    setOf("unregistered non-token public permanent $id"), 0)
            // EntersTapped is a self-entry replacement, already consumed by a battlefield
            // permanent. This search never creates a permanent, so it cannot alter payment.
            } else if (definition.script.effectiveStaticAbilities().isNotEmpty() ||
                definition.script.replacementEffects.any { it !is EntersTapped }) {
                return ManaPaymentFeasibilityResult.Unsupported(setOf("battlefield static or replacement modifier $id"), 0)
            }
        }
        var examined = 0
        var capped = false
        var unexpressible = false

        for (reservedList in finalOptions) {
            val reserved = reservedList.toSet()
            val seen = hashSetOf<NodeKey>()
            fun search(board: GameState, pool: ManaPool, consumed: Set<EntityId>,
                path: List<ManaFundingStep>, expressible: Boolean): ManaPaymentFeasibilityResult.Payable? {
                val tapped = board.getBattlefield().filterTo(hashSetOf()) {
                    board.getEntity(it)?.has<TappedComponent>() == true
                }
                if (!seen.add(NodeKey(tapped, consumed, pool, expressible))) return null
                if (++examined > request.maxSearchStates) { capped = true; return null }
                val remaining = pool.pay(request.cost, request.context)
                if (remaining != null) {
                    if (expressible) return ManaPaymentFeasibilityResult.Payable(path, reservedList, pool, remaining, examined)
                    unexpressible = true
                    return null
                }
                val modes = modes(board, playerId, request.excludedManaEntities, reasons)
                if (!optimisticBoundCovers(board, playerId, pool, request.cost, modes, reserved,
                        request.excludedManaEntities, reasons)) return null
                for (mode in modes) {
                    val legalMaterials = materials(board, playerId, mode, reserved, request.excludedManaEntities)
                    if (legalMaterials.isEmpty()) continue
                    val selector = request.selectFundingSacrifice
                    val materials = if (selector != null && mode.cost.sacrifice != null) {
                        val options = legalMaterials.map { it.single() }
                        val selected = selector(ManaFundingMaterialOptions(mode.source, mode.ability.id, options))
                            ?: continue
                        if (selected !in options) {
                            reasons += "funding policy selected a material outside the current legal options"
                            continue
                        }
                        listOf(listOf(selected))
                    } else legalMaterials
                    val activationCost = ManaCost.parse("{${mode.cost.generic}}")
                    val canonicalPayment = pool.pay(activationCost)
                    if (canonicalPayment == null) continue
                    val payments = genericPayments(pool, mode.cost.generic, canonicalPayment)
                    for (material in materials) {
                        var afterCosts = board
                        if (mode.cost.taps) afterCosts = afterCosts.updateEntity(mode.source) { it.with(TappedComponent) }
                        material.forEach { afterCosts = afterCosts.removeEntity(it) }
                        val outputs = productions(afterCosts, playerId, mode.source, mode.ability.effect, reasons) ?: continue
                        for (paid in payments) for (output in outputs) {
                            val afterPool = if (output.color == null) paid.addColorless(output.amount)
                                else paid.add(output.color, output.amount)
                            val action = ActivateAbility(playerId, mode.source, mode.ability.id,
                                costPayment = material.takeIf { it.isNotEmpty() }?.let { AdditionalCostPayment(sacrificedPermanents = it) },
                                manaColorChoice = output.color,
                                paymentStrategy = PaymentStrategy.FromPool)
                            val step = ManaFundingStep(action, pool, afterPool)
                            search(afterCosts, afterPool, consumed + material, path + step,
                                expressible && paid == canonicalPayment)?.let { return it }
                            if (capped) return null
                        }
                    }
                }
                return null
            }
            search(state, initialPool, emptySet(), emptyList(), true)?.let { return it }
            if (capped) break
        }
        if (request.selectFundingSacrifice != null) {
            reasons += "policy-constrained funding search is not an exhaustive resource negative"
        }
        if (capped) reasons += "finite search state limit reached"
        if (unexpressible) reasons += "legal funding requires a pool-spend selection not expressible by FromPool"
        return if (reasons.isEmpty()) ManaPaymentFeasibilityResult.Impossible(examined)
            else ManaPaymentFeasibilityResult.Unsupported(reasons, examined)
    }

    private fun modes(state: GameState, payer: EntityId, excluded: Set<EntityId>,
        reasons: MutableSet<String>): List<Mode> = buildList {
        val projected = state.projectedState
        for (id in projected.getBattlefieldControlledBy(payer).sortedBy { it.value }) {
            if (id in excluded) continue
            val container = state.getEntity(id) ?: continue
            val card = container.get<CardComponent>() ?: continue
            if (permissions.isActivationPrevented(state, id, abilityIsManaAbility = true) ||
                permissions.isActivationPreventedForPlayer(state, id, payer)) continue
            val faceDown = container.has<FaceDownComponent>()
            val lost = projected.hasLostAllAbilities(id)
            val intrinsic = if (faceDown) emptyList() else IntrinsicManaAbilities.forEntity(state, projected, id)
            val own = when {
                faceDown -> emptyList()
                lost && projected.hasBasicLandTypesSetByEffect(id) -> intrinsic
                lost -> emptyList()
                intrinsic.isNotEmpty() -> intrinsic
                else -> registry.getCard(card.cardDefinitionId)?.script
                    ?.effectiveActivatedAbilities(container.get<ClassLevelComponent>()?.currentLevel).orEmpty()
            }
            val granted = state.grantedActivatedAbilities.filter { it.entityId == id }.map { it.ability }
            for (ability in (own + granted).filter { it.isManaAbility }.distinctBy { it.id }) {
                if (container.has<TextReplacementComponent>() || ability.targetRequirements.isNotEmpty() ||
                    ability.restrictions.isNotEmpty() || ability.trackActivations || ability.hasConvoke || ability.hasWaterbend ||
                    ability.genericCostReduction != null || ability.isPowerUp || ability.isExhaust || ability.isPlaneswalkerAbility ||
                    ability.xDefinedAs != null || ability.xManaRestriction.isNotEmpty() || ability.minimumXValue != 0 ||
                    ability.timing != TimingRule.ManaAbility ||
                    ability.activateFromZone != Zone.BATTLEFIELD) {
                    reasons += "mana activation metadata outside supported grammar: $id/${ability.id}"
                    continue
                }
                val cost = costShape(ability.cost)
                if (cost == null) reasons += "mana activation cost outside supported grammar: $id/${ability.id}"
                else add(Mode(id, ability, cost))
            }
        }
    }

    private fun costShape(cost: AbilityCost): CostShape? {
        var generic = 0
        var taps = false
        var self = false
        var sacrifice: CostAtom.Sacrifice? = null
        fun take(part: AbilityCost): Boolean = when (part) {
            AbilityCost.Free -> true
            AbilityCost.Tap -> if (taps) false else { taps = true; true }
            AbilityCost.SacrificeSelf -> if (self || sacrifice != null) false else { self = true; true }
            is AbilityCost.Composite -> part.costs.all(::take)
            is AbilityCost.Atom -> when (val atom = part.atom) {
                is CostAtom.Mana -> if (atom.cost.symbols.all { it is ManaSymbol.Generic }) {
                    generic += atom.cost.genericAmount; true
                } else false
                is CostAtom.Sacrifice -> if (self || sacrifice != null || atom.count != 1 || atom.distinctNames ||
                    !publicFilter(atom.filter)) false else { sacrifice = atom; true }
                else -> false
            }
            else -> false
        }
        return if (take(cost) && (taps || self || sacrifice != null)) CostShape(generic, taps, self, sacrifice) else null
    }

    private fun productions(state: GameState, payer: EntityId, source: EntityId, effect: Effect,
        reasons: MutableSet<String>, optimistic: Boolean = false): List<Production>? {
        fun fixed(amount: DynamicAmount): Int? = (amount as? DynamicAmount.Fixed)?.amount?.takeIf { it >= 0 }
        val result = when (effect) {
            is AddManaEffect -> fixed(effect.amount)?.takeIf { effect.restriction == null && effect.riders.isEmpty() }
                ?.let { listOf(Production(effect.color, it)) }
            is AddColorlessManaEffect -> fixed(effect.amount)?.takeIf { effect.restriction == null }
                ?.let { listOf(Production(null, it)) }
            is AddManaOfChoiceEffect -> fixed(effect.amount)?.takeIf { effect.restriction == null && effect.riders.isEmpty() &&
                effect.colorSet == ManaColorSet.AnyColor && effect.recipient == EffectTarget.Controller }
                ?.let { amount -> Color.entries.map { Production(it, amount) } }
            is GatedEffect -> {
                val gate = effect.gate as? Gate.WhenCondition
                if (gate == null || !publicCondition(gate.condition)) null
                else {
                    // Validate BOTH branches even if one is currently unselected; consuming a
                    // material can change a later public battlefield condition.
                    val yes = productions(state, payer, source, effect.then, reasons, optimistic)
                    val no = effect.otherwise?.let { productions(state, payer, source, it, reasons, optimistic) } ?: listOf(Production(null, 0))
                    if (yes == null || no == null) null
                    else if (optimistic) (yes + no).distinct()
                    else if (conditions.evaluate(state, gate.condition, EffectContext(source, payer))) yes else no
                }
            }
            is CompositeEffect -> if (effect.effects.size == 1)
                productions(state, payer, source, effect.effects.single(), reasons, optimistic) else null
            else -> null
        }
        if (result == null) reasons += "mana production outside supported grammar: $source"
        return result
    }

    private fun materials(state: GameState, payer: EntityId, mode: Mode, reserved: Set<EntityId>,
        excluded: Set<EntityId>): List<List<EntityId>> {
        val container = state.getEntity(mode.source) ?: return emptyList()
        if (mode.cost.taps && (container.has<TappedComponent>() ||
            (state.projectedState.isCreature(mode.source) && SummoningSicknessRules.blocksTapOrUntapCost(
                mode.source, container, state.projectedState)))) return emptyList()
        return when {
            mode.cost.sacrificesSelf -> if (mode.source in reserved) emptyList() else listOf(listOf(mode.source))
            mode.cost.sacrifice != null -> matching(state, payer, mode.cost.sacrifice.filter, mode.source,
                if (mode.cost.sacrifice.excludeSelf) mode.source else null)
                .filter { it !in reserved && it !in excluded }.map { listOf(it) }
            else -> listOf(emptyList())
        }
    }

    /**
     * A safe overestimate, never a positive payment proof. A resource may contribute its greatest
     * tap output AND its greatest sacrifice output (tap first, then sacrifice). Multiple Altars
     * never add multiple sacrifice contributions for the same material. Activation funding fees
     * are ignored and every conditional branch is considered, so this can only overestimate.
     * In particular, a large colorless-only creature board fails a black-pip query without exploring
     * all equivalent sacrifice subsets. Unknown grammar still records UNSUPPORTED in [reasons].
     */
    private fun optimisticBoundCovers(state: GameState, payer: EntityId, pool: ManaPool, cost: ManaCost,
        modes: List<Mode>, reserved: Set<EntityId>, excluded: Set<EntityId>, reasons: MutableSet<String>): Boolean {
        val taps = mutableMapOf<EntityId, MutableMap<Color?, Int>>()
        val sacrifices = mutableMapOf<EntityId, MutableMap<Color?, Int>>()
        fun record(map: MutableMap<EntityId, MutableMap<Color?, Int>>, id: EntityId, output: Production) {
            val entry = map.getOrPut(id) { mutableMapOf() }
            entry[output.color] = maxOf(entry[output.color] ?: 0, output.amount)
        }
        for (mode in modes) {
            val choices = materials(state, payer, mode, reserved, excluded)
            if (choices.isEmpty()) continue
            val outputs = productions(state, payer, mode.source, mode.ability.effect, reasons, optimistic = true) ?: continue
            for (output in outputs) {
                if (mode.cost.taps) record(taps, mode.source, output)
                choices.flatten().forEach { record(sacrifices, it, output) }
            }
        }
        val resources = taps.values + sacrifices.values
        if (pool.total + resources.sumOf { it.values.maxOrNull() ?: 0 } < cost.cmc) return false
        for (color in Color.entries) {
            val required = cost.symbols.filterIsInstance<ManaSymbol.Colored>().count { it.color == color }
            if (pool.get(color) + resources.sumOf { it[color] ?: 0 } < required) return false
        }
        val colorless = cost.symbols.count { it is ManaSymbol.Colorless }
        return pool.colorless + resources.sumOf { it[null] ?: 0 } >= colorless
    }

    private fun matching(state: GameState, payer: EntityId, filter: GameObjectFilter,
        source: EntityId?, excluded: EntityId?): List<EntityId> {
        val projected = state.projectedState
        return projected.getBattlefieldControlledBy(payer).filter { id -> id != excluded &&
            state.getEntity(id)?.get<CardComponent>() != null &&
            predicates.matches(state, projected, id, filter, PredicateContext(controllerId = payer, sourceId = source))
        }.sortedBy { it.value }
    }

    private fun fixedCost(cost: ManaCost): Boolean = cost.symbols.all {
        it is ManaSymbol.Generic || it is ManaSymbol.Colored || it is ManaSymbol.Colorless
    }
    private fun publicCondition(condition: Condition): Boolean = when (condition) {
        is Exists -> condition.zone == Zone.BATTLEFIELD && condition.player == Player.You && publicFilter(condition.filter)
        is AllConditions -> condition.conditions.all(::publicCondition)
        is AnyCondition -> condition.conditions.all(::publicCondition)
        is NotCondition -> publicCondition(condition.condition)
        else -> false
    }
    private fun publicFilter(filter: GameObjectFilter): Boolean = filter.statePredicates.isEmpty() &&
        filter.controllerPredicate == null && filter.cardPredicates.all(::publicPredicate) && filter.anyOf.all(::publicFilter)
    private fun publicPredicate(predicate: CardPredicate): Boolean = when (predicate) {
        CardPredicate.IsCreature, CardPredicate.IsLand, CardPredicate.IsArtifact, CardPredicate.IsEnchantment,
        CardPredicate.IsPlaneswalker, CardPredicate.IsPermanent, CardPredicate.IsBasicLand,
        CardPredicate.IsNoncreature, CardPredicate.IsNonland, CardPredicate.IsNonartifact -> true
        is CardPredicate.NameEquals, is CardPredicate.HasSubtype -> true
        is CardPredicate.And -> predicate.predicates.all(::publicPredicate)
        is CardPredicate.Or -> predicate.predicates.all(::publicPredicate)
        is CardPredicate.Not -> publicPredicate(predicate.predicate)
        else -> false
    }

    /** Enumerate every legal generic spend, retaining canonical FromPool first for real witnesses. */
    private fun genericPayments(pool: ManaPool, amount: Int, canonical: ManaPool): List<ManaPool> {
        val counts = intArrayOf(pool.colorless, pool.white, pool.blue, pool.black, pool.red, pool.green)
        val results = linkedSetOf(canonical)
        fun choose(index: Int, left: Int) {
            if (index == counts.size) {
                if (left == 0) results += ManaPool(counts[1], counts[2], counts[3], counts[4], counts[5], counts[0])
                return
            }
            val available = counts[index]
            for (used in 0..minOf(available, left)) {
                counts[index] = available - used
                choose(index + 1, left - used)
            }
            counts[index] = available
        }
        choose(0, amount)
        return results.toList()
    }
}
