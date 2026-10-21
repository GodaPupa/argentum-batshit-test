package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Independent restricted-X sibling of the unchanged generic paid-activation
 * witness. A {U}{U}, {T}: Add {R}{R}{R} ability cannot be funded by Forests
 * simply because the activation's mana value is two.
 */
class ManualPrecisionR9MultiPipRestrictedActivationHeldoutTest : ScenarioTestBase() {
    private val blueCostRedBattery = card("R9 Restricted X Blue-Paid Red Battery") {
        typeLine = "Land"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{U}{U}"), Costs.Tap)
            effect = AddManaEffect(Color.RED, 3)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    init {
        cardRegistry.register(blueCostRedBattery)

        test("negative: two Forests cannot fund {U}{U} to supply red-restricted X=1") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, blueCostRedBattery.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{X}"),
                xValue = 1,
                xManaRestriction = setOf(Color.RED),
            ) shouldBe null
        }

        test("positive control: Mountain can pay red-restricted X=1") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solution = ManaSolver(cardRegistry).solve(
                game.state,
                game.player1Id,
                ManaCost.parse("{X}"),
                xValue = 1,
                xManaRestriction = setOf(Color.RED),
            )
            solution shouldNotBe null
            solution!!.xRestrictedManaSpent[Color.RED] shouldBe 1
        }
    }
}
