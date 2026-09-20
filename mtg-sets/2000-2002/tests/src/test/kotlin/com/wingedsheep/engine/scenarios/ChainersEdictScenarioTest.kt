package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tor.cards.ChainersEdict
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChainersEdictScenarioTest : FunSpec({
    test("flashback casts from the graveyard and exiles the Edict after resolution") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + ChainersEdict)
        game.initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        game.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val edict = game.putCardInGraveyard(player, "Chainer's Edict")
        game.giveMana(player, Color.BLACK, 7)

        game.submit(CastSpell(
            playerId = player,
            cardId = edict,
            targets = listOf(ChosenTarget.Player(opponent)),
            useAlternativeCost = true,
            alternativeCostType = AlternativeCostType.FLASHBACK,
        )).error shouldBe null
        while (game.stackSize > 0) game.bothPass()

        game.findPermanent(opponent, "Grizzly Bears") shouldBe null
        game.getExileCardNames(player).contains("Chainer's Edict") shouldBe true
    }
})
