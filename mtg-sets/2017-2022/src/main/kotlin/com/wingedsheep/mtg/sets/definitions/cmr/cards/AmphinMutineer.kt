package com.wingedsheep.mtg.sets.definitions.cmr.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.EncoreCopiesEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/**
 * Amphin Mutineer — Commander Legends #55.
 *
 * The ETB is deliberately an optional target, not an optional effect: choosing zero targets must
 * create no Salamander Warrior. The branch is also gated on the selected target still matching at
 * resolution, while normal target revalidation handles a target that has become illegal.
 */
val AmphinMutineer = card("Amphin Mutineer") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Salamander Pirate"
    oracleText = "When this creature enters, exile up to one target non-Salamander creature. " +
        "That creature's controller creates a 4/3 blue Salamander Warrior creature token.\n" +
        "Encore {4}{U}{U} ({4}{U}{U}, Exile this card from your graveyard: For each opponent, " +
        "create a token copy that attacks that opponent this turn if able. They gain haste. " +
        "Sacrifice them at the beginning of the next end step. Activate only as a sorcery.)"
    power = 3
    toughness = 3

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val creature = target(
            "target non-Salamander creature",
            TargetCreature(
                optional = true,
                filter = TargetFilter(
                    GameObjectFilter.Creature.notSubtype(Subtype.SALAMANDER)
                ),
            )
        )
        effect = ConditionalEffect(
            condition = Conditions.TargetMatchesFilter(
                GameObjectFilter.Creature.notSubtype(Subtype.SALAMANDER)
            ),
            effect = Effects.Composite(
                Effects.Exile(creature),
                Effects.CreateToken(
                    power = 4,
                    toughness = 3,
                    colors = setOf(Color.BLUE),
                    creatureTypes = setOf("Salamander", "Warrior"),
                    controller = EffectTarget.TargetController,
                    name = "Salamander Warrior Token",
                ),
            ),
        )
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{4}{U}{U}"), Costs.ExileSelf)
        effect = EncoreCopiesEffect
        activateFromZone = Zone.GRAVEYARD
        timing = TimingRule.SorcerySpeed
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "55"
        artist = "Nicholas Gregory"
        imageUri = "https://cards.scryfall.io/normal/front/c/7/c7be9d06-3651-47f4-9e44-00ecd4a15e3c.jpg?1783928868"
        ruling("2020-11-10", "If the target creature is an illegal target by the time Amphin Mutineer's first ability tries to resolve, the ability doesn't resolve. No player creates a Salamander Warrior token.")
        ruling("2020-11-10", "Exiling the card with encore is a cost to activate the ability.")
        ruling("2020-11-10", "Opponents who have left the game aren't counted when determining how many tokens to create.")
        ruling("2020-11-10", "The tokens copy exactly what was printed on the original card and nothing else about it.")
        ruling("2020-11-10", "If one of the tokens can't attack the appropriate player for any reason, it can attack another player or a planeswalker, or not attack at all.")
    }
}
