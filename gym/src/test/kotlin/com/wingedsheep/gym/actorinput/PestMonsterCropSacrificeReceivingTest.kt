package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P03-03/P04-01 Crop public cost and current search; no policy parity. */
class PestMonsterCropSacrificeReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        for (seat in listOf(1, 2)) {
            test("P03-03 Monster seat $seat Crop sacrifices physical Forest and selects Tower") {
                val otherSeat = if (seat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE001001L + seat)
                    .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                        if (seat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(seat, "Crop Rotation")
                    .withLandsOnBattlefield(seat, "Forest", 2)
                    .withLandsOnBattlefield(seat, "Urza's Mine", 1)
                    .withLandsOnBattlefield(seat, "Urza's Power Plant", 1)
                    .withCardInLibrary(seat, "Urza's Tower")
                    .withCardInLibrary(seat, "Forest")
                    .withCardInLibrary(otherSeat, "Swamp")
                    .build()
                val monster = if (seat == 1) game.player1Id else game.player2Id
                val crop = game.state.getHand(monster).single()
                val sacrificed = game.state.getBattlefield(monster).first { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Forest"
                }
                val tower = game.state.getLibrary(monster).single { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Urza's Tower"
                }
                val forest = game.state.getLibrary(monster).single { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Forest"
                }
                game.execute(CastSpell(monster, crop,
                    additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrificed))))
                    .error shouldBe null
                (sacrificed in game.state.getGraveyard(monster)) shouldBe true
                game.resolveStack().forEach { it.error shouldBe null }
                val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                question.playerId shouldBe monster
                question.options.toSet() shouldBe setOf(tower, forest)
                (question.authorizedLibrarySearch != null) shouldBe true
                val input = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p03-03-v1", "monster-seat-$seat-search", 0),
                    0xFE001001L + seat)
                input.decision.shouldBeInstanceOf<SelectCardsDecision>().options.toSet() shouldBe
                    setOf(tower, forest)
                game.execute(SubmitDecision(monster,
                    CardsSelectedResponse(question.id, listOf(tower)))).error shouldBe null
                (tower in game.state.getBattlefield(monster)) shouldBe true
                (tower in game.state.getLibrary(monster)) shouldBe false
            }
        }
    }
}
