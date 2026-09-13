package com.wingedsheep.gym.telemetry

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
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
    val bestValidatedSetupSequence: List<String>?,
    val focalCastBeforeSuperiorSetup: Boolean,
)

/**
 * Uses authoritative action enumeration and simulation to distinguish a spell that can really be
 * cast before the focal spell from one that becomes executable only after a legal same-turn land
 * play. A setup is reported only when it improves the position and leaves the focal spell payable;
 * the superior-line flag additionally requires the completed sequence to beat casting the focal
 * spell immediately.
 */
class PreSpellSetupTelemetry(private val registry: CardRegistry) {
    private val simulator = GameSimulator(registry)
    private val evaluator = AIPlayer.defaultEvaluator()

    fun observe(state: GameState, playerId: EntityId, focalCardId: EntityId): PreSpellSetupSnapshot {
        val immediate = simulator.getLegalActions(state, playerId).firstOrNull { legal ->
            val cast = legal.action as? CastSpell
            legal.affordable && cast?.cardId == focalCardId
        }
        val immediateScore = immediate?.let { legal ->
            simulator.simulate(state, legal.action).usableState()?.let { score(it, playerId) }
        } ?: Double.NEGATIVE_INFINITY

        val current = setupLines(state, playerId, focalCardId, landName = null)
        val afterLand = simulator.getLegalActions(state, playerId).mapNotNull { legal ->
            val play = legal.action as? PlayLand ?: return@mapNotNull null
            val result = simulator.simulate(state, play)
            val landState = result.usableState() ?: return@mapNotNull null
            val landName = name(state, play.cardId)
            setupLines(landState, playerId, focalCardId, landName)
        }.flatten()
        val best = (current + afterLand).maxByOrNull(SetupLine::completedScore)
            ?.takeIf { it.completedScore > immediateScore + MATERIAL_MARGIN }

        return PreSpellSetupSnapshot(
            currentlyExecutableBeforeFocal = current.map(SetupLine::spellName).distinct().sorted(),
            executableAfterLegalLandPlay = afterLand.map { LandUnlockedSpell(requireNotNull(it.landName), it.spellName) }
                .distinct().sortedWith(compareBy(LandUnlockedSpell::land, LandUnlockedSpell::spell)),
            bestValidatedSetupSequence = best?.let { line ->
                listOfNotNull(line.landName?.let { "play $it" }, "cast ${line.spellName}", "cast ${name(state, focalCardId)}")
            },
            focalCastBeforeSuperiorSetup = best != null,
        )
    }

    private fun setupLines(
        state: GameState,
        playerId: EntityId,
        focalCardId: EntityId,
        landName: String?,
    ): List<SetupLine> {
        val baseline = score(state, playerId)
        return simulator.getLegalActions(state, playerId).mapNotNull { legal ->
            val cast = legal.action as? CastSpell ?: return@mapNotNull null
            if (!legal.affordable || legal.requiresTargets || cast.cardId == focalCardId) return@mapNotNull null
            val setupResult = simulator.simulate(state, cast)
            val setupState = setupResult.usableState() ?: return@mapNotNull null
            if (score(setupState, playerId) <= baseline + MATERIAL_MARGIN) return@mapNotNull null
            val focal = simulator.getLegalActions(setupState, playerId).firstOrNull { next ->
                val nextCast = next.action as? CastSpell
                next.affordable && nextCast?.cardId == focalCardId
            } ?: return@mapNotNull null
            val completed = simulator.simulate(setupState, focal.action).usableState() ?: return@mapNotNull null
            SetupLine(landName, name(state, cast.cardId), score(completed, playerId))
        }
    }

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
        val spellName: String,
        val completedScore: Double,
    )

    private companion object {
        const val MATERIAL_MARGIN = 0.1
    }
}
