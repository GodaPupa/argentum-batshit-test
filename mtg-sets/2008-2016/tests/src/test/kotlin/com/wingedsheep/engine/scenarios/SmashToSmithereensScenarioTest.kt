package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SmashToSmithereensScenarioTest : ScenarioTestBase() {
    init {
        test("destroys a stolen artifact before damaging its last controller instead of its owner") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Smash to Smithereens")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(1, "Bonesplitter")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val artifact = game.findPermanent("Bonesplitter")!!
            // Fixture: player 1 owns the artifact, player 2 currently controls it.
            game.state = game.state.updateEntity(artifact) { it.with(ControllerComponent(game.player2Id)) }
            game.state.projectedState.getController(artifact) shouldBe game.player2Id
            val smash = game.findCardsInHand(1, "Smash to Smithereens").single()
            game.castSpell(1, "Smash to Smithereens", artifact).error shouldBe null
            val results = game.resolveStack()
            results.forEach { it.error shouldBe null }
            game.state.getGraveyard(game.player1Id).contains(artifact) shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 17
            val events = results.flatMap { it.events }
            val destroyed = events.indexOfFirst {
                it is ZoneChangeEvent && it.entityId == artifact && it.toZone == Zone.GRAVEYARD
            }
            val damage = events.indexOfFirst { it is DamageDealtEvent && it.sourceId == smash }
            (destroyed >= 0 && damage > destroyed) shouldBe true
            (events[damage] as DamageDealtEvent).targetId shouldBe game.player2Id
        }

        test("an indestructible artifact survives but its controller still takes three damage") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Smash to Smithereens")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(2, "Darksteel Citadel")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val artifact = game.findPermanent("Darksteel Citadel")!!
            game.castSpell(1, "Smash to Smithereens", artifact).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.findPermanent("Darksteel Citadel") shouldBe artifact
            game.getLifeTotal(2) shouldBe 17
        }

        test("destroying the target in response makes Smash do no damage") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Smash to Smithereens")
                .withCardInHand(2, "Naturalize")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardOnBattlefield(2, "Bonesplitter")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val artifact = game.findPermanent("Bonesplitter")!!
            game.castSpell(1, "Smash to Smithereens", artifact).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Naturalize", artifact).error shouldBe null
            val results = game.resolveStack()
            results.forEach { it.error shouldBe null }
            game.findPermanent("Bonesplitter") shouldBe null
            game.getLifeTotal(1) shouldBe 20
            game.getLifeTotal(2) shouldBe 20
            results.flatMap { it.events }.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
        }

        test("one artifact is the only target and a nonartifact is rejected without payment") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Smash to Smithereens")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.state
            game.castSpell(1, "Smash to Smithereens", game.findPermanent("Grizzly Bears")!!)
                .error shouldNotBe null
            game.state shouldBe before
        }
    }
}
