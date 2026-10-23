package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.targeting.TargetSlotAddress
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Validated live trigger responses and the subsequent division pause; no resolver/card admission. */
class TargetSlotCaptureIntegrationTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        data class Fixture(val state: GameState, val player: EntityId, val a: EntityId, val b: EntityId)
        fun fixture(divided: Boolean = false): Fixture {
            val game = scenario().withPlayers("One", "Two")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Llanowar Elves").build()
            val source = requireNotNull(game.findPermanent("Hill Giant"))
            val a = requireNotNull(game.findPermanent("Grizzly Bears"))
            val b = requireNotNull(game.findPermanent("Llanowar Elves"))
            val frame = TriggeredAbilityContinuation(sourceId = source, sourceName = "Capture fixture",
                controllerId = game.player1Id, effect = if (divided) DividedDamageEffect(3) else Effects.GainLife(1),
                description = "Capture original target groups",
                targetRequirements = listOf(AnyTarget(minCount = 0, optional = true), AnyTarget(count = 2, minCount = 1)))
            val state = game.state.suspendForDecision(question = { id ->
                ChooseTargetsDecision(id, game.player1Id, "Choose fixture targets", DecisionContext(),
                    listOf(TargetRequirementInfo(0, "Optional first group", 0, 1),
                        TargetRequirementInfo(1, "One or two later targets", 1, 2)),
                    mapOf(0 to listOf(a, b), 1 to listOf(a, b)))
            }, answer = frame).state
            return Fixture(state, game.player1Id, a, b)
        }
        fun select(f: Fixture, groups: Map<Int, List<EntityId>>) = actionProcessor.process(f.state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(f.state.pendingDecision).id, groups))).result
        fun divide(state: GameState, player: EntityId, division: Map<EntityId, Int>) = actionProcessor.process(state,
            SubmitDecision(player, DistributionResponse(requireNotNull(state.pendingDecision).id, division))).result
        fun ability(state: GameState) = requireNotNull(state.getEntity(state.stack.last())?.get<TriggeredAbilityOnStackComponent>())

        test("CI01 validated trigger selection captures optional and partial groups before legacy alignment") {
            val f = fixture()
            val result = select(f, mapOf(1 to listOf(f.a)))
            result.error shouldBe null
            val plan = requireNotNull(ability(result.state).originalTargetSlots)
            plan.instances.map { it.slots.size } shouldBe listOf(0, 1)
            plan.slot(TargetSlotAddress(1, 0)).objectRef shouldBe f.state.objectRef(f.a)
            val legacy = requireNotNull(result.state.getEntity(result.state.stack.last())?.get<TargetsComponent>())
            legacy.targetRequirements.map { it.count } shouldBe listOf(1)
            result.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 1
        }
        test("CI02 division pause serialization retains selection visits even if current identity changes") {
            val f = fixture(divided = true)
            val paused = select(f, mapOf(1 to listOf(f.a, f.b)))
            paused.error shouldBe null
            (paused.state.pendingDecision is DistributeDecision) shouldBe true
            val oldRef = requireNotNull(paused.state.objectRef(f.a))
            val identities = paused.state.objectIdentities
            val changed = paused.state.copy(objectIdentities = identities +
                (f.a to identities.getValue(f.a).copy(generation = oldRef.generation + 100)))
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), changed))
            val result = divide(loaded, f.player, mapOf(f.a to 1, f.b to 2))
            result.error shouldBe null
            val plan = requireNotNull(ability(result.state).originalTargetSlots)
            plan.instances.map { it.slots.size } shouldBe listOf(0, 2)
            plan.slot(TargetSlotAddress(1, 0)).objectRef shouldBe oldRef
            plan.instances[1].slots.map { it.dividedAmount } shouldBe listOf(1, 2)
        }
        test("CI03 legacy division continuation stays untracked instead of recapturing current targets") {
            val f = fixture(divided = true)
            val paused = select(f, mapOf(1 to listOf(f.a, f.b)))
            val suspension = paused.state.peekContinuation() as Suspension
            val frame = (suspension.answer as TriggerDamageDistributionContinuation).copy(originalTargetSlots = null)
            val (_, popped) = paused.state.popContinuation()
            val legacy = popped.suspendForDecision(question = { id ->
                (suspension.question as DistributeDecision).copy(id = id)
            }, answer = frame).state
            val bytes = json.encodeToString(GameState.serializer(), legacy)
            bytes.contains("originalTargetSlots") shouldBe false
            val loaded = json.decodeFromString(GameState.serializer(), bytes)
            json.encodeToString(GameState.serializer(), loaded) shouldBe bytes
            val result = divide(loaded, f.player, mapOf(f.a to 1, f.b to 2))
            result.error shouldBe null
            ability(result.state).originalTargetSlots shouldBe null
        }
        test("CI04 invalid group response is rejected atomically before capture") {
            val f = fixture()
            val result = select(f, mapOf(1 to listOf(f.a), 7 to emptyList()))
            (result.error != null) shouldBe true
            result.state shouldBe f.state
            result.events shouldBe emptyList()
        }
        test("CI05 separate targeting instances retain repeated recipients separately") {
            val f = fixture()
            val result = select(f, mapOf(0 to listOf(f.a), 1 to listOf(f.a)))
            result.error shouldBe null
            val plan = requireNotNull(ability(result.state).originalTargetSlots)
            plan.instances.map { it.slots.size } shouldBe listOf(1, 1)
            plan.instances.map { it.slots.single().objectRef } shouldBe listOf(f.state.objectRef(f.a), f.state.objectRef(f.a))
        }
        test("CI06 serialized paused capture produces the same final stack state and events") {
            val f = fixture(divided = true)
            val paused = select(f, mapOf(1 to listOf(f.a, f.b)))
            val bytes = json.encodeToString(GameState.serializer(), paused.state)
            val loaded = json.decodeFromString(GameState.serializer(), bytes)
            json.encodeToString(GameState.serializer(), loaded) shouldBe bytes
            val direct = divide(paused.state, f.player, mapOf(f.a to 2, f.b to 1))
            val replay = divide(loaded, f.player, mapOf(f.a to 2, f.b to 1))
            direct.error shouldBe null
            replay.state shouldBe direct.state
            replay.events shouldBe direct.events
            val legacyComponent = ability(direct.state).copy(originalTargetSlots = null)
            json.encodeToString(TriggeredAbilityOnStackComponent.serializer(), legacyComponent)
                .contains("originalTargetSlots") shouldBe false
        }
    }
}
