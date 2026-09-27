package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment

/** Materializes explicitly chosen domains. It never chooses a target, fodder, consent or fallback. */
object ActorChoiceSupport {
    fun targetId(target: ChosenTarget): EntityId = when (target) {
        is ChosenTarget.Player -> target.playerId
        is ChosenTarget.Permanent -> target.entityId
        is ChosenTarget.Card -> target.cardId
        is ChosenTarget.Spell -> target.spellEntityId
    }

    fun proposal(input: ActorInput, action: GameAction): ActorProposal {
        check(action.playerId == input.actorId) { "Pilot action belongs to another actor" }
        return ActorProposal(input.bindingHash, action, input.policyRngState)
    }

    fun chosenTarget(input: ActorInput, id: EntityId): ChosenTarget {
        if (input.observation.players.any { it.id == id }) return ChosenTarget.Player(id)
        if (input.observation.stack.any { it.view.entityId == id }) return ChosenTarget.Spell(id)
        val card = ActorPublicCards(input).card(id)
        return when (card.zone) {
            Zone.BATTLEFIELD -> ChosenTarget.Permanent(id)
            Zone.STACK -> ChosenTarget.Spell(id)
            else -> ChosenTarget.Card(id, card.ownerId
                ?: throw UnsupportedPolicyInput("Visible off-battlefield target lacks owner"), card.zone)
        }
    }

    fun materialize(
        input: ActorInput,
        option: ActorLegalAction,
        targets: Map<Int, List<EntityId>> = emptyMap(),
        costs: AdditionalCostPayment? = null,
        xValue: Int? = null,
        manaColor: Color? = null,
        repeatCount: Int? = null,
        damageDistribution: Map<EntityId, Int>? = null,
        chosenModes: List<Int>? = null,
        modeTargets: Map<Int, Map<Int, List<EntityId>>> = emptyMap(),
    ): GameAction {
        require(option in input.legalActions) { "Action template is absent from this actor input" }
        require(option.affordable && option.action.playerId == input.actorId)
        require(!option.hasConvoke && !option.hasDelve && !option.hasHarmonize && !option.hasTapForGeneric &&
            !option.tapForPower && !option.requiresForage) { "Unsupported alternative payment domain" }
        val modal = option.modalEnumeration
        val modeBindings = if (modal == null) {
            require(chosenModes == null && modeTargets.isEmpty())
            emptyList()
        } else {
            val modes = requireNotNull(chosenModes) { "Modal action requires an explicit mode selection" }
            require(modes.size in modal.minChooseCount..modal.chooseCount && modes.distinct().size == modes.size)
            require(modal.additionalCostPerExtraMode == null && modal.additionalManaCostPerExtraMode == null) {
                "Modal extra payment needs its own explicit qualification"
            }
            require(modeTargets.keys.all { it in modes })
            modes.map { index ->
                val mode = modal.modes.single { it.index == index }
                require(mode.available && index !in modal.unavailableIndices)
                require(mode.additionalCostInfo == null && mode.additionalManaCost == null) {
                    "Mode-specific payment requires an explicit selection"
                }
                selectedTargets(input, option.copy(targetRequirements = mode.targetRequirements,
                    requiresTargets = mode.targetRequirements.isNotEmpty()), modeTargets[index].orEmpty(), xValue)
            }
        }
        if (option.hasXCost) {
            require(xValue != null && xValue >= option.minX && xValue <= requireNotNull(option.maxAffordableX))
        } else require(xValue == null || xValue == 0) { "X chosen for an action without X" }
        if (option.requiresManaColorChoice) require(manaColor in option.availableManaColors.orEmpty())
        else require(manaColor == null)
        val count = repeatCount ?: 1
        require(count >= 1 && (count == 1 || count <= (option.maxRepeatableActivations ?: 1)))
        validateCost(option, costs, count)
        val selected = selectedTargets(input, option, targets, xValue)
        // Some complete engine templates already bind their only legal player/self target and
        // therefore expose no further target choice. Preserve that binding instead of erasing it.
        val hasTargetChoice = option.requiresTargets || !option.targetRequirements.isNullOrEmpty()
        val chosen = if (hasTargetChoice) selected else when (val template = option.action) {
            is CastSpell -> template.targets
            is ActivateAbility -> template.targets
            else -> selected
        }
        if (option.requiresDamageDistribution) {
            val distribution = requireNotNull(damageDistribution)
            require(distribution.keys == targets.values.flatten().toSet())
            require(distribution.values.sum() == option.totalDamageToDistribute)
            require(distribution.values.all { it >= (option.minDamagePerTarget ?: 0) })
        } else require(damageDistribution == null)
        return when (val template = option.action) {
            is CastSpell -> template.copy(targets = chosen + modeBindings.flatten(), additionalCostPayment = costs,
                xValue = xValue ?: template.xValue, damageDistribution = damageDistribution,
                chosenModes = chosenModes ?: template.chosenModes,
                modalSelectionCompleted = if (modal != null) true else template.modalSelectionCompleted,
                modeTargetsOrdered = if (modal != null) modeBindings else template.modeTargetsOrdered)
            is ActivateAbility -> template.copy(targets = chosen, costPayment = costs,
                xValue = xValue ?: template.xValue, manaColorChoice = manaColor ?: template.manaColorChoice,
                repeatCount = count, damageDistribution = damageDistribution)
            else -> {
                require(targets.isEmpty() && (costs == null || costs.isEmpty) && xValue == null &&
                    manaColor == null && count == 1 && damageDistribution == null && chosenModes == null && modeTargets.isEmpty())
                template
            }
        }
    }

