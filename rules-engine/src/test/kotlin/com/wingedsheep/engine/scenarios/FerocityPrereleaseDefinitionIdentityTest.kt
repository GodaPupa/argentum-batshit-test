package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.research.ferocity.FerocityOfTheHuntPrerelease
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.serialization.CardSerialization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Metadata equivalence only. The comparison value is never registered or used for replay. */
class FerocityPrereleaseDefinitionIdentityTest : FunSpec({
    test("shared prerelease definition preserves every qualified field except generated ability identity") {
        val json = Json(CardSerialization.json) { encodeDefaults = true }
        val baseline = requireNotNull(javaClass.getResourceAsStream(
            "/ferocity-recycling/prerelease-definition/legacy-ferocity.raw.json"
        )).bufferedReader().use { json.parseToJsonElement(it.readText()) }
        val actual = json.encodeToJsonElement(CardDefinition.serializer(), FerocityOfTheHuntPrerelease)

        // The later Gift SDK adds two serialized defaults. Preserve the raw legacy resource;
        // adapt only this comparison value after proving the exact old and new field shapes.
        val legacyScript = baseline.jsonObject.getValue("script").jsonObject
        legacyScript.containsKey("giftTargetRequirements") shouldBe false
        legacyScript.containsKey("giftSpellEffect") shouldBe false
        val giftDefaults = mapOf<String, JsonElement>(
            "giftTargetRequirements" to JsonArray(emptyList()),
            "giftSpellEffect" to JsonNull,
        )
        val actualScript = actual.jsonObject.getValue("script").jsonObject
        giftDefaults.forEach { (key, expected) ->
            actualScript.getValue(key) shouldBe expected
        }
        val baselineWithGiftDefaults = JsonObject(baseline.jsonObject + (
            "script" to JsonObject(legacyScript + giftDefaults)
        ))

        fun semanticComparisonOnly(value: JsonElement): JsonElement {
            val root = value.jsonObject
            val script = root.getValue("script").jsonObject
            val abilities = script.getValue("triggeredAbilities").jsonArray
            abilities.size shouldBe 1
            val ability = abilities.single().jsonObject
            ability.getValue("id").jsonPrimitive.content.matches(Regex("^ability_[0-9]+$")) shouldBe true
            return JsonObject(root + ("script" to JsonObject(script + ("triggeredAbilities" to JsonArray(
                listOf(JsonObject(ability + ("id" to JsonPrimitive("ability_1"))))
            )))))
        }

        semanticComparisonOnly(actual) shouldBe semanticComparisonOnly(baselineWithGiftDefaults)
        (ferocityResearchFixture === FerocityOfTheHuntPrerelease) shouldBe true
    }
})
