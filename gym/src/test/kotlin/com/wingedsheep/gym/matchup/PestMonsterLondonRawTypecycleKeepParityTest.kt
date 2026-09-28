package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

/** Exact raw-controller keep comparison on four predeclared one-land typecycling frozen-main openings. */
class PestMonsterLondonRawTypecycleKeepParityTest : ScenarioTestBase() {
    private val main = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }
    private val bridge = PestMonsterLondonSetupBridge(cardRegistry)

    init {
        for (seat in 0..1) {
            for (keepLine in listOf(false, true)) {
                test("Monster seat ${seat + 1} Tower Ent exact raw keep=$keepLine and hidden-library permutation") {
                    val env = GameEnvironment.create(cardRegistry)
                    env.reset(GameConfig(players = (0..1).map { index ->
                        PlayerConfig(if (index == seat) "Monster" else "Fixture opponent",
                            Deck(if (index == seat) main else List(60) { "Forest" }))
                    }, startingPlayerIndex = seat, seed = 0xC254C0L + seat * 2 + if (keepLine) 1 else 0))
                    val actor = env.playerIds[seat]
                    val names = if (keepLine) listOf("Urza's Tower", "Generous Ent",
                        "Ancient Stirrings", "Crop Rotation", "Rooftop Percher",
                        "Boulderbranch Golem", "Bramble Wurm")
                    else listOf("Urza's Tower", "Generous Ent",
                        "Ancient Stirrings", "Ancient Stirrings", "Crop Rotation",
                        "Crop Rotation", "Rooftop Percher")
                    val state = arrange(env.state, actor, names)
                    val epoch = ActorEpoch("monster-raw-typecycle-keep-parity-v1",
                        "seat-$seat-keep-$keepLine", 0)
                    val setup = bridge.project(state, actor, epoch, 0xC254D0L + seat)
                    val hand = setup.input.observation.zones.single {
                        it.ownerId == actor && it.zoneType == Zone.HAND
                    }
                    val cards = hand.cards.associate { card ->
                        card.entityId to CardSummary(card.name, card.manaCost,
                            card.types.joinToString(" "))
                    }
                    val message = MulliganInfo(setup.ownHandOrder, 0, 0, cards)
                    val raw = EngineAiPlayerController(cardRegistry, actor, { state })
                        .decideMulligan(message)
                    PestMonsterLondonKeepPolicy.decide(setup, 0) shouldBe raw
                    raw shouldBe keepLine
                    val permuted = state.copy(zones = state.zones +
                        (ZoneKey(actor, Zone.LIBRARY) to state.getLibrary(actor).reversed()))
                    val again = bridge.project(permuted, actor, epoch, 0xC25490L + seat)
                    PestMonsterLondonKeepPolicy.decide(again, 0) shouldBe raw
                    EngineAiPlayerController(cardRegistry, actor, { permuted })
                        .decideMulligan(message) shouldBe raw
                }
            }
        }
    }

    private fun arrange(state: GameState, actor: EntityId, names: List<String>): GameState {
        val pool = state.getHand(actor) + state.getLibrary(actor)
        val chosen = mutableListOf<EntityId>()
        for (name in names) {
            chosen += pool.first { it !in chosen &&
                state.getEntity(it)!!.get<CardComponent>()!!.name == name }
        }
        val handKey = ZoneKey(actor, Zone.HAND)
        val libraryKey = ZoneKey(actor, Zone.LIBRARY)
        return state.copy(zones = state.zones +
            (handKey to chosen.toList()) + (libraryKey to pool.filterNot { it in chosen }),
            objectIdentities = state.objectIdentities.mapValues { (id, identity) ->
                when (id) {
                    in chosen -> identity.copy(logicalZone = handKey)
                    in pool -> identity.copy(logicalZone = libraryKey)
                    else -> identity
                }
            })
    }
}
