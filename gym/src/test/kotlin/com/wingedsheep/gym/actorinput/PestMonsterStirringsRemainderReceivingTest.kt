package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-02 Ancient Stirrings selected card and physical remainder ordering only. */
class PestMonsterStirringsRemainderReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        for (monsterSeat in listOf(1, 2)) {
            test("P04-02 Monster seat $monsterSeat Stirrings selects and orders physical remainder") {
                val pestSeat = if (monsterSeat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE000901L + monsterSeat)
                    .withPlayers(if (monsterSeat == 1) "Monster seat" else "Pest seat",
                        if (monsterSeat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(monsterSeat).withPriorityPlayer(monsterSeat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(monsterSeat, "Ancient Stirrings")
                    .withLandsOnBattlefield(monsterSeat, "Forest", 1)
                    .withCardInLibrary(monsterSeat, "Expedition Map")
                    .withCardInLibrary(monsterSeat, "Urza's Tower")
                    .withCardInLibrary(monsterSeat, "Crop Rotation")
                    .withCardInLibrary(monsterSeat, "Bonder's Ornament")
                    .withCardInLibrary(monsterSeat, "Bramble Wurm")
                    .withCardInLibrary(pestSeat, "Swamp")
                    .build()
                val monster = if (monsterSeat == 1) game.player1Id else game.player2Id
                game.castSpell(monsterSeat, "Ancient Stirrings").error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                val select = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                select.playerId shouldBe monster
                select.options.size shouldBe 3
                val looked = game.state.getLibrary(monster).toSet()
                looked.size shouldBe 5
                select.options.toSet().all { it in looked } shouldBe true
                val first = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-02-stirrings-v1", "seat-$monsterSeat-select", 0),
                    0xFE000901L + monsterSeat)
                first.decision.shouldBeInstanceOf<SelectCardsDecision>().options.toSet() shouldBe select.options.toSet()
                val tower = select.options.single { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Urza's Tower"
                }
                game.execute(SubmitDecision(monster, CardsSelectedResponse(select.id, listOf(tower)))).error shouldBe null
                (tower in game.state.getHand(monster)) shouldBe true
                val reorder = game.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
                reorder.playerId shouldBe monster
                reorder.cards.toSet() shouldBe looked - tower
                val second = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-02-stirrings-v1", "seat-$monsterSeat-reorder", 1),
                    0xFE000901L + monsterSeat)
                second.decision.shouldBeInstanceOf<ReorderLibraryDecision>().cards.toSet() shouldBe reorder.cards.toSet()
                game.execute(SubmitDecision(monster, OrderedResponse(reorder.id, reorder.cards))).error shouldBe null
                game.state.pendingDecision shouldBe null
                game.state.getLibrary(monster).toSet() shouldBe reorder.cards.toSet()
            }
        }
    }
}
