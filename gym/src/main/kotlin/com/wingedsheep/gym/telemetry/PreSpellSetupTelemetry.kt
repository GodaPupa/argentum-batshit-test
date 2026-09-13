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
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

@Serializable
data class LandUnlockedSpell(
    val land: String,
    val spell: String,
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
            Triple(landState, landName, play.cardId)
        }
        val afterLand = landStates.flatMap { (landState, landName, landId) ->
            setupLines(landState, playerId, focalCardId, landName, landId)
        }
        val stillUnexecutable = landStates.flatMap { (landState, landName, _) ->
            simulator.getLegalActions(landState, playerId).mapNotNull { legal ->
                val cast = legal.action as? CastSpell ?: return@mapNotNull null
                if (legal.requiresTargets || cast.cardId == focalCardId) return@mapNotNull null
                val complete = castStates(landState, playerId, cast.cardId).any { setupState ->
                    castStates(setupState, playerId, focalCardId).isNotEmpty()
                }
                LandUnlockedSpell(landName, name(landState, cast.cardId)).takeUnless { complete }
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
            stillUnexecutableAfterLegalLandPlay = stillUnexecutable.distinct()
                .sortedWith(compareBy(LandUnlockedSpell::land, LandUnlockedSpell::spell)),
            executableButNotMateriallySuperior = nonSuperior.map(::describe).distinct().sortedBy { it.joinToString() },
            bestValidatedSetupSequence = best?.let { line ->
                describe(line, name(state, focalCardId))
            },
            focalCastBeforeSuperiorSetup = best != null,
        )
    }

    private fun setupLines(
        state: GameState,
        playerId: EntityId,
        focalCardId: EntityId,
        landName: String?,
        landCardId: EntityId? = null,
    ): List<SetupLine> {
        val baseline = score(state, playerId)
        return simulator.getLegalActions(state, playerId).mapNotNull { legal ->
            val cast = legal.action as? CastSpell ?: return@mapNotNull null
            if (!legal.affordable || legal.requiresTargets || cast.cardId == focalCardId) return@mapNotNull null
            val setupStates = castStates(state, playerId, cast.cardId)
            val setupUseful = setupStates.any { score(it, playerId) > baseline + MATERIAL_MARGIN }
            val completedScore = setupStates.asSequence()
                .flatMap { setupState -> castStates(setupState, playerId, focalCardId).asSequence() }
                .maxOfOrNull { score(it, playerId) }
                ?: return@mapNotNull null
            SetupLine(landName, landCardId, cast.cardId, name(state, cast.cardId), completedScore, setupUseful)
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
        return castStates(state, playerId, focalCardId).maxOfOrNull { afterFocal ->
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
                castStates(afterLand, playerId, continuation.cardId).maxOfOrNull { score(it, playerId) }
            }
            continuationScores.maxOrNull() ?: score(afterLand, playerId)
        } ?: Double.NEGATIVE_INFINITY
    }

    /** Explore legal explicit source choices as well as auto-pay so telemetry tests feasibility,
     * not an auto-tapper's incidental choice of which otherwise-equivalent land supplies generic
     * mana. Invalid color/source selections are rejected by the authoritative simulator. */
    private fun castStates(state: GameState, playerId: EntityId, cardId: EntityId): List<GameState> {
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
        return actions.mapNotNull { simulator.simulate(state, it).usableState() }
    }

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
        val reorderedScore: Double = Double.NEGATIVE_INFINITY,
    )

    private companion object {
        const val MATERIAL_MARGIN = 0.1
    }
}
