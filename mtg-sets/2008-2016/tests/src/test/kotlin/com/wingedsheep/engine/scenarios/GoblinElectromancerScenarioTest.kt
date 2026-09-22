package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GoblinElectromancerScenarioTest : ScenarioTestBase() {

    private val genericInstant = card("Electromancer Generic Probe") {
        manaCost = "{1}{U}"
        colorIdentity = "U"
        typeLine = "Instant"
        oracleText = "Draw a card."
        spell { effect = Effects.DrawCards(1) }
    }

    private val coloredInstant = card("Electromancer Colored Probe") {
        manaCost = "{U}"
        colorIdentity = "U"
        typeLine = "Instant"
        oracleText = "Draw a card."
        spell { effect = Effects.DrawCards(1) }
    }

    init {
        cardRegistry.register(listOf(genericInstant, coloredInstant))

        context("Goblin Electromancer cost reduction") {

            test("reduces the generic portion of an instant by one") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Goblin Electromancer")
                    .withCardInHand(1, "Electromancer Generic Probe")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpell(1, "Electromancer Generic Probe")
                withClue("one Island should pay the reduced {U} total cost: ${result.error}") {
                    result.error shouldBe null
                }
            }

            test("does not reduce colored mana symbols") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Goblin Electromancer")
                    .withCardInHand(1, "Electromancer Colored Probe")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpell(1, "Electromancer Colored Probe")
                withClue("a {U} spell still requires blue mana") {
                    result.error shouldNotBe null
                }
            }
        }
    }
}
