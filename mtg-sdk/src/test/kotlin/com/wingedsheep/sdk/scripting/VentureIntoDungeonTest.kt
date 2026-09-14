package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.effects.VentureIntoDungeonEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VentureIntoDungeonTest : FunSpec({
    test("the venture facade exposes one canonical effect") {
        Effects.VentureIntoDungeon() shouldBe VentureIntoDungeonEffect
        Effects.VentureIntoDungeon().description shouldBe "Venture into the dungeon"
    }
})
