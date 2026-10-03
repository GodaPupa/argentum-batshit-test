package com.wingedsheep.engine.core

import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

@Serializable
enum class DestructionReplacementKind { REGENERATION, REMOVE_DAMAGE, SHIELD_COUNTER, UMBRA_ARMOR }

/** Identity of one applicable replacement, rather than a preselected replacement family. */
@Serializable
data class DestructionReplacementOption(
    val kind: DestructionReplacementKind,
    val sourceId: EntityId,
    val label: String
)

/** The destruction is consumed by the selected replacement, including any nested suspension. */
@Serializable
data class DestructionReplacementContinuation(
    val permanent: ObjectRef,
    val options: List<DestructionReplacementOption>
) : AnswerContinuation

/** Remaining lethal determinations and their shared pre-pass replacement source snapshot. */
@Serializable
data class LethalDestructionContinuation(
    val remaining: List<ObjectRef>,
    val passStartState: com.wingedsheep.engine.state.GameState
) : AutomaticContinuation

/** Retains the complete collection operation while a nested destruction replacement asks a question. */
@Serializable
data class DestroyCollectionContinuation(
    val effect: com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect,
    val context: com.wingedsheep.engine.handlers.EffectContext,
    val cards: List<EntityId>,
    val remaining: List<ObjectRef>,
    val attempted: List<ObjectRef>
) : AutomaticContinuation
