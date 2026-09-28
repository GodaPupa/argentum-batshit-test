package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

/** Construction-only projection; it submits no official action and proves no policy parity. */
class PestMonsterLondonSetupBridgeTest : ScenarioTestBase() {
    private val main = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }
    private val bridge = PestMonsterLondonSetupBridge(cardRegistry)

    init {
        for (seat in 0..1) {
            test("London setup bridge seat ${seat + 1} carries own order without hidden library handles") {
                val env = opening(seat, main)
                val actor = env.playerIds[seat]
                val state = env.state
                val epoch = ActorEpoch("monster-london-setup-bridge-v1", "seat-$seat", 0)
                val setup = bridge.project(state, actor, epoch, 0xC25140L + seat)
                setup.ownHandOrder shouldBe state.getHand(actor)
                val hidden = state.getLibrary(actor).toSet()
                setup.ownHandOrder.any { it in hidden } shouldBe false
                val library = setup.input.observation.zones.single {
                    it.ownerId == actor && it.zoneType == Zone.LIBRARY
                }
                library.cards.isEmpty() shouldBe true
                setup.printedForestExists shouldBe state.getLibrary(actor).any { id ->
                    state.getEntity(id)!!.get<CardComponent>()!!.name == "Forest"
                }
                val reordered = state.copy(zones = state.zones +
                    (ZoneKey(actor, Zone.LIBRARY) to state.getLibrary(actor).reversed()))
                val again = bridge.project(reordered, actor, epoch, 0xC25140L + seat)
                again.ownHandOrder shouldBe setup.ownHandOrder
                again.printedForestExists shouldBe setup.printedForestExists
            }
            test("London setup bridge seat ${seat + 1} rejects changed loaded main") {
                val changed = main.toMutableList().apply { this[indexOf("Forest")] = "Island" }
                val env = opening(seat, changed)
                shouldThrow<IllegalArgumentException> {
                    bridge.project(env.state, env.playerIds[seat],
                        ActorEpoch("monster-london-setup-bridge-v1", "seat-$seat", 0),
                        0xC25140L + seat)
                }
            }
        }
    }

    private fun opening(seat: Int, cards: List<String>): GameEnvironment =
        GameEnvironment.create(cardRegistry).also { env ->
            env.reset(GameConfig(
                players = (0..1).map { index -> PlayerConfig(
                    if (index == seat) "Monster" else "Fixture opponent",
                    Deck(cards = if (index == seat) cards else List(60) { "Forest" }),
                ) },
                startingPlayerIndex = 0,
                seed = 0xC25150L + seat,
            ))
            if (seat == 1) env.stepExactlyOne(KeepHand(env.playerIds[0]))
        }
}
