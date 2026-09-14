package com.wingedsheep.mtg.sets.definitions.dgm.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.EntityMatches
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.TapUntapEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Hidden Strings — two independently optional tap/untap clauses followed by cipher. */
val HiddenStrings = card("Hidden Strings") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText =
        "You may tap or untap target permanent, then you may tap or untap another target permanent.\n" +
            "Cipher (Then you may exile this spell card encoded on a creature you control. " +
            "Whenever that creature deals combat damage to a player, its controller may cast a " +
            "copy of the encoded card without paying its mana cost.)"

    spell {
        target(
            "target permanents",
            TargetPermanent(count = 2, optional = true),
        )
        effect = Effects.Composite(
            ConditionalEffect(
                EntityMatches(
                    EffectTarget.ContextTarget(0),
                    GameObjectFilter.Permanent,
                ),
                MayEffect(
                    ModalEffect(
                        modes = listOf(
                            Mode.noTarget(
                                TapUntapEffect(
                                    EffectTarget.ContextTarget(0),
                                    tap = true,
                                ),
                                "Tap that permanent",
                            ),
                            Mode.noTarget(
                                TapUntapEffect(
                                    EffectTarget.ContextTarget(0),
                                    tap = false,
                                ),
                                "Untap that permanent",
                            ),
                        ),
                        chooseCount = 1,
                        countsAsModalSpell = false,
                    ),
                    descriptionOverride = "You may tap or untap the first permanent",
                ),
            ),
            ConditionalEffect(
                EntityMatches(
                    EffectTarget.ContextTarget(1),
                    GameObjectFilter.Permanent,
                ),
                MayEffect(
                    ModalEffect(
                        modes = listOf(
                            Mode.noTarget(
                                TapUntapEffect(
                                    EffectTarget.ContextTarget(1),
                                    tap = true,
                                ),
                                "Tap that permanent",
                            ),
                            Mode.noTarget(
                                TapUntapEffect(
                                    EffectTarget.ContextTarget(1),
                                    tap = false,
                                ),
                                "Untap that permanent",
                            ),
                        ),
                        chooseCount = 1,
                        countsAsModalSpell = false,
                    ),
                    descriptionOverride = "You may tap or untap the second permanent",
                ),
            ),
        )
        cipher()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "12"
        artist = "Daarken"
        imageUri =
            "https://cards.scryfall.io/normal/front/2/1/216e8047-6f54-49ce-bf86-27dc8fc8c8f7.jpg?1783940042"
    }
}
