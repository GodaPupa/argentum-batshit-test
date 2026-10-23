package com.wingedsheep.engine.core

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.DoubleFacedComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.effects.Effect
import kotlinx.serialization.Serializable

/** Future token data is saved here, without exposing an entity or a battlefield arrival. */
@Serializable
data class PendingTokenEntry(
    val id: EntityId,
    val card: CardComponent,
    val doubleFaced: DoubleFacedComponent? = null,
    val questions: List<PendingTokenQuestion> = emptyList(),
    val questionIndex: Int = 0,
    val choices: CastChoicesComponent = CastChoicesComponent(),
    val riotModes: List<String> = emptyList(),
    val hostId: EntityId? = null,
    val hostChosen: Boolean = false,
    val defenderId: EntityId? = null,
    val defenderChosen: Boolean = false,
    val omitted: Boolean = false,
)

@Serializable
data class PendingTokenQuestion(val choice: EntersWithChoice, val grantedRiot: Boolean = false)

/** One creation instruction. The required entry checkpoint cannot be invented when loading. */
@Serializable
data class PendingTokenEntryPlan(
    val effect: Effect,
    val context: EffectContext,
    val controllerId: EntityId,
    val beforeEntry: GameState,
    val entries: List<PendingTokenEntry>,
    val index: Int = 0,
)

@Serializable
data class PendingTokenChoiceContinuation(
    val plan: PendingTokenEntryPlan,
    val choice: EntersWithChoiceOnBattlefieldContinuation,
    val grantedRiot: Boolean,
) : AnswerContinuation

@Serializable
data class PendingTokenHostContinuation(
    val plan: PendingTokenEntryPlan,
    val legalHosts: List<EntityId>,
) : AnswerContinuation

@Serializable
data class PendingTokenDefenderContinuation(
    val plan: PendingTokenEntryPlan,
    val legalDefenders: List<EntityId>,
) : AnswerContinuation
