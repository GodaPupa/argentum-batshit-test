package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fixed-color hexproof for a player or permanent; never grants damage prevention. */
@Serializable
@SerialName("GrantHexproofFromColors")
data class GrantHexproofFromColorsEffect(
    val colors: Set<Color>,
    val target: EffectTarget = EffectTarget.ContextTarget(0),
    val duration: Duration = Duration.EndOfTurn
) : Effect {
    override val description: String = "${target.description} gains hexproof from ${colors.joinToString { it.displayName.lowercase() }} ${duration.description}"
}
