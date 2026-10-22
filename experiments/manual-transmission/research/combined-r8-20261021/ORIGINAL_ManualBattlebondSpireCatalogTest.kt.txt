package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Manual Transmission Phase 2 BBD discovery regression.
 * Catalog-only; never initialize an actual deck, seed, policy or game.
 *
 * Negative control is the exact source-bound 467/800 registry inventory:
 * 10 original Spire Garden tests ran by explicitly registering the card in a
 * fixture, while MtgSetCatalog lacked the BBD MtgSet object.
 */
class ManualBattlebondSpireCatalogTest : FunSpec({
    fun fullRegistry(): CardRegistry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
    }

    test("BBD set is discoverable once and Spire Garden resolves through actual catalog registry") {
        val registeredBbd = MtgSetCatalog.all.filter { it.code == "BBD" }
        registeredBbd.size shouldBe 1
        registeredBbd.single().cards.count { it.name == "Spire Garden" } shouldBe 1
        val card = fullRegistry().getCard("Spire Garden")
        card shouldNotBe null
        card!!.name shouldBe "Spire Garden"
        card.typeLine.isLand shouldBe true
    }

    test("previously unresolved Gwenna and Amphin are not synthesized by BBD registration") {
        val registry = fullRegistry()
        registry.getCard("Spire Garden") shouldNotBe null
        registry.getCard("Gwenna, Eyes of Gaea") shouldBe null
        registry.getCard("Amphin Mutineer") shouldBe null
    }
})
