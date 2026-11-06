package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PendingTriggersContinuation
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.TriggeredAbility
import io.kotest.matchers.shouldBe

class DepartureTimeContinuationNegativeWitnessTest : ScenarioTestBase() {
    init {
        test("DT negative pending queue retains original selected choice") {
            val game = scenario().withPlayers("Player", "Opponent").withCardOnBattlefield(1, "Grizzly Bears").build()
            val source = game.findPermanent("Grizzly Bears")!!
            val choices = mapOf(ChoiceSlot.SELF_ALTERNATIVE to ChoiceValue.Flag)
            game.state = game.state.updateEntity(source) { it.with(CastChoicesComponent(chosen = choices)) }
            val origin = game.state.objectRef(source)!!
            val trigger = PendingTrigger(TriggeredAbility.create(trigger = Triggers.EntersBattlefield.event, effect = Effects.GainLife(3)),
                source, "probe", controllerId = game.player1Id, triggerContext = TriggerContext(),
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin))
            game.state = game.state.copy(continuationStack = listOf(PendingTriggersContinuation(listOf(trigger))))
            game.state = SerializationTestSupport.roundTrip(game.state)
            game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND).state
            game.state = SerializationTestSupport.roundTrip(game.state)
            val stored = (game.state.continuationStack.single() as PendingTriggersContinuation).remainingTriggers.single()
            stored.lastKnownSourceSnapshot?.castChoices?.containsKey(ChoiceSlot.SELF_ALTERNATIVE) shouldBe true
            stored.lastKnownSourceSnapshot?.objectRef shouldBe origin
            stored shouldBe trigger.copy(lastKnownSourceSnapshot = stored.lastKnownSourceSnapshot)
        }
    }
}

class DepartureTimeExistingLkiNegativeWitnessTest : ScenarioTestBase() {
    init {
        test("DT negative genuine LKI is never overwritten") {
            val game = scenario().withPlayers("Player", "Opponent").withCardOnBattlefield(1, "Grizzly Bears").build()
            val source = game.findPermanent("Grizzly Bears")!!
            val origin = game.state.objectRef(source)!!
            val genuine = EntitySnapshot.fromProjection(source, game.state).copy(power = 71, colors = setOf("BLUE"))
            val trigger = PendingTrigger(TriggeredAbility.create(trigger = Triggers.EntersBattlefield.event, effect = Effects.GainLife(3)),
                source, "probe", controllerId = game.player1Id, triggerContext = TriggerContext(),
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin),
                lastKnownSourceSnapshot = genuine)
            game.state = game.state.copy(continuationStack = listOf(PendingTriggersContinuation(listOf(trigger))))
            game.state = SerializationTestSupport.roundTrip(game.state)
            game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND).state
            game.state = SerializationTestSupport.roundTrip(game.state)
            val stored = (game.state.continuationStack.single() as PendingTriggersContinuation).remainingTriggers.single()
            stored.lastKnownSourceSnapshot?.power shouldBe 71
            stored shouldBe trigger
        }
    }
}
