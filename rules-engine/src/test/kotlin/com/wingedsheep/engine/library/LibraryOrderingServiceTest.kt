package com.wingedsheep.engine.library

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.ShuffleCause
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.library.ShuffleLibraryExecutor
import com.wingedsheep.engine.mechanics.library.LibraryOrderingCause
import com.wingedsheep.engine.mechanics.library.LibraryOrderingService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingComponent
import com.wingedsheep.engine.state.components.player.LibraryOrderingPlan
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ShuffleLibraryEffect
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

/** Synthetic regression domain only. No project corpus, allocation or matchup seed is read. */
class LibraryOrderingServiceTest : FunSpec({
    val registry = CardRegistry().also { it.register(CardDefinition.creature(
        name = "Ordering Bear", manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")), power = 2, toughness = 2,
    )) }
    val deck = Deck.of("Ordering Bear" to 20)
    val labels = (1..20).map { "Ordering Bear#$it" }
    val plan = LibraryOrderingPlan("IW_V2_ORDERING_REGRESSION_ONLY", 999, listOf(
        labels, labels.reversed(), labels.drop(5) + labels.take(5), labels.drop(10) + labels.take(10),
    ))
    fun config(order: LibraryOrderingPlan? = plan, hand: Int = 7) = GameConfig(
        players = listOf(PlayerConfig("Ordered", deck, libraryOrdering = order), PlayerConfig("Ordinary", deck)),
        startingHandSize = hand, startingPlayerIndex = 0, seed = 9_250_925_004L,
    )
    fun init(order: LibraryOrderingPlan? = plan, hand: Int = 7) = GameInitializer(registry).initializeGame(config(order, hand))
    fun GameState.order(player: EntityId) = getEntity(player)!!.get<LibraryOrderingComponent>()!!
    fun GameState.labels(player: EntityId, cards: List<EntityId>) = cards.map { order(player).originalCopies.getValue(it) }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    fun encoded(state: GameState) = json.encodeToString(GameState.serializer(), state)
    fun effect(state: GameState, player: EntityId) = ShuffleLibraryExecutor().execute(
        state, ShuffleLibraryEffect(), EffectContext(sourceId = null, controllerId = player),
    )

    test("frozen initialization orders the actual opening draw and its event IDs") {
        val result = init()
        val player = result.playerIds[0]
        result.state.labels(player, result.state.getHand(player)) shouldBe labels.take(7)
        result.state.labels(player, result.state.getLibrary(player)) shouldBe labels.drop(7)
        val draw = result.events.filterIsInstance<CardsDrawnEvent>().single { it.playerId == player }
        draw.cardIds shouldBe result.state.getHand(player)
        result.events.filterIsInstance<LibraryShuffledEvent>().single { it.playerId == player }.cause shouldBe ShuffleCause.GAME_SETUP
        result.state.order(player).setupComplete shouldBe true
        result.state.getEntity(result.playerIds[1])!!.has<LibraryOrderingComponent>() shouldBe false
        result.state.rng shouldBe init(null).state.rng
    }

    test("three real London mulligans consume the next frozen orders then legally keep four") {
        val initial = init()
        val player = initial.playerIds[0]
        val processor = ActionProcessor(registry)
        var state = initial.state
        repeat(3) { index ->
            val result = processor.process(state, TakeMulligan(player)).result
            result.error shouldBe null
            state = result.newState
            state.labels(player, state.getHand(player)) shouldBe plan.openingOrders[index + 1].take(7)
            result.events.filterIsInstance<CardsDrawnEvent>().single().cardIds shouldBe state.getHand(player)
            result.events.filterIsInstance<LibraryShuffledEvent>().single().cause shouldBe ShuffleCause.MULLIGAN
            state.order(player).mulligansUsed shouldBe index + 1
        }
        val kept = processor.process(state, KeepHand(player)).result
        kept.error shouldBe null
        val bottom = kept.newState.getHand(player).takeLast(3)
        val finished = processor.process(kept.newState, BottomCards(player, bottom)).result
        finished.error shouldBe null
        finished.newState.getHand(player).size shouldBe 4
        finished.newState.getLibrary(player).takeLast(3) shouldBe bottom
        finished.newState.order(player).mulligansUsed shouldBe 3
        finished.newState.getEntity(player)!!.get<MulliganStateComponent>()!!.hasKept shouldBe true
    }

    test("a fourth mulligan fails closed without changing the immutable input state") {
        val initial = init()
        val player = initial.playerIds[0]
        val processor = ActionProcessor(registry)
        var state = initial.state
        repeat(3) { state = processor.process(state, TakeMulligan(player)).result.newState }
        val before = encoded(state)
        shouldThrow<IllegalArgumentException> { processor.process(state, TakeMulligan(player)) }
        encoded(state) shouldBe before
    }

    test("absent component preserves the exact prior shuffle state RNG and JSON bytes") {
        val result = init(null)
        val player = result.playerIds[0]
        val key = ZoneKey(player, Zone.LIBRARY)
        val (oldOrder, advanced) = result.state.nextRandom { shuffle(result.state.getZone(key)) }
        val expected = advanced.reorderZone(key, oldOrder)
        LibraryOrderingCause.entries.forEach { cause ->
            val actual = LibraryOrderingService.shuffle(result.state, player, cause)
            actual shouldBe expected
            encoded(actual) shouldBe encoded(expected)
        }
    }

    test("effect shuffles use independently computed hash ranks and clear positional reveals") {
        val result = init(hand = 0)
        val player = result.playerIds[0]
        val marked = result.state.updateEntity(result.state.getLibrary(player).first()) {
            it.with(RevealedToComponent(setOf(player)))
        }
        val first = effect(marked, player)
        first.error shouldBe null
        // Reference ranks were computed independently with Python hashlib, outside this implementation.
        val rank1 = listOf(16, 5, 10, 12, 13, 6, 20, 8, 4, 14, 1, 7, 19, 17, 15, 11, 9, 3, 18, 2)
        val rank2 = listOf(15, 20, 18, 19, 17, 13, 2, 5, 14, 6, 7, 11, 4, 12, 9, 1, 16, 3, 10, 8)
        first.newState.labels(player, first.newState.getLibrary(player)) shouldBe rank1.map { "Ordering Bear#$it" }
        first.newState.getLibrary(player).all { !first.newState.getEntity(it)!!.has<RevealedToComponent>() } shouldBe true
        first.events.filterIsInstance<LibraryShuffledEvent>().single().cause shouldBe ShuffleCause.SPELL_OR_ABILITY
        first.newState.order(player).effectShufflesUsed shouldBe 1
        val second = effect(first.newState, player)
        second.newState.labels(player, second.newState.getLibrary(player)) shouldBe rank2.map { "Ordering Bear#$it" }
        second.newState.order(player).effectShufflesUsed shouldBe 2
    }

    test("effect ordering ignores current order and retains copy labels after zone changes") {
        val result = init(hand = 0)
        val player = result.playerIds[0]
        val key = ZoneKey(player, Zone.LIBRARY)
        val before = result.state
        val card = before.getLibrary(player).first()
        val away = before.removeFromZone(key, card).addToZone(ZoneKey(player, Zone.GRAVEYARD), card)
        val back = away.removeFromZone(ZoneKey(player, Zone.GRAVEYARD), card).addToZone(key, card)
        val reversed = before.reorderZone(key, before.getLibrary(player).reversed())
        effect(back, player).newState.getLibrary(player) shouldBe effect(reversed, player).newState.getLibrary(player)
        effect(back, player).newState.order(player).originalCopies shouldBe before.order(player).originalCopies
        effect(away, player).newState.getLibrary(player) shouldBe effect(before, player).newState.getLibrary(player).filterNot { it == card }
    }

    test("whole state serialization resumes a real London action and shuffle deterministically") {
        val initial = init()
        val player = initial.playerIds[0]
        val processor = ActionProcessor(registry)
        val first = processor.process(initial.state, TakeMulligan(player)).result.newState
        val restored = json.decodeFromString(GameState.serializer(), encoded(first))
        restored shouldBe first
        encoded(restored) shouldBe encoded(first)
        val next = processor.process(first, TakeMulligan(player)).result
        val replay = processor.process(restored, TakeMulligan(player)).result
        next.newState shouldBe replay.newState
        next.events shouldBe replay.events
        val shuffled = effect(next.newState, player)
        val shuffledReplay = effect(replay.newState, player)
        encoded(shuffled.newState) shouldBe encoded(shuffledReplay.newState)
        shuffled.events shouldBe shuffledReplay.events
    }

    test("matched original copy ranks remain coupled across different card counts and seats") {
        val smallerLabels = labels.take(17)
        val smallerPlan = plan.copy(openingOrders = plan.openingOrders.map { order -> order.filter { it in smallerLabels } })
        val smaller = GameInitializer(registry).initializeGame(config().copy(
            players = listOf(PlayerConfig("Ordinary", deck), PlayerConfig("Smaller", Deck.of("Ordering Bear" to 17), libraryOrdering = smallerPlan)),
            startingHandSize = 0, startingPlayerIndex = 0,
        ))
        val original = init(hand = 0)
        val p = original.playerIds[0]
        val q = smaller.playerIds[1]
        val a = effect(original.state, p).newState
        val b = effect(smaller.state, q).newState
        b.labels(q, b.getLibrary(q)) shouldBe a.labels(p, a.getLibrary(p)).filter { it in smallerLabels }
        smaller.state.activePlayerId shouldNotBe q
    }

    test("duplicate missing or wrong-domain opening labels are rejected") {
        shouldThrow<IllegalArgumentException> { plan.copy(openingOrders = listOf(labels + labels.first())) }
        shouldThrow<IllegalArgumentException> { init(plan.copy(openingOrders = listOf(labels.dropLast(1)))) }
        shouldThrow<IllegalArgumentException> { init(plan.copy(openingOrders = listOf(labels.dropLast(1) + "Other#1"))) }
        shouldThrow<IllegalArgumentException> { plan.copy(namespace = "ambiguous\nnamespace") }
    }

    test("opt-in rejects entropy seeds hand smoothing and cards outside the frozen original domain") {
        shouldThrow<IllegalArgumentException> { GameInitializer(registry).initializeGame(config().copy(seed = null)) }
        shouldThrow<IllegalArgumentException> { GameInitializer(registry).initializeGame(config().copy(useHandSmoother = true)) }
        val result = init()
        val player = result.playerIds[0]
        val foreign = result.state.getLibrary(result.playerIds[1]).first()
        val bad = result.state.addToZone(ZoneKey(player, Zone.LIBRARY), foreign)
        shouldThrow<IllegalArgumentException> { effect(bad, player) }
        shouldThrow<IllegalArgumentException> { LibraryOrderingService.shuffle(result.state, player, LibraryOrderingCause.GAME_SETUP) }
    }

    test("empty and singleton effect shuffles still record one actual shuffle event") {
        listOf(19, 20).forEach { hand ->
            val initial = init(hand = hand)
            val player = initial.playerIds[0]
            val result = effect(initial.state, player)
            result.error shouldBe null
            result.newState.getLibrary(player) shouldBe initial.state.getLibrary(player)
            result.newState.order(player).effectShufflesUsed shouldBe 1
            result.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
        }
    }
})
