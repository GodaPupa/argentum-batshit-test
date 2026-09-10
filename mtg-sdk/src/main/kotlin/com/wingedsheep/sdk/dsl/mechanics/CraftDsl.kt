package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.costs.CostAtom

/**
 * Craft with [filter] — [cost] (CR 702.167, The Lost Caverns of Ixalan).
 *
 * "[Cost], Exile this permanent, Exile [filter] from among permanents you control and/or
 * [filter] cards from your graveyard: Return this card to the battlefield transformed under
 * its owner's control. Activate only as a sorcery."
 *
 * Composed from existing primitives — no special handling at the DSL layer:
 *  - cost = [AbilityCost.Mana] ⊕ [AbilityCost.Craft] (the latter handles both the self-exile
 *    and the materials-exile in one atomic cost shape, recording the exiled materials on
 *    the source so the back face's CDA can read them).
 *  - effect = [com.wingedsheep.sdk.scripting.effects.ReturnSelfFromExileTransformedEffect].
 *  - `timing = TimingRule.SorcerySpeed` enforces "Activate only as a sorcery".
 *
 * Marks the front face with the [Keyword.CRAFT] tag for client display.
 *
 * ```kotlin
 * craft(filter = Filters.Dinosaur, cost = "{4}{R}")                             // one or more
 * craft(filter = Filters.Artifact, cost = "{2}{W}", minCount = 1, maxCount = 1) // exactly one
 * ```
 *
 * @param filter The material filter (the [filter] in "Craft with [filter]").
 * @param cost The mana portion of the craft cost.
 * @param materialDescription Optional override for the filter's name in the rendered cost
 *   description — e.g. "one or more Dinosaurs". Defaults to the filter's own description.
 * @param minCount Minimum number of materials (CR 702.167a).
 * @param maxCount Maximum number of materials, or `null` for "... or more" wordings. Exact-count
 *   crafts ("Craft with artifact", "Craft with two creatures") set `maxCount == minCount`.
 */
fun CardBuilder.craft(
    filter: GameObjectFilter,
    cost: String,
    materialDescription: String? = null,
    minCount: Int = 1,
    maxCount: Int? = null
) {
    val materials = materialDescription ?: "one or more ${filter.description}s"
    activatedAbilities.add(
        ActivatedAbility(
            cost = AbilityCost.Composite(
                listOf(
                    AbilityCost.Atom(CostAtom.Mana(ManaCost.parse(cost))),
                    AbilityCost.Craft(filter, minCount = minCount, maxCount = maxCount)
                )
            ),
            effect = com.wingedsheep.sdk.scripting.effects.ReturnSelfFromExileTransformedEffect,
            targetRequirements = emptyList(),
            timing = TimingRule.SorcerySpeed,
            descriptionOverride = "Craft with $materials — $cost"
        )
    )
    keywordSet.add(Keyword.CRAFT)
}

/**
 * Craft with a heterogeneous set of materials — one distinct material per entry in [slots]
 * (CR 702.167, e.g. Throne of the Grim Captain's "Craft with a Dinosaur, a Merfolk, a Pirate,
 * and a Vampire {4}").
 *
 * Unlike the homogeneous [craft] above, each slot names its own material filter and each is filled
 * by exactly one distinct material. Because a single card can satisfy several slot filters (a
 * Merfolk Pirate matches two), the engine validates a chosen set by bipartite perfect matching
 * (`CraftSlotMatching`), not a per-subtype count — so four Vampires cannot fill Dinosaur/Merfolk/
 * Pirate/Vampire.
 *
 * The built [AbilityCost.Craft] carries the [slots] plus a union [filter] (`anyOf` of the slots)
 * and `minCount == maxCount == slots.size`, so the count-based candidate gathering, `canPay`, and
 * legal-action enumeration keep working unchanged; only the matching check is layered on top.
 *
 * ```kotlin
 * craft(
 *     slots = listOf(
 *         GameObjectFilter().withSubtype(Subtype.DINOSAUR),
 *         GameObjectFilter().withSubtype(Subtype.MERFOLK),
 *         GameObjectFilter().withSubtype(Subtype.PIRATE),
 *         GameObjectFilter().withSubtype(Subtype.VAMPIRE),
 *     ),
 *     cost = "{4}",
 *     materialDescription = "a Dinosaur, a Merfolk, a Pirate, and a Vampire"
 * )
 * ```
 *
 * @param slots The per-slot material filters, in printed order. Must be non-empty.
 * @param cost The mana portion of the craft cost.
 * @param materialDescription Optional override for the material text in the rendered cost
 *   description; defaults to the slot filters joined with commas and a trailing "and".
 */
fun CardBuilder.craft(
    slots: List<GameObjectFilter>,
    cost: String,
    materialDescription: String? = null
) {
    require(slots.isNotEmpty()) { "craft(slots = ...) needs at least one slot" }
    val materials = materialDescription ?: slots.mapIndexed { i, slot ->
        val prefix = if (i == slots.size - 1 && slots.size > 1) "and " else ""
        val d = slot.description
        val article = if (d.firstOrNull()?.lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u')) "an" else "a"
        "$prefix$article $d"
    }.joinToString(", ")
    activatedAbilities.add(
        ActivatedAbility(
            cost = AbilityCost.Composite(
                listOf(
                    AbilityCost.Atom(CostAtom.Mana(ManaCost.parse(cost))),
                    AbilityCost.Craft(
                        filter = GameObjectFilter(anyOf = slots),
                        minCount = slots.size,
                        maxCount = slots.size,
                        slots = slots
                    )
                )
            ),
            effect = com.wingedsheep.sdk.scripting.effects.ReturnSelfFromExileTransformedEffect,
            targetRequirements = emptyList(),
            timing = TimingRule.SorcerySpeed,
            descriptionOverride = "Craft with $materials — $cost"
        )
    )
    keywordSet.add(Keyword.CRAFT)
}
