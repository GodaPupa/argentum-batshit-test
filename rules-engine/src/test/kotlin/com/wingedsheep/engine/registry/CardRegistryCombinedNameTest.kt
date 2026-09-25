package com.wingedsheep.engine.registry

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardFace
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.ScryfallMetadata
import com.wingedsheep.sdk.scripting.CardNamePool
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** Exact deck-export aliases preserve the registered front, face metadata and naming pools. */
class CardRegistryCombinedNameTest : FunSpec({

    fun creature(name: String, power: Int = 2, collector: String? = null) = CardDefinition.creature(
        name = name,
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype.BEAR),
        power = power,
        toughness = power,
        metadata = ScryfallMetadata(collectorNumber = collector),
    )

    fun adventureFace(name: String) = CardFace(
        name = name,
        manaCost = ManaCost.parse("{G}"),
        typeLine = TypeLine.parse("Instant — Adventure"),
    )

    fun adventurer(front: String = "Test Adventurer", adventure: String = "Test Journey") =
        creature(front).copy(layout = CardLayout.ADVENTURE, cardFaces = listOf(adventureFace(adventure)))

    fun transforming(front: String = "Test Day Bear", back: String = "Test Night Bear") =
        CardDefinition.doubleFacedCreature(creature(front), creature(back, power = 4))

    test("a combined DFC name resolves the front and preserves ordinary front and back lookups") {
        val front = transforming()
        val registry = CardRegistry().apply { register(front) }

        (registry.requireCard("Test Day Bear // Test Night Bear") === front) shouldBe true
        (registry.requireCard("Test Day Bear") === front) shouldBe true
        (registry.requireCard("Test Night Bear") === front.backFace) shouldBe true
        (registry.getFrontFace("Test Night Bear") === front) shouldBe true
        registry.getFrontFace("Test Day Bear // Test Night Bear").shouldBeNull()
    }

    test("a combined Adventure name resolves the whole card without registering the spell as a card") {
        val front = adventurer()
        val registry = CardRegistry().apply { register(front) }

        (registry.requireCard("Test Adventurer // Test Journey") === front) shouldBe true
        (registry.requireCard("Test Adventurer") === front) shouldBe true
        registry.requireCard("Test Adventurer // Test Journey").cardFaces shouldBe front.cardFaces
        registry.getCard("Test Journey").shouldBeNull()
    }

    test("wrong reversed incomplete and additional faces never fall back to a registered prefix") {
        val registry = CardRegistry().apply { register(listOf(transforming(), adventurer(), creature("Wrong"))) }
        listOf(
            "Test Day Bear // Wrong",
            "Test Adventurer // Wrong",
            "Test Night Bear // Test Day Bear",
            "Test Journey // Test Adventurer",
            "Test Day Bear // Test Night Bear // Wrong",
            "Test Adventurer // Test Journey // Wrong",
            "Test Day Bear // ",
            "Test Day Bear//Test Night Bear",
            "test day bear // Test Night Bear",
            "Test Day Bear // Test Night Bear ",
            "Wrong // Test Night Bear",
        ).forEach { name ->
            registry.getCard(name).shouldBeNull()
            registry.hasCard(name) shouldBe false
        }
    }

    test("an alias requires a real back face or exactly one Adventure face") {
        val oneFace = adventureFace("Test Journey")
        val registry = CardRegistry().apply {
            register(listOf(
                creature("Ordinary").copy(cardFaces = listOf(oneFace)),
                creature("Omen").copy(layout = CardLayout.OMEN, cardFaces = listOf(oneFace)),
                creature("Empty Adventure").copy(layout = CardLayout.ADVENTURE),
                creature("Multiple Adventures").copy(
                    layout = CardLayout.ADVENTURE,
                    cardFaces = listOf(oneFace, adventureFace("Other Journey")),
                ),
                transforming(front = "Already // Combined"),
            ))
        }
        listOf("Ordinary", "Omen", "Empty Adventure", "Multiple Adventures").forEach { front ->
            registry.getCard("$front // Test Journey").shouldBeNull()
        }
        registry.getCard("Multiple Adventures // Other Journey").shouldBeNull()
        registry.getCard("Already // Combined // Test Night Bear").shouldBeNull()
    }

    test("explicit card names take precedence over aliases in either registration order") {
        val front = transforming()
        val exact = creature("Test Day Bear // Test Night Bear", power = 7)
        listOf(listOf(front, exact), listOf(exact, front)).forEach { order ->
            val registry = CardRegistry().apply { register(order) }
            (registry.requireCard(exact.name) === exact) shouldBe true
            (registry.requireCard(front.name) === front) shouldBe true
            (registry.requireCard(front.backFace!!.name) === front.backFace) shouldBe true
        }
    }

    test("explicit back-face registrations and unrelated aliases are independent of registration order") {
        val front = transforming()
        val explicitBack = front.backFace!!.copy(oracleText = "Pinned back-face definition")
        val adventure = adventurer()
        listOf(
            listOf(front, explicitBack, adventure),
            listOf(adventure, explicitBack, front),
            listOf(explicitBack, adventure, front),
        ).forEach { order ->
            val registry = CardRegistry().apply { register(order) }
            (registry.requireCard("Test Day Bear // Test Night Bear") === front) shouldBe true
            (registry.requireCard("Test Adventurer // Test Journey") === adventure) shouldBe true
            (registry.requireCard("Test Night Bear") === explicitBack) shouldBe true
            (registry.getFrontFace("Test Night Bear") === front) shouldBe true
        }
    }

    test("replacing a front removes its former alias and follows ordinary last registration semantics") {
        val registry = CardRegistry().apply { register(adventurer()) }
        val replacement = adventurer(adventure = "Changed Journey")
        registry.register(replacement)

        registry.getCard("Test Adventurer // Test Journey").shouldBeNull()
        (registry.requireCard("Test Adventurer // Changed Journey") === replacement) shouldBe true

        val ordinary = creature("Test Adventurer")
        registry.register(ordinary)
        registry.getCard("Test Adventurer // Changed Journey").shouldBeNull()
        (registry.requireCard("Test Adventurer") === ordinary) shouldBe true
    }

    test("combined lookup retains the front's variant while existing collector lookups keep both printings") {
        val first = transforming().copy(metadata = ScryfallMetadata(collectorNumber = "1"), setCode = "TST")
        val second = first.copy(metadata = ScryfallMetadata(collectorNumber = "2"))
        val registry = CardRegistry().apply { register(listOf(first, second)) }

        (registry.requireCard("Test Day Bear // Test Night Bear") === second) shouldBe true
        (registry.requireCard("Test Day Bear#TST-1") === first) shouldBe true
        (registry.requireCard("Test Day Bear#TST-2") === second) shouldBe true
        registry.getCardsByName("Test Day Bear") shouldContainExactlyInAnyOrder listOf(first, second)
    }

    test("an overlay alias resolves its pinned front and never mutates the parent") {
        val liveFront = adventurer()
        val live = CardRegistry().apply { register(listOf(liveFront, transforming())) }
        val pinnedFront = liveFront.copy(oracleText = "Pinned primary definition")
        val overlay = CardRegistry(parent = live).apply { register(pinnedFront) }

        (overlay.requireCard("Test Adventurer // Test Journey") === pinnedFront) shouldBe true
        (live.requireCard("Test Adventurer // Test Journey") === liveFront) shouldBe true
        (overlay.requireCard("Test Day Bear // Test Night Bear") === live.requireCard("Test Day Bear")) shouldBe true
    }

    test("an overlay does not inherit aliases whose secondary face is absent from its pinned front") {
        val live = CardRegistry().apply { register(adventurer()) }
        val changed = adventurer(adventure = "Changed Journey")
        val overlay = CardRegistry(parent = live).apply { register(changed) }

        overlay.getCard("Test Adventurer // Test Journey").shouldBeNull()
        (overlay.requireCard("Test Adventurer // Changed Journey") === changed) shouldBe true
        live.hasCard("Test Adventurer // Test Journey") shouldBe true
        live.getCard("Test Adventurer // Changed Journey").shouldBeNull()

        overlay.register(creature("Test Adventurer"))
        overlay.getCard("Test Adventurer // Test Journey").shouldBeNull()
        overlay.getCard("Test Adventurer // Changed Journey").shouldBeNull()
    }

    test("overlay precedence still applies to collisions between actual names and aliases") {
        val front = transforming()
        val exact = creature("Test Day Bear // Test Night Bear", power = 7)
        val actualParent = CardRegistry().apply { register(exact) }
        val aliasChild = CardRegistry(parent = actualParent).apply { register(front) }
        (aliasChild.requireCard(exact.name) === front) shouldBe true

        val aliasParent = CardRegistry().apply { register(front) }
        val actualChild = CardRegistry(parent = aliasParent).apply { register(exact) }
        (actualChild.requireCard(exact.name) === exact) shouldBe true
    }

    test("lookup aliases do not create extra identities or enter card-name choice pools") {
        val registry = CardRegistry().apply { register(listOf(transforming(), adventurer())) }
        val actualNames = setOf("Test Day Bear", "Test Night Bear", "Test Adventurer")

        registry.size shouldBe 3
        registry.allCardNames() shouldBe actualNames
        registry.cardNamesIn(CardNamePool.ANY) shouldBe actualNames
        registry.cardNamesIn(CardNamePool.NONLAND) shouldBe actualNames
        registry.cardNamesIn(CardNamePool.LAND) shouldBe emptySet()
    }

    test("clearing a registry removes its aliases and an overlay then reveals its parent") {
        val front = adventurer()
        val live = CardRegistry().apply { register(front) }
        val overlay = CardRegistry(parent = live).apply { register(adventurer(adventure = "Changed Journey")) }
        overlay.clear()

        overlay.getCard("Test Adventurer // Changed Journey").shouldBeNull()
        (overlay.requireCard("Test Adventurer // Test Journey") === front) shouldBe true
        live.clear()
        overlay.getCard("Test Adventurer // Test Journey").shouldBeNull()
        live.size shouldBe 0
    }
})
