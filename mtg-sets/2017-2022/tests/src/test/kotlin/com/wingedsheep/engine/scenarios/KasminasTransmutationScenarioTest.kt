package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.war.cards.KasminasTransmutation
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KasminasTransmutationScenarioTest : FunSpec({
    val creatureDefinition = CardDefinition.creature(
        name = "Kasmina Test Dragon",
        manaCost = ManaCost.parse("{2}{R}"),
        subtypes = setOf(Subtype("Dragon")),
        power = 3,
        toughness = 3,
        keywords = setOf(Keyword.FLYING)
    )

    test("sets base power and toughness and removes abilities without changing color or type") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(creatureDefinition, KasminasTransmutation))
        driver.initMirrorMatch(deck = Deck.of("Island" to 20, "Mountain" to 20))
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val creature = driver.putCreatureOnBattlefield(player, creatureDefinition.name)
        val aura = driver.putCardInHand(player, "Kasmina's Transmutation")
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
        projected.hasSubtype(creature, "Dragon") shouldBe true
        projected.hasColor(creature, Color.RED) shouldBe true
    }
})
