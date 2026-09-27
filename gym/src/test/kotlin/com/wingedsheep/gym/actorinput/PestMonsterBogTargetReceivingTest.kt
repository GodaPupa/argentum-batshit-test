package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.pest.PestMonsterTronActorDecision
import com.wingedsheep.gym.pest.PestMonsterTronActorDecisions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.identity.CardComponent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-04 two-seat real Bojuka Bog target receiving; no pair-pilot admission. */
class PestMonsterBogTargetReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        for (monsterSeat in listOf(1, 2)) {
            test("P04-04 Monster seat $monsterSeat Bog targets Pest public graveyard") {
                val pestSeat = if (monsterSeat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE000801L + monsterSeat)
                    .withPlayers(if (monsterSeat == 1) "Monster seat" else "Pest seat",
                        if (monsterSeat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(monsterSeat).withPriorityPlayer(monsterSeat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(monsterSeat, "Bojuka Bog")
                    .withCardInGraveyard(monsterSeat, "Forest")
                    .withCardInGraveyard(pestSeat, "Carrier Thrall")
                    .withCardInGraveyard(pestSeat, "Blood Researcher")
                    .withCardInLibrary(monsterSeat, "Urza's Mine")
                    .withCardInLibrary(pestSeat, "Swamp")
                    .build()
                val monster = if (monsterSeat == 1) game.player1Id else game.player2Id
                val pest = if (monsterSeat == 1) game.player2Id else game.player1Id
                val bog = game.state.getHand(monster).single()
                game.execute(PlayLand(monster, bog)).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                question.playerId shouldBe monster
                val epoch = ActorEpoch("pest-monster-p04-04-v1", "monster-seat-$monsterSeat", 0)
                val input = projection.build(game.state, monster, emptyList(), epoch, 0xFE000801L + monsterSeat)
                input.actorId shouldBe monster
                input.decision.shouldBeInstanceOf<ChooseTargetsDecision>()
                val proposal = PestMonsterTronActorDecisions(epoch, monster).respond(input)
                    .shouldBeInstanceOf<PestMonsterTronActorDecision.Proposed>().proposal
                proposal.inputBindingHash shouldBe input.bindingHash
                val answer = proposal.action.shouldBeInstanceOf<SubmitDecision>()
                answer.response.shouldBeInstanceOf<TargetsResponse>()
                    .selectedTargets.values.flatten() shouldBe listOf(pest)
                game.execute(answer).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.getGraveyard(pest) shouldBe emptyList()
                game.state.getExile(pest).map { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name
                }.toSet() shouldBe setOf("Carrier Thrall", "Blood Researcher")
                game.state.getGraveyard(monster).map { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name
                } shouldBe listOf("Forest")
            }
        }
    }
}
