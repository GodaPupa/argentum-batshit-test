package com.wingedsheep.engine.core

import com.wingedsheep.engine.mechanics.targeting.FixedRecipientOffer
import kotlinx.serialization.Serializable

/** Retains the exact offered occurrences and original object visits across serialization. */
@Serializable
internal data class FixedRecipientChoiceContinuation(
    val offer: FixedRecipientOffer,
) : AnswerContinuation
