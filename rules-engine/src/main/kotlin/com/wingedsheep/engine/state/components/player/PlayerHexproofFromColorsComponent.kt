package com.wingedsheep.engine.state.components.player

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.core.Color
import kotlinx.serialization.Serializable

/** Each grant retains its own lifetime; a temporary grant cannot erase a permanent one. */
@Serializable
data class PlayerHexproofFromColorsComponent(val grants: List<Grant> = emptyList()) : Component {
    @Serializable
    data class Grant(val colors: Set<Color>, val removeOn: PlayerEffectRemoval)
}
