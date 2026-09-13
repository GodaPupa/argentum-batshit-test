package com.wingedsheep.gym.telemetry

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.TargetSelection
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.insight.AiDecisionInsight
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import kotlinx.serialization.Serializable

@Serializable
data class LandUnlockedSpell(
    val land: String,
    val spell: String,
)

@Serializable
enum class PreSpellSetupClassification {
    CURRENTLY_EXECUTABLE,
    LAND_UNLOCKED,
    STILL_UNEXECUTABLE_AFTER_LAND,
    EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR,
    GENUINE_MISSED_SUPERIOR_SEQUENCE,
}

// SHARED ARGENTUM CHANGE: yes — generic counterfactual sequencing audit vocabulary.

@Serializable
data class SetupManaSource(
    val id: EntityId,
    val name: String,
    val tapped: Boolean,
)

@Serializable
data class SetupEntityAudit(
    val id: EntityId,
    val name: String,
    val controllerId: EntityId? = null,
)

@Serializable
data class SetupAdditionalCostAudit(
    val kind: String,
    val entities: List<SetupEntityAudit> = emptyList(),
    val amount: Int? = null,
)

@Serializable
data class SetupResourceState(
    val manaSources: List<SetupManaSource>,
    val untappedManaSourceCount: Int,
    val floatingMana: Map<String, Int>,
    val handSize: Int,
    val spellsCastThisTurn: Int,
    val temporaryConditions: List<String>,
)

@Serializable
data class SetupActionAudit(
    val action: String,
    val cardId: EntityId? = null,
    val semanticActionIdentity: SetupSemanticActionIdentity? = null,
    val manaCost: String? = null,
    val coloredRequirements: String? = null,
    val targets: List<EntityId> = emptyList(),
    val targetDetails: List<SetupEntityAudit> = emptyList(),
    val additionalCostMode: String = "none",
    val additionalCosts: List<SetupAdditionalCostAudit> = emptyList(),
    val paymentSources: List<SetupManaSource> = emptyList(),
    val resourcesBefore: SetupResourceState,
    val resourcesAfter: SetupResourceState? = null,
)

@Serializable
data class SetupSemanticActionIdentity(
    val actionType: String,
    val cardDefinitionId: String,
    val castFaceIndex: Int? = null,
    val sourceZone: String? = null,
    val controllerId: EntityId,
    val chosenModes: List<Int> = emptyList(),
    val targets: List<EntityId> = emptyList(),
    val xValue: Int? = null,
    val alternativeCost: String? = null,
    val additionalCostMode: String = "none",
    val paymentRequirement: String? = null,
)

@Serializable
data class PreSpellSetupEvaluation(
    val turn: Int? = null,
    val relevantAction: String,
    val proposedLandPlay: String? = null,
    val landEntersTapped: Boolean? = null,
    val classifications: List<PreSpellSetupClassification>,
    /** Land plus setup action only; intentionally shorter than a complete continuation. */
    val setupPrefix: List<String>,
    /** Setup-first line through the focal spell; empty only when no complete continuation exists. */
    val completeSetupContinuation: List<String>,
    val proposedActionOrder: List<String>,
    val steps: List<SetupActionAudit>,
    val actualLineTaken: List<String> = emptyList(),
    /** Complete setup-first alternative to the actual focal-first line. */
    val counterfactualLineEvaluated: List<String>,
    /** Complete focal-first comparator used for the material-superiority score. */
    val comparisonLineEvaluated: List<String>,
    /** Source-specific structured actions for that complete focal-first comparator. */
    val comparisonSteps: List<SetupActionAudit> = emptyList(),
    val completedLineScore: Double? = null,
    val reorderedLineScore: Double? = null,
    /** Generic payoff engines active before and after the proposed deployment. */
    val activePayoffsBeforeDeployment: List<String> = emptyList(),
    val activePayoffsAfterDeployment: List<String> = emptyList(),
    /** Evaluator delta attributable to setup-first versus the same complete focal-first line. */
    val additionalImmediatePayoffValue: Double? = null,
    val setupFirstResourcesAfter: SetupResourceState? = null,
    val focalFirstResourcesAfter: SetupResourceState? = null,
    val completeResourcesEquivalent: Boolean? = null,
    val materiallySuperior: Boolean,
    val productionAdmissible: Boolean = false,
    val productionRejectionOrHoldReason: String? = null,
    val staticBoardValue: Double? = null,
    val strategicSequencingAdjustment: Double = 0.0,
    val adjustedValue: Double? = null,
    val passValue: Double? = null,
    val continuationHorizon: Int = 0,
    val completeComparedContinuation: List<String> = emptyList(),
    val reason: String,
)

