package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Exercises the state-trigger latch (CR 603.8) on a source that survives resolution — the
 * one behaviour the Dandân / Island Fish Jasconius / Merchant Ship tests can't cover, since
 * those all sacrifice their source the instant the trigger resolves.
 *
 * The test card has a non-self-removing state-triggered ability ("When you control no
 * Islands, you gain 1 life"). With the condition permanently true (the controller never
 * holds an Island), the trigger must fire exactly once across many priority passes: the
 * [com.wingedsheep.engine.state.components.battlefield.StateTriggerLatchesComponent] latch
 * suppresses re-firing while the condition stays true. A broken latch would re-emit the
 * trigger at every priority check and inflate the life total well past +1.
 */
class StateTriggerLatchScenarioTest : ScenarioTestBase() {

    private val latchTestBeast = card("Latch Test Beast") {
        manaCost = "{2}{U}"
        typeLine = "Creature — Beast"
        power = 2
        toughness = 2

        stateTriggeredAbility {
            condition = Conditions.YouControl(
                GameObjectFilter.Land.withSubtype("Island"),
                negate = true,
            )
            effect = Effects.GainLife(1, EffectTarget.Controller)
            description = "When you control no Islands, you gain 1 life"
        }
    }

    init {
        cardRegistry.register(latchTestBeast)

        context("State-trigger latch — fires once while the condition stays true") {

            test("gains exactly 1 life despite many priority passes with no Islands") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Latch Test Beast", summoningSickness = false)
                    // Controller holds only non-Islands, so "control no Islands" is true for
                    // the whole turn and never resets the latch.
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startLife = game.getLifeTotal(1)

                // Walk all the way to end of turn: dozens of priority checks, each polling
                // the state trigger.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("latch must let the state trigger fire exactly once, not per priority pass") {
                    game.getLifeTotal(1) shouldBe startLife + 1
                }
            }
        }
    }
}
