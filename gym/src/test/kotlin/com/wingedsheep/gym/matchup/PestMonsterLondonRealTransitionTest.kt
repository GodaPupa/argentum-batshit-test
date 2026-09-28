package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

/**
 * Excluded real-engine action seam, not a sampled game or London policy choice.
 * The trusted fixture constructs the physical opening; only actor-visible own handles and
 * enumerated legal actions are used to choose land and cycle submissions.
 */
class PestMonsterLondonRealTransitionTest : ScenarioTestBase() {
    private val main = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val fillers = listOf("Rooftop Percher", "Boulderbranch Golem", "Bramble Wurm",
        "Ancient Stirrings", "Crop Rotation")

    init {
        for (seat in 0..1) {
            test("Monster seat ${seat + 1} submits actual legal Tower land and Ent cycling") {
                val env = GameEnvironment.create(cardRegistry)
                env.reset(GameConfig(
                    players = (0..1).map { index -> PlayerConfig(
                        if (index == seat) "Monster" else "Fixture opponent",
                        Deck(cards = if (index == seat) main else List(60) { "Forest" }),
                    ) }, startingPlayerIndex = seat, seed = 0xC25390L + seat,
                    skipMulligans = true,
                ))
                val actor = env.playerIds[seat]
                val (opening, _) = knownOpening(env.state, actor)
                // Trusted fixture enters the first main phase; this does not qualify turn advancement.
                env.restore(opening.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN), env.playerIds)
                val epoch = ActorEpoch("monster-real-transition-v1", "seat-$seat", 0)
                val input = adapter.build(env.state, actor,
                    completeActorLegalActions(env.state, actor, enumerator), epoch, 0xC25380L + seat)
                input.verifyBinding(epoch, actor)
                val own = input.observation.zones.single {
                    it.ownerId == actor && it.zoneType == Zone.HAND
                }
                val tower = own.cards.single { it.name == "Urza's Tower" }.entityId
                val ent = own.cards.single { it.name == "Generous Ent" }.entityId
                val hidden = env.state.getLibrary(actor).toSet()
                (tower in hidden || ent in hidden) shouldBe false

                fun submitWhenLegal(matches: (Any) -> Boolean) {
                    repeat(48) {
                        val legal = env.legalActions()
                        val chosen = legal.firstOrNull { matches(it.action) }
                        if (chosen != null) {
                            env.stepExactlyOne(chosen.action)
                            env.lastRejection shouldBe null
                            return
                        }
                        val pass = legal.firstOrNull { it.action is PassPriority }
                            ?: error("No lawful priority pass en route to opening action")
                        env.stepExactlyOne(pass.action)
                        env.lastRejection shouldBe null
                    }
                    error("Opening action never became legal within bounded pretest")
                }
                submitWhenLegal { it is PlayLand && it.playerId == actor && it.cardId == tower }
                (tower in env.state.getBattlefield()) shouldBe true
                submitWhenLegal { it is CycleCard && it.playerId == actor && it.cardId == ent }
                (ent in env.state.getHand(actor)) shouldBe false
                (tower in env.state.getBattlefield()) shouldBe true
            }
        }
    }

    private fun knownOpening(state: GameState, actor: EntityId): Pair<GameState, List<EntityId>> {
        val pool = state.getHand(actor) + state.getLibrary(actor)
        val chosen = mutableListOf<EntityId>()
        for (name in listOf("Urza's Tower", "Generous Ent") + fillers) {
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
}
