package com.wingedsheep.sdk.model

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.TypeLine
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * Color identity per CR 903.4. Used by Commander deck construction; covered here at the model
 * layer so the rule lives next to the `colorIdentity` getter and can be reused without
 * spinning up a deck-validator.
 */
class ColorIdentityTest : DescribeSpec({

    describe("color identity from mana cost") {
        it("vanilla colorless artifact has empty identity") {
            val card = CardDefinition.artifact(
                name = "Sol Ring",
                manaCost = ManaCost.parse("{1}"),
                oracleText = "{T}: Add {C}{C}.",
            )
            card.colorIdentity shouldBe emptySet()
        }

        it("mono-coloured spell picks up its mana cost colour") {
            val card = CardDefinition.instant(
                name = "Lightning Bolt",
                manaCost = ManaCost.parse("{R}"),
                oracleText = "Lightning Bolt deals 3 damage to any target.",
            )
            card.colorIdentity shouldBe setOf(Color.RED)
        }

        it("multi-coloured legendary picks up every mana-cost colour") {
            val card = CardDefinition.creature(
                name = "Yuriko, the Tiger's Shadow",
                manaCost = ManaCost.parse("{1}{U}{B}"),
                subtypes = setOf(Subtype("Human"), Subtype("Ninja")),
                power = 1,
                toughness = 3,
                supertypes = setOf(Supertype.LEGENDARY),
            )
            card.colorIdentity shouldBe setOf(Color.BLUE, Color.BLACK)
        }
    }

    describe("color identity from oracle text") {
        it("hybrid mana symbol contributes both colours") {
            val card = CardDefinition.creature(
                name = "Boros Spell",
                manaCost = ManaCost.parse("{1}"),
                subtypes = setOf(Subtype("Human")),
                power = 2,
                toughness = 2,
                oracleText = "{R/W}: Boros Spell gets +1/+0 until end of turn.",
            )
            card.colorIdentity shouldBe setOf(Color.RED, Color.WHITE)
        }

        it("Phyrexian mana symbol contributes its colour") {
            val card = CardDefinition.creature(
                name = "Test Phyrexian",
                manaCost = ManaCost.parse("{1}"),
                subtypes = setOf(Subtype("Human")),
                power = 2,
                toughness = 2,
                oracleText = "{B/P}: Test Phyrexian gains lifelink until end of turn.",
            )
            card.colorIdentity shouldBe setOf(Color.BLACK)
        }

        it("activated ability with colored cost adds that colour to identity") {
            // Classic case: a colorless artifact whose only colored symbol is in an activation
            // cost — the artifact itself isn't black, but its color identity includes black.
            val card = CardDefinition.artifact(
                name = "Coldsteel Heart",
                manaCost = ManaCost.parse("{2}"),
                oracleText = "Coldsteel Heart enters tapped.\n{T}: Add {B}.",
            )
            card.colorIdentity shouldBe setOf(Color.BLACK)
        }

        it("multiple distinct colored symbols accumulate") {
            val card = CardDefinition.enchantment(
                name = "Test Enchantment",
                manaCost = ManaCost.parse("{2}{W}"),
                oracleText = "{W}, {T}: Draw a card.\n{U}{B}: Counter target spell.",
            )
            card.colorIdentity shouldBe setOf(Color.WHITE, Color.BLUE, Color.BLACK)
        }

        it("generic / X / colourless symbols do not contribute identity") {
            val card = CardDefinition.sorcery(
                name = "Test Sorcery",
                manaCost = ManaCost.parse("{X}"),
                oracleText = "Test Sorcery deals X damage divided as you choose.\nGeneric reminder text mentioning {2} and {C}.",
            )
            card.colorIdentity shouldBe emptySet()
        }
    }

    describe("color identity from basic land subtypes") {
        it("basic Mountain has red identity from its Mountain subtype") {
            val card = CardDefinition.basicLand("Mountain", Subtype.MOUNTAIN)
            card.colorIdentity shouldBe setOf(Color.RED)
        }

        it("basic Forest has green identity") {
            val card = CardDefinition.basicLand("Forest", Subtype.FOREST)
            card.colorIdentity shouldBe setOf(Color.GREEN)
        }

        it("dual land with two basic land subtypes contributes both colours") {
            // Tundra-shaped: Land — Plains Island. No mana cost, no rules text — identity comes
            // entirely from the basic-land-subtype rule.
            val card = CardDefinition(
                name = "Tundra",
                manaCost = ManaCost.ZERO,
                typeLine = TypeLine(
                    cardTypes = setOf(CardType.LAND),
                    subtypes = setOf(Subtype.PLAINS, Subtype.ISLAND),
                ),
            )
            card.colorIdentity shouldBe setOf(Color.WHITE, Color.BLUE)
        }

        it("non-basic-land subtypes (e.g. Forest creature subtypes) — only land-subtype names match by string") {
            // Sanity: the basic-subtype map is keyed by the Subtype value, so a creature card
            // that carries a coincidentally-named subtype would also match. That's correct
            // because Subtype is a string-valued type; in real card data only land cards carry
            // basic-land subtypes. Documented here so the behaviour isn't surprising.
            val card = CardDefinition.creature(
                name = "Forest Spirit",  // hypothetical creature with Forest subtype
                manaCost = ManaCost.parse("{2}{G}"),
                subtypes = setOf(Subtype.FOREST, Subtype("Spirit")),
                power = 2,
                toughness = 2,
            )
            // Mana cost already gives green; Forest subtype would too. Result is just green.
            card.colorIdentity shouldBe setOf(Color.GREEN)
        }
    }

    describe("identity combines all sources") {
        it("Wastes-style colourless basic still has empty identity") {
            // No mana cost, no relevant subtype, no colored symbols in text → empty.
            val card = CardDefinition(
                name = "Wastes",
                manaCost = ManaCost.ZERO,
                typeLine = TypeLine(
                    supertypes = setOf(Supertype.BASIC),
                    cardTypes = setOf(CardType.LAND),
                ),
                oracleText = "{T}: Add {C}.",
            )
            card.colorIdentity shouldBe emptySet()
        }

        it("card with cost and oracle in different colours unions them") {
            val card = CardDefinition.creature(
                name = "Test Hybrid",
                manaCost = ManaCost.parse("{G}"),
                subtypes = setOf(Subtype("Elf")),
                power = 1,
                toughness = 1,
                oracleText = "{U}: Test Hybrid gains flying until end of turn.",
            )
            card.colorIdentity shouldBe setOf(Color.GREEN, Color.BLUE)
        }
    }
})
