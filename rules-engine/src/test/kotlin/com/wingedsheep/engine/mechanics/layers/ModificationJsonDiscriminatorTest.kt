package com.wingedsheep.engine.mechanics.layers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exact source-only regression: a polymorphic modification is serialized with JSON's
 * existing "type" discriminator AND a separate "cardType" payload. Does not create
 * a sampled Manual deck, modify rules semantics, or change the engine Json contract.
 */
class ModificationJsonDiscriminatorTest : FunSpec({
    val json = Json

    test("AddType has disjoint type discriminator and cardType payload and roundtrips") {
        val source: Modification = Modification.AddType("CREATURE")
        val wire = json.encodeToString<Modification>(source)
        val payload = json.parseToJsonElement(wire).jsonObject
        payload["type"]!!.jsonPrimitive.content.isNotBlank() shouldBe true
        payload["cardType"]!!.jsonPrimitive.content shouldBe "CREATURE"
        json.decodeFromString<Modification>(wire) shouldBe source
    }

    test("RemoveType has disjoint type discriminator and cardType payload and roundtrips") {
        val source: Modification = Modification.RemoveType("CREATURE")
        val wire = json.encodeToString<Modification>(source)
        val payload = json.parseToJsonElement(wire).jsonObject
        payload["type"]!!.jsonPrimitive.content.isNotBlank() shouldBe true
        payload["cardType"]!!.jsonPrimitive.content shouldBe "CREATURE"
        json.decodeFromString<Modification>(wire) shouldBe source
    }

    test("ContinuousEffectData persists a dynamic RemoveType component with the same wire contract") {
        val original = ContinuousEffectData(modification = Modification.RemoveType("CREATURE"))
        val wire = json.encodeToString(original)
        val payload = json.parseToJsonElement(wire).jsonObject["modification"]!!.jsonObject
        payload["type"]!!.jsonPrimitive.content.isNotBlank() shouldBe true
        payload["cardType"]!!.jsonPrimitive.content shouldBe "CREATURE"
        json.decodeFromString<ContinuousEffectData>(wire) shouldBe original
    }
})
