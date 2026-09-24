package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c20.cards.BondersOrnament
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BondersOrnamentBatchAEScenarioTest : FunSpec({
    val manaAbilityId = BondersOrnament.activatedAbilities[0].id
    val drawAbilityId = BondersOrnament.activatedAbilities[1].id

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(BondersOrnament)
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("tap mana ability produces the explicitly chosen color without using the stack") {
        val d = driver()
        val you = d.activePlayer!!
        val ornament = d.putPermanentOnBattlefield(you, "Bonder's Ornament")

        d.submitSuccess(
            ActivateAbility(
                playerId = you,
                sourceId = ornament,
                abilityId = manaAbilityId,
                manaColorChoice = Color.BLUE,
            )
        )

        d.assertStackSize(0)
        val pool = d.state.getEntity(you)?.get<ManaPoolComponent>()!!
        pool.blue shouldBe 1
        pool.total shouldBe 1
    }

    test("draw ability draws only for players who control an Ornament") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        val ornament = d.putPermanentOnBattlefield(you, "Bonder's Ornament")
        val yourHand = d.getHand(you).size
        val opponentHand = d.getHand(opponent).size
        d.giveColorlessMana(you, 4)

        d.submitSuccess(ActivateAbility(playerId = you, sourceId = ornament, abilityId = drawAbilityId))
        d.bothPass()

        d.getHand(you).size shouldBe yourHand + 1
        d.getHand(opponent).size shouldBe opponentHand
    }

    test("draw ability draws for both players when both control an Ornament") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        val ornament = d.putPermanentOnBattlefield(you, "Bonder's Ornament")
        d.putPermanentOnBattlefield(opponent, "Bonder's Ornament")
        val yourHand = d.getHand(you).size
        val opponentHand = d.getHand(opponent).size
        d.giveColorlessMana(you, 4)

        d.submitSuccess(ActivateAbility(playerId = you, sourceId = ornament, abilityId = drawAbilityId))
        d.bothPass()

        d.getHand(you).size shouldBe yourHand + 1
        d.getHand(opponent).size shouldBe opponentHand + 1
    }
})
