package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

/** Construction fixtures only: no official seed, claim, London policy or game. */
class PestMonsterLondonLoadedMainBindingTest : ScenarioTestBase() {
    private val frozen = PestControlTierOneMonsterTronAdmission.mainCounts.flatMap { (name, count) ->
        List(count) { name }
    }

    init {
        for (seat in 0..1) {
            test("London runner seat ${seat + 1} binds loaded Monster main before policy") {
                val environment = opening(seat, frozen)
                PestMonsterLondonLoadedMainBinding.verify(environment.state,
                    environment.playerIds[seat]) shouldBe PEST_MONSTER_TRON_MAIN_SHA256
            }
            test("London runner seat ${seat + 1} rejects changed loaded Monster main") {
                val changed = frozen.toMutableList().apply { this[indexOf("Forest")] = "Island" }
                val environment = opening(seat, changed)
                shouldThrow<IllegalArgumentException> {
                    PestMonsterLondonLoadedMainBinding.verify(environment.state,
                        environment.playerIds[seat])
                }
            }
        }
    }

    private fun opening(seat: Int, cards: List<String>): GameEnvironment =
        GameEnvironment.create(cardRegistry).also { environment ->
            environment.reset(GameConfig(
                players = (0..1).map { index -> PlayerConfig(
                    if (index == seat) "Monster" else "Fixture opponent",
                    Deck(cards = if (index == seat) cards else List(60) { "Forest" }),
                ) },
                startingPlayerIndex = 0,
                seed = 0xC25130L + seat,
            ))
        }
}
