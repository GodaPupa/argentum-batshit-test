package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Visible-hand colour development, using the authoritative engine for every continuation. */
class EarlyManaDevelopmentTest : ScenarioTestBase() {
    private fun seeded() = scenario().withPlayers().withRngSeed(2026101901L)
    private fun selectedLand(game: TestGame): String? {
        val action = AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
            .chooseAction(game.state).shouldBeInstanceOf<PlayLand>()
        return game.state.getEntity(action.cardId)?.get<CardComponent>()?.name
    }
    init {
        test("third land unlocks known black plays instead of leaving three Forests idle") {
            val game = seeded().withTurnNumber(6).withLifeTotal(1,12)
                .withLandsOnBattlefield(1,"Forest",2).withCardOnBattlefield(1,"Essence Warden")
                .withCardOnBattlefield(2,"Sneaky Snacker")
                .withCardInHand(1,"Forest").withCardInHand(1,"Swamp")
                .withCardInHand(1,"Pest Mascot").withCardInHand(1,"Carrier Thrall")
                .withCardInHand(1,"Cast Down").build()
            selectedLand(game) shouldBe "Swamp"
        }
        test("first Forest remains correct for an immediate green one drop") {
            val game = seeded().withCardInHand(1,"Forest").withCardInHand(1,"Swamp")
                .withCardInHand(1,"Essence Warden").build()
            selectedLand(game) shouldBe "Forest"
        }
        test("second green source supports a double green spell") {
            val game = seeded().withLandsOnBattlefield(1,"Forest",1)
                .withCardInHand(1,"Forest").withCardInHand(1,"Swamp")
                .withCardInHand(1,"Garruk's Companion").build()
            selectedLand(game) shouldBe "Forest"
        }
        test("black source enables removal of a visible flying threat") {
            val game = seeded().withLandsOnBattlefield(1,"Forest",1)
                .withCardInHand(1,"Forest").withCardInHand(1,"Swamp")
                .withCardInHand(1,"Cast Down").withCardOnBattlefield(2,"Sneaky Snacker").build()
            selectedLand(game) shouldBe "Swamp"
        }
        test("tapped dual does not substitute for immediately usable black mana") {
            val game = seeded().withLandsOnBattlefield(1,"Forest",1)
                .withCardInHand(1,"Jungle Hollow").withCardInHand(1,"Swamp")
                .withCardInHand(1,"Cast Down").withCardOnBattlefield(2,"Sneaky Snacker").build()
            selectedLand(game) shouldBe "Swamp"
        }
        test("idle first turn can develop a tapped dual") {
            val game = seeded().withCardInHand(1,"Jungle Hollow").withCardInHand(1,"Forest")
                .withCardInHand(1,"Swamp").withCardInHand(1,"Carrier Thrall").build()
            selectedLand(game) shouldBe "Jungle Hollow"
        }
        test("spent mana cannot make a two mana spell affordable after the land drop") {
            val game = seeded().withCardOnBattlefield(1,"Forest",tapped=true)
                .withCardInHand(1,"Swamp").withCardInHand(1,"Carrier Thrall").build()
            val simulator = GameSimulator(cardRegistry)
            val land = simulator.getLegalActions(game.state,game.player1Id).single { it.action is PlayLand }
            val next = simulator.simulate(game.state,land.action).state
            simulator.getLegalActions(next,game.player1Id).any { it.affordable && it.action is CastSpell } shouldBe false
        }
    }
}
