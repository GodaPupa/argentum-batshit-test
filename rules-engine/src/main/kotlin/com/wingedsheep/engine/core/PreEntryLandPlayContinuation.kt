package com.wingedsheep.engine.core

import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.ZoneKey
import kotlinx.serialization.Serializable

/**
 * Serialized pre-entry checkpoint for a land play whose own "as it enters" replacement asks
 * whether its controller will pay life.
 *
 * The source remains in [sourceZone] and no land drop, payment, battlefield placement, entry event,
 * or play-history update is committed until this continuation resumes. [source] pins the exact
 * zone-object visit so a stale/removed/blinked source fails closed rather than playing a different
 * visit that reused the same entity id.
 */
@Serializable
data class PreEntryLandPlayContinuation(
    val action: PlayLand,
    val source: ObjectRef,
    val sourceZone: ZoneKey,
    val lifeCost: Int,
    /** True when the play was initiated by a resolving effect rather than as a normal special action. */
    val duringResolution: Boolean = false,
) : AnswerContinuation
