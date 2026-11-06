package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.event.TriggerProcessor
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.ConditionEvaluationContext
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.captureEntitySnapshots
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Additional transport controls; the eight original real-action diagnostics remain untouched. */
class SourceDepartureChoiceRepairTest : ScenarioTestBase() {
    private val selected = mapOf(ChoiceSlot.SELF_ALTERNATIVE to ChoiceValue.Flag)
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    init {
        cardRegistry.register(card("Returning Choice Probe") {
            manaCost = "{0}"; typeLine = "Creature — Construct"; power = 2; toughness = 2
            keywords(Keyword.FLASH)
            selfAlternativeCost = SelfAlternativeCost(ManaCost.parse("{0}"))
            triggeredAbility {
                trigger = Triggers.EntersBattlefield
                interveningIf = CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE)
                effect = Effects.GainLife(3)
            }
        })
        for (serialized in listOf(false, true)) {
            test("SD02 selected old source returns normally serialized=$serialized") {
                val game = board()
                val action = game.getLegalActions(1).mapNotNull { it.action as? CastSpell }.first {
                    game.state.getEntity(it.cardId)?.get<CardComponent>()?.name == "Returning Choice Probe" &&
                        it.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
                }
                game.execute(action).error shouldBe null
                var passes = 0
                while (game.findPermanent("Returning Choice Probe") == null && passes++ < 8) game.passPriority().error shouldBe null
                game.findPermanent("Returning Choice Probe") shouldBe action.cardId
                val old = game.state.objectRef(action.cardId)!!
                game.state.stack.size shouldBe 1
                game.castSpell(1, "Unsummon", action.cardId).error shouldBe null
                passes = 0
                while (action.cardId !in game.state.getHand(game.player1Id) && passes++ < 8) game.passPriority().error shouldBe null
                game.state.getHand(game.player1Id).contains(action.cardId) shouldBe true
                game.state.isCurrentObject(old) shouldBe false
                if (serialized) roundTrip(game)
                game.castSpell(1, "Returning Choice Probe").error shouldBe null
                passes = 0
                while (game.findPermanent("Returning Choice Probe") == null && passes++ < 8) game.passPriority().error shouldBe null
                game.findPermanent("Returning Choice Probe") shouldBe action.cardId
                game.state.objectRef(action.cardId) shouldNotBe old
                game.state.getEntity(action.cardId)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
                game.state.stack.size shouldBe 1
                if (serialized) roundTrip(game)
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.stack.size shouldBe 0
                game.getLifeTotal(1) shouldBe 23
            }
        }

