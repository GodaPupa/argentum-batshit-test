package com.wingedsheep.engine.core

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.sdk.scripting.effects.Effect
import kotlinx.serialization.Serializable

/** Announced entry data, held outside every permanent until the complete batch can enter. */
@Serializable
data class PendingPermanentEntry(
    val source: ObjectRef,
    val controller: EntityId,
    val host: ObjectRef? = null,
    val needsHost: Boolean = false,
    val choices: Map<ChoiceSlot, ChoiceValue> = emptyMap(),
    val choiceCursor: Int = 0,
    val skipped: Boolean = false,
    val sourceZone: com.wingedsheep.engine.state.ZoneKey? = null
)

@Serializable
data class PreEntryOperation(
    val effect: Effect,
    val context: EffectContext,
    val entries: List<PendingPermanentEntry>,
    val cursor: Int = 0
)

@Serializable
data class PreEntryContinuation(
    val operation: PreEntryOperation,
    val choiceFormat: EntersWithChoiceOnBattlefieldContinuation? = null,
    val legalHosts: List<ObjectRef> = emptyList()
) : AnswerContinuation
