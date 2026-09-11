package com.wingedsheep.ai.solitaire

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.isOpponentTo
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.AbilityCost

/**
 * Goal-directed controller for a no-interaction Project X goldfish.
 *
 * It is deliberately separate from [AIPlayer]: Project X wants the earliest provable terminal
 * state, while the match AI maximizes generic position value. Decisions unrelated to that goal are
 * delegated to the existing AI. The rules engine remains the authority for legal actions.
 */
class ProjectXSolitaireAgent(
    private val cardRegistry: CardRegistry,
    val playerId: EntityId,
    val analyzer: ProjectXStateAnalyzer = ProjectXStateAnalyzer(),
) {
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val simulator = GameSimulator(cardRegistry)
    private val fallback = AIPlayer.create(cardRegistry, playerId)

    fun outcome(state: GameState): ProjectXOutcome = analyzer.outcome(state, playerId)

    fun chooseAction(state: GameState): GameAction = chooseActionWithDiagnostics(state).action

    fun chooseActionWithDiagnostics(state: GameState): ProjectXActionChoice {
        val legal = enumerator.enumerate(state, playerId, EnumerationMode.ACTIONS_ONLY)
        if (legal.isEmpty()) return ProjectXActionChoice(PassPriority(playerId))

        val offered = legal.asSequence()
            .filter { it.affordable }
            .map { it to materialize(state, it) }
            .toList()
        val validations = offered.map { (offer, action) -> Triple(offer, action, simulator.validateSubmission(state, action)) }
        val rejected = validations.filter { !it.third.accepted }.map { (offer, action, validation) ->
            ProjectXRejectedSubmission(action, offer.description, validation.error ?: "executor rejected action")
        }
        val materialized = validations.filter { it.third.accepted }.map { it.first to it.second }
        val specialized = materialized.asSequence()
            .map { (legalAction, action) -> Triple(priority(state, legalAction, action), legalAction, action) }
            .filter { it.first > 0 }
            .maxWithOrNull(compareBy<Triple<Int, LegalAction, GameAction>> { it.first }
                .thenBy { it.second.description })

        if (specialized != null) return ProjectXActionChoice(specialized.third, rejected)

        // Once a sacrifice loop has been proven symbolically, executing it again cannot improve the
        // deterministic outcome. Keep developing or pass toward the next combat window instead of
        // burning thousands of identical engine transitions.
        val nonLoopActions = materialized.filterNot { (legalAction, action) ->
            redundantLoopIteration(state, action) ||
                purposelessCreatureManaActivation(state, legalAction, action)
        }
        val action = if (nonLoopActions.isNotEmpty()) {
            fallback.chooseFrom(state, nonLoopActions.map { it.first }).action
        } else {
            PassPriority(playerId)
        }
        return ProjectXActionChoice(action, rejected)
    }

    fun respondToDecision(state: GameState, decision: PendingDecision): DecisionResponse {
        val source = decision.context.sourceName
        return when {
            decision is SearchLibraryDecision && source == ProjectXStateAnalyzer.WIREWOOD_HERALD ->
                CardsSelectedResponse(
                    decision.id,
                    chooseHeraldTutor(state, decision.options) { decision.cards[it]?.name }
                        .take(decision.maxSelections),
                )

            decision is SelectCardsDecision && source == ProjectXStateAnalyzer.WIREWOOD_HERALD ->
                CardsSelectedResponse(
                    decision.id,
                    chooseHeraldTutor(state, decision.options) { id ->
                        analyzer.name(state, id) ?: decision.cardInfo?.get(id)?.name
                    }.take(decision.maxSelections),
                )

            decision is ChooseModeDecision && source == ProjectXStateAnalyzer.WINDING_WAY ->
                chooseWindingWayMode(state, decision)

            decision is ChooseOptionDecision && source == ProjectXStateAnalyzer.WINDING_WAY ->
                chooseWindingWayOption(state, decision)

            decision is SelectCardsDecision && source == ProjectXStateAnalyzer.LEAD_THE_STAMPEDE ->
                CardsSelectedResponse(decision.id, decision.options.filter { id ->
                    state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.isCreature == true
                }.take(decision.maxSelections))

            decision is ChooseColorDecision && source == ProjectXStateAnalyzer.BIRCHLORE_RANGERS ->
                ColorChosenResponse(decision.id, preferredManaColor(state, decision.availableColors))

            decision is ChooseTargetsDecision && source == ProjectXStateAnalyzer.IVY_LANE_DENIZEN ->
                chooseIvyTarget(state, decision)

            decision is ChooseTargetsDecision && source == ProjectXStateAnalyzer.EVOLUTION_WITNESS ->
                chooseWitnessTarget(state, decision)

            decision is ChooseTargetsDecision && source == ProjectXStateAnalyzer.QUIRION_RANGER ->
                chooseQuirionTarget(state, decision)

            decision is ChooseTargetsDecision && source == ProjectXStateAnalyzer.FALKENRATH_NOBLE ->
                chooseOpponentTarget(state, decision)

            decision is YesNoDecision && source in ALWAYS_ACCEPT_SOURCES ->
                YesNoResponse(decision.id, true)

            decision is BatchYesNoDecision && source in ALWAYS_ACCEPT_SOURCES ->
                BatchYesNoResponse(decision.id, choice = true, applyToAll = true)

            else -> fallback.respondToDecision(state, decision)
        }
    }

    private fun priority(state: GameState, legal: LegalAction, action: GameAction): Int {
        val missing = analyzer.missingPrimaryRoles(state, playerId)
        val battlefield = analyzer.battlefieldNames(state, playerId)
        return when (action) {
            is CastSpell -> castPriority(state, action.cardId)

            is ActivateAbility -> when (analyzer.name(state, action.sourceId)) {
                ProjectXStateAnalyzer.CARRION_FEEDER -> sacrificePriority(state, action, missing, battlefield)
                ProjectXStateAnalyzer.EVOLUTION_WITNESS ->
                    if (missing.any(analyzer.graveyardNames(state, playerId)::contains) && witnessCanAdapt(state, action.sourceId)) 7_200 else 0
                ProjectXStateAnalyzer.QUIRION_RANGER -> if (quirionUnlocksMana(state)) 6_800 else 0
                else -> creatureManaActivationPriority(state, legal, action)
            }

            is PlayLand -> 1_000 + landPriority(state, analyzer.name(state, action.cardId))
            else -> 0
        }
    }

    private fun sacrificePriority(
        state: GameState,
        action: ActivateAbility,
        missing: Set<String>,
        battlefield: Set<String>,
    ): Int {
        val victim = action.costPayment?.sacrificedPermanents?.singleOrNull()?.let { analyzer.name(state, it) }
        return when {
            outcome(state).arbitrarilyLargeCarrionFeeder -> 0
            victim == ProjectXStateAnalyzer.SAFEHOLD_ELITE && analyzer.primaryRoles.all(battlefield::contains) -> 9_500
            victim == ProjectXStateAnalyzer.WIREWOOD_HERALD && missing.size == 1 &&
                missing.single() in analyzer.libraryNames(state, playerId) -> 7_800
            else -> 0
        }
    }

    private fun redundantLoopIteration(state: GameState, action: GameAction): Boolean {
        if (!outcome(state).arbitrarilyLargeCarrionFeeder || action !is ActivateAbility) return false
        if (analyzer.name(state, action.sourceId) != ProjectXStateAnalyzer.CARRION_FEEDER) return false
        val victim = action.costPayment?.sacrificedPermanents?.singleOrNull() ?: return false
        return analyzer.name(state, victim) in setOf(
            ProjectXStateAnalyzer.SAFEHOLD_ELITE,
            ProjectXStateAnalyzer.WIREWOOD_HERALD,
            ProjectXStateAnalyzer.NETTLE_SENTINEL,
            ProjectXStateAnalyzer.BIRCHLORE_RANGERS,
            ProjectXStateAnalyzer.QUIRION_RANGER,
        )
    }

    private fun materialize(state: GameState, legal: LegalAction): GameAction {
        val action = legal.action
        if (action !is ActivateAbility) return action
        val candidates = materializeActivationCandidates(state, legal)
        if (candidates.size == 1) return candidates.single()
        return candidates.maxWithOrNull(
            compareBy<ActivateAbility> { unlockedCastPriority(state, it) }
                .thenBy { it.manaColorChoice?.let { color -> coloredManaDemandScore(state, color) } ?: 0 }
                .thenBy { if (it.manaColorChoice == Color.GREEN) 1 else 0 }
                .thenByDescending { it.manaColorChoice?.name.orEmpty() },
        ) ?: action
    }

    private fun materializeActivationCandidates(state: GameState, legal: LegalAction): List<ActivateAbility> {
        val action = legal.action as? ActivateAbility ?: return emptyList()
        var payment = action.costPayment ?: AdditionalCostPayment()
        legal.additionalCostInfo?.let { info ->
            when (info.costType) {
                "SacrificePermanent" -> payment = payment.copy(
                    sacrificedPermanents = chooseSacrifice(state, info.validSacrificeTargets).take(info.sacrificeCount)
                )
                "TapPermanents" -> payment = payment.copy(
                    tappedPermanents = chooseElvesToTap(state, info.validTapTargets, info.tapCount)
                )
                "BouncePermanent" -> payment = payment.copy(
                    bouncedPermanents = info.validBounceTargets.sortedBy { analyzer.name(state, it) }.take(info.bounceCount)
                )
            }
        }
        val targets = if (legal.requiresTargets && action.targets.isEmpty()) {
            legal.validTargets.orEmpty().let { targets ->
                val chosen = when (analyzer.name(state, action.sourceId)) {
                    ProjectXStateAnalyzer.QUIRION_RANGER -> targets.maxByOrNull { quirionTargetScore(state, it) }
                    else -> targets.firstOrNull()
                }
                listOfNotNull(chosen?.let { ChosenTarget.Permanent(it) })
            }
        } else action.targets
        val prepared = action.copy(costPayment = payment, targets = targets)
        if (!legal.requiresManaColorChoice) return listOf(prepared)
        return (legal.availableManaColors?.toSet() ?: Color.entries.toSet())
            .sortedBy { it.name }
            .map { prepared.copy(manaColorChoice = it) }
    }

    private fun chooseSacrifice(state: GameState, candidates: List<EntityId>): List<EntityId> {
        val missing = analyzer.missingPrimaryRoles(state, playerId)
        val battlefield = analyzer.battlefieldNames(state, playerId)
        fun score(id: EntityId): Int = when (analyzer.name(state, id)) {
            ProjectXStateAnalyzer.SAFEHOLD_ELITE -> if (analyzer.primaryRoles.all(battlefield::contains)) 10_000 else -10_000
            ProjectXStateAnalyzer.WIREWOOD_HERALD -> if (missing.size == 1 && missing.single() in analyzer.libraryNames(state, playerId)) 9_000 else 100
            ProjectXStateAnalyzer.CARRION_FEEDER,
            ProjectXStateAnalyzer.IVY_LANE_DENIZEN -> -10_000
            ProjectXStateAnalyzer.EVOLUTION_WITNESS,
            ProjectXStateAnalyzer.NETTLE_SENTINEL,
            ProjectXStateAnalyzer.BIRCHLORE_RANGERS -> if (analyzer.secondaryWitnessSequence(state, playerId) != null) -8_000 else 0
            else -> 10
        }
        return candidates.sortedByDescending(::score)
    }

    private fun chooseElvesToTap(state: GameState, candidates: List<EntityId>, count: Int): List<EntityId> {
        fun preservationCost(id: EntityId): Int = when (analyzer.name(state, id)) {
            ProjectXStateAnalyzer.NETTLE_SENTINEL ->
                if (state.getHand(playerId).any { isGreenSpell(state, it) }) 0 else 30
            ProjectXStateAnalyzer.BIRCHLORE_RANGERS -> 10
            ProjectXStateAnalyzer.WIREWOOD_HERALD -> 15
            ProjectXStateAnalyzer.SAFEHOLD_ELITE,
            ProjectXStateAnalyzer.IVY_LANE_DENIZEN,
            ProjectXStateAnalyzer.EVOLUTION_WITNESS -> 50
            else -> 5
        }
        return candidates.sortedWith(compareBy(::preservationCost).thenBy { analyzer.name(state, it) }).take(count)
    }

    private fun chooseHeraldTutor(
        state: GameState,
        options: List<EntityId>,
        cardName: (EntityId) -> String?,
    ): List<EntityId> {
        val missing = analyzer.missingPrimaryRoles(state, playerId)
        val graveyard = analyzer.graveyardNames(state, playerId)
        fun score(id: EntityId): Int = when (val name = cardName(id)) {
            missing.singleOrNull() -> 10_000
            ProjectXStateAnalyzer.ESSENCE_WARDEN -> if (outcome(state).completeInfiniteEngine) 9_000 else 200
            ProjectXStateAnalyzer.EVOLUTION_WITNESS -> if (missing.any(graveyard::contains)) 8_500 else 300
            ProjectXStateAnalyzer.SAFEHOLD_ELITE,
            ProjectXStateAnalyzer.IVY_LANE_DENIZEN -> if (name in missing) 8_000 else 400
            else -> 0
        }
        return listOfNotNull(options.maxByOrNull(::score))
    }

    private fun chooseWindingWayMode(state: GameState, decision: ChooseModeDecision): DecisionResponse {
        val desired = windingWayDesiredType(state)
        val mode = decision.modes.firstOrNull { it.available && it.text.contains(desired, ignoreCase = true) }
            ?: decision.modes.first { it.available }
        return ModesChosenResponse(decision.id, listOf(mode.index))
    }

    private fun chooseWindingWayOption(state: GameState, decision: ChooseOptionDecision): DecisionResponse {
        val desired = windingWayDesiredType(state)
        val index = decision.options.indexOfFirst { it.contains(desired, ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
        return OptionChosenResponse(decision.id, index)
    }

    private fun windingWayDesiredType(state: GameState): String = if (needsLand(state)) "land" else "creature"

    private fun chooseIvyTarget(state: GameState, decision: ChooseTargetsDecision): DecisionResponse {
        val legal = decision.targetRequirements.firstOrNull()?.let { decision.legalTargets[it.index] }.orEmpty()
        fun score(id: EntityId): Int = when (analyzer.name(state, id)) {
            ProjectXStateAnalyzer.SAFEHOLD_ELITE -> {
                val minus = state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.MINUS_ONE_MINUS_ONE) ?: 0
                if (minus > 0) 10_000 else 500
            }
            ProjectXStateAnalyzer.EVOLUTION_WITNESS -> if (analyzer.graveyardNames(state, playerId).isNotEmpty()) 9_000 else 600
            else -> 0
        }
        return singleTarget(decision, legal.maxByOrNull(::score))
    }

    private fun chooseWitnessTarget(state: GameState, decision: ChooseTargetsDecision): DecisionResponse {
        val legal = decision.targetRequirements.firstOrNull()?.let { decision.legalTargets[it.index] }.orEmpty()
        val missing = analyzer.missingPrimaryRoles(state, playerId)
        fun score(id: EntityId): Int {
            val name = analyzer.name(state, id)
            return when {
                name != null && name in missing -> 10_000
                name == ProjectXStateAnalyzer.FALKENRATH_NOBLE -> if (outcome(state).completeInfiniteEngine) 9_000 else 500
                name == ProjectXStateAnalyzer.ESSENCE_WARDEN -> if (outcome(state).completeInfiniteEngine) 8_500 else 450
                name == ProjectXStateAnalyzer.QUIRION_RANGER || name == ProjectXStateAnalyzer.NETTLE_SENTINEL -> 7_000
                name != null -> 100
                else -> 0
            }
        }
        return singleTarget(decision, legal.maxByOrNull(::score))
    }

    private fun chooseQuirionTarget(state: GameState, decision: ChooseTargetsDecision): DecisionResponse {
        val legal = decision.targetRequirements.firstOrNull()?.let { decision.legalTargets[it.index] }.orEmpty()
        return singleTarget(decision, legal.maxByOrNull { quirionTargetScore(state, it) })
    }

    private fun chooseOpponentTarget(state: GameState, decision: ChooseTargetsDecision): DecisionResponse {
        val requirement = decision.targetRequirements.firstOrNull()
        val legal = requirement?.let { decision.legalTargets[it.index] }.orEmpty()
        return singleTarget(decision, legal.firstOrNull { state.isOpponentTo(it, playerId) } ?: legal.firstOrNull())
    }

    private fun singleTarget(decision: ChooseTargetsDecision, target: EntityId?): TargetsResponse {
        val index = decision.targetRequirements.firstOrNull()?.index ?: 0
        return TargetsResponse(decision.id, mapOf(index to listOfNotNull(target)))
    }

    private fun preferredManaColor(state: GameState, available: Set<Color>): Color {
        return available.maxWithOrNull(
            compareBy<Color> { coloredManaDemandScore(state, it) }
                .thenBy { if (it == Color.GREEN) 1 else 0 }
                .thenByDescending { it.name },
        ) ?: error("A mana color decision must offer at least one color")
    }

    private fun coloredManaDemandScore(state: GameState, color: Color): Int {
        val pool = state.getEntity(playerId)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
        return state.getHand(playerId).maxOfOrNull { cardId ->
            val card = state.getEntity(cardId)?.get<CardComponent>() ?: return@maxOfOrNull 0
            val unmet = ((card.manaCost.colorCount[color] ?: 0) - pool.getAmount(color)).coerceAtLeast(0)
            if (unmet == 0) 0 else unmet * 100 + castPriority(state, cardId).coerceAtLeast(1)
        } ?: 0
    }

    private fun needsColorMana(state: GameState, color: Color): Boolean =
        coloredManaDemandScore(state, color) > 0

    private fun needsLand(state: GameState): Boolean {
        val lands = state.controlledBattlefield(playerId).count { id ->
            state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.isLand == true
        }
        val landInHand = state.getHand(playerId).any { id ->
            state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.isLand == true
        }
        return lands < 2 || (lands < 3 && !landInHand)
    }

    private fun quirionUnlocksMana(state: GameState): Boolean {
        val nettle = analyzer.permanent(state, playerId, ProjectXStateAnalyzer.NETTLE_SENTINEL)
        if (nettle == null || analyzer.isUntapped(state, nettle)) return false
        val untappedElves = state.controlledBattlefield(playerId).count { analyzer.isElf(state, it) && analyzer.isUntapped(state, it) }
        if (untappedElves < 1) return false

        val pool = state.getEntity(playerId)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
        val floating = pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless
        val untappedLands = state.controlledBattlefield(playerId).count { id ->
            state.getEntity(id)?.get<CardComponent>()?.isLand == true && analyzer.isUntapped(state, id)
        }
        // Returning a Forest consumes one land, then the restored Elf pair can supply one mana of
        // whichever color the executable spell actually needs.
        val projectedTotal = floating + (untappedLands - 1).coerceAtLeast(0) + 1
        return state.getHand(playerId).any { cardId ->
            val card = state.getEntity(cardId)?.get<CardComponent>() ?: return@any false
            !card.isLand && card.manaValue <= projectedTotal &&
                card.manaCost.colors.any { needsColorMana(state, it) }
        }
    }

    private fun quirionTargetScore(state: GameState, id: EntityId): Int = when {
        analyzer.isUntapped(state, id) -> -100
        analyzer.name(state, id) == ProjectXStateAnalyzer.NETTLE_SENTINEL -> 1_000
        analyzer.isElf(state, id) -> 500
        else -> 0
    }

    private fun witnessCanAdapt(state: GameState, witness: EntityId): Boolean =
        (state.getEntity(witness)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) == 0

    private fun landPriority(state: GameState, name: String?): Int {
        return when (name) {
            "Swamp" -> if (needsColorMana(state, Color.BLACK)) 40 else 20
            "Forest" -> 35
            "Haunted Mire" -> if (needsColorMana(state, Color.BLACK)) 25 else 5
            "Khalni Garden" -> 10
            else -> 0
        }
    }

    private fun primaryRoleTieBreak(name: String?): Int = when (name) {
        ProjectXStateAnalyzer.CARRION_FEEDER -> 30
        ProjectXStateAnalyzer.SAFEHOLD_ELITE -> 20
        ProjectXStateAnalyzer.IVY_LANE_DENIZEN -> 10
        else -> 0
    }

    /** Prefer a creature mana ability only when its actual post-payment line reaches a new cast. */
    private fun creatureManaActivationPriority(
        state: GameState,
        legal: LegalAction,
        action: ActivateAbility,
    ): Int {
        val source = state.getEntity(action.sourceId)?.get<CardComponent>() ?: return 0
        if (!source.isCreature || !legal.isManaAbility) return 0

        return unlockedCastPriority(state, action)
    }

    private fun purposelessCreatureManaActivation(
        state: GameState,
        legal: LegalAction,
        action: GameAction,
    ): Boolean {
        if (action !is ActivateAbility) return false
        val source = state.getEntity(action.sourceId)?.get<CardComponent>() ?: return false
        return source.isCreature && legal.isManaAbility && unlockedCastPriority(state, action) == 0
    }

    /**
     * Score only casts that become executable because of this exact activation and mana-color
     * choice, optionally followed by a short sequence of other legal creature-mana activations.
     * Merely increasing the pool, or matching a colored card somewhere in hand, is not a strategic
     * use. Every intermediate action is simulated and every destination cast passes executor
     * validation, keeping the policy aligned with real auto-payment rather than approximate counts.
     */
    private fun unlockedCastPriority(state: GameState, action: ActivateAbility): Int {
        val before = affordableCastIds(state)

        val simulated = simulator.simulate(state, action)
        if (simulated is com.wingedsheep.ai.engine.SimulationResult.Illegal ||
            simulated is com.wingedsheep.ai.engine.SimulationResult.StoppedAtLimit
        ) return 0

        val unlockedPriority = reachableCastPriority(
            state = simulated.state,
            originallyAffordable = before,
            remainingManaActivations = MAX_MANA_ACTIVATION_LOOKAHEAD - 1,
        )
        return (unlockedPriority - 1).coerceAtLeast(0)
    }

    private fun reachableCastPriority(
        state: GameState,
        originallyAffordable: Set<EntityId>,
        remainingManaActivations: Int,
    ): Int {
        val immediate = (affordableCastIds(state) - originallyAffordable).maxOfOrNull { cardId ->
            castPriority(state, cardId).coerceAtLeast(CONCRETE_CAST_USE_PRIORITY)
        } ?: 0
        if (remainingManaActivations == 0) return immediate

        val continued = enumerator.enumerate(state, playerId, EnumerationMode.ACTIONS_ONLY)
            .asSequence()
            .filter { it.affordable && it.isManaAbility }
            .filter { legal ->
                val activation = legal.action as? ActivateAbility ?: return@filter false
                state.getEntity(activation.sourceId)?.get<CardComponent>()?.isCreature == true
            }
            .flatMap { legal -> materializeActivationCandidates(state, legal).asSequence() }
            .mapNotNull { activation ->
                val simulated = simulator.simulate(state, activation)
                if (simulated is com.wingedsheep.ai.engine.SimulationResult.Illegal ||
                    simulated is com.wingedsheep.ai.engine.SimulationResult.StoppedAtLimit
                ) null else simulated.state
            }
            .maxOfOrNull { next ->
                (reachableCastPriority(next, originallyAffordable, remainingManaActivations - 1) - 1)
                    .coerceAtLeast(0)
            } ?: 0
        return maxOf(immediate, continued)
    }

    private fun affordableCastIds(state: GameState): Set<EntityId> =
        enumerator.enumerate(state, playerId, EnumerationMode.ACTIONS_ONLY)
            .asSequence()
            .filter(LegalAction::affordable)
            .mapNotNull { legal -> legal.action as? CastSpell }
            .filter { simulator.validateSubmission(state, it).accepted }
            .map(CastSpell::cardId)
            .toSet()

    private fun castPriority(state: GameState, cardId: EntityId): Int {
        val missing = analyzer.missingPrimaryRoles(state, playerId)
        val battlefield = analyzer.battlefieldNames(state, playerId)
        val undeployedPrimary = analyzer.primaryRoles - battlefield
        val name = analyzer.name(state, cardId)
        return when {
            name == ProjectXStateAnalyzer.FALKENRATH_NOBLE ->
                if (analyzer.primaryRoles.all(battlefield::contains)) 10_000 else 900
            name == ProjectXStateAnalyzer.ESSENCE_WARDEN ->
                if (analyzer.primaryRoles.all(battlefield::contains)) 9_000 else 700
            name != null && name in undeployedPrimary -> 8_000 + primaryRoleTieBreak(name)
            name == ProjectXStateAnalyzer.LEAD_THE_STAMPEDE -> if (missing.isNotEmpty()) 5_200 else 500
            name == ProjectXStateAnalyzer.WINDING_WAY -> if (missing.isNotEmpty() || needsLand(state)) 5_000 else 450
            name == ProjectXStateAnalyzer.EVOLUTION_WITNESS ->
                if (missing.any(analyzer.graveyardNames(state, playerId)::contains)) 6_500 else 650
            name == ProjectXStateAnalyzer.WIREWOOD_HERALD -> if (missing.isNotEmpty()) 5_800 else 600
            else -> accelerationPriority(state, cardId)
        }
    }

    private fun accelerationPriority(state: GameState, cardId: EntityId): Int {
        val card = state.getEntity(cardId)?.get<CardComponent>() ?: return 0
        if (!card.isCreature || !isReusableCreatureManaSource(card)) return 0

        val reusableSources = state.controlledBattlefield(playerId).count { permanentId ->
            val permanent = state.getEntity(permanentId)?.get<CardComponent>() ?: return@count false
            permanent.isLand || (permanent.isCreature && isReusableCreatureManaSource(permanent))
        }
        val bestTurnGain = state.getHand(playerId).asSequence()
            .filter { it != cardId }
            .mapNotNull { state.getEntity(it)?.get<CardComponent>() }
            .filterNot { it.isLand || it.manaValue == 0 }
            .maxOfOrNull { futureCastTurn(it.manaValue, reusableSources) - futureCastTurn(it.manaValue, reusableSources + 1) }
            ?: 0
        return if (bestTurnGain > 0) 750 + bestTurnGain * 100 else 0
    }

    private fun futureCastTurn(manaValue: Int, reusableSources: Int): Int {
        for (turnsFromNow in 1..MAX_ACCELERATION_LOOKAHEAD) {
            if (reusableSources + turnsFromNow >= manaValue) return turnsFromNow
        }
        return MAX_ACCELERATION_LOOKAHEAD + 1
    }

    private fun isReusableCreatureManaSource(card: CardComponent): Boolean =
        cardRegistry.getCard(card.cardDefinitionId)?.script?.activatedAbilities?.any { ability ->
            ability.isManaAbility && ability.cost.includesSelfTap()
        } == true

    private fun AbilityCost.includesSelfTap(): Boolean = when (this) {
        AbilityCost.Tap -> true
        is AbilityCost.Composite -> costs.any { it.includesSelfTap() }
        else -> false
    }

    private fun isGreenSpell(state: GameState, cardId: EntityId): Boolean =
        state.getEntity(cardId)?.get<CardComponent>()?.let { card ->
            !card.isLand && Color.GREEN in card.colors
        } == true

    companion object {
        private const val MAX_ACCELERATION_LOOKAHEAD = 8
        private const val MAX_MANA_ACTIVATION_LOOKAHEAD = 4
        private const val CONCRETE_CAST_USE_PRIORITY = 100

        private val ALWAYS_ACCEPT_SOURCES = setOf(
            ProjectXStateAnalyzer.WIREWOOD_HERALD,
            ProjectXStateAnalyzer.NETTLE_SENTINEL,
            ProjectXStateAnalyzer.EVOLUTION_WITNESS,
        )
    }
}

data class ProjectXActionChoice(
    val action: GameAction,
    val rejectedSubmissions: List<ProjectXRejectedSubmission> = emptyList(),
)

data class ProjectXRejectedSubmission(
    val action: GameAction,
    val legalActionDescription: String,
    val executorReason: String,
)
