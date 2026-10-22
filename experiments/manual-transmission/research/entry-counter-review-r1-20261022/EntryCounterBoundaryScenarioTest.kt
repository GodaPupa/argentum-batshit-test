package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Global entry replacement sources exist before the simultaneous entry event. */
class EntryCounterBoundaryScenarioTest : FunSpec({
    val plain = card("Counter Boundary Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
    }
    val provider = card("Counter Boundary Provider") {
        manaCost = "{0}"; typeLine = "Creature — Elf"; power = 2; toughness = 3
        replacementEffect(EntersWithCounters(count = 2, otherOnly = true))
    }
    val graveProvider = card("Counter Boundary Grave Provider") {
        manaCost = "{0}"; typeLine = "Creature — Spirit"; power = 6; toughness = 6
        replacementEffect(EntersWithDynamicCounters(count = DynamicAmount.Fixed(2), otherOnly = true,
            activeZones = setOf(Zone.GRAVEYARD)))
    }
    val copier = card("Counter Boundary Copier") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(plain, provider, graveProvider, copier))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun counters(d: GameTestDriver, id: EntityId) = d.state.getEntity(id).shouldNotBeNull()
        .get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    fun move(d: GameTestDriver, cards: List<EntityId>, faceDown: FaceDownMode? = null) {
        val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            MoveCollectionEffect("entries", CardDestination.ToZone(Zone.BATTLEFIELD), faceDown = faceDown),
            EffectContext(sourceId = null, controllerId = d.player1,
                pipeline = PipelineState(storedCollections = mapOf("entries" to cards))))
        result.error shouldBe null
        result.isPaused shouldBe false
        d.replaceState(result.state)
        cards.all { it in d.state.getBattlefield() } shouldBe true
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }
    for (providerFirst in listOf(false, true)) {
        test("a battlefield counter source entering in the same batch grants no counters; providerFirst=$providerFirst") {
            val d = fixture()
            val source = d.putCardInGraveyard(d.player1, provider.name)
            val body = d.putCardInGraveyard(d.player1, plain.name)
            move(d, if (providerFirst) listOf(source, body) else listOf(body, source))
            counters(d, body) shouldBe 0
            counters(d, source) shouldBe 0
            d.state.projectedState.getPower(body) shouldBe 2
        }
        test("a graveyard counter source leaving in the same batch still grants counters; providerFirst=$providerFirst") {
            val d = fixture()
            val source = d.putCardInGraveyard(d.player1, graveProvider.name)
            val body = d.putCardInGraveyard(d.player1, plain.name)
            move(d, if (providerFirst) listOf(source, body) else listOf(body, source))
            counters(d, body) shouldBe 2
            counters(d, source) shouldBe 0
            d.state.projectedState.getPower(body) shouldBe 4
        }
    }
    for (direct in listOf(false, true)) {
        for (token in listOf(false, true)) {
            test("copied public characteristics still receive external entry counters; direct=$direct, token=$token") {
                val d = fixture()
                d.putCreatureOnBattlefield(d.player1, provider.name)
                val target = if (token) {
                    val created = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                        CreateTokenEffect(power = 2, toughness = 3, colors = emptySet(), creatureTypes = setOf("Bear")),
                        EffectContext(sourceId = null, controllerId = d.player2))
                    created.error shouldBe null
                    d.replaceState(created.state)
                    created.events.filterIsInstance<ZoneChangeEvent>().single { it.toZone == Zone.BATTLEFIELD }.entityId
                } else {
                    val hidden = d.putCreatureOnBattlefield(d.player2, plain.name)
                    d.replaceState(d.state.updateEntity(hidden) { it.with(FaceDownComponent) })
                    hidden
                }
                val entrant = if (direct) d.putCardInGraveyard(d.player1, copier.name)
                    else d.putCardInHand(d.player1, copier.name)
                if (direct) {
                    val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                        MoveToZoneEffect(EffectTarget.SpecificEntity(entrant), Zone.BATTLEFIELD, fromZone = Zone.GRAVEYARD),
                        EffectContext(sourceId = null, controllerId = d.player1))
                    result.error shouldBe null
                    result.isPaused shouldBe true
                    d.replaceState(result.state)
                } else {
                    d.castSpell(d.player1, entrant).error shouldBe null
                    d.bothPass().error shouldBe null
                }
                d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>().options.contains(target) shouldBe true
                restore(d)
                d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
                d.pendingDecision shouldBe null
                counters(d, entrant) shouldBe 2
                d.state.projectedState.getPower(entrant) shouldBe 4
                d.state.getEntity(entrant).shouldNotBeNull().has<FaceDownComponent>() shouldBe false
            }
        }
        test("ordinary printed entry already receives external counters; direct=$direct") {
            val d = fixture()
            d.putCreatureOnBattlefield(d.player1, provider.name)
            val entrant = if (direct) d.putCardInGraveyard(d.player1, plain.name)
                else d.putCardInHand(d.player1, plain.name)
            if (direct) move(d, listOf(entrant)) else {
                d.castSpell(d.player1, entrant).error shouldBe null
                d.bothPass().error shouldBe null
            }
            counters(d, entrant) shouldBe 2
            d.state.projectedState.getPower(entrant) shouldBe 4
        }
        test("face-down entry receives external counters without its concealed text; collection=$direct") {
            val d = fixture()
            d.putCreatureOnBattlefield(d.player1, provider.name)
            val entrant = d.putCardInGraveyard(d.player1, graveProvider.name)
            if (direct) move(d, listOf(entrant), FaceDownMode.MANIFEST) else {
                val result = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                    MoveToZoneEffect(EffectTarget.SpecificEntity(entrant), Zone.BATTLEFIELD,
                        fromZone = Zone.GRAVEYARD, faceDown = FaceDownMode.MANIFEST),
                    EffectContext(sourceId = null, controllerId = d.player1))
                result.error shouldBe null
                result.isPaused shouldBe false
                d.replaceState(result.state)
            }
            counters(d, entrant) shouldBe 2
            d.state.projectedState.getPower(entrant) shouldBe 4
            d.state.getEntity(entrant).shouldNotBeNull().has<FaceDownComponent>() shouldBe true
        }
    }
})
