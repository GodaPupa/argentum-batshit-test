package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A rule restriction on targeting an object or player. The referenced players are resolved
 * when the effect resolves; changing the protected object's controller or abilities does not
 * change that set. This grants no ability, unlike hexproof or shroud.
 */
@SerialName("PreventTargeting")
@Serializable
data class PreventTargetingEffect(
    val target: EffectTarget = EffectTarget.ContextTarget(0),
    val fromPlayers: Player = Player.EachOpponent,
    val duration: Duration = Duration.EndOfTurn
) : Effect {
    override val description: String =
        "${target.description} can't be the target of spells or abilities controlled by ${fromPlayers.description} ${duration.description}"
}
