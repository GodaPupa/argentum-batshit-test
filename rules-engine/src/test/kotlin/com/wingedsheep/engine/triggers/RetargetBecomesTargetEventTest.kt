package com.wingedsheep.engine.triggers

import com.wingedsheep.engine.core.BecomesTargetEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.stack.ContestedRetargetLogic
import com.wingedsheep.engine.handlers.effects.stack.ReselectTargetRandomlyExecutor
import com.wingedsheep.engine.mechanics.stack.TargetingEvents
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.battlefield.TargetedByControllerThisTurnComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ReselectTargetRandomlyEffect
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/** Excluded fixed component cases: no fixture initialization, policy, allocation, or official game. */
class RetargetBecomesTargetEventTest : FunSpec({
    val caster = EntityId("caster")
    val chooser = EntityId("chooser")
    val stackId = EntityId("targeting-object")
    val source = EntityId("redirect-source")
    val a = EntityId("old-target")
    val b = EntityId("new-target")

    fun state(targets: List<ChosenTarget>, kind: String = "spell"): GameState {
        val stackComponent = when (kind) {
            "activated" -> ComponentContainer.of(ActivatedAbilityOnStackComponent(source, "source", caster, Effects.DrawCards(1)))
            "triggered" -> ComponentContainer.of(TriggeredAbilityOnStackComponent(source, "source", caster, Effects.DrawCards(1), "draw"))
            else -> ComponentContainer.of(SpellOnStackComponent(caster))
        }
        return GameState(
            entities = mapOf(
                stackId to stackComponent.with(TargetsComponent(targets)),
                caster to ComponentContainer.EMPTY,
                chooser to ComponentContainer.EMPTY,
                a to ComponentContainer.of(BattlefieldEntryTimestampComponent(1)),
                b to ComponentContainer.of(BattlefieldEntryTimestampComponent(2))
            ),
            zones = mapOf(ZoneKey(caster, Zone.BATTLEFIELD) to listOf(a, b)),
            turnOrder = listOf(caster, chooser),
            stack = listOf(stackId)
        )
    }

    for (kind in listOf("spell", "activated", "triggered")) {
        test("$kind redirect identifies the original targeting source and controller") {
            val before = state(listOf(ChosenTarget.Permanent(a)), kind)
            val result = TargetingEvents.replaceTargets(before, stackId, listOf(ChosenTarget.Permanent(b)))
            val event = result.events.filterIsInstance<BecomesTargetEvent>().single()
            event.sourceEntityId shouldBe stackId
            event.controllerId shouldBe caster
            event.targetEntityId shouldBe b
            event.sourceIsSpell shouldBe (kind == "spell")
            event.firstTimeByThisController shouldBe true
            result.state.getEntity(b)!!.get<TargetedByControllerThisTurnComponent>()!!.hasBeenTargetedBy(caster) shouldBe true
            before.getEntity(b)!!.get<TargetedByControllerThisTurnComponent>() shouldBe null
            result.state.getEntity(stackId)!!.get<TargetsComponent>()!!.targetEntryStamps[b] shouldBe 2L
        }
    }

    test("new player target carries player axis") {
        val result = TargetingEvents.replaceTargets(state(listOf(ChosenTarget.Permanent(a))), stackId, listOf(ChosenTarget.Player(chooser)))
        result.events.filterIsInstance<BecomesTargetEvent>().single().targetIsPlayer shouldBe true
    }

    test("new stack target carries spell axis without permanent tracking") {
        val result = TargetingEvents.replaceTargets(state(listOf(ChosenTarget.Permanent(a))), stackId, listOf(ChosenTarget.Spell(source)))
        val event = result.events.filterIsInstance<BecomesTargetEvent>().single()
        event.targetIsSpell shouldBe true
        event.firstTimeByThisController shouldBe true
    }

    test("retained targets and slot swaps emit no becomes event and preserve captured identity") {
        val before = state(listOf(ChosenTarget.Permanent(a), ChosenTarget.Permanent(b))).updateEntity(stackId) {
            it.with(TargetsComponent(listOf(ChosenTarget.Permanent(a), ChosenTarget.Permanent(b)), targetEntryStamps = mapOf(a to 0L, b to 2L)))
        }
        val result = TargetingEvents.replaceTargets(before, stackId, listOf(ChosenTarget.Permanent(b), ChosenTarget.Permanent(a)))
        result.events shouldHaveSize 0
        result.state.getEntity(stackId)!!.get<TargetsComponent>()!!.targetEntryStamps[a] shouldBe 0L
    }

    test("several slots acquiring the same new entity emit once") {
        val result = TargetingEvents.replaceTargets(state(listOf(ChosenTarget.Permanent(a))), stackId, listOf(ChosenTarget.Permanent(b), ChosenTarget.Permanent(b)))
        result.events.filterIsInstance<BecomesTargetEvent>() shouldHaveSize 1
    }

    test("retargeting back in the same turn emits with first-time false") {
        val first = TargetingEvents.replaceTargets(state(listOf(ChosenTarget.Permanent(a))), stackId, listOf(ChosenTarget.Permanent(b)))
        val away = TargetingEvents.replaceTargets(first.state, stackId, listOf(ChosenTarget.Permanent(a)))
        val back = TargetingEvents.replaceTargets(away.state, stackId, listOf(ChosenTarget.Permanent(b)))
        back.events.filterIsInstance<BecomesTargetEvent>().single().firstTimeByThisController shouldBe false
    }

    test("stack object removed before redirect is unchanged") {
        val before = state(listOf(ChosenTarget.Permanent(a))).copy(stack = emptyList())
        val result = TargetingEvents.replaceTargets(before, stackId, listOf(ChosenTarget.Permanent(b)))
        result.state shouldBe before
        result.events shouldHaveSize 0
    }

    test("completed contested retarget emits before continuing with the original controller") {
        val result = ContestedRetargetLogic.advance(
            state(listOf(ChosenTarget.Permanent(a))), stackId, chooser, caster,
            emptyList(), listOf(ChosenTarget.Permanent(a)), listOf(ChosenTarget.Permanent(b)), 1, source
        )
        val event = result.events.filterIsInstance<BecomesTargetEvent>().single()
        event.controllerId shouldBe caster
        event.sourceEntityId shouldBe stackId
    }

    test("random reselection emits when it chooses a new target") {
        // The old target is deliberately outside the current legal player set: exactly one legal choice.
        val before = state(listOf(ChosenTarget.Player(a))).copy(turnOrder = listOf(chooser)).updateEntity(stackId) {
            it.with(TargetsComponent(listOf(ChosenTarget.Player(a)), listOf(TargetPlayer())))
        }
        val result = ReselectTargetRandomlyExecutor().execute(before, ReselectTargetRandomlyEffect,
            EffectContext(sourceId = source, controllerId = caster, triggeringEntityId = stackId))
        val event = result.events.filterIsInstance<BecomesTargetEvent>().single()
        event.targetEntityId shouldBe chooser
        event.controllerId shouldBe caster
    }

    test("random reselection keeping its target emits no becomes event") {
        val before = state(listOf(ChosenTarget.Player(chooser))).copy(turnOrder = listOf(chooser)).updateEntity(stackId) {
            it.with(TargetsComponent(listOf(ChosenTarget.Player(chooser)), listOf(TargetPlayer())))
        }
        val result = ReselectTargetRandomlyExecutor().execute(before, ReselectTargetRandomlyEffect,
            EffectContext(sourceId = source, controllerId = caster, triggeringEntityId = stackId))
        result.events.filterIsInstance<BecomesTargetEvent>() shouldHaveSize 0
    }
})
