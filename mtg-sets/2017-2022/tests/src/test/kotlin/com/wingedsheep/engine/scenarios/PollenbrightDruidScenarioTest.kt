package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class PollenbrightDruidScenarioTest : ScenarioTestBase() {
    private fun counters(game: TestGame, name: String): Int {
        val permanent = game.findPermanent(name)!!
        return game.state.getEntity(permanent)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        test("its first ETB mode puts a +1/+1 counter on the chosen creature") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Pollenbright Druid")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val target = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Pollenbright Druid").error shouldBe null
            game.resolveStack()
            val mode = game.getPendingDecision() as ChooseOptionDecision
            game.submitDecision(OptionChosenResponse(mode.id, optionIndex = 0))
            val targets = game.getPendingDecision() as ChooseTargetsDecision
            game.submitDecision(TargetsResponse(targets.id, mapOf(0 to listOf(target))))
            game.resolveStack()

            counters(game, "Grizzly Bears") shouldBe 1
        }

        test("its second ETB mode proliferates a selected permanent") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Pollenbright Druid")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val target = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(target) { container ->
                container.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
            }

            game.castSpell(1, "Pollenbright Druid").error shouldBe null
            game.resolveStack()
            val mode = game.getPendingDecision() as ChooseOptionDecision
            game.submitDecision(OptionChosenResponse(mode.id, optionIndex = 1))
            game.resolveStack()
            val select = game.getPendingDecision()!!
            game.submitDecision(CardsSelectedResponse(select.id, listOf(target)))
            game.resolveStack()

            counters(game, "Grizzly Bears") shouldBe 2
        }
    }
}
