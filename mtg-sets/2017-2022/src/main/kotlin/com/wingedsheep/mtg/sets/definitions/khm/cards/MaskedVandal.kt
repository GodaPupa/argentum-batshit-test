package com.wingedsheep.mtg.sets.definitions.khm.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.IfYouDoEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val MaskedVandal = card("Masked Vandal") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Shapeshifter"
    power = 1
    toughness = 3
    oracleText = "Changeling (This card is every creature type.)\n" +
        "When this creature enters, you may exile a creature card from your graveyard. " +
        "If you do, exile target artifact or enchantment an opponent controls."

    keywords(Keyword.CHANGELING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val victim = target(
            "target artifact or enchantment an opponent controls",
            TargetObject(filter = TargetFilter.ArtifactOrEnchantment.opponentControls()),
        )
        effect = Effects.Composite(
            MayEffect(
                IfYouDoEffect(
                    action = Effects.Composite(
                        GatherCardsEffect(
                            source = CardSource.FromZone(Zone.GRAVEYARD, filter = GameObjectFilter.Creature),
                            storeAs = "graveyardCreatures",
                        ),
                        SelectFromCollectionEffect(
                            from = "graveyardCreatures",
                            selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),
                            storeSelected = "vandalExileCost",
                            selectedLabel = "Exile",
                        ),
                        MoveCollectionEffect(
                            from = "vandalExileCost",
                            destination = CardDestination.ToZone(Zone.EXILE),
                        ),
                    ),
                    ifYouDo = Effects.Exile(victim),
                    successCriterion = SuccessCriterion.CollectionNonEmpty("vandalExileCost"),
                ),
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "184"
        artist = "Jason A. Engle"
        imageUri = "https://cards.scryfall.io/normal/front/f/0/f0a9c72a-e450-41e3-80e5-06f2f1171245.jpg?1783928209"
    }
}
