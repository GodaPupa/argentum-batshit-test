package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ons.cards.RiptideEntrancer
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tests for Riptide Entrancer.
 *
 * Riptide Entrancer: {1}{U}{U}
 * Creature — Human Wizard
 * 1/1
 * Whenever Riptide Entrancer deals combat damage to a player, you may sacrifice it.
 * If you do, gain control of target creature that player controls.
 * (This effect lasts indefinitely.)
 * Morph {U}{U}
 *
 * The trigger selects its target before entering the stack. After the response window,
 * its controller chooses whether to sacrifice the Entrancer during resolution.
 */
class RiptideEntrancerTest : FunSpec({

    val projector = StateProjector()

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        return driver
    }

    /**
     * Drive combat through first strike to combat damage step.
     * After this, a ChooseTargetsDecision is pending for trigger placement.
     */
    fun driveToCombatDamage(driver: GameTestDriver, attacker: com.wingedsheep.sdk.model.EntityId, defender: com.wingedsheep.sdk.model.EntityId, entrancer: com.wingedsheep.sdk.model.EntityId) {
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(entrancer), defender)
        driver.bothPass()
        driver.declareNoBlockers(defender)
        driver.bothPass()

        driver.currentStep shouldBe Step.COMBAT_DAMAGE
        driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
    }

    test("gain control of opponent creature when choosing to sacrifice after combat damage") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Grizzly Bears" to 40),
            startingLife = 20
        )

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val entrancer = driver.putCreatureOnBattlefield(attacker, "Riptide Entrancer")
        driver.removeSummoningSickness(entrancer)

        val targetCreature = driver.putCreatureOnBattlefield(defender, "Grizzly Bears")

        driveToCombatDamage(driver, attacker, defender, entrancer)

        // Choose the target creature before placing the trigger.
        val chooseTargets = driver.pendingDecision as ChooseTargetsDecision
        driver.submitTargetSelection(chooseTargets.playerId, listOf(targetCreature))

        // Both players may respond before the sacrifice choice on resolution.
        driver.stackSize shouldBe 1
        driver.bothPass()
        val yesNoDecision = driver.pendingDecision as YesNoDecision
        driver.submitYesNo(yesNoDecision.playerId, true).error shouldBe null

        // Entrancer should be in graveyard (sacrificed)
        driver.assertInGraveyard(attacker, "Riptide Entrancer")

        // Target creature should now be controlled by attacker (check projected state for floating effect)
        val projected = projector.project(driver.state)
        projected.getController(targetCreature) shouldBe attacker

        // Defender took 1 combat damage
        driver.assertLifeTotal(defender, 19)
    }

    test("choosing not to sacrifice keeps Entrancer and opponent keeps creature") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Grizzly Bears" to 40),
            startingLife = 20
        )

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val entrancer = driver.putCreatureOnBattlefield(attacker, "Riptide Entrancer")
        driver.removeSummoningSickness(entrancer)

        val targetCreature = driver.putCreatureOnBattlefield(defender, "Grizzly Bears")

        driveToCombatDamage(driver, attacker, defender, entrancer)

        // Choose a target, then decline the sacrifice after the response window.
        driver.submitTargetSelection(attacker, listOf(targetCreature)).error shouldBe null
        driver.stackSize shouldBe 1
        driver.bothPass()
        val yesNoDecision = driver.pendingDecision as YesNoDecision
        driver.submitYesNo(yesNoDecision.playerId, false)

        // Entrancer should still be on battlefield
        driver.getController(entrancer) shouldBe attacker

        // Target creature should still be controlled by defender
        driver.getController(targetCreature) shouldBe defender

        // Defender took 1 combat damage
        driver.assertLifeTotal(defender, 19)
    }

    test("trigger does not fire when blocked") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Grizzly Bears" to 40),
            startingLife = 20
        )

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val entrancer = driver.putCreatureOnBattlefield(attacker, "Riptide Entrancer")
        driver.removeSummoningSickness(entrancer)

        val blocker = driver.putCreatureOnBattlefield(defender, "Grizzly Bears")
        driver.removeSummoningSickness(blocker)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)

        driver.declareAttackers(attacker, listOf(entrancer), defender)
        driver.bothPass()

        driver.declareBlockers(defender, mapOf(blocker to listOf(entrancer)))
        driver.bothPass()

        // Skip first strike damage
        driver.bothPass()

        // Combat damage - Entrancer is blocked, no damage to player, no trigger
        // Both pass through end of combat
        driver.bothPass()

        // Entrancer (1/1) dies to Grizzly Bears (2/2)
        driver.assertInGraveyard(attacker, "Riptide Entrancer")
        driver.assertLifeTotal(defender, 20)
    }
})
