package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.event.BattlefieldEntryOrigin
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class TriggerEntryOriginProvenanceTest : FunSpec({
    val evaluator = ConditionEvaluator()
    val me = EntityId.generate()
    val source = EntityId.generate()
    fun event(from: Zone, cast: Zone? = null) = ZoneChangeEvent(
        source, "Synthetic entry", from, Zone.BATTLEFIELD, me, castFromZone = cast
    )
    fun context(origin: BattlefieldEntryOrigin?) = EffectContext(
        sourceId = source, controllerId = me, triggeringEntityId = source, triggerEntryOrigin = origin
    )
    val absent = GameState(turnOrder = listOf(me)).withEntity(me, ComponentContainer.EMPTY)
    for (zone in listOf(Zone.LIBRARY, Zone.GRAVEYARD, Zone.EXILE, Zone.HAND)) {
        test("direct $zone entry is captured without consulting a surviving permanent") {
            val captured = TriggerContext.fromEvent(event(zone)).entryOrigin
            captured shouldBe BattlefieldEntryOrigin(zone)
            evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(zone), context(captured)) shouldBe true
            evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.COMMAND), context(captured)) shouldBe false
        }
    }
    test("cast origin survives event and trigger serialization independently of stack entry zone") {
        val condition = Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY)
        Json.decodeFromString<com.wingedsheep.sdk.scripting.conditions.Condition>(
            Json.encodeToString<com.wingedsheep.sdk.scripting.conditions.Condition>(condition)) shouldBe condition
        val e = event(Zone.STACK, Zone.LIBRARY)
        val restoredEvent = Json.decodeFromString<ZoneChangeEvent>(Json.encodeToString(e))
        val trigger = TriggerContext.fromEvent(restoredEvent)
        val restored = Json.decodeFromString<TriggerContext>(Json.encodeToString(trigger))
        restored.entryOrigin shouldBe BattlefieldEntryOrigin(Zone.STACK, Zone.LIBRARY)
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY), context(restored.entryOrigin)) shouldBe true
    }
    test("stack ability and copied ability retain entry origin after the source is gone") {
        val ability = TriggeredAbilityOnStackComponent(
            sourceId = source, sourceName = "Synthetic entry", controllerId = me,
            effect = Effects.DrawCards(1), description = "Entry origin fixture",
            triggeringEntityId = source, triggerEntryOrigin = BattlefieldEntryOrigin(Zone.LIBRARY)
        )
        for (a in listOf(ability, ability.copy(copyIndex = 1, copyTotal = 1))) {
            val restored = Json.decodeFromString<TriggeredAbilityOnStackComponent>(Json.encodeToString(a))
            val ctx = EffectContext.forTriggeredAbility(restored)
            evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY), ctx) shouldBe true
        }
    }
    test("a later entry cannot replace an earlier entry's origin") {
        val fromLibrary = TriggerContext.fromEvent(event(Zone.LIBRARY)).entryOrigin
        val fromHand = TriggerContext.fromEvent(event(Zone.HAND)).entryOrigin
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY), context(fromLibrary)) shouldBe true
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY), context(fromHand)) shouldBe false
    }
    test("non-entry and missing historical contexts fail closed for the generalized query") {
        val exit = ZoneChangeEvent(source, "Synthetic exit", Zone.BATTLEFIELD, Zone.LIBRARY, me)
        TriggerContext.fromEvent(exit).entryOrigin shouldBe null
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY), context(null)) shouldBe false
    }
    test("legacy graveyard query uses the captured original rather than later entity markers") {
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromGraveyard,
            context(BattlefieldEntryOrigin(Zone.STACK, Zone.GRAVEYARD))) shouldBe true
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromGraveyard,
            context(BattlefieldEntryOrigin(Zone.HAND))) shouldBe false
    }
    test("real library to battlefield transition provides the direct origin") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        val id = d.putCardOnTopOfLibrary(d.player1, "Centaur Courser")
        val result = ZoneTransitionService.moveToZone(d.state, id, Zone.BATTLEFIELD)
        val entered = result.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
        TriggerContext.fromEvent(entered).entryOrigin shouldBe BattlefieldEntryOrigin(Zone.LIBRARY)
    }
    test("real permanent spell resolution freezes its cast origin on the entry event") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        val id = d.putCardInHand(d.player1, "Centaur Courser")
        val stacked = d.state.removeFromZone(ZoneKey(d.player1, Zone.HAND), id)
            .updateEntity(id) { it.with(SpellOnStackComponent(casterId = d.player1, castFromZone = Zone.LIBRARY)) }
            .pushToStack(id)
        val result = StackResolver(d.cardRegistry).resolveTop(stacked)
        result.isSuccess shouldBe true
        val entered = result.events.filterIsInstance<ZoneChangeEvent>()
            .single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
        entered.castFromZone shouldBe Zone.LIBRARY
        TriggerContext.fromEvent(entered).entryOrigin shouldBe BattlefieldEntryOrigin(Zone.STACK, Zone.LIBRARY)
    }
    test("the historical entry condition is unavailable during projection") {
        evaluator.evaluate(absent, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY),
            ConditionEvaluationContext.Projection(source, null, emptyMap())) shouldBe false
    }
    test("a resolving permanent spell copy does not inherit the original spell cast origin") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        val id = d.putCardInHand(d.player1, "Centaur Courser")
        val stacked = d.state.removeFromZone(ZoneKey(d.player1, Zone.HAND), id)
            .updateEntity(id) { it.with(SpellOnStackComponent(casterId = d.player1, castFromZone = Zone.LIBRARY))
                .with(com.wingedsheep.engine.state.components.identity.CopyOfComponent(
                    originalCardDefinitionId = "Centaur Courser", copiedCardDefinitionId = "Centaur Courser")) }
            .pushToStack(id)
        val result = StackResolver(d.cardRegistry).resolveTop(stacked)
        result.isSuccess shouldBe true
        val entry = result.events.filterIsInstance<ZoneChangeEvent>()
            .single { it.entityId == id && it.toZone == Zone.BATTLEFIELD }
        entry.castFromZone shouldBe null
        TriggerContext.fromEvent(entry).entryOrigin shouldBe BattlefieldEntryOrigin(Zone.STACK)
        evaluator.evaluate(result.state, Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY),
            context(TriggerContext.fromEvent(entry).entryOrigin)) shouldBe false
    }
})
