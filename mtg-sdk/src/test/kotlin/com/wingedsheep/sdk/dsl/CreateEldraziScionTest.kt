package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CreateEldraziScionTest : FunSpec({

    test("fixed count delegates to the authoritative predefined token") {
        val effect = Effects.CreateEldraziScion(
            count = 2,
            controller = EffectTarget.TargetController,
            imageUri = "scion.jpg"
        ).shouldBeInstanceOf<CreatePredefinedTokenEffect>()

        effect.tokenType shouldBe "Eldrazi Scion"
        effect.count shouldBe 2
        effect.controller shouldBe EffectTarget.TargetController
        effect.imageUri shouldBe "scion.jpg"
    }

    test("dynamic count delegates to the authoritative predefined token") {
        val effect = Effects.CreateEldraziScion(
            count = DynamicAmount.XValue,
            controller = EffectTarget.TargetController,
            imageUri = "scion.jpg"
        ).shouldBeInstanceOf<CreatePredefinedTokenEffect>()

        effect.tokenType shouldBe "Eldrazi Scion"
        effect.dynamicCount shouldBe DynamicAmount.XValue
        effect.controller shouldBe EffectTarget.TargetController
        effect.imageUri shouldBe "scion.jpg"
    }
})
