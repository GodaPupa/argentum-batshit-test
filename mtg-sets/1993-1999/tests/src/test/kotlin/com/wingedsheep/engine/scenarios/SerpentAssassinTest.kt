package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tests for Serpent Assassin's ETB triggered ability.
 *
 * Serpent Assassin: {3}{B}{B}
 * Creature — Snake Assassin
 * 2/2
 * When Serpent Assassin enters the battlefield, you may destroy target nonblack creature.
 */
class SerpentAssassinTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        return driver
    }

    test("Serpent Assassin ETB trigger can destroy a nonblack creature") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of(
                "Swamp" to 20,
                "Forest" to 20
            ),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)

        // Advance to main phase
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Put a nonblack creature (green) on opponent's battlefield
        val grizzlyBears = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        driver.findPermanent(opponent, "Grizzly Bears") shouldNotBe null

        // Give active player Serpent Assassin and mana to cast it
        val serpentAssassin = driver.putCardInHand(activePlayer, "Serpent Assassin")
        driver.giveMana(activePlayer, Color.BLACK, 5)

        // Cast Serpent Assassin
        val castResult = driver.castSpell(activePlayer, serpentAssassin)
        castResult.isSuccess shouldBe true

        // Let the creature spell resolve (both players pass priority)
        driver.bothPass().error shouldBe null

        // Serpent Assassin should be on the battlefield
        driver.findPermanent(activePlayer, "Serpent Assassin") shouldNotBe null

        // Choose the mandatory target as the ETB trigger is put on the stack.
        driver.isPaused shouldBe true
        val targetDecision = driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        targetDecision.playerId shouldBe activePlayer
        targetDecision.targetRequirements.single().minTargets shouldBe 1

        // Legal targets should include Grizzly Bears (nonblack creature)
        val legalTargets = targetDecision.legalTargets[0] ?: emptyList()
        legalTargets shouldContain grizzlyBears

        // Submit the target selection (choose Grizzly Bears)
        val targetResult = driver.submitTargetSelection(activePlayer, listOf(grizzlyBears))
        targetResult.error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 1

        // Players receive priority before the optional destruction is offered on resolution.
        driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 1
        driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe activePlayer
        driver.findPermanent(opponent, "Grizzly Bears") shouldBe grizzlyBears
        driver.submitYesNo(activePlayer, true).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 0

        // Grizzly Bears should now be destroyed (in graveyard)
        driver.findPermanent(opponent, "Grizzly Bears") shouldBe null
        driver.getGraveyardCardNames(opponent) shouldContain "Grizzly Bears"
    }

    test("Serpent Assassin ETB trigger cannot target black creatures") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of(
                "Swamp" to 20,
                "Forest" to 20
            ),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)

        // Advance to main phase
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Put another Serpent Assassin (black creature) on opponent's battlefield
        val opponentSerpent = driver.putCreatureOnBattlefield(opponent, "Serpent Assassin")

        // Give active player Serpent Assassin and mana to cast it
        val serpentAssassin = driver.putCardInHand(activePlayer, "Serpent Assassin")
        driver.giveMana(activePlayer, Color.BLACK, 5)

        // Cast Serpent Assassin
        driver.castSpell(activePlayer, serpentAssassin)

        // Let the creature spell resolve
        driver.bothPass()

        // Serpent Assassin should be on the battlefield
        driver.findPermanent(activePlayer, "Serpent Assassin") shouldNotBe null

        // The ETB trigger fires, but with no valid targets (only black creature on battlefield)
        // For "may" abilities with no legal targets, the ability should fizzle without pausing
        // Actually, the ability should not go on the stack at all if there are no legal targets

        // Opponent's Serpent Assassin (black) should NOT be a legal target
        // The ability should have fizzled or not triggered due to no legal targets
        if (driver.isPaused) {
            val targetDecision = driver.pendingDecision as? ChooseTargetsDecision
            if (targetDecision != null) {
                val legalTargets = targetDecision.legalTargets[0] ?: emptyList()
                // The opponent's black Serpent Assassin should NOT be in legal targets
                legalTargets.contains(opponentSerpent) shouldBe false
            }
        }

        // The opponent's Serpent Assassin should still be on the battlefield
        driver.findPermanent(opponent, "Serpent Assassin") shouldNotBe null
    }

    test("Serpent Assassin ETB trigger allows declining optional ability") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of(
                "Swamp" to 20,
                "Forest" to 20
            ),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)

        // Advance to main phase
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Put a nonblack creature on opponent's battlefield
        val grizzlyBears = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        // Give active player Serpent Assassin and mana
        val serpentAssassin = driver.putCardInHand(activePlayer, "Serpent Assassin")
        driver.giveMana(activePlayer, Color.BLACK, 5)

        // Cast Serpent Assassin
        driver.castSpell(activePlayer, serpentAssassin).error shouldBe null
        driver.bothPass().error shouldBe null

        // Serpent Assassin should be on the battlefield
        driver.findPermanent(activePlayer, "Serpent Assassin") shouldNotBe null

        // The optional action does not make the trigger's target optional. An empty target
        // selection is rejected; the separate yes/no decision happens on resolution.
        driver.isPaused shouldBe true
        val targetDecision = driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        targetDecision.playerId shouldBe activePlayer
        targetDecision.targetRequirements.single().minTargets shouldBe 1
        targetDecision.legalTargets.getValue(0) shouldContain grizzlyBears
        val beforeEmptyTargets = driver.state
        driver.submitTargetSelection(activePlayer, emptyList()).error shouldNotBe null
        driver.state shouldBe beforeEmptyTargets
        driver.pendingDecision shouldBe targetDecision

        // Submit the target selection (choose Grizzly Bears to use the ability)
        driver.submitTargetSelection(activePlayer, listOf(grizzlyBears)).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 1

        driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 1
        driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe activePlayer
        driver.submitYesNo(activePlayer, true).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 0

        // Grizzly Bears should be destroyed
        driver.findPermanent(opponent, "Grizzly Bears") shouldBe null
        driver.getGraveyardCardNames(opponent) shouldContain "Grizzly Bears"
    }

    test("Serpent Assassin ETB trigger can be declined at the may-question") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of(
                "Swamp" to 20,
                "Forest" to 20
            ),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)

        // Advance to main phase
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Put a nonblack creature on opponent's battlefield
        val grizzlyBears = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        // Give active player Serpent Assassin and mana
        val serpentAssassin = driver.putCardInHand(activePlayer, "Serpent Assassin")
        driver.giveMana(activePlayer, Color.BLACK, 5)

        // Cast Serpent Assassin
        driver.castSpell(activePlayer, serpentAssassin).error shouldBe null
        driver.bothPass().error shouldBe null

        // Serpent Assassin should be on the battlefield
        driver.findPermanent(activePlayer, "Serpent Assassin") shouldNotBe null

        // Choose a legal target during placement even though the player will decline on resolution.
        driver.isPaused shouldBe true
        val targetDecision = driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        targetDecision.playerId shouldBe activePlayer
        targetDecision.targetRequirements.single().minTargets shouldBe 1
        targetDecision.legalTargets.getValue(0) shouldContain grizzlyBears
        driver.submitTargetSelection(activePlayer, listOf(grizzlyBears)).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 1
        driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 1
        driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe activePlayer

        val declineResult = driver.submitYesNo(activePlayer, false)
        declineResult.error shouldBe null

        // The targeted ability has resolved, with its optional action declined.
        driver.isPaused shouldBe false
        driver.pendingDecision shouldBe null
        driver.stackSize shouldBe 0

        // Grizzly Bears should still be on the battlefield (not destroyed)
        driver.findPermanent(opponent, "Grizzly Bears") shouldNotBe null
    }
})
