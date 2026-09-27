package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Prospective P03-07 two-seat declaration and post-block priority receiving. */
class PestMonsterCombatPriorityReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))

    init {
        listOf(1, 2).forEach { attackingSeat ->
            test("P03-07 attacking seat $attackingSeat sees blocker baton then post-block priority") {
                val defendingSeat = if (attackingSeat == 1) 2 else 1
                val attackerName = if (attackingSeat == 1) "Carrier Thrall" else "Bramble Wurm"
                val blockerName = if (attackingSeat == 1) "Bramble Wurm" else "Carrier Thrall"
                val game = scenario().withRngSeed(0xFE000601L + attackingSeat)
                    .withPlayers("Pest seat", "Monster seat")
                    .withActivePlayer(attackingSeat).withPriorityPlayer(attackingSeat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardOnBattlefield(attackingSeat, attackerName, summoningSickness = false)
                    .withCardOnBattlefield(defendingSeat, blockerName, summoningSickness = false)
                    .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                    .build()
                val active = if (attackingSeat == 1) game.player1Id else game.player2Id
                val defender = if (attackingSeat == 1) game.player2Id else game.player1Id
                val attacker = game.findPermanent(attackerName)!!
                val blocker = game.findPermanent(blockerName)!!
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.execute(DeclareAttackers(active, mapOf(attacker to defender))).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.state.priorityPlayerId shouldBe defender
                game.state.hasPriority(defender) shouldBe false
                val menu = completeActorLegalActions(game.state, defender, enumerator)
                val input = projection.build(game.state, defender, menu,
                    ActorEpoch("pest-monster-p03-07-v1", "attacking-seat-$attackingSeat", 0), 0xFE000601L + attackingSeat)
                input.actorId shouldBe defender
                input.legalActions.map { it.action::class } shouldBe listOf(DeclareBlockers::class)
                game.execute(DeclareBlockers(defender, mapOf(blocker to listOf(attacker)))).error shouldBe null
                game.state.hasPriority(active) shouldBe true
                val after = completeActorLegalActions(game.state, active, enumerator)
                after.any { it.action is PassPriority } shouldBe true
                game.execute(PassPriority(active)).error shouldBe null
                game.state.hasPriority(defender) shouldBe true
            }
        }
    }
}
