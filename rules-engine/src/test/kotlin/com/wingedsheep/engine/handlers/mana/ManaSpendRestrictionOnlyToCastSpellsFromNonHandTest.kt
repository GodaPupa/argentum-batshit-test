package com.wingedsheep.engine.handlers.mana

import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * BDD test: restricted mana annotated with [ManaRestriction.CastFromNonHandOnly] (Mm'menon,
 * the Right Hand's granted artifact mana) must satisfy any spell cast originating outside
 * the caster's hand — exile, graveyard, top of library, command zone — but never a hand
 * cast and never an activated-ability payment.
 *
 * GIVEN  A player's mana pool contains one mana annotated with CastFromNonHandOnly
 * AND    The cost of a hypothetical {U} spell or ability
 * WHEN   The engine validates payment with several spell payment contexts
 * THEN   Hand-cast and ability-activation contexts reject the payment
 * AND    Exile / graveyard / library / command-zone casts accept it (default isFromExile only
 *        differentiates exile-specific restrictions; CastFromNonHandOnly looks at isFromHand)
 */
class ManaSpendRestrictionOnlyToCastSpellsFromNonHandTest : FunSpec({

    val cost = ManaCost.parse("{U}")
    val pool = ManaPool().addRestricted(Color.BLUE, 1, ManaRestriction.CastFromNonHandOnly)

    test("CastFromNonHandOnly rejects hand cast") {
        // Default context has isFromHand = true (most spells originate from hand).
        pool.canPay(cost, SpellPaymentContext()) shouldBe false
    }

    test("CastFromNonHandOnly accepts non-hand casts (graveyard / library / exile / command zone)") {
        // CastSpellHandler sets isFromHand = false for cast paths that don't originate in the
        // caster's hand; from the restriction's perspective the specific non-hand zone does not
        // matter. The isFromExile flag is set additionally so the existing exile-specific
        // restriction can be tested independently in [ManaSpendRestrictionOnlyToCastSpellsFromExileTest].
        val fromExile = SpellPaymentContext(isFromHand = false, isFromExile = true)
        val fromNonExileNonHand = SpellPaymentContext(isFromHand = false, isFromExile = false)

        pool.canPay(cost, fromExile) shouldBe true
        pool.canPay(cost, fromNonExileNonHand) shouldBe true
    }

    test("CastFromNonHandOnly rejects ability activation regardless of isFromHand") {
        // "Spend this mana only to cast a spell" — abilities don't count even when the source
        // isn't in hand (which is the normal case for activated abilities anyway).
        val abilityFromNonHand = SpellPaymentContext(isAbilityActivation = true, isFromHand = false)
        pool.canPay(cost, abilityFromNonHand) shouldBe false
    }
})
