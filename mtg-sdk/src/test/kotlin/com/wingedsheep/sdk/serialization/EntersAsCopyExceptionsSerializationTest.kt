package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.ReplacementEffect
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class EntersAsCopyExceptionsSerializationTest : StringSpec({
    "polymorphic replacement retains copy exceptions and their rendered meaning" {
        val original = EntersAsCopy(exceptions = CopyExceptions(
            addedCardTypes = setOf(CardType.ARTIFACT),
            addedKeywords = setOf(Keyword.FLYING),
        ))
        val encoded = CardSerialization.json.encodeToString(ReplacementEffect.serializer(), original)
        val restored = CardSerialization.json.decodeFromString(ReplacementEffect.serializer(), encoded)
        restored shouldBe original
        restored.description shouldBe "You may have this creature enter as a copy of any creature on the battlefield, except it's an artifact in addition to its other types and it has flying"
    }

    "plain and historical rider payloads omit an empty shared exception even with defaults enabled" {
        val json = Json { encodeDefaults = true }
        for (original in listOf(EntersAsCopy(), EntersAsCopy(additionalKeywords = listOf(Keyword.FLYING)))) {
            val encoded = json.encodeToString(ReplacementEffect.serializer(), original)
            json.parseToJsonElement(encoded).jsonObject.containsKey("exceptions") shouldBe false
            json.decodeFromString(ReplacementEffect.serializer(), encoded) shouldBe original
        }
    }
})
