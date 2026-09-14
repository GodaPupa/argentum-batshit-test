package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.eld.cards.Frogify
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FrogifyScenarioTest : FunSpec({
    val flyingDragon = CardDefinition.creature(
        name = "Frogify Test Dragon",
        manaCost = ManaCost.parse("{2}{R}"),
        subtypes = setOf(Subtype("Dragon")),
        power = 3,
        toughness = 3,
        keywords = setOf(Keyword.FLYING)
    )

    test("makes the enchanted creature a blue 1/1 Frog with no abilities") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(flyingDragon, Frogify))
        driver.initMirrorMatch(deck = Deck.of("Island" to 20, "Mountain" to 20))
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val creature = driver.putCreatureOnBattlefield(player, flyingDragon.name)
        val aura = driver.putCardInHand(player, "Frogify")
        driver.giveMana(player, Color.BLUE, 1)
        driver.giveColorlessMana(player, 1)
        driver.castSpell(player, aura, listOf(creature))
        driver.bothPass()

        val projected = driver.state.projectedState
        val projector = StateProjector()
        projector.getProjectedPower(driver.state, creature) shouldBe 1
        projector.getProjectedToughness(driver.state, creature) shouldBe 1
        projected.hasLostAllAbilities(creature) shouldBe true
        projected.hasKeyword(creature, Keyword.FLYING) shouldBe false
        projected.hasSubtype(creature, "Frog") shouldBe true
        projected.hasSubtype(creature, "Dragon") shouldBe false
        projected.hasColor(creature, Color.BLUE) shouldBe true
        projected.hasColor(creature, Color.RED) shouldBe false
    }
})
