package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Prospective opponent-card mechanic receiving; no Izzet game or pilot evidence. */
class VeteranBeastriderReceivingTest : ScenarioTestBase() {
    init {
        test("Veteran end step untaps each own creature, never an opposing creature or land") {
            val game = scenario().withPlayers("Veteran controller", "Opponent")
                .withCardOnBattlefield(1, "Veteran Beastrider", tapped = true)
                .withCardOnBattlefield(1, "Grizzly Bears", tapped = true)
                .withCardOnBattlefield(2, "Centaur Courser", tapped = true)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val veteran = game.findPermanent("Veteran Beastrider")!!
            val bear = game.findPermanent("Grizzly Bears")!!
            val courser = game.findPermanent("Centaur Courser")!!
            val forest = game.findPermanent("Forest")!!
            game.state = game.state.updateEntity(forest) { it.with(TappedComponent) }
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(veteran)!!.has<TappedComponent>() shouldBe false
            game.state.getEntity(bear)!!.has<TappedComponent>() shouldBe false
            game.state.getEntity(courser)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
        }

        test("Veteran paid activation buffs all own creatures but not opponent") {
            val game = scenario().withPlayers("Veteran controller", "Opponent")
                .withCardOnBattlefield(1, "Veteran Beastrider")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Centaur Courser")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withLandsOnBattlefield(1, "Plains", 1)
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val veteran = game.findPermanent("Veteran Beastrider")!!
            val bear = game.findPermanent("Grizzly Bears")!!
            val courser = game.findPermanent("Centaur Courser")!!
            val ability = cardRegistry.requireCard("Veteran Beastrider").activatedAbilities.single()
            game.execute(ActivateAbility(game.player1Id, veteran, ability.id)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.projectedState.getPower(veteran) shouldBe 4
            game.state.projectedState.getToughness(veteran) shouldBe 5
            game.state.projectedState.getPower(bear) shouldBe 3
            game.state.projectedState.getToughness(bear) shouldBe 3
            game.state.projectedState.getPower(courser) shouldBe 3
            game.state.projectedState.getToughness(courser) shouldBe 3
        }
    }
}
