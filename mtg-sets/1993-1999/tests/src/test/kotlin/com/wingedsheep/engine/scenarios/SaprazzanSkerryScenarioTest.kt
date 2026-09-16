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

class SaprazzanSkerryScenarioTest : ScenarioTestBase() {
    private val abilityId = cardRegistry.getCard("Saprazzan Skerry")!!.activatedAbilities.single().id

    private fun counters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.DEPLETION) ?: 0

    private fun blue(game: TestGame): Int =
        game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.blue ?: 0

    init {
        test("enters tapped with two depletion counters") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Saprazzan Skerry")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val card = game.findCardsInHand(1, "Saprazzan Skerry").single()
            game.execute(PlayLand(game.player1Id, card)).error shouldBe null
            val skerry = game.findPermanent("Saprazzan Skerry")
            skerry shouldNotBe null
            game.state.getEntity(skerry!!)?.get<TappedComponent>() shouldNotBe null
            counters(game, skerry) shouldBe 2
        }

        test("each activation adds two blue and the second sacrifices it") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Saprazzan Skerry")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val skerry = game.findPermanent("Saprazzan Skerry")!!
            game.state = game.state.updateEntity(skerry) { c ->
                c.with(CountersComponent().withAdded(CounterType.DEPLETION, 2))
            }

            game.execute(ActivateAbility(game.player1Id, skerry, abilityId)).error shouldBe null
            blue(game) shouldBe 2
            counters(game, skerry) shouldBe 1
            game.findPermanent("Saprazzan Skerry") shouldBe skerry

            game.state = game.state.updateEntity(skerry) { it.without<TappedComponent>() }
            game.execute(ActivateAbility(game.player1Id, skerry, abilityId)).error shouldBe null
            blue(game) shouldBe 4
            game.findPermanent("Saprazzan Skerry") shouldBe null
        }
    }
}
