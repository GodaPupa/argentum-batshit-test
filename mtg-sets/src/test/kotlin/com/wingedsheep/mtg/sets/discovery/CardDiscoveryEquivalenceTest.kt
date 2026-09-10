package com.wingedsheep.mtg.sets.discovery

import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.MtgSet
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

/**
 * Equivalence harness for [CardDiscovery] across every registered set.
 *
 * For each [MtgSet] in [MtgSetCatalog.all], asserts:
 *
 *  1. `CardDiscovery.findIn(...)` returns exactly the non-basic-land cards in
 *     `MtgSet.cards`. (`cards` may currently include basic-land variants via
 *     `... + FooBasicLands` — we filter those out here so this test stays meaningful
 *     during the rollout, when sets still concatenate basics into `cards`.)
 *
 *  2. `CardDiscovery.findBasicLandsIn(...)` returns exactly `MtgSet.basicLands`,
 *     compared by name. Sets that have no own basics (e.g. Scourge) are skipped.
 *
 * This is the gate for the Phase 3/4 rollout: as long as it stays green, flipping a
 * set over to `cards by lazy { CardDiscovery.findIn(...) }` (and likewise for
 * `basicLands`) cannot silently drop or add a card. Delete the harness once every set
 * is converted and the manual lists are gone.
 *
 * A set's cards package is `<the set object's own package>.cards` — derived from the object
 * itself rather than the set code, since a directory can't always match the code (`con` is a
 * reserved filename on Windows, so Conflux lives in `definitions/conflux/`).
 */
class CardDiscoveryEquivalenceTest : FunSpec({

    test("CardDiscovery.findIn agrees with the non-basic part of every MtgSet.cards list") {
        assertSoftly {
            for (set in MtgSetCatalog.all) {
                val packageName = packageNameFor(set)
                withClue("$packageName (${set.code})") {
                    val discovered = CardDiscovery.findIn(packageName).map { it.name }
                    val manual = set.cards
                        .filterNot { it.typeLine.isBasicLand }
                        .map { it.name }
                    discovered.shouldContainExactlyInAnyOrder(manual)
                }
            }
        }
    }

    test("CardDiscovery.findBasicLandsIn agrees with every MtgSet.basicLands list") {
        assertSoftly {
            for (set in MtgSetCatalog.all) {
                if (set.basicLands.isEmpty()) continue
                val packageName = packageNameFor(set)
                withClue("$packageName (${set.code})") {
                    val discovered = CardDiscovery.findBasicLandsIn(packageName).map { it.name }
                    val manual = set.basicLands.map { it.name }
                    discovered.shouldContainExactlyInAnyOrder(manual)
                }
            }
        }
    }
})

private fun packageNameFor(set: MtgSet): String =
    "${set::class.java.packageName}.cards"