    private fun selectedTargets(input: ActorInput, option: ActorLegalAction,
                                selected: Map<Int, List<EntityId>>, x: Int?): List<ChosenTarget> {
        val requirements = option.targetRequirements ?: if (option.requiresTargets) listOf(
            ActorTargetInfo(0, option.targetDescription ?: "Target", option.minTargets, option.targetCount,
                option.validTargets ?: throw UnsupportedPolicyInput("Targeted action lacks target domain"),
                xConstrainsManaValue = option.xConstrainsTargetManaValue,
                xConstrainsManaValueExactly = option.xConstrainsTargetManaValueExactly,
                xConstrainsPower = option.xConstrainsTargetPower,
                xConstrainsCount = option.xConstrainsTargetCount)) else emptyList()
        require(selected.keys.all { key -> requirements.any { it.index == key } })
        val cards = ActorPublicCards(input)
        val used = mutableSetOf<EntityId>()
        return requirements.sortedBy { it.index }.flatMap { requirement ->
            val ids = selected[requirement.index].orEmpty()
            val maximum = if (requirement.xConstrainsCount) requireNotNull(x) else requirement.maxTargets
            require(ids.size in requirement.minTargets..maximum && ids.distinct().size == ids.size)
            require(ids.all { it in requirement.validTargets }) { "Selected target outside declared domain" }
            if (requirement.mustDifferFromEarlier) require(ids.none { it in used })
            ids.forEach { id ->
                if (requirement.xConstrainsManaValue) require(cards.card(id).manaValue <= requireNotNull(x))
                if (requirement.xConstrainsManaValueExactly) require(cards.card(id).manaValue == requireNotNull(x))
                if (requirement.xConstrainsPower) require((cards.card(id).power ?: 0) <= requireNotNull(x))
            }
            used.addAll(ids)
            ids.map { chosenTarget(input, it) }
        }
    }

    private fun validateCost(option: ActorLegalAction, payment: AdditionalCostPayment?, repeats: Int) {
        val info = option.additionalCostInfo
        val p = payment ?: AdditionalCostPayment.NONE
        if (info == null) {
            require(p.isEmpty) { "Undeclared additional cost payment" }
            return
        }
        require(info.costType in setOf("SacrificePermanent", "SacrificeForCostReduction", "SacrificeSelf",
            "DiscardCard", "BouncePermanent", "TapPermanents", "ExileFromGraveyard", "Behold", "RevealCard", "PayLife")) {
            "Unsupported additional cost domain: ${info.costType}"
        }
        fun exact(values: List<EntityId>, domain: List<EntityId>, number: Int) {
            require(values.size == number && values.distinct().size == values.size && values.all { it in domain }) {
                "Additional cost selection does not match its explicit domain/count"
            }
        }
        exact(p.sacrificedPermanents, info.validSacrificeTargets,
            if (info.costType == "SacrificeSelf") 0 else if (info.validSacrificeTargets.isNotEmpty()) info.sacrificeCount * repeats else 0)
        exact(p.discardedCards, info.validDiscardTargets, info.discardCount * repeats)
        exact(p.bouncedPermanents, info.validBounceTargets, info.bounceCount * repeats)
        exact(p.tappedPermanents, info.validTapTargets, info.tapCount * repeats)
        exact(p.beheldCards, info.validBeholdTargets, info.beholdCount * repeats)
        exact(p.revealedCards, info.validRevealTargets, info.revealCount * repeats)
        require(p.exiledCards.distinct().size == p.exiledCards.size && p.exiledCards.all { it in info.validExileTargets })
        require(p.exiledCards.size in info.exileMinCount * repeats..info.exileMaxCount * repeats)
        require(p.variableCostPermanents.isEmpty() && p.blightTargets.isEmpty() && p.blightAmount == 0 &&
            p.payXLifeAmount == 0 && p.distributedCounterRemovals.isEmpty()) { "Unsupported additional cost channel" }
        require(p.lifePaid == option.additionalLifeCost) { "Life payment differs from enumerated cost" }
        // A single object may not leave its zone twice while paying a composite cost.
        val departed = p.sacrificedPermanents + p.discardedCards + p.bouncedPermanents + p.exiledCards
        require(departed.distinct().size == departed.size) { "One object assigned to multiple departure costs" }
    }

