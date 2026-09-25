package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Fixed real-action regression fixtures only; no sampled matchup or official seed. */
class HydroblastScenarioTest : ScenarioTestBase() {
    private fun fixture(): ScenarioBuilder = scenario().withPlayers()
        .withRngSeed(9250925022L)
        .withCardInHand(1, "Hydroblast")
        .withLandsOnBattlefield(1, "Island", 1)

    private fun TestGame.castMode(mode: Int, target: ChosenTarget) = execute(CastSpell(
        player1Id, findCardsInHand(1, "Hydroblast").single(), listOf(target),
        chosenModes = listOf(mode), modeTargetsOrdered = listOf(listOf(target))
    ))

    private fun TestGame.settle() {
        resolveStack().forEach { it.error shouldBe null }
        state.stack.size shouldBe 0
        hasPendingDecision() shouldBe false
    }

    init {
        for ((victim, shouldDestroy) in listOf("Hill Giant" to true, "Grizzly Bears" to false)) {
            test("permanent mode legally targets $victim and checks its red color on resolution") {
                val game = fixture().withCardOnBattlefield(2, victim).build()
                game.castMode(1, ChosenTarget.Permanent(game.findPermanent(victim)!!)).error shouldBe null
                game.state.stack.size shouldBe 1
                game.settle()
                game.isInGraveyard(2, victim) shouldBe shouldDestroy
                game.isOnBattlefield(victim) shouldBe !shouldDestroy
                game.isInGraveyard(1, "Hydroblast") shouldBe true
            }
        }

        for ((victim, lands, shouldCounter) in listOf(
            Triple("Hill Giant", "Mountain", true),
            Triple("Grizzly Bears", "Forest", false)
        )) {
            test("spell mode legally targets $victim and only counters a red spell") {
                val game = fixture().withCardInHand(2, victim).withLandsOnBattlefield(2, lands, 5)
                    .withActivePlayer(2).build()
                game.castSpell(2, victim).error shouldBe null
                val spell = game.state.stack.single()
                game.passPriority().error shouldBe null
                game.castMode(0, ChosenTarget.Spell(spell)).error shouldBe null
                game.settle()
                game.isInGraveyard(2, victim) shouldBe shouldCounter
                game.isOnBattlefield(victim) shouldBe !shouldCounter
                game.isInGraveyard(1, "Hydroblast") shouldBe true
            }
        }

        test("a real color changing ability in response controls the destruction result") {
            val game = fixture().withCardOnBattlefield(2, "Hill Giant")
                .withCardOnBattlefield(2, "Fylamarid").withLandsOnBattlefield(2, "Island", 1).build()
            val victim = game.findPermanent("Hill Giant")!!
            game.castMode(1, ChosenTarget.Permanent(victim)).error shouldBe null
            game.passPriority().error shouldBe null
            val ability = cardRegistry.getCard("Fylamarid")!!.activatedAbilities.single()
            game.execute(ActivateAbility(game.player2Id, game.findPermanent("Fylamarid")!!,
                ability.id, listOf(ChosenTarget.Permanent(victim)))).error shouldBe null
            game.settle()
            game.isInGraveyard(2, "Hill Giant") shouldBe false
            game.isOnBattlefield("Hill Giant") shouldBe true
        }

        test("a target returned to hand in response is never destroyed in its new zone") {
            val game = fixture().withCardOnBattlefield(2, "Hill Giant")
                .withCardInHand(2, "Unsummon").withLandsOnBattlefield(2, "Island", 1).build()
            val victim = game.findPermanent("Hill Giant")!!
            game.castMode(1, ChosenTarget.Permanent(victim)).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Unsummon", victim).error shouldBe null
            game.settle()
            game.isInHand(2, "Hill Giant") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe false
            game.isInGraveyard(1, "Hydroblast") shouldBe true
        }

        test("the permanent mode may destroy the caster's own matching permanent") {
            val game = fixture().withCardOnBattlefield(1, "Hill Giant").build()
            game.castMode(1, ChosenTarget.Permanent(game.findPermanent("Hill Giant")!!)).error shouldBe null
            game.settle()
            game.isInGraveyard(1, "Hill Giant") shouldBe true
        }

        test("choosing a mode cannot substitute the other mode's target category") {
            val game = fixture().withCardInHand(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Forest", 2).withCardOnBattlefield(2, "Hill Giant")
                .withActivePlayer(2).build()
            game.castSpell(2, "Grizzly Bears").error shouldBe null
            val spell = game.state.stack.single()
            game.passPriority().error shouldBe null
            val before = game.state
            game.castMode(0, ChosenTarget.Permanent(game.findPermanent("Hill Giant")!!)).error shouldNotBe null
            game.state shouldBe before
            game.castMode(1, ChosenTarget.Spell(spell)).error shouldNotBe null
            game.state shouldBe before
            game.isInHand(1, "Hydroblast") shouldBe true
        }
    }
}
