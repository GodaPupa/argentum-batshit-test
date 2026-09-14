package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.CastFromCollectionWithoutPayingCostEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.CopyCardIntoCollectionEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.events.DamageType
import com.wingedsheep.sdk.scripting.events.RecipientFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Shared triggered ability placed on the stack when an encoded creature hits a player. */
object Cipher {
    const val COPY_COLLECTION: String = "cipher_copy"

    val copyAbility: TriggeredAbility = TriggeredAbility(
        id = AbilityId("cipher_copy_cast"),
        trigger = EventPattern.DealsDamageEvent(
            damageType = DamageType.Combat,
            recipient = RecipientFilter.AnyPlayer,
        ),
        binding = TriggerBinding.SELF,
        activeZones = setOf(Zone.EXILE),
        effect = MayEffect(
            CompositeEffect(
                listOf(
                    CopyCardIntoCollectionEffect(
                        source = EffectTarget.Self,
                        storeAs = COPY_COLLECTION,
                    ),
                    CastFromCollectionWithoutPayingCostEffect(from = COPY_COLLECTION),
                )
            ),
            descriptionOverride = "cast a copy of the encoded card without paying its mana cost",
        ),
        descriptionOverride = "Whenever the encoded creature deals combat damage to a player, " +
            "its controller may cast a copy of this card without paying its mana cost.",
    )
}