/** Actionable setup information captured immediately before a focal spell is cast. */
@Serializable
data class PreSpellSetupSnapshot(
    val currentlyExecutableBeforeFocal: List<String>,
    val executableAfterLegalLandPlay: List<LandUnlockedSpell>,
    val stillUnexecutableAfterLegalLandPlay: List<LandUnlockedSpell> = emptyList(),
    val executableButNotMateriallySuperior: List<List<String>> = emptyList(),
    val bestValidatedSetupSequence: List<String>?,
    val focalCastBeforeSuperiorSetup: Boolean,
    val evaluatedSequences: List<PreSpellSetupEvaluation> = emptyList(),
)

/**
 * Uses authoritative action enumeration and simulation to distinguish a spell that can really be
 * cast before the focal spell from one that becomes executable only after a legal same-turn land
 * play. Feasibility includes explicit mana-source choices, and complete candidate sequences are
 * compared with the best same-length continuation after casting the focal spell first. Availability,
 * non-executability, outcome-equivalence, and genuine superiority are therefore kept distinct.
 */
class PreSpellSetupTelemetry(private val registry: CardRegistry) {
    private val simulator = GameSimulator(registry)
    private val evaluator = AIPlayer.defaultEvaluator()
    private val intents = IntentCatalog.of(registry)
    private val decisionPlayers = mutableMapOf<EntityId, AIPlayer>()

    init {
        // Complete any engine decision that remains after the concrete target/additional-cost
        // materialization above. This is a shadow counterfactual only; it never submits an action
        // to the live game and uses the same deterministic generic responder as the agent.
        simulator.decisionResolver = { state, decision ->
            decisionPlayers.getOrPut(decision.playerId) {
                AIPlayer.create(registry, decision.playerId)
            }.respondToDecision(state, decision)
        }
    }

    fun observe(state: GameState, playerId: EntityId, focalCardId: EntityId): PreSpellSetupSnapshot {
        val current = setupLines(state, playerId, focalCardId, landName = null)
        val currentlyCompletableIds = current.map(SetupLine::setupCardId).toSet()
        val landStates = simulator.getLegalActions(state, playerId).mapNotNull { legal ->
            val play = legal.action as? PlayLand ?: return@mapNotNull null
            val result = simulator.simulate(state, play)
            val landState = result.usableState() ?: return@mapNotNull null
            val landName = name(state, play.cardId)
            LandOutcome(state, landState, landName, play.cardId)
        }
        val afterLand = landStates.flatMap { land ->
            setupLines(land.after, playerId, focalCardId, land.name, land.id, land)
        }
        val stillUnexecutable = landStates.flatMap { land ->
            simulator.getLegalActions(land.after, playerId).mapNotNull { legal ->
                val cast = legal.action as? CastSpell ?: return@mapNotNull null
                if (cast.cardId == focalCardId) return@mapNotNull null
                val complete = castStates(land.after, playerId, cast.cardId).any { setup ->
                    castStates(setup.state, playerId, focalCardId).isNotEmpty()
                }
                StillLine(land, cast, legal.manaCostString, name(land.after, cast.cardId)).takeUnless { complete }
            }
        }
        val assessed = (current + afterLand).map { line ->
            val reordered = reorderedLine(state, playerId, focalCardId, line)
            val continuation = reordered?.let {
                commonProductionContinuation(line.focal.state, it.state, playerId)
            }
            line.copy(reordered = reordered, continuation = continuation)
        }
        val superior = assessed.filter { line ->
            line.production.productionAdmissible && line.reordered != null &&
                line.setupFirstAdjustedScore > line.focalFirstAdjustedScore + MATERIAL_MARGIN
        }
        val best = superior.maxByOrNull(SetupLine::setupFirstAdjustedScore)
        val nonSuperior = assessed.filterNot(superior::contains)

        return PreSpellSetupSnapshot(
            currentlyExecutableBeforeFocal = current.filter(SetupLine::setupUseful)
                .map(SetupLine::spellName).distinct().sorted(),
            executableAfterLegalLandPlay = superior
                .filter { it.landName != null && it.setupCardId !in currentlyCompletableIds }
                .map { LandUnlockedSpell(requireNotNull(it.landName), it.spellName) }
                .distinct().sortedWith(compareBy(LandUnlockedSpell::land, LandUnlockedSpell::spell)),
            stillUnexecutableAfterLegalLandPlay = stillUnexecutable.map { LandUnlockedSpell(it.land.name, it.spellName) }.distinct()
                .sortedWith(compareBy(LandUnlockedSpell::land, LandUnlockedSpell::spell)),
            executableButNotMateriallySuperior = nonSuperior.map(::describe).distinct().sortedBy { it.joinToString() },
            bestValidatedSetupSequence = best?.let { line ->
                describe(line, name(state, focalCardId))
            },
            focalCastBeforeSuperiorSetup = best != null,
            evaluatedSequences = assessed.map { line -> evaluation(line, line in superior, name(state, focalCardId)) } +
                stillUnexecutable.map { evaluation(it) },
        )
    }

