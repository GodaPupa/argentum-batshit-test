package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.effects.CREATED_TOKENS
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Living weapon composes one atomic token/choice/attachment pipeline, before state-based actions. */
val ColossalDreadmask = card("Colossal Dreadmask") {
    manaCost = "{4}{G}{G}"
    colorIdentity = "G"
    typeLine = "Artifact — Equipment"
    oracleText = "Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)\nEquipped creature gets +6/+6 and has trample.\nEquip {3}{G}{G}"
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            Effects.CreateToken(power = 0, toughness = 0, colors = setOf(Color.BLACK),
                creatureTypes = setOf("Phyrexian", "Germ")),
            SelectFromCollectionEffect(
                from = CREATED_TOKENS,
                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),
                storeSelected = "germ_to_equip",
                storeRemainder = "other_germs",
                prompt = "Choose the Phyrexian Germ to attach Colossal Dreadmask to"
            ),
            Effects.AttachEquipment(EffectTarget.PipelineTarget("germ_to_equip", 0))
        )
        description = "Living weapon"
    }
    staticAbility { ability = ModifyStats(6, 6, Filters.EquippedCreature) }
    staticAbility { ability = GrantKeyword(Keyword.TRAMPLE, Filters.EquippedCreature) }
    equipAbility("{3}{G}{G}")
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "148"
        artist = "Caio Monteiro"
        flavorText = "No legs to quake, no lungs to bellow, but if you see its teeth, it's still too late."
        imageUri = "https://cards.scryfall.io/normal/front/9/8/98164430-64c1-465f-b786-45753c965f44.jpg?1783911264"
        ruling("2024-06-07", "The Phyrexian Germ token enters the battlefield as a 0/0 creature and the Equipment becomes attached to it before state-based actions would cause the token to die. Abilities that trigger as the token enters the battlefield see that a 0/0 creature entered the battlefield.")
        ruling("2024-06-07", "Like other Equipment, each Equipment with living weapon has an equip cost. You can pay this cost to attach an Equipment to another creature you control. Once the Phyrexian Germ token is no longer equipped, it will be put into your graveyard and subsequently cease to exist, unless another effect raises its toughness above 0.")
        ruling("2024-06-07", "If the Phyrexian Germ token is destroyed, the Equipment remains on the battlefield as with any other Equipment.")
        ruling("2024-06-07", "If the living weapon trigger causes two Phyrexian Germs to be created (due to an effect such as that of Doubling Season), the Equipment becomes attached to one of them. The other will be put into your graveyard and subsequently cease to exist, unless another effect raises its toughness above 0.")
    }
}
