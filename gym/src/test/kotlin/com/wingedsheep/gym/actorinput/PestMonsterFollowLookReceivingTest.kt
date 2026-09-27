package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-02 Pest Follow the Lumarets look-choice receiving only. */
class PestMonsterFollowLookReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        for (pestSeat in listOf(1, 2)) for (gainedLife in listOf(false, true)) {
            test("P04-02 Pest seat $pestSeat Follow look with life gain $gainedLife") {
                val other = if (pestSeat == 1) 2 else 1
                val builder = scenario().withRngSeed(0xFE000701L + pestSeat * 2 + if (gainedLife) 1 else 0)
                    .withPlayers(if (pestSeat == 1) "Pest seat" else "Monster seat",
                        if (pestSeat == 2) "Pest seat" else "Monster seat")
                    .withActivePlayer(pestSeat).withPriorityPlayer(pestSeat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(pestSeat, "Follow the Lumarets")
                    .withLandsOnBattlefield(pestSeat, "Forest", 2)
                    .withCardInLibrary(pestSeat, "Carrier Thrall")
                    .withCardInLibrary(pestSeat, "Forest")
                    .withCardInLibrary(pestSeat, "Blood Researcher")
                    .withCardInLibrary(pestSeat, "Weather the Storm")
                    .withCardInLibrary(pestSeat, "Swamp")
                    .withCardInLibrary(other, "Forest")
                if (gainedLife) builder.withCardInHand(pestSeat, "Jungle Hollow")
                val game = builder.build()
                val pest = if (pestSeat == 1) game.player1Id else game.player2Id
                if (gainedLife) {
                    val hollow = game.state.getHand(pest).single { id ->
                        game.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name == "Jungle Hollow"
                    }
                    game.execute(PlayLand(pest, hollow)).error shouldBe null
                    game.resolveStack().forEach { it.error shouldBe null }
                }
                game.castSpell(pestSeat, "Follow the Lumarets").error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                question.playerId shouldBe pest
                question.minSelections shouldBe 0
                question.maxSelections shouldBe if (gainedLife) 2 else 1
                val input = projection.build(game.state, pest, emptyList(),
                    ActorEpoch("pest-monster-p04-02-v1", "seat-$pestSeat-life-$gainedLife", 0),
                    0xFE000701L + pestSeat * 2 + if (gainedLife) 1 else 0)
                val visible = input.decision.shouldBeInstanceOf<SelectCardsDecision>()
                visible.options.toSet() shouldBe question.options.toSet()
                visible.maxSelections shouldBe question.maxSelections
                val chosen = visible.options.take(if (gainedLife) 2 else 1)
                chosen.size shouldBe if (gainedLife) 2 else 1
                game.execute(SubmitDecision(pest, CardsSelectedResponse(question.id, chosen))).error shouldBe null
                chosen.all { it in game.state.getHand(pest) } shouldBe true
            }
        }
    }
}