    private fun setupLines(
        state: GameState,
        playerId: EntityId,
        focalCardId: EntityId,
        landName: String?,
        landCardId: EntityId? = null,
        landOutcome: LandOutcome? = null,
    ): List<SetupLine> {
        val baseline = score(state, playerId)
        return productionCastCandidates(state, playerId).mapNotNull { production ->
            val legal = production.legal
            val cast = production.action
            if (cast.cardId == focalCardId) return@mapNotNull null
            val setupStates = productionCastOutcomes(state, production)
            val setupUseful = production.productionAdmissible &&
                setupStates.any { score(it.state, playerId) > baseline + MATERIAL_MARGIN }
            val completed = setupStates.flatMap { setup ->
                productionCastCandidates(setup.state, playerId)
                    // The focal action is the observed production action. Its sequencing deferral
                    // is the very fact this counterfactual explains, so it remains simulatable even
                    // when the current production gate would now hold it for this setup-first line.
                    .filter { it.action.cardId == focalCardId }
                    .flatMap { focal -> productionCastOutcomes(setup.state, focal).map { setup to it } }
            }.maxByOrNull { (_, focal) -> score(focal.state, playerId) }
                ?: return@mapNotNull null
            SetupLine(landName, landCardId, cast.cardId, name(state, cast.cardId),
                score(completed.second.state, playerId), setupUseful, state, landOutcome,
                completed.first, completed.second, production)
        }
    }

    /**
     * Compare like with like: if the same land and setup can follow the focal spell, their complete
     * result is the sequencing baseline. This prevents an outcome-equivalent land timing from being
     * called superior while preserving real order-sensitive value such as a setup spell increasing
     * the focal spell's storm count or enabling a temporary condition before it resolves.
     */
    private fun reorderedLine(
        state: GameState,
        playerId: EntityId,
        focalCardId: EntityId,
        line: SetupLine,
    ): ReorderedLine? {
        return productionCastCandidates(state, playerId)
            .filter { it.action.cardId == focalCardId }
            .mapNotNull { focalCandidate ->
            productionCastOutcomes(state, focalCandidate).mapNotNull { focal ->
            val afterFocal = focal.state
            val landOutcome = line.landCardId?.let { landId ->
                val land = simulator.getLegalActions(afterFocal, playerId).firstOrNull { candidate ->
                    (candidate.action as? PlayLand)?.cardId == landId
                } ?: return@mapNotNull null
                val after = simulator.simulate(afterFocal, land.action).usableState() ?: return@mapNotNull null
                LandOutcome(afterFocal, after, name(afterFocal, landId), landId)
            }
            val afterLand = landOutcome?.after ?: afterFocal
            simulator.getLegalActions(afterLand, playerId).mapNotNull { candidate ->
                val continuation = candidate.action as? CastSpell ?: return@mapNotNull null
                if (continuation.cardId != line.setupCardId) {
                    return@mapNotNull null
                }
                productionCastCandidates(afterLand, playerId)
                    // A production-rejected setup can still be simulated on both sides of the
                    // audit comparison. Its rejection remains authoritative and prevents a
                    // superior classification; retaining the legal shadow result keeps the two
                    // bounded continuations equally complete and explains the held action.
                    .filter { it.action.cardId == continuation.cardId }
                    .flatMap { setupCandidate ->
                        productionCastOutcomes(afterLand, setupCandidate).map { setup ->
                            ReorderedLine(
                                score(setup.state, playerId), setup.state, focal, landOutcome, setup,
                                focalCandidate,
                            )
                        }
                    }.maxByOrNull(ReorderedLine::score)
            }.maxByOrNull(ReorderedLine::score)
            }.maxByOrNull(ReorderedLine::score)
        }.maxByOrNull(ReorderedLine::score)
    }

    /**
     * Ask a fresh production strategist to assess this exact priority position, then expose its
     * already-materialized cast candidates to the shadow telemetry. Fresh strategy state prevents
     * observation from inheriting or mutating the live player's commitments or progress memory.
     *
     * SHARED ARGENTUM CHANGE: yes
     */
    private fun productionCastCandidates(
        state: GameState,
        playerId: EntityId,
    ): List<ProductionCastCandidate> {
        var captured: AiDecisionInsight? = null
        val legalActions = simulator.getLegalActions(state, playerId)
        AIPlayer.create(
            registry,
            playerId,
            AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
            insightSink = { _, insight -> captured = insight },
        ).chooseFrom(state, legalActions)
        val insight = captured ?: return emptyList()
        return insight.options.mapNotNull { option ->
            val cast = option.action as? CastSpell ?: return@mapNotNull null
            val legal = legalActions.firstOrNull { candidate ->
                val candidateCast = candidate.action as? CastSpell
                candidateCast?.cardId == cast.cardId && candidateCast.faceIndex == cast.faceIndex &&
                    candidateCast.chosenModes == cast.chosenModes
            } ?: return@mapNotNull null
            ProductionCastCandidate(
                legal = legal,
                action = cast,
                productionAdmissible = option.productionAdmissible,
                rejectionReason = option.productionRejectionReason,
                staticBoardValue = option.rawScore ?: option.score,
                sequencingAdjustment = option.strategicSequencingAdjustment,
                expiringConditionSequencingAdjustment = option.expiringConditionSequencingAdjustment,
                adjustedValue = option.score,
                passValue = insight.baselineScore,
                chosen = option.chosen,
            )
        }
    }

