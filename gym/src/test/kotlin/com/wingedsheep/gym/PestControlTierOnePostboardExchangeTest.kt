package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_75_HASH
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_SIDEBOARD_HASH
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOnePostboardExchange
import com.wingedsheep.gym.matchup.PestControlTierOneSpyComboAdmission
import com.wingedsheep.gym.matchup.PestPostboardDeckBinding
import com.wingedsheep.gym.matchup.PestPostboardExchange
import com.wingedsheep.gym.matchup.PestTierOneDeck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOnePostboardExchangeTest : FunSpec({
    val none = PestPostboardExchange(emptyMap(), emptyMap())
    // Arithmetic fixture only. It is not a proposed matchup strategy or a frozen boarding plan.
    val exchange = PestPostboardExchange(
        toMain = linkedMapOf("Nature's Claim" to 3, "Snuff Out" to 3),
        toSideboard = linkedMapOf("Weather the Storm" to 4, "Chainer's Edict" to 2),
    )
    val expected = PestPostboardDeckBinding(
        deck = PestTierOneDeck.PEST_CONTROL,
        originalMainSha256 = PEST_CONTROL_V10_HASH,
        originalSideboardSha256 = PEST_CONTROL_V10_SIDEBOARD_HASH,
        originalComplete75Sha256 = PEST_CONTROL_V10_75_HASH,
        postboardMainSha256 = "a2dab5913b649ffa20f04665926a2d252cc75f347681c60144067d7d12a5c104",
        postboardSideboardSha256 = "c44e126d78f3d75ed01b00e095e4ed2917dc4d4f9836f5c21d0eeb55bed2445d",
        postboardComplete75Sha256 = "b00f50ac322c05a8a0591198ca4a28fd8daa07cc5c651cf64fee3aeee517ad89",
    )

    PestTierOneDeck.entries.forEach { identity ->
        test("unchanged exchange verifies all frozen hashes and both zones for $identity") {
            val prepared = PestControlTierOnePostboardExchange.prepare(identity, none)
            prepared.binding.deck shouldBe identity
            prepared.binding.postboardMainSha256 shouldBe prepared.binding.originalMainSha256
            prepared.binding.postboardSideboardSha256 shouldBe prepared.binding.originalSideboardSha256
            prepared.binding.postboardComplete75Sha256 shouldBe prepared.binding.originalComplete75Sha256
            val deck = prepared.toDeck(prepared.binding)
            deck.size shouldBe 60
            deck.sideboard.size shouldBe 15
            deck.cards.groupingBy { it }.eachCount() shouldBe prepared.mainCounts
            deck.sideboard.groupingBy { it.name }.eachCount() shouldBe prepared.sideboardCounts
        }
    }

    test("exact exchange matches independently calculated byte hashes and conserves all copies") {
        val originalMain = PestControlPreboardDecks.pestMainCounts
        val originalSideboard = PestControlPreboardDecks.pestSideboardCounts
        val prepared = PestControlTierOnePostboardExchange.prepare(PestTierOneDeck.PEST_CONTROL, exchange)
        prepared.binding shouldBe expected
        val deck = prepared.toDeck(expected)
        deck.cards.count { it == "Nature's Claim" } shouldBe 3
        deck.cards.count { it == "Snuff Out" } shouldBe 3
        deck.cards.count { it == "Weather the Storm" || it == "Chainer's Edict" } shouldBe 0
        deck.sideboard.count { it.name == "Weather the Storm" } shouldBe 4
        deck.sideboard.count { it.name == "Chainer's Edict" } shouldBe 2
        val originalCopies = (originalMain.keys + originalSideboard.keys).associateWith {
            originalMain.getOrDefault(it, 0) + originalSideboard.getOrDefault(it, 0)
        }
        (deck.cards + deck.sideboard.map { it.name }).groupingBy { it }.eachCount() shouldBe originalCopies
        PestControlPreboardDecks.pestMainCounts shouldBe originalMain
        PestControlPreboardDecks.pestSideboardCounts shouldBe originalSideboard
    }

    test("exchange order does not alter deterministic deck order or hashes") {
        val reverse = PestPostboardExchange(
            exchange.toMain.entries.reversed().associate { it.toPair() },
            exchange.toSideboard.entries.reversed().associate { it.toPair() },
        )
        val first = PestControlTierOnePostboardExchange.prepare(PestTierOneDeck.PEST_CONTROL, exchange)
        val second = PestControlTierOnePostboardExchange.prepare(PestTierOneDeck.PEST_CONTROL, reverse)
        first.binding shouldBe second.binding
        first.toDeck(expected) shouldBe second.toDeck(expected)
    }

    test("names already present in both zones aggregate instead of replacing existing copies") {
        val prepared = PestControlTierOnePostboardExchange.prepare(
            PestTierOneDeck.SPY_COMBO,
            PestPostboardExchange(mapOf("Nyxborn Hydra" to 1), mapOf("Lead the Stampede" to 1)),
        )
        prepared.mainCounts["Nyxborn Hydra"] shouldBe 3
        prepared.sideboardCounts["Nyxborn Hydra"] shouldBe null
        prepared.mainCounts["Lead the Stampede"] shouldBe 3
        prepared.sideboardCounts["Lead the Stampede"] shouldBe 1
        PestControlTierOneSpyComboAdmission.mainCounts["Nyxborn Hydra"] shouldBe 2
        PestControlTierOneSpyComboAdmission.sideboardCounts["Nyxborn Hydra"] shouldBe 1
    }

    test("caller mutations cannot alter a prepared deck or a future reconstruction") {
        val incoming = exchange.toMain.toMutableMap()
        val outgoing = exchange.toSideboard.toMutableMap()
        val prepared = PestControlTierOnePostboardExchange.prepare(
            PestTierOneDeck.PEST_CONTROL, PestPostboardExchange(incoming, outgoing),
        )
        incoming.clear()
        outgoing["Forest"] = 10
        (prepared.mainCounts as? MutableMap<String, Int>)?.clear()
        (prepared.sideboardCounts as? MutableMap<String, Int>)?.clear()
        prepared.binding shouldBe expected
        prepared.toDeck(expected).cards.size shouldBe 60
        prepared.toDeck(expected).sideboard.size shouldBe 15
        PestControlTierOnePostboardExchange.prepare(PestTierOneDeck.PEST_CONTROL, exchange).binding shouldBe expected
    }

    val badExchanges = linkedMapOf(
        "unknown incoming name" to PestPostboardExchange(mapOf("Lightning Bolt" to 1), mapOf("Forest" to 1)),
        "unknown outgoing name" to PestPostboardExchange(mapOf("Nature's Claim" to 1), mapOf("Island" to 1)),
        "overdrawn sideboard" to PestPostboardExchange(mapOf("Nature's Claim" to 4), mapOf("Forest" to 4)),
        "overdrawn main" to PestPostboardExchange(mapOf("Snuff Out" to 3), mapOf("Chainer's Edict" to 3)),
        "zero incoming" to PestPostboardExchange(mapOf("Nature's Claim" to 0), emptyMap()),
        "negative outgoing" to PestPostboardExchange(emptyMap(), mapOf("Forest" to -1)),
        "overflow quantity" to PestPostboardExchange(mapOf("Nature's Claim" to Int.MAX_VALUE), emptyMap()),
        "unbalanced exchange" to PestPostboardExchange(mapOf("Nature's Claim" to 2), mapOf("Forest" to 1)),
    )
    badExchanges.forEach { (label, bad) ->
        test("reject $label before constructing any deck") {
            shouldThrow<IllegalArgumentException> {
                PestControlTierOnePostboardExchange.prepare(PestTierOneDeck.PEST_CONTROL, bad)
            }
        }
    }

    test("reject a name moved in both directions instead of silently normalizing the plan") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOnePostboardExchange.prepare(
                PestTierOneDeck.SPY_COMBO,
                PestPostboardExchange(mapOf("Swamp" to 1), mapOf("Swamp" to 1)),
            )
        }
    }

    test("reject every changed frozen identity field before runtime deck construction") {
        val prepared = PestControlTierOnePostboardExchange.prepare(PestTierOneDeck.PEST_CONTROL, exchange)
        val falseHash = "0".repeat(64)
        val drifted = listOf(
            expected.copy(deck = PestTierOneDeck.MONSTER_TRON),
            expected.copy(originalMainSha256 = falseHash),
            expected.copy(originalSideboardSha256 = falseHash),
            expected.copy(originalComplete75Sha256 = falseHash),
            expected.copy(postboardMainSha256 = falseHash),
            expected.copy(postboardSideboardSha256 = falseHash),
            expected.copy(postboardComplete75Sha256 = falseHash),
        )
        drifted.forEach { binding ->
            shouldThrow<IllegalArgumentException> { prepared.toDeck(binding) }
        }
    }
})
