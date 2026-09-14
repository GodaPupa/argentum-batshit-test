package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.one.cards.RustvineCultivator
import com.wingedsheep.sdk.core.ChosenTarget
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RustvineCultivatorScenarioTest : FunSpec({
    val addOilAbility = RustvineCultivator.activatedAbilities[0].id
    val untapLandAbility = RustvineCultivator.activatedAbilities[1].id

    fun oilCounters(game: GameTestDriver, cultivator: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(cultivator)?.get<CountersComponent>()?.getCount(CounterType.OIL) ?: 0

    fun setup(): Triple<GameTestDriver, com.wingedsheep.sdk.model.EntityId, com.wingedsheep.sdk.model.EntityId> {
        val game = GameTestDriver().apply {
            registerCards(TestCards.all + RustvineCultivator)
            initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val me = game.activePlayer!!
        val cultivator = game.putCreatureOnBattlefield(me, "Rustvine Cultivator")
        game.removeSummoningSickness(cultivator)
        val land = game.putLandOnBattlefield(me, "Forest")
        return Triple(game, cultivator, land)
    }

    test("its first tap ability places an oil counter on itself") {
        val (game, cultivator) = setup()

        game.submitSuccess(ActivateAbility(game.activePlayer!!, cultivator, addOilAbility))
        game.bothPass()

        game.isTapped(cultivator) shouldBe true
        oilCounters(game, cultivator) shouldBe 1
    }

    test("its second ability spends an oil counter to untap a target land") {
        val (game, cultivator, land) = setup()
        game.replaceState(
            game.state
                .updateEntity(cultivator) {
                    it.with(CountersComponent(mapOf(CounterType.OIL to 1)))
                }
                .updateEntity(land) { it.with(TappedComponent) }
        )

        game.submitSuccess(
            ActivateAbility(
                playerId = game.activePlayer!!,
                sourceId = cultivator,
                abilityId = untapLandAbility,
                targets = listOf(ChosenTarget.Permanent(land)),
            )
        )
        game.bothPass()

        game.isTapped(cultivator) shouldBe true
        game.isTapped(land) shouldBe false
        oilCounters(game, cultivator) shouldBe 0
    }
})
