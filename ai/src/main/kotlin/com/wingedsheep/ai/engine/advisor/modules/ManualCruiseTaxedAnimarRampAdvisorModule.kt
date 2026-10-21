package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Independent CRUISE R1 opt-in experiment. R5, CURRENT and the existing
 * ManualAnimarDevelopmentAdvisor are never modified. One registered card only.
 */
object ManualCruiseTaxedAnimarRampAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(ManualCruiseTaxedAnimarRampAdvisor)
    }
}

/**
 * Credit an ACTUAL, immediately affordable Llanowar Elves spell only if the
 * one-creature addition makes a previously-unaffordable command-zone Animar
 * payable in a bounded same-resource READY-MANA projection.
 *
 * Projection semantics: hypothetically untap our current permanents and clear
 * summoning sickness to compare mana access; it does NOT perform a turn
 * transition, predict draws, guarantee that Elves survives, model enemy turns,
 * or claim a future win. All payment checks use the actual legal-action engine.
 *
 * No unconditional "ramp is good" bonus; no extra candidate searches; no
 * direct profile/CURRENT changes. Losing an immediate win remains possible only
 * if a future-resource option were somehow scored above that win; the credited
 * score is capped at pass-score + 0.05, not an invented terminal value.
 */
object ManualCruiseTaxedAnimarRampAdvisor : CardAdvisor {
    override val cardNames = setOf("Llanowar Elves")

    private fun readyManaProjection(state: GameState, playerId: EntityId): GameState {
        var projected = state
        for (id in state.controlledBattlefield(playerId)) {
            projected = projected.updateEntity(id) { container ->
                container.without<TappedComponent>()
                    .without<SummoningSicknessComponent>()
            }
        }
        return projected
    }

    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action.action as? CastSpell ?: return null
        val before = context.state
        val player = context.playerId
        if (!context.action.affordable ||
            context.action.requiresTargets ||
            context.action.hasXCost ||
            !context.defaultScore.isFinite() ||
            !context.passScore.isFinite() ||
            before.activePlayerId != player ||
            !before.phase.isMainPhase ||
            before.stack.isNotEmpty() ||
            cast.cardId !in before.getZone(ZoneKey(player, Zone.HAND))
        ) return null
        if (before.getEntity(cast.cardId)?.get<CardComponent>()?.name != "Llanowar Elves") {
            return null
        }

        val commanderId = before.getZone(ZoneKey(player, Zone.COMMAND))
            .singleOrNull { id ->
                before.getEntity(id)?.get<CardComponent>()?.name ==
                    "Animar, Soul of Elements"
            } ?: return null

        fun commandPayable(state: GameState): Boolean =
            context.simulator.getLegalActions(state, player).any {
                it.affordable && (it.action as? CastSpell)?.cardId == commanderId
            }

        // No boost if Animar is already payable from resources that would
        // refresh even without casting the Elf.
        if (commandPayable(readyManaProjection(before, player))) return null

        val simulated = context.simulator.simulate(before, cast) as?
            SimulationResult.Terminal ?: return null
        val after = simulated.state
        if (after.gameOver ||
            after.activePlayerId != player ||
            after.turnNumber != before.turnNumber ||
            cast.cardId !in after.getZone(ZoneKey(player, Zone.BATTLEFIELD))
        ) return null

        val beforePermanents = before.controlledBattlefield(player).toSet()
        val addedPermanents = after.controlledBattlefield(player).toSet() - beforePermanents
        // Avoid granting development credit for an unrelated simulated
        // permanent/tutor/extra mana source: only the cast Elf can be new.
        if (addedPermanents != setOf(cast.cardId)) return null

        if (!commandPayable(readyManaProjection(after, player))) return null

        // A modest preference to INVEST now in real prospective access.
        // This is not an absolute command-cast bonus and does not override
        // the separate lethal / protection scoring mechanisms.
        return maxOf(context.defaultScore, context.passScore + 0.05)
    }
}
