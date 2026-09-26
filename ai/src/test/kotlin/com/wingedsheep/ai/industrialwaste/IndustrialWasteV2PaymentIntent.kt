package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.mana.IntrinsicManaAbilities
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibility
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibilityResult
import com.wingedsheep.engine.mechanics.mana.ManaPaymentRequest
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
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom
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
    private val feasibility = ManaPaymentFeasibility(registry)
    private var serial = 0
    private var pending: IndustrialWasteV2PaymentIntent? = null

    fun choose(state: GameState, player: EntityId, legal: List<LegalAction>): GameAction {
        val retained = pending
        val selected = if (retained == null) {
            IndustrialWasteV2PublicActionPolicy.chooseBound(state, player, legal) { entry, action ->
                policyPayable(state, player, entry, action, legal)
            }
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
        check(policyPayable(state, player, entry, action, legal)) {
            "Retained action lost its complete frozen-policy payment witness"
        }
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
            if (pool.pay(fundingCost) == null) return@mapNotNull null
            var witness: ManaPaymentFeasibilityResult.Payable? = null
            if (!policyPayable(state, player, entry, action, legal,
                    requiredFirstFunding = { proposed ->
                        // The canonical witness always spells out self-sacrifice, uses FromPool
                        // and records a fixed colored source's output color. Those execution
                        // details do not alter the existing offered action or its pilot choices.
                        proposed == funding.copy(paymentStrategy = PaymentStrategy.FromPool,
                            manaColorChoice = if (offered.requiresManaColorChoice) funding.manaColorChoice else proposed.manaColorChoice,
                            costPayment = if (sacrificesSelf(fundingAbility.cost))
                                AdditionalCostPayment(sacrificedPermanents = listOf(funding.sourceId)) else funding.costPayment)
                    },
                    captureWitness = { witness = it })) return@mapNotNull null
            val fundedPool = requireNotNull(witness).funding.firstOrNull()?.poolAfter
                ?: error("Explicit funding proof has no first action")
            var augmented = state.updateEntity(player) { entity -> entity.with(fundedPool.component()) }
            if (hasTap(fundingAbility.cost)) augmented = augmented.updateEntity(funding.sourceId) { it.with(TappedComponent) }
            // The complete suffix proof sees each already consumed resource as absent.
            consumed.forEach { augmented = augmented.removeEntity(it) }
            // The same canonical witness already certifies this first action and its complete
            // suffix, including public conditional production such as both Tron branches.
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

    /**
     * A complete fixed-resource proof for the already bound action under the same public material
     * and color policy. This does not remove anything from the engine menu or checkpoint metric.
     * Only a complete supported policy-constrained negative can decline a pilot binding; unknown
     * grammar, a search cap or an invalid policy answer remains a runtime qualification fault.
     */
    private fun policyPayable(state: GameState, player: EntityId, entry: LegalAction,
        action: GameAction, legal: List<LegalAction>,
        requiredFirstFunding: ((ActivateAbility) -> Boolean)? = null,
        captureWitness: ((ManaPaymentFeasibilityResult.Payable) -> Unit)? = null): Boolean {
        val costText = entry.manaCostString ?: return true
        check(!entry.hasXCost) { "Unqualified variable payment shape" }
        val cost = ManaCost.parse(costText)
        if (cost.isEmpty()) return true
        // The frozen lists have no mana-paid activation with a non-self sacrifice choice.
        // Altar's creature sacrifice costs zero mana; every paid sacrifice activation is self-only.
        check(action !is ActivateAbility || materials(action).isEmpty()) {
            "Unqualified paid activation with a bound non-self sacrifice"
        }
        val card = (action as? CastSpell)?.cardId?.let { state.getEntity(it)?.get<CardComponent>() }
        val definition = card?.let { registry.getCard(it.cardDefinitionId) }
        val additional = definition?.script?.additionalCosts.orEmpty() +
            if (action is CastSpell && action.alternativeCostType == AlternativeCostType.FLASHBACK)
                definition?.keywordAbilities?.filterIsInstance<KeywordAbility.Flashback>()
                    ?.singleOrNull()?.additionalCost?.let(::listOf).orEmpty()
            else emptyList()
        val sacrifice = if (additional.isEmpty()) null else {
            check(additional.size == 1) { "Unqualified combined additional-cost payment" }
            ((additional.single() as? AdditionalCost.Atom)?.atom as? CostAtom.Sacrifice)
                ?: error("Unqualified non-sacrifice additional-cost payment")
        }
        check(sacrifice != null || materials(action).isEmpty() || action is ActivateAbility) {
            "Bound sacrifice has no exact printed cost"
        }
        val excluded = targets(action).toSet() + if (action is ActivateAbility) {
            val selectedAbility = ability(state, action) ?: error("Selected activation lost its definition")
            if (hasTap(selectedAbility.cost) || sacrificesSelf(selectedAbility.cost)) setOf(action.sourceId)
            else emptySet()
        } else emptySet()
        fun offered(source: EntityId, abilityId: com.wingedsheep.sdk.scripting.AbilityId): LegalAction? =
            legal.firstOrNull { (it.action as? ActivateAbility)?.let { a ->
                a.sourceId == source && a.abilityId == abilityId
            } == true }
        val initialTapped = state.getBattlefield().filterTo(hashSetOf()) {
            state.getEntity(it)?.has<TappedComponent>() == true
        }
        val request = ManaPaymentRequest(cost, excludedManaEntities = excluded,
            finalSacrificeCost = sacrifice,
            boundFinalSacrifices = sacrifice?.let { materials(action) }, costSourceId = source(action),
            selectFundingSacrifice = { options ->
                offered(options.sourceId, options.abilityId)?.let { candidate ->
                    val info = candidate.additionalCostInfo
                    if (info == null) null else
                        (IndustrialWasteV2PublicActionPolicy.bind(state, player, candidate.copy(
                            additionalCostInfo = info.copy(validSacrificeTargets = options.eligibleMaterials)
                        )) as? ActivateAbility)?.costPayment?.sacrificedPermanents?.singleOrNull()
                }
            },
            allowFundingAction = { options ->
                // Every supported activation consumes a tap or sacrifice resource. Equal initial
                // tapped/consumed sets therefore identify only the first step of this proof.
                val firstAllowed = requiredFirstFunding == null || options.tappedSources != initialTapped ||
                    options.consumedMaterials.isNotEmpty() || requiredFirstFunding(options.action)
                // These are canonical proof resource facts, not a second payment simulation.
                // Once the unchanged automatic planner can pay, its choice of ordinary sources
                // and colors remains delegated. Only preceding explicit steps use pilot binding.
                var resources = state.updateEntity(player) { it.with(options.pool.component()) }
                options.tappedSources.forEach { id ->
                    resources = resources.updateEntity(id) { it.with(TappedComponent) }
                }
                options.consumedMaterials.forEach { resources = resources.removeEntity(it) }
                val funding = options.action
                if (!firstAllowed) false else if (autoPayable(resources, player, action, cost)) true else
                offered(funding.sourceId, funding.abilityId)?.let { candidate ->
                    val chosen = materials(funding)
                    val info = candidate.additionalCostInfo
                    val constrained = if (chosen.isNotEmpty() && info != null)
                        candidate.copy(additionalCostInfo = info.copy(validSacrificeTargets = chosen))
                    else candidate
                    val bound = IndustrialWasteV2PublicActionPolicy.bind(resources, player, constrained) as? ActivateAbility
                    bound != null && (!candidate.requiresManaColorChoice || bound.manaColorChoice == funding.manaColorChoice)
                } == true
            })
        return when (val result = feasibility.assess(state, player, request)) {
            is ManaPaymentFeasibilityResult.Payable -> { captureWitness?.invoke(result); true }
            is ManaPaymentFeasibilityResult.Impossible -> false
            is ManaPaymentFeasibilityResult.Unsupported -> {
                val policyOnly = setOf("policy-constrained funding search is not an exhaustive resource negative",
                    "no legal final material in a policy-constrained query")
                check(result.reasons.isNotEmpty() && result.reasons.all { it in policyOnly }) {
                    "Incomplete frozen-policy payment qualification: ${result.reasons}"
                }
                false
            }
        }
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
