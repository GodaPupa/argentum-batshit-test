package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Change one legal existing target occurrence to a fixed recipient. */
@Serializable
@SerialName("ChangeTargetTo")
data class ChangeTargetToEffect(
    val stackObject: EffectTarget = EffectTarget.ContextTarget(0),
    val recipient: EffectTarget = EffectTarget.Self,
) : Effect {
    override val description: String = "Change one target of the specified spell or ability to the specified recipient"
}
