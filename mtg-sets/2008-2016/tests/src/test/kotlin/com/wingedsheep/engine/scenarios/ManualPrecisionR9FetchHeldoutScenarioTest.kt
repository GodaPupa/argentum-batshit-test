package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * R9 independent held-out fetch/entry gate on pinned R8 engine source.
 * No fabricated zone transitions, choices or shuffle events.
 */
class ManualPrecisionR9FetchHeldoutScenarioTest : ScenarioTestBase() {
    init {
        for ((fetch, shock) in listOf(
            "Misty Rainforest" to "Steam Vents",
            "Wooded Foothills" to "Breeding Pool"
        )) {
            test("$fetch at two life cannot offer unaffordable shock payment") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withLifeTotal(1, 2)
                    .withCardOnBattlefield(1, fetch)
                    .withCardInLibrary(1, shock)
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val fetchId = game.findPermanent(fetch)!!
                val landId = game.findCardsInLibrary(1, shock).single()
                val ability = cardRegistry.getCard(fetch)!!.activatedAbilities.single().id
                game.execute(ActivateAbility(game.player1Id, fetchId, ability)).error shouldBe null
                game.getLifeTotal(1) shouldBe 1
                game.resolveStack()
                game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
                game.selectCards(listOf(landId)).error shouldBe null
                game.resolveStack()
                game.getPendingDecision() shouldBe null
                game.findPermanent(shock) shouldBe landId
                game.state.getEntity(landId)?.has<TappedComponent>() shouldBe true
                game.getLifeTotal(1) shouldBe 1
            }
        }

        test("paying fetched shock cost cannot override Zhao's independent forced-tapped replacement") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Zhao, the Moon Slayer")
                .withCardOnBattlefield(1, "Misty Rainforest")
                .withCardInLibrary(1, "Breeding Pool")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val fetch = game.findPermanent("Misty Rainforest")!!
            val shock = game.findCardsInLibrary(1, "Breeding Pool").single()
            val before = game.getLifeTotal(1)
            val ability = cardRegistry.getCard("Misty Rainforest")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, fetch, ability)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(listOf(shock)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true).error shouldBe null
            game.state.getEntity(shock)?.has<TappedComponent>() shouldBe true
            game.getLifeTotal(1) shouldBe before - 3
        }

        test("declining fetched shock cost still allows Minstrel's independent untapped replacement") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "The Wandering Minstrel")
                .withCardOnBattlefield(1, "Wooded Foothills")
                .withCardInLibrary(1, "Stomping Ground")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val fetch = game.findPermanent("Wooded Foothills")!!
            val shock = game.findCardsInLibrary(1, "Stomping Ground").single()
            val before = game.getLifeTotal(1)
            val ability = cardRegistry.getCard("Wooded Foothills")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, fetch, ability)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(listOf(shock)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(false).error shouldBe null
            game.state.getEntity(shock)?.has<TappedComponent>() shouldBe false
            game.getLifeTotal(1) shouldBe before - 1
        }

        test("shock choice is genuinely pre-entry and resumes identity, zone event, search and shuffle once") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withRngSeed(20261020L)
                .withCardOnBattlefield(1, "Misty Rainforest")
                .withCardInLibrary(1, "Steam Vents")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Island")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val fetch = game.findPermanent("Misty Rainforest")!!
            val shock = game.findCardsInLibrary(1, "Steam Vents").single()
            val objectBefore = game.state.objectRef(shock)!!
            val beforeLife = game.getLifeTotal(1)
            val ability = cardRegistry.getCard("Misty Rainforest")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, fetch, ability)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(listOf(shock)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            // No entering object or shock payment may be committed before the choice.
            game.findCardsInLibrary(1, "Steam Vents").single() shouldBe shock
            game.state.objectRef(shock) shouldBe objectBefore
            game.findPermanent("Steam Vents") shouldBe null
            game.getLifeTotal(1) shouldBe beforeLife - 1

            val resumed = game.answerYesNo(true)
            resumed.error shouldBe null
            game.findPermanent("Steam Vents") shouldBe shock
            game.state.objectRef(shock) shouldNotBe objectBefore
            game.findCardsInLibrary(1, "Steam Vents").isEmpty() shouldBe true
            game.getLifeTotal(1) shouldBe beforeLife - 3
            resumed.events.filterIsInstance<ZoneChangeEvent>()
                .count { it.toZone == Zone.BATTLEFIELD } shouldBe 1
            resumed.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 1
            game.getPendingDecision() shouldBe null
        }
    }
}
