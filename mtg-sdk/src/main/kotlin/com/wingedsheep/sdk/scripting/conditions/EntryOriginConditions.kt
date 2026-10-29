package com.wingedsheep.sdk.scripting.conditions

import com.wingedsheep.sdk.core.Zone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Read the immutable origin of the triggering battlefield entry, not the current object. */
@SerialName("TriggeringEntityEnteredOrWasCastFromZone")
@Serializable
data class TriggeringEntityEnteredOrWasCastFromZone(
    val zone: Zone,
    val ownedByController: Boolean = false
) : Condition {
    override val description: String =
        "if it entered or was cast from " + (if (ownedByController) "your " else "a ") + zone.name.lowercase()
}
