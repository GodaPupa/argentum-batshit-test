package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh2.cards.BoneShards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BoneShardsScenarioTest : FunSpec({
    fun game(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + BoneShards)
        initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("sacrifice pays the first additional-cost branch") {
        val game = game()
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        val fodder = game.putCreatureOnBattlefield(player, "Ornithopter")
        val victim = game.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = game.putCardInHand(player, "Bone Shards")
        game.giveMana(player, Color.BLACK, 1)

        game.submit(CastSpell(
            playerId = player,
            cardId = spell,
            chosenModes = listOf(0),
            targets = listOf(ChosenTarget.Permanent(victim)),
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder)),
        )).error shouldBe null
        while (game.stackSize > 0) game.bothPass()

        game.findPermanent(player, "Ornithopter") shouldBe null
        game.findPermanent(opponent, "Grizzly Bears") shouldBe null
    }

    test("discard pays the second additional-cost branch") {
        val game = game()
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        val victim = game.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val discard = game.putCardInHand(player, "Ornithopter")
        val spell = game.putCardInHand(player, "Bone Shards")
        game.giveMana(player, Color.BLACK, 1)

        game.submit(CastSpell(
            playerId = player,
            cardId = spell,
            chosenModes = listOf(1),
            targets = listOf(ChosenTarget.Permanent(victim)),
            additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(discard)),
        )).error shouldBe null
        while (game.stackSize > 0) game.bothPass()

        game.getGraveyardCardNames(player).contains("Ornithopter") shouldBe true
        game.findPermanent(opponent, "Grizzly Bears") shouldBe null
    }
})
