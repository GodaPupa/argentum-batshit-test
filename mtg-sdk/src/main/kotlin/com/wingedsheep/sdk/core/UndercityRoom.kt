package com.wingedsheep.sdk.core

import kotlinx.serialization.Serializable

/**
 * Rooms of the Undercity dungeon used by the initiative mechanic.
 */
@Serializable
enum class UndercityRoom(val displayName: String) {
    SECRET_ENTRANCE("Secret Entrance"),
    FORGE("Forge"),
    LOST_WELL("Lost Well"),
    TRAP("Trap!"),
    ARENA("Arena"),
    STASH("Stash"),
    ARCHIVES("Archives"),
    CATACOMBS("Catacombs"),
    THRONE_OF_THE_DEAD_THREE("Throne of the Dead Three")
}
