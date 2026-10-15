package com.wingedsheep.engine.registry

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardFace
import com.wingedsheep.sdk.model.CardLayout
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class CardRegistryCompoundAliasTest : FunSpec({

    fun bear(name: String, power: Int) = CardDefinition.creature(
        name = name,
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype.BEAR),
        power = power,
        toughness = power,
    )

    test("transforming double-faced card resolves front-back compound identity without inflating canonical names") {
        val front = bear("Day Bear", 2)
        val back = bear("Night Bear", 4)
        val card = CardDefinition.doubleFacedCreature(front, back)
        val registry = CardRegistry().apply { register(card) }

        registry.getCard("Day Bear") shouldBe card
        registry.getCard("Night Bear") shouldBe back
        registry.getCard("Day Bear // Night Bear") shouldBe card
        registry.getFrontFace("Night Bear") shouldBe card
        registry.allCardNames() shouldContainExactlyInAnyOrder listOf("Day Bear", "Night Bear")
        registry.size shouldBe 2
    }

    test("adventure card resolves creature-adventure compound identity while adventure face stays noncanonical") {
        val card = bear("Questing Bear", 3).copy(
            layout = CardLayout.ADVENTURE,
            cardFaces = listOf(
                CardFace(
                    name = "Scout Ahead",
                    manaCost = ManaCost.parse("{1}{G}"),
                    typeLine = TypeLine.sorcery(),
                )
            ),
        )
        val registry = CardRegistry().apply { register(card) }

        registry.getCard("Questing Bear") shouldBe card
        registry.getCard("Questing Bear // Scout Ahead") shouldBe card
        registry.getCard("Scout Ahead").shouldBeNull()
        registry.allCardNames() shouldContainExactlyInAnyOrder listOf("Questing Bear")
        registry.size shouldBe 1
    }

    test("local canonical name outranks an earlier compound alias with identical text") {
        val multi = CardDefinition.doubleFacedCreature(bear("Day Bear", 2), bear("Night Bear", 4))
        val canonical = bear("Day Bear // Night Bear", 7)
        val registry = CardRegistry().apply {
            register(multi)
            register(canonical)
        }

        registry.getCard("Day Bear // Night Bear") shouldBe canonical
    }

    test("compound aliases fall through overlays without entering card-name choice pools") {
        val card = CardDefinition.doubleFacedCreature(bear("Day Bear", 2), bear("Night Bear", 4))
        val live = CardRegistry().apply { register(card) }
        val overlay = CardRegistry(parent = live).apply { register(bear("Pin Bear", 1)) }

        overlay.getCard("Day Bear // Night Bear") shouldBe card
        overlay.allCardNames() shouldContainExactlyInAnyOrder listOf("Day Bear", "Night Bear", "Pin Bear")
        overlay.size shouldBe 3
    }
})
