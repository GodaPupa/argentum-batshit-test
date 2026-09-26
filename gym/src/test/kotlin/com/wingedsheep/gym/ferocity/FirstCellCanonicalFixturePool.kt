package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.CardDefinition

/**
 * Trusted fixed-scenario setup only, never a pilot API. ScenarioTestBase appends TestCards after
 * the real catalog and can override familiar names with simplified definitions. Register these
 * named canonical definitions last, preserving the catalog's deterministic alias ordering.
 * Predefined tokens may be selectedNames without a catalog entry; every requiredNames entry must
 * have a real definition. The trial runner's independent pinnedRegistry remains authoritative for
 * admission; this fixture helper does not create pins or admit a source version.
 */
internal fun registerFirstCellCanonicalCards(
    registry: CardRegistry,
    selectedNames: Set<String>,
    requiredNames: Set<String> = selectedNames,
): List<CardDefinition> {
    require(requiredNames.all { it in selectedNames })
    val definitions = MtgSetCatalog.all.flatMap { it.cards + it.basicLands }
        .filter { it.name in selectedNames }
    val names = definitions.map { it.name }.toSet()
    require(requiredNames.all { it in names }) { "Required fixed-scenario card lacks a canonical definition" }
    registry.register(definitions)
    return names.sorted().map(registry::requireCard)
}
