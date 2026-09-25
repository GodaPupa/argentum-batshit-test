package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class MurmuringMysticScenarioTest : ScenarioTestBase() {
    init {
        test("own instant creates one exact Bird Illusion before the spell resolves") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Murmuring Mystic")
                .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.state.stack.size shouldBe 2
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            val bird = game.findAllPermanents("Bird Illusion Token").single()
            val projected = game.state.projectedState
            projected.getPower(bird) shouldBe 1
            projected.getToughness(bird) shouldBe 1
            projected.getColors(bird) shouldBe setOf("BLUE")
            projected.getSubtypes(bird) shouldBe setOf("Bird", "Illusion")
            projected.hasKeyword(bird, Keyword.FLYING) shouldBe true
            projected.getController(bird) shouldBe game.player1Id
            game.getLifeTotal(2) shouldBe 20
            game.resolveStack().forEach { it.error shouldBe null }
            game.getLifeTotal(2) shouldBe 17
        }

        test("own sorcery also triggers while creature spells and opponent instants do not") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Murmuring Mystic")
                .withCardInHand(1, "Divination").withLandsOnBattlefield(1, "Island", 3)
                .withCardInLibrary(1, "Island").withCardInLibrary(1, "Island")
                .withCardInHand(1, "Grizzly Bears").withLandsOnBattlefield(1, "Forest", 2)
                .withCardInHand(2, "Lightning Bolt").withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            game.state.stack.size shouldBe 1
            game.resolveStack().forEach { it.error shouldBe null }
            game.findAllPermanents("Bird Illusion Token").size shouldBe 0
            game.passPriority().error shouldBe null
            game.castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
            game.state.stack.size shouldBe 1
            game.resolveStack().forEach { it.error shouldBe null }
            game.findAllPermanents("Bird Illusion Token").size shouldBe 0
            game.castSpell(1, "Divination").error shouldBe null
            game.state.stack.size shouldBe 2
            game.resolveStack().forEach { it.error shouldBe null }
            game.findAllPermanents("Bird Illusion Token").size shouldBe 1
            game.librarySize(1) shouldBe 0
        }

        test("the trigger survives countering its spell") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Murmuring Mystic")
                .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Lightning Bolt").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.findAllPermanents("Bird Illusion Token").size shouldBe 1
            game.getLifeTotal(2) shouldBe 20
            game.findCardsInGraveyard(1, "Lightning Bolt").size shouldBe 1
        }

        test("the trigger survives removal of Mystic") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Murmuring Mystic")
                .withCardInHand(1, "Lightning Bolt").withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(2, "Murder").withLandsOnBattlefield(2, "Swamp", 3)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Murder", game.findPermanent("Murmuring Mystic")!!).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.findPermanent("Murmuring Mystic") shouldBe null
            game.findAllPermanents("Bird Illusion Token").size shouldBe 1
            game.getLifeTotal(2) shouldBe 17
        }

        test("copying a spell creates no extra Mystic trigger") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Murmuring Mystic")
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Display of Power")
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.castSpellTargetingStackSpell(1, "Display of Power", "Lightning Bolt").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.hasPendingDecision() shouldBe true
            game.selectTargets(listOf(game.player2Id)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            // Two casts (Bolt and Display), three resolved spells (including Bolt's copy).
            game.findAllPermanents("Bird Illusion Token").size shouldBe 2
            game.getLifeTotal(2) shouldBe 14
            game.state.stack.size shouldBe 0
        }
    }
}
