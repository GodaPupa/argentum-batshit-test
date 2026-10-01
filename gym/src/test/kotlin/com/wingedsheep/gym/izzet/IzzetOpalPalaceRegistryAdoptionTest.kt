package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IzzetOpalPalaceRegistryAdoptionTest : FunSpec({
    test("canonical Opal Palace is discovered in current runtime") {
        val registry = CardRegistry()
        MtgSetCatalog.all.forEach { registry.register(it.cards) }
        registry.findByName("Opal Palace")?.name shouldBe "Opal Palace"
    }
})
