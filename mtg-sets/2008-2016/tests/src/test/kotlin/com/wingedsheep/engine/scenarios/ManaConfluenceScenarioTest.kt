package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Real engine regression: Mana Confluence pays one life as an activation cost. */
class ManaConfluenceScenarioTest : ScenarioTestBase() {
    init {
        test("mana activation pays exactly one life and taps the land") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence", tapped = false)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.getLifeTotal(1)
            val land = game.findPermanent("Mana Confluence")!!
            val ability = cardRegistry.getCard("Mana Confluence")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, land, ability)).error shouldBe null
            val pending = game.getPendingDecision()
            if (pending is ChooseColorDecision)
                game.submitDecision(ColorChosenResponse(pending.id, Color.GREEN)).error shouldBe null
            game.getLifeTotal(1) shouldBe before - 1
            game.state.getEntity(land)?.has<TappedComponent>() shouldBe true
        }

        test("mana auto-payment for a creature spell charges one life") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withCardInHand(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.getLifeTotal(1)
            game.castSpell(1, "Llanowar Elves").error shouldBe null
            game.getLifeTotal(1) shouldBe before - 1
        }

        test("nonspell Temur Sabertooth ability auto-payment charges Mana Confluence life") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Temur Sabertooth")
                .withCardOnBattlefield(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.getLifeTotal(1)
            val creature = game.findPermanent("Temur Sabertooth")!!
            val land = game.findPermanent("Mana Confluence")!!
            val ability = cardRegistry.getCard("Temur Sabertooth")!!.activatedAbilities.single().id
            val attempted = game.execute(ActivateAbility(game.player1Id, creature, ability))
            println("R8_TEMUR_DIAGNOSTIC lifeBefore=" + before +
                " lifeAfter=" + game.getLifeTotal(1) +
                " confluenceTapped=" + (game.state.getEntity(land)?.has<TappedComponent>() == true) +
                " pending=" + game.getPendingDecision() +
                " events=" + attempted.events)
            attempted.error shouldBe null
            game.getLifeTotal(1) shouldBe before - 1
        }

        test("nonspell Purphoros ability auto-payment charges Mana Confluence life") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mana Confluence")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardOnBattlefield(1, "Purphoros, God of the Forge")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.getLifeTotal(1)
            val permanent = game.findPermanent("Purphoros, God of the Forge")!!
            val ability = cardRegistry.getCard("Purphoros, God of the Forge")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, permanent, ability)).error shouldBe null
            game.getLifeTotal(1) shouldBe before - 1
        }

    }
}
