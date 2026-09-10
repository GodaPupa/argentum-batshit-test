package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.decayed
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests for the Decayed keyword (CR 702.147, Innistrad: Midnight Hunt).
 *
 * "This creature can't block, and when it attacks, sacrifice it at end of combat."
 *
 * The keyword is wired by the `decayed()` builder helper: a display-only keyword plus a
 * `CantBlock(GroupFilter.source())` static ability and an attack-triggered
 * `CreateDelayedTriggerEffect(step = END_COMBAT, effect = SacrificeTarget(Self))` — mirroring
 * Mardu Blazebringer's "sacrifice it at end of combat" wiring.
 */
class DecayedTest : FunSpec({

    val decayedZombie = card("Decayed Zombie") {
        manaCost = "{1}{B}"
        typeLine = "Creature — Zombie"
        power = 3
        toughness = 3
        decayed()
    }

    val vanillaAttacker = card("Test Marauder") {
        manaCost = "{2}{R}"
        typeLine = "Creature — Human Warrior"
        power = 2
        toughness = 2
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(decayedZombie, vanillaAttacker))
        return driver
    }

    test("a creature with decayed can't be declared as a blocker") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        // Attacker swings with a vanilla creature; the defender holds the decayed creature.
        val marauder = driver.putCreatureOnBattlefield(attacker, "Test Marauder")
        driver.removeSummoningSickness(marauder)
        val zombie = driver.putCreatureOnBattlefield(defender, "Decayed Zombie")

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(marauder), defender)
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)

        // Declaring the decayed creature as a blocker is illegal.
        driver.submitExpectFailure(
            DeclareBlockers(defender, mapOf(zombie to listOf(marauder)))
        )
    }

    test("when a creature with decayed attacks, a sacrifice trigger is scheduled for end of combat") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val zombie = driver.putCreatureOnBattlefield(attacker, "Decayed Zombie")
        driver.removeSummoningSickness(zombie)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(zombie), defender)
        driver.bothPass() // resolve the attack trigger that creates the delayed sacrifice

        driver.state.delayedTriggers.size shouldBe 1
        driver.state.delayedTriggers.first().fireAtStep shouldBe Step.END_COMBAT
    }

    test("a decayed creature deals combat damage, then is sacrificed at end of combat") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val zombie = driver.putCreatureOnBattlefield(attacker, "Decayed Zombie")
        driver.removeSummoningSickness(zombie)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(zombie), defender)
        driver.bothPass()

        // Combat damage is dealt before the creature is sacrificed (3/3 hits for 3).
        driver.passPriorityUntil(Step.END_COMBAT)
        driver.assertLifeTotal(defender, 17)

        // At end of combat the delayed sacrifice trigger goes on the stack — resolve it.
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        // The creature has been sacrificed and is no longer on the battlefield.
        driver.findPermanent(attacker, "Decayed Zombie") shouldBe null
        driver.state.delayedTriggers.size shouldBe 0
    }

    test("a creature with decayed has no requirement to attack") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)

        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val zombie = driver.putCreatureOnBattlefield(attacker, "Decayed Zombie")
        driver.removeSummoningSickness(zombie)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        // Declaring no attackers is legal; decayed adds no attack requirement.
        driver.declareAttackers(attacker, emptyList(), defender)

        driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        // No attack happened, so no sacrifice trigger and the creature survives.
        driver.findPermanent(attacker, "Decayed Zombie") shouldNotBe null
        driver.state.delayedTriggers.size shouldBe 0
    }
})
