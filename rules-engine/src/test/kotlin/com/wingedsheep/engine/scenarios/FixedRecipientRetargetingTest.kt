package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class FixedRecipientRetargetingTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        data class Fixture(val state: GameState, val stack: EntityId, val source: EntityId,
            val old: EntityId, val recipient: EntityId, val controller: EntityId, val chooser: EntityId, val plan: CurrentTargetPlan)
        fun fixture(groups: List<List<Int>> = listOf(listOf(0)),
            requirements: List<TargetRequirement> = groups.map { AnyTarget(count = it.size.coerceAtLeast(1), optional = it.isEmpty()) },
            kind: String = "trigger"): Fixture {
            val game = scenario().withPlayers("One", "Two")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(2, "Craw Wurm").build()
            val source = game.findPermanent("Grizzly Bears")!!
            val old = game.findPermanent("Hill Giant")!!
            val recipient = game.findPermanent("Craw Wurm")!!
            val ids = listOf(old, recipient, source)
            val selections = TargetSlotPlan(groups.map { group -> TargetInstanceSelection(group.map { n ->
                TargetSlot(ChosenTarget.Permanent(ids[n]), game.state.objectRef(ids[n]))
            }) })
            val plan = CurrentTargetPlan(selections)
            val origin = game.state.objectRef(source)!!
            val refs = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin)
            val resolver = StackResolver(cardRegistry)
            val effect = Effects.Destroy(EffectTarget.ContextTarget(0))
            val queued = if (kind == "activation") resolver.putActivatedAbility(game.state,
                ActivatedAbilityOnStackComponent(source, "Source", game.player1Id, effect,
                    objectReferences = refs, originalTargetSlots = selections), plan.targets, requirements, currentTargetPlan = plan)
            else resolver.putTriggeredAbility(game.state,
                TriggeredAbilityOnStackComponent(source, "Source", game.player1Id, effect, "Retarget fixture",
                    objectReferences = refs, originalTargetSlots = selections), plan.targets, requirements, currentTargetPlan = plan)
            queued.error shouldBe null
            return Fixture(queued.state, queued.state.stack.last(), source, old, recipient, game.player1Id, game.player2Id, plan)
        }
        fun offer(f: Fixture) = FixedRecipientRetargeting.offer(f.state, f.stack, ChosenTarget.Permanent(f.recipient), f.chooser)
        fun changed(f: Fixture, address: TargetSlotAddress = TargetSlotAddress(0, 0)): EffectResult =
            FixedRecipientRetargeting.apply(f.state, requireNotNull(offer(f)), address)
        fun keyword(f: Fixture, id: EntityId, word: String): Fixture = f.copy(state = f.state.addFloatingEffect(
            Layer.ABILITY, SerializableModification.GrantKeyword(word), setOf(id), Duration.EndOfTurn,
            EffectContext(sourceId = f.source, controllerId = f.controller)))

        test("FR01 change one occurrence and preserve original provenance") {
            val f = fixture(); val r = changed(f)
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient))
            r.state.getEntity(f.stack)!!.get<TriggeredAbilityOnStackComponent>()!!.originalTargetSlots shouldBe f.plan.selections
            r.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
            r.events.filterIsInstance<BecomesTargetEvent>().single().controllerId shouldBe f.controller
            r.events.filterIsInstance<TargetsChosenEvent>().single().chooserId shouldBe f.chooser
        }
        test("FR02 repeated entity occurrences retain distinct addresses and omitted group") {
            val f = fixture(listOf(emptyList(), listOf(0), listOf(0)))
            offer(f)!!.addresses shouldBe listOf(TargetSlotAddress(1, 0), TargetSlotAddress(2, 0))
            val r = changed(f, TargetSlotAddress(2, 0)); val plan = r.state.getEntity(f.stack)!!.currentTargetPlan()!!
            plan.selections.instances[0].slots shouldBe emptyList()
            plan.selections.instances[1] shouldBe f.plan.selections.instances[1]
            plan.targets shouldBe listOf(ChosenTarget.Permanent(f.old), ChosenTarget.Permanent(f.recipient))
        }
        test("FR03 duplicate within an instance cannot be selected") {
            val f = fixture(listOf(listOf(0, 1)))
            offer(f) shouldBe null
        }
        test("FR04 duplicate across instances changes without a second becomes-target event") {
            val f = fixture(listOf(listOf(0), listOf(1))); val r = changed(f)
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe List(2) { ChosenTarget.Permanent(f.recipient) }
            r.events.filterIsInstance<BecomesTargetEvent>() shouldBe emptyList()
            r.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 1
        }
        test("FR05 no-op has no menu or events") {
            val f = fixture(listOf(listOf(1))); offer(f) shouldBe null
        }
        test("FR06 no authoritative plan fails closed") {
            val f = fixture(); val s = f.state.updateEntity(f.stack) { it.without<CurrentTargetPlanComponent>()
                .with(it.get<TargetsComponent>()!!.copy(currentTargetPlan = null)) }
            offer(f.copy(state = s)) shouldBe null
        }
        test("FR07 stale serialized expected plan cannot overwrite a newer change") {
            val f = fixture(listOf(listOf(0), listOf(0)))
            val o = json.decodeFromString(FixedRecipientOffer.serializer(), json.encodeToString(FixedRecipientOffer.serializer(), offer(f)!!))
            val first = FixedRecipientRetargeting.apply(f.state, o, TargetSlotAddress(0, 0))
            val stale = FixedRecipientRetargeting.apply(first.state, o, TargetSlotAddress(1, 0))
            stale.state shouldBe first.state; stale.events shouldBe emptyList()
        }
        test("FR08 stale recipient visit rejects without mutating state") {
            val f = fixture(); val o = offer(f)!!
            val identity = f.state.objectIdentities.getValue(f.recipient)
            val state = f.state.copy(objectIdentities = f.state.objectIdentities + (f.recipient to identity.copy(generation = identity.generation + 1)))
            val r = FixedRecipientRetargeting.apply(state, o, o.addresses.single())
            r.state shouldBe state; r.events shouldBe emptyList()
        }
        test("FR09 stale stack visit rejects without mutating state") {
            val f = fixture(); val o = offer(f)!!; val identity = f.state.objectIdentities.getValue(f.stack)
            val state = f.state.copy(objectIdentities = f.state.objectIdentities + (f.stack to identity.copy(generation = identity.generation + 1)))
            FixedRecipientRetargeting.apply(state, o, o.addresses.single()).state shouldBe state
        }
        test("FR10 unchanged illegal target remains untouched") {
            val base = fixture(listOf(listOf(0), listOf(2)))
            val f = keyword(base, base.source, "SHROUD")
            val r = changed(f)
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.selections.instances[1] shouldBe f.plan.selections.instances[1]
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.targets.first() shouldBe ChosenTarget.Permanent(f.recipient)
        }
        test("FR11 original controller decides opponent hexproof") {
            val base = fixture(); val f = keyword(base, base.recipient, "HEXPROOF")
            offer(f) shouldBe null // chooser owns recipient; original ability controller does not
        }
        test("FR12 final same-controller relation rejects replacement") {
            val f = fixture(listOf(listOf(0, 2)), listOf(TargetCreature(count = 2, sameController = true)))
            offer(f) shouldBe null
        }
        test("FR13 dynamic original count is not recalculated") {
            val f = fixture(requirements = listOf(TargetObject(filter = com.wingedsheep.sdk.scripting.filters.unified.TargetFilter.Creature,
                dynamicMaxCount = DynamicAmount.Fixed(0))))
            changed(f).state.getEntity(f.stack)!!.currentTargetPlan()!!.targets shouldBe listOf(ChosenTarget.Permanent(f.recipient))
        }
        test("FR14 selected division is preserved") {
            val f = fixture(); val plan = f.plan.copy(selections = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(
                f.plan.slots.single().copy(dividedAmount = 7))))))
            val s = f.state.updateEntity(f.stack) { it.with(CurrentTargetPlanComponent(plan)) }
            val r = changed(f.copy(state = s, plan = plan))
            r.state.getEntity(f.stack)!!.currentTargetPlan()!!.slots.single().dividedAmount shouldBe 7
            r.state.getEntity(f.stack)!!.get<TriggeredAbilityOnStackComponent>()!!.damageDistribution shouldBe mapOf(f.recipient to 7)
        }
        test("FR15 missing stack membership rejects stale offer") {
            val f = fixture(); val o = offer(f)!!; val s = f.state.copy(stack = emptyList())
            FixedRecipientRetargeting.apply(s, o, o.addresses.single()).state shouldBe s
        }
        test("FR16 newly illegal recipient is rechecked on apply") {
            val f = fixture(); val o = offer(f)!!; val s = keyword(f, f.recipient, "SHROUD").state
            FixedRecipientRetargeting.apply(s, o, o.addresses.single()).state shouldBe s
        }
        for (kind in listOf("trigger", "activation")) for (visit in listOf("live", "removed", "returned")) {
            for (word in listOf("PROTECTION_FROM_RED", "PROTECTION_FROM_SUBTYPE_ZOMBIE",
                "PROTECTION_FROM_CARDTYPE_ARTIFACT", "CANT_BE_TARGETED_BY_CARDTYPE_ARTIFACT_SOURCE_ABILITIES")) {
                test("FR source $kind $visit $word") {
                    val base = fixture(kind = kind)
                    val context = EffectContext(sourceId = base.source, controllerId = base.controller)
                    var s = base.state.addFloatingEffect(Layer.COLOR, SerializableModification.ChangeColor(setOf("RED")), setOf(base.source), Duration.EndOfTurn, context)
                        .addFloatingEffect(Layer.TYPE, SerializableModification.AddSubtype("Zombie"), setOf(base.source), Duration.EndOfTurn, context)
                        .addFloatingEffect(Layer.TYPE, SerializableModification.AddType("ARTIFACT"), setOf(base.source), Duration.EndOfTurn, context)
                    if (visit != "live") {
                        val snapshot = EntitySnapshot.fromProjection(base.source, s)
                        s = s.updateEntity(base.stack) { c -> if (kind == "trigger") c.with(c.get<TriggeredAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = snapshot))
                            else c.with(c.get<ActivatedAbilityOnStackComponent>()!!.copy(lastKnownSourceSnapshot = snapshot)) }
                        if (visit == "removed") s = s.removeEntity(base.source)
                        else {
                            val origin = s.logicalZone(base.source)!!; val grave = ZoneKey(base.controller, Zone.GRAVEYARD)
                            s = s.removeFromZone(origin, base.source).addToZone(grave, base.source)
                                .removeFromZone(grave, base.source).addToZone(origin, base.source).copy(floatingEffects = emptyList())
                        }
                    }
                    val f = keyword(base.copy(state = s), base.recipient, word)
                    val roundTrip = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), f.state))
                    offer(f.copy(state = roundTrip)) shouldBe null
                }
            }
        }
    }
}
