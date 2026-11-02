package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PendingTriggersContinuation
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Actual departure capture, preserving the difference between absent and explicitly empty data. */
class LegacyAbsenceChoiceCaptureTest : ScenarioTestBase() {
    private val selected = mapOf(ChoiceSlot.SELF_ALTERNATIVE to ChoiceValue.Flag, ChoiceSlot.KICKED to ChoiceValue.Flag)

    init {
        for ((index, mode) in listOf("absent", "present-empty", "selected").withIndex()) {
            for ((serialIndex, serialized) in listOf(false, true).withIndex()) {
                val label = "LA0${index * 2 + serialIndex + 1}"
                test("$label departure component=$mode serialized=$serialized") {
                    val game = scenario().withPlayers("Player", "Opponent").withCardOnBattlefield(1, "Grizzly Bears").build()
                    val source = game.findPermanent("Grizzly Bears")!!
                    game.state = game.state.updateEntity(source) { it.without<CastChoicesComponent>() }
                    val expected = when (mode) { "absent" -> null; "present-empty" -> emptyMap(); else -> selected }
                    if (expected != null) game.state = game.state.updateEntity(source) { it.with(CastChoicesComponent(chosen = expected)) }
                    game.state.getEntity(source)!!.get<CastChoicesComponent>()?.chosen shouldBe expected
                    val origin = game.state.objectRef(source)!!
                    val trigger = PendingTrigger(TriggeredAbility.create(trigger = Triggers.EntersBattlefield.event, effect = Effects.GainLife(3)),
                        source, "absence probe", controllerId = game.player1Id, triggerContext = TriggerContext(),
                        objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin))
                    game.state = game.state.copy(continuationStack = listOf(PendingTriggersContinuation(listOf(trigger))))
                    if (serialized) roundTrip(game)
                    val result = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND)
                    result.error shouldBe null
                    game.state = result.state
                    if (serialized) roundTrip(game)
                    val captured = game.state.getEntity(source)!!.get<LastKnownPermanentComponent>()!!.snapshot
                    if (mode == "absent") (captured.castChoices == null) shouldBe true
                    else captured.castChoices shouldBe expected
                    captured.objectRef shouldBe origin
                    captured.power shouldBe 2
                    val stored = (game.state.continuationStack.single() as PendingTriggersContinuation).remainingTriggers.single()
                    stored shouldBe trigger.copy(lastKnownSourceSnapshot = captured)
                    game.state.isCurrentObject(origin) shouldBe false
                    ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                        EffectContext(sourceId = source, controllerId = game.player1Id,
                            objectReferences = stored.objectReferences, lastKnownSourceSnapshot = stored.lastKnownSourceSnapshot)) shouldBe (mode == "selected")
                }
            }
        }
        for ((index, mode) in listOf("absent", "present-empty").withIndex()) {
            test("LA0${index + 7} serialized original component=$mode cannot borrow selected later generation") {
                val game = scenario().withPlayers("Player", "Opponent").withCardOnBattlefield(1, "Grizzly Bears").build()
                val source = game.findPermanent("Grizzly Bears")!!
                game.state = game.state.updateEntity(source) { it.without<CastChoicesComponent>() }
                if (mode == "present-empty") game.state = game.state.updateEntity(source) { it.with(CastChoicesComponent(chosen = emptyMap())) }
                val origin = game.state.objectRef(source)!!
                val trigger = PendingTrigger(TriggeredAbility.create(trigger = Triggers.EntersBattlefield.event, effect = Effects.GainLife(3)),
                    source, "generation probe", controllerId = game.player1Id, triggerContext = TriggerContext(),
                    objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin))
                game.state = game.state.copy(continuationStack = listOf(PendingTriggersContinuation(listOf(trigger))))
                val departure = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND)
                departure.error shouldBe null
                game.state = departure.state
                roundTrip(game)
                val stored = (game.state.continuationStack.single() as PendingTriggersContinuation).remainingTriggers.single()
                stored.lastKnownSourceSnapshot!!.objectRef shouldBe origin
                if (mode == "absent") (stored.lastKnownSourceSnapshot!!.castChoices == null) shouldBe true
                else stored.lastKnownSourceSnapshot!!.castChoices shouldBe emptyMap()
                val returned = ZoneTransitionService.moveToZone(game.state, source, Zone.BATTLEFIELD)
                returned.error shouldBe null
                game.state = returned.state.updateEntity(source) { it.with(CastChoicesComponent(chosen = selected)) }
                roundTrip(game)
                game.state.objectRef(source) shouldNotBe origin
                game.state.isCurrentObject(origin) shouldBe false
                game.state.getEntity(source)!!.get<CastChoicesComponent>()!!.chosen shouldBe selected
                (game.state.continuationStack.single() as PendingTriggersContinuation).remainingTriggers.single() shouldBe stored
                ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE),
                    EffectContext(sourceId = source, controllerId = game.player1Id,
                        objectReferences = stored.objectReferences, lastKnownSourceSnapshot = stored.lastKnownSourceSnapshot)) shouldBe false
            }
        }
    }

    private fun roundTrip(game: TestGame) {
        val before = game.state
        game.state = SerializationTestSupport.roundTrip(before)
        game.state shouldBe before
    }
}
