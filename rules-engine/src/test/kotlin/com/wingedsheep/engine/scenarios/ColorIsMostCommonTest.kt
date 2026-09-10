package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ConditionalStaticAbility
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.conditions.ColorIsMostCommon
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Locks in `Condition.ColorIsMostCommon(color)` — the self-gating "as long as [color] is the
 * most common color among all permanents (or tied for most common)" condition that backs the
 * Invasion djinn cycle (Goham/Halam/Ruham/Sulam/Zanam).
 *
 * Modeled exactly like a djinn: a white creature that gets -2/-2 while white is the most common
 * color. Exercised through a `ConditionalStaticAbility`, so it runs on the **projection** path
 * (Rule 613 layer application), which is the mode the djinns actually use. The board's
 * most-common color is computed from projected creature colors; basic lands are colorless and
 * don't count.
 */
class ColorIsMostCommonTest : FunSpec({

    // Ruham Djinn shape: 5/5 white, "gets -2/-2 as long as white is the most common color among
    // all permanents, or is tied for most common."
    val WhiteDjinn = CardDefinition.creature(
        name = "White Djinn",
        manaCost = ManaCost.parse("{5}{W}"),
        subtypes = setOf(Subtype("Djinn")),
        power = 5,
        toughness = 5,
        script = CardScript(
            staticAbilities = listOf(
                ConditionalStaticAbility(
                    ability = ModifyStats(powerBonus = -2, toughnessBonus = -2, filter = GroupFilter.source()),
                    condition = ColorIsMostCommon(Color.WHITE)
                )
            )
        )
    )

    val WhiteVanilla = CardDefinition.creature(
        name = "White Vanilla",
        manaCost = ManaCost.parse("{W}"),
        subtypes = setOf(Subtype("Test")),
        power = 1,
        toughness = 1,
    )

    val GreenVanilla = CardDefinition.creature(
        name = "Green Vanilla",
        manaCost = ManaCost.parse("{G}"),
        subtypes = setOf(Subtype("Test")),
        power = 1,
        toughness = 1,
    )

    val projector = StateProjector()

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(WhiteDjinn, WhiteVanilla, GreenVanilla))
        return driver
    }

    fun newGame(): GameTestDriver {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Plains" to 20, "Forest" to 20),
            skipMulligans = true
        )
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("white sole most common → djinn shrinks to 3/3") {
        val driver = newGame()
        val you = driver.activePlayer!!

        // Only white-bearing permanents: white is the unique most common color.
        val djinn = driver.putCreatureOnBattlefield(you, "White Djinn")
        driver.putCreatureOnBattlefield(you, "White Vanilla")

        val projected = projector.project(driver.state)
        projected.getPower(djinn) shouldBe 3
        projected.getToughness(djinn) shouldBe 3
    }

    test("green strictly more common than white → djinn stays 5/5") {
        val driver = newGame()
        val you = driver.activePlayer!!
        val opponent = driver.getOpponent(you)

        val djinn = driver.putCreatureOnBattlefield(you, "White Djinn") // white: 1
        driver.putCreatureOnBattlefield(you, "Green Vanilla")           // green: 1
        driver.putCreatureOnBattlefield(opponent, "Green Vanilla")      // green: 2 (counts all players)

        // green 2 > white 1 → white is not most common → no penalty.
        val projected = projector.project(driver.state)
        projected.getPower(djinn) shouldBe 5
        projected.getToughness(djinn) shouldBe 5
    }

    test("white tied with green for most common → djinn still shrinks (ties count)") {
        val driver = newGame()
        val you = driver.activePlayer!!

        val djinn = driver.putCreatureOnBattlefield(you, "White Djinn") // white: 1
        driver.putCreatureOnBattlefield(you, "Green Vanilla")           // green: 1

        // white 1 == green 1 → both tied for most common → white qualifies → penalty applies.
        val projected = projector.project(driver.state)
        projected.getPower(djinn) shouldBe 3
        projected.getToughness(djinn) shouldBe 3
    }
})
