package com.wingedsheep.engine.core

import com.wingedsheep.engine.event.PendingTrigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Exact triggers, including object identities and captured payloads, survive each ordering choice. */
@Serializable
@SerialName("TriggerOrderingContinuation")
data class TriggerOrderingContinuation(
    val chosen: List<PendingTrigger>,
    val remaining: List<PendingTrigger>,
) : AnswerContinuation
