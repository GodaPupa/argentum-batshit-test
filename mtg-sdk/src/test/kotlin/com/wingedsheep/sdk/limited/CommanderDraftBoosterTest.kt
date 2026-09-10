package com.wingedsheep.sdk.limited

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.model.ScryfallMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class CommanderDraftBoosterTest : DescribeSpec({

    describe("CommanderDraftBooster") {

        it("produces packs of exactly 20 cards with a well-stocked pool") {
            val pool = generousPool()
            val pack = CommanderDraftBooster().generate(pool, Random(0))
            pack shouldHaveSize 20
        }

        it("ships exactly 2 legendary cards (creature or planeswalker) per pack") {
            val pool = generousPool()
            val pack = CommanderDraftBooster().generate(pool, Random(1))
            pack.count { it.isLegendaryCommanderCandidate() } shouldBe 2
        }

        it("ships 13 commons, 3 uncommons, and 1 non-legendary R/M slot when the pool has them") {
            val pool = generousPool()
            val pack = CommanderDraftBooster().generate(pool, Random(2))

            val nonLeg = pack.filter { !it.isLegendaryCommanderCandidate() }
            // 13 commons + 3 uncommons + 1 non-leg R/M + 1 bonus (any rarity) = 18 non-legendary slots
            nonLeg shouldHaveSize 18
            nonLeg.count { it.metadata.rarity == Rarity.COMMON } shouldBeGreaterThanOrEqual 13
            nonLeg.count { it.metadata.rarity == Rarity.UNCOMMON } shouldBeGreaterThanOrEqual 3
            nonLeg.count { it.metadata.rarity == Rarity.RARE || it.metadata.rarity == Rarity.MYTHIC } shouldBeGreaterThanOrEqual 1
        }

        it("falls back to Piper when the pool has no legendaries") {
            val piper = card("The Prismatic Piper", Rarity.COMMON, legendary = true, creature = true)
            val pool = nonLegendaryPool() // no legendaries at all
            val pack = CommanderDraftBooster(piperFallback = piper).generate(pool, Random(3))

            // Both legendary slots collapse to Piper.
            pack.count { it.name == "The Prismatic Piper" } shouldBe 2
            pack shouldHaveSize 20
        }

        it("backfills with non-legendary uncommons when no legendaries and no Piper are available") {
            val pool = nonLegendaryPool()
            val pack = CommanderDraftBooster(piperFallback = null).generate(pool, Random(4))

            // No legendary candidates appear; non-leg backfill keeps the pack full.
            pack.count { it.isLegendaryCommanderCandidate() } shouldBe 0
            pack shouldHaveSize 20
        }

        it("never duplicates a card name within a single pack") {
            val pool = generousPool()
            val pack = CommanderDraftBooster().generate(pool, Random(5))
            pack.map { it.name }.toSet().size shouldBe pack.size
        }

        it("accepts legendary planeswalkers in the legendary slots") {
            val planeswalker = card("Test Walker", Rarity.MYTHIC, legendary = true, planeswalker = true)
            val pool = listOf(planeswalker) + commonsPool(20) + uncommonsPool(10) + raresPool(5)
            // Force the legendary slot to use the planeswalker by giving it as the sole legendary candidate.
            val pack = CommanderDraftBooster(legendaries = 1).generate(pool, Random(6))
            pack.count { it.name == "Test Walker" } shouldBe 1
        }

        it("does not pull legendary creatures into the non-legendary rare-or-mythic slot") {
            // Pool with rare legendaries and rare non-legendaries; verify the non-leg R/M slot
            // never picks a legendary.
            val pool = listOf(
                card("Big Legend", Rarity.RARE, legendary = true, creature = true),
                card("Bigger Legend", Rarity.RARE, legendary = true, creature = true),
                card("Plain Rare A", Rarity.RARE, creature = true),
                card("Plain Rare B", Rarity.RARE, creature = true),
                card("Plain Rare C", Rarity.RARE, creature = true),
            ) + commonsPool(30) + uncommonsPool(15)

            repeat(50) { seed ->
                val pack = CommanderDraftBooster().generate(pool, Random(seed.toLong()))
                // The single non-legendary R/M slot must be one of the plain rares.
                val nonLegRMs = pack.filter {
                    !it.isLegendaryCommanderCandidate() &&
                        (it.metadata.rarity == Rarity.RARE || it.metadata.rarity == Rarity.MYTHIC)
                }
                // 1 dedicated non-leg R/M slot + occasionally the bonus slot rolled R/M.
                nonLegRMs.size shouldBeGreaterThanOrEqual 1
                nonLegRMs.forEach { it.name shouldBeIn listOf("Plain Rare A", "Plain Rare B", "Plain Rare C") }
            }
        }
    }
})

private fun CardDefinition.isLegendaryCommanderCandidate(): Boolean =
    typeLine.isLegendary && (typeLine.isCreature || CardType.PLANESWALKER in typeLine.cardTypes)

private fun card(
    name: String,
    rarity: Rarity,
    legendary: Boolean = false,
    creature: Boolean = true,
    planeswalker: Boolean = false,
): CardDefinition {
    val supertypes = if (legendary) setOf(Supertype.LEGENDARY) else emptySet()
    val cardTypes = buildSet {
        if (creature) add(CardType.CREATURE)
        if (planeswalker) add(CardType.PLANESWALKER)
    }.ifEmpty { setOf(CardType.CREATURE) }
    val subtypes = if (planeswalker) setOf(Subtype("Test")) else setOf(Subtype("Test"))
    return CardDefinition(
        name = name,
        manaCost = ManaCost.parse("{1}"),
        typeLine = TypeLine(supertypes = supertypes, cardTypes = cardTypes, subtypes = subtypes),
        creatureStats = if (creature) CreatureStats(1, 1) else null,
        startingLoyalty = if (planeswalker) 3 else null,
        metadata = ScryfallMetadata(rarity = rarity),
    )
}

private fun commonsPool(n: Int): List<CardDefinition> =
    (1..n).map { card("Common $it", Rarity.COMMON) }

private fun uncommonsPool(n: Int): List<CardDefinition> =
    (1..n).map { card("Uncommon $it", Rarity.UNCOMMON) }

private fun raresPool(n: Int): List<CardDefinition> =
    (1..n).map { card("Rare $it", Rarity.RARE) }

private fun mythicsPool(n: Int): List<CardDefinition> =
    (1..n).map { card("Mythic $it", Rarity.MYTHIC) }

private fun legendariesPool(n: Int, rarity: Rarity): List<CardDefinition> =
    (1..n).map { card("Legendary ${rarity.name} $it", rarity, legendary = true, creature = true) }

private fun nonLegendaryPool(): List<CardDefinition> =
    commonsPool(40) + uncommonsPool(20) + raresPool(10) + mythicsPool(3)

private fun generousPool(): List<CardDefinition> =
    nonLegendaryPool() +
        legendariesPool(8, Rarity.UNCOMMON) +
        legendariesPool(6, Rarity.RARE) +
        legendariesPool(2, Rarity.MYTHIC)
