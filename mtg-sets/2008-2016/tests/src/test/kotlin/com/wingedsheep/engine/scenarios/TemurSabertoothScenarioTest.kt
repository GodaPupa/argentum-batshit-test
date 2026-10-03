package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.frf.cards.TemurSabertooth
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TemurSabertoothScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + TemurSabertooth)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun activate(d: GameTestDriver, player: EntityId, temur: EntityId) {
        d.giveMana(player, Color.GREEN, 1)
        d.giveColorlessMana(player, 1)
        d.submit(
            ActivateAbility(
                player,
                temur,
                TemurSabertooth.activatedAbilities.single().id
            )
        ).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
    }

    test("returns exactly another creature you control without targeting and grants indestructible") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        val temur = d.putCreatureOnBattlefield(me, "Temur Sabertooth")
        val mine = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Centaur Courser")

        activate(d, me, temur)

        val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        decision.minSelections shouldBe 0
        decision.maxSelections shouldBe 1
        withClue("Temur itself and the opponent's creature are excluded from the non-targeting choice") {
            decision.options.toSet() shouldBe setOf(mine)
        }

        d.submitCardSelection(me, listOf(mine)).isSuccess shouldBe true

        d.getHand(me).contains(mine) shouldBe true
        d.state.getBattlefield().contains(mine) shouldBe false
        d.state.projectedState.hasKeyword(temur, Keyword.INDESTRUCTIBLE) shouldBe true
    }

    test("declining the optional return leaves the creature in play and grants no indestructible") {
        val d = driver()
        val me = d.activePlayer!!
        val temur = d.putCreatureOnBattlefield(me, "Temur Sabertooth")
        val mine = d.putCreatureOnBattlefield(me, "Grizzly Bears")

        activate(d, me, temur)

        val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        decision.options.toSet() shouldBe setOf(mine)
        d.submitCardSelection(me, emptyList()).isSuccess shouldBe true

        d.state.getBattlefield().contains(mine) shouldBe true
        d.state.projectedState.hasKeyword(temur, Keyword.INDESTRUCTIBLE) shouldBe false
    }

    test("with no other creature you control the ability cannot bounce Temur or an opponent creature") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        val temur = d.putCreatureOnBattlefield(me, "Temur Sabertooth")
        val theirs = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        activate(d, me, temur)

        if (d.pendingDecision != null) {
            val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            decision.options shouldBe emptyList()
            d.submitCardSelection(me, emptyList()).isSuccess shouldBe true
        }

        d.state.getBattlefield().contains(temur) shouldBe true
        d.state.getBattlefield().contains(theirs) shouldBe true
        d.state.projectedState.hasKeyword(temur, Keyword.INDESTRUCTIBLE) shouldBe false
    }
})
