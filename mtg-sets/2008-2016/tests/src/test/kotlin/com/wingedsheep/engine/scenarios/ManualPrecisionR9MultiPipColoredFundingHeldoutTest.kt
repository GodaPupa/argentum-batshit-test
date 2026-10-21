package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
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

/** Immutable independent witness: a paid COLORED mana source also needs ALL activation pips. */
class ManualPrecisionR9MultiPipColoredFundingHeldoutTest : ScenarioTestBase() {
    private val genericPaidBattery = card("R9 Two-Cost Red Battery") {
        typeLine = "Land"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
            effect = AddManaEffect(Color.RED, 3)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }
    private val bluePaidBattery = card("R9 Double-Blue Red Battery") {
        typeLine = "Land"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{U}{U}"), Costs.Tap)
            effect = AddManaEffect(Color.RED, 3)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }
    private val genericSpell = card("R9 Colored Battery Three-Cost Creature") {
        manaCost = "{3}"
        typeLine = "Creature — Construct"
        power = 1
        toughness = 1
    }

    init {
        cardRegistry.register(listOf(genericPaidBattery, bluePaidBattery, genericSpell))

        test("negative: one Forest cannot fund {2} for paid red producer of three mana") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, genericPaidBattery.name)
                .withCardInHand(1, genericSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.state
            ManaSolver(cardRegistry).solve(game.state, game.player1Id, ManaCost.parse("{3}")) shouldBe null
            (game.castSpell(1, genericSpell.name).error != null) shouldBe true
            game.state shouldBe before
        }

        test("positive: two Forests fund {2} for paid red producer and cast {3}") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, genericPaidBattery.name)
                .withCardInHand(1, genericSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val solution = ManaSolver(cardRegistry).solve(game.state, game.player1Id, ManaCost.parse("{3}"))
            solution shouldNotBe null
            val sources = solution!!.sources
            sources.count { it.name == "Forest" } shouldBe 2
            sources.count { it.name == genericPaidBattery.name } shouldBe 1
            sources.map { it.entityId }.toSet().size shouldBe 3
            solution.manaProduced.values.count { it.color == Color.RED && it.amount == 3 } shouldBe 1
            game.castSpell(1, genericSpell.name).error shouldBe null
            for (source in sources) game.state.getEntity(source.entityId)?.has<TappedComponent>() shouldBe true
        }

        test("negative: two Forests cannot pay {U}{U} activation by treating it as generic") {
            val game = scenario().withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, bluePaidBattery.name)
                .withCardInHand(1, genericSpell.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.state
            ManaSolver(cardRegistry).solve(game.state, game.player1Id, ManaCost.parse("{3}")) shouldBe null
            (game.castSpell(1, genericSpell.name).error != null) shouldBe true
            game.state shouldBe before
        }
    }
}
