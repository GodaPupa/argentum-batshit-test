package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ParadiseDruidScenarioTest : ScenarioTestBase() {
    init {
        test("untapped hexproof prevents an opponent spell") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Paradise Druid")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(2, "Lightning Bolt", game.findPermanent("Paradise Druid")!!).error shouldNotBe null
        }

        test("tapped druid can be targeted and destroyed") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Paradise Druid", tapped = true)
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(2, "Lightning Bolt", game.findPermanent("Paradise Druid")!!).error shouldBe null
            game.resolveStack()
            game.findPermanent("Paradise Druid") shouldBe null
        }

        test("activated mana ability produces the chosen color without using the stack") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Paradise Druid")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val ability = cardRegistry.getCard("Paradise Druid")!!.script.activatedAbilities.single()
            game.execute(ActivateAbility(
                game.player1Id, game.findPermanent("Paradise Druid")!!, ability.id,
                manaColorChoice = Color.BLUE
            )).error shouldBe null
            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.blue shouldBe 1
            game.state.stack.size shouldBe 0
        }

        test("summoning sickness forbids the tap mana ability") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardOnBattlefield(1, "Paradise Druid", summoningSickness = true)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val ability = cardRegistry.getCard("Paradise Druid")!!.script.activatedAbilities.single()
            game.execute(ActivateAbility(
                game.player1Id, game.findPermanent("Paradise Druid")!!, ability.id,
                manaColorChoice = Color.GREEN
            )).error shouldNotBe null
        }
    }
}
