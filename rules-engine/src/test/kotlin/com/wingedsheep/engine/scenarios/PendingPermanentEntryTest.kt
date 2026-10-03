package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.handlers.effects.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PendingPermanentEntryTest : ScenarioTestBase() {
    private val chooser = card("Entry Choice Creature") {
        manaCost = "{W}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    private val aura = card("Entry Choice Aura") {
        manaCost = "{W}"; typeLine = "Enchantment — Aura"; auraTarget = Targets.Creature
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    private fun answer(state: GameState, color: Color = Color.WHITE): ExecutionResult =
        ContinuationHandler(EngineServices(cardRegistry)).resume(state, ColorChosenResponse(state.pendingDecision!!.id, color))
    private fun restored(state: GameState): GameState {
        val json = Json { allowStructuredMapKeys = true; serializersModule = engineSerializersModule }
        return json.decodeFromString<GameState>(json.encodeToString(state))
    }
    init {
        cardRegistry.register(chooser); cardRegistry.register(aura)
        test("attached entry remains outside battlefield and unattached until choice then emits one entry") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInGraveyard(1, aura.name).build()
            val host = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, aura.name).single()
            val owner = g.state.projectedState.getController(host)!!
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                ReturnSelfToBattlefieldAttachedEffect(EffectTarget.SpecificEntity(host)), EffectContext(sourceId = id, controllerId = owner))
            (id in paused.state.getBattlefield()) shouldBe false
            paused.state.getEntity(id)?.get<AttachedToComponent>() shouldBe null
            paused.events.filterIsInstance<ZoneChangeEvent>().size shouldBe 0
            val result = answer(restored(paused.state))
            result.error shouldBe null
            (id in result.state.getBattlefield()) shouldBe true
            result.state.getEntity(id)?.get<AttachedToComponent>()?.targetId shouldBe host
            result.state.getEntity(id)?.chosenColor() shouldBe Color.WHITE
            result.state.getEntity(host)?.get<AttachmentsComponent>()?.attachedIds?.count { it == id } shouldBe 1
            result.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == id && it.toZone == Zone.BATTLEFIELD } shouldBe 1
        }
        test("whole collection waits for both serialized choices and resumes each object exactly once") {
            val g = scenario().withPlayers("A", "B").withCardInGraveyard(1, chooser.name)
                .withCardInGraveyard(1, chooser.name).build()
            val ids = g.findCardsInGraveyard(1, chooser.name); val owner = g.state.turnOrder.first()
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                MoveCollectionEffect("entries", CardDestination.ToZone(Zone.BATTLEFIELD)),
                EffectContext(sourceId = null, controllerId = owner, pipeline = PipelineState(storedCollections = mapOf("entries" to ids))))
            val first = answer(restored(paused.state), Color.RED)
            first.error shouldBe null
            ids.any { it in first.state.getBattlefield() } shouldBe false
            first.events.filterIsInstance<ZoneChangeEvent>().size shouldBe 0
            val last = answer(restored(first.state), Color.BLUE)
            last.error shouldBe null
            last.state.pendingDecision shouldBe null
            ids.all { it in last.state.getBattlefield() } shouldBe true
            last.state.getEntity(ids[0])?.chosenColor() shouldBe Color.RED
            last.state.getEntity(ids[1])?.chosenColor() shouldBe Color.BLUE
            last.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 2
        }
        test("stale source visit is not moved on resumption") {
            val g = scenario().withPlayers("A", "B").withCardInGraveyard(1, chooser.name).build()
            val id = g.findCardsInGraveyard(1, chooser.name).single(); val owner = g.state.turnOrder.first()
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                MoveToZoneEffect(EffectTarget.SpecificEntity(id), Zone.BATTLEFIELD), EffectContext(sourceId = null, controllerId = owner))
            val changed = ZoneTransitionService.moveToZone(paused.state, id, Zone.EXILE).state
            val result = answer(restored(changed))
            result.error shouldBe null
            (id in result.state.getBattlefield()) shouldBe false
            result.state.getZone(ZoneKey(owner, Zone.EXILE)).contains(id) shouldBe true
        }
        test("stale fixed attachment host cancels return without selecting a replacement host") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInGraveyard(1, aura.name).build()
            val host = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, aura.name).single()
            val owner = g.state.projectedState.getController(host)!!
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                ReturnSelfToBattlefieldAttachedEffect(EffectTarget.SpecificEntity(host)), EffectContext(sourceId = id, controllerId = owner))
            val changed = ZoneTransitionService.moveToZone(paused.state, host, Zone.EXILE).state
            val result = answer(restored(changed))
            result.error shouldBe null
            result.state.pendingDecision shouldBe null
            (id in result.state.getBattlefield()) shouldBe false
        }
        test("generic Aura move chooses host and color before entry") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInGraveyard(1, aura.name).build()
            val host = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, aura.name).single()
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                MoveToZoneEffect(EffectTarget.SpecificEntity(id), Zone.BATTLEFIELD), EffectContext(sourceId = null, controllerId = g.state.turnOrder.first()))
            (paused.state.pendingDecision is SelectCardsDecision) shouldBe true
            val selected = ContinuationHandler(EngineServices(cardRegistry)).resume(restored(paused.state),
                CardsSelectedResponse(paused.state.pendingDecision!!.id, listOf(host)))
            (selected.state.pendingDecision is ChooseColorDecision) shouldBe true
            (id in selected.state.getBattlefield()) shouldBe false
            selected.state.getEntity(id)?.get<AttachedToComponent>() shouldBe null
            val done = answer(restored(selected.state))
            done.error shouldBe null
            done.state.getEntity(id)?.get<AttachedToComponent>()?.targetId shouldBe host
            done.state.getEntity(id)?.chosenColor() shouldBe Color.WHITE
            done.state.getEntity(host)?.get<AttachmentsComponent>()?.attachedIds?.count { it == id } shouldBe 1
        }
        test("printed ETB is absent during choice and detected once after entry") {
            val etb = card("Entry Choice ETB") {
                manaCost = "{W}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
                replacementEffect(EntersWithChoice(ChoiceType.COLOR))
                triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(1) }
            }
            cardRegistry.register(etb)
            val g = scenario().withPlayers("A", "B").withCardInGraveyard(1, etb.name).build()
            val id = g.findCardsInGraveyard(1, etb.name).single()
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                MoveToZoneEffect(EffectTarget.SpecificEntity(id), Zone.BATTLEFIELD), EffectContext(sourceId = null, controllerId = g.state.turnOrder.first()))
            paused.state.stack.size shouldBe 0
            val done = answer(restored(paused.state))
            done.error shouldBe null
            done.state.stack.size shouldBe 1
            val repeat = ContinuationHandler(EngineServices(cardRegistry)).resume(done.state,
                ColorChosenResponse(paused.state.pendingDecision!!.id, Color.BLUE))
            (repeat.error != null) shouldBe true
            repeat.state.stack.size shouldBe 1
        }
        test("entry keeps its announced controller when attachment host changes control during the pause") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInGraveyard(1, aura.name).build()
            val host = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, aura.name).single()
            val owner = g.state.projectedState.getController(host)!!
            val paused = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                ReturnSelfToBattlefieldAttachedEffect(EffectTarget.SpecificEntity(host)), EffectContext(sourceId = id, controllerId = owner))
            val changed = paused.state.updateEntity(host) { it.with(
                com.wingedsheep.engine.state.components.identity.ControllerComponent(g.state.turnOrder.last())) }
            val done = answer(restored(changed))
            done.error shouldBe null
            done.state.projectedState.getController(id) shouldBe owner
            done.state.getEntity(id)?.get<AttachedToComponent>()?.targetId shouldBe host
        }
    }
}
