package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.core.UndercityRoom
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Target player takes the initiative. Taking it again still counts as taking it and therefore
 * produces the initiative's inherent venture trigger.
 */
@SerialName("TakeInitiative")
@Serializable
data class TakeInitiativeEffect(
    val target: EffectTarget = EffectTarget.Controller
) : Effect {
    override val description: String = "${target.description.replaceFirstChar { it.uppercase() }} takes the initiative"
}

/** The initiative's inherent "venture into Undercity" action. */
@SerialName("VentureIntoUndercity")
@Serializable
data class VentureIntoUndercityEffect(
    val target: EffectTarget = EffectTarget.Controller
) : Effect {
    override val description: String = "${target.description.replaceFirstChar { it.uppercase() }} ventures into Undercity"
}

/**
 * Enter one concrete room of Undercity. This updates dungeon progress and emits the room-entry
 * signal; the room's printed ability is put onto the stack separately by the rules engine.
 */
@SerialName("EnterUndercityRoom")
@Serializable
data class EnterUndercityRoomEffect(
    val room: UndercityRoom,
    val target: EffectTarget = EffectTarget.Controller
) : Effect {
    override val description: String = "Enter ${room.displayName}"
}
