package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class ChromaticStarScenarioTest : ScenarioTestBase() {
    init {
        test("mana is immediate while the independent draw trigger waits on the stack") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Chromatic Star")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val star = game.findPermanent("Chromatic Star")!!
            val ability = cardRegistry.getCard("Chromatic Star")!!.script.activatedAbilities.single()
            game.execute(ActivateAbility(game.player1Id, star, ability.id, manaColorChoice = Color.RED)).error shouldBe null
            game.isInGraveyard(1, "Chromatic Star") shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.red shouldBe 1
            game.handSize(1) shouldBe 0
            game.state.stack.size shouldBe 1
            game.resolveStack()
            game.handSize(1) shouldBe 1
            game.librarySize(1) shouldBe 1
        }

        test("sacrificing Star to Eviscerators Insight draws one in addition to the spell's two") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Chromatic Star")
                .withCardInHand(1, "Eviscerator's Insight")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpellWithAdditionalSacrifice(1, "Eviscerator's Insight", "Chromatic Star").error shouldBe null
            game.isInGraveyard(1, "Chromatic Star") shouldBe true
            game.handSize(1) shouldBe 0
            game.resolveStack()
            game.handSize(1) shouldBe 3
            game.librarySize(1) shouldBe 1
        }

        test("destroying Star draws without adding mana") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Chromatic Star")
                .withCardInHand(1, "Naturalize")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Naturalize", game.findPermanent("Chromatic Star")!!).error shouldBe null
            game.resolveStack()
            game.handSize(1) shouldBe 1
            game.isInGraveyard(1, "Chromatic Star") shouldBe true
            (game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()?.total ?: 0) shouldBe 0
        }

        test("milling Star with Malevolent Rumble does not trigger its battlefield departure ability") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Malevolent Rumble")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Myr Retriever")
                .withCardInLibrary(1, "Chromatic Star")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Swamp")
                .withCardInLibrary(1, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Malevolent Rumble").error shouldBe null
            game.resolveStack()
            game.selectCards(game.findCardsInLibrary(1, "Myr Retriever")).error shouldBe null
            game.resolveStack()
            game.isInGraveyard(1, "Chromatic Star") shouldBe true
            game.isInHand(1, "Myr Retriever") shouldBe true
            game.handSize(1) shouldBe 1
            game.librarySize(1) shouldBe 1
        }
    }
}
