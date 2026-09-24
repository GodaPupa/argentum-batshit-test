package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mom.cards.AlabasterHostIntercessor
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class AlabasterHostIntercessorBatchAIScenarioTest : ScenarioTestBase() {
    init {
        test("ETB exiles an opponent creature and linked leave trigger returns it") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Alabaster Host Intercessor")
                .withCardInHand(1, "Murder")
                .withLandsOnBattlefield(1, "Plains", 6)
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Alabaster Host Intercessor").error shouldBe null
            game.resolveStack()

            val giant = game.findPermanent("Hill Giant")!!
            game.selectTargets(listOf(giant))
            game.resolveStack()

            game.isOnBattlefield("Hill Giant") shouldBe false
            game.state.getExile(game.player2Id).any { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Hill Giant"
            } shouldBe true

            val intercessor = game.findPermanent("Alabaster Host Intercessor")!!
            game.castSpell(1, "Murder", intercessor).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Alabaster Host Intercessor") shouldBe false
            game.isOnBattlefield("Hill Giant") shouldBe true
        }

        test("leaving before the ETB resolves prevents the target from being exiled") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Alabaster Host Intercessor")
                .withLandsOnBattlefield(1, "Plains", 6)
                .withCardInHand(2, "Murder")
                .withLandsOnBattlefield(2, "Swamp", 3)
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Alabaster Host Intercessor").error shouldBe null
            game.passPriority()
            game.passPriority()
            val intercessor = game.findPermanent("Alabaster Host Intercessor")!!

            game.passPriority()
            game.castSpell(2, "Murder", intercessor).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Alabaster Host Intercessor") shouldBe false
            game.isOnBattlefield("Hill Giant") shouldBe true
            game.state.getExile(game.player2Id).isEmpty() shouldBe true
        }

        test("Plainscycling costs two, discards, and searches for a Plains") {
            val driver = GameTestDriver()
            driver.registerCards(com.wingedsheep.engine.support.TestCards.all + AlabasterHostIntercessor)
            driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
            val player = driver.activePlayer!!
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

            val intercessor = driver.putCardInHand(player, "Alabaster Host Intercessor")
            val plains = driver.putCardOnTopOfLibrary(player, "Plains")
            driver.giveColorlessMana(player, 2)

            val result = driver.submit(TypecycleCard(playerId = player, cardId = intercessor))
            (result.isSuccess || result.isPaused).shouldBeTrue()
            driver.getGraveyardCardNames(player) shouldContain "Alabaster Host Intercessor"

            val decision = driver.pendingDecision
            decision.shouldBeInstanceOf<SelectCardsDecision>()
            decision.options shouldContain plains
            driver.submitDecision(player, CardsSelectedResponse(decision.id, listOf(plains)))

            driver.findCardInHand(player, "Plains") shouldBe plains
            driver.state.getLibrary(player) shouldNotContain plains
        }
    }
}
