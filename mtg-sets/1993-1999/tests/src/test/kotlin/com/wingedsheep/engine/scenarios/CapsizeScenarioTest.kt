package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tmp.cards.Capsize
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class CapsizeScenarioTest : FunSpec({
    fun newGame(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + Capsize)
        initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(game: GameTestDriver) {
        var guard = 0
        while (guard++ < 30 && game.state.stack.isNotEmpty() && !game.isPaused) game.bothPass()
    }

    test("without buyback it bounces the permanent and goes to the graveyard") {
        val game = newGame()
        val you = game.activePlayer!!
        val target = game.putCreatureOnBattlefield(game.getOpponent(you), "Centaur Courser")
        val capsize = game.putCardInHand(you, "Capsize")
        game.giveMana(you, Color.BLUE, 3)

        game.submit(
            CastSpell(
                playerId = you,
                cardId = capsize,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(game)

        game.state.getBattlefield() shouldNotContain target
        game.state.getZone(ZoneKey(you, Zone.GRAVEYARD)) shouldContain capsize
    }

    test("paid buyback returns the resolving spell to its owner's hand") {
        val game = newGame()
        val you = game.activePlayer!!
        val target = game.putCreatureOnBattlefield(game.getOpponent(you), "Centaur Courser")
        val capsize = game.putCardInHand(you, "Capsize")
        game.giveMana(you, Color.BLUE, 6)

        game.submit(
            CastSpell(
                playerId = you,
                cardId = capsize,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(game)

        game.state.getBattlefield() shouldNotContain target
        game.state.getZone(ZoneKey(you, Zone.HAND)) shouldContain capsize
        game.state.getZone(ZoneKey(you, Zone.GRAVEYARD)) shouldNotContain capsize
    }

    test("buyback cannot be declared without enough mana for the additional cost") {
        val game = newGame()
        val you = game.activePlayer!!
        val target = game.putCreatureOnBattlefield(game.getOpponent(you), "Centaur Courser")
        val capsize = game.putCardInHand(you, "Capsize")
        game.giveMana(you, Color.BLUE, 3)

        game.submit(
            CastSpell(
                playerId = you,
                cardId = capsize,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe false
    }

    test("paid buyback does not return a spell whose only target became illegal") {
        val game = newGame()
        val you = game.activePlayer!!
        val target = game.putCreatureOnBattlefield(game.getOpponent(you), "Centaur Courser")
        val capsize = game.putCardInHand(you, "Capsize")
        game.giveMana(you, Color.BLUE, 6)

        game.submit(
            CastSpell(
                playerId = you,
                cardId = capsize,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        game.moveToGraveyard(target)
        resolveStack(game)

        game.state.getZone(ZoneKey(you, Zone.GRAVEYARD)) shouldContain capsize
        game.state.getZone(ZoneKey(you, Zone.HAND)) shouldNotContain capsize
    }

    test("paid buyback does not return a countered spell") {
        val game = newGame()
        val you = game.activePlayer!!
        val opponent = game.getOpponent(you)
        val target = game.putCreatureOnBattlefield(opponent, "Centaur Courser")
        val capsize = game.putCardInHand(you, "Capsize")
        val counterspell = game.putCardInHand(opponent, "Counterspell")
        game.giveMana(you, Color.BLUE, 6)
        game.giveMana(opponent, Color.BLUE, 2)

        game.submit(
            CastSpell(
                playerId = you,
                cardId = capsize,
                targets = listOf(ChosenTarget.Permanent(target)),
                declaredCostSlot = ChoiceSlot.BUYBACK,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        game.passPriority(you)
        game.submit(
            CastSpell(
                playerId = opponent,
                cardId = counterspell,
                targets = listOf(ChosenTarget.Spell(capsize)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(game)

        game.state.getZone(ZoneKey(you, Zone.GRAVEYARD)) shouldContain capsize
        game.state.getZone(ZoneKey(you, Zone.HAND)) shouldNotContain capsize
        game.state.getBattlefield() shouldContain target
    }
})
