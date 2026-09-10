package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.RestrictedManaEntry
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.effects.ManaExpiry
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit coverage for combat-duration mana — the engine primitive behind firebending
 * (CR 702.189). It is held as an [ManaRestriction.AnySpend] restricted entry tagged with
 * [ManaExpiry.END_OF_COMBAT], so it flows through the normal spend logic but is cleared by
 * `CombatManager.endCombat` (and, like all mana, by the end-of-turn pool emptying).
 */
class CombatDurationManaTest : FunSpec({

    test("addRestricted tags the entry with the requested expiry") {
        val pool = ManaPoolComponent().addRestricted(
            color = Color.RED,
            amount = 2,
            restriction = ManaRestriction.AnySpend,
            expiry = ManaExpiry.END_OF_COMBAT
        )
        pool.restrictedMana.size shouldBe 2
        pool.restrictedMana.all { it.color == Color.RED && it.expiry == ManaExpiry.END_OF_COMBAT } shouldBe true
    }

    test("clearExpired(END_OF_COMBAT) removes only combat-duration mana, leaving end-of-turn mana") {
        val pool = ManaPoolComponent(
            restrictedMana = listOf(
                RestrictedManaEntry(Color.RED, ManaRestriction.AnySpend, expiry = ManaExpiry.END_OF_COMBAT),
                // An ordinary restricted entry (e.g. ritual mana spendable only on instants/sorceries)
                // that persists to end of turn must survive end-of-combat clearing.
                RestrictedManaEntry(Color.BLACK, ManaRestriction.InstantOrSorceryOnly, expiry = ManaExpiry.END_OF_TURN),
            )
        )
        val after = pool.clearExpired(ManaExpiry.END_OF_COMBAT)
        after.restrictedMana.size shouldBe 1
        after.restrictedMana.single().color shouldBe Color.BLACK
    }

    test("combat-duration mana is spendable like any other mana (AnySpend)") {
        val pool = ManaPool(
            restrictedMana = listOf(
                RestrictedManaEntry(Color.RED, ManaRestriction.AnySpend, expiry = ManaExpiry.END_OF_COMBAT)
            )
        )
        val context = SpellPaymentContext()
        pool.canPay(ManaCost.parse("{R}"), context) shouldBe true

        val afterPaying = pool.pay(ManaCost.parse("{R}"), context)
        afterPaying shouldBe ManaPool.EMPTY
    }

    test("end-of-turn pool emptying also discards combat-duration mana") {
        val pool = ManaPoolComponent().addRestricted(
            color = Color.RED, amount = 1, restriction = ManaRestriction.AnySpend, expiry = ManaExpiry.END_OF_COMBAT
        )
        pool.empty() shouldBe ManaPoolComponent()
    }
})
