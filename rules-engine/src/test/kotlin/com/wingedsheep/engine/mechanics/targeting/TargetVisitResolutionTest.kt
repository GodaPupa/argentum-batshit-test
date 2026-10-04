package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class TargetVisitResolutionTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    private val body = card("Visit witness") { manaCost = "{0}"; typeLine = "Creature — Bear"; power = 1; toughness = 10 }
    private val effect = Effects.Composite(Effects.DealDamage(1, EffectTarget.ContextTarget(0)), Effects.DealDamage(2, EffectTarget.ContextTarget(1)))
    private val spell = card("Visit resolution spell") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("first", Targets.Creature); target("second", Targets.Creature); effect = this@TargetVisitResolutionTest.effect }
    }
    init {
        listOf(body, spell).forEach(cardRegistry::register)
        for (kind in listOf("spell", "triggered", "activated")) {
            for (slot in listOf(0, 1)) {
                test("$kind resolves refreshed slot $slot while the same entity's other visit stays illegal") {
                    val g = scenario().withPlayers().withCardOnBattlefield(1, body.name).withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, spell.name).build()
                    val permanent = g.findPermanent(body.name)!!
                    val resolver = EngineServices(cardRegistry).stackResolver
                    val same = ChosenTarget.Permanent(permanent)
                    val targets = listOf(same, same)
                    val requirements = listOf(Targets.Creature, Targets.Creature)
                    val stacked = when (kind) {
                        "spell" -> resolver.castSpell(g.state, g.state.getHand(g.player1Id).single(), g.player1Id,
                            targets = targets, targetRequirements = requirements)
                        "triggered" -> resolver.putTriggeredAbility(g.state,
                            TriggeredAbilityOnStackComponent(g.findPermanent("Grizzly Bears")!!, body.name, g.player1Id, effect, "visit test"), targets, requirements)
                        else -> resolver.putActivatedAbility(g.state,
                            ActivatedAbilityOnStackComponent(g.findPermanent("Grizzly Bears")!!, body.name, g.player1Id, effect), targets, requirements)
                    }
                    stacked.error shouldBe null
                    val id = stacked.state.stack.last()
                    val before = stacked.state.getEntity(id)!!.get<TargetsComponent>()!!
                    val stamp = stacked.state.getEntity(permanent)!!.get<BattlefieldEntryTimestampComponent>()?.timestamp ?: 0L
                    val returned = stacked.state.updateEntity(permanent) { it.with(BattlefieldEntryTimestampComponent(stamp + 1)) }
                    val redirected = FixedDestinationRetarget.replaceSlot(returned, id, slot, same)
                    redirected.error shouldBe null
                    redirected.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
                    val after = redirected.state.getEntity(id)!!.get<TargetsComponent>()!!
                    after.visitAt(1 - slot) shouldBe before.visitAt(1 - slot)
                    after.isCurrentSlot(redirected.state, slot) shouldBe true
                    after.isCurrentSlot(redirected.state, 1 - slot) shouldBe false
                    val decoded = json.decodeFromString<TargetsComponent>(json.encodeToString(after))
                    decoded shouldBe after
                    val resolved = resolver.resolveTop(redirected.state)
                    resolved.error shouldBe null
                    resolved.state.getEntity(permanent)!!.get<DamageComponent>()!!.amount shouldBe slot + 1
                }
            }
        }
        test("a spell copy inherits the two different visits without recapturing either") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, body.name).withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, spell.name).build()
            val permanent = g.findPermanent(body.name)!!
            val resolver = EngineServices(cardRegistry).stackResolver
            val same = ChosenTarget.Permanent(permanent)
            val stacked = resolver.castSpell(g.state, g.state.getHand(g.player1Id).single(), g.player1Id,
                targets = listOf(same, same), targetRequirements = listOf(Targets.Creature, Targets.Creature))
            val id = stacked.state.stack.last()
            val stamp = stacked.state.getEntity(permanent)!!.get<BattlefieldEntryTimestampComponent>()?.timestamp ?: 0L
            val returned = stacked.state.updateEntity(permanent) { it.with(BattlefieldEntryTimestampComponent(stamp + 1)) }
            val redirected = FixedDestinationRetarget.replaceSlot(returned, id, 1, same)
            val original = redirected.state.getEntity(id)!!.get<TargetsComponent>()!!
            val copied = resolver.putSpellCopy(redirected.state, id)
            copied.error shouldBe null
            copied.state.getEntity(copied.state.stack.last())!!.get<TargetsComponent>()!!.targetVisits shouldBe original.targetVisits
            val resolved = resolver.resolveTop(copied.state)
            resolved.state.getEntity(permanent)!!.get<DamageComponent>()!!.amount shouldBe 2
        }
        test("queued mode keeps invalid first slot and live second slot across serialization") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, body.name).build()
            val permanent = g.findPermanent(body.name)!!
            val target = ChosenTarget.Permanent(permanent)
            val original = TargetsComponent.capture(g.state, listOf(target, target), listOf(Targets.Creature, Targets.Creature))
            val stamp = g.state.getEntity(permanent)!!.get<BattlefieldEntryTimestampComponent>()?.timestamp ?: 0L
            val returned = g.state.updateEntity(permanent) { it.with(BattlefieldEntryTimestampComponent(stamp + 1)) }
            val visits = TargetsComponent.capture(returned, listOf(target, target)).inheritingVisits(original, setOf(1))
            val entry = PreTargetedEffectEntry(effect, listOf(target, target), listOf(Targets.Creature, Targets.Creature), visits.targetVisits)
            val decoded = json.decodeFromString<PreTargetedEffectEntry>(json.encodeToString(entry))
            val result = com.wingedsheep.engine.handlers.effects.composite.processPreTargetedEffectQueue(
                returned, listOf(decoded), com.wingedsheep.engine.handlers.effects.composite.PreTargetedEffectContext(
                    g.player1Id, null, null, null, null),
                { state, _, context ->
                    context.positionalTarget(0) shouldBe null
                    context.positionalTarget(1) shouldBe target
                    context.pipeline.namedTargets["first"] shouldBe null
                    EffectResult.success(state)
                }, TargetValidator(), emptyList())
            result.error shouldBe null
        }
        test("explicit replacement copy recaptures the selected returned visit") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, body.name).withCardInHand(1, spell.name).build()
            val permanent = g.findPermanent(body.name)!!
            val same = ChosenTarget.Permanent(permanent)
            val resolver = EngineServices(cardRegistry).stackResolver
            val stacked = resolver.castSpell(g.state, g.state.getHand(g.player1Id).single(), g.player1Id,
                targets = listOf(same, same), targetRequirements = listOf(Targets.Creature, Targets.Creature))
            val id = stacked.state.stack.last()
            val stamp = stacked.state.getEntity(permanent)!!.get<BattlefieldEntryTimestampComponent>()?.timestamp ?: 0L
            val returned = stacked.state.updateEntity(permanent) { it.with(BattlefieldEntryTimestampComponent(stamp + 1)) }
            val copied = resolver.putSpellCopy(returned, id, targets = listOf(same, same))
            copied.error shouldBe null
            val component = copied.state.getEntity(copied.state.stack.last())!!.get<TargetsComponent>()!!
            component.isCurrentSlot(copied.state, 0) shouldBe true
            component.isCurrentSlot(copied.state, 1) shouldBe true
            resolver.resolveTop(copied.state).state.getEntity(permanent)!!.get<DamageComponent>()!!.amount shouldBe 3
        }
        test("real zone round trip keeps independent ObjectRefs through whole state serialization") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, body.name).withCardInHand(1, spell.name).build()
            val permanent = g.findPermanent(body.name)!!
            val same = ChosenTarget.Permanent(permanent)
            val resolver = EngineServices(cardRegistry).stackResolver
            val stacked = resolver.castSpell(g.state, g.state.getHand(g.player1Id).single(), g.player1Id,
                targets = listOf(same, same), targetRequirements = listOf(Targets.Creature, Targets.Creature))
            val id = stacked.state.stack.last()
            val oldRef = stacked.state.objectRef(permanent)!!
            val exiled = com.wingedsheep.engine.handlers.effects.ZoneTransitionService.moveToZone(
                stacked.state, permanent, com.wingedsheep.sdk.core.Zone.EXILE).state
            val returned = com.wingedsheep.engine.handlers.effects.ZoneTransitionService.moveToZone(
                exiled, permanent, com.wingedsheep.sdk.core.Zone.BATTLEFIELD).state
            returned.isCurrentObject(oldRef) shouldBe false
            val changed = FixedDestinationRetarget.replaceSlot(returned, id, 1, same)
            changed.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
            val roundTrip = json.decodeFromString<GameState>(json.encodeToString(changed.state))
            val slots = roundTrip.getEntity(id)!!.get<TargetsComponent>()!!
            slots.visitAt(0)!!.objectRef shouldBe oldRef
            slots.visitAt(1)!!.objectRef shouldBe roundTrip.objectRef(permanent)
            slots.isCurrentSlot(roundTrip, 0) shouldBe false
            slots.isCurrentSlot(roundTrip, 1) shouldBe true
            resolver.resolveTop(roundTrip).state.getEntity(permanent)!!.get<DamageComponent>()!!.amount shouldBe 2
        }
        test("explicit flat copy replacements rebind inherited modal slot slices") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, body.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, spell.name).build()
            val first = ChosenTarget.Permanent(g.findPermanent(body.name)!!)
            val second = ChosenTarget.Permanent(g.findPermanent("Grizzly Bears")!!)
            val resolver = EngineServices(cardRegistry).stackResolver
            val stacked = resolver.castSpell(g.state, g.state.getHand(g.player1Id).single(), g.player1Id,
                targets = listOf(first, first), targetRequirements = listOf(Targets.Creature, Targets.Creature))
            val id = stacked.state.stack.last()
            val modal = stacked.state.updateEntity(id) { container -> container.with(container.get<SpellOnStackComponent>()!!.copy(
                chosenModes = listOf(0, 1), modeTargetsOrdered = listOf(listOf(first), listOf(first)),
                modeTargetRequirements = mapOf(0 to listOf(Targets.Creature), 1 to listOf(Targets.Creature)))) }
            val copied = resolver.putSpellCopy(modal, id, targets = listOf(first, second))
            copied.error shouldBe null
            val result = copied.state.getEntity(copied.state.stack.last())!!
            result.get<SpellOnStackComponent>()!!.modeTargetsOrdered shouldBe listOf(listOf(first), listOf(second))
            result.get<TargetsComponent>()!!.targets shouldBe listOf(first, second)
        }
    }
}
