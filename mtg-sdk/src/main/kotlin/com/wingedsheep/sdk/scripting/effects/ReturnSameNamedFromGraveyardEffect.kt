package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Return the target graveyard card and every other card with the same name in the controller's
 * graveyard to the battlefield tapped under the controller's control.
 *
 * Rat King, Verminister: "{T}, Sacrifice three Rats: Return target creature card and all other
 * cards with the same name as that card from your graveyard to the battlefield tapped."
 *
 * @property target The target graveyard card whose name is matched (default [EffectTarget.ContextTarget]).
 */
@SerialName("ReturnSameNamedFromGraveyard")
@Serializable
data class ReturnSameNamedFromGraveyardEffect(
    val target: EffectTarget = EffectTarget.ContextTarget(0)
) : Effect {
    override val description: String =
        "Return ${target.description} and all other cards with the same name from your graveyard to the battlefield tapped"
}
