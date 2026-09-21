package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Deterministic, nonexperimental probes for the exact Gate 5 Boros engines. */
class IndustrialWasteBorosPolicyAuditTest : ScenarioTestBase() {

    private fun ai(game: TestGame): AIPlayer {
        val base = AiProfile.LEGACY_V0
        return AIPlayer.create(
            cardRegistry,
            game.player1Id,
            base.copy(
                id = "boros-gate-5-readiness",
                advisorModules = base.advisorModules + IndustrialWasteBorosAdvisorModule,
            ),
        )
    }

    private fun seeded() = scenario().withPlayers().withRngSeed(0x1A57_B005L)

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun advanceToDecision(game: TestGame) {
        while (game.state.pendingDecision == null && game.state.stack.isNotEmpty()) {
            game.execute(PassPriority(game.state.priorityPlayerId!!)).error shouldBe null
        }
    }

    init {
        test("Boros casts Battle Screech from hand") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Plains", 4)
                .withCardInHand(1, "Battle Screech")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Battle Screech"
        }

        test("Boros flashes back Battle Screech by tapping three white creatures") {
            val game = seeded()
                .withCardInGraveyard(1, "Battle Screech")
                .withCardOnBattlefield(1, "Savannah Lions")
                .withCardOnBattlefield(1, "Savannah Lions")
                .withCardOnBattlefield(1, "Savannah Lions")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Battle Screech"
            action.alternativeCostType shouldBe AlternativeCostType.FLASHBACK
            action.additionalCostPayment?.tappedPermanents?.size shouldBe 3
        }

        test("Boros flashes back Prismatic Strands and names an opponent-relevant color") {
            val game = seeded()
                .withCardInGraveyard(1, "Prismatic Strands")
                .withCardOnBattlefield(1, "Savannah Lions")
                .withCardOnBattlefield(2, "Pactdoll Terror")
                .build()
            val player = ai(game)

            val action = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Prismatic Strands"
            action.alternativeCostType shouldBe AlternativeCostType.FLASHBACK
            action.additionalCostPayment?.tappedPermanents?.size shouldBe 1
            game.execute(action).error shouldBe null
            advanceToDecision(game)
            game.state.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
            player.respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<ColorChosenResponse>().color shouldBe Color.BLACK
        }

        test("Boros sacrifices Perilous Landscape to find its missing Mountain") {
            val game = seeded()
                .withCardOnBattlefield(1, "Perilous Landscape")
                .withLandsOnBattlefield(1, "Plains", 1)
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(1, "Mountain")
                .build()
            val player = ai(game)

            val action = player.chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            cardName(game, action.sourceId) shouldBe "Perilous Landscape"
            game.execute(action).error shouldBe null
            advanceToDecision(game)
            game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val response = player.respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            response.selectedCards.map { cardName(game, it) } shouldBe listOf("Mountain")
        }

        test("Boros takes Thrilling Discovery's discard and draw branch when stocked") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Plains", 1)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(1, "Thrilling Discovery")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInLibrary(1, "Plains")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Battle Screech")
                .build()
            val player = ai(game)

            val action = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Thrilling Discovery"
            game.execute(action).error shouldBe null
            advanceToDecision(game)
            game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            val yes = player.respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>()
            yes.choice shouldBe true
        }
    }
}
