package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.CipherEncodeContinuation
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.sdk.scripting.effects.CipherEncodeEffect
import kotlin.reflect.KClass

/** Presents cipher's optional, non-targeting creature choice at the end of spell resolution. */
class CipherEncodeExecutor : EffectExecutor<CipherEncodeEffect> {
    override val effectType: KClass<CipherEncodeEffect> = CipherEncodeEffect::class

    override fun execute(
        state: GameState,
        effect: CipherEncodeEffect,
        context: EffectContext,
    ): EffectResult {
        val sourceId = context.sourceId ?: return EffectResult.success(state)
        val source = state.getEntity(sourceId) ?: return EffectResult.success(state)
        // CR 702.99a says "this spell card"; a copied card/spell is not a card.
        if (source.has<CopyOfComponent>()) return EffectResult.success(state)

        val creatures = state.projectedState.getBattlefieldControlledBy(context.controllerId)
            .filter { state.projectedState.isCreature(it) }
        if (creatures.isEmpty()) return EffectResult.success(state)

        val sourceName = source.get<CardComponent>()?.name
        val question = { decisionId: String ->
            SelectCardsDecision(
                id = decisionId,
                playerId = context.controllerId,
                prompt = "You may encode ${sourceName ?: "this spell"} on a creature you control",
                context = DecisionContext(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    phase = DecisionPhase.RESOLUTION,
                ),
                options = creatures,
                minSelections = 0,
                maxSelections = 1,
            )
        }
        val continuation = CipherEncodeContinuation(
            playerId = context.controllerId,
            sourceId = sourceId,
            legalCreatureIds = creatures.toSet(),
        )
        return EffectResult.from(state.suspendForDecision(question, continuation))
    }
}
