package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Exact v0.7 card identities in synthetic fixtures for the shared CR 305 guard.
 * These do not initialize the frozen 100-card deck or qualify its Commander pilots.
 * The fixed fixture seed is excluded from every official Phase 2 allocation.
 */
class ManualTransmissionLandSpellGuardScenarioTest : ScenarioTestBase() {
    init {
        for (name in listOf("Forest", "Command Tower")) {
            test("$name cannot be cast as a spell but can still be played as a land") {
                val game = scenario().withPlayers().withRngSeed(9250925013L)
                    .withCardInHand(1, name)
                    .build()
                val land = game.findCardsInHand(1, name).single()
                val before = game.state
                game.getLegalActions(1).any {
                    it.actionType == "CastSpell" && it.description.contains(name)
                } shouldBe false
                game.getLegalActions(1).any {
                    it.actionType == "PlayLand" && it.description.contains(name)
                } shouldBe true

                game.execute(CastSpell(game.player1Id, land)).error shouldNotBe null
                game.state shouldBe before
                game.isInHand(1, name) shouldBe true
                game.state.stack.isEmpty() shouldBe true
                game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.total shouldBe 0

                game.execute(PlayLand(game.player1Id, land)).error shouldBe null
                game.findPermanent(name) shouldBe land
                game.isInHand(1, name) shouldBe false
                game.state.projectedState.hasType(land, "LAND") shouldBe true
                game.state.projectedState.getController(land) shouldBe game.player1Id
                game.state.stack.isEmpty() shouldBe true
                game.hasPendingDecision() shouldBe false
                if (name == "Forest") {
                    game.execute(ActivateAbility(game.player1Id, land, AbilityId.intrinsicMana('G'))).error shouldBe null
                    game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.green shouldBe 1
                    game.state.stack.isEmpty() shouldBe true
                }
            }
        }
    }
}
