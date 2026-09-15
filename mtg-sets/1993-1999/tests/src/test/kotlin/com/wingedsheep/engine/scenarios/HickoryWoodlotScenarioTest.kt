package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class HickoryWoodlotScenarioTest : ScenarioTestBase() {
    private val abilityId = cardRegistry.getCard("Hickory Woodlot")!!.activatedAbilities.single().id

    private fun counters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.DEPLETION) ?: 0

    private fun green(game: TestGame): Int =
        game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.green ?: 0

    init {
        test("enters tapped with two depletion counters") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Hickory Woodlot")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val card = game.findCardsInHand(1, "Hickory Woodlot").single()
            game.execute(PlayLand(game.player1Id, card)).error shouldBe null
            val woodlot = game.findPermanent("Hickory Woodlot")
            woodlot shouldNotBe null
            game.state.getEntity(woodlot!!)?.get<TappedComponent>() shouldNotBe null
            counters(game, woodlot) shouldBe 2
        }

        test("each activation adds two green and the second sacrifices it") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Hickory Woodlot")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val woodlot = game.findPermanent("Hickory Woodlot")!!
            game.state = game.state.updateEntity(woodlot) { c ->
                c.with(CountersComponent().withAdded(CounterType.DEPLETION, 2))
            }

            game.execute(ActivateAbility(game.player1Id, woodlot, abilityId)).error shouldBe null
            green(game) shouldBe 2
            counters(game, woodlot) shouldBe 1
            game.findPermanent("Hickory Woodlot") shouldBe woodlot

            game.state = game.state.updateEntity(woodlot) { it.without<TappedComponent>() }
            game.execute(ActivateAbility(game.player1Id, woodlot, abilityId)).error shouldBe null
            green(game) shouldBe 4
            game.findPermanent("Hickory Woodlot") shouldBe null
        }
    }
}
