package com.wingedsheep.tooling.coverage

import com.wingedsheep.tooling.coverage.emitter.Emitter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Pins the emitter's `AsPermanentEnters` -> `EntersWithACounter` mapping: a self-scoped single +1/+1
 * counter renders as `replacementEffect(EntersWithCounters(count = 1, selfOnly = true))`, while the
 * shapes the engine can't reproduce exactly here — a non-+1/+1 counter kind, or a group scope rather
 * than this permanent — decline to a SCAFFOLD rather than being silently widened.
 *
 * Each fixture is a synthetic single-rule creature, so the only thing under test is the enters
 * replacement. Hermetic: no IR download, no Scryfall cache.
 */
class AsPermanentEntersCounterTest : StringSpec({

    val effects = Registry.loadEffectSerialNames()
    val keywords = Registry.loadKeywords()

    // Builds a creature whose only rule is `AsPermanentEnters` with one `EntersWithACounter` replacement.
    // `permanent` is the rule's scope arg (`ThisPermanent` for the self case). `counterType` + `counterArgs`
    // describe the counter; PTCounter [1,1] is the +1/+1 the mapping renders.
    fun entersCard(
        permanent: String,
        counterType: String,
        counterPt: Pair<Int, Int>?,
    ): JsonObject = buildJsonObject {
        put("Name", JsonPrimitive("Test Enters $permanent $counterType"))
        putJsonObject("Typeline") {
            putJsonArray("Supertypes") {}
            putJsonArray("Cardtypes") { add(JsonPrimitive("Creature")) }
            putJsonArray("Subtypes") {}
        }
        putJsonArray("ManaCost") { addJsonObject { put("_ManaSymbol", JsonPrimitive("ManaCostG")) } }
        putJsonObject("CardPT") { putJsonArray("args") { add(JsonPrimitive(0)); add(JsonPrimitive(0)) } }
        putJsonArray("Rules") {
            addJsonObject {
                put("_Rule", JsonPrimitive("AsPermanentEnters"))
                putJsonArray("args") {
                    addJsonObject { put("_Permanent", JsonPrimitive(permanent)) }
                    addJsonArray {
                        addJsonObject {
                            put("_ReplacementActionWouldEnter", JsonPrimitive("EntersWithACounter"))
                            putJsonObject("args") {
                                put("_CounterType", JsonPrimitive(counterType))
                                if (counterPt != null) {
                                    putJsonArray("args") { add(JsonPrimitive(counterPt.first)); add(JsonPrimitive(counterPt.second)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    "a self-scoped +1/+1 EntersWithACounter renders EntersWithCounters(count = 1, selfOnly = true)" {
        val r = Emitter.renderCard(entersCard("ThisPermanent", "PTCounter", 1 to 1), null, effects, keywords)
        r.complete shouldBe true
        r.text shouldContain "replacementEffect(EntersWithCounters(count = 1, selfOnly = true))"
    }

    "a non-+1/+1 counter kind declines to a scaffold" {
        val r = Emitter.renderCard(entersCard("ThisPermanent", "ShieldCounter", null), null, effects, keywords)
        r.complete shouldBe false
        r.text shouldNotContain "EntersWithCounters"
    }

    "a group scope (not this permanent) declines to a scaffold" {
        val r = Emitter.renderCard(entersCard("EachCreatureYouControl", "PTCounter", 1 to 1), null, effects, keywords)
        r.complete shouldBe false
        r.text shouldNotContain "EntersWithCounters"
    }
})
