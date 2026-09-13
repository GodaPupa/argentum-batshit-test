package com.wingedsheep.gym.telemetry

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
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
    val proposedActionOrder: List<String>,
    val steps: List<SetupActionAudit>,
    val actualLineTaken: List<String> = emptyList(),
    val counterfactualLineEvaluated: List<String>,
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
                if (legal.requiresTargets || cast.cardId == focalCardId) return@mapNotNull null
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
            if (!legal.affordable || legal.requiresTargets || cast.cardId == focalCardId) return@mapNotNull null
            val setupStates = castStates(state, playerId, cast.cardId)
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
                if (!candidate.affordable || candidate.requiresTargets || continuation.cardId == focalCardId) {
                    return@mapNotNull null
                }
                castStates(afterLand, playerId, continuation.cardId).maxOfOrNull { score(it.state, playerId) }
            }
            continuationScores.maxOrNull() ?: score(afterLand, playerId)
        } ?: Double.NEGATIVE_INFINITY
    }

    /** Explore legal explicit source choices as well as auto-pay so telemetry tests feasibility,
     * not an auto-tapper's incidental choice of which otherwise-equivalent land supplies generic
     * mana. Invalid color/source selections are rejected by the authoritative simulator. */
    private fun castStates(state: GameState, playerId: EntityId, cardId: EntityId): List<CastOutcome> {
        val legal = simulator.getLegalActions(state, playerId).firstOrNull { candidate ->
            val cast = candidate.action as? CastSpell
            candidate.affordable && cast?.cardId == cardId
        } ?: return emptyList()
        val base = legal.action as CastSpell
        val lands = state.projectedState.getBattlefieldControlledBy(playerId).filter { id ->
            state.projectedState.hasType(id, "LAND") &&
                state.getEntity(id)?.has<TappedComponent>() != true
        }
        val manaValue = legal.manaCostString?.let { com.wingedsheep.sdk.core.ManaCost.parse(it).cmc }
            ?: lands.size
        val maxSources = minOf(lands.size, manaValue + 1)
        val actions = buildList {
            add(base)
            fun combinations(start: Int, remaining: Int, chosen: MutableList<EntityId>) {
                if (remaining == 0) {
                    add(base.copy(paymentStrategy = PaymentStrategy.Explicit(chosen.toList())))
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
        return actions.mapNotNull { action ->
            simulator.simulate(state, action).usableState()?.let { CastOutcome(action, legal.manaCostString, state, it) }
        }
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
            proposedActionOrder = describe(line, focalName),
            steps = steps,
            counterfactualLineEvaluated = listOf("cast $focalName") + describe(line),
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
        proposedActionOrder = listOf("play ${line.land.name}", "cast ${line.spellName}"),
        steps = listOf(
            SetupActionAudit("play ${line.land.name}", line.land.id, resourcesBefore = resources(line.land.before, line.cast.playerId), resourcesAfter = resources(line.land.after, line.cast.playerId)),
            SetupActionAudit("cast ${line.spellName}", line.cast.cardId, line.manaCost, line.manaCost,
                line.cast.targets.mapNotNull { (it as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent)?.entityId },
                resourcesBefore = resources(line.land.after, line.cast.playerId)),
        ),
        counterfactualLineEvaluated = listOf("play ${line.land.name}", "cast ${line.spellName}"),
        materiallySuperior = false,
        reason = "spell or complete continuation remains unexecutable after the legal land play",
    )

    private fun actionAudit(label: String, outcome: CastOutcome, before: GameState): SetupActionAudit {
        val explicit = (outcome.action.paymentStrategy as? PaymentStrategy.Explicit)?.manaAbilitiesToActivate.orEmpty()
        val inferred = before.projectedState.getBattlefieldControlledBy(outcome.action.playerId).filter { id ->
            before.getEntity(id)?.has<TappedComponent>() != true && outcome.state.getEntity(id)?.has<TappedComponent>() == true
        }
        val sources = (explicit.ifEmpty { inferred }).map { manaSource(before, it) }
        return SetupActionAudit(label, outcome.action.cardId, outcome.manaCost, outcome.manaCost,
            outcome.action.targets.mapNotNull { (it as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent)?.entityId },
            sources, resources(before, outcome.action.playerId), resources(outcome.state, outcome.action.playerId))
    }

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
