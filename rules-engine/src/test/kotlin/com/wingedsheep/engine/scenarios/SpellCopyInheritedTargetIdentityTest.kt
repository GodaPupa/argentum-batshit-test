package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.BecomesTargetEvent
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpellCopyInheritedTargetIdentityTest : FunSpec({
    val resolver = StackResolver(cardRegistry = CardRegistry())
    val requirement = TargetObject(filter = TargetFilter(GameObjectFilter.Creature))

    fun state(player: EntityId, spell: EntityId, creature: EntityId, modal: Boolean, currentStamp: Long): GameState {
        val target = ChosenTarget.Permanent(creature)
        return GameState(
            activePlayerId = player, priorityPlayerId = player, turnOrder = listOf(player),
            zones = mapOf(ZoneKey(player, Zone.BATTLEFIELD) to listOf(creature)),
            stack = listOf(spell)
        ).withEntity(creature, ComponentContainer.of(
            CardComponent(cardDefinitionId = "Probe Creature", name = "Probe Creature",
                manaCost = ManaCost.parse("{1}"), typeLine = TypeLine.creature(), oracleText = "", ownerId = player),
            BattlefieldEntryTimestampComponent(currentStamp)
        )).withEntity(spell, ComponentContainer.of(
            CardComponent(cardDefinitionId = "Probe Spell", name = "Probe Spell",
                manaCost = ManaCost.parse("{U}"), typeLine = TypeLine.instant(), oracleText = "", ownerId = player),
            SpellOnStackComponent(
                casterId = player,
                chosenModes = if (modal) listOf(0) else emptyList(),
                modeTargetsOrdered = if (modal) listOf(listOf(target)) else emptyList(),
                modeTargetRequirements = if (modal) mapOf(0 to listOf(requirement)) else emptyMap()
            ),
            TargetsComponent(listOf(target), listOf(requirement), mapOf(creature to 1L))
        ))
    }

    test("inherited flat targets keep the original battlefield visit after blink") {
        val p = EntityId.generate(); val s = EntityId.generate(); val c = EntityId.generate()
        val result = resolver.putSpellCopy(state(p, s, c, false, 2L), s)
        result.isSuccess shouldBe true
        val copied = result.newState.getEntity(result.newState.stack.last())!!.get<TargetsComponent>()!!
        copied.targetEntryStamps[c] shouldBe 1L
        TargetsComponent.isDifferentObject(result.newState, c, copied.targetEntryStamps) shouldBe true
        result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
    }

    test("inherited modal targets keep the same original battlefield visit") {
        val p = EntityId.generate(); val s = EntityId.generate(); val c = EntityId.generate()
        val result = resolver.putSpellCopy(state(p, s, c, true, 2L), s)
        result.isSuccess shouldBe true
        val copied = result.newState.getEntity(result.newState.stack.last())!!.get<TargetsComponent>()!!
        copied.targetEntryStamps[c] shouldBe 1L
        result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
    }

    test("an explicit replacement target captures its current battlefield visit") {
        val p = EntityId.generate(); val s = EntityId.generate(); val c = EntityId.generate()
        val result = resolver.putSpellCopy(state(p, s, c, false, 2L), s,
            targets = listOf(ChosenTarget.Permanent(c)), targetRequirements = listOf(requirement))
        result.isSuccess shouldBe true
        val copied = result.newState.getEntity(result.newState.stack.last())!!.get<TargetsComponent>()!!
        copied.targetEntryStamps[c] shouldBe 2L
        TargetsComponent.isDifferentObject(result.newState, c, copied.targetEntryStamps) shouldBe false
        result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
    }

    test("an unchanged live target inherits identity and emits its new copy targeting event") {
        val p = EntityId.generate(); val s = EntityId.generate(); val c = EntityId.generate()
        val result = resolver.putSpellCopy(state(p, s, c, false, 1L), s)
        result.isSuccess shouldBe true
        val copied = result.newState.getEntity(result.newState.stack.last())!!.get<TargetsComponent>()!!
        copied.targetEntryStamps[c] shouldBe 1L
        result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
    }
})
