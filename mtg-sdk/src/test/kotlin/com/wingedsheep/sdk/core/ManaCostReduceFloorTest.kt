package com.wingedsheep.sdk.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [ManaCost.reduceGenericWithManaFloor] — generic-only reduction with a *total*-mana floor
 * (Power Artifact: "cost {2} less … can't reduce the mana in that cost to less than one mana").
 * The interesting cases are costs that carry colored pips, where the floor is partly (or fully)
 * satisfied by the kept colored mana — exercising `genericFloor = minTotalMana - nonGenericMana`.
 */
class ManaCostReduceFloorTest : StringSpec({

    fun reduce(cost: String, amount: Int, floor: Int) =
        ManaCost.parse(cost).reduceGenericWithManaFloor(amount, floor)

    // Pure-generic costs (Millstone {2}, Obelisk of Undoing {6}).
    "{2} reduced by {2} floored at 1 stays {1}, never {0}" {
        reduce("{2}", amount = 2, floor = 1) shouldBe ManaCost.parse("{1}")
    }
    "{1} reduced by {2} floored at 1 is unchanged" {
        reduce("{1}", amount = 2, floor = 1) shouldBe ManaCost.parse("{1}")
    }
    "{6} reduced by {2} floored at 1 lands well above the floor at {4}" {
        reduce("{6}", amount = 2, floor = 1) shouldBe ManaCost.parse("{4}")
    }

    // Colored pips: untouched (CR 118.7a), and the floor is met by the colored mana itself.
    "{3}{U} reduced by {2} floored at 1 becomes {1}{U} (colored pip untouched)" {
        reduce("{3}{U}", amount = 2, floor = 1) shouldBe ManaCost.parse("{1}{U}")
    }
    "{2}{U} reduced by {2} floored at 1 becomes {U} — the floor is already met by the colored pip" {
        reduce("{2}{U}", amount = 2, floor = 1) shouldBe ManaCost.parse("{U}")
    }
    "{1}{U} reduced by {2} floored at 1 becomes {U} (never drops the colored pip)" {
        reduce("{1}{U}", amount = 2, floor = 1) shouldBe ManaCost.parse("{U}")
    }
    "{U} reduced by {2} floored at 1 is unchanged — no generic to remove" {
        reduce("{U}", amount = 2, floor = 1) shouldBe ManaCost.parse("{U}")
    }

    // A floor larger than the colored contribution still keeps generic mana to reach it.
    "{3}{U} reduced by {5} floored at 2 keeps {1}{U} (colored covers 1, generic floored at 1)" {
        reduce("{3}{U}", amount = 5, floor = 2) shouldBe ManaCost.parse("{1}{U}")
    }

    // floor = 0 is exactly reduceGeneric (can reach {0} / drop generic entirely).
    "{2}{U} reduced by {2} floored at 0 becomes {U}, matching reduceGeneric" {
        val viaFloor = reduce("{2}{U}", amount = 2, floor = 0)
        viaFloor shouldBe ManaCost.parse("{2}{U}").reduceGeneric(2)
        viaFloor shouldBe ManaCost.parse("{U}")
    }
})
