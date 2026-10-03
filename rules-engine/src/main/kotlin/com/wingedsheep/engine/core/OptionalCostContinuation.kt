package com.wingedsheep.engine.core

import com.wingedsheep.sdk.scripting.ChoiceSlot
import kotlinx.serialization.Serializable

/** An announcement pause before any mana or non-mana cost is paid. */
@Serializable
data class OptionalCostCountContinuation(val action: CastSpell, val slot: ChoiceSlot) : AnswerContinuation
