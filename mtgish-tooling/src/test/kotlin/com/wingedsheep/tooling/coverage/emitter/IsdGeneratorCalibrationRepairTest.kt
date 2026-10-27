package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Fixtures
import com.wingedsheep.tooling.coverage.Registry
import com.wingedsheep.tooling.coverage.J
import com.wingedsheep.tooling.coverage.render
import com.wingedsheep.tooling.coverage.strField
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class IsdGeneratorCalibrationRepairTest : StringSpec({
    val current = J.parseToJsonElement(checkNotNull(javaClass.getResource("/isd-calibration-repair/current-ir.json")).readText()) as JsonObject
    val fixture = Fixtures.load("ISD")
    val effects = Registry.loadEffectSerialNames()
    val keywords = Registry.loadKeywords()
    fun obj(text: String) = J.parseToJsonElement(text) as JsonObject
    fun card(name: String) = current[name] as JsonObject
    fun whole(name: String, ir: JsonObject) = Emitter.renderCard(ir, fixture.scryfall[name], effects, keywords)
    fun actions(node: JsonElement): List<JsonObject> = when (node) {
        is JsonObject -> (if (node.containsKey("_Action")) listOf(node) else emptyList()) + node.values.flatMap(::actions)
        is JsonArray -> node.flatMap(::actions)
        else -> emptyList()
    }
    fun action(name: String, tag: String) = actions(card(name)).single { it.strField("_Action") == tag }
    fun emit(node: JsonObject, target: String? = "t") = EmitCtx(keywords).renderAction(node, target)?.let(::render)
    fun token(specs: JsonArray, flags: JsonElement = JsonArray(emptyList())) = obj("""{"_Action":"CreateTokens","args":[$specs,$flags]}""")
    val spirit = (action("Doomed Traveler", "CreateTokens")["args"] as JsonArray)[0] as JsonArray
    val tapped = obj("""{"_TokenFlag":"EntersTapped"}""")
    val exPermanent = action("Silverchase Fox", "Exile")
    val exGraveyard = action("Purify the Grave", "Exile")
    "morbid gate preserves Hollowhenge Scavenger" {
        val result = whole("Hollowhenge Scavenger", card("Hollowhenge Scavenger"))
        result.complete shouldBe true
        result.text.contains("interveningIf = Conditions.CreatureDiedThisTurn") shouldBe true
        result.text.contains("triggerCondition =") shouldBe false
        result.text.contains("triggerRestriction =") shouldBe false
        result.text shouldBe whole("Hollowhenge Scavenger", fixture.mtgish.getValue("Hollowhenge Scavenger")).text
    }
    "morbid gate preserves Morkrut Banshee" {
        val result = whole("Morkrut Banshee", card("Morkrut Banshee"))
        result.complete shouldBe true
        result.text.contains("interveningIf = Conditions.CreatureDiedThisTurn") shouldBe true
        result.text.contains("triggerCondition =") shouldBe false
        result.text.contains("triggerRestriction =") shouldBe false
        result.text shouldBe whole("Morkrut Banshee", fixture.mtgish.getValue("Morkrut Banshee")).text
    }
    "morbid gate preserves Reaper from the Abyss" {
        val result = whole("Reaper from the Abyss", card("Reaper from the Abyss"))
        result.complete shouldBe true
        result.text.contains("interveningIf = Conditions.CreatureDiedThisTurn") shouldBe true
        result.text.contains("triggerCondition =") shouldBe false
        result.text.contains("triggerRestriction =") shouldBe false
        result.text shouldBe whole("Reaper from the Abyss", fixture.mtgish.getValue("Reaper from the Abyss")).text
    }
    "current and frozen forms preserve Army of the Damned" {
        val result = whole("Army of the Damned", card("Army of the Damned"))
        result.complete shouldBe true
        result.text shouldBe whole("Army of the Damned", fixture.mtgish.getValue("Army of the Damned")).text
    }
    "current and frozen forms preserve Doomed Traveler" {
        val result = whole("Doomed Traveler", card("Doomed Traveler"))
        result.complete shouldBe true
        result.text shouldBe whole("Doomed Traveler", fixture.mtgish.getValue("Doomed Traveler")).text
    }
    "current and frozen forms preserve Endless Ranks of the Dead" {
        val result = whole("Endless Ranks of the Dead", card("Endless Ranks of the Dead"))
        result.complete shouldBe true
        result.text shouldBe whole("Endless Ranks of the Dead", fixture.mtgish.getValue("Endless Ranks of the Dead")).text
    }
    "current and frozen forms preserve Geist-Honored Monk" {
        val result = whole("Geist-Honored Monk", card("Geist-Honored Monk"))
        result.complete shouldBe true
        result.text shouldBe whole("Geist-Honored Monk", fixture.mtgish.getValue("Geist-Honored Monk")).text
    }
    "current and frozen forms preserve Mausoleum Guard" {
        val result = whole("Mausoleum Guard", card("Mausoleum Guard"))
        result.complete shouldBe true
        result.text shouldBe whole("Mausoleum Guard", fixture.mtgish.getValue("Mausoleum Guard")).text
    }
    "current and frozen forms preserve Midnight Haunting" {
        val result = whole("Midnight Haunting", card("Midnight Haunting"))
        result.complete shouldBe true
        result.text shouldBe whole("Midnight Haunting", fixture.mtgish.getValue("Midnight Haunting")).text
    }
    "current and frozen forms preserve Moan of the Unhallowed" {
        val result = whole("Moan of the Unhallowed", card("Moan of the Unhallowed"))
        result.complete shouldBe true
        result.text shouldBe whole("Moan of the Unhallowed", fixture.mtgish.getValue("Moan of the Unhallowed")).text
    }
    "current and frozen forms preserve Purify the Grave" {
        val result = whole("Purify the Grave", card("Purify the Grave"))
        result.complete shouldBe true
        result.text shouldBe whole("Purify the Grave", fixture.mtgish.getValue("Purify the Grave")).text
    }
    "current and frozen forms preserve Silverchase Fox" {
        val result = whole("Silverchase Fox", card("Silverchase Fox"))
        result.complete shouldBe true
        result.text shouldBe whole("Silverchase Fox", fixture.mtgish.getValue("Silverchase Fox")).text
    }
    "current and frozen forms preserve Spider Spawning" {
        val result = whole("Spider Spawning", card("Spider Spawning"))
        result.complete shouldBe true
        result.text shouldBe whole("Spider Spawning", fixture.mtgish.getValue("Spider Spawning")).text
    }
    "current and frozen forms preserve Stitcher's Apprentice" {
        val result = whole("Stitcher's Apprentice", card("Stitcher's Apprentice"))
        result.complete shouldBe true
        result.text shouldBe whole("Stitcher's Apprentice", fixture.mtgish.getValue("Stitcher's Apprentice")).text
    }
    "current token flags preserve tapped state" {
        emit(token(spirit, JsonArray(listOf(tapped)))) shouldBe "CreateTokenEffect(power = 1, toughness = 1, colors = setOf(Color.WHITE), creatureTypes = setOf(\"Spirit\"), keywords = setOf(Keyword.FLYING), tapped = true)"
    }
    "current token flags reject unsupported entry state" {
        emit(token(spirit, JsonArray(listOf(obj("""{"_TokenFlag":"Attacking"}"""))))).shouldBeNull()
    }
    "current token flags reject non array and annotated operands" {
        emit(token(spirit, tapped)).shouldBeNull()
        emit(token(spirit, JsonArray(listOf(obj("""{"_TokenFlag":"EntersTapped","extra":true}"""))))).shouldBeNull()
    }
    "current tokens reject missing or extra operands" {
        emit(obj("""{"_Action":"CreateTokens","args":[$spirit]}""")).shouldBeNull()
        emit(obj("""{"_Action":"CreateTokens","args":[$spirit,[],[]]}""")).shouldBeNull()
    }
    "current tokens reject plural specifications rather than dropping one" {
        emit(token(JsonArray(spirit + spirit))).shouldBeNull()
        emit(token(JsonArray(emptyList()))).shouldBeNull()
    }
    "generic permanent exile preserves its bound target" {
        emit(exPermanent, "chosenPermanent") shouldBe "Effects.Exile(chosenPermanent)"
    }
    "generic graveyard exile preserves its bound target and zone" {
        emit(exGraveyard, "chosenGraveyardCard") shouldBe "Effects.Move(chosenGraveyardCard, Zone.EXILE)"
    }
    "generic exile rejects missing bindings instead of choosing self" {
        emit(exPermanent, null).shouldBeNull()
        emit(exGraveyard, null).shouldBeNull()
    }
    "generic exile rejects flags and multiple operands" {
        val a = exPermanent["args"] as JsonArray
        val operands = a[0] as JsonArray
        emit(obj("""{"_Action":"Exile","args":[$operands,[{"_ExileFlag":"ExilesWithACounter"}]]}""")).shouldBeNull()
        emit(obj("""{"_Action":"Exile","args":[${JsonArray(operands + operands)},[]]}""")).shouldBeNull()
    }
    "generic exile rejects wrong kind and reference pairings" {
        emit(obj(exPermanent.toString().replace("Ref_TargetPermanent", "ThisPermanent"))).shouldBeNull()
        emit(obj(exGraveyard.toString().replace("CardInGraveyards", "CardInHand"))).shouldBeNull()
        emit(obj(exPermanent.toString().replace("_Permanent", "_CardInGraveyards"))).shouldBeNull()
    }
    "generic exile rejects annotations and arity changes" {
        emit(obj(exPermanent.toString().replace("\"_Exilable\"", "\"extra\":true,\"_Exilable\""))).shouldBeNull()
        val a = exPermanent["args"] as JsonArray
        emit(obj("""{"_Action":"Exile","args":[${a[0]},[],[]]}""")).shouldBeNull()
    }
})
