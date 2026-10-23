package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Recipient identity must be correct while planning a token before it is on the battlefield. */
class PausedTokenRecipientFilterTest : FunSpec({
    val body = card("Token Recipient Filter Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
    }
    // Printed-card fixture: the repository has no registered Rhythm definition. These controls
    // qualify its riot recipient restriction only, not the complete card or countering behavior.
    // https://magic.wizards.com/en/news/feature/ravnica-allegiance-release-notes-2019-01-16
    val rhythm = card("Rhythm of the Wild") {
        manaCost = "{1}{R}{G}"; typeLine = "Enchantment"
        staticAbility { ability = GrantCantBeCountered(GameObjectFilter.Creature.youControl()) }
        staticAbility {
            ability = GrantKeyword(Keyword.RIOT, GroupFilter(GameObjectFilter.Creature.nontoken().youControl()))
        }
    }
    val tokenLord = card("Token Recipient Riot Grant") {
        manaCost = "{0}"; typeLine = "Enchantment"
        staticAbility {
            ability = GrantKeyword(Keyword.RIOT, GroupFilter(GameObjectFilter.Creature.token().youControl()))
        }
    }
    val registry = CardRegistry().also { r -> listOf(body, rhythm, tokenLord).forEach(r::register) }
    val statics = StaticAbilityHandler(registry)
    val services = EngineServices(registry)
    val processor = ActionProcessor(registry)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(state: GameState): GameState {
        val raw = json.encodeToString(GameState.serializer(), state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        return restored
    }
    fun base(): GameState = GameInitializer(registry).initializeGame(GameConfig(
        players = (1..2).map { PlayerConfig("Recipient filter seat $it", Deck(List(40) { body.name })) },
        skipMulligans = true, startingPlayerIndex = 0, seed = 0x50545246494CL,
    )).state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
    fun put(state: GameState, definition: CardDefinition): Pair<GameState, EntityId> {
        val owner = state.turnOrder[0]
        val (id, next) = state.newEntity()
        val entity = statics.addReplacementEffectComponent(
            statics.addContinuousEffectComponent(CardEntityFactory.create(definition, owner)))
        return next.withEntity(id, entity).addToZone(ZoneKey(owner, Zone.BATTLEFIELD), id) to id
    }
    fun tokens(state: GameState) = state.getBattlefield().filter { state.getEntity(it)?.has<TokenComponent>() == true }
    fun copy(state: GameState, source: EntityId, sourceCopy: Boolean): EffectResult {
        val effect = if (sourceCopy) CreateTokenCopyOfSourceEffect(count = 1)
            else CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(source))
        return services.effectExecutorRegistry.execute(state, effect, EffectContext(source, state.turnOrder[0]))
    }
    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {
        val suffix = "sourceCopy=$sourceCopy, serialized=$serialized"
        test("PF01 Rhythm of the Wild never grants riot to a token copy; $suffix") {
            val (withBody, source) = put(base(), body)
            val (initial, _) = put(withBody, rhythm)
            val state = if (serialized) restore(initial) else initial
            val result = copy(state, source, sourceCopy)
            result.error shouldBe null
            result.state.pendingDecision shouldBe null
            result.state.continuationStack shouldBe emptyList()
            val token = tokens(result.state).single()
            (result.state.getEntity(token)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            result.state.projectedState.hasKeyword(token, Keyword.RIOT) shouldBe false
            result.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe false
            result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
                .map { it.entityId } shouldBe listOf(token)
        }
        test("PF02 a token-only riot grant applies to a token copy before entry; $suffix") {
            val (withBody, source) = put(base(), body)
            val (initial, _) = put(withBody, tokenLord)
            val state = if (serialized) restore(initial) else initial
            val started = copy(state, source, sourceCopy)
            started.error shouldBe null
            (started.state.pendingDecision is ChooseOptionDecision) shouldBe true
            val paused = if (serialized) restore(started.state) else started.state
            val question = paused.pendingDecision as ChooseOptionDecision
            question.options shouldBe RIOT_MODE_OPTIONS.map { it.label }
            val resumed = processor.process(paused,
                SubmitDecision(question.playerId, OptionChosenResponse(question.id, 0))).result
            resumed.error shouldBe null
            resumed.state.pendingDecision shouldBe null
            resumed.state.continuationStack shouldBe emptyList()
            val token = tokens(resumed.state).single()
            resumed.state.getEntity(token)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            resumed.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe false
            resumed.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
                .map { it.entityId } shouldBe listOf(token)
        }
    }
})
