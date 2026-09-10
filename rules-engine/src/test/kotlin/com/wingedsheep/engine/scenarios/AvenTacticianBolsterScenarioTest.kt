package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Aven Tactician's Bolster 1 (CR 701.36).
 *
 * Aven Tactician: {4}{W} Creature — Bird Soldier 2/3, Flying.
 * "When this creature enters, bolster 1." — the controller puts a +1/+1 counter on
 * a creature with the least toughness among creatures they control (the controller
 * breaks ties). Bolster is non-targeting.
 */
class AvenTacticianBolsterScenarioTest : ScenarioTestBase() {

    // A 1/1 vanilla creature — strictly lower toughness than the entering 2/3 Tactician,
    // so bolster has a single unambiguous least-toughness target.
    private val tinyGoblin = card("Tiny Test Goblin") {
        manaCost = "{R}"
        typeLine = "Creature — Goblin"
        power = 1
        toughness = 1
    }

    init {
        cardRegistry.register(tinyGoblin)

        context("Aven Tactician — bolster 1 on enter") {

            test("bolster puts a +1/+1 counter on the least-toughness creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    // A 1/1 is strictly the least toughness on board: smaller than the
                    // entering 2/3 Tactician, so no tie / no ambiguity.
                    .withCardOnBattlefield(1, "Tiny Test Goblin", summoningSickness = false)
                    .withCardInHand(1, "Aven Tactician")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val goblinId = game.findPermanent("Tiny Test Goblin")!!

                val cast = game.castSpell(1, "Aven Tactician")
                withClue("Casting Aven Tactician should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // Bolster resolves as a choice; with a single least-toughness creature the
                // selection is unambiguous. Answer it if the engine surfaces a decision.
                if (game.state.pendingDecision != null) {
                    game.selectCards(listOf(goblinId))
                    game.resolveStack()
                }

                withClue("Aven Tactician should have resolved onto the battlefield") {
                    game.isOnBattlefield("Aven Tactician") shouldBe true
                }

                val counters = game.state.getEntity(goblinId)?.get<CountersComponent>()
                val plusOne = counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
                withClue("Tiny Test Goblin (least toughness) should have the bolster +1/+1 counter, had $plusOne") {
                    plusOne shouldBe 1
                }

                // The entering Tactician (toughness 3) must NOT be bolstered.
                val tacticianId = game.findPermanent("Aven Tactician")!!
                tacticianId shouldNotBe goblinId
                val tacCounters = game.state.getEntity(tacticianId)?.get<CountersComponent>()
                withClue("The entering Tactician is not the least toughness and should not be bolstered") {
                    (tacCounters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
            }
        }
    }
}
