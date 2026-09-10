package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.text.TextReplacer
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * "Amass [subtype] N" (CR 701.47).
 *
 * To amass Orcs N: if the controller doesn't control an Army creature, first create a 0/0 black
 * [subtype] Army creature token. Then they choose an Army they control, put N +1/+1 counters on it,
 * and — if it isn't already that subtype — it becomes that subtype in addition to its other types.
 *
 * [subtype] is required — the amassed Army's type is part of each card's printed text (Orcs for the
 * LTR cards, Zombies / Phyrexians elsewhere), so it must never default to one set's flavor.
 *
 * The amount is a [DynamicAmount] so the keyword supports both the fixed printings ("amass Orcs 2")
 * and the variable ones ("amass Orcs X", e.g. Fall of Cair Andros, The Mouth of Sauron, Shagrat).
 */
@SerialName("Amass")
@Serializable
data class AmassEffect(
    val amount: DynamicAmount = DynamicAmount.Fixed(1),
    val subtype: String
) : Effect {
    override val description: String = "amass ${subtype}s ${amount.description}"

    override fun applyTextReplacement(replacer: TextReplacer): Effect =
        copy(amount = amount.applyTextReplacement(replacer))
}
