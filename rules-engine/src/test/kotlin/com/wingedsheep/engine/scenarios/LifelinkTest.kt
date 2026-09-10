package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for Lifelink keyword (Rule 702.15).
 * Damage dealt by a source with lifelink causes its controller to gain that much life.
 */
class LifelinkTest : FunSpec({

    val LifelinkBear = CardDefinition.creature(
        name = "Lifelink Bear",
        manaCost = ManaCost.parse("{1}{W}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2,
        oracleText = "Lifelink",
        keywords = setOf(Keyword.LIFELINK)
    )

    val VanillaBear = CardDefinition.creature(
        name = "Vanilla Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2,
        oracleText = ""
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(LifelinkBear, VanillaBear))
        return driver
    }

    test("lifelink creature gains life for controller when dealing combat damage to player") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Plains" to 20, "Forest" to 20),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val lifelinkCreature = driver.putCreatureOnBattlefield(activePlayer, "Lifelink Bear")
        driver.removeSummoningSickness(lifelinkCreature)

        // Advance to combat
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(activePlayer, listOf(lifelinkCreature), opponent)
        driver.bothPass()

        // No blockers
        driver.bothPass()

        // First strike step (no first strikers)
        driver.bothPass()

        // Combat damage step
        driver.bothPass()

        // Opponent should take 2 damage, active player should gain 2 life
        driver.assertLifeTotal(opponent, 18)
        driver.assertLifeTotal(activePlayer, 22)
    }

    test("lifelink creature gains life when dealing combat damage to blocking creature") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Plains" to 20, "Forest" to 20),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val lifelinkCreature = driver.putCreatureOnBattlefield(activePlayer, "Lifelink Bear")
        driver.removeSummoningSickness(lifelinkCreature)
        val blocker = driver.putCreatureOnBattlefield(opponent, "Vanilla Bear")
        driver.removeSummoningSickness(blocker)

        // Advance to combat
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(activePlayer, listOf(lifelinkCreature), opponent)
        driver.bothPass()

        // Block
        driver.declareBlockers(opponent, mapOf(blocker to listOf(lifelinkCreature)))
        driver.bothPass()

        // First strike step
        driver.bothPass()

        // Combat damage step
        driver.bothPass()

        // Both creatures trade, but active player gains 2 life from lifelink
        driver.assertLifeTotal(activePlayer, 22)
        // Opponent gains no life (no lifelink on their creature)
        driver.assertLifeTotal(opponent, 20)
    }

    test("creature without lifelink does not gain life") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Plains" to 20, "Forest" to 20),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val creature = driver.putCreatureOnBattlefield(activePlayer, "Vanilla Bear")
        driver.removeSummoningSickness(creature)

        // Advance to combat
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(activePlayer, listOf(creature), opponent)
        driver.bothPass()

        // No blockers
        driver.bothPass()

        // First strike step
        driver.bothPass()

        // Combat damage
        driver.bothPass()

        // Opponent takes 2, active player stays at 20
        driver.assertLifeTotal(opponent, 18)
        driver.assertLifeTotal(activePlayer, 20)
    }
})
