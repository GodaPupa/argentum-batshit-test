package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Exact Recursive Eggs engine card qualification. These are deterministic fixtures,
 * not allocations from the frozen Industrial Waste v2 R1 ordering corpus.
 * CR 307.5 / 602.5d restrict only the recursion ability to sorcery timing.
 * CR 608.2b prevents its draw if its only target is illegal on resolution.
 */
class DrossSkullbombScenarioTest : ScenarioTestBase() {
    init {
        test("one generic mana sacrifices the Skullbomb and draws through the stack") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Dross Skullbomb")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Myr Retriever")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bomb = game.findPermanent("Dross Skullbomb")!!
            val draw = cardRegistry.getCard("Dross Skullbomb")!!.activatedAbilities[0]
            game.execute(ActivateAbility(game.player1Id, bomb, draw.id)).error shouldBe null
            game.isInGraveyard(1, "Dross Skullbomb") shouldBe true
            game.handSize(1) shouldBe 0
            game.state.stack.size shouldBe 1
            game.resolveStack()
            game.isInHand(1, "Myr Retriever") shouldBe true
        }

        test("two generic and one black return a creature and then draw one card") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Dross Skullbomb")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInGraveyard(1, "Myr Retriever")
                .withCardInLibrary(1, "Ashnod's Altar")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bomb = game.findPermanent("Dross Skullbomb")!!
            val retriever = game.findCardsInGraveyard(1, "Myr Retriever").single()
            val recur = cardRegistry.getCard("Dross Skullbomb")!!.activatedAbilities[1]
            game.execute(ActivateAbility(
                game.player1Id, bomb, recur.id,
                targets = listOf(ChosenTarget.Card(retriever, game.player1Id, Zone.GRAVEYARD)),
            )).error shouldBe null
            game.isInGraveyard(1, "Dross Skullbomb") shouldBe true
            game.handSize(1) shouldBe 0
            game.resolveStack()
            game.isInHand(1, "Myr Retriever") shouldBe true
            game.isInHand(1, "Ashnod's Altar") shouldBe true
            game.handSize(1) shouldBe 2
        }

        test("recursion is unavailable in the end step but the draw ability remains legal") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Dross Skullbomb")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInGraveyard(1, "Myr Retriever")
                .withCardInLibrary(1, "Ashnod's Altar")
                .withActivePlayer(1)
                .inPhase(Phase.ENDING, Step.END)
                .build()

            val bomb = game.findPermanent("Dross Skullbomb")!!
            val retriever = game.findCardsInGraveyard(1, "Myr Retriever").single()
            val abilities = cardRegistry.getCard("Dross Skullbomb")!!.activatedAbilities
            game.execute(ActivateAbility(
                game.player1Id, bomb, abilities[1].id,
                targets = listOf(ChosenTarget.Card(retriever, game.player1Id, Zone.GRAVEYARD)),
            )).error shouldNotBe null
            game.isOnBattlefield("Dross Skullbomb") shouldBe true
            game.isInGraveyard(1, "Myr Retriever") shouldBe true
            game.state.stack.size shouldBe 0

            game.execute(ActivateAbility(game.player1Id, bomb, abilities[0].id)).error shouldBe null
            game.resolveStack()
            game.isInHand(1, "Ashnod's Altar") shouldBe true
            game.isInGraveyard(1, "Myr Retriever") shouldBe true
        }

        test("generic mana cannot pay the black recursion pip and a rejected attempt pays no costs") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Dross Skullbomb")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInGraveyard(1, "Myr Retriever")
                .withCardInLibrary(1, "Ashnod's Altar")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bomb = game.findPermanent("Dross Skullbomb")!!
            val retriever = game.findCardsInGraveyard(1, "Myr Retriever").single()
            val abilities = cardRegistry.getCard("Dross Skullbomb")!!.activatedAbilities
            val before = game.state
            game.execute(ActivateAbility(
                game.player1Id, bomb, abilities[1].id,
                targets = listOf(ChosenTarget.Card(retriever, game.player1Id, Zone.GRAVEYARD)),
            )).error shouldNotBe null
            game.state shouldBe before
            game.execute(ActivateAbility(game.player1Id, bomb, abilities[0].id)).error shouldBe null
            game.resolveStack()
            game.isInHand(1, "Ashnod's Altar") shouldBe true
        }

        test("an illegal sole recursion target prevents both return and draw on resolution") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Dross Skullbomb")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withCardInGraveyard(1, "Myr Retriever")
                .withCardInLibrary(1, "Ashnod's Altar")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bomb = game.findPermanent("Dross Skullbomb")!!
            val retriever = game.findCardsInGraveyard(1, "Myr Retriever").single()
            val recur = cardRegistry.getCard("Dross Skullbomb")!!.activatedAbilities[1]
            game.execute(ActivateAbility(
                game.player1Id, bomb, recur.id,
                targets = listOf(ChosenTarget.Card(retriever, game.player1Id, Zone.GRAVEYARD)),
            )).error shouldBe null
            // Deterministic fixture intervention models graveyard removal before resolution.
            game.state = game.state
                .removeFromZone(ZoneKey(game.player1Id, Zone.GRAVEYARD), retriever)
                .addToZone(ZoneKey(game.player1Id, Zone.EXILE), retriever)
            game.resolveStack()

            game.isInExile(1, "Myr Retriever") shouldBe true
            game.isInGraveyard(1, "Dross Skullbomb") shouldBe true
            game.handSize(1) shouldBe 0
            game.librarySize(1) shouldBe 1
        }
    }
}
