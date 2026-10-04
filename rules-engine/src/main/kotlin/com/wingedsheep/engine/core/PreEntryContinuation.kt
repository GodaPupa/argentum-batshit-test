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
    val sourceZone: com.wingedsheep.engine.state.ZoneKey? = null,
    val copyEffect: com.wingedsheep.sdk.scripting.EntersAsCopy? = null,
    val copyCandidates: List<PendingEntryCopyCandidate> = emptyList(),
    val copyHandled: Boolean = false,
    val selectedCopy: PendingEntryCopyCandidate? = null,
    val copiedCard: com.wingedsheep.engine.state.components.identity.CardComponent? = null
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
    val legalHosts: List<ObjectRef> = emptyList(),
    val choosingCopy: Boolean = false
) : AnswerContinuation

/** Immutable copiable values announced before any member of the entry batch is placed. */
@Serializable
data class PendingEntryCopyCandidate(
    val source: ObjectRef,
    val card: com.wingedsheep.engine.state.components.identity.CardComponent
)
