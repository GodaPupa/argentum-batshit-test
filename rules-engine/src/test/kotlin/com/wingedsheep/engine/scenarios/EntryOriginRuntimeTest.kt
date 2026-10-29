package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.EntryOrigin
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.conditions.Condition
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.EffectChoice
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class EntryOriginRuntimeTest : FunSpec({
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    val owner = EntityId.generate()
    val other = EntityId.generate()
    val entity = EntityId.generate()
    val origin = EntryOrigin(entity, ObjectRef(entity, 3L), Zone.LIBRARY, owner)
    val condition = Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY, ownedByController = true)
    val context = EffectContext(entity, owner, triggeringEntityId = entity, triggerEntryOrigin = origin)
    val evaluator = ConditionEvaluator()
    val payoff = ConditionalEffect(condition, Effects.DrawCards(2), Effects.DrawCards(1))

    test("zone condition survives polymorphic SDK serialization") {
        val decoded = json.decodeFromString<Condition>(json.encodeToString<Condition>(condition))
        decoded shouldBe condition
        evaluator.evaluate(GameState(), decoded, context) shouldBe true
    }
    test("event capture keeps both direct and cast zones and the exact entry object") {
        val event = ZoneChangeEvent(entity, "Origin", Zone.STACK, Zone.BATTLEFIELD, owner,
            newObject = origin.entryObject, entryCastFromZone = Zone.LIBRARY)
        val captured = TriggerContext.fromEvent(json.decodeFromString<ZoneChangeEvent>(json.encodeToString(event)))
        captured.entryOrigin shouldBe origin.copy(fromZone = Zone.STACK, castFromZone = Zone.LIBRARY)
        captured.triggeringEntityId shouldBe entity
    }
    test("non entry events cannot provide battlefield entry provenance") {
        TriggerContext.fromEvent(ZoneChangeEvent(entity, "Origin", Zone.LIBRARY, Zone.EXILE, owner)).entryOrigin shouldBe null
    }
    test("unknown legacy origin is false and its default remains omitted") {
        val legacy = context.copy(triggerEntryOrigin = null)
        val encoded = json.encodeToString(legacy)
        encoded.contains("triggerEntryOrigin") shouldBe false
        val restored = json.decodeFromString<EffectContext>(encoded)
        restored.triggerEntryOrigin shouldBe null
        evaluator.evaluate(GameState(), condition, restored) shouldBe false
    }
    test("owner constraint distinguishes the controller library from another library") {
        evaluator.evaluate(GameState(), condition, context.copy(controllerId = other)) shouldBe false
        evaluator.evaluate(GameState(), condition, context.copy(controllerId = other, effectControllerId = owner)) shouldBe true
        evaluator.evaluate(GameState(), Conditions.TriggeringEntityEnteredOrWasCastFromZone(Zone.LIBRARY), context.copy(controllerId = other)) shouldBe true
    }
    test("unrelated triggering entity cannot borrow captured origin") {
        evaluator.evaluate(GameState(), condition, context.copy(triggeringEntityId = other)) shouldBe false
    }
    test("stack and nested effect continuation serialize the same captured origin") {
        val ability = TriggeredAbilityOnStackComponent(entity, "Origin", owner, payoff, "Draw from origin",
            triggeringEntityId = entity, triggerEntryOrigin = origin)
        val decoded = json.decodeFromString<TriggeredAbilityOnStackComponent>(json.encodeToString(ability))
        decoded.triggerEntryOrigin shouldBe origin
        val effectContext = EffectContext.forTriggeredAbility(decoded)
        val nested = EffectContinuation(listOf(payoff), effectContext)
        val restored = json.decodeFromString<EffectContinuation>(json.encodeToString(nested))
        restored.effectContext.triggerEntryOrigin shouldBe origin
        evaluator.evaluate(GameState(), condition, restored.effectContext) shouldBe true
    }
    test("each entry keeps its own immutable origin across successive visits") {
        val first = TriggerContext.fromEvent(ZoneChangeEvent(entity, "Origin", Zone.LIBRARY, Zone.BATTLEFIELD, owner, newObject = ObjectRef(entity, 3L)))
        val second = TriggerContext.fromEvent(ZoneChangeEvent(entity, "Origin", Zone.EXILE, Zone.BATTLEFIELD, owner, newObject = ObjectRef(entity, 5L)))
        first.entryOrigin shouldBe origin
        second.entryOrigin shouldBe origin.copy(entryObject = ObjectRef(entity, 5L), fromZone = Zone.EXILE)
        evaluator.evaluate(GameState(), condition, context.copy(triggerEntryOrigin = second.entryOrigin)) shouldBe false
        evaluator.evaluate(GameState(), condition, context.copy(triggerEntryOrigin = first.entryOrigin)) shouldBe true
    }

    val entrant = card("Entry Origin Modal Fixture") {
        manaCost = "{0}"; typeLine = "Creature — Homunculus"; power = 1; toughness = 1
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            effect = com.wingedsheep.sdk.scripting.effects.ModalEffect(chooseCount = 2, modes = listOf(
                com.wingedsheep.sdk.scripting.effects.Mode.noTarget(Effects.ChooseAction(listOf(
                    EffectChoice("Draw from entry", payoff), EffectChoice("Gain one", Effects.GainLife(1)))), "Nested origin choice"),
                com.wingedsheep.sdk.scripting.effects.Mode.noTarget(payoff, "Origin tail")
            ))
        }
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = payoff }
    }
    val entry = card("Entry Origin Library Fixture") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
            move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
        } }
    }
    test("queued modal and nested choices retain origin through every serialized pause") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(entrant, entry))
        d.initMirrorMatch(Deck.of("Island" to 40)); d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        d.putCardOnTopOfLibrary(d.player1, entrant.name)
        val spell = d.putCardInHand(d.player1, entry.name)
        d.castSpell(d.player1, spell).error shouldBe null
        val before = d.getHandSize(d.player1)
        d.bothPass().error shouldBe null
        d.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        var choices = 0
        var passes = 0
        while (d.state.pendingDecision != null || d.state.stack.isNotEmpty()) {
            d.replaceState(json.decodeFromString<GameState>(json.encodeToString(d.state)))
            val question = d.state.pendingDecision
            if (question != null) {
                val q = question.shouldBeInstanceOf<ChooseOptionDecision>()
                val index = q.options.indexOfFirst { it.contains("Nested origin choice") || it.contains("Draw from entry") }.takeIf { it >= 0 } ?: 0
                d.submitDecision(q.playerId, OptionChosenResponse(q.id, index)).error shouldBe null
                choices++
            } else {
                d.bothPass().error shouldBe null
                passes++
            }
            (choices + passes < 20) shouldBe true
        }
        (choices >= 4) shouldBe true
        d.getHandSize(d.player1) shouldBe before + 6
    }
})