    private fun productionCastOutcomes(
        state: GameState,
        candidate: ProductionCastCandidate,
    ): List<CastOutcome> {
        val lands = state.projectedState.getBattlefieldControlledBy(candidate.action.playerId).filter { id ->
            state.projectedState.hasType(id, "LAND") && state.getEntity(id)?.has<TappedComponent>() != true
        }
        val manaValue = candidate.legal.manaCostString
            ?.let { com.wingedsheep.sdk.core.ManaCost.parse(it).cmc } ?: lands.size
        val actions = buildList {
            add(candidate.action)
            fun choose(start: Int, remaining: Int, chosen: MutableList<EntityId>) {
                if (remaining == 0) {
                    add(candidate.action.copy(paymentStrategy = PaymentStrategy.Explicit(chosen.toList())))
                    return
                }
                for (index in start..lands.size - remaining) {
                    chosen += lands[index]
                    choose(index + 1, remaining - 1, chosen)
                    chosen.removeAt(chosen.lastIndex)
                }
            }
            for (size in 1..minOf(lands.size, manaValue + 1)) choose(0, size, mutableListOf())
        }
        return actions.distinct().mapNotNull { action ->
            simulator.simulate(state, action).usableState()?.let {
                CastOutcome(action, candidate.legal.manaCostString, state, it)
            }
        }
    }

    /** One further action is retained only when the same concrete card remains production-
     * admissible in both orders. That gives both sides an equivalent three-action horizon without
     * inventing a continuation that one ordering cannot legally take. */
    private fun commonProductionContinuation(
        setupFirstState: GameState,
        focalFirstState: GameState,
        playerId: EntityId,
    ): CommonContinuation? {
        val setupFirst = productionCastCandidates(setupFirstState, playerId)
            .filter(ProductionCastCandidate::productionAdmissible)
        val focalFirst = productionCastCandidates(focalFirstState, playerId)
            .filter(ProductionCastCandidate::productionAdmissible)
        return setupFirst.asSequence().mapNotNull { first ->
            val second = focalFirst.firstOrNull { it.action.cardId == first.action.cardId } ?: return@mapNotNull null
            val firstOutcome = productionCastOutcomes(setupFirstState, first)
                .maxByOrNull { score(it.state, playerId) } ?: return@mapNotNull null
            val secondOutcome = productionCastOutcomes(focalFirstState, second)
                .maxByOrNull { score(it.state, playerId) } ?: return@mapNotNull null
            CommonContinuation(firstOutcome, secondOutcome, first, second)
        }.maxByOrNull { pair ->
            score(pair.setupFirst.state, playerId) + score(pair.focalFirst.state, playerId)
        }
    }

    /** Explore legal explicit source choices as well as auto-pay so telemetry tests feasibility,
     * not an auto-tapper's incidental choice of which otherwise-equivalent land supplies generic
     * mana. Invalid color/source selections are rejected by the authoritative simulator. */
    private fun castStates(state: GameState, playerId: EntityId, cardId: EntityId): List<CastOutcome> =
        simulator.getLegalActions(state, playerId).filter { candidate ->
            val cast = candidate.action as? CastSpell
            cast?.cardId == cardId
        }.flatMap { legal -> castStates(state, playerId, legal) }

    /**
     * Materialize only choices the authoritative [com.wingedsheep.engine.legalactions.LegalAction]
     * actually offers. Targeted/modal setup spells therefore participate without invented targets
     * or costs, and every concrete target/payment combination remains visible in [CastOutcome].
     */
    private fun castStates(
        state: GameState,
        playerId: EntityId,
        legal: com.wingedsheep.engine.legalactions.LegalAction,
    ): List<CastOutcome> {
        val base = legal.action as? CastSpell ?: return emptyList()
        val targeted = targetVariants(state, playerId, legal, base)
        val paid = targeted.flatMap { cast -> additionalCostVariants(legal.additionalCostInfo, cast) }
        val lands = state.projectedState.getBattlefieldControlledBy(playerId).filter { id ->
            state.projectedState.hasType(id, "LAND") &&
                state.getEntity(id)?.has<TappedComponent>() != true
        }
        val manaValue = legal.manaCostString?.let { com.wingedsheep.sdk.core.ManaCost.parse(it).cmc }
            ?: lands.size
        val maxSources = minOf(lands.size, manaValue + 1)
        val actions = buildList {
            addAll(paid)
            fun combinations(start: Int, remaining: Int, chosen: MutableList<EntityId>) {
                if (remaining == 0) {
                    paid.forEach { add(it.copy(paymentStrategy = PaymentStrategy.Explicit(chosen.toList()))) }
                    return
                }
                for (index in start..lands.size - remaining) {
                    chosen += lands[index]
                    combinations(index + 1, remaining - 1, chosen)
                    chosen.removeAt(chosen.lastIndex)
                }
            }
            for (size in 1..maxSources) combinations(0, size, mutableListOf())
        }
        return actions.distinct().mapNotNull { action ->
            simulator.simulate(state, action).usableState()?.let { CastOutcome(action, legal.manaCostString, state, it) }
        }
    }

