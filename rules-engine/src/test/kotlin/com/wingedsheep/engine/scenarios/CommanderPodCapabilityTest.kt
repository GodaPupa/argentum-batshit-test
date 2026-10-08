package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Synthetic fixtures only: no actual experiment decks or performance seeds. */
class CommanderPodCapabilityTest : FunSpec({
    val fixture = card("Pod Capability Fixture Commander") {
        manaCost = "{0}"
        typeLine = "Legendary Creature — Human"
        power = 1
        toughness = 1
    }
    val registry = CardRegistry().apply { register(TestCards.all); register(fixture) }
    fun setup(start: Int = 0, skip: Boolean = true): InitializationResult =
        GameInitializer(registry).initializeGame(GameConfig(
            players = (0..3).map { seat ->
                if (seat == 0) {
                    PlayerConfig(
                        "Fixture seat 0",
                        Deck.of("Forest" to 98),
                        commanderCardNames = listOf(fixture.name, "Grizzly Bears"),
                    )
                } else {
                    PlayerConfig("Fixture seat $seat", Deck.of("Forest" to 99), commanderCardName = fixture.name)
                }
            },
            format = Format.Commander(),
            startingPlayerIndex = start,
            skipMulligans = skip,
            useHandSmoother = false,
            seed = 0x4D545032434150L,
        ))
    fun submit(state: GameState, action: GameAction): GameState {
        val result = ActionProcessor(registry).process(state, action).result
        result.error shouldBe null
        return result.newState
    }

    test("four-player commander setup preserves exact seat rotation and 100 physical cards per player") {
        for (start in 0..3) {
            val initialized = setup(start)
            val ids = initialized.playerIds
            initialized.state.turnOrder shouldBe ids.drop(start) + ids.take(start)
            ids.forEachIndexed { seat, pid ->
                initialized.state.lifeTotal(pid) shouldBe 40
                initialized.state.getHand(pid).size shouldBe 7
                initialized.state.getLibrary(pid).size shouldBe if (seat == 0) 91 else 92
                initialized.state.getZone(pid, Zone.COMMAND).size shouldBe if (seat == 0) 2 else 1
            }
        }
    }
    test("every player receives the multiplayer free-mulligan flag") {
        val initialized = setup(skip = false)
        initialized.playerIds.forEach { pid ->
            initialized.state.getEntity(pid)?.get<MulliganStateComponent>()?.freeMulligan shouldBe true
            initialized.state.getEntity(pid)?.get<MulliganStateComponent>()?.hasKept shouldBe false
        }
    }
    test("an on-stack commander waits for all four priority passes") {
        val initialized = setup()
        val ids = initialized.playerIds
        var state = initialized.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN)
        val commander = state.getZone(ids[0], Zone.COMMAND).single { id ->
            state.getEntity(id)?.get<CardComponent>()?.name == fixture.name
        }
        state = submit(state, CastSpell(ids[0], commander))
        state.stack.contains(commander) shouldBe true
        for (index in 0..2) {
            state.priorityPlayerId shouldBe ids[index]
            state = submit(state, PassPriority(ids[index]))
            state.stack.contains(commander) shouldBe true
        }
        state.priorityPlayerId shouldBe ids[3]
        state = submit(state, PassPriority(ids[3]))
        state.stack.contains(commander) shouldBe false
        state.getBattlefield().contains(commander) shouldBe true
    }
    test("one concession does not end a four-player pod and the last survivor ends it") {
        val initialized = setup()
        var state = initialized.state
        for (index in 0..2) {
            state = submit(state, Concede(initialized.playerIds[index]))
            state.gameOver shouldBe (index == 2)
        }
    }
    test("fixed-seed four-player initial state serializes deterministically") {
        // Production persistence and the shared test bridge both enable structured map keys because
        // GameState.zones is keyed by ZoneKey. The capability gate must use the same contract.
        val json = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
        val first = json.encodeToString(GameState.serializer(), setup().state)
        val second = json.encodeToString(GameState.serializer(), setup().state)
        first shouldBe second
        json.encodeToString(GameState.serializer(), json.decodeFromString(GameState.serializer(), first)) shouldBe first
    }
})
