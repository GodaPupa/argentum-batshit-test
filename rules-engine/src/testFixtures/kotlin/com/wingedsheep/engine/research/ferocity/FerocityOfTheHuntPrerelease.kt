package com.wingedsheep.engine.research.ferocity

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Shared project-only prerelease definition for fixed fixtures and separately admitted research.
 * Never registered in MtgSetCatalog or a released/sanctioned card pool.
 *
 * Verified source: ferocity-recycling/sources/ferocity-of-the-hunt-canonical.json, 2026-09-26.
 * FRA #134, common; scheduled release 2026-10-02; source Pauper legality not_legal at retrieval.
 * The original mechanically qualified card(...) expression is preserved byte for byte.
 * The death trigger belongs to this Aura, not to the enchanted creature.
 *
 * Exact serialized runtime definitions retain their AbilityIds for replay. Any separate
 * normalized semantic hash is an extraction check only and must never replace a runtime pin.
 */
val FerocityOfTheHuntPrerelease: CardDefinition = card("Ferocity of the Hunt") {
    manaCost = "{1}{B/G}"
    colorIdentity = "BG"
    typeLine = "Enchantment — Aura"
    oracleText = "Flash\nEnchant creature\n" +
        "Enchanted creature gets +1/+0 and has deathtouch.\n" +
        "When enchanted creature dies, return that card to the battlefield tapped under its owner's control."
    keywords(Keyword.FLASH)
    auraTarget = Targets.Creature
    staticAbility { ability = ModifyStats(1, 0) }
    staticAbility { ability = GrantKeyword(Keyword.DEATHTOUCH) }
    triggeredAbility {
        trigger = Triggers.leavesBattlefield(to = Zone.GRAVEYARD, binding = TriggerBinding.ATTACHED)
        effect = Effects.PutOntoBattlefieldFromGraveyard(EffectTarget.TriggeringEntity, tapped = true)
    }
}
