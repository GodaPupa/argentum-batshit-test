package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Four prospective mechanical checks. No pilot selection, experimental seed or matchup. */
class EntryChoiceEventProvenanceTest : FunSpec({
    val vanilla = card("Entry Provenance Plain Choice") {
        manaCost = "{2}"; typeLine = "Creature — Test"; power = 2; toughness = 2
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    fun ability(id: String, from: Zone? = null, to: Zone, binding: TriggerBinding, life: Int) =
        TriggeredAbility(id = AbilityId(id), trigger = EventPattern.ZoneChangeEvent(from = from, to = to),
            binding = binding, effect = GainLifeEffect(life))
    val selfChoice = vanilla.copy(name = "Entry Provenance Self Choice", script = vanilla.script.copy(
        triggeredAbilities = listOf(ability("entry-self", to = Zone.BATTLEFIELD, binding = TriggerBinding.SELF, life = 1)),
    ))
    val entryWatcher = card("Entry Provenance Entry Watcher") { manaCost = "{2}"; typeLine = "Artifact" }
        .copy(script = CardScript(triggeredAbilities = listOf(
            ability("entry-watcher", to = Zone.BATTLEFIELD, binding = TriggerBinding.ANY, life = 1),
        )))
    val leaveWatcher = card("Entry Provenance Leave Watcher") { manaCost = "{2}"; typeLine = "Artifact" }
        .copy(script = CardScript(triggeredAbilities = listOf(
            ability("leave-watcher", from = Zone.BATTLEFIELD, to = Zone.GRAVEYARD, binding = TriggerBinding.ANY, life = 2),
        )))
    val registry = CardRegistry().also { r -> listOf(vanilla, selfChoice, entryWatcher, leaveWatcher).forEach(r::register) }
    val services = EngineServices(registry)
    val processor = ActionProcessor(registry)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    fun encode(state: GameState) = json.encodeToString(GameState.serializer(), state)
    fun restore(state: GameState) = json.decodeFromString(GameState.serializer(), encode(state))
    fun base(): GameState = GameInitializer(registry).initializeGame(GameConfig(
        players = listOf(PlayerConfig("Entry seat1", Deck(List(40) { vanilla.name })),
            PlayerConfig("Entry seat2", Deck(List(40) { vanilla.name }))),
        skipMulligans = true, startingPlayerIndex = 0, seed = 0x45504F434801L,
    )).state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
    fun put(state: GameState, definition: CardDefinition): Pair<GameState, EntityId> {
        val owner = state.turnOrder[0]
        val (id, next) = state.newEntity()
        val card = CardComponent(cardDefinitionId = definition.name, name = definition.name,
            manaCost = definition.manaCost, typeLine = definition.typeLine,
            baseStats = definition.creatureStats, ownerId = owner)
        return next.withEntity(id, ComponentContainer.of(card, OwnerComponent(owner), ControllerComponent(owner)))
            .addToZone(ZoneKey(owner, Zone.BATTLEFIELD), id) to id
    }
    fun copyEffect(id: EntityId, count: Int = 1) = CreateTokenCopyOfTargetEffect(
        EffectTarget.SpecificEntity(id), count = DynamicAmount.Fixed(count),
    )
    fun pause(state: GameState, effect: Effect): GameState {
        val result = services.effectExecutorRegistry.execute(state, effect, EffectContext(null, state.turnOrder[0]))
        result.error shouldBe null
        (result.pendingDecision is ChooseColorDecision) shouldBe true
        return result.state
    }
    fun answer(state: GameState, color: Color = Color.BLUE): ExecutionResult {
        val question = state.pendingDecision as ChooseColorDecision
        return processor.process(state, SubmitDecision(question.playerId, ColorChosenResponse(question.id, color))).result
            .also { it.error shouldBe null }
    }
    fun tokens(state: GameState) = state.getBattlefield().filter { state.getEntity(it)?.has<TokenComponent>() == true }
    fun triggerSources(state: GameState) = state.stack.map {
        requireNotNull(state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()).sourceId
    }
    fun settle(state: GameState): GameState {
        var current = state
        var actions = 0
        while (current.stack.isNotEmpty()) {
            check(++actions <= 20)
            current.pendingDecision shouldBe null
            val result = processor.process(current, PassPriority(requireNotNull(current.priorityPlayerId))).result
            result.error shouldBe null
            current = result.state
        }
        current.pendingDecision shouldBe null
        return current
    }
    fun life(state: GameState) = state.getEntity(state.turnOrder[0])!!.get<LifeTotalComponent>()!!.life

    test("EP01 a real as-enters token publishes its entry and resolves exactly one self ETB trigger") {
        val (state, target) = put(base(), selfChoice)
        val result = answer(pause(state, copyEffect(target)))
        val token = tokens(result.state).single()
        val entries = result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
        entries.map { it.entityId } shouldBe listOf(token)
        entries.single().entryTriggersAlreadyProcessed shouldBe true
        triggerSources(result.state) shouldBe listOf(token)
        life(settle(result.state)) shouldBe life(state) + 1
    }

    test("EP02 entry-time observer survives its automatic-tail removal and the distinct leave trigger is not suppressed") {
        val (s1, target) = put(base(), vanilla)
        val (s2, watcher) = put(s1, entryWatcher)
        val (state, leave) = put(s2, leaveWatcher)
        val result = answer(pause(state, CompositeEffect(listOf(
            copyEffect(target), SacrificeTargetEffect(EffectTarget.SpecificEntity(watcher)),
        ))))
        val token = tokens(result.state).single()
        val zoneEvents = result.events.filterIsInstance<ZoneChangeEvent>()
        zoneEvents.map { it.entityId } shouldBe listOf(token, watcher)
        zoneEvents.map { it.entryTriggersAlreadyProcessed } shouldBe listOf(true, false)
        zoneEvents.last().fromZone shouldBe Zone.BATTLEFIELD
        zoneEvents.last().toZone shouldBe Zone.GRAVEYARD
        (watcher in result.state.getBattlefield()) shouldBe false
        triggerSources(result.state).groupingBy { it }.eachCount() shouldBe mapOf(watcher to 1, leave to 1)
        life(settle(result.state)) shouldBe life(state) + 3
    }

    test("EP03 serialized pending token tail and repeated answers preserve exact entry records, state and two ETB effects") {
        val (state, target) = put(base(), selfChoice)
        val paused = pause(state, copyEffect(target, 2))
        val first = answer(paused)
        val replayFirst = answer(restore(paused))
        encode(replayFirst.state) shouldBe encode(first.state)
        replayFirst.events shouldBe first.events
        (first.state.pendingDecision is ChooseColorDecision) shouldBe true
        val second = answer(first.state, Color.RED)
        val replaySecond = answer(restore(replayFirst.state), Color.RED)
        encode(replaySecond.state) shouldBe encode(second.state)
        replaySecond.events shouldBe second.events
        val ids = tokens(second.state)
        ids.size shouldBe 2
        val entries = (first.events + second.events).filterIsInstance<ZoneChangeEvent>()
            .filter { it.toZone == Zone.BATTLEFIELD }
        entries.map { it.entityId } shouldBe ids
        entries.map { it.entryTriggersAlreadyProcessed } shouldBe listOf(true, true)
        triggerSources(second.state).groupingBy { it }.eachCount() shouldBe ids.associateWith { 1 }
        life(settle(second.state)) shouldBe life(state) + 2
    }

    test("EP04 serialized provenance skips only the marked occurrence and preserves identical unmarked occurrences") {
        val (state, target) = put(base(), selfChoice)
        val result = answer(pause(state, copyEffect(target)))
        val marked = result.events.filterIsInstance<ZoneChangeEvent>().single()
        val raw = marked.copy(entryTriggersAlreadyProcessed = false)
        val historical = json.encodeToJsonElement(GameEvent.serializer(), raw)
        historical.jsonObject.containsKey("entryTriggersAlreadyProcessed") shouldBe false
        val decodedRaw = json.decodeFromString(GameEvent.serializer(), historical.toString()) as ZoneChangeEvent
        val markedBytes = json.encodeToString(GameEvent.serializer(), marked)
        val decodedMarked = json.decodeFromString(GameEvent.serializer(), markedBytes) as ZoneChangeEvent
        decodedMarked shouldBe marked
        decodedRaw shouldBe raw
        val detector = services.triggerDetector
        detector.detectTriggers(result.state, listOf(raw, decodedRaw)).size shouldBe 2
        detector.detectTriggers(result.state, listOf(decodedMarked, decodedRaw)).size shouldBe 1
        detector.detectTriggers(result.state, listOf(decodedRaw, decodedMarked)).size shouldBe 1
        detector.detectTriggers(result.state, listOf(decodedMarked)).size shouldBe 0

        // Synthetic partial event input isolates the detector's reference-context contract;
        // this is not presented as a complete legal game history. A later already-processed
        // entry remains context for the earlier cause even though it cannot trigger again.
        val (contextState, observer) = put(result.state, entryWatcher)
        val currentObserver = requireNotNull(contextState.objectRef(observer))
        val priorObserver = currentObserver.copy(generation = currentObserver.generation - 1)
        val contextOnlyEntry = decodedMarked.copy(
            entityId = observer, entityName = entryWatcher.name,
            oldObject = priorObserver, newObject = currentObserver,
        )
        val contextual = detector.detectTriggers(contextState, listOf(decodedRaw, contextOnlyEntry))
        contextual.size shouldBe 2
        contextual.single { it.sourceId == observer }.objectReferences.source shouldBe priorObserver
        contextual.single { it.sourceId == observer }.objectReferences.origin shouldBe priorObserver
        detector.detectTriggers(contextState, listOf(contextOnlyEntry)).size shouldBe 0
        shouldThrow<IllegalArgumentException> {
            marked.copy(toZone = Zone.GRAVEYARD)
        }
    }
})
