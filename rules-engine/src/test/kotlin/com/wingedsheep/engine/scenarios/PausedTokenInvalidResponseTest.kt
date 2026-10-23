package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Supplemental transaction-boundary controls; the original forty-eight cases remain separate. */
class PausedTokenInvalidResponseTest : FunSpec({
    val host = card("Rejected Token Host") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
    }
    val colored = card("Rejected Token Color Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    val modal = card("Rejected Token Mode Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        riot()
    }
    val aura = card("Rejected Token Aura") {
        manaCost = "{0}"; typeLine = "Enchantment — Aura"
    }.let { it.copy(script = it.script.copy(auraTarget = TargetCreature())) }
    val registry = CardRegistry().also { r -> listOf(host, colored, modal, aura).forEach(r::register) }
    val services = EngineServices(registry)
    val processor = ActionProcessor(registry)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun encode(state: GameState) = json.encodeToString(GameState.serializer(), state)
    fun restore(state: GameState) = json.decodeFromString(GameState.serializer(), encode(state))
    fun base(players: Int = 2): GameState = GameInitializer(registry).initializeGame(GameConfig(
        players = (1..players).map { PlayerConfig("Rejection seat $it", Deck(List(40) { host.name })) },
        skipMulligans = true, startingPlayerIndex = 0, seed = 0x5054494E56414CL,
    )).state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
    fun put(state: GameState, definition: CardDefinition): Pair<GameState, EntityId> {
        val owner = state.turnOrder[0]
        val (id, next) = state.newEntity()
        val entity = StaticAbilityHandler(registry).addReplacementEffectComponent(CardEntityFactory.create(definition, owner))
        return next.withEntity(id, entity).addToZone(ZoneKey(owner, Zone.BATTLEFIELD), id) to id
    }
    fun tokens(state: GameState) = state.getBattlefield().filter { state.getEntity(it)?.has<TokenComponent>() == true }
    fun begin(state: GameState, source: EntityId, sourceCopy: Boolean, attacking: Boolean = false): GameState {
        val effect = if (sourceCopy) CreateTokenCopyOfSourceEffect(count = 1)
            else CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(source),
                count = DynamicAmount.Fixed(if (attacking) 2 else 1), attacking = attacking)
        val result = services.effectExecutorRegistry.execute(state, effect, EffectContext(source, state.turnOrder[0]))
        result.error shouldBe null
        return result.state
    }
    fun rejectAndResume(
        original: GameState,
        serialized: Boolean,
        valid: DecisionResponse,
        malformed: List<DecisionResponse>,
        stale: DecisionResponse,
    ): GameState {
        val pending = requireNotNull(original.pendingDecision)
        val state = if (serialized) restore(original) else original
        val encoded = encode(state)
        encoded shouldBe encode(original)
        val actions = listOf(SubmitDecision(state.turnOrder[1], valid), SubmitDecision(pending.playerId, stale)) +
            malformed.map { SubmitDecision(pending.playerId, it) }
        for (action in actions) {
            val rejected = processor.process(state, action).result
            (rejected.error != null) shouldBe true
            encode(rejected.state) shouldBe encoded
            rejected.state.pendingDecision shouldBe pending
            rejected.state.continuationStack shouldBe state.continuationStack
            rejected.events shouldBe emptyList()
            rejected.pendingDecision shouldBe null
            rejected.triggersAlreadyProcessed shouldBe false
        }
        val resumed = processor.process(state, SubmitDecision(pending.playerId, valid)).result
        val untouched = processor.process(original, SubmitDecision(pending.playerId, valid)).result
        resumed.error shouldBe null
        untouched.error shouldBe null
        encode(resumed.state) shouldBe encode(untouched.state)
        resumed.events shouldBe untouched.events
        resumed.state.pendingDecision shouldBe null
        resumed.state.continuationStack shouldBe emptyList()
        return resumed.state
    }
    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {
        val suffix = "sourceCopy=$sourceCopy, serialized=$serialized"
        test("PI01 rejected color replies preserve routing and permit the original answer; $suffix") {
            val (state, source) = put(base(), colored)
            val paused = begin(state, source, sourceCopy)
            (paused.pendingDecision is ChooseColorDecision) shouldBe true
            val question = paused.pendingDecision as ChooseColorDecision
            val valid = ColorChosenResponse(question.id, Color.BLUE)
            val result = rejectAndResume(paused, serialized, valid,
                listOf(OptionChosenResponse(question.id, 0), CancelDecisionResponse(question.id)),
                valid.copy(decisionId = question.id + "-stale"))
            val token = tokens(result).single()
            result.getEntity(token)!!.chosenColor() shouldBe Color.BLUE
        }
        test("PI02 rejected mode indices preserve routing and permit the original answer; $suffix") {
            val (state, source) = put(base(), modal)
            val paused = begin(state, source, sourceCopy)
            (paused.pendingDecision is ChooseOptionDecision) shouldBe true
            val question = paused.pendingDecision as ChooseOptionDecision
            val valid = OptionChosenResponse(question.id, 0)
            val result = rejectAndResume(paused, serialized, valid, listOf(
                OptionChosenResponse(question.id, -1), OptionChosenResponse(question.id, question.options.size),
                ColorChosenResponse(question.id, Color.BLUE),
            ), valid.copy(decisionId = question.id + "-stale"))
            val token = tokens(result).single()
            result.getEntity(token)!!.chosenModeId() shouldBe RIOT_MODE_COUNTER
            result.getEntity(token)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        }
        test("PI03 rejected Aura hosts preserve routing and permit the original answer; $suffix") {
            val (withHost, hostId) = put(base(), host)
            val (withAura, source) = put(withHost, aura)
            val state = withAura.updateEntity(source) { it.with(AttachedToComponent(hostId)) }
                .updateEntity(hostId) { it.with(AttachmentsComponent(listOf(source))) }
            val paused = begin(state, source, sourceCopy)
            (paused.pendingDecision is ChooseTargetsDecision) shouldBe true // PI03 source-copy baseline mismatch
            val question = paused.pendingDecision as ChooseTargetsDecision
            val valid = TargetsResponse(question.id, mapOf(0 to listOf(hostId)))
            val result = rejectAndResume(paused, serialized, valid, listOf(
                TargetsResponse(question.id, emptyMap()),
                TargetsResponse(question.id, mapOf(0 to listOf(state.turnOrder[0]))),
                TargetsResponse(question.id, mapOf(0 to listOf(hostId, hostId))),
                TargetsResponse(question.id, mapOf(0 to listOf(hostId), 1 to listOf(hostId))),
                ColorChosenResponse(question.id, Color.BLUE),
            ), valid.copy(decisionId = question.id + "-stale"))
            val token = tokens(result).single()
            result.getEntity(token)!!.get<AttachedToComponent>()!!.targetId shouldBe hostId
            result.getEntity(hostId)!!.get<AttachmentsComponent>()!!.attachedIds.toSet() shouldBe setOf(source, token)
        }
    }
    for (serialized in listOf(false, true)) {
        test("PI04 rejected defender groups preserve the whole pending batch; serialized=$serialized") {
            val (precombat, source) = put(base(3), host)
            val state = precombat.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS)
            val paused = begin(state, source, sourceCopy = false, attacking = true)
            (paused.pendingDecision is ChooseTargetsDecision) shouldBe true
            val question = paused.pendingDecision as ChooseTargetsDecision
            val players = state.turnOrder
            val valid = TargetsResponse(question.id, mapOf(0 to listOf(players[1]), 1 to listOf(players[2])))
            val result = rejectAndResume(paused, serialized, valid, listOf(
                TargetsResponse(question.id, mapOf(0 to listOf(players[1]))),
                TargetsResponse(question.id, mapOf(0 to listOf(players[0]), 1 to listOf(players[2]))),
                TargetsResponse(question.id, valid.selectedTargets + (2 to listOf(players[1]))),
                TargetsResponse(question.id, mapOf(0 to listOf(players[1], players[1]), 1 to listOf(players[2]))),
                OptionChosenResponse(question.id, 0),
            ), valid.copy(decisionId = question.id + "-stale"))
            tokens(result).size shouldBe 2
            tokens(result).map { result.getEntity(it)!!.get<AttackingComponent>()!!.defenderId } shouldBe players.drop(1)
        }
    }
})
