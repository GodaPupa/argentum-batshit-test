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

/** Three independent diagnostic fixtures; original four regression assertions unchanged. */
class EntryChoiceR4PostOrderingDiagnosticTest : FunSpec({
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

    test("R4 post-order EP02: preserve both triggers through actual controller ordering and resolution") {
        val (s1, target) = put(base(), vanilla)
        val (s2, watcher) = put(s1, entryWatcher)
        val (state, leave) = put(s2, leaveWatcher)
        val response = answer(pause(state, CompositeEffect(listOf(
            copyEffect(target), SacrificeTargetEffect(EffectTarget.SpecificEntity(watcher)),
        ))))
        val pending = response.state.pendingDecision as ChooseOptionDecision
        pending.prompt.startsWith("Choose the next triggered ability to put on the stack") shouldBe true
        response.state.stack.isEmpty() shouldBe true
        val events = response.events.filterIsInstance<ZoneChangeEvent>()
        events.map { it.entryTriggersAlreadyProcessed } shouldBe listOf(true, false)
        events.first().observedEntryTriggers?.map { it.sourceId } shouldBe listOf(watcher)
        val order = processor.process(response.state, SubmitDecision(
            pending.playerId, OptionChosenResponse(pending.id, 0),
        )).result
        order.error shouldBe null
        order.state.pendingDecision shouldBe null
        triggerSources(order.state).groupingBy { it }.eachCount() shouldBe mapOf(watcher to 1, leave to 1)
        life(settle(order.state)) shouldBe life(state) + 3
    }

    test("R4 post-order EP03: two serialized self triggers survive ordering and resolve once each") {
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
        val pending = second.state.pendingDecision as ChooseOptionDecision
        pending.prompt.startsWith("Choose the next triggered ability to put on the stack") shouldBe true
        second.state.stack.isEmpty() shouldBe true
        val tokens = tokens(second.state)
        tokens.size shouldBe 2
        second.events.filterIsInstance<ZoneChangeEvent>().map {
            it.entryTriggersAlreadyProcessed
        } shouldBe listOf(true, true)
        val firstOrder = processor.process(second.state, SubmitDecision(
            pending.playerId, OptionChosenResponse(pending.id, 0),
        )).result
        firstOrder.error shouldBe null
        val replayPending = replaySecond.state.pendingDecision as ChooseOptionDecision
        val replayOrder = processor.process(replaySecond.state, SubmitDecision(
            replayPending.playerId, OptionChosenResponse(replayPending.id, 0),
        )).result
        replayOrder.error shouldBe null
        encode(firstOrder.state) shouldBe encode(replayOrder.state)
        firstOrder.events shouldBe replayOrder.events
        firstOrder.state.pendingDecision shouldBe null
        triggerSources(firstOrder.state).groupingBy { it }.eachCount() shouldBe tokens.associateWith { 1 }
        life(settle(firstOrder.state)) shouldBe life(state) + 2
    }

    test("R4 post-order EP04: cached observation remains captured; explicit legacy raw synthetic replay redetects") {
        val (state, target) = put(base(), selfChoice)
        val result = answer(pause(state, copyEffect(target)))
        val marked = result.events.filterIsInstance<ZoneChangeEvent>().single()
        marked.entryTriggersAlreadyProcessed shouldBe true
        marked.observedEntryTriggers?.map { it.sourceId } shouldBe listOf(marked.entityId)
        val detector = services.triggerDetector
        detector.detectTriggers(result.state, listOf(marked)).size shouldBe 0

        val syntheticCachedRaw = marked.copy(entryTriggersAlreadyProcessed = false)
        val syntheticLegacyRaw = syntheticCachedRaw.copy(observedEntryTriggers = null)
        val (contextState, observer) = put(result.state, entryWatcher)
        val currentObserver = requireNotNull(contextState.objectRef(observer))
        val priorObserver = currentObserver.copy(generation = currentObserver.generation - 1)
        val contextOnlyEntry = marked.copy(
            entityId = observer, entityName = entryWatcher.name,
            oldObject = priorObserver, newObject = currentObserver,
        )
        syntheticCachedRaw.observedEntryTriggers?.map { it.sourceId } shouldBe listOf(marked.entityId)
        detector.detectTriggers(contextState, listOf(syntheticCachedRaw, contextOnlyEntry))
            .map { it.sourceId } shouldBe listOf(marked.entityId)
        detector.detectTriggers(contextState, listOf(syntheticLegacyRaw, contextOnlyEntry))
            .map { it.sourceId }.toSet() shouldBe setOf(marked.entityId, observer)
    }
})
