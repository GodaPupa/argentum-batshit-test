package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** CR 608.2f: one synchronous copy action over a collection is one entry instruction. */
class ForEachCopyEntryBatchScenarioTest : FunSpec({
    val body = card("Copy Batch Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
    }
    val observer = card("Copy Batch Observer") {
        manaCost = "{0}"; typeLine = "Enchantment"
        triggeredAbility { trigger = Triggers.OtherCreatureEnters; effect = Effects.GainLife(3) }
    }
    val artifact = card("Copy Batch Artifact") { manaCost = "{0}"; typeLine = "Artifact" }
    val restricted = card("Copy Batch Restricted") {
        manaCost = "{0}"; typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            triggerRestriction = Conditions.ControlArtifact
            effect = Effects.GainLife(3)
        }
    }
    val batched = card("Copy Batch Group Observer") {
        manaCost = "{0}"; typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.OneOrMorePermanentsEnter(GameObjectFilter.Creature)
            effect = Effects.GainLife(3)
        }
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(body, observer, artifact, restricted, batched))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun execute(d: GameTestDriver, originals: List<EntityId>, separate: Boolean = false): Pair<GameState, List<GameEvent>> {
        val effect = if (separate) CompositeEffect(originals.map {
            CreateTokenCopyOfTargetEffect(target = EffectTarget.SpecificEntity(it))
        }) else ForEachInCollectionEffect("copies", CreateTokenCopyOfTargetEffect(target = EffectTarget.Self))
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, effect,
            EffectContext(sourceId = null, controllerId = d.player1,
                pipeline = PipelineState(storedCollections = mapOf("copies" to originals))))
        result.isSuccess shouldBe true
        result.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 2
        val stateRaw = json.encodeToString(GameState.serializer(), result.state)
        val eventRaw = json.encodeToString(ListSerializer(GameEvent.serializer()), result.events)
        val state = json.decodeFromString(GameState.serializer(), stateRaw)
        val events = json.decodeFromString(ListSerializer(GameEvent.serializer()), eventRaw)
        json.encodeToString(GameState.serializer(), state) shouldBe stateRaw
        json.encodeToString(ListSerializer(GameEvent.serializer()), events) shouldBe eventRaw
        return state to events
    }
    for (providerFirst in listOf(false, true)) {
        test("a copied coentrant observer sees the whole copy instruction; providerFirst=$providerFirst") {
            val d = fixture()
            val names = if (providerFirst) listOf(observer.name, body.name) else listOf(body.name, observer.name)
            val originals = names.map { d.putPermanentOnBattlefield(d.player2, it) }
            val (state, events) = execute(d, originals)
            val triggers = EngineServices(d.cardRegistry).triggerDetector.detectTriggers(state, events)
                .filter { it.controllerId == d.player1 }
            triggers.size shouldBe 1
        }
        test("a copied coentrant artifact satisfies the entry restriction; providerFirst=$providerFirst") {
            val d = fixture(); val id = d.putPermanentOnBattlefield(d.player1, restricted.name)
            val names = if (providerFirst) listOf(artifact.name, body.name) else listOf(body.name, artifact.name)
            val originals = names.map { d.putPermanentOnBattlefield(d.player2, it) }
            val (state, events) = execute(d, originals)
            val triggers = EngineServices(d.cardRegistry).triggerDetector.detectTriggers(state, events)
                .filter { it.sourceId == id }
            triggers.size shouldBe 1
        }
    }
    for (separate in listOf(false, true)) {
        test("copy batch grouping preserves separate composite instructions; separate=$separate") {
            val d = fixture(); val id = d.putPermanentOnBattlefield(d.player1, batched.name)
            val originals = List(2) { d.putPermanentOnBattlefield(d.player2, body.name) }
            val (state, events) = execute(d, originals, separate)
            val triggers = EngineServices(d.cardRegistry).triggerDetector.detectTriggers(state, events)
                .filter { it.sourceId == id }
            triggers.size shouldBe if (separate) 2 else 1
        }
    }
})
