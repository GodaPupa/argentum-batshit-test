package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ContinuationHandler
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.state.components.identity.CantBeCopiedComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import kotlinx.serialization.json.Json
import io.kotest.matchers.types.shouldBeInstanceOf
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.SpellCopiedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.stack.StormCopyEffectExecutor
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.effects.StormCopyEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HistoricalSpellCopyTest : FunSpec({
    val registry = CardRegistry()
    val resolver = StackResolver(cardRegistry = registry)
    val executor = StormCopyEffectExecutor(registry)
    val effect = DrawCardsEffect(DynamicAmount.Fixed(1), EffectTarget.Controller)

    fun initial(player: EntityId, original: EntityId): GameState = GameState(
        activePlayerId = player, priorityPlayerId = player, turnOrder = listOf(player)
    ).withEntity(original, ComponentContainer.of(
        CardComponent(
            cardDefinitionId = "Copy Lifetime Probe", name = "Copy Lifetime Probe",
            manaCost = ManaCost.parse("{X}{U}"), typeLine = TypeLine.instant(),
            oracleText = "", ownerId = player, spellEffect = effect
        ),
        OwnerComponent(player), ControllerComponent(player),
        SpellOnStackComponent(casterId = player, xValue = 3)
    )).copy(stack = listOf(original)).initializeObjectIdentities()

    fun copy(state: GameState, player: EntityId, original: EntityId, count: Int) = executor.execute(
        state, StormCopyEffect(copyCount = count, spellEffect = effect, spellName = "Copy Lifetime Probe"),
        EffectContext(sourceId = original, controllerId = player)
    )

    fun copies(state: GameState) = state.stack.filter { state.getEntity(it)?.has<CopyOfComponent>() == true }

    test("control - zero copies leaves the ordinary spell unchanged") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val state = initial(player, original)
        val result = copy(state, player, original, 0)
        result.isSuccess shouldBe true
        result.state shouldBe state
        result.events shouldBe emptyList()
    }

    test("control - two live-source copies retain X and are spells without cast events") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val result = copy(initial(player, original), player, original, 2)
        result.isSuccess shouldBe true
        copies(result.state).size shouldBe 2
        copies(result.state).forEach {
            result.state.getEntity(it)!!.get<SpellOnStackComponent>()!!.xValue shouldBe 3
        }
        result.events.filterIsInstance<SpellCopiedEvent>().size shouldBe 2
        result.events.filterIsInstance<SpellCastEvent>().size shouldBe 0
    }

    test("countering the original before the copy trigger resolves must preserve two copies and X") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val countered = resolver.counterSpell(initial(player, original), original)
        countered.isSuccess shouldBe true
        countered.newState.stack shouldBe emptyList()
        val result = copy(countered.newState, player, original, 2)
        result.isSuccess shouldBe true
        copies(result.state).size shouldBe 2
        copies(result.state).forEach {
            result.state.getEntity(it)!!.get<SpellOnStackComponent>()!!.xValue shouldBe 3
        }
    }

    test("control - countering one already-created copy leaves original and sibling intact") {
        val player = EntityId.generate(); val original = EntityId.generate()
        val copied = copy(initial(player, original), player, original, 2)
        copied.isSuccess shouldBe true
        val ids = copies(copied.state)
        val countered = resolver.counterSpell(copied.state, ids.first())
        countered.isSuccess shouldBe true
        countered.newState.stack.contains(original) shouldBe true
        copies(countered.newState) shouldBe listOf(ids.last())
    }

    test("departure history survives serialization and preserves copied choices but not runtime markers") {
        val p = EntityId.generate(); val s = EntityId.generate()
        val before = initial(p, s).updateEntity(s) { it.with(TappedComponent) }
        val departed = resolver.counterSpell(before, s).newState
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), departed))
        val result = copy(restored, p, s, 1)
        result.isSuccess shouldBe true
        val entity = result.state.getEntity(copies(result.state).single())!!
        entity.get<SpellOnStackComponent>()!!.xValue shouldBe 3
        entity.has<TappedComponent>() shouldBe false
    }

    test("captured stack object selects the old spell after the same card is cast again") {
        val p = EntityId.generate(); val s = EntityId.generate()
        val first = initial(p, s)
        val ref = first.objectRef(s)!!
        val departed = resolver.counterSpell(first, s).newState
        val recast = departed.removeFromZone(ZoneKey(p, Zone.GRAVEYARD), s)
            .updateEntity(s) { it.with(SpellOnStackComponent(casterId = p, xValue = 9)) }.pushToStack(s)
        val result = executor.execute(recast, StormCopyEffect(1, effect, spellName = "old spell"),
            EffectContext(s, p, objectReferences = ObjectReferenceEnvironment(captured = true, origin = ref, source = ref)))
        result.isSuccess shouldBe true
        result.state.getEntity(copies(result.state).single())!!.get<SpellOnStackComponent>()!!.xValue shouldBe 3
        result.state.getEntity(s)!!.get<SpellOnStackComponent>()!!.xValue shouldBe 9
    }

    test("counter-to-hand also preserves history and new copy ownership follows its controller") {
        val p = EntityId.generate(); val other = EntityId.generate(); val s = EntityId.generate()
        val departed = resolver.counterSpellToHand(initial(p, s), s)
        departed.isSuccess shouldBe true
        val result = copy(departed.newState, other, s, 1)
        result.isSuccess shouldBe true
        val entity = result.state.getEntity(copies(result.state).single())!!
        entity.get<CardComponent>()!!.ownerId shouldBe other
        entity.get<SpellOnStackComponent>()!!.casterId shouldBe other
        result.state.getEntity(s)!!.get<CardComponent>()!!.ownerId shouldBe p
    }

    test("departure records last spell choices and cannot-be-copied restriction") {
        val p = EntityId.generate(); val s = EntityId.generate()
        val before = initial(p, s).updateEntity(s) { it.with(SpellOnStackComponent(casterId = p, xValue = 7)).with(CantBeCopiedComponent) }
        val departed = resolver.counterSpell(before, s).newState
        departed.departedSpellCopies.values.single().spell.xValue shouldBe 7
        val result = copy(departed, p, s, 2)
        result.isSuccess shouldBe true
        copies(result.state).size shouldBe 0
    }

    test("countered targeted spell forms two copies through serialized target continuations") {
        val p = EntityId.generate(); val s = EntityId.generate()
        val req = AnyTarget()
        val before = initial(p, s).withEntity(p, ComponentContainer.of(PlayerComponent("P"), LifeTotalComponent(20)))
            .updateEntity(s) { it.with(TargetsComponent(listOf(ChosenTarget.Player(p)), listOf(req))) }
        val departed = resolver.counterSpell(before, s).newState
        val paused = executor.execute(departed, StormCopyEffect(2, effect, listOf(req), "targeted"), EffectContext(s, p))
        paused.isPaused shouldBe true
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), paused.state))
        val handler = ContinuationHandler(EngineServices(registry))
        val one = handler.resume(restored, TargetsResponse(restored.pendingDecision!!.id, mapOf(0 to listOf(p))))
        one.isPaused shouldBe true
        val two = handler.resume(one.newState, TargetsResponse(one.newState.pendingDecision!!.id, mapOf(0 to listOf(p))))
        two.isSuccess shouldBe true
        copies(two.newState).size shouldBe 2
        copies(two.newState).forEach { id -> two.newState.getEntity(id)!!.get<SpellOnStackComponent>()!!.xValue shouldBe 3 }
    }

    test("countered modal spell retains its modes through the modal target continuation") {
        val p = EntityId.generate(); val s = EntityId.generate()
        val req = AnyTarget()
        val before = initial(p, s).withEntity(p, ComponentContainer.of(PlayerComponent("P"), LifeTotalComponent(20)))
            .updateEntity(s) { it.with(SpellOnStackComponent(casterId = p, xValue = 4, chosenModes = listOf(0, 1),
                modeTargetsOrdered = listOf(listOf(ChosenTarget.Player(p)), emptyList()),
                modeTargetRequirements = mapOf(0 to listOf(req), 1 to emptyList()))) }
        val departed = resolver.counterSpell(before, s).newState
        val paused = copy(departed, p, s, 1)
        paused.isPaused shouldBe true
        val frame = paused.state.continuationStack.last().shouldBeInstanceOf<Suspension>().answer.shouldBeInstanceOf<StormCopyModalTargetContinuation>()
        frame.sourceSnapshot!!.spell.xValue shouldBe 4
        val result = ContinuationHandler(EngineServices(registry)).resume(paused.state,
            TargetsResponse(paused.state.pendingDecision!!.id, mapOf(0 to listOf(p))))
        result.isSuccess shouldBe true
        val spell = result.newState.getEntity(copies(result.newState).single())!!.get<SpellOnStackComponent>()!!
        spell.chosenModes shouldBe listOf(0, 1)
        spell.modeTargetsOrdered shouldBe listOf(listOf(ChosenTarget.Player(p)), emptyList())
        spell.xValue shouldBe 4
    }
})
