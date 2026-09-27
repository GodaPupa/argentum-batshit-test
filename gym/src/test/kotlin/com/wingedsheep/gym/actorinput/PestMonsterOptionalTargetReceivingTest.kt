package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P04-04 optional physical target receiving; no pair-pilot authority. */
class PestMonsterOptionalTargetReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)

    init {
        for (seat in listOf(1, 2)) {
            for (takeTargets in listOf(false, true)) {
                test("P04-04 Monster seat $seat Rooftop Percher ${if (takeTargets) "two targets" else "zero targets"}") {
                    val otherSeat = if (seat == 1) 2 else 1
                    val game = scenario().withRngSeed(0xFE000C01L + seat + if (takeTargets) 10 else 0)
                        .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                            if (seat == 2) "Monster seat" else "Pest seat")
                        .withActivePlayer(seat).withPriorityPlayer(seat)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                        .withCardInHand(seat, "Rooftop Percher")
                        .withLandsOnBattlefield(seat, "Forest", 5)
                        .withCardInGraveyard(otherSeat, "Carrier Thrall")
                        .withCardInGraveyard(otherSeat, "Blood Researcher")
                        .withCardInLibrary(seat, "Urza's Mine")
                        .withCardInLibrary(otherSeat, "Swamp")
                        .build()
                    val monster = if (seat == 1) game.player1Id else game.player2Id
                    val pest = if (seat == 1) game.player2Id else game.player1Id
                    val publicGraveyard = game.state.getGraveyard(pest).toList()
                    game.castSpell(seat, "Rooftop Percher").error shouldBe null
                    game.resolveStack().forEach { it.error shouldBe null }
                    val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                    question.playerId shouldBe monster
                    val requirement = question.targetRequirements.single()
                    requirement.minTargets shouldBe 0
                    requirement.maxTargets shouldBe 2
                    question.legalTargets[requirement.index]!!.toSet() shouldBe publicGraveyard.toSet()
                    val input = projection.build(game.state, monster, emptyList(),
                        ActorEpoch("pest-monster-p04-04-optional-v1", "percher-$seat-$takeTargets", 0),
                        0xFE000C01L + seat + if (takeTargets) 10 else 0)
                    val projected = input.decision.shouldBeInstanceOf<ChooseTargetsDecision>()
                    projected.legalTargets[requirement.index]!!.toSet() shouldBe publicGraveyard.toSet()
                    val selected = if (takeTargets) publicGraveyard else emptyList()
                    game.execute(SubmitDecision(monster,
                        TargetsResponse(question.id, mapOf(requirement.index to selected)))).error shouldBe null
                    game.resolveStack().forEach { it.error shouldBe null }
                    game.state.getGraveyard(pest).toSet() shouldBe (publicGraveyard - selected.toSet()).toSet()
                    game.state.getExile(pest).toSet().containsAll(selected) shouldBe true
                }

                test("P04-04 Monster seat $seat Pinnacle Kill-Ship ${if (takeTargets) "one target" else "zero targets"}") {
                    val otherSeat = if (seat == 1) 2 else 1
                    val game = scenario().withRngSeed(0xFE000D01L + seat + if (takeTargets) 10 else 0)
                        .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                            if (seat == 2) "Monster seat" else "Pest seat")
                        .withActivePlayer(seat).withPriorityPlayer(seat)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                        .withCardInHand(seat, "Pinnacle Kill-Ship")
                        .withLandsOnBattlefield(seat, "Forest", 7)
                        .withCardOnBattlefield(otherSeat, "Carrier Thrall")
                        .withCardInLibrary(seat, "Urza's Mine")
                        .withCardInLibrary(otherSeat, "Swamp")
                        .build()
                    val monster = if (seat == 1) game.player1Id else game.player2Id
                    val pest = if (seat == 1) game.player2Id else game.player1Id
                    val creature = game.state.getBattlefield(pest).single()
                    game.castSpell(seat, "Pinnacle Kill-Ship").error shouldBe null
                    game.resolveStack().forEach { it.error shouldBe null }
                    val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
                    question.playerId shouldBe monster
                    val requirement = question.targetRequirements.single()
                    requirement.minTargets shouldBe 0
                    requirement.maxTargets shouldBe 1
                    question.legalTargets[requirement.index] shouldBe listOf(creature)
                    val input = projection.build(game.state, monster, emptyList(),
                        ActorEpoch("pest-monster-p04-04-optional-v1", "ship-$seat-$takeTargets", 0),
                        0xFE000D01L + seat + if (takeTargets) 10 else 0)
                    input.decision.shouldBeInstanceOf<ChooseTargetsDecision>()
                        .legalTargets[requirement.index] shouldBe listOf(creature)
                    val selected = if (takeTargets) listOf(creature) else emptyList()
                    game.execute(SubmitDecision(monster,
                        TargetsResponse(question.id, mapOf(requirement.index to selected)))).error shouldBe null
                    game.resolveStack().forEach { it.error shouldBe null }
                }
            }
        }
    }
}