    private fun targetVariants(
        state: GameState,
        playerId: EntityId,
        legal: com.wingedsheep.engine.legalactions.LegalAction,
        base: CastSpell,
    ): List<CastSpell> {
        if (!legal.requiresTargets || base.targets.isNotEmpty()) return listOf(base)
        val requirements = TargetSelection.targetInfosFor(legal) ?: return emptyList()
        val results = mutableListOf<List<com.wingedsheep.engine.state.components.stack.ChosenTarget>>()
        fun choose(index: Int, chosen: List<com.wingedsheep.engine.state.components.stack.ChosenTarget>, ids: Set<EntityId>) {
            if (index == requirements.size) {
                results += chosen
                return
            }
            val requirement = requirements[index]
            val available = requirement.validTargets.filterNot { requirement.mustDifferFromEarlier && it in ids }
            val max = minOf(requirement.maxTargets, available.size)
            for (count in requirement.minTargets..max) {
                combinations(available, count).forEach { selection ->
                    choose(
                        index + 1,
                        chosen + selection.map { TargetSelection.toChosenTarget(state, requirement, it, playerId) },
                        ids + selection,
                    )
                }
            }
        }
        choose(0, emptyList(), emptySet())
        return results.map { TargetSelection.applyTargets(base, it) as CastSpell }
    }

    private fun additionalCostVariants(
        info: com.wingedsheep.engine.legalactions.AdditionalCostData?,
        base: CastSpell,
    ): List<CastSpell> {
        if (info == null) return listOf(base)
        val existing = base.additionalCostPayment ?: AdditionalCostPayment()
        fun attach(payment: AdditionalCostPayment) = base.copy(additionalCostPayment = payment)
        return when (info.costType) {
            "SacrificePermanent" -> combinations(info.validSacrificeTargets, info.sacrificeCount)
                .map { attach(existing.copy(sacrificedPermanents = it)) }
            "DiscardCard" -> combinations(info.validDiscardTargets, info.discardCount)
                .map { attach(existing.copy(discardedCards = it)) }
            "TapPermanents" -> combinations(info.validTapTargets, info.tapCount)
                .map { attach(existing.copy(tappedPermanents = it)) }
            "BouncePermanent" -> combinations(info.validBounceTargets, info.bounceCount)
                .map { attach(existing.copy(bouncedPermanents = it)) }
            "ExileFromGraveyard" -> combinations(info.validExileTargets, info.exileMinCount)
                .map { attach(existing.copy(exiledCards = it)) }
            else -> emptyList()
        }
    }

    private fun <T> combinations(values: List<T>, count: Int): List<List<T>> {
        if (count == 0) return listOf(emptyList())
        if (count < 0 || count > values.size) return emptyList()
        val result = mutableListOf<List<T>>()
        fun visit(start: Int, chosen: MutableList<T>) {
            if (chosen.size == count) {
                result += chosen.toList()
                return
            }
            for (index in start..values.size - (count - chosen.size)) {
                chosen += values[index]
                visit(index + 1, chosen)
                chosen.removeAt(chosen.lastIndex)
            }
        }
        visit(0, mutableListOf())
        return result
    }

