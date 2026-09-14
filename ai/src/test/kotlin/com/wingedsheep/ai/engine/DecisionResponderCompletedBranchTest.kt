package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.IfYouDoEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Shared decision-policy controls for optional effects whose next choice is "up to one card." */
class DecisionResponderCompletedBranchTest : ScenarioTestBase() {

    private val beneficialSelection = card("Completed Branch Beneficial Selection") {
        manaCost = "{0}"
        typeLine = "Artifact"
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            effect = MayEffect(
                IfYouDoEffect(
                    action = Patterns.Hand.discardCards(1),
                    ifYouDo = Effects.GainLife(10),
                )
            )
        }
    }

    private val distinctEmptySelection = card("Completed Branch Beneficial Empty Selection") {
        manaCost = "{0}"
        typeLine = "Artifact"
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            effect = MayEffect(
                IfYouDoEffect(
                    action = Patterns.Hand.discardCards(1),
                    ifYouDo = Effects.LoseLife(10),
                    ifYouDont = Effects.GainLife(10),
                )
            )
        }
    }

    private fun seeded() = scenario().withPlayers("Chooser", "Opponent").withRngSeed(0xC0A1_0001L)

    private fun ai(game: TestGame) =
        AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun resolveOptionalEtb(game: TestGame, cardName: String) {
        game.castSpell(1, cardName).error.shouldBeNull()
        game.resolveStack()
        game.state.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.YesNoDecision>()
    }

    init {
        cardRegistry.register(listOf(beneficialSelection, distinctEmptySelection))

        test("min zero max one completed branch chooses a beneficial legal selection") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, beneficialSelection.name)
                .withCardInHand(1, "Mountain")
                .build()
            val candidate = game.findCardsInHand(1, "Mountain").single()
            resolveOptionalEtb(game, beneficialSelection.name)

            val agent = ai(game)
            val accept = agent.respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>()
            withClue("a materially beneficial legal singleton branch must justify acceptance") {
                accept.choice.shouldBeTrue()
            }

            game.answerYesNo(true).error.shouldBeNull()
            val selection = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            selection.minSelections shouldBe 0
            selection.maxSelections shouldBe 1
            val response = agent.respondToDecision(game.state, selection)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            withClue("the follow-up must preserve the singleton branch that justified acceptance") {
                response.selectedCards shouldBe listOf(candidate)
                cardName(game, response.selectedCards.single()) shouldBe "Mountain"
            }
        }

        test("min zero max one completed branch declines without a nonempty option or empty payoff") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, beneficialSelection.name)
                .build()
            resolveOptionalEtb(game, beneficialSelection.name)

            withClue("the legal empty branch is equivalent to declining and must not justify acceptance") {
                ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                    .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeFalse()
            }
        }

        test("min zero max one completed branch preserves a distinct beneficial empty selection") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, distinctEmptySelection.name)
                .build()
            resolveOptionalEtb(game, distinctEmptySelection.name)

            val agent = ai(game)
            val accept = agent.respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>()
            withClue("the real empty branch gains life and is materially better than declining") {
                accept.choice.shouldBeTrue()
            }

            game.answerYesNo(true).error.shouldBeNull()
            val selection = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val response = agent.respondToDecision(game.state, selection)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            withClue("acceptance must retain the evaluated empty-selection continuation") {
                response.selectedCards.shouldBeEmpty()
            }
        }
    }
}
