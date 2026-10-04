package com.wingedsheep.gym.pest

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog

/**
 * Exact card-definition registry used by the Phase-B production entrypoint.
 *
 * MtgSet.cards intentionally excludes basic lands, so every discovered set contributes both
 * its ordinary card definitions and its basic-land definitions. This is deterministic registry
 * assembly only: it initializes no game and draws no entropy.
 */
internal object PestPhaseBProductionRegistry {
    fun build(): CardRegistry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }
}
