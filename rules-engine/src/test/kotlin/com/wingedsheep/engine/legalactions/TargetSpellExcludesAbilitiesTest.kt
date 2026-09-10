package com.wingedsheep.engine.legalactions

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.legalactions.support.EnumerationTestDriver
import com.wingedsheep.engine.legalactions.support.shouldContainCastOf
import com.wingedsheep.engine.legalactions.support.shouldNotContainCastOf
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Regression test for the "target spell" enumeration loop.
 *
 * A counterspell's target requirement is `TargetSpell()`, whose filter is
 * `Any` with `zone = STACK`. The enumerator's stack-target finders used to
 * return *every* stack object — including triggered/activated abilities — so a
 * counterspell was offered as a legal cast whenever ANYTHING was on the stack,
 * and an AI that re-picked the same (ultimately illegal) enumerated action
 * looped forever.
 *
 * "Target spell" must match only actual spells (a spell is a card on the stack,
 * CR 112.1), never abilities on the stack — an activated or triggered ability on
 * the stack is an ability, not a spell (CR 113.3b/c, 113.7a). The canonical marker
 * is `SpellOnStackComponent`, surfaced via `GameState.isSpellOnStack`.
 */
class TargetSpellExcludesAbilitiesTest : FunSpec({

    val pingerAbility = AbilityId("test-life-pinger")

    // Artifact with a NON-mana activated ability that uses the stack.
    val LifePinger = CardDefinition(
        name = "Life Pinger",
        manaCost = ManaCost.parse("{1}"),
        typeLine = TypeLine.artifact(),
        oracleText = "{T}: You gain 1 life.",
        script = CardScript.permanent(
            ActivatedAbility(
                id = pingerAbility,
                cost = AbilityCost.Tap,
                effect = GainLifeEffect(1)
            )
        )
    )

    fun newDriver(): EnumerationTestDriver {
        val driver = EnumerationTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCards(listOf(LifePinger))
        driver.game.initMirrorMatch(
            deck = Deck.of("Island" to 40, "Life Pinger" to 4, "Counterspell" to 4),
            skipMulligans = true
        )
        driver.game.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("an activated ability on the stack does NOT make a counterspell castable") {
        val driver = newDriver()
        val p1 = driver.player1

        // P1 controls the artifact and holds Counterspell with mana up.
        val pinger = driver.game.putPermanentOnBattlefield(p1, "Life Pinger")
        driver.game.putCardInHand(p1, "Counterspell")
        driver.game.giveMana(p1, Color.BLUE, 2)

        // Sanity: with an empty stack, Counterspell has no legal target → not castable.
        driver.enumerateFor(p1) shouldNotContainCastOf "Counterspell"

        // Activate the artifact's non-mana ability — it goes on the stack.
        driver.game.submitSuccess(ActivateAbility(p1, pinger, pingerAbility))
        driver.game.stackSize shouldBe 1

        // The only stack object is an ability, not a spell. Counterspell must NOT be
        // offered — this is the case that previously looped.
        driver.enumerateFor(p1) shouldNotContainCastOf "Counterspell"
    }

    test("a spell on the stack DOES make a counterspell castable (positive control)") {
        val driver = newDriver()
        val p1 = driver.player1
        val p2 = driver.player2

        driver.game.putCardInHand(p1, "Counterspell")
        driver.game.giveMana(p1, Color.BLUE, 2)

        // Put a real spell on the stack: P1 casts Lightning Bolt at P2.
        val bolt = driver.game.putCardInHand(p1, "Lightning Bolt")
        driver.game.giveMana(p1, Color.RED, 1)
        driver.game.castSpell(p1, bolt, listOf(p2))

        // A genuine spell is on the stack → Counterspell can target it.
        driver.enumerateFor(p1) shouldContainCastOf "Counterspell"
    }
})