    private fun evaluation(line: SetupLine, superior: Boolean, focalName: String): PreSpellSetupEvaluation {
        val classifications = buildList {
            add(if (line.landName == null) PreSpellSetupClassification.CURRENTLY_EXECUTABLE else PreSpellSetupClassification.LAND_UNLOCKED)
            add(if (superior) PreSpellSetupClassification.GENUINE_MISSED_SUPERIOR_SEQUENCE else
                PreSpellSetupClassification.EXECUTABLE_BUT_NOT_MATERIALLY_SUPERIOR)
        }
        val steps = buildList {
            line.land?.let { land ->
                add(SetupActionAudit("play ${land.name}", land.id, resourcesBefore = resources(land.before, line.setup.action.playerId), resourcesAfter = resources(land.after, line.setup.action.playerId)))
            }
            add(actionAudit("cast ${line.spellName}", line.setup, line.setupStart))
            add(actionAudit("cast $focalName", line.focal, line.setup.state))
            line.continuation?.let { continuation ->
                add(actionAudit(
                    "cast ${name(continuation.setupFirst.before, continuation.setupFirst.action.cardId)}",
                    continuation.setupFirst,
                    continuation.setupFirst.before,
                ))
            }
        }
        val setupFirstLine = describe(line, focalName) + line.continuation?.let {
            "cast ${name(it.setupFirst.before, it.setupFirst.action.cardId)}"
        }.orEmpty().let { if (it.isEmpty()) emptyList() else listOf(it) }
        val focalFirstLine = listOf("cast $focalName") + describe(line) + line.continuation?.let {
            "cast ${name(it.focalFirst.before, it.focalFirst.action.cardId)}"
        }.orEmpty().let { if (it.isEmpty()) emptyList() else listOf(it) }
        return PreSpellSetupEvaluation(
            relevantAction = line.spellName,
            proposedLandPlay = line.landName,
            landEntersTapped = line.land?.after?.getEntity(line.land.id)?.has<TappedComponent>(),
            classifications = classifications,
            setupPrefix = describe(line),
            completeSetupContinuation = setupFirstLine,
            proposedActionOrder = setupFirstLine,
            steps = steps,
            counterfactualLineEvaluated = setupFirstLine,
            comparisonLineEvaluated = focalFirstLine,
            comparisonSteps = line.reordered?.let { reordered ->
                buildList {
                    add(actionAudit("cast $focalName", reordered.focal, line.setupStart))
                    reordered.land?.let { land ->
                        add(SetupActionAudit(
                            "play ${land.name}", land.id,
                            resourcesBefore = resources(land.before, line.setup.action.playerId),
                            resourcesAfter = resources(land.after, line.setup.action.playerId),
                        ))
                    }
                    add(actionAudit("cast ${line.spellName}", reordered.setup, reordered.setup.before))
                    line.continuation?.let { continuation ->
                        add(actionAudit(
                            "cast ${name(continuation.focalFirst.before, continuation.focalFirst.action.cardId)}",
                            continuation.focalFirst,
                            continuation.focalFirst.before,
                        ))
                    }
                }
            }.orEmpty(),
            completedLineScore = line.setupFirstAdjustedScore,
            reorderedLineScore = line.reordered?.let { line.focalFirstAdjustedScore },
            activePayoffsBeforeDeployment = activePayoffs(line.setupStart, line.setup.action.playerId),
            activePayoffsAfterDeployment = activePayoffs(line.setup.state, line.setup.action.playerId),
            additionalImmediatePayoffValue = line.reordered?.let {
                line.setupFirstAdjustedScore - line.focalFirstAdjustedScore
            },
            setupFirstResourcesAfter = resources(line.setupFirstFinalState, line.setup.action.playerId),
            focalFirstResourcesAfter = line.reordered?.let { resources(line.focalFirstFinalState, line.setup.action.playerId) },
            completeResourcesEquivalent = line.reordered?.let {
                equivalentResources(line.setupFirstFinalState, line.focalFirstFinalState, line.setup.action.playerId)
            },
            materiallySuperior = superior,
            productionAdmissible = line.production.productionAdmissible,
            productionRejectionOrHoldReason = line.production.rejectionReason,
            staticBoardValue = line.production.staticBoardValue,
            strategicSequencingAdjustment = line.production.sequencingAdjustment,
            adjustedValue = line.production.adjustedValue,
            passValue = line.production.passValue,
            continuationHorizon = maxOf(setupFirstLine.count { it.startsWith("cast ") }, focalFirstLine.count { it.startsWith("cast ") }),
            completeComparedContinuation = focalFirstLine,
            reason = when {
                !line.production.productionAdmissible ->
                    "setup rejected by production: ${line.production.rejectionReason ?: "unspecified hold"}"
                superior -> "production-admissible complete setup-first line exceeds the equivalently bounded focal-first continuation by more than $MATERIAL_MARGIN"
                else -> "production-admissible complete setup-first line does not materially exceed the production-adjusted focal-first continuation"
            },
        )
    }

