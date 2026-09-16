package com.wingedsheep.sdk.scripting.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Venture into the dungeon (CR 701.46).
 *
 * A player who is not in a dungeon chooses one of the three ordinary dungeon cards and enters its
 * first room. Otherwise they advance to a room connected to their current room. Entering the last
 * room completes that dungeon, allowing the player's next venture to choose a new one.
 */
@SerialName("VentureIntoDungeon")
@Serializable
data object VentureIntoDungeonEffect : Effect {
    override val description: String = "Venture into the dungeon"
}
