package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
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

/** Physical bottom parity on the separately declared Tower+Ent one-land opening family. */
class PestMonsterLondonRawTypecycleBottomParityTest : ScenarioTestBase() {
    private val main = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }
    private val bridge = PestMonsterLondonSetupBridge(cardRegistry)

    init {
        for (seat in 0..1) {
            for (keepLine in listOf(false, true)) {
                for (count in 1..2) {
                    test("Monster seat ${seat + 1} Tower Ent keep=$keepLine bottom=$count exact physical parity and hidden-library permutation") {
                        val env = GameEnvironment.create(cardRegistry)
                        env.reset(GameConfig(players = (0..1).map { index ->
                            PlayerConfig(if (index == seat) "Monster" else "Fixture opponent",
                                Deck(if (index == seat) main else List(60) { "Forest" }))
                        }, startingPlayerIndex = seat,
                            seed = 0xC254E0L + seat * 4 + (if (keepLine) 2 else 0) + count))
                        val actor = env.playerIds[seat]
                        val names = if (keepLine) listOf("Urza's Tower", "Generous Ent",
                            "Ancient Stirrings", "Crop Rotation", "Rooftop Percher",
                            "Boulderbranch Golem", "Bramble Wurm")
                        else listOf("Urza's Tower", "Generous Ent", "Ancient Stirrings",
                            "Ancient Stirrings", "Crop Rotation", "Crop Rotation", "Rooftop Percher")
                        val state = arrange(env.state, actor, names)
                        val epoch = ActorEpoch("monster-raw-typecycle-bottom-parity-v1",
                            "seat-$seat-keep-$keepLine-bottom-$count", 0)
                        val setup = bridge.project(state, actor, epoch, 0xC254F0L + seat)
                        val hand = setup.input.observation.zones.single {
                            it.ownerId == actor && it.zoneType == Zone.HAND
                        }
                        val cards = hand.cards.associate { card ->
                            card.entityId to CardSummary(card.name, card.manaCost,
                                card.types.joinToString(" "))
                        }
                        val message = BottomCardsInfo(setup.ownHandOrder, count, cards)
                        val raw = EngineAiPlayerController(cardRegistry, actor, { state })
                            .chooseBottomCards(message)
                        PestMonsterLondonBottomPolicy.choose(setup, count) shouldBe raw
                        raw.size shouldBe count
                        raw.any { it in state.getLibrary(actor).toSet() } shouldBe false
                        val permuted = state.copy(zones = state.zones +
                            (ZoneKey(actor, Zone.LIBRARY) to state.getLibrary(actor).reversed()))
                        val again = bridge.project(permuted, actor, epoch, 0xC254F0L + seat)
                        PestMonsterLondonBottomPolicy.choose(again, count) shouldBe raw
                        EngineAiPlayerController(cardRegistry, actor, { permuted })
                            .chooseBottomCards(message) shouldBe raw
                    }
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
