package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class EntersAsCopyExceptionsTest : FunSpec({
    val json = Json { encodeDefaults = true }

    test("typed exceptions round trip with the replacement effect") {
        val effect = EntersAsCopy(exceptions = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT)))
        val encoded = json.encodeToString(ReplacementEffect.serializer(), effect)
        json.decodeFromString(ReplacementEffect.serializer(), encoded) shouldBe effect
        effect.description shouldBe "You may have this creature enter as a copy of any creature on the battlefield, except it's an artifact in addition to its other types"
    }

    test("legacy definitions omit the new default even when encoding defaults") {
        val effect = EntersAsCopy(additionalSubtypes = listOf("Bird"), additionalKeywords = listOf(Keyword.FLYING))
        val encoded = json.encodeToString(ReplacementEffect.serializer(), effect)
        json.parseToJsonElement(encoded).jsonObject.containsKey("exceptions") shouldBe false
        json.decodeFromString(ReplacementEffect.serializer(), encoded) shouldBe effect
        effect.description shouldBe "You may have this creature enter as a copy of any creature on the battlefield, except a Bird in addition to its other types and it has flying"
    }

    test("modern additions retain legacy subtype and keyword additions") {
        val effect = EntersAsCopy(
            additionalSubtypes = listOf("Bird"),
            additionalKeywords = listOf(Keyword.FLYING),
            exceptions = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT), addedKeywords = setOf(Keyword.HASTE)),
        )
        effect.copyExceptions.addedCardTypes shouldBe setOf(CardType.ARTIFACT)
        effect.copyExceptions.addedSubtypes shouldBe setOf(Subtype("Bird"))
        effect.copyExceptions.addedKeywords shouldBe setOf(Keyword.FLYING, Keyword.HASTE)
    }

    test("modern explicit overrides take precedence over historical riders") {
        val effect = EntersAsCopy(
            nameOverride = "Legacy Name", powerOverride = 1, toughnessOverride = 2,
            exceptions = CopyExceptions(nameOverride = "Modern Name", powerOverride = 3, toughnessOverride = 4),
        )
        effect.copyExceptions.nameOverride shouldBe "Modern Name"
        effect.copyExceptions.powerOverride shouldBe 3
        effect.copyExceptions.toughnessOverride shouldBe 4
    }
})
