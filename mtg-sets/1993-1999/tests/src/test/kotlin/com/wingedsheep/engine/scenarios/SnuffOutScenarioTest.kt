package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mmq.cards.SnuffOut
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SnuffOutScenarioTest : FunSpec({
    test("a Swamp enables the four-life alternative cost") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + SnuffOut)
        game.initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        game.putLandOnBattlefield(player, "Swamp")
        val victim = game.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = game.putCardInHand(player, "Snuff Out")
        val life = game.getLifeTotal(player)

        game.submit(
            CastSpell(
                playerId = player,
                cardId = spell,
                targets = listOf(ChosenTarget.Permanent(victim)),
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE,
            ),
        ).error shouldBe null
        while (game.stackSize > 0) game.bothPass()

        game.getLifeTotal(player) shouldBe life - 4
        game.findPermanent(opponent, "Grizzly Bears") shouldBe null
    }
})
