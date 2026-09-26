package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.vis.cards.CryptRats
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Uses the canonical VIS registry card; the earlier project-only fixture is separately retained in history. */
class CryptRatsScenarioTest : ScenarioTestBase() {
    private fun setup() = scenario().withPlayers("Rats", "Opponent")
        .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Swamp")
        .withCardInLibrary(2, "Island").withCardInLibrary(2, "Mountain")
        .withRngSeed(0xFEC005).withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.finish() {
        resolveStack().forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null; state.stack shouldBe emptyList(); state.gameOver shouldBe false
    }
    private fun TestGame.pulse(x: Int) = execute(ActivateAbility(
        player1Id, findPermanent("Crypt Rats")!!, CryptRats.activatedAbilities.single().id, xValue = x
    ))

    init {
        test("black X damages both players and every creature including friendly creatures and fliers") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Ornithopter")
                .withCardOnBattlefield(2, "Craw Wurm").withLandsOnBattlefield(1, "Swamp", 2).build()
            game.pulse(2).error shouldBe null; game.finish()
            game.isInGraveyard(1, "Crypt Rats") shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Ornithopter") shouldBe true
            game.isOnBattlefield("Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 18; game.getLifeTotal(2) shouldBe 18
            game.findPermanents("Swamp").all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
        }

        test("nonblack mana cannot pay X and a rejected payment changes no state") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats")
                .withLandsOnBattlefield(1, "Swamp", 1).withLandsOnBattlefield(1, "Forest", 1).build()
            val before = game.state
            val rejected = game.pulse(2)
            rejected.error.shouldNotBeNull()
            rejected.state shouldBe before
            game.state shouldBe before
        }

        test("X zero is legal and causes neither deathtouch destruction nor lifelink gain") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats").withCardOnBattlefield(2, "Craw Wurm")
                .withCardInHand(1, "Toxin Analysis").withLandsOnBattlefield(1, "Swamp", 1).build()
            game.castSpell(1, "Toxin Analysis", game.findPermanent("Crypt Rats")!!).error shouldBe null
            game.finish(); game.pulse(0).error shouldBe null; game.finish()
            game.isOnBattlefield("Crypt Rats") shouldBe true; game.isOnBattlefield("Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 20; game.getLifeTotal(2) shouldBe 20
            game.findPermanents("Clue").size shouldBe 1
        }

        test("Toxin deathtouch sweeps large creatures and lifelink counts actual creature and player damage") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Craw Wurm")
                .withCardInHand(1, "Toxin Analysis").withLandsOnBattlefield(1, "Swamp", 2).build()
            game.castSpell(1, "Toxin Analysis", game.findPermanent("Crypt Rats")!!).error shouldBe null
            game.finish(); game.pulse(1).error shouldBe null; game.finish()
            game.isInGraveyard(1, "Crypt Rats") shouldBe true
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
            game.getLifeTotal(1) shouldBe 24; game.getLifeTotal(2) shouldBe 19
            game.findPermanents("Clue").size shouldBe 1
        }

        test("indestructible creatures survive lethal marked damage") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats")
                .withCardOnBattlefield(2, "Darksteel Myr").withLandsOnBattlefield(1, "Swamp", 1).build()
            game.pulse(1).error shouldBe null; game.finish()
            game.isInGraveyard(1, "Crypt Rats") shouldBe true
            game.isOnBattlefield("Darksteel Myr") shouldBe true
        }

        test("lifelink at one life prevents a racing loss before state-based actions") {
            val game = setup().withLifeTotal(1, 1).withLifeTotal(2, 2)
                .withCardOnBattlefield(1, "Crypt Rats").withCardOnBattlefield(2, "Craw Wurm")
                .withCardInHand(1, "Toxin Analysis").withLandsOnBattlefield(1, "Swamp", 2).build()
            game.castSpell(1, "Toxin Analysis", game.findPermanent("Crypt Rats")!!).error shouldBe null
            game.finish(); game.pulse(1).error shouldBe null; game.finish()
            game.getLifeTotal(1) shouldBe 4
            game.getLifeTotal(2) shouldBe 1
            game.state.gameOver shouldBe false
            game.isInGraveyard(1, "Crypt Rats") shouldBe true
            game.isInGraveyard(2, "Craw Wurm") shouldBe true
        }

        test("a tapped summoning-sick Rat may activate because the cost has no tap symbol") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats", tapped = true, summoningSickness = true)
                .withLandsOnBattlefield(1, "Swamp", 1).build()
            game.pulse(1).error shouldBe null; game.finish()
            game.isInGraveyard(1, "Crypt Rats") shouldBe true
            game.getLifeTotal(1) shouldBe 19; game.getLifeTotal(2) shouldBe 19
        }

        test("two queued activations both resolve after the first resolving pulse kills their source") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats").withCardOnBattlefield(2, "Craw Wurm")
                .withLandsOnBattlefield(1, "Swamp", 2).build()
            game.pulse(1).error shouldBe null; game.pulse(1).error shouldBe null
            game.state.stack.size shouldBe 2; game.finish()
            game.isInGraveyard(1, "Crypt Rats") shouldBe true
            game.state.getEntity(game.findPermanent("Craw Wurm")!!)!!.get<DamageComponent>()!!.amount shouldBe 2
            game.getLifeTotal(1) shouldBe 18; game.getLifeTotal(2) shouldBe 18
        }

        test("removal in response does not counter the already activated damage ability") {
            val game = setup().withCardOnBattlefield(1, "Crypt Rats").withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Mountain", 1).build()
            val rats = game.findPermanent("Crypt Rats")!!
            game.pulse(2).error shouldBe null
            game.castSpell(1, "Lightning Bolt", rats).error shouldBe null; game.finish()
            game.isInGraveyard(1, "Crypt Rats") shouldBe true; game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            game.getLifeTotal(1) shouldBe 18; game.getLifeTotal(2) shouldBe 18
        }
    }
}
