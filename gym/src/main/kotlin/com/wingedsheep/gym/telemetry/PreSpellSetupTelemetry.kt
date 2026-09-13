package com.wingedsheep.gym.telemetry

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.TargetSelection
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
    val completedLineScore: Double? = null,
    val reorderedLineScore: Double? = null,
    val materiallySuperior: Boolean,
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
            line.copy(reorderedScore = reorderedScore(state, playerId, focalCardId, line))
        }
        val superior = assessed.filter { line ->
            line.setupUseful && line.completedScore > line.reorderedScore + MATERIAL_MARGIN
        }
        val best = superior.maxByOrNull(SetupLine::completedScore)
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
        return simulator.getLegalActions(state, playerId).mapNotNull { legal ->
            val cast = legal.action as? CastSpell ?: return@mapNotNull null
            if (!legal.affordable || cast.cardId == focalCardId) return@mapNotNull null
            val setupStates = castStates(state, playerId, legal)
            val setupUseful = setupStates.any { score(it.state, playerId) > baseline + MATERIAL_MARGIN }
            val completed = setupStates.flatMap { setup ->
                castStates(setup.state, playerId, focalCardId).map { focal -> setup to focal }
            }.maxByOrNull { (_, focal) -> score(focal.state, playerId) }
                ?: return@mapNotNull null
            SetupLine(landName, landCardId, cast.cardId, name(state, cast.cardId),
                score(completed.second.state, playerId), setupUseful, state, landOutcome, completed.first, completed.second)
        }
    }

    /**
     * Compare like with like: if the same land and setup can follow the focal spell, their complete
     * result is the sequencing baseline. This prevents an outcome-equivalent land timing from being
     * called superior while preserving real order-sensitive value such as a setup spell increasing
     * the focal spell's storm count or enabling a temporary condition before it resolves.
     */
    private fun reorderedScore(
        state: GameState,
        playerId: EntityId,
        focalCardId: EntityId,
        line: SetupLine,
    ): Double {
        return castStates(state, playerId, focalCardId).maxOfOrNull { focal ->
            val afterFocal = focal.state
            val afterLand = line.landCardId?.let { landId ->
                val land = simulator.getLegalActions(afterFocal, playerId).firstOrNull { candidate ->
                    (candidate.action as? PlayLand)?.cardId == landId
                } ?: return@maxOfOrNull score(afterFocal, playerId)
                simulator.simulate(afterFocal, land.action).usableState()
                    ?: return@maxOfOrNull score(afterFocal, playerId)
            } ?: afterFocal
            val continuationScores = simulator.getLegalActions(afterLand, playerId).mapNotNull { candidate ->
                val continuation = candidate.action as? CastSpell ?: return@mapNotNull null
                if (!candidate.affordable || continuation.cardId == focalCardId) {
                    return@mapNotNull null
                }
                castStates(afterLand, playerId, candidate).maxOfOrNull { score(it.state, playerId) }
            }
            continuationScores.maxOrNull() ?: score(afterLand, playerId)
        } ?: Double.NEGATIVE_INFINITY
    }

    /** Explore legal explicit source choices as well as auto-pay so telemetry tests feasibility,
     * not an auto-tapper's incidental choice of which otherwise-equivalent land supplies generic
     * mana. Invalid color/source selections are rejected by the authoritative simulator. */
    private fun castStates(state: GameState, playerId: EntityId, cardId: EntityId): List<CastOutcome> =
        simulator.getLegalActions(state, playerId).filter { candidate ->
            val cast = candidate.action as? CastSpell
            candidate.affordable && cast?.cardId == cardId
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
        }
        return PreSpellSetupEvaluation(
            relevantAction = line.spellName,
            proposedLandPlay = line.landName,
            landEntersTapped = line.land?.after?.getEntity(line.land.id)?.has<TappedComponent>(),
            classifications = classifications,
            setupPrefix = describe(line),
            completeSetupContinuation = describe(line, focalName),
            proposedActionOrder = describe(line, focalName),
            steps = steps,
            counterfactualLineEvaluated = describe(line, focalName),
            comparisonLineEvaluated = listOf("cast $focalName") + describe(line),
            completedLineScore = line.completedScore,
            reorderedLineScore = line.reorderedScore,
            materiallySuperior = superior,
            reason = if (superior) "complete setup-first line exceeds focal-first continuation by more than $MATERIAL_MARGIN" else
                "complete setup-first line does not materially exceed the focal-first continuation",
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
        val reorderedScore: Double = Double.NEGATIVE_INFINITY,
    )
    private data class LandOutcome(val before: GameState, val after: GameState, val name: String, val id: EntityId)
    private data class CastOutcome(val action: CastSpell, val manaCost: String?, val before: GameState, val state: GameState)
    private data class StillLine(val land: LandOutcome, val cast: CastSpell, val manaCost: String?, val spellName: String)

    private companion object {
        const val MATERIAL_MARGIN = 0.1
    }
}
