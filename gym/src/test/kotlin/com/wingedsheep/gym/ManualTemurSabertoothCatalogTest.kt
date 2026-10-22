package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Source-only catalog qualification. No frozen full deck/game/seed initialized. */
class ManualTemurSabertoothCatalogTest : FunSpec({
    fun registry() = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
    }

    test("Temur Sabertooth is a genuine FRF CardRegistry definition with an activated bounce ability") {
        val frf = MtgSetCatalog.byCode("FRF")
        frf shouldNotBe null
        frf!!.cards.count { it.name == "Temur Sabertooth" } shouldBe 1
        val card = registry().getCard("Temur Sabertooth")
        card shouldNotBe null
        card!!.name shouldBe "Temur Sabertooth"
        card.script.activatedAbilities.size shouldBe 1
    }

    test("Temur card receiving does not fabricate unrelated unresolved Amphin Mutineer") {
        val catalog = registry()
        catalog.getCard("Temur Sabertooth") shouldNotBe null
        catalog.getCard("Amphin Mutineer") shouldBe null
    }
})
