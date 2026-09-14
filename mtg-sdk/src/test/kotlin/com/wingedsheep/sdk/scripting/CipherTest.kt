package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.effects.CipherEncodeEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CipherTest : FunSpec({
    test("cipher decorates a spell with its display keyword and final encoding effect") {
        val definition = card("Cipher Fixture") {
            manaCost = "{U}"
            typeLine = "Sorcery"
            spell {
                effect = Effects.DrawCards(1)
                cipher()
            }
        }

        definition.keywords.contains(Keyword.CIPHER) shouldBe true
        val composite = definition.spellEffect.shouldBeInstanceOf<CompositeEffect>()
        composite.effects.last() shouldBe CipherEncodeEffect
    }

    test("cipher combat trigger reuses the copy-card and free-cast pipeline behind a may gate") {
        val may = Cipher.copyAbility.effect.shouldBeInstanceOf<GatedEffect>()
        may.then.shouldBeInstanceOf<CompositeEffect>().effects.size shouldBe 2
    }
})
