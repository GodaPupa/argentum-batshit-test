package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Fixtures
import com.wingedsheep.tooling.coverage.Registry
import com.wingedsheep.tooling.coverage.J
import com.wingedsheep.tooling.coverage.render
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class GeneratorCalibrationRepairTest : StringSpec({
    val data = J.parseToJsonElement(checkNotNull(javaClass.getResource("/calibration-repair/current-ir.json")).readText()) as JsonObject
    val current = data["current"] as JsonObject
    fun card(name: String) = current[name] as JsonObject
    fun action(name: String): JsonObject {
        val rule = (card(name)["Rules"] as JsonArray).single() as JsonObject
        return ((rule["args"] as JsonObject)["args"] as JsonArray).single() as JsonObject
    }
    fun emit(node: JsonObject, oracle: String? = null) = EmitCtx(emptySet(), oracle).renderAction(node, null)?.let(::render)
    fun truce(text: String) = EmitCtx(emptySet()).eachplayerMaydraw(J.parseToJsonElement(text) as JsonObject)?.let(::render)
    val truceIr = card("Temporary Truce").toString()
    "collected destruction preserves Boiling Seas" {
        emit(action("Boiling Seas")) shouldBe "Effects.DestroyAll(GameObjectFilter.Land.withSubtype(Subtype.ISLAND), noRegenerate = false)"
    }
    "collected destruction preserves Devastation" {
        emit(action("Devastation")) shouldBe "Effects.DestroyAll(GameObjectFilter.CreatureOrLand, noRegenerate = false)"
    }
    "collected destruction preserves Nature's Ruin" {
        emit(action("Nature's Ruin")) shouldBe "Effects.DestroyAll(GameObjectFilter.Creature.withColor(Color.GREEN), noRegenerate = false)"
    }
    "collected destruction preserves Virtue's Ruin" {
        emit(action("Virtue's Ruin")) shouldBe "Effects.DestroyAll(GameObjectFilter.Creature.withColor(Color.WHITE), noRegenerate = false)"
    }
    "collected destruction preserves no regeneration" {
        val node = J.parseToJsonElement(action("Boiling Seas").toString().replace("DestroyEachPermanent", "DestroyEachPermanentNoRegen")) as JsonObject
        emit(node) shouldBe "Effects.DestroyAll(GameObjectFilter.Land.withSubtype(Subtype.ISLAND), noRegenerate = true)"
    }
    "targeted destruction retains its selected target path" {
        val node = J.parseToJsonElement("""{"_Action":"DestroyEachPermanent","args":{"_Permanents":"Ref_TargetPermanents"}}""") as JsonObject
        emit(node) shouldBe "ForEachTargetEffect(listOf(Effects.Move(EffectTarget.ContextTarget(0), Zone.GRAVEYARD, byDestruction = true)))"
    }
    "self excluding destruction retains its existing binding" {
        emit(action("Boiling Seas"), "Destroy all other Islands.") shouldBe "Effects.ForEachInGroup(GroupFilter(GameObjectFilter.Land.withSubtype(Subtype.ISLAND), excludeSelf = true), Effects.Move(EffectTarget.Self, Zone.GRAVEYARD, byDestruction = true), noRegenerate = false)"
    }
    "unrenderable destruction restrictions still decline" {
        val node = J.parseToJsonElement("""{"_Action":"DestroyEachPermanent","args":{"_Permanents":"SharesAColorWithPermanent","args":{"_Permanent":"Ref_TargetPermanent"}}}""") as JsonObject
        emit(node).shouldBeNull()
    }
    "current singular player envelopes preserve the draw and life contract" {
        truce(truceIr) shouldBe "Patterns.Hand.eachPlayerMayDraw(maxCards = 2, lifePerCardNotDrawn = 2)"
    }
    "legacy player envelope keeps its existing emission" {
        truce(data["legacy_truce"].toString()) shouldBe "Patterns.Hand.eachPlayerMayDraw(maxCards = 2, lifePerCardNotDrawn = 2)"
    }
    "current player envelope rejects changed player scope" {
        truce(truceIr.replace("AnyPlayer", "Opponent")).shouldBeNull()
    }
    "current player envelope rejects an unrelated remainder ceiling" {
        truce(truceIr.replace("\"Minus\",\"args\":[{\"_GameNumber\":\"Integer\",\"args\":2}", "\"Minus\",\"args\":[{\"_GameNumber\":\"Integer\",\"args\":3}")).shouldBeNull()
    }
    "current player envelope rejects an unrelated count reference" {
        truce(truceIr.replace("NumberOfCardsDrawnThisWay", "NumberOfCardsInHand")).shouldBeNull()
    }
    "current player envelope rejects extra clauses and annotations" {
        truce(truceIr.replace("\"_Action\":\"DrawUptoNumberCards\"", "\"extra\":true,\"_Action\":\"DrawUptoNumberCards\"")).shouldBeNull()
        truce(truceIr.replace("\"Rules\":[", "\"Rules\":[{\"_Rule\":\"Unknown\"},")).shouldBeNull()
    }
    "capture exact fixture projections for the required compatibility successor" {
        val sets = Fixtures.committedSets()
        sets.sorted() shouldBe listOf("10E", "CHK", "EOE", "HOB", "INR", "ISD", "ONS", "POR", "RAV", "SOS", "TMP")
        val effects = Registry.loadEffectSerialNames()
        val keywords = Registry.loadKeywords()
        val out = java.io.File(Fixtures.FIXTURES_DIR, "../../../../build/calibration-projections").canonicalFile
        out.mkdirs()
        for (code in sets) java.io.File(out, "$code.txt").writeText(Fixtures.renderGolden(Fixtures.load(code), effects, keywords))
    }
})
