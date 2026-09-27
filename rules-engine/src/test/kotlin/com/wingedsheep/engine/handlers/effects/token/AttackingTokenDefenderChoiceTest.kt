package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.combat.CombatDefenders
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Fixed mechanical qualification, with construction entropy only; no experimental pilots or games. */
class AttackingTokenDefenderChoiceTest : FunSpec({
    val bear = card("Defender Test Bear") {
        manaCost = "{1}{G}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
    }
    val land = card("Defender Test Land") { typeLine = "Land" }
    val walker = card("Defender Test Walker") {
        manaCost = "{4}"; typeLine = "Planeswalker — Test"
        keywords(Keyword.SHROUD, Keyword.HEXPROOF)
    }
    val battle = card("Defender Test Battle") { manaCost = "{3}"; typeLine = "Battle — Siege" }
    val doubler = card("Defender Test Doubler") {
        manaCost = "{4}"; typeLine = "Enchantment"; replacementEffect(MultiplyTokenCreation())
    }
    val replacement = card("Defender Test Replacement") {
        manaCost = "{2}"; typeLine = "Artifact — Equipment"
        replacementEffect(ReplaceTokenCreationWithAttachedCopy())
    }
    val colorCreature = card("Defender Test Color Creature") {
        manaCost = "{2}"; typeLine = "Creature — Test"; power = 2; toughness = 2
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    val registry = CardRegistry().also { r ->
        listOf(bear, land, walker, battle, doubler, replacement, colorCreature).forEach(r::register)
    }
    val services = EngineServices(registry)
    val processor = ActionProcessor(registry)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    fun encode(s: GameState) = json.encodeToString(GameState.serializer(), s)
    fun restore(s: GameState) = json.decodeFromString(GameState.serializer(), encode(s))
    fun base(players: Int = 3, mode: AttackMode = AttackMode.MULTIPLE, team: Boolean = false): GameState {
        val initialized = GameInitializer(registry).initializeGame(GameConfig(
            players = (1..players).map { PlayerConfig("Player $it", Deck(List(40) { bear.name }), 20) },
            startingPlayerIndex = 0, skipMulligans = true, seed = 0x544F4B454E01L,
            attackMode = mode,
            format = if (team) Format.TwoHeadedGiant() else Format.Standard,
            teams = if (team) listOf(listOf(0, 1), listOf(2, 3)) else null,
        ))
        return initialized.state.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS)
    }
    fun put(state: GameState, owner: EntityId, definition: CardDefinition, extra: List<Component> = emptyList()): Pair<GameState, EntityId> {
        val (id, next) = state.newEntity()
        var container = ComponentContainer.of(
            CardComponent(cardDefinitionId = definition.name, name = definition.name, manaCost = definition.manaCost,
                typeLine = definition.typeLine, baseStats = definition.creatureStats,
                baseKeywords = definition.keywords, ownerId = owner),
            OwnerComponent(owner), ControllerComponent(owner),
        )
        container = StaticAbilityHandler(registry).addReplacementEffectComponent(container)
        for (component in extra) container = container.withComponent(component)
        return next.withEntity(id, container).addToZone(ZoneKey(owner, Zone.BATTLEFIELD), id) to id
    }
    fun effect(count: Int = 2) = CreateTokenEffect(
        count = DynamicAmount.Fixed(count), power = 1, toughness = 1, colors = setOf(Color.RED),
        creatureTypes = setOf("Warrior"), attacking = true, tapped = true,
    )
    fun tokens(s: GameState) = s.getBattlefield().filter { s.getEntity(it)?.has<TokenComponent>() == true }
    fun defender(s: GameState, id: EntityId) = s.getEntity(id)?.get<AttackingComponent>()?.defenderId
    fun castEffect(s: GameState, e: Effect, context: EffectContext = EffectContext(null, s.turnOrder[0]), mark: Boolean = false): EffectResult =
        services.effectExecutorRegistry.execute(s, if (mark) CompositeEffect(listOf(
            e, AddCountersToCollectionEffect(CREATED_TOKENS, "+1/+1", 1),
        )) else e, context)
    fun choose(s: GameState, choices: List<EntityId>, actor: EntityId = s.pendingDecision!!.playerId): ExecutionResult =
        processor.process(s, SubmitDecision(actor, TargetsResponse(s.pendingDecision!!.id,
            choices.mapIndexed { index, id -> index to listOf(id) }.toMap()))).result
    fun marked(s: GameState, ids: List<EntityId>) {
        ids.forEach { s.getEntity(it)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1 }
    }

    test("TD01 land attack trigger creates two tokens attacking the sole opponent, never its controller") {
        val initial = base(2); val p = initial.turnOrder
        val (state, source) = put(initial, p[0], land)
        val result = castEffect(state, effect(), EffectContext(source, p[0], triggeringPlayerId = p[0]))
        result.isSuccess shouldBe true
        val ids = tokens(result.state); ids.size shouldBe 2
        ids.forEach { defender(result.state, it) shouldBe p[1]; result.state.getEntity(it)?.has<TappedComponent>() shouldBe true }
        result.updatedCollections[CREATED_TOKENS] shouldBe ids
        result.events.filterIsInstance<ZoneChangeEvent>().map { it.entityId } shouldBe ids
    }
    test("TD02 multiple opponents require a separate controller choice for every not-yet-created token") {
        val state = base(); val p = state.turnOrder
        val context = EffectContext(null, p[0], triggeringPlayerId = p[0], xValue = 7)
        val paused = castEffect(state, effect(), context)
        paused.isPaused shouldBe true; tokens(paused.state).size shouldBe 0
        val decision = paused.pendingDecision as ChooseTargetsDecision
        decision.playerId shouldBe p[0]
        decision.targetRequirements.map { it.index } shouldBe listOf(0, 1)
        decision.legalTargets shouldBe mapOf(0 to p.drop(1), 1 to p.drop(1))
        (paused.state.peekContinuation() as Suspension).answer.let { it as AttackingTokenDefenderContinuation }.context shouldBe context
        val resumed = choose(paused.state, listOf(p[1], p[2]))
        resumed.error shouldBe null; resumed.state.pendingDecision shouldBe null
        tokens(resumed.state).map { defender(resumed.state, it) } shouldBe listOf(p[1], p[2])
    }
    test("TD03 public planeswalkers and protected battles are nontargeting defender choices") {
        val initial = base(); val p = initial.turnOrder
        val (s1, enemyWalker) = put(initial, p[1], walker, listOf(CountersComponent().withAdded(CounterType.LOYALTY, 5)))
        val (s2, ownWalker) = put(s1, p[0], walker, listOf(CountersComponent().withAdded(CounterType.LOYALTY, 5)))
        val (s3, enemyProtected) = put(s2, p[0], battle, listOf(ProtectorComponent(p[2]), CountersComponent().withAdded(CounterType.DEFENSE, 5)))
        val (state, ownProtected) = put(s3, p[1], battle, listOf(ProtectorComponent(p[0]), CountersComponent().withAdded(CounterType.DEFENSE, 5)))
        val paused = castEffect(state, effect(), mark = true)
        val offered = (paused.pendingDecision as ChooseTargetsDecision).legalTargets[0]!!
        offered.toSet() shouldBe setOf(p[1], p[2], enemyWalker, enemyProtected)
        (ownWalker in offered || ownProtected in offered) shouldBe false
        val resumed = choose(paused.state, listOf(enemyWalker, enemyProtected))
        resumed.error shouldBe null
        val ids = tokens(resumed.state); ids.map { defender(resumed.state, it) } shouldBe listOf(enemyWalker, enemyProtected)
        marked(resumed.state, ids)
        resumed.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        resumed.events.filterIsInstance<TargetsChosenEvent>().size shouldBe 0
    }
    test("TD04 attack-left and attack-right preserve the legal neighboring seat") {
        for ((mode, index) in listOf(AttackMode.LEFT to 1, AttackMode.RIGHT to 3)) {
            val state = base(4, mode); val result = castEffect(state, effect())
            result.isSuccess shouldBe true
            tokens(result.state).map { defender(result.state, it) } shouldBe List(2) { state.turnOrder[index] }
        }
    }
    test("TD05 recipient fanout suspends once, retains all token collections and does not make nonactive players attack") {
        val state = base(); val p = state.turnOrder
        val paused = castEffect(state, effect().copy(controller = EffectTarget.PlayerRef(Player.Each)), mark = true)
        paused.isPaused shouldBe true; tokens(paused.state).size shouldBe 0
        val resumed = choose(restore(paused.state), listOf(p[1], p[2]))
        resumed.error shouldBe null; resumed.state.pendingDecision shouldBe null
        val ids = tokens(resumed.state); ids.size shouldBe 6; marked(resumed.state, ids)
        ids.groupingBy { resumed.state.getEntity(it)?.get<ControllerComponent>()?.playerId }.eachCount() shouldBe p.associateWith { 2 }
        ids.filter { resumed.state.getEntity(it)?.get<ControllerComponent>()?.playerId != p[0] }
            .forEach { defender(resumed.state, it) shouldBe null }
        resumed.events.filterIsInstance<ZoneChangeEvent>().map { it.entityId }.toSet() shouldBe ids.toSet()
    }
    test("TD06 doubled plain batch freezes four defender choices and four delayed riders across serialization") {
        val initial = base(); val p = initial.turnOrder
        val (state, _) = put(initial, p[0], doubler)
        val paused = castEffect(state, effect().copy(sacrificeAtStep = Step.END), mark = true)
        (paused.pendingDecision as ChooseTargetsDecision).targetRequirements.size shouldBe 4
        val resumed = choose(restore(paused.state), listOf(p[1], p[2], p[1], p[2]))
        resumed.error shouldBe null; val ids = tokens(resumed.state); ids.size shouldBe 4; marked(resumed.state, ids)
        resumed.state.delayedTriggers.size shouldBe 4
        ids.map { defender(resumed.state, it) } shouldBe listOf(p[1], p[2], p[1], p[2])
    }
    test("TD07 target-copy batch uses the same once-only count replacement and selected defenders") {
        val initial = base(); val p = initial.turnOrder
        val (s1, target) = put(initial, p[0], bear)
        val (state, _) = put(s1, p[0], doubler)
        val paused = castEffect(state, CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(target),
            count = DynamicAmount.Fixed(2), attacking = true, tapped = true), mark = true)
        (paused.pendingDecision as ChooseTargetsDecision).targetRequirements.size shouldBe 4
        val resumed = choose(restore(paused.state), listOf(p[2], p[1], p[2], p[1]))
        resumed.error shouldBe null; val ids = tokens(resumed.state); ids.size shouldBe 4; marked(resumed.state, ids)
        ids.map { defender(resumed.state, it) } shouldBe listOf(p[2], p[1], p[2], p[1])
        ids.forEach { resumed.state.getEntity(it)?.get<CardComponent>()?.name shouldBe bear.name }
        resumed.state.getEntity(target) shouldBe state.getEntity(target)
    }
    test("TD08 noncreature target copies enter tapped without attacking or asking for a defender") {
        val initial = base(); val p = initial.turnOrder
        val (state, target) = put(initial, p[0], bear)
        val result = castEffect(state, CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(target),
            attacking = true, tapped = true, overrideCardTypes = setOf(CardType.ARTIFACT)))
        result.isSuccess shouldBe true; tokens(result.state).size shouldBe 1
        val id = tokens(result.state).single(); defender(result.state, id) shouldBe null
        result.state.getEntity(id)?.has<TappedComponent>() shouldBe true
    }
    test("TD09 wrong actor and malformed defender submissions preserve the exact pending state") {
        val state = base(); val p = state.turnOrder; val paused = castEffect(state, effect()).state
        val responses = listOf(
            p[1] to mapOf(0 to listOf(p[1]), 1 to listOf(p[2])),
            p[0] to mapOf(0 to listOf(p[0]), 1 to listOf(p[2])),
            p[0] to mapOf(0 to listOf(p[1])),
            p[0] to mapOf(0 to listOf(p[1], p[2]), 1 to listOf(p[2])),
            p[0] to mapOf(0 to listOf(p[1]), 1 to listOf(p[2]), 2 to listOf(p[1])),
        )
        for ((actor, selections) in responses) {
            val result = processor.process(paused, SubmitDecision(actor, TargetsResponse(paused.pendingDecision!!.id, selections))).result
            (result.error != null) shouldBe true; encode(result.state) shouldBe encode(paused)
        }
        tokens(paused).size shouldBe 0
    }
    test("TD10 serialized pending choice and identical answer replay preserve state and events exactly") {
        val state = base(); val p = state.turnOrder
        val paused = castEffect(state, effect().copy(exileAtStep = Step.END), mark = true).state
        val decoded = restore(paused); encode(decoded) shouldBe encode(paused)
        val answer = listOf(p[2], p[1])
        val first = choose(paused, answer); val replay = choose(decoded, answer)
        first.error shouldBe null; replay.error shouldBe null
        encode(replay.state) shouldBe encode(first.state)
        replay.events shouldBe first.events
        tokens(first.state).size shouldBe 2; first.state.delayedTriggers.size shouldBe 2
    }
    test("TD11 copy as-enters choices retain chosen defender tail, token collection, entry events and delayed riders") {
        val initial = base(); val p = initial.turnOrder
        val (state, target) = put(initial, p[0], colorCreature)
        val paused = castEffect(state, CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(target),
            count = DynamicAmount.Fixed(2), attacking = true, tapped = true,
            sacrificeAtStep = Step.END, exileAtStep = Step.END), mark = true)
        var result = choose(restore(paused.state), listOf(p[2], p[1]))
        result.error shouldBe null; (result.state.pendingDecision is ChooseColorDecision) shouldBe true
        val events = result.events.toMutableList()
        for (color in listOf(Color.BLUE, Color.RED)) {
            val decoded = restore(result.state); val decision = decoded.pendingDecision as ChooseColorDecision
            result = processor.process(decoded, SubmitDecision(p[0], ColorChosenResponse(decision.id, color))).result
            result.error shouldBe null; events.addAll(result.events)
        }
        result.state.pendingDecision shouldBe null
        val ids = tokens(result.state); ids.size shouldBe 2
        ids.map { defender(result.state, it) } shouldBe listOf(p[2], p[1]); marked(result.state, ids)
        result.state.delayedTriggers.size shouldBe 4
        events.filterIsInstance<ZoneChangeEvent>().filter { it.entityId in ids }.map { it.entityId } shouldBe ids
    }
    test("TD12 declining token replacement keeps the prepared count and actual recipient across the next choice") {
        val initial = base(); val p = initial.turnOrder
        val (s1, target) = put(initial, p[0], bear)
        val (s2, _) = put(s1, p[0], doubler)
        val (state, _) = put(s2, p[0], replacement, listOf(AttachedToComponent(target)))
        val paused = castEffect(state, effect(), EffectContext(null, p[1]), mark = true)
        // The explicit recipient is the active player even though another player controls the effect.
        val actual = castEffect(state, effect().copy(controller = EffectTarget.SpecificEntity(p[0])), EffectContext(null, p[1]), mark = true)
        paused.isSuccess shouldBe true; tokens(paused.state).size shouldBe 2
        val question = actual.pendingDecision as YesNoDecision
        question.playerId shouldBe p[0]
        val declined = processor.process(restore(actual.state), SubmitDecision(p[0], YesNoResponse(question.id, false))).result
        declined.error shouldBe null
        (declined.state.pendingDecision as ChooseTargetsDecision).targetRequirements.size shouldBe 4
        val resumed = choose(restore(declined.state), List(4) { p[2] })
        resumed.error shouldBe null; val ids = tokens(resumed.state); ids.size shouldBe 4; marked(resumed.state, ids)
        ids.forEach { resumed.state.getEntity(it)?.get<ControllerComponent>()?.playerId shouldBe p[0] }
    }
    test("TD13 accepting replacement preserves attacking state, actual recipient, collection and delayed riders") {
        val initial = base(); val p = initial.turnOrder
        val (s1, target) = put(initial, p[0], bear)
        val (state, _) = put(s1, p[0], replacement, listOf(AttachedToComponent(target)))
        val paused = castEffect(state, effect().copy(controller = EffectTarget.SpecificEntity(p[0]),
            sacrificeAtStep = Step.END, exileAtStep = Step.END), EffectContext(null, p[1]), mark = true)
        val question = paused.pendingDecision as YesNoDecision
        val accepted = processor.process(restore(paused.state), SubmitDecision(p[0], YesNoResponse(question.id, true))).result
        accepted.error shouldBe null; (accepted.state.pendingDecision is ChooseTargetsDecision) shouldBe true
        tokens(accepted.state).size shouldBe 0
        val result = choose(restore(accepted.state), listOf(p[2], p[1]))
        result.error shouldBe null; val ids = tokens(result.state); ids.size shouldBe 2; marked(result.state, ids)
        ids.forEach {
            result.state.getEntity(it)?.get<ControllerComponent>()?.playerId shouldBe p[0]
            result.state.getEntity(it)?.get<CardComponent>()?.name shouldBe bear.name
            result.state.getEntity(it)?.has<TappedComponent>() shouldBe true
        }
        ids.map { defender(result.state, it) } shouldBe listOf(p[2], p[1])
        result.state.delayedTriggers.size shouldBe 4
        // The remaining initial-counter replacement boundary must be an explicit technical error,
        // never a successful creation that silently loses the counters.
        val unqualified = castEffect(state, effect().copy(initialCounters = mapOf("+1/+1" to 1)))
        val counterQuestion = unqualified.pendingDecision as YesNoDecision
        val rejected = processor.process(unqualified.state, SubmitDecision(p[0], YesNoResponse(counterQuestion.id, true))).result
        (rejected.error?.contains("initial counters") == true) shouldBe true
        tokens(rejected.state).size shouldBe 0
    }
    test("TD14 active teammate chooses its own legal defenders while declaration restrictions do not prevent entry") {
        val state = base(4, team = true); val p = state.turnOrder
        val paused = castEffect(state, effect().copy(keywords = setOf(Keyword.DEFENDER)), EffectContext(null, p[1]))
        val decision = paused.pendingDecision as ChooseTargetsDecision
        decision.playerId shouldBe p[1]; decision.legalTargets[0] shouldBe p.drop(2)
        val result = choose(paused.state, listOf(p[2], p[3]))
        result.error shouldBe null
        val ids = tokens(result.state); ids.size shouldBe 2
        ids.map { defender(result.state, it) } shouldBe listOf(p[2], p[3])
        ids.forEach { result.state.getEntity(it)?.get<CardComponent>()?.baseKeywords?.contains(Keyword.DEFENDER) shouldBe true }
        CombatDefenders.legalAttackDefenders(result.state, p[1]).toSet() shouldBe p.drop(2).toSet()
    }
})
