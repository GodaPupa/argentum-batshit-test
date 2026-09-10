package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Add Sneak [cost] (Teenage Mutant Ninja Turtles, CR 702.190).
 *
 * "Any time you could cast an instant during your declare blockers step, you may cast
 * this spell by paying [cost] and returning an unblocked creature you control to its
 * owner's hand rather than paying this spell's mana cost. [A permanent spell] enters
 * tapped and attacking."
 *
 * Display-only at the DSL layer — all behavior lives in the engine's alternative-cost
 * pipeline, which keys off the [KeywordAbility.Sneak] entry in `cardDef.keywordAbilities`:
 * the legal-action enumerator surfaces the cast only during the active player's declare
 * blockers step while they control an unblocked attacker, the cast handler charges the
 * sneak mana plus returns the chosen unblocked attacker to hand, and a resolving permanent
 * spell enters tapped and attacking the same defender (CR 702.190b). The "sneak cost was
 * paid" fact is readable via [com.wingedsheep.sdk.dsl.Conditions.SneakCostWasPaid].
 */
fun CardBuilder.sneak(cost: String) {
    keywordAbilityList.add(KeywordAbility.sneak(cost))
}