    /** Typed choices are deliberate input, not a universal Yes/first-option pilot. */
    fun submit(input: ActorInput, response: DecisionResponse): SubmitDecision {
        val q = input.decision ?: throw UnsupportedPolicyInput("No decision is pending")
        require(q.playerId == input.actorId && response.decisionId == q.id)
        val validShape = when (q) {
            is ChooseTargetsDecision -> response is TargetsResponse
            is SelectCardsDecision, is SearchLibraryDecision -> response is CardsSelectedResponse
            is YesNoDecision -> response is YesNoResponse
            is BatchYesNoDecision -> response is BatchYesNoResponse
            is ChooseModeDecision -> response is ModesChosenResponse
            is ChooseColorDecision -> response is ColorChosenResponse
            is ChooseNumberDecision -> response is NumberChosenResponse
            is DistributeDecision -> response is DistributionResponse
            is OrderObjectsDecision, is ReorderLibraryDecision -> response is OrderedResponse
            is SplitPilesDecision -> response is PilesSplitResponse
            is ChooseOptionDecision -> response is OptionChosenResponse
            is ChooseReplacementDecision -> response is ReplacementChosenResponse
            is AssignDamageDecision -> response is DamageAssignmentResponse
            is SelectManaSourcesDecision -> response is ManaSourcesSelectedResponse
            is BudgetModalDecision -> response is BudgetModalResponse
            is CombatResolutionDecision -> response is CombatResolutionResponse
        }
        require(validShape) { "Response does not match the pending typed question" }
        validateResponse(input, q, response)
        return SubmitDecision(input.actorId, response)
    }

