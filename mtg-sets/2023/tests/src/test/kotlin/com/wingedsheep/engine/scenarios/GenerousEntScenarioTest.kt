package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ltr.cards.GenerousEnt
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class GenerousEntScenarioTest : FunSpec({
    test("Forestcycling discards the Ent and finds a Forest card") {
        val game = GameTestDriver()
        game.registerCards(TestCards.all + GenerousEnt)
        game.initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = game.activePlayer!!
        val ent = game.putCardInHand(player, "Generous Ent")
        val forest = game.putCardOnTopOfLibrary(player, "Forest")
        game.giveColorlessMana(player, 1)

        val result = game.submit(TypecycleCard(playerId = player, cardId = ent))
        (result.isSuccess || result.isPaused).shouldBeTrue()
        game.getGraveyardCardNames(player) shouldContain "Generous Ent"
        val decision = game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        decision.options shouldContain forest
        game.submitDecision(player, CardsSelectedResponse(decision.id, listOf(forest)))

        game.findCardInHand(player, "Forest") shouldBe forest
    }
})