        // Engine transport isolation: move the same entity through actual identity bookkeeping,
        // explicitly arrange independent choices, then resolve an already-created trigger/context.
        for (capture in listOf("selected", "empty", "unknown", "missing", "mismatched")) {
            test("SD03 original capture=$capture cannot borrow a selected new generation") {
                val game = board(); val id = game.findPermanent("Grizzly Bears")!!
                val old = game.state.objectRef(id)!!
                val snapshot = EntitySnapshot.fromProjection(id, game.state).copy(castChoices = when (capture) {
                    "selected" -> selected
                    "empty" -> emptyMap()
                    else -> null
                })
                depart(game, id); returnSelected(game, id)
                val supplied = when (capture) {
                    "missing" -> null
                    "mismatched" -> snapshot.copy(objectRef = game.state.objectRef(id), castChoices = selected)
                    else -> snapshot
                }
                val context = EffectContext(sourceId = id, controllerId = game.player1Id,
                    objectReferences = ObjectReferenceEnvironment(captured = true, origin = old, source = old),
                    lastKnownSourceSnapshot = supplied)
                val restored = json.decodeFromString<EffectContext>(json.encodeToString(context))
                restored shouldBe context
                roundTrip(game)
                game.state.isCurrentObject(old) shouldBe false
                game.state.getEntity(id)?.get<CastChoicesComponent>()?.chosen shouldBe selected
                ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE), restored) shouldBe (capture == "selected")
            }
        }
        for (beforePause in listOf(false, true)) for (serialized in listOf(false, true)) for (paid in listOf(false, true)) {
            test("SD05 target pause departedBefore=$beforePause serialized=$serialized selected=$paid") {
                val game = board(); val source = game.findPermanent("Grizzly Bears")!!
                val target = game.findPermanent("Hill Giant")!!
                game.state = game.state.updateEntity(source) { it.with(CastChoicesComponent(chosen = if (paid) selected else emptyMap())) }
                val snapshot = EntitySnapshot.fromProjection(source, game.state)
                val origin = game.state.objectRef(source)!!
                val trigger = pending(game, source, game.player1Id, targeted = true).copy(
                    lastKnownSourceSnapshot = if (beforePause) snapshot else null)
                if (beforePause) depart(game, source)
                if (serialized) roundTrip(game)
                val result = TriggerProcessor(cardRegistry, StackResolver(cardRegistry)).processTriggers(game.state, listOf(trigger))
                result.error shouldBe null
                result.isPaused shouldBe true
                game.state = result.state
                game.getPendingDecision() shouldNotBe null
                if (serialized) roundTrip(game)
                if (!beforePause) depart(game, source)
                if (serialized) roundTrip(game)
                game.selectTargets(listOf(target)).error shouldBe null
                game.state.stack.size shouldBe 1
                val stack = game.state.getEntity(game.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!
                stack.objectReferences.origin shouldBe origin
                stack.lastKnownSourceSnapshot?.objectRef shouldBe origin
                stack.lastKnownSourceSnapshot?.castChoices shouldBe if (paid) selected else emptyMap()
                if (serialized) roundTrip(game)
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.stack.size shouldBe 0
                game.getLifeTotal(1) shouldBe if (paid) 23 else 20
            }
        }
        for (reverseInput in listOf(false, true)) {
            test("SD04 opposite instances retain controller and APNAP order reverseInput=$reverseInput") {
                val game = board(); val a = game.findPermanent("Grizzly Bears")!!
                val b = game.findPermanent("Hill Giant")!!
                game.state = game.state.updateEntity(a) { it.with(CastChoicesComponent(chosen = selected)) }
                val triggers = listOf(pending(game, a, game.player1Id), pending(game, b, game.player2Id))
                val result = TriggerProcessor(cardRegistry, StackResolver(cardRegistry)).processTriggers(
                    game.state, if (reverseInput) triggers.reversed() else triggers)
                result.error shouldBe null; result.isPaused shouldBe false
                game.state = result.state
                game.state.stack.map { game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!.controllerId } shouldBe
                    listOf(game.player1Id, game.player2Id)
                depart(game, a); depart(game, b); roundTrip(game)
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.stack.size shouldBe 0
                game.getLifeTotal(1) shouldBe 23
                game.getLifeTotal(2) shouldBe 20
            }
        }
        test("SD06 legacy unknown and known empty snapshots stay distinct") {
            val game = board(); val id = game.findPermanent("Grizzly Bears")!!
            val legacy = EntitySnapshot(id)
            val encoded = json.encodeToString(legacy)
            encoded.contains("castChoices") shouldBe false
            json.decodeFromString<EntitySnapshot>(encoded).castChoices shouldBe null
            val empty = EntitySnapshot.fromProjection(id, game.state)
            empty.castChoices shouldBe emptyMap()
            json.decodeFromString<EntitySnapshot>(json.encodeToString(empty)) shouldBe empty
            captureEntitySnapshots(listOf(id), game.state).single().castChoices shouldBe emptyMap()
            captureEntitySnapshots(listOf(id), game.state.projectedState).single().castChoices shouldBe null
            game.state = game.state.updateEntity(id) { it.with(CastChoicesComponent(chosen = selected)) }
            captureEntitySnapshots(listOf(id), game.state).single().castChoices shouldBe selected
            empty.castChoices shouldBe emptyMap()
        }
        for (capture in listOf("selected", "empty", "unknown", "missing")) {
            test("SD06 current original capture=$capture respects known versus unknown") {
                val game = board(); val id = game.findPermanent("Grizzly Bears")!!
                game.state = game.state.updateEntity(id) { it.with(CastChoicesComponent(chosen = selected)) }
                val origin = game.state.objectRef(id)!!
                val snapshot = if (capture == "missing") null else EntitySnapshot.fromProjection(id, game.state).copy(
                    castChoices = when (capture) { "empty" -> emptyMap(); "unknown" -> null; else -> selected })
                val ctx = EffectContext(sourceId = id, controllerId = game.player1Id,
                    objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin),
                    lastKnownSourceSnapshot = snapshot)
                ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE), ctx) shouldBe (capture != "empty")
            }
        }
        test("SD08 legacy declaration projection and unrelated slots preserve their readers") {
            val game = board(); val id = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(id) { it.with(CastChoicesComponent(chosen = selected + (ChoiceSlot.KICKED to ChoiceValue.Flag))) }
            val evaluator = ConditionEvaluator()
            val legacy = EffectContext(sourceId = id, controllerId = game.player1Id)
            evaluator.evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE), legacy) shouldBe true
            evaluator.evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                legacy.copy(objectReferences = ObjectReferenceEnvironment(captured = true))) shouldBe false
            evaluator.evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                legacy.copy(sourceId = null, declaredCostSlot = ChoiceSlot.SELF_ALTERNATIVE)) shouldBe true
            val projection = ConditionEvaluationContext.Projection(id, null, emptyMap())
            evaluator.evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE), projection) shouldBe true
            evaluator.evaluate(game.state, CastChoiceMade(ChoiceSlot.KICKED), legacy) shouldBe true
            val snapshot = EntitySnapshot.fromProjection(id, game.state)
            depart(game, id)
            evaluator.evaluate(game.state, CastChoiceMade(ChoiceSlot.KICKED), legacy.copy(
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = snapshot.objectRef),
                lastKnownSourceSnapshot = snapshot)) shouldBe false
        }
        test("SD08 current capture enriches choices without replacing prior source fields") {
            val game = board(); val id = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(id) { it.with(CastChoicesComponent(chosen = selected)) }
            val prior = EntitySnapshot.fromProjection(id, game.state).copy(power = 71, colors = setOf("BLUE"), castChoices = emptyMap())
            val result = TriggerProcessor(cardRegistry, StackResolver(cardRegistry)).processTriggers(game.state,
                listOf(pending(game, id, game.player1Id).copy(lastKnownSourceSnapshot = prior)))
            result.error shouldBe null
            val frozen = result.state.getEntity(result.state.stack.single())!!.get<TriggeredAbilityOnStackComponent>()!!.lastKnownSourceSnapshot
            frozen shouldBe prior.copy(castChoices = selected)
        }
    }

    private fun board(): TestGame {
        var builder = scenario().withPlayers("Player", "Opponent")
            .withCardInHand(1, "Returning Choice Probe").withCardInHand(1, "Unsummon")
            .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Hill Giant")
            .withLandsOnBattlefield(1, "Island", 2)
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(5) { builder = builder.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain") }
        return builder.build()
    }
    private fun roundTrip(game: TestGame) {
        val before = game.state
        game.state = SerializationTestSupport.roundTrip(before)
        game.state shouldBe before
    }
    private fun depart(game: TestGame, id: EntityId) {
        game.state = com.wingedsheep.engine.handlers.effects.ZoneTransitionService
            .moveToZone(game.state, id, Zone.HAND).state
    }
    private fun returnSelected(game: TestGame, id: EntityId) {
        val from = game.state.logicalZone(id)!!
        game.state = game.state.moveToZone(id, from, ZoneKey(from.ownerId, Zone.BATTLEFIELD))
            .updateEntity(id) { it.with(CastChoicesComponent(chosen = selected)) }
    }
    private fun pending(game: TestGame, id: EntityId, controller: EntityId, targeted: Boolean = false): PendingTrigger {
        val origin = game.state.objectRef(id)!!
        return PendingTrigger(
            ability = TriggeredAbility.create(trigger = Triggers.EntersBattlefield.event, effect = Effects.GainLife(3),
                interveningIf = CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                targetRequirement = if (targeted) TargetCreature() else null),
            sourceId = id, sourceName = "Transport Probe", controllerId = controller,
            objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin),
            triggerContext = TriggerContext())
    }
}
