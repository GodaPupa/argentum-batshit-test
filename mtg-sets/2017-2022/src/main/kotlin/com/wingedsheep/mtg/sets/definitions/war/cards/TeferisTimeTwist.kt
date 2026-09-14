package com.wingedsheep.mtg.sets.definitions.war.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect

/** Teferi's Time Twist — War of the Spark #72. */
val TeferisTimeTwist = card("Teferi's Time Twist") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile target permanent you control. Return that card to the battlefield under " +
        "its owner's control at the beginning of the next end step. If it enters as a creature, " +
        "it enters with an additional +1/+1 counter on it."

    spell {
        val permanent = target("permanent you control", Targets.PermanentYouControl)
        effect = Effects.Move(permanent, Zone.EXILE).then(
            CreateDelayedTriggerEffect(
                step = Step.END,
                // The move carries the counter so the returned creature enters with it. The
                // engine cannot yet condition an entry counter on the returned object's projected
                // type, so a noncreature permanent can retain an inert +1/+1 counter.
                effect = Effects.Move(
                    permanent,
                    Zone.BATTLEFIELD,
                    addCounterType = CounterType.PLUS_ONE_PLUS_ONE
                )
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "72"
        artist = "Ralph Horsley"
        flavorText = "\"The safest place for you is not now.\""
        imageUri = "https://cards.scryfall.io/normal/front/c/8/c878bdc0-d697-4a2f-bba5-758b27f4247a.jpg?1783933453"
        ruling("2019-05-03", "If a token is exiled this way, it will cease to exist and won’t return to the battlefield.")
        ruling(
            "2019-05-03",
            "Auras attached to the exiled permanent will be put into their owners’ graveyards. " +
                "Equipment attached to the exiled permanent will become unattached and remain on " +
                "the battlefield. Any counters on the exiled permanent will cease to exist. Once " +
                "the exiled permanent returns, it’s considered a new object with no relation to " +
                "the object that it was."
        )
        ruling("2019-05-03", "The permanent returns untapped unless another effect causes it to enter the battlefield tapped.")
        ruling(
            "2019-05-03",
            "To determine whether the entering permanent is entering as a creature, consider any " +
                "effects that will modify that permanent’s characteristics once it’s on the " +
                "battlefield, including that permanent’s own abilities that affect only itself and " +
                "effects from other objects."
        )
        ruling(
            "2019-05-03",
            "A creature returned to the battlefield this way enters the battlefield with one +1/+1 " +
                "counter if it would otherwise enter with no +1/+1 counters."
        )
    }
}
