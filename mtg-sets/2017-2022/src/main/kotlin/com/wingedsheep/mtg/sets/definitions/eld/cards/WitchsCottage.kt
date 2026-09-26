package com.wingedsheep.mtg.sets.definitions.eld.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Witch's Cottage — canonical ELD 249.
 * The Swamp subtype supplies its mana ability. Actual untapped entry gates the trigger only when it fires.
 * Current Oracle text and printing metadata verified 2026-09-26.
 */
val WitchsCottage = card("Witch's Cottage") {
    manaCost = ""
    typeLine = "Land — Swamp"
    oracleText = "({T}: Add {B}.)\nThis land enters tapped unless you control three or more other Swamps.\nWhen this land enters untapped, you may put target creature card from your graveyard on top of your library."
    colorIdentity = "B"

    replacementEffect(
        EntersTapped(
            unlessCondition = Conditions.YouControlOtherAtLeast(
                3, GameObjectFilter.Land.withSubtype("Swamp")
            )
        )
    )
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        // "enters untapped" qualifies the event; tapping it later does not cancel the trigger.
        triggerRestriction = Conditions.SourceIsUntapped
        val creature = target("target creature card in your graveyard", Targets.CreatureCardInYourGraveyard)
        optional = true
        effect = Effects.PutOnTopOfLibrary(creature)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "249"
        artist = "Gabor Szikszai"
        imageUri = "https://cards.scryfall.io/normal/front/b/8/b87891cd-b457-4dff-8d18-a7eaf6748fc6.jpg?1783932575"
        ruling("2019-10-04", "As these lands are entering the battlefield, they check for lands that are already on the battlefield. They won't see lands that are entering the battlefield at the same time (due to Scapeshift, for example).")
        ruling("2019-10-04", "If another effect puts these lands onto the battlefield tapped, they enter tapped, even if you control enough lands with the appropriate basic land type.")
    }
}
