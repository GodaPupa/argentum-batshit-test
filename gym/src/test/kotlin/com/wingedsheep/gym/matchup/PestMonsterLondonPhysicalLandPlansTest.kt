package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

/** Future physical drop order only; neither entry nor payment nor keep/bottom is asserted. */
class PestMonsterLondonPhysicalLandPlansTest : ScenarioTestBase() {
    private val main = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }
    private val bridge = PestMonsterLondonSetupBridge(cardRegistry)

    init {
        for (seat in 0..1) {
            test("London physical land plan seat ${seat + 1} uses only own visible handles") {
                val env = opening(seat)
                val actor = env.playerIds[seat]
                val epoch = ActorEpoch("monster-land-plan-v1", "seat-$seat", 0)
                val setup = bridge.project(env.state, actor, epoch, 0xC25160L + seat)
                val lands = setup.input.observation.zones.single {
                    it.ownerId == actor && it.zoneType == Zone.HAND
                }.cards.filter { "LAND" in it.types }.map { it.entityId }.toSet()
                val plans = PestMonsterLondonPhysicalLandPlans.enumerate(setup)
                val horizon = lands.size.coerceAtMost(3)
                val expectedCount = if (horizon == 0) 0 else
                    (0 until horizon).fold(1) { count, index -> count * (lands.size - index) }
                plans.size shouldBe expectedCount
                plans.all { plan -> plan.size == horizon && plan.distinct().size == horizon &&
                    plan.all { it in lands } } shouldBe true
                val hidden = env.state.getLibrary(actor).toSet()
                plans.flatten().any { it in hidden } shouldBe false
                val reversed = env.state.copy(zones = env.state.zones +
                    (ZoneKey(actor, Zone.LIBRARY) to env.state.getLibrary(actor).reversed()))
                PestMonsterLondonPhysicalLandPlans.enumerate(
                    bridge.project(reversed, actor, epoch, 0xC25160L + seat)) shouldBe plans
            }
            test("London physical land plan seat ${seat + 1} rejects altered hand order") {
                val env = opening(seat)
                val actor = env.playerIds[seat]
                val setup = bridge.project(env.state, actor,
                    ActorEpoch("monster-land-plan-v1", "seat-$seat", 0), 0xC25160L + seat)
                shouldThrow<IllegalArgumentException> {
                    PestMonsterLondonPhysicalLandPlans.enumerate(
                        setup.copy(ownHandOrder = setup.ownHandOrder.drop(1)))
                }
            }
        }
    }

    private fun opening(seat: Int): GameEnvironment =
        GameEnvironment.create(cardRegistry).also { env ->
            env.reset(GameConfig(
                players = (0..1).map { index -> PlayerConfig(
                    if (index == seat) "Monster" else "Fixture opponent",
                    Deck(cards = if (index == seat) main else List(60) { "Forest" }),
                ) },
                startingPlayerIndex = 0,
                seed = 0xC25170L + seat,
            ))
            if (seat == 1) env.stepExactlyOne(KeepHand(env.playerIds[0]))
        }
}
