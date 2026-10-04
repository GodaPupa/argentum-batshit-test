package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CloneEntersContinuation
import com.wingedsheep.engine.core.CloneEntersOnBattlefieldContinuation
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Fixed generic copy-entry probes; no exact card or gameplay admission. */
class EntersAsCopyExceptionsScenarioTest : FunSpec({
    val artifactException = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT))
    val specimen = card("Copy Exception Specimen") {
        manaCost = "{2}{G}"
        typeLine = "Legendary Enchantment Creature — Bear"
        power = 2
        toughness = 3
        keywords(Keyword.TRAMPLE)
        activatedAbility {
            cost = com.wingedsheep.sdk.dsl.Costs.Free
            effect = Effects.GainLife(1)
        }
    }
    val rock = card("Copy Exception Rock") {
        manaCost = "{3}"
        typeLine = "Artifact"
        activatedAbility {
            cost = com.wingedsheep.sdk.dsl.Costs.Free
            effect = Effects.GainLife(1)
        }
    }
    val copier = card("Copy Exception Carrier") {
        manaCost = "{0}"
        typeLine = "Creature — Shapeshifter"
        power = 0
        toughness = 5
        replacementEffect(EntersAsCopy(
            copyFilter = GameObjectFilter.Artifact or GameObjectFilter.Creature,
            exceptions = artifactException,
        ))
    }
    val mergedCopier = card("Copy Exception Merged Carrier") {
        manaCost = "{0}"
        typeLine = "Creature — Shapeshifter"
        power = 0
        toughness = 5
        replacementEffect(EntersAsCopy(
            additionalSubtypes = listOf("Bird"), additionalKeywords = listOf(Keyword.FLYING),
            nameOverride = "Legacy Name", powerOverride = 1, toughnessOverride = 1,
            exceptions = artifactException.copy(nameOverride = "Modern Name", powerOverride = 4, toughnessOverride = 4),
        ))
    }
    val plainCopier = card("Copy Exception Plain Carrier") {
        manaCost = "{0}"
        typeLine = "Creature — Shapeshifter"
        power = 0
        toughness = 5
        replacementEffect(EntersAsCopy())
    }
    val directLandCarrier = card("Copy Exception Land Carrier") {
        typeLine = "Land"
        replacementEffect(EntersAsCopy(
            copyFilter = GameObjectFilter.Artifact or GameObjectFilter.Creature,
            exceptions = artifactException,
        ))
    }
    val bounce = card("Copy Exception Bounce") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            val creature = target("target creature", Targets.Creature)
            effect = Effects.ReturnToHand(creature)
        }
    }

    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(specimen, rock, copier, mergedCopier, plainCopier, directLandCarrier, bounce))
        it.initMirrorMatch(Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun component(d: GameTestDriver, id: EntityId) = d.state.getEntity(id)?.get<CardComponent>().shouldNotBeNull()
    fun castCopy(d: GameTestDriver, name: String, selected: EntityId?): EntityId {
        val id = d.putCardInHand(d.player1, name)
        d.castSpell(d.player1, id).error shouldBe null
        d.bothPass().error shouldBe null
        (d.state.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.submitCardSelection(d.player1, listOfNotNull(selected)).error shouldBe null
        return id
    }
    fun directLandCopy(d: GameTestDriver, selected: EntityId?): EntityId {
        val id = d.putCardInHand(d.player1, directLandCarrier.name)
        d.playLand(d.player1, id).error shouldBe null
        (d.state.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.submitCardSelection(d.player1, listOfNotNull(selected)).error shouldBe null
        return id
    }
    fun artifactCopyOf(source: CardComponent, owner: EntityId) = source.copy(
        ownerId = owner,
        typeLine = source.typeLine.copy(cardTypes = source.typeLine.cardTypes + CardType.ARTIFACT),
    )

    test("spell entry adds artifact while retaining every copied characteristic") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val expected = artifactCopyOf(component(d, target), d.player1)
        val copy = castCopy(d, copier.name, target)
        component(d, copy) shouldBe expected
        d.state.getBattlefield(d.player1).contains(copy) shouldBe true
    }

    test("direct land entry carries the same typed exceptions through its decision") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, specimen.name)
        val expected = artifactCopyOf(component(d, target), d.player1)
        val copy = directLandCopy(d, target)
        component(d, copy) shouldBe expected
        d.state.getBattlefield(d.player1).contains(copy) shouldBe true
    }

    test("an artifact noncreature is still a legal copy and keeps its characteristics") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, rock.name)
        val expected = artifactCopyOf(component(d, target), d.player1)
        val copy = castCopy(d, copier.name, target)
        component(d, copy) shouldBe expected
        component(d, copy).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT)
    }

    test("declining the spell entry does not add artifact to the printed carrier") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, specimen.name)
        val copy = castCopy(d, copier.name, null)
        component(d, copy).name shouldBe copier.name
        component(d, copy).typeLine shouldBe copier.typeLine
        component(d, copy).baseStats shouldBe copier.creatureStats
    }

    test("declining direct land entry does not apply copy exceptions") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, specimen.name)
        val copy = directLandCopy(d, null)
        component(d, copy).name shouldBe directLandCarrier.name
        component(d, copy).typeLine shouldBe directLandCarrier.typeLine
    }

    test("a later ordinary copy inherits the added artifact as a copiable characteristic") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val first = castCopy(d, copier.name, target)
        val expected = component(d, first)
        val second = castCopy(d, plainCopier.name, first)
        component(d, second) shouldBe expected
    }

    test("leaving the battlefield restores the printed type line") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val copy = castCopy(d, copier.name, target)
        val spell = d.putCardInHand(d.player1, bounce.name)
        d.castSpell(d.player1, spell, listOf(copy)).error shouldBe null
        d.bothPass().error shouldBe null
        d.getHand(d.player1).contains(copy) shouldBe true
        component(d, copy).name shouldBe copier.name
        component(d, copy).typeLine shouldBe copier.typeLine
        component(d, copy).baseStats shouldBe copier.creatureStats
    }

    test("modern exceptions merge with legacy riders in the actual entry path") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val copy = castCopy(d, mergedCopier.name, target)
        val c = component(d, copy)
        c.name shouldBe "Modern Name"
        c.typeLine.cardTypes shouldBe setOf(CardType.CREATURE, CardType.ARTIFACT)
        c.typeLine.subtypes.contains(com.wingedsheep.sdk.core.Subtype("Bird")) shouldBe true
        c.baseKeywords.contains(Keyword.FLYING) shouldBe true
        c.baseStats shouldBe com.wingedsheep.sdk.model.CreatureStats(4, 4)
    }

    test("spell copy continuation retains exceptions across serialization") {
        val json = Json { encodeDefaults = true }
        val original = CloneEntersContinuation(EntityId.generate(), EntityId.generate(), EntityId.generate(), false,
            exceptions = artifactException)
        val encoded = json.encodeToString(CloneEntersContinuation.serializer(), original)
        json.decodeFromString(CloneEntersContinuation.serializer(), encoded) shouldBe original
        val defaults = json.encodeToString(CloneEntersContinuation.serializer(), original.copy(exceptions = CopyExceptions.None))
        json.parseToJsonElement(defaults).jsonObject.containsKey("exceptions") shouldBe false
    }

    test("direct copy continuation retains exceptions across serialization") {
        val json = Json { encodeDefaults = true }
        val original = CloneEntersOnBattlefieldContinuation(EntityId.generate(), EntityId.generate(), Zone.LIBRARY,
            exceptions = artifactException)
        val encoded = json.encodeToString(CloneEntersOnBattlefieldContinuation.serializer(), original)
        json.decodeFromString(CloneEntersOnBattlefieldContinuation.serializer(), encoded) shouldBe original
        val defaults = json.encodeToString(CloneEntersOnBattlefieldContinuation.serializer(), original.copy(exceptions = CopyExceptions.None))
        json.parseToJsonElement(defaults).jsonObject.containsKey("exceptions") shouldBe false
    }
})
