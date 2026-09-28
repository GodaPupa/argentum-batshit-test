package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
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

/** Conditional two-land payment certificate only; neither game nor London policy evidence. */
class PestMonsterLondonEarlyDevelopmentTest : ScenarioTestBase() {
    private val main = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }
    private val bridge = PestMonsterLondonSetupBridge(cardRegistry)
    private val fillers = listOf("Rooftop Percher", "Boulderbranch Golem", "Bramble Wurm",
        "Ancient Stirrings", "Crop Rotation")

    init {
        for (seat in 0..1) {
            test("London next-turn Tower Forest symbolic payment seat ${seat + 1}") {
                val env = opening(seat)
                val actor = env.playerIds[seat]
                val (state, hand) = knownOpening(env.state, actor, "Urza's Tower")
                val epoch = ActorEpoch("monster-early-development-v1", "seat-$seat", 0)
                val setup = bridge.project(state, actor, epoch, 0xC25280L + seat)
                val plan = PestMonsterLondonEarlyDevelopmentPlanner
                    .conditionalTowerForestNextTurn(setup)!!
                plan.firstLandId shouldBe hand[0]
                plan.acquisitionCardId shouldBe hand[1]
                plan.secondLandName shouldBe "Forest"
                plan.availableMana shouldBe listOf("{C}", "{G}")
                val hidden = state.getLibrary(actor).toSet()
                (plan.firstLandId in hidden || plan.acquisitionCardId in hidden) shouldBe false
                val reversed = state.copy(zones = state.zones +
                    (ZoneKey(actor, Zone.LIBRARY) to state.getLibrary(actor).reversed()))
                PestMonsterLondonEarlyDevelopmentPlanner.conditionalTowerForestNextTurn(
                    bridge.project(reversed, actor, epoch, 0xC25280L + seat)) shouldBe plan
            }
        }
    }

    private fun knownOpening(state: GameState, actor: EntityId, land: String):
        Pair<GameState, List<EntityId>> {
        val pool = state.getHand(actor) + state.getLibrary(actor)
        val chosen = mutableListOf<EntityId>()
        for (name in listOf(land, "Generous Ent") + fillers) {
            val id = pool.first { it !in chosen &&
                state.getEntity(it)!!.get<CardComponent>()!!.name == name }
            chosen += id
        }
        val handKey = ZoneKey(actor, Zone.HAND)
        val libraryKey = ZoneKey(actor, Zone.LIBRARY)
        val identities = state.objectIdentities.mapValues { (id, identity) ->
            when (id) {
                in chosen -> identity.copy(logicalZone = handKey)
                in pool -> identity.copy(logicalZone = libraryKey)
                else -> identity
            }
        }
        return state.copy(zones = state.zones +
            (handKey to chosen.toList()) + (libraryKey to pool.filterNot { it in chosen }),
            objectIdentities = identities) to chosen
    }

    private fun opening(seat: Int): GameEnvironment =
        GameEnvironment.create(cardRegistry).also { env ->
            env.reset(GameConfig(
                players = (0..1).map { index -> PlayerConfig(
                    if (index == seat) "Monster" else "Fixture opponent",
                    Deck(cards = if (index == seat) main else List(60) { "Forest" }),
                ) }, startingPlayerIndex = 0, seed = 0xC25290L + seat,
            ))
            if (seat == 1) env.stepExactlyOne(KeepHand(env.playerIds[0]))
        }
}