    private fun evaluation(line: StillLine): PreSpellSetupEvaluation = PreSpellSetupEvaluation(
        relevantAction = line.spellName,
        proposedLandPlay = line.land.name,
        landEntersTapped = line.land.after.getEntity(line.land.id)?.has<TappedComponent>(),
        classifications = listOf(PreSpellSetupClassification.STILL_UNEXECUTABLE_AFTER_LAND),
        setupPrefix = listOf("play ${line.land.name}", "cast ${line.spellName}"),
        completeSetupContinuation = emptyList(),
        proposedActionOrder = listOf("play ${line.land.name}", "cast ${line.spellName}"),
        steps = listOf(
            SetupActionAudit("play ${line.land.name}", line.land.id, resourcesBefore = resources(line.land.before, line.cast.playerId), resourcesAfter = resources(line.land.after, line.cast.playerId)),
            SetupActionAudit(
                action = "cast ${line.spellName}",
                cardId = line.cast.cardId,
                manaCost = line.manaCost,
                coloredRequirements = line.manaCost,
                targets = line.cast.targets.mapNotNull {
                    (it as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent)?.entityId
                },
                targetDetails = line.cast.targets.mapNotNull {
                    (it as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent)?.entityId
                }.map { entity(line.land.after, it) },
                resourcesBefore = resources(line.land.after, line.cast.playerId),
            ),
        ),
        counterfactualLineEvaluated = emptyList(),
        comparisonLineEvaluated = emptyList(),
        materiallySuperior = false,
        reason = "spell or complete continuation remains unexecutable after the legal land play",
    )

    private fun actionAudit(label: String, outcome: CastOutcome, before: GameState): SetupActionAudit {
        val explicit = (outcome.action.paymentStrategy as? PaymentStrategy.Explicit)?.manaAbilitiesToActivate.orEmpty()
        val inferred = before.projectedState.getBattlefieldControlledBy(outcome.action.playerId).filter { id ->
            before.getEntity(id)?.has<TappedComponent>() != true && outcome.state.getEntity(id)?.has<TappedComponent>() == true
        }
        val sources = (explicit.ifEmpty { inferred }).map { manaSource(before, it) }
        val targets = outcome.action.targets.mapNotNull {
            (it as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent)?.entityId
        }
        val costs = additionalCosts(before, outcome.action.additionalCostPayment)
        return SetupActionAudit(
            action = label,
            cardId = outcome.action.cardId,
            semanticActionIdentity = semanticIdentity(before, outcome),
            manaCost = outcome.manaCost,
            coloredRequirements = outcome.manaCost,
            targets = targets,
            targetDetails = targets.map { entity(before, it) },
            additionalCostMode = costs.firstOrNull()?.kind ?: "none",
            additionalCosts = costs,
            paymentSources = sources,
            resourcesBefore = resources(before, outcome.action.playerId),
            resourcesAfter = resources(outcome.state, outcome.action.playerId),
        )
    }

    private fun semanticIdentity(state: GameState, outcome: CastOutcome): SetupSemanticActionIdentity {
        val card = state.getEntity(outcome.action.cardId)?.get<CardComponent>()
        val targets = outcome.action.targets.mapNotNull {
            (it as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent)?.entityId
        }
        val costs = additionalCosts(state, outcome.action.additionalCostPayment)
        return SetupSemanticActionIdentity(
            actionType = "CastSpell",
            cardDefinitionId = card?.cardDefinitionId ?: outcome.action.cardId.toString(),
            castFaceIndex = outcome.action.faceIndex,
            sourceZone = when (outcome.action.cardId) {
                in state.getHand(outcome.action.playerId) -> "HAND"
                in state.getGraveyard(outcome.action.playerId) -> "GRAVEYARD"
                in state.getExile(outcome.action.playerId) -> "EXILE"
                else -> null
            },
            controllerId = outcome.action.playerId,
            chosenModes = outcome.action.chosenModes,
            targets = targets,
            xValue = outcome.action.xValue,
            alternativeCost = outcome.action.alternativeCostType?.name
                ?: outcome.action.alternativePayment?.toString(),
            additionalCostMode = costs.firstOrNull()?.kind ?: "none",
            paymentRequirement = outcome.manaCost,
        )
    }

    private fun additionalCosts(state: GameState, payment: AdditionalCostPayment?): List<SetupAdditionalCostAudit> =
        buildList {
            fun add(kind: String, ids: List<EntityId>) {
                if (ids.isNotEmpty()) add(SetupAdditionalCostAudit(kind, ids.map { entity(state, it) }))
            }
            add("sacrifice", payment?.sacrificedPermanents.orEmpty())
            add("discard", payment?.discardedCards.orEmpty())
            add("exile", payment?.exiledCards.orEmpty())
            add("tap", payment?.tappedPermanents.orEmpty())
            add("return-to-hand", payment?.bouncedPermanents.orEmpty())
            payment?.lifePaid?.takeIf { it > 0 }?.let { add(SetupAdditionalCostAudit("life", amount = it)) }
        }

    private fun entity(state: GameState, id: EntityId) = SetupEntityAudit(
        id = id,
        name = name(state, id),
        controllerId = state.projectedState.getController(id),
    )

