package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.PermanentsEnteredUnderControlThisTurnComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.PermanentsEnterTapped
import com.wingedsheep.sdk.scripting.conditions.PermanentTypeEnteredBattlefieldThisTurn
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfSourceEffect
import com.wingedsheep.sdk.scripting.effects.CreateTokenCopyOfTargetEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

/** Four fixed mechanical cases: at most three decisions / six answers including replay. No gameplay allocation. */
class TokenBatchEntryHistoryTest : FunSpec({
    val chooser = card("Batch Entry Color Fixture") {
        manaCost = "{2}"; typeLine = "Creature — Test"; power = 2; toughness = 2
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    val host = card("Batch Entry Host Fixture") {
        manaCost = "{2}"; typeLine = "Creature — Test"; power = 2; toughness = 2
    }
    val aura = card("Batch Entry Aura Fixture") {
        manaCost = "{1}"; typeLine = "Enchantment — Aura"
    }.let { it.copy(script = it.script.copy(auraTarget = TargetCreature())) }
    fun historyGate(name: String, type: CardType) = card(name) {
        manaCost = "{1}"; typeLine = "Artifact"
        replacementEffect(PermanentsEnterTapped(condition = PermanentTypeEnteredBattlefieldThisTurn(type)))
    }
    val creatureGate = historyGate("Batch Entry Creature Gate", CardType.CREATURE)
    val auraGate = historyGate("Batch Entry Enchantment Gate", CardType.ENCHANTMENT)
    val registry = CardRegistry().also { r ->
        listOf(chooser, host, aura, creatureGate, auraGate).forEach(r::register)
    }
    val services = EngineServices(registry)
    val processor = ActionProcessor(registry)
    val staticAbilities = StaticAbilityHandler(registry)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    fun encode(state: GameState) = json.encodeToString(GameState.serializer(), state)
    fun restore(state: GameState) = json.decodeFromString(GameState.serializer(), encode(state))
    fun base() = GameInitializer(registry).initializeGame(GameConfig(
        players = listOf(PlayerConfig("Batch seat1", Deck(List(40) { host.name })),
            PlayerConfig("Batch seat2", Deck(List(40) { host.name }))),
        skipMulligans = true, startingPlayerIndex = 0, seed = 0x4D54454E545259L,
    )).state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
    fun put(state: GameState, definition: CardDefinition): Pair<GameState, EntityId> {
        val owner = state.turnOrder[0]
        val (id, next) = state.newEntity()
        val container = staticAbilities.addReplacementEffectComponent(CardEntityFactory.create(definition, owner), definition)
        // Fixture setup deliberately records no entry this turn; only the subsequent real token batch does.
        return next.withEntity(id, container).addToZone(ZoneKey(owner, Zone.BATTLEFIELD), id) to id
    }
    fun tokens(state: GameState) = state.getBattlefield().filter { state.getEntity(it)?.has<TokenComponent>() == true }
    fun targetCopy(id: EntityId, count: Int) = CreateTokenCopyOfTargetEffect(
        EffectTarget.SpecificEntity(id), count = DynamicAmount.Fixed(count),
    )
    fun start(state: GameState, effect: Effect, source: EntityId): GameState {
        val result = services.effectExecutorRegistry.execute(state, effect, EffectContext(source, state.turnOrder[0]))
        result.error shouldBe null
        return result.state
    }
    fun answer(state: GameState, hostId: EntityId? = null): ExecutionResult {
        val question = requireNotNull(state.pendingDecision)
        val response = when (question) {
            is ChooseColorDecision -> ColorChosenResponse(question.id, Color.BLUE)
            is ChooseTargetsDecision -> TargetsResponse(question.id, mapOf(0 to listOf(requireNotNull(hostId))))
            else -> error("Unexpected fixed-fixture question: ${question::class.simpleName}")
        }
        return processor.process(state, SubmitDecision(question.playerId, response)).result.also { it.error shouldBe null }
    }
    fun checkSnapshots(state: GameState, original: GameState) {
        state.continuationStack.forEach { frame ->
            val payload: Any = if (frame is Suspension) frame.answer else frame
            val before = when (payload) {
                is CreateTokenCopyRemainingContinuation -> payload.beforeEntry
                is CreateTokenCopyAuraHostContinuation -> payload.beforeEntry
                else -> null
            }
            if (before != null) {
                before.continuationStack shouldBe emptyList()
                encode(before) shouldBe encode(original.copy(continuationStack = emptyList()))
            }
        }
    }
    fun finish(original: GameState, paused: GameState, count: Int, hostId: EntityId? = null) {
        var current = paused
        val published = mutableListOf<EntityId>()
        repeat(count) {
            checkSnapshots(current, original)
            val direct = answer(current, hostId)
            val replay = answer(restore(current), hostId)
            encode(replay.state) shouldBe encode(direct.state)
            replay.events shouldBe direct.events
            published += direct.events.filterIsInstance<ZoneChangeEvent>()
                .filter { it.toZone == Zone.BATTLEFIELD && direct.state.getEntity(it.entityId)?.has<TokenComponent>() == true }
                .map { it.entityId }
            current = direct.state
            tokens(current).forEach { id -> current.getEntity(id)!!.has<TappedComponent>() shouldBe false }
            current.getEntity(current.turnOrder[0])!!.get<PermanentsEnteredUnderControlThisTurnComponent>()!!
                .entries.size shouldBe tokens(current).size
        }
        current.pendingDecision shouldBe null
        tokens(current).size shouldBe count
        published shouldBe tokens(current)
    }

    test("BE01 target-copy siblings retain original entry history across three serialized color choices") {
        val (withGate, _) = put(base(), creatureGate)
        val (original, source) = put(withGate, chooser)
        finish(original, start(original, targetCopy(source, 3), source), 3)
    }

    test("BE02 source-copy siblings retain original entry history across three serialized color choices") {
        val (withGate, _) = put(base(), creatureGate)
        val (original, source) = put(withGate, chooser)
        finish(original, start(original, CreateTokenCopyOfSourceEffect(count = 3), source), 3)
    }

    test("BE03 Aura-copy siblings retain original entry history across two serialized host choices") {
        val (withGate, _) = put(base(), auraGate)
        val (withHost, hostId) = put(withGate, host)
        val (withAura, source) = put(withHost, aura)
        val original = withAura.updateEntity(source) { it.with(AttachedToComponent(hostId)) }
            .updateEntity(hostId) { it.with(AttachmentsComponent(listOf(source))) }
        finish(original, start(original, targetCopy(source, 2), source), 2, hostId)
    }

    test("BE04 a pending copy tail missing its original checkpoint is rejected without inventing history") {
        val (withGate, _) = put(base(), creatureGate)
        val (original, source) = put(withGate, chooser)
        val paused = start(original, targetCopy(source, 2), source)
        val exact = encode(paused)
        fun removeCheckpoint(element: JsonElement): JsonElement = when (element) {
            is JsonArray -> JsonArray(element.map(::removeCheckpoint))
            is JsonObject -> JsonObject(element.filterKeys { it != "beforeEntry" }.mapValues { removeCheckpoint(it.value) })
            else -> element
        }
        val oldShape = removeCheckpoint(json.parseToJsonElement(exact)).toString()
        (oldShape == exact) shouldBe false
        shouldThrow<SerializationException> { json.decodeFromString(GameState.serializer(), oldShape) }
        encode(paused) shouldBe exact
    }
})
