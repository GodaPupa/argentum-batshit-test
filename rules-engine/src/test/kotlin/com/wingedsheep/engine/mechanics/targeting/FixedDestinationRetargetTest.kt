package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.BecomesTargetEvent
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FixedDestinationRetargetTest : FunSpec({
    val caster = EntityId("caster")
    val old = EntityId("old")
    val next = EntityId("next")
    val stack = EntityId("spell")
    fun target(id: EntityId) = ChosenTarget.Player(id)
    fun state(targets: List<ChosenTarget> = listOf(target(old)), counts: List<Int> = listOf(1)): GameState = GameState(
        entities = mapOf(caster to ComponentContainer.EMPTY, old to ComponentContainer.EMPTY, next to ComponentContainer.EMPTY,
            stack to ComponentContainer.of(SpellOnStackComponent(caster)).with(TargetsComponent(
                targets, counts.map { TargetPlayer(count = it) }, announcedTargetCounts = counts))),
        turnOrder = listOf(caster, old, next), stack = listOf(stack))
    test("single legal slot is replaced and emits exactly one event") {
        val before = state()
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe listOf(0)
        val result = FixedDestinationRetarget.replaceSlot(before, stack, 0, target(next))
        result.state.getEntity(stack)!!.get<TargetsComponent>()!!.targets shouldBe listOf(target(next))
        result.events.filterIsInstance<BecomesTargetEvent>().map { it.targetEntityId } shouldBe listOf(next)
    }
    test("one slot among several preserves every other target") {
        val before = state(listOf(target(old), target(caster)), listOf(1, 1))
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe listOf(0, 1)
        FixedDestinationRetarget.replaceSlot(before, stack, 1, target(next)).state.getEntity(stack)!!
            .get<TargetsComponent>()!!.targets shouldBe listOf(target(old), target(next))
    }
    test("wrong destination kind is rejected without mutation") {
        val before = state()
        val destination = ChosenTarget.Permanent(next)
        FixedDestinationRetarget.legalSlots(before, stack, destination) shouldBe emptyList()
        FixedDestinationRetarget.replaceSlot(before, stack, 0, destination).state shouldBe before
    }
    test("distinct target collision within one group is rejected") {
        val before = state(listOf(target(old), target(next)), listOf(2))
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe emptyList()
    }
    test("nonexistent destination and absent stack object have no legal slot") {
        FixedDestinationRetarget.legalSlots(state(), stack, target(EntityId("absent"))) shouldBe emptyList()
        FixedDestinationRetarget.legalSlots(state(), EntityId("absent"), target(next)) shouldBe emptyList()
    }
    test("an unchanged target that is now illegal does not block a legal changed slot") {
        val before = state(listOf(target(EntityId("departed")), target(old)), listOf(1, 1))
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe listOf(0, 1)
        FixedDestinationRetarget.replaceSlot(before, stack, 1, target(next)).state.getEntity(stack)!!
            .get<TargetsComponent>()!!.targets shouldBe listOf(target(EntityId("departed")), target(next))
    }
    test("stale selection is revalidated and remains atomic") {
        val before = state()
        FixedDestinationRetarget.replaceSlot(before, stack, 7, target(next)).state shouldBe before
        FixedDestinationRetarget.replaceSlot(before, stack, 0, target(old)).events shouldBe emptyList()
    }
    test("triggered ability modal bindings and allocations follow the changed slot") {
        val targets = listOf(target(old), target(caster))
        val ability = TriggeredAbilityOnStackComponent(caster, "source", caster,
            com.wingedsheep.sdk.dsl.Effects.DrawCards(1), "synthetic", chosenModes = listOf(0, 1),
            modeTargetsOrdered = targets.map { listOf(it) },
            modeTargetRequirements = mapOf(0 to listOf(TargetPlayer()), 1 to listOf(TargetPlayer())),
            modeDamageDistribution = mapOf(0 to mapOf(old to 2)))
        val before = state(targets, listOf(1, 1)).updateEntity(stack) {
            it.without<SpellOnStackComponent>().with(ability)
        }
        val result = FixedDestinationRetarget.replaceSlot(before, stack, 0, target(next))
        val after = result.state.getEntity(stack)!!.get<TriggeredAbilityOnStackComponent>()!!
        after.modeTargetsOrdered shouldBe listOf(listOf(target(next)), listOf(target(caster)))
        after.modeDamageDistribution shouldBe mapOf(0 to mapOf(next to 2))
        result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
    }
    test("departure snapshot supports legal retarget after source leaves") {
        val definition = com.wingedsheep.sdk.dsl.card("Historical source") { manaCost = "{0}"; typeLine = "Artifact" }
        val ability = ActivatedAbilityOnStackComponent(old, "source", caster,
            com.wingedsheep.sdk.dsl.Effects.DrawCards(1), lastKnownSourceSnapshot = EntitySnapshot(
                old, colors = emptySet(), typeLine = definition.typeLine))
        val before = state().updateEntity(stack) { it.without<SpellOnStackComponent>().with(ability) }
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe listOf(0)
    }
    test("partial departure snapshot fails closed") {
        val ability = ActivatedAbilityOnStackComponent(old, "source", caster,
            com.wingedsheep.sdk.dsl.Effects.DrawCards(1), lastKnownSourceSnapshot = EntitySnapshot(old))
        val before = state().updateEntity(stack) { it.without<SpellOnStackComponent>().with(ability) }
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe emptyList()
    }
    test("historical source card type protection overrides returned source characteristics") {
        val artifact = com.wingedsheep.sdk.dsl.card("Historical artifact") { manaCost = "{0}"; typeLine = "Artifact" }
        val creature = com.wingedsheep.sdk.dsl.card("Returned creature") {
            manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
        }
        val permanent = com.wingedsheep.engine.core.CardEntityFactory.create(creature, caster)
            .with(com.wingedsheep.engine.state.components.identity.ProtectionComponent(
                colors = emptySet(), cardTypes = setOf("ARTIFACT")))
        val before = GameState(entities = mapOf(next to permanent,
            old to com.wingedsheep.engine.core.CardEntityFactory.create(creature, caster)),
            zones = mapOf(com.wingedsheep.engine.state.ZoneKey(caster, com.wingedsheep.sdk.core.Zone.BATTLEFIELD) to listOf(next, old)))
        val validator = TargetValidator()
        val targets = listOf(ChosenTarget.Permanent(next))
        val requirements = listOf(com.wingedsheep.sdk.dsl.Targets.Creature)
        validator.validateTargets(before, targets, requirements, caster, sourceId = old,
            targetingSourceType = com.wingedsheep.engine.handlers.TargetingSourceType.ABILITY) shouldBe null
        validator.validateTargets(before, targets, requirements, caster, sourceId = old,
            targetingSourceType = com.wingedsheep.engine.handlers.TargetingSourceType.ABILITY,
            sourceSnapshot = EntitySnapshot(old, colors = emptySet(), typeLine = artifact.typeLine)).isNullOrBlank() shouldBe false
    }
    test("missing source without departure snapshot cannot establish legality") {
        val ability = ActivatedAbilityOnStackComponent(EntityId("missing-source"), "source", caster,
            com.wingedsheep.sdk.dsl.Effects.DrawCards(1))
        val before = state().updateEntity(stack) { it.without<SpellOnStackComponent>().with(ability) }
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe emptyList()
    }
    test("stale captured source origin without departure snapshot cannot borrow new visit") {
        val before = state()
        val ability = ActivatedAbilityOnStackComponent(old, "source", caster,
            com.wingedsheep.sdk.dsl.Effects.DrawCards(1), objectReferences = com.wingedsheep.engine.handlers.ObjectReferenceEnvironment(
                captured = true, origin = com.wingedsheep.engine.state.ObjectRef(old, 999)))
        val updated = before.updateEntity(stack) { it.without<SpellOnStackComponent>().with(ability) }
        FixedDestinationRetarget.legalSlots(updated, stack, target(next)) shouldBe emptyList()
    }
    test("divided allocations in independent modes follow only their own target slot") {
        val same = listOf(target(old), target(old))
        val spell = SpellOnStackComponent(caster, chosenModes = listOf(0, 1),
            modeTargetsOrdered = same.map { listOf(it) },
            modeTargetRequirements = mapOf(0 to listOf(TargetPlayer()), 1 to listOf(TargetPlayer())),
            modeDamageDistribution = mapOf(0 to mapOf(old to 2), 1 to mapOf(old to 3)))
        val before = state(same, listOf(1, 1)).updateEntity(stack) { it.with(spell) }
        FixedDestinationRetarget.legalSlots(before, stack, target(next)) shouldBe listOf(0, 1)
        val result = FixedDestinationRetarget.replaceSlot(before, stack, 0, target(next))
        result.state.getEntity(stack)!!.get<SpellOnStackComponent>()!!.modeDamageDistribution shouldBe
            mapOf(0 to mapOf(next to 2), 1 to mapOf(old to 3))
        result.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
    }
})
