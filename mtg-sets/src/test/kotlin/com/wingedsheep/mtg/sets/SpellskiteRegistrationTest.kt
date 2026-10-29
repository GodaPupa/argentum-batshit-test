package com.wingedsheep.mtg.sets

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*

/** Production discovery and card data, independent of the scenario registry. */
class SpellskiteRegistrationTest : FunSpec({
    fun actual() = MtgSetCatalog.all.flatMap { it.cards }.singleOrNull { it.name == "Spellskite" }
    val expected = card("Spellskite") {
        manaCost = "{2}"; typeLine = "Artifact Creature — Phyrexian Horror"; power = 0; toughness = 4
        activatedAbility {
            cost = Costs.Mana("{U/P}")
            val selectedStack = target("spell or ability", Targets.SpellOrAbility)
            effect = Effects.ChangeTargetTo(stackObject = selectedStack, recipient = EffectTarget.Self)
        }
    }
    fun script(card: CardDefinition?): JsonElement? = card?.let {
        Json.parseToJsonElement(CardExporter.exportToJson(it).replace(Regex("ability_\\d+"), "ability_1")).jsonObject["script"]
    }
    test("SR01 unique canonical discovery in NPH") {
        MtgSetCatalog.all.flatMap { set -> set.cards.filter { it.name == "Spellskite" }.map { set.code } } shouldBe listOf("NPH")
    }
    test("SR02 mechanics equal accepted test-local definition") {
        script(actual()) shouldBe script(expected)
    }
    test("SR03 Phyrexian activation payment") {
        actual()?.activatedAbilities?.singleOrNull()?.cost shouldBe Costs.Mana("{U/P}")
    }
    test("SR04 spell or ability target requirement") {
        actual()?.activatedAbilities?.singleOrNull()?.targetRequirements shouldBe expected.activatedAbilities.single().targetRequirements
    }
    test("SR05 fixed self recipient") {
        actual()?.activatedAbilities?.singleOrNull()?.effect shouldBe expected.activatedAbilities.single().effect
        (actual()?.activatedAbilities?.singleOrNull()?.effect as? com.wingedsheep.sdk.scripting.effects.ChangeTargetToEffect)?.recipient shouldBe EffectTarget.Self
    }
    test("SR06 canonical metadata and characteristics") {
        val a = actual()
        a?.manaCost shouldBe expected.manaCost
        a?.typeLine shouldBe expected.typeLine
        a?.creatureStats shouldBe expected.creatureStats
        a?.colors shouldBe emptySet<Color>()
        a?.colorIdentity shouldBe setOf(Color.BLUE)
        a?.oracleText shouldBe "{U/P}: Change a target of target spell or ability to this creature. ({U/P} can be paid with either {U} or 2 life.)"
        a?.metadata shouldBe ScryfallMetadata(
            collectorNumber = "159", rarity = Rarity.RARE, artist = "Chippy",
            flavorText = "\"Let's show Vorinclex that progress doesn't always need teeth or claws.\"\n—Malcator, Executor of Synthesis", imageUri = "https://cards.scryfall.io/normal/front/1/a/1a84bada-ed6a-4e97-8a0c-05b7cb32d66f.jpg?1783941290",
            rulings = listOf(
                Ruling("2020-08-07", "You can activate Spellskite's ability even if Spellskite isn't a legal target for the target spell or ability—or even if that spell or ability has no targets. In this case, no targets are changed."),
                Ruling("2020-08-07", "If Spellskite leaves the battlefield before its ability resolves or otherwise becomes an illegal target for the target spell or ability before its ability resolves, no targets are changed."),
                Ruling("2020-08-07", "If changing one target of a spell or ability to Spellskite would make other targets of that spell or ability illegal, that target can't be changed to Spellskite."),
                Ruling("2020-08-07", "If the spell or ability has multiple instances of the word “target,” you choose which one target you're changing to Spellskite as Spellskite's ability resolves."),
                Ruling("2020-08-07", "If a spell or ability has multiple targets but doesn't use the word “target” multiple times, such as the ability of Deepglow Skate, you can only change one of the targets to Spellskite."),
                Ruling("2020-08-07", "If a spell or ability has a variable number of targets, you can't change the number of targets.")
            )
        )
    }
    test("SR07 CLB873 reprint discovered with exact metadata") {
        MtgSetCatalog.all.flatMap { it.printings }.filter { it.name == "Spellskite" } shouldBe listOf(
            Printing(
                oracleId = "e0aa6ce0-ca31-433b-ac6c-32b8675cdb71",
                name = "Spellskite",
                setCode = "CLB",
                collectorNumber = "873",
                scryfallId = "72204934-f5aa-4559-8f7e-7b0b223580d0",
                artist = "Chippy",
                imageUri = "https://cards.scryfall.io/normal/front/7/2/72204934-f5aa-4559-8f7e-7b0b223580d0.jpg?1783922383",
                releaseDate = "2022-06-10",
                rarity = Rarity.RARE,
            )
        )
    }
    test("SR08 existing discovery control") {
        MtgSetCatalog.requireByCode("NPH").cards.count { it.name == "Phyrexian Metamorph" } shouldBe 1
        MtgSetCatalog.requireByCode("CLB").cards.isNotEmpty() shouldBe true
    }
})
