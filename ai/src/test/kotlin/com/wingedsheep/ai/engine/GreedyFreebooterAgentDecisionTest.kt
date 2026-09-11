package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Deterministic policy probes for the generic decisions Greedy Freebooter exercises. */
class GreedyFreebooterAgentDecisionTest : ScenarioTestBase() {

    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun seeded() = scenario().withPlayers().withRngSeed(0xF8EE_B007L)

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun beginFreebooterScry(game: TestGame) {
        val victim = game.findPermanent("Greedy Freebooter")!!
        game.castSpell(2, "Lightning Bolt", victim).isSuccess.shouldBeTrue()
        game.resolveStack()
        game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
    }

    init {
        test("agent keeps a desirable card on top after scry 1") {
            val game = seeded()
                .withCardOnBattlefield(1, "Greedy Freebooter")
                .withCardInLibrary(1, "Lightning Bolt")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2)
                .build()
            beginFreebooterScry(game)

            val response = ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            response.selectedCards shouldBe emptyList()
        }

        test("agent bottoms an unwanted land after scry 1 while flooded") {
            val game = seeded()
                .withCardOnBattlefield(1, "Greedy Freebooter")
                .withCardInLibrary(1, "Mountain")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2)
                .build()
            beginFreebooterScry(game)

            val decision = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            response.selectedCards shouldBe decision.options
        }

        test("Freebooter death value makes it the preferred Village Rites sacrifice") {
            val game = seeded()
                .withActivePlayer(2)
                .withCardOnBattlefield(1, "Voldaren Epicure")
                .withCardOnBattlefield(1, "Greedy Freebooter")
                .withCardInHand(1, "Village Rites")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val victim = game.findPermanent("Greedy Freebooter")!!
            game.castSpell(2, "Lightning Bolt", victim).isSuccess.shouldBeTrue()
            game.execute(PassPriority(game.player2Id)).isSuccess.shouldBeTrue()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Village Rites"
            val sacrificed = action.additionalCostPayment!!.sacrificedPermanents.single()
            sacrificed shouldBe victim
        }

        test("Freebooter death value can make it the preferred combat blocker") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardOnBattlefield(1, "Voldaren Epicure", summoningSickness = false)
                .withCardOnBattlefield(1, "Greedy Freebooter", summoningSickness = false)
                .withCardOnBattlefield(2, "Craw Wurm", summoningSickness = false)
                .withCardInLibrary(1, "Mountain")
                .withActivePlayer(2)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            game.declareAttackers(mapOf("Craw Wurm" to 1)).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<DeclareBlockers>()
            val blocker = action.blockers.keys.single()
            cardName(game, blocker) shouldBe "Greedy Freebooter"
        }

        test("Munitions prefers Freebooter when its Treasure and Bats trigger complete lethal") {
            val game = seeded()
                .withCardOnBattlefield(1, "Makeshift Munitions")
                .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                .withCardOnBattlefield(1, "Voldaren Epicure")
                .withCardOnBattlefield(1, "Greedy Freebooter")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInLibrary(1, "Swamp")
                .withLifeTotal(2, 2)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            cardName(game, action.sourceId) shouldBe "Makeshift Munitions"
            val sacrificed = action.costPayment!!.sacrificedPermanents.single()
            cardName(game, sacrificed) shouldBe "Greedy Freebooter"
        }

        test("agent preserves a higher-value creature when Freebooter has no superior use") {
            val game = seeded()
                .withCardOnBattlefield(1, "Makeshift Munitions")
                .withCardOnBattlefield(1, "Mirkwood Bats", summoningSickness = false)
                .withCardOnBattlefield(1, "Greedy Freebooter")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withLifeTotal(2, 1)
                .build()
            val bats = game.findPermanent("Mirkwood Bats")!!

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            val sacrificed = action.costPayment!!.sacrificedPermanents.single()
            cardName(game, sacrificed) shouldBe "Greedy Freebooter"
            sacrificed shouldBe game.findPermanent("Greedy Freebooter")
            game.findPermanent("Mirkwood Bats") shouldBe bats
        }
    }
}