    private fun validateResponse(input: ActorInput, q: PendingDecision, r: DecisionResponse) {
        fun distinctIn(ids: List<EntityId>, domain: Collection<EntityId>, minimum: Int, maximum: Int) {
            require(ids.distinct().size == ids.size && ids.size in minimum..maximum && ids.all { it in domain }) {
                "Response is outside the explicit choice domain"
            }
        }
        val cards = ActorPublicCards(input)
        when (q) {
            is ChooseTargetsDecision -> {
                val chosen = (r as TargetsResponse).selectedTargets
                require(chosen.keys.all { key -> q.targetRequirements.any { it.index == key } })
                for (target in q.targetRequirements) {
                    val ids = chosen[target.index].orEmpty()
                    distinctIn(ids, requireNotNull(q.legalTargets[target.index]), target.minTargets, target.maxTargets)
                    if (target.sameOwner) require(ids.map { cards.card(it).ownerId }.distinct().size <= 1)
                    if (target.differentNames) require(ids.map { cards.card(it).name }.distinct().size == ids.size)
                    if (target.differentControllers) require(ids.map { cards.card(it).controllerId }.distinct().size == ids.size)
                    target.totalManaValueAtMost?.let { limit -> require(ids.sumOf { cards.card(it).manaValue } <= limit) }
                }
            }
            is SelectCardsDecision -> {
                val ids = (r as CardsSelectedResponse).selectedCards
                require(q.conditionalMinimums.isEmpty() && !q.onePerCardType && !q.onePerColor &&
                    !q.onePerBasicLandType && !q.onePerPower) { "Selection constraint needs explicit policy qualification" }
                distinctIn(ids, q.options - q.nonSelectableOptions.toSet(), q.minSelections, q.maxSelections)
                if (q.onePerCardName) require(ids.map { cards.card(it).name }.distinct().size == ids.size)
                q.maxTotalManaValue?.let { require(ids.sumOf { cards.card(it).manaValue } <= it) }
                q.minTotalManaValue?.let { require(ids.sumOf { cards.card(it).manaValue } >= it) }
                q.maxTotalPower?.let { require(ids.sumOf { cards.card(it).power ?: 0 } <= it) }
            }
            is SearchLibraryDecision -> distinctIn((r as CardsSelectedResponse).selectedCards,
                q.options, q.minSelections, q.maxSelections)
            is ChooseOptionDecision -> require((r as OptionChosenResponse).optionIndex in q.options.indices)
            is ChooseModeDecision -> {
                val modes = (r as ModesChosenResponse).selectedModes
                require(modes.size in q.minModes..q.maxModes && modes.distinct().size == modes.size &&
                    modes.all { index -> q.modes.any { it.index == index && it.available } })
            }
            is ChooseColorDecision -> require((r as ColorChosenResponse).color in q.availableColors)
            is ChooseNumberDecision -> require((r as NumberChosenResponse).number in q.minValue..q.maxValue)
            is OrderObjectsDecision -> distinctIn((r as OrderedResponse).orderedObjects, q.objects, q.objects.size, q.objects.size)
            is ReorderLibraryDecision -> distinctIn((r as OrderedResponse).orderedObjects, q.cards, q.cards.size, q.cards.size)
            is SelectManaSourcesDecision -> {
                val answer = r as ManaSourcesSelectedResponse
                require(!answer.declined || q.canDecline)
                distinctIn(answer.selectedSources, q.availableSources.map { it.entityId }, 0, q.availableSources.size)
                require(answer.waterbendPermanents.all { id -> q.waterbendPermanents.any { it.entityId == id } })
                require(!answer.declined || (!answer.autoPay && answer.selectedSources.isEmpty() && answer.waterbendPermanents.isEmpty()))
            }
            is AssignDamageDecision -> {
                val answer = (r as DamageAssignmentResponse).assignments
                val domain = q.orderedTargets + listOfNotNull(q.defenderId)
                require(answer.keys.all { it in domain } && answer.values.all { it >= 0 })
                require(answer.values.sum() == q.availablePower)
                require(q.minimumAssignments.all { (id, minimum) -> (answer[id] ?: 0) >= minimum })
            }
            is CombatResolutionDecision -> {
                val answer = r as CombatResolutionResponse
                val editable = q.edges.filter { it.editableBy == input.actorId }
                require(answer.edges.map { it.edgeId }.distinct().size == answer.edges.size)
                require(answer.edges.map { it.edgeId }.toSet() == editable.map { it.id }.toSet())
                require(answer.edges.all { selected -> editable.any { it.id == selected.edgeId && selected.amount in 0..it.maximum } })
            }
            is DistributeDecision -> {
                val answer = (r as DistributionResponse).distribution
                require(answer.keys.all { it in q.targets } && answer.all { (id, amount) -> amount >= q.minPerTarget &&
                    (q.maxPerTarget[id] == null || amount <= requireNotNull(q.maxPerTarget[id])) })
                require(if (q.allowPartial) answer.values.sum() <= q.totalAmount else answer.values.sum() == q.totalAmount)
            }
            is SplitPilesDecision -> {
                val piles = (r as PilesSplitResponse).piles
                require(piles.size == q.numberOfPiles)
                distinctIn(piles.flatten(), q.cards, q.cards.size, q.cards.size)
            }
            is ChooseReplacementDecision -> {
                val answer = r as ReplacementChosenResponse
                require(answer.fromIndex in q.fromOptions.indices && answer.toIndex in q.toOptions.indices)
                require(q.allowedToByFrom.isEmpty() || q.allowedToByFrom.getOrNull(answer.fromIndex)?.contains(answer.toIndex) == true)
            }
            is BudgetModalDecision -> {
                val answer = (r as BudgetModalResponse).selectedModeIndices
                require(answer.all { it in q.modes.indices } && answer.sumOf { q.modes[it].cost } <= q.budget)
            }
            is YesNoDecision, is BatchYesNoDecision -> Unit
        }
    }
}
