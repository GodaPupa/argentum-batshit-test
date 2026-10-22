package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CopyExceptions
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Direct entry choices precede publication; this is component evidence only. */
class PendingCopyEntryScenarioTest : FunSpec({
    val body = card("Pending Entry Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE, allowedCreatureTypes = listOf("Elf", "Goblin")))
        replacementEffect(EntersWithCounters(CounterTypeFilter.PlusOnePlusOne, 2, selfOnly = true))
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(3) }
    }
    fun carrier(graveyard: Boolean = false, ownChoice: Boolean = false) = card(
        if (ownChoice) "Pending Entry Own Choice" else if (graveyard) "Pending Entry Grave Copy" else "Pending Entry Copy"
    ) {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy(
            copyFromZone = if (graveyard) Zone.GRAVEYARD else Zone.BATTLEFIELD,
            exileCopiedCard = graveyard,
            exceptions = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT)),
        ))
        if (ownChoice) replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    fun libraryEntry(count: Int) = card("Pending Library Entry $count") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(count)))
            val moved = moveTracked(top, CardDestination.ToZone(Zone.BATTLEFIELD))
            ifNotEmpty(moved, minSize = count) { run(Effects.GainLife(7)) }
        } }
    }
    val graveEntry = card("Pending Graveyard Entry") {
        manaCost = "{0}"; typeLine = "Instant"
        spell {
            val creature = target("target creature card", Targets.CreatureCardInGraveyard)
            effect = Effects.Pipeline {
                run(Effects.PutOntoBattlefieldFromGraveyard(creature, underYourControl = true))
                run(Effects.GainLife(7))
            }
        }
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val state = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), state) shouldBe raw
        d.replaceState(state)
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(body, carrier(), carrier(true), carrier(ownChoice = true), libraryEntry(1), libraryEntry(2), graveEntry))
        initMirrorMatch(Deck.of("Island" to 40), startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun resolve(d: GameTestDriver, name: String, targets: List<EntityId> = emptyList()): EntityId {
        val spell = d.putCardInHand(d.player1, name)
        val chosenTargets = targets.map { id ->
            val zone = d.state.logicalZone(id).shouldNotBeNull()
            zone.zoneType shouldBe Zone.GRAVEYARD
            ChosenTarget.Card(id, zone.ownerId, zone.zoneType)
        }
        d.castSpellWithTargets(d.player1, spell, chosenTargets).error shouldBe null
        d.bothPass().error shouldBe null
        return spell
    }
    fun unpublished(d: GameTestDriver, ids: List<EntityId>) {
        for (id in ids) {
            d.state.getBattlefield().contains(id) shouldBe false
            d.state.getEntity(id)!!.get<BattlefieldEntryTimestampComponent>() shouldBe null
            (d.state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == id && it.toZone == Zone.BATTLEFIELD } shouldBe 0
        }
        d.getLifeTotal(d.player1) shouldBe 20
    }
    fun color(d: GameTestDriver, value: Color) {
        val question = (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
        d.submitDecision(question.playerId, ColorChosenResponse(question.id, value)).error shouldBe null
    }
    fun creatureType(d: GameTestDriver) {
        val question = (d.pendingDecision as? ChooseOptionDecision).shouldNotBeNull()
        question.options shouldBe listOf("Elf", "Goblin")
        d.submitDecision(question.playerId, OptionChosenResponse(question.id, 1)).error shouldBe null
    }

    for (fromGraveyard in listOf(false, true)) {
        for (serialized in listOf(false, true)) {
            test("direct copied chained choices precede publication; graveyard=$fromGraveyard, serialized=$serialized") {
                val d = driver()
                val target = d.putCreatureOnBattlefield(d.player2, body.name)
                d.replaceState(d.state.updateEntity(target) { it.withCastChoice(ChoiceSlot.COLOR, ChoiceValue.ColorChoice(Color.RED)) })
                val id = if (fromGraveyard) d.putCardInGraveyard(d.player2, carrier().name)
                    else d.putCardOnTopOfLibrary(d.player1, carrier().name)
                val oldObject = d.state.objectRef(id)
                val originalCard = d.state.getEntity(id)!!.get<CardComponent>()
                val spell = if (fromGraveyard) resolve(d, graveEntry.name, listOf(id)) else resolve(d, libraryEntry(1).name)
                val copyQuestion = (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
                copyQuestion.options.contains(target) shouldBe true
                unpublished(d, listOf(id))
                if (serialized) restore(d)
                d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
                (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
                unpublished(d, listOf(id))
                d.state.objectRef(id) shouldBe oldObject
                d.state.getEntity(id)!!.get<CardComponent>() shouldBe originalCard
                if (serialized) restore(d)
                color(d, Color.BLUE)
                unpublished(d, listOf(id))
                d.state.objectRef(id) shouldBe oldObject
                if (serialized) restore(d)
                creatureType(d)
                d.pendingDecision shouldBe null
                d.state.continuationStack shouldBe emptyList()
                d.state.getBattlefield(d.player1).contains(id) shouldBe true
                d.state.getEntity(id)!!.chosenColor() shouldBe Color.BLUE
                d.state.getEntity(id)!!.chosenCreatureType() shouldBe "Goblin"
                d.state.getEntity(id)!!.get<CardComponent>()!!.typeLine.cardTypes shouldBe setOf(CardType.CREATURE, CardType.ARTIFACT)
                d.state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
                val event = d.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
                event.oldObject shouldBe oldObject
                event.newObject shouldBe d.state.objectRef(id)
                event.copyOfOriginalName shouldBe carrier().name
                d.events.filterIsInstance<ResolvedEvent>().count { it.entityId == spell } shouldBe 1
                d.getLifeTotal(d.player1) shouldBe 27
                d.stackSize shouldBe 1
                d.bothPass().error shouldBe null
                d.getLifeTotal(d.player1) shouldBe 30
                d.stackSize shouldBe 0
                d.state.getEntity(target)!!.chosenColor() shouldBe Color.RED
            }
        }
    }

    test("simultaneous copies exclude every coentrant and publish only after all choices") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, body.name)
        val ids = List(2) { d.putCardOnTopOfLibrary(d.player1, carrier().name) }
        val oldObjects = ids.associateWith { d.state.objectRef(it) }
        resolve(d, libraryEntry(2).name)
        for (index in 0..1) {
            val question = (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
            question.options shouldBe listOf(target)
            unpublished(d, ids)
            restore(d)
            d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
            unpublished(d, ids)
            restore(d)
            color(d, if (index == 0) Color.BLUE else Color.BLACK)
            unpublished(d, ids)
            restore(d)
            creatureType(d)
        }
        val ordering = (d.pendingDecision as? ChooseOptionDecision).shouldNotBeNull()
        ordering.options.size shouldBe 2
        ordering.prompt shouldBe "Choose the next triggered ability to put on the stack (first chosen resolves last)"
        restore(d)
        d.submitDecision(ordering.playerId, OptionChosenResponse(ordering.id, 0)).error shouldBe null
        d.pendingDecision shouldBe null
        d.getLifeTotal(d.player1) shouldBe 27
        for (id in ids) {
            d.state.getBattlefield(d.player1).contains(id) shouldBe true
            d.state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            val event = d.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
            event.oldObject shouldBe oldObjects[id]
            event.newObject shouldBe d.state.objectRef(id)
        }
        d.stackSize shouldBe 2
        d.bothPass().error shouldBe null
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 33
        d.stackSize shouldBe 0
    }

    test("declined direct copy asks its own printed choice before entry") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player2, body.name)
        val id = d.putCardInGraveyard(d.player1, carrier(ownChoice = true).name)
        resolve(d, graveEntry.name, listOf(id))
        (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.submitCardSelection(d.player1, emptyList()).error shouldBe null
        unpublished(d, listOf(id))
        restore(d)
        color(d, Color.WHITE)
        d.state.getEntity(id)!!.chosenColor() shouldBe Color.WHITE
        d.state.getEntity(id)!!.get<CardComponent>()!!.name shouldBe carrier(ownChoice = true).name
        d.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }.copyOfOriginalName shouldBe null
        d.getLifeTotal(d.player1) shouldBe 27
    }

    test("pending entry cannot move a later source-zone incarnation") {
        val d = driver()
        val target = d.putCreatureOnBattlefield(d.player2, body.name)
        val id = d.putCardOnTopOfLibrary(d.player1, carrier().name)
        resolve(d, libraryEntry(1).name)
        (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.replaceState(d.state.moveToZone(id, ZoneKey(d.player1, Zone.LIBRARY), ZoneKey(d.player1, Zone.EXILE))
            .moveToZone(id, ZoneKey(d.player1, Zone.EXILE), ZoneKey(d.player1, Zone.LIBRARY)))
        val later = d.state.objectRef(id)
        restore(d)
        d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
        d.pendingDecision shouldBe null
        d.state.objectRef(id) shouldBe later
        d.state.getZone(ZoneKey(d.player1, Zone.LIBRARY)).contains(id) shouldBe true
        unpublished(d, listOf(id))
    }

    test("direct graveyard-copy rider cannot exile a later zone visit after serialized choices") {
        val d = driver()
        val target = d.putCardInGraveyard(d.player2, body.name)
        val id = d.putCardOnTopOfLibrary(d.player1, carrier(graveyard = true).name)
        resolve(d, libraryEntry(1).name)
        (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
        (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
        d.replaceState(d.state.moveToZone(target, ZoneKey(d.player2, Zone.GRAVEYARD), ZoneKey(d.player2, Zone.EXILE))
            .moveToZone(target, ZoneKey(d.player2, Zone.EXILE), ZoneKey(d.player2, Zone.GRAVEYARD)))
        val later = d.state.objectRef(target)
        restore(d)
        color(d, Color.GREEN)
        restore(d)
        creatureType(d)
        d.state.objectRef(target) shouldBe later
        d.state.getGraveyard(d.player2).contains(target) shouldBe true
        d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == target && it.toZone == Zone.EXILE } shouldBe 0
        d.state.getBattlefield(d.player1).contains(id) shouldBe true
    }

    test("two coentering copies with no preexisting eligible object enter without copying each other") {
        val d = driver()
        val ids = List(2) { d.putCardOnTopOfLibrary(d.player1, carrier().name) }
        resolve(d, libraryEntry(2).name)
        d.pendingDecision shouldBe null
        ids.forEach {
            d.state.getBattlefield(d.player1).contains(it) shouldBe true
            d.state.getEntity(it)!!.get<CardComponent>()!!.name shouldBe carrier().name
            d.events.filterIsInstance<ZoneChangeEvent>().count { e -> e.entityId == it && e.toZone == Zone.BATTLEFIELD } shouldBe 1
        }
        d.getLifeTotal(d.player1) shouldBe 27
        d.stackSize shouldBe 0
    }
})
