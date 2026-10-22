package com.wingedsheep.engine.core

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlinx.serialization.Serializable

/** Prepared characteristics belong to the future permanent, never its still-existing source object. */
@Serializable
data class PendingCardEntry(
    val source: ObjectRef,
    val controllerId: EntityId,
    val originalCard: CardComponent,
    val card: CardComponent = originalCard,
    val copy: CopyOfComponent? = null,
    val choices: CastChoicesComponent = CastChoicesComponent(),
    val copyProcessed: Boolean = false,
    val lastChoiceOrdinal: Int = -1,
    val tappedIfCopied: Boolean = false,
    val additionalCounters: DynamicAmount? = null,
    val exileAfterEntry: ObjectRef? = null,
)

/** A whole collection is prepared before its first member is published. */
@Serializable
data class PendingZoneEntryPlan(
    val effect: Effect,
    val context: EffectContext,
    val entries: List<PendingCardEntry>,
    val index: Int = 0,
)

@Serializable
data class PendingEntryCopyContinuation(
    val plan: PendingZoneEntryPlan,
    val replacement: EntersAsCopy,
    val candidates: List<ObjectRef>,
) : AnswerContinuation

/** Reuses the existing choice description; completion belongs to the pending entry plan. */
@Serializable
data class PendingEntryChoiceContinuation(
    val plan: PendingZoneEntryPlan,
    val choice: EntersWithChoiceOnBattlefieldContinuation,
) : AnswerContinuation
