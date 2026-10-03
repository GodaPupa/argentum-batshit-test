package com.wingedsheep.engine.triggers

import com.wingedsheep.engine.core.BecomesTargetEvent
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.mechanics.stack.TargetingEvents
import com.wingedsheep.engine.mechanics.targeting.TargetValidator
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Excluded fixed-component diagnostics, not gameplay or card admission. */
class RetargetStructureBoundaryTest : FunSpec({
    val caster = EntityId("caster")
    val old = EntityId("old-player")
    val destination = EntityId("new-player")
    val stackId = EntityId("stack-object")
    val beforeTargets = listOf(ChosenTarget.Player(old), ChosenTarget.Player(caster))
    val afterTargets = listOf(ChosenTarget.Player(destination), ChosenTarget.Player(caster))
    fun state(spell: SpellOnStackComponent = SpellOnStackComponent(caster)): GameState = GameState(
        entities = mapOf(
            stackId to ComponentContainer.of(spell).with(TargetsComponent(beforeTargets, listOf(TargetPlayer(), TargetPlayer()))),
            caster to ComponentContainer.EMPTY,
            old to ComponentContainer.EMPTY,
            destination to ComponentContainer.EMPTY
        ),
        turnOrder = listOf(caster, old, destination),
        stack = listOf(stackId)
    )

    test("control one replaced flat slot preserves the other slot and emits exactly once") {
        val result = TargetingEvents.replaceTargets(state(), stackId, afterTargets)
        result.state.getEntity(stackId)!!.get<TargetsComponent>()!!.targets shouldBe afterTargets
        result.events.filterIsInstance<BecomesTargetEvent>().map { it.targetEntityId } shouldBe listOf(destination)
    }

    test("required modal target bindings follow the replaced flat slot without changing modes") {
        val spell = SpellOnStackComponent(caster, chosenModes = listOf(0, 1),
            modeTargetsOrdered = beforeTargets.map { listOf(it) },
            modeTargetRequirements = mapOf(0 to listOf(TargetPlayer()), 1 to listOf(TargetPlayer())))
        val result = TargetingEvents.replaceTargets(state(spell), stackId, afterTargets)
        val updated = result.state.getEntity(stackId)!!.get<SpellOnStackComponent>()!!
        updated.chosenModes shouldBe listOf(0, 1)
        updated.modeTargetsOrdered shouldBe afterTargets.map { listOf(it) }
    }

    test("required spliced target bindings follow the replaced splice slot") {
        val spell = SpellOnStackComponent(caster, splicedCardNames = listOf("Synthetic splice text"),
            splicedTargetsOrdered = listOf(listOf(beforeTargets[1])))
        val changed = listOf(beforeTargets[0], ChosenTarget.Player(destination))
        val result = TargetingEvents.replaceTargets(state(spell), stackId, changed)
        result.state.getEntity(stackId)!!.get<SpellOnStackComponent>()!!.splicedTargetsOrdered shouldBe listOf(listOf(changed[1]))
    }

    test("required divided allocation follows its changed target without redistribution") {
        val spell = SpellOnStackComponent(caster, damageDistribution = mapOf(old to 2, caster to 1))
        val result = TargetingEvents.replaceTargets(state(spell), stackId, afterTargets)
        result.state.getEntity(stackId)!!.get<SpellOnStackComponent>()!!.damageDistribution shouldBe mapOf(destination to 2, caster to 1)
    }

    test("required omitted optional target group does not consume the following required group") {
        // Legal declaration: choose zero for 'up to one target player', then choose old for
        // the separately required 'target player'. The flat action carries no group cardinality.
        val error = TargetValidator().validateTargets(
            state(), listOf(ChosenTarget.Player(old)),
            listOf(TargetPlayer(optional = true, id = "optional"), TargetPlayer(id = "required")),
            caster, sourceId = stackId, targetingSourceType = TargetingSourceType.SPELL
        )
        error shouldBe null
    }
    test("ambiguous allocation merge rejects atomically without events") {
        val before = state(SpellOnStackComponent(caster, damageDistribution = mapOf(old to 2, caster to 1)))
        val result = TargetingEvents.replaceTargets(before, stackId, listOf(ChosenTarget.Player(caster), ChosenTarget.Player(caster)))
        result.state shouldBe before
        result.events shouldBe emptyList()
    }

    test("inconsistent existing modal slice rejects atomically without events") {
        val before = state(SpellOnStackComponent(caster, chosenModes = listOf(0), modeTargetsOrdered = listOf(listOf(ChosenTarget.Player(destination)))))
        val result = TargetingEvents.replaceTargets(before, stackId, afterTargets)
        result.state shouldBe before
        result.events shouldBe emptyList()
    }

})
