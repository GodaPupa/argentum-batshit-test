package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Fixtures
import com.wingedsheep.tooling.coverage.Registry
import com.wingedsheep.tooling.coverage.J
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject

class HumanSubtypeCalibrationRepairTest : StringSpec({
    val current = J.parseToJsonElement(checkNotNull(javaClass.getResource("/human-filter-repair/current-ir.json")).readText()) as JsonObject
    val fixture = Fixtures.load("ISD")
    val effects = Registry.loadEffectSerialNames()
    val keywords = Registry.loadKeywords()
    fun whole(name: String, ir: JsonObject) = Emitter.renderCard(ir, fixture.scryfall[name], effects, keywords)
    fun node(text: String) = J.parseToJsonElement(text)
    val bare = """{"_Permanents":"IsCreatureType","args":"Human"}"""
    val entry = """{"_Permanents":"And","args":[{"_Permanents":"Other","args":{"_Permanent":"ThisPermanent"}},{"_Permanents":"And","args":[{"_Permanents":"IsCreatureType","args":"Human"},{"_Permanents":"ControlledByAPlayer","args":{"_Players":"SinglePlayer","args":{"_Player":"You"}}}]}]}"""
    "Champion current and fixture preserve permanent Human entry" {
        val ir = current["Champion of the Parish"] as JsonObject
        ir shouldBe fixture.mtgish.getValue("Champion of the Parish")
        val output = whole("Champion of the Parish", ir)
        output.complete shouldBe true
        output.text.contains("GameObjectFilter.Permanent.withSubtype(Subtype.HUMAN).youControl()") shouldBe true
        output.text.contains("binding = TriggerBinding.OTHER") shouldBe true
        output.text shouldBe whole("Champion of the Parish", fixture.mtgish.getValue("Champion of the Parish")).text
    }
    "Noble current and fixture preserve permanent Human blockers" {
        val ir = current["Stromkirk Noble"] as JsonObject
        ir shouldBe fixture.mtgish.getValue("Stromkirk Noble")
        val output = whole("Stromkirk Noble", ir)
        output.complete shouldBe true
        output.text.contains("CantBeBlockedBy(blockerFilter = GameObjectFilter.Permanent.withSubtype(Subtype.HUMAN))") shouldBe true
        output.text.contains("Triggers.DealsCombatDamageToPlayer") shouldBe true
        output.text shouldBe whole("Stromkirk Noble", fixture.mtgish.getValue("Stromkirk Noble")).text
    }
    "entry shape preserves permanent subtype and controller" {
        humanEntryFilter(node(entry)) shouldBe "GameObjectFilter.Permanent.withSubtype(Subtype.HUMAN).youControl()"
    }
    "blocker shape preserves permanent subtype" {
        humanBlockerFilter(node(bare)) shouldBe "GameObjectFilter.Permanent.withSubtype(Subtype.HUMAN)"
    }
    "explicit Creature restrictions are not widened" {
        humanBlockerFilter(node("""{"_Permanents":"And","args":[$bare,{"_Permanents":"IsCardtype","args":"Creature"}]}""")) shouldBe null
        humanEntryFilter(node(entry.replace(bare, """{"_Permanents":"And","args":[$bare,{"_Permanents":"IsCardtype","args":"Creature"}]}"""))) shouldBe null
    }
    "other subtypes retain existing emitter paths" {
        humanBlockerFilter(node(bare.replace("Human", "Goblin"))) shouldBe null
        humanEntryFilter(node(entry.replace("Human", "Goblin"))) shouldBe null
    }
    "controller and self restrictions are not dropped" {
        humanEntryFilter(node(entry.replace("You", "Opponent"))) shouldBe null
        humanEntryFilter(node(entry.replace("ThisPermanent", "HostPermanent"))) shouldBe null
    }
    "annotations and unsupported shapes do not enter bounded repair" {
        humanBlockerFilter(node(bare.dropLast(1) + ""","extra":true}""")) shouldBe null
        humanEntryFilter(node(entry.dropLast(1) + ""","extra":true}""")) shouldBe null
        humanBlockerFilter(null) shouldBe null
        humanEntryFilter(node(bare)) shouldBe null
    }
})
