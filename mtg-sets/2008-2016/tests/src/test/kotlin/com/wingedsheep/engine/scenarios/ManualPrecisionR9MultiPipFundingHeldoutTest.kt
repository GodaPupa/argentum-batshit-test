package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Independent heldout for the R9 bounded two-source payment planner.
 *
 * A paid mana mode with {2}, {T} cannot be financed by a single Forest.
 * It needs TWO distinct, actually available mana units before its own production
 * can pay the outer spell. Preserve the original GREEN R9 and R5 sources/tests.
 */
class ManualPrecisionR9MultiPipFundingHeldoutTest : ScenarioTestBase() {
    private val twoCostBattery = card("R9 Two-Cost Colorless Battery") {
        typeLine = "Land"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
            effect = AddColorlessManaEffect(3)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    private val threeCostCreature = card("R9 Three-Cost Payment Witness") {
        manaCost = "{3}"
        typeLine = "Creature — Construct"
        power = 1
        toughness = 1
    }

    init {
        cardRegistry.register(listOf(twoCostBattery, threeCostCreature))

        test("negative: paid {2} activation cannot finance itself") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, twoCostBattery.name)
                .withCardInHand(1, threeCostCreature.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.state

            ManaSolver(cardRegistry).solve(
                game.state, game.player1Id, ManaCost.parse("{3}")
            ) shouldBe null
            (game.castSpell(1, threeCostCreature.name).error != null) shouldBe true
            game.state shouldBe before
        }

        test("negative: one Forest cannot cover a paid {2} activation for outer {3}") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, twoCostBattery.name)
                .withCardInHand(1, threeCostCreature.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.state

            // One Forest supplies one mana, NOT the full {2} activation.
            ManaSolver(cardRegistry).solve(
                game.state, game.player1Id, ManaCost.parse("{3}")
            ) shouldBe null
            (game.castSpell(1, threeCostCreature.name).error != null) shouldBe true
            game.state shouldBe before
        }

        test("positive: two distinct Forests fund {2}, battery produces exactly {C}{C}{C}") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, twoCostBattery.name)
                .withCardInHand(1, threeCostCreature.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val startingLife = game.state.lifeTotal(game.player1Id)
            val quote = ManaSolver(cardRegistry).solve(
                game.state, game.player1Id, ManaCost.parse("{3}")
            )
            quote shouldNotBe null

            // These are three DIFFERENT physical sources: two external funders
            // and one producer. No phantom free activation or duplicated source.
            val sources = quote!!.sources
            sources.count { it.name == "Forest" } shouldBe 2
            sources.count { it.name == twoCostBattery.name } shouldBe 1
            sources.map { it.entityId }.toSet().size shouldBe 3
            quote.manaProduced.values.sumOf { it.colorless } shouldBe 3

            val result = game.castSpell(1, threeCostCreature.name)
            result.error shouldBe null
            game.state.lifeTotal(game.player1Id) shouldBe startingLife
            for (source in sources) {
                game.state.getEntity(source.entityId)?.has<TappedComponent>() shouldBe true
            }
            // All three colorless mana pay the outer spell; none floats freely.
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.colorless shouldBe 0
        }
    }
}
