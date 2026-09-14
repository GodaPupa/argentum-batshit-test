package com.wingedsheep.mtg.sets.definitions.war.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Pollenbright Druid — War of the Spark #173. */
val PollenbrightDruid = card("Pollenbright Druid") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, choose one —\n• Put a +1/+1 counter on target " +
        "creature.\n• Proliferate. (Choose any number of permanents and/or players, then give each " +
        "another counter of each kind already there.)"

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = ModalEffect.chooseOne(
            Mode.withTarget(
                Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.ContextTarget(0)),
                Targets.Creature,
                "Put a +1/+1 counter on target creature"
            ),
            Mode.noTarget(Effects.Proliferate(), "Proliferate")
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "173"
        artist = "Matt Stewart"
        imageUri = "https://cards.scryfall.io/normal/front/4/b/4b0f478c-54f8-4087-ad52-d089e2049dda.jpg?1783933409"
        ruling(
            "2023-02-04",
            "If a player or permanent has more than one kind of counter on it, and you choose for " +
                "it to get additional counters, it must get one of each kind of counter it already " +
                "has. You can't have it get just one kind of counter it already has and not the others."
        )
        ruling(
            "2023-02-04",
            "To proliferate, you can choose any permanent that has a counter, including ones " +
                "controlled by opponents. You can choose any player who has a counter, including " +
                "opponents. You can't choose cards in any zone other than the battlefield, even if " +
                "they have counters on them."
        )
        ruling(
            "2023-02-04",
            "You don't have to choose every permanent or player that has a counter, only the ones " +
                "you want to add another counter to. Since \"any number\" includes zero, you don't " +
                "have to choose any permanents at all, and you don't have to choose any players at all."
        )
        ruling(
            "2023-02-04",
            "If a permanent ever has both +1/+1 counters and -1/-1 counters on it at the same time, " +
                "they're removed in pairs as a state-based action so that the permanent has only one " +
                "of those kinds of counters on it."
        )
        ruling(
            "2023-02-04",
            "An ability that triggers \"Whenever you proliferate\" triggers even if you chose no " +
                "permanents or players while doing so."
        )
        ruling(
            "2023-02-04",
            "Players can respond to a spell or ability whose effect includes proliferating. Once " +
                "that spell or ability starts to resolve, however, and its controller chooses which " +
                "permanents and players will get new counters, it's too late for anyone to respond."
        )
        ruling("2019-05-03", "You may choose Pollenbright Druid as the target of its own ability.")
    }
}
