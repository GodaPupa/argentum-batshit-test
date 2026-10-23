package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ContinuationHandler
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

/** Three read-only diagnostic traces. NOT qualifying gameplay or changed original assertions. */
class EntryChoiceR4BoundaryTraceDiagnosticTest : FunSpec({
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

    fun trace(label: String, result: ExecutionResult) {
        val st = result.state
        val stackDetails = st.stack.map { id ->
            val c = st.getEntity(id)
            listOf(
                id.toString(),
                "triggerSource=" + c?.get<TriggeredAbilityOnStackComponent>()?.sourceId,
                "spell=" + (c?.has<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>() == true),
            ).joinToString("/")
        }
        val zones = result.events.filterIsInstance<ZoneChangeEvent>()
        println("R4TRACE " + label + " error=" + result.error +
            " resultIsPaused=" + result.isPaused +
            " triggersAlreadyProcessed=" + result.triggersAlreadyProcessed +
            " pendingDecision=" + st.pendingDecision?.javaClass?.simpleName +
            " stackResolutionPendingPriority=" + st.stackResolutionPendingPriority +
            " pendingCastPriority=" + (st.pendingCastPriority != null) +
            " continuations=" + st.continuationStack.map { it::class.simpleName } +
            " stack=" + stackDetails +
            " allEventTypes=" + result.events.map { it::class.simpleName })
        zones.forEachIndexed { index, e ->
            println("R4TRACE " + label + " zone[" + index + "] name=" + e.entityName +
                " entity=" + e.entityId + " from=" + e.fromZone + " to=" + e.toZone +
                " processed=" + e.entryTriggersAlreadyProcessed +
                " observedPresent=" + (e.observedEntryTriggers != null) +
                " observedSources=" + e.observedEntryTriggers?.map { it.sourceId.toString() } +
                " entrySnapshotPresent=" + (e.entrySnapshot != null))
        }
        println("R4TRACE " + label + " triggerSourcesOnStack=" +
            st.stack.mapNotNull { st.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()?.sourceId })
        val detected = services.triggerDetector.detectTriggers(st, result.events)
        println("R4TRACE " + label + " detectPublishedEventsSources=" +
            detected.map { it.sourceId.toString() })
        val unmarked = result.events.map { e ->
            if (e is ZoneChangeEvent && e.toZone == Zone.BATTLEFIELD)
                e.copy(entryTriggersAlreadyProcessed = false)
            else e
        }
        println("R4TRACE " + label + " unmarkedDetectSources=" +
            services.triggerDetector.detectTriggers(st, unmarked).map { it.sourceId.toString() })
    }

    test("R4 diagnostic EP02: continuation event capture versus decision public trigger stack") {
        val (s1, target) = put(base(), vanilla)
        val (s2, watcher) = put(s1, entryWatcher)
        val (state, leave) = put(s2, leaveWatcher)
        val paused = pause(state, CompositeEffect(listOf(
            copyEffect(target), SacrificeTargetEffect(EffectTarget.SpecificEntity(watcher)),
        )))
        val q = paused.pendingDecision as ChooseColorDecision
        val response = ColorChosenResponse(q.id, Color.BLUE)
        val continuation = ContinuationHandler(services).resume(paused, response)
        trace("EP02_direct_continuation", continuation)
        val publicResult = processor.process(paused, SubmitDecision(q.playerId, response)).result
        publicResult.error shouldBe null
        trace("EP02_public_decision", publicResult)
        println("R4TRACE EP02 identities=token:" + tokens(publicResult.state) +
            ",watcher:" + watcher + ",leave:" + leave)
    }

    test("R4 diagnostic EP03: two-token first and second answer event capture versus stack") {
        val (state, target) = put(base(), selfChoice)
        val paused = pause(state, copyEffect(target, 2))
        val firstQ = paused.pendingDecision as ChooseColorDecision
        val firstResponse = ColorChosenResponse(firstQ.id, Color.BLUE)
        val firstDirect = ContinuationHandler(services).resume(paused, firstResponse)
        trace("EP03_first_direct_continuation", firstDirect)
        val first = processor.process(paused, SubmitDecision(firstQ.playerId, firstResponse)).result
        first.error shouldBe null
        trace("EP03_first_public_decision", first)
        val secondQ = first.state.pendingDecision as ChooseColorDecision
        val secondResponse = ColorChosenResponse(secondQ.id, Color.RED)
        val secondDirect = ContinuationHandler(services).resume(first.state, secondResponse)
        trace("EP03_second_direct_continuation", secondDirect)
        val second = processor.process(first.state, SubmitDecision(secondQ.playerId, secondResponse)).result
        second.error shouldBe null
        trace("EP03_second_public_decision", second)
    }

    test("R4 diagnostic EP04: captured-vs-uncaptured synthetic observer replay") {
        val (state, target) = put(base(), selfChoice)
        val result = answer(pause(state, copyEffect(target)))
        trace("EP04_public_decision", result)
        val marked = result.events.filterIsInstance<ZoneChangeEvent>().single()
        val raw = marked.copy(entryTriggersAlreadyProcessed = false)
        val (contextState, observer) = put(result.state, entryWatcher)
        val currentObserver = requireNotNull(contextState.objectRef(observer))
        val priorObserver = currentObserver.copy(generation = currentObserver.generation - 1)
        val contextOnlyEntry = marked.copy(
            entityId = observer, entityName = entryWatcher.name,
            oldObject = priorObserver, newObject = currentObserver,
        )
        val variations = mapOf(
            "raw_markedContext" to listOf(raw, contextOnlyEntry),
            "raw_clearObserved_markedContext" to listOf(raw.copy(observedEntryTriggers = null), contextOnlyEntry),
            "raw_clearObserved_clearContext" to listOf(raw.copy(observedEntryTriggers = null), contextOnlyEntry.copy(observedEntryTriggers = null)),
        )
        variations.forEach { (name, events) ->
            println("R4TRACE EP04 " + name + " detected=" +
                services.triggerDetector.detectTriggers(contextState, events).map { it.sourceId.toString() })
        }
        println("R4TRACE EP04 rawObserved=" + raw.observedEntryTriggers?.map { it.sourceId.toString() } +
            " contextObserved=" + contextOnlyEntry.observedEntryTriggers?.map { it.sourceId.toString() })
    }
})
