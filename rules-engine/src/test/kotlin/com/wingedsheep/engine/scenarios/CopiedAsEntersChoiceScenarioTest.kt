package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Copied choices are made afresh before entry (CR 707.5, 707.6 and 614.12a). */
class CopiedAsEntersChoiceScenarioTest : FunSpec({
    val choiceBody = card("Copied Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        replacementEffect(EntersWithCounters(CounterTypeFilter.PlusOnePlusOne, 2, selfOnly = true))
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(3) }
    }
    val twoChoices = card("Copied Two Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE, allowedCreatureTypes = listOf("Elf", "Goblin")))
    }
    fun carrier(graveyard: Boolean) = card(if (graveyard) "Copied Choice Grave Carrier" else "Copied Choice Carrier") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy(
            copyFromZone = if (graveyard) Zone.GRAVEYARD else Zone.BATTLEFIELD,
            exileCopiedCard = graveyard,
            exceptions = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT)),
        ))
    }
    val ownChoiceCarrier = card("Declined Copy Choice Carrier") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun roundTrip(d: GameTestDriver) {
        val encoded = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), encoded)
        json.encodeToString(GameState.serializer(), restored) shouldBe encoded
        d.replaceState(restored)
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(choiceBody, twoChoices, carrier(false), carrier(true), ownChoiceCarrier))
        initMirrorMatch(Deck.of("Island" to 40), startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun color(d: GameTestDriver, value: Color) {
        val question = (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
        d.submitDecision(question.playerId, ColorChosenResponse(question.id, value)).error shouldBe null
    }
    fun cast(d: GameTestDriver, name: String): EntityId {
        val id = d.putCardInHand(d.player1, name)
        d.castSpell(d.player1, id).error shouldBe null
        d.bothPass().error shouldBe null
        return id
    }

    for (graveyard in listOf(false, true)) {
        for (restore in listOf(false, true)) {
            test("copied color is chosen before one entry and one trigger; graveyard=$graveyard, restore=$restore") {
                val d = driver()
                val target = if (graveyard) d.putCardInGraveyard(d.player2, choiceBody.name)
                    else d.putCreatureOnBattlefield(d.player2, choiceBody.name)
                d.replaceState(d.state.updateEntity(target) { it.withCastChoice(ChoiceSlot.COLOR, ChoiceValue.ColorChoice(Color.RED)) })
                val originalTarget = d.state.objectRef(target)!!
                val id = cast(d, carrier(graveyard).name)
                val stackObject = d.state.objectRef(id)!!
                if (restore) roundTrip(d)
                d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
                (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
                d.state.getBattlefield().contains(id) shouldBe false
                d.state.objectRef(id) shouldBe stackObject
                d.state.getEntity(id)!!.get<BattlefieldEntryTimestampComponent>() shouldBe null
                d.state.getEntity(id)!!.chosenColor() shouldBe null
                (d.state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                d.state.objectRef(target) shouldBe originalTarget
                d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == id && it.toZone == Zone.BATTLEFIELD } shouldBe 0
                if (restore) roundTrip(d)
                color(d, Color.BLUE)
                d.pendingDecision shouldBe null
                d.state.continuationStack shouldBe emptyList()
                d.state.getBattlefield(d.player1).contains(id) shouldBe true
                d.state.getEntity(id)!!.chosenColor() shouldBe Color.BLUE
                d.state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
                d.state.getEntity(id)!!.get<CardComponent>()!!.typeLine.cardTypes shouldBe setOf(CardType.CREATURE, CardType.ARTIFACT)
                val entered = d.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
                entered.oldObject shouldBe stackObject
                entered.newObject shouldBe d.state.objectRef(id)
                entered.copyOfOriginalName shouldBe carrier(graveyard).name
                d.events.filterIsInstance<ResolvedEvent>().count { it.entityId == id } shouldBe 1
                if (graveyard) {
                    d.state.getGraveyard(d.player2).contains(target) shouldBe false
                    d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == target && it.toZone == Zone.EXILE } shouldBe 1
                } else {
                    d.state.objectRef(target) shouldBe originalTarget
                    d.state.getEntity(target)!!.chosenColor() shouldBe Color.RED
                }
                d.stackSize shouldBe 1
                val life = d.getLifeTotal(d.player1)
                d.bothPass().error shouldBe null
                d.getLifeTotal(d.player1) shouldBe life + 3
                d.stackSize shouldBe 0
            }
        }
    }

    test("copied chained choices retain pending entry through both serialized decisions") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, twoChoices.name)
        val id = cast(d, carrier(false).name)
        val stackObject = d.state.objectRef(id)!!
        d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
        roundTrip(d)
        color(d, Color.GREEN)
        (d.pendingDecision as? ChooseOptionDecision).shouldNotBeNull()
        d.state.objectRef(id) shouldBe stackObject
        d.state.getBattlefield().contains(id) shouldBe false
        roundTrip(d)
        val question = (d.pendingDecision as? ChooseOptionDecision).shouldNotBeNull()
        question.options shouldBe listOf("Elf", "Goblin")
        d.submitDecision(question.playerId, OptionChosenResponse(question.id, 1)).error shouldBe null
        d.state.getEntity(id)!!.chosenColor() shouldBe Color.GREEN
        d.state.getEntity(id)!!.chosenCreatureType() shouldBe "Goblin"
        d.state.getBattlefield(d.player1).contains(id) shouldBe true
        d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == id && it.toZone == Zone.BATTLEFIELD } shouldBe 1
        d.events.filterIsInstance<ResolvedEvent>().count { it.entityId == id } shouldBe 1
    }

    test("declining a copy still makes the entering card's own choice") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, choiceBody.name)
        val id = cast(d, ownChoiceCarrier.name)
        d.submitCardSelection(d.player1, emptyList()).error shouldBe null
        d.state.getBattlefield().contains(id) shouldBe false
        color(d, Color.WHITE)
        d.state.getEntity(id)!!.chosenColor() shouldBe Color.WHITE
        d.state.getEntity(id)!!.get<CardComponent>()!!.name shouldBe ownChoiceCarrier.name
        d.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }.copyOfOriginalName shouldBe null
    }

    test("serialized graveyard copy rider cannot exile a different zone visit") {
        val d = driver()
        val target = d.putCardInGraveyard(d.player2, choiceBody.name)
        val id = cast(d, carrier(true).name)
        d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
        (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
        d.replaceState(d.state
            .moveToZone(target, ZoneKey(d.player2, Zone.GRAVEYARD), ZoneKey(d.player2, Zone.EXILE))
            .moveToZone(target, ZoneKey(d.player2, Zone.EXILE), ZoneKey(d.player2, Zone.GRAVEYARD)))
        val laterVisit = d.state.objectRef(target)
        roundTrip(d)
        color(d, Color.BLACK)
        d.state.objectRef(target) shouldBe laterVisit
        d.state.getGraveyard(d.player2).contains(target) shouldBe true
        d.state.getBattlefield(d.player1).contains(id) shouldBe true
        d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == target && it.toZone == Zone.EXILE } shouldBe 0
    }
})
