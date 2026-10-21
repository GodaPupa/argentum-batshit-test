package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.LandPlayContext
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** CRUISE R9: explicitly opt-in; never installed on R5 or AiProfile.CURRENT. */
object ManualCruiseR9ExecutableFetchAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(ManualCruiseR9ExecutableFetchAdvisor)
    }
}

/**
 * Bounded land *sequencing* score for an already-held, fully legal fetch.
 *
 * This is NOT future-game prediction. A single source-bound probe:
 *    legal land drop → legal activated fetch → explicit all-pass stack clearing
 *    → genuine searchable basic Forest → actual untapped land
 *    → an immediately funded 1-mana ramp creature CastSpell.
 *
 * Every step uses ActionProcessor/LegalActionEnumerator, not imagined mana.
 * The four opponent priority passes are a CONDITIONAL MECHANISM witness only.
 * Opponent willingness to pass, survival of Birds, later card draws and a
 * subsequent Animar cast are NOT proved or credited. No other turn is advanced.
 * A missing/different decision, unavailable Forest, unfunded ability/cast,
 * lethal mana payment or an entered-tapped land fails closed to R5 scoring.
 *
 * A tiny 0.125 score tie-break, not a general ramp/land/commander bonus, leaves
 * genuine wins and existing R5 survival priorities authoritative.
 */
object ManualCruiseR9ExecutableFetchAdvisor : CardAdvisor {
    override val cardNames: Set<String> = setOf("Misty Rainforest", "Evolving Wilds")

    private val eligibleRamp = setOf(
        "Birds of Paradise", "Llanowar Elves", "Elvish Mystic",
        "Fyndhorn Elves", "Arbor Elf",
    )
    private const val MAX_PRIORITY_PASSES = 8
    private const val TIE_BREAK_BONUS = 0.125

    override fun evaluateLandPlay(context: LandPlayContext): Double? {
        val action = context.action.action as? PlayLand ?: return null
        if (action.asBackFace || !context.action.affordable ||
            !context.defaultScore.isFinite() || !context.passScore.isFinite()
        ) return null
        val before = context.state
        val player = context.playerId
        if (before.gameOver || before.pendingDecision != null ||
            before.stack.isNotEmpty() || before.activePlayerId != player ||
            before.priorityPlayerId != player || !before.phase.isMainPhase
        ) return null
        if (action.cardId !in before.getZone(ZoneKey(player, Zone.HAND))) return null
        val name = before.getEntity(action.cardId)?.get<CardComponent>()?.name ?: return null
        if (name !in cardNames) return null

        // This advisor is intentionally limited to an opening-turn enabling
        // action, not a generic fetch optimization on a complex board.
        val currentLands = before.controlledBattlefield(player).count {
            before.projectedState.hasType(it, "LAND")
        }
        if (currentLands > 1) return null

        val rampIds = before.getZone(ZoneKey(player, Zone.HAND)).filter { id ->
            val card = before.getEntity(id)?.get<CardComponent>()
            card?.typeLine?.isCreature == true &&
                card.name in eligibleRamp && card.manaValue <= 1
        }.toSet()
        if (rampIds.isEmpty()) return null

        // Only an *unlocked* cast can earn incremental resource credit.
        if (context.simulator.getLegalActions(before, player).any {
            it.affordable && (it.action as? CastSpell)?.cardId in rampIds
        }) return null

        // No synthetic land creation. A fresh ActionProcessor is an isolated,
        // immutable state branch, not a simulator that silently resolves foes.
        val processor = ActionProcessor(context.simulator.cardRegistry)
        fun process(state: GameState, action: GameAction): GameState? {
            val processed = processor.process(state, action).result
            return if (processed.error == null && !processed.state.gameOver &&
                processed.state.lifeTotal(player) > 0) processed.state else null
        }

        var fork = process(before, action) ?: return null
        if (action.cardId !in fork.getZone(ZoneKey(player, Zone.BATTLEFIELD))) return null
        val activations = context.simulator.getLegalActions(fork, player).filter {
            it.affordable && !it.requiresTargets &&
                (it.action as? ActivateAbility)?.sourceId == action.cardId
        }
        if (activations.size != 1) return null
        val activation = activations.single().action as ActivateAbility
        fork = process(fork, activation) ?: return null
        if (action.cardId !in fork.getZone(ZoneKey(player, Zone.GRAVEYARD))) return null

        // Exactly one fetch resolution. We do NOT make other strategic actions,
        // opponent draws, extra land drops, or subsequent game turns. These
        // automatic passes prove mechanism availability only, never its success
        // against responding opponents.
        var passes = 0
        while (fork.pendingDecision == null && fork.stack.isNotEmpty()) {
            if (++passes > MAX_PRIORITY_PASSES) return null
            val priority = fork.priorityPlayerId ?: return null
            fork = process(fork, PassPriority(priority)) ?: return null
        }

        val search = fork.pendingDecision as? SelectCardsDecision ?: return null
        // A hidden-library search may legally be represented as 0..1 (the
        // player can decline to find), even though the intended line chooses
        // precisely one genuinely offered legal Forest. Require 1 to be
        // permitted, not mandatory: a 1..1 requirement is not universal.
        if (search.playerId != player || search.maxSelections < 1 ||
            search.minSelections > 1) return null
        // Basic Forest avoids unresolved optional shockland entry decisions.
        // Entity IDs (not positions in a library) only disambiguate same-name
        // legal search targets; no peek at cards on top or future draws.
        val forest = search.options.filter { id ->
            fork.getEntity(id)?.get<CardComponent>()?.name == "Forest"
        }.minByOrNull { it.value } ?: return null
        fork = process(fork, SubmitDecision(
            player, CardsSelectedResponse(search.id, listOf(forest))
        )) ?: return null
        if (fork.pendingDecision != null || fork.stack.isNotEmpty()) return null
        if (forest !in fork.getZone(ZoneKey(player, Zone.BATTLEFIELD)) ||
            fork.getEntity(forest)?.has<TappedComponent>() == true) return null
        if (fork.turnNumber != before.turnNumber ||
            fork.activePlayerId != player || fork.priorityPlayerId != player) return null

        val funded = context.simulator.getLegalActions(fork, player).firstOrNull {
            it.affordable && (it.action as? CastSpell)?.cardId in rampIds
        } ?: return null
        val cast = funded.action as? CastSpell ?: return null
        val castResult = processor.process(fork, cast).result
        if (castResult.error != null || castResult.state.lifeTotal(player) <= 0 ||
            castResult.events.none { event ->
                event is SpellCastEvent &&
                    event.cardName in eligibleRamp && event.casterId == player
            }
        ) return null

        // An observed legal, payable, same-turn ramp cast is a strictly bounded
        // resource-access tie-break only; never a conditional-game win score.
        return context.defaultScore + TIE_BREAK_BONUS
    }
}