    private fun resources(state: GameState, playerId: EntityId): SetupResourceState {
        val sources = state.projectedState.getBattlefieldControlledBy(playerId)
            .filter { state.projectedState.hasType(it, "LAND") }.map { manaSource(state, it) }
        val pool = state.getEntity(playerId)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
        return SetupResourceState(
        manaSources = sources,
        untappedManaSourceCount = sources.count { !it.tapped },
        floatingMana = linkedMapOf(
            "white" to pool.white, "blue" to pool.blue, "black" to pool.black,
            "red" to pool.red, "green" to pool.green, "colorless" to pool.colorless,
        ),
        handSize = state.getHand(playerId).size,
        spellsCastThisTurn = state.spellsCastThisTurn,
        temporaryConditions = buildList {
            if (state.getEntity(playerId)?.has<LifeGainedThisTurnComponent>() == true) add("life-gained-this-turn")
            if (state.spellsCastThisTurn > 0) add("spells-cast-this-turn=${state.spellsCastThisTurn}")
        },
        )
    }

    private fun activePayoffs(state: GameState, playerId: EntityId): List<String> =
        state.projectedState.getBattlefieldControlledBy(playerId).mapNotNull { id ->
            val permanent = state.getEntity(id) ?: return@mapNotNull null
            val card = permanent.get<CardComponent>() ?: return@mapNotNull null
            card.name.takeIf { intents.forPermanent(permanent, card.name).any { it.repeatable } }
        }.sorted()

    private fun equivalentResources(first: GameState, second: GameState, playerId: EntityId): Boolean {
        val a = resources(first, playerId)
        val b = resources(second, playerId)
        return a.untappedManaSourceCount == b.untappedManaSourceCount &&
            a.floatingMana == b.floatingMana && a.handSize == b.handSize &&
            a.spellsCastThisTurn == b.spellsCastThisTurn
    }

    private fun manaSource(state: GameState, id: EntityId) = SetupManaSource(id, name(state, id), state.getEntity(id)?.has<TappedComponent>() == true)

    private fun describe(line: SetupLine, focalName: String? = null): List<String> =
        listOfNotNull(
            line.landName?.let { "play $it" },
            "cast ${line.spellName}",
            focalName?.let { "cast $it" },
        )

    private fun SimulationResult.usableState(): GameState? = when (this) {
        is SimulationResult.Illegal, is SimulationResult.StoppedAtLimit -> null
        else -> state
    }

    private fun score(state: GameState, playerId: EntityId): Double =
        evaluator.evaluate(state, state.projectedState, playerId)

    private fun name(state: GameState, id: EntityId): String =
        state.getEntity(id)?.get<CardComponent>()?.name ?: id.toString()

    private data class SetupLine(
        val landName: String?,
        val landCardId: EntityId?,
        val setupCardId: EntityId,
        val spellName: String,
        val completedScore: Double,
        val setupUseful: Boolean,
        val setupStart: GameState,
        val land: LandOutcome?,
        val setup: CastOutcome,
        val focal: CastOutcome,
        val production: ProductionCastCandidate,
        val reordered: ReorderedLine? = null,
        val continuation: CommonContinuation? = null,
    ) {
        val setupFirstFinalState: GameState get() = continuation?.setupFirst?.state ?: focal.state
        val focalFirstFinalState: GameState get() = continuation?.focalFirst?.state ?: requireNotNull(reordered).state
        val setupFirstAdjustedScore: Double get() =
            scoreOf(setupFirstFinalState) + production.expiringConditionSequencingAdjustment
        val focalFirstAdjustedScore: Double get() =
            scoreOf(focalFirstFinalState) +
                (reordered?.production?.expiringConditionSequencingAdjustment ?: 0.0)

        private fun scoreOf(position: GameState): Double =
            AIPlayer.defaultEvaluator().evaluate(position, position.projectedState, setup.action.playerId)
    }
    private data class ReorderedLine(
        val score: Double,
        val state: GameState,
        val focal: CastOutcome,
        val land: LandOutcome?,
        val setup: CastOutcome,
        val production: ProductionCastCandidate,
    )
    private data class ProductionCastCandidate(
        val legal: com.wingedsheep.engine.legalactions.LegalAction,
        val action: CastSpell,
        val productionAdmissible: Boolean,
        val rejectionReason: String?,
        val staticBoardValue: Double?,
        val sequencingAdjustment: Double,
        val expiringConditionSequencingAdjustment: Double,
        val adjustedValue: Double?,
        val passValue: Double,
        val chosen: Boolean,
    )
    private data class CommonContinuation(
        val setupFirst: CastOutcome,
        val focalFirst: CastOutcome,
        val setupFirstProduction: ProductionCastCandidate,
        val focalFirstProduction: ProductionCastCandidate,
    )
    private data class LandOutcome(val before: GameState, val after: GameState, val name: String, val id: EntityId)
    private data class CastOutcome(val action: CastSpell, val manaCost: String?, val before: GameState, val state: GameState)
    private data class StillLine(val land: LandOutcome, val cast: CastSpell, val manaCost: String?, val spellName: String)

    private companion object {
        const val MATERIAL_MARGIN = 0.1
    }
}
