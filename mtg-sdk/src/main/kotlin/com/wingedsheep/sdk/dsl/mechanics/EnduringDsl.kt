package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.ConditionalStaticAbility
import com.wingedsheep.sdk.scripting.TransformPermanent
import com.wingedsheep.sdk.scripting.conditions.SourceReturnedAsEnchantment
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Add Enduring (Duskmourn: House of Horror — the Glimmer "Enduring" cycle).
 *
 * "When this permanent dies, if it was a creature, return it to the battlefield under its
 * owner's control. It's an enchantment. (It's not a creature.)"
 *
 * Wires the full mechanic from one call:
 *  - the [Keyword.ENDURING] display keyword, which the engine reads from projected (last-known)
 *    keywords to synthesize the self-return dies-trigger (see
 *    [com.wingedsheep.engine.event.DeathAndLeaveTriggerDetector.detectEnduringTriggers]); that
 *    trigger fires only if the permanent was a creature when it died, and is suppressed on
 *    tokens (CR 111.7), so the returned enchantment never loops, and
 *  - a [ConditionalStaticAbility] gated on [SourceReturnedAsEnchantment] that, while the
 *    enduring-return marker is present, makes the permanent an enchantment with no other card
 *    types or subtypes ([TransformPermanent] with `setCardTypes = {"ENCHANTMENT"}` and empty
 *    `setSubtypes`). `setColors = null` leaves its color untouched. The original creature
 *    instance carries no marker, so the static doesn't apply to it.
 *
 * Modeled on the same "synthesized self-return + conditional static" shape as Persist and
 * Impending; the keyword itself adds no combat behavior.
 */
fun CardBuilder.enduring() {
    keywordSet.add(Keyword.ENDURING)
    staticAbilities.add(
        ConditionalStaticAbility(
            ability = TransformPermanent(
                setCardTypes = setOf("ENCHANTMENT"),
                setSubtypes = emptySet(),
                setColors = null,
                clearSubtypes = true,
                filter = GroupFilter.source()
            ),
            condition = SourceReturnedAsEnchantment
        )
    )
}
