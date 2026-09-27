package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChosenTarget
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-05 real Edict opponent choice, separate from Pest's cast policy. */
class PestMonsterOpposingSacrificeReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        listOf(1, 2).forEach { pestSeat ->
            test("P04-05 Pest seat $pestSeat leaves Monster sacrifice to its own actor") {
                val monsterSeat = if (pestSeat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE000401L + pestSeat)
                    .withPlayers(if (pestSeat == 1) "Pest seat" else "Monster seat",
                        if (pestSeat == 2) "Pest seat" else "Monster seat")
                    .withActivePlayer(pestSeat).withPriorityPlayer(pestSeat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(pestSeat, "Chainer's Edict")
                    .withLandsOnBattlefield(pestSeat, "Swamp", 1)
                    .withLandsOnBattlefield(pestSeat, "Forest", 1)
                    .withCardOnBattlefield(monsterSeat, "Rooftop Percher")
                    .withCardOnBattlefield(monsterSeat, "Bramble Wurm")
                    .withCardOnBattlefield(pestSeat, "Carrier Thrall")
                    .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                    .build()
                val pest = if (pestSeat == 1) game.player1Id else game.player2Id
                val monster = if (pestSeat == 1) game.player2Id else game.player1Id
                val monsterCreatures = game.state.getBattlefield(monster).toSet()
                val edict = game.state.getHand(pest).single()
                game.execute(CastSpell(pest, edict,
                    targets = listOf(ChosenTarget.Player(monster)))).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                question.playerId shouldBe monster
                question.options.toSet() shouldBe monsterCreatures
                val input = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-05-v1", "pest-seat-$pestSeat", 0), 0xFE000401L + pestSeat)
                input.actorId shouldBe monster
                input.decision.shouldBeInstanceOf<SelectCardsDecision>().options.toSet() shouldBe monsterCreatures
                val chosen = question.options.first()
                game.execute(SubmitDecision(monster, CardsSelectedResponse(question.id, listOf(chosen)))).error shouldBe null
                (chosen in game.state.getGraveyard(monster)) shouldBe true
                game.state.getBattlefield(monster).toSet() shouldBe monsterCreatures - chosen
            }
        }
    }
}
