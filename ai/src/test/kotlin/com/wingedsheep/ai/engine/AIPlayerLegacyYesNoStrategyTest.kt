package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.evaluation.EvalWeights
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.IfYouDoEffect
import com.wingedsheep.sdk.scripting.effects.LoseLifeEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Strategy identity regression, separate from deck pilots and the vanilla action-stream baseline. */
class AIPlayerLegacyYesNoStrategyTest : ScenarioTestBase() {
    // Same harmful-singleton / harmless-empty shape as the existing completed-branch controls.
    private val optionalSelection = card("Legacy Factory Up To One Control") {
        manaCost = "{0}"
        typeLine = "Artifact"
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            effect = MayEffect(
                IfYouDoEffect(
                    action = Effects.Composite(
                        listOf(
                            GatherCardsEffect(CardSource.FromZone(Zone.HAND), storeAs = "candidates"),
                            SelectFromCollectionEffect(
                                from = "candidates",
                                selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(1)),
                                storeSelected = "selected",
                            ),
                        )
                    ),
                    ifYouDo = LoseLifeEffect(DynamicAmount.Fixed(10), EffectTarget.Controller),
                    successCriterion = SuccessCriterion.CollectionNonEmpty("selected"),
                )
            )
        }
    }

    private fun pausedGame(): TestGame {
        val game = scenario()
            .withPlayers("Chooser", "Opponent")
            .withRngSeed(0xC0A1_0001L)
            .withLifeTotal(1, 5)
            .withCardInHand(1, optionalSelection.name)
            .withCardInHand(1, "Mountain")
            .build()
        game.castSpell(1, optionalSelection.name).error.shouldBeNull()
        game.resolveStack()
        game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        return game
    }

    /** Equal factory inputs, including the resolver wiring used for nontrivial follow-up choices. */
    private fun explicitResponder(strategy: YesNoDecisionStrategy): DecisionResponder {
        val profile = AiProfile.LEGACY_V0
        val advisors = CardAdvisorRegistry()
        profile.advisorModules.forEach { it.register(advisors) }
        val intents = if (profile.useCardIntent) IntentCatalog.of(cardRegistry) else IntentCatalog.NONE
        val evaluationIntents = if (EvalWeights.isRawProfile(profile.evalWeightsId)) {
            IntentCatalog.of(cardRegistry)
        } else {
            intents
        }
        val evaluator = EvalWeights.resolveEvaluator(
            profile.evalWeightsId,
            evaluationIntents,
            landDropIsNotCardLoss = profile.landDropIsNotCardLoss,
            sequenceLandsByUsableMana = profile.sequenceLandsByUsableMana,
            discountedRaceClock = profile.discountedRaceClock,
            creatureValuation = profile.creatureValuation,
            priceLandsInHandAsMana = profile.priceLandsInHandAsMana,
        )
        val simulator = GameSimulator(
            cardRegistry,
            resolveThroughCombatDamage = profile.resolveThroughCombatDamage,
        )
        val responder = DecisionResponder(
            simulator,
            evaluator,
            advisorRegistry = advisors,
            budgetPolicy = profile.budgetPolicy,
            intents = intents,
            yesNoStrategy = strategy,
        )
        simulator.decisionResolver = { state, decision ->
            responder.respond(state, decision, decision.playerId)
        }
        return responder
    }

    init {
        cardRegistry.register(listOf(optionalSelection))

        test("frozen profile and equal copies retain legacy YesNo behavior through both public factories") {
            val game = pausedGame()
            val state = game.state
            val decision = state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            val originalState = state.toString()
            val observations = linkedMapOf<String, Boolean>()
            fun record(label: String, response: DecisionResponse) {
                withClue("STATE_UNCHANGED_AFTER_$label") {
                    game.state.toString() shouldBe originalState
                }
                observations[label] = response.shouldBeInstanceOf<YesNoResponse>().choice
            }

            record("explicitLegacy", explicitResponder(YesNoDecisionStrategy.LEGACY_SIMULATE_BOTH_V1)
                .respond(state, decision, game.player1Id))
            record("explicitCompleted", explicitResponder(YesNoDecisionStrategy.COMPLETED_BRANCH_V1)
                .respond(state, decision, game.player1Id))
            val frozen = AiProfile.LEGACY_V0
            val renamed = frozen.copy(id = "v0-completed-control")
            renamed.copy(id = frozen.id) shouldBe frozen
            val equalCopy = frozen.copy()
            equalCopy shouldBe frozen
            val reusable = AIPlayer.Factory(cardRegistry)
            record("renamedCompanion", AIPlayer.create(cardRegistry, game.player1Id, renamed)
                .respondToDecision(state, decision))
            record("renamedReusable", reusable.create(game.player1Id, renamed)
                .respondToDecision(state, decision))
            record("frozenCompanion", AIPlayer.create(cardRegistry, game.player1Id, frozen)
                .respondToDecision(state, decision))
            record("frozenReusable", reusable.create(game.player1Id, frozen)
                .respondToDecision(state, decision))
            record("equalCopyCompanion", AIPlayer.create(cardRegistry, game.player1Id, equalCopy)
                .respondToDecision(state, decision))
            record("equalCopyReusable", reusable.create(game.player1Id, equalCopy)
                .respondToDecision(state, decision))
            println("IZZET_LEGACY_FACTORY_DIFFERENTIAL $observations")

            // Controls and all state checks precede the specific expected pre-fix regression.
            withClue("EXPLICIT_STRATEGY_DIFFERENTIAL") {
                observations["explicitLegacy"] shouldBe true
                observations["explicitCompleted"] shouldBe false
            }
            observations["renamedCompanion"] shouldBe false
            observations["renamedReusable"] shouldBe false
            withClue("FROZEN_FACTORY_LEGACY_BINDING") {
                observations["frozenCompanion"] shouldBe true
            }
            observations["frozenReusable"] shouldBe true
            observations["equalCopyCompanion"] shouldBe true
            observations["equalCopyReusable"] shouldBe true
        }

        test("changed configuration retaining the v0 id keeps completed YesNo behavior") {
            val game = pausedGame()
            val state = game.state
            val decision = state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            val originalState = state.toString()
            val frozen = AiProfile.LEGACY_V0
            // This flag is used by action selection, not respondToDecision or its evaluator.
            val altered = frozen.copy(useMeaningfulFilter = true)
            altered.id shouldBe frozen.id
            altered shouldNotBe frozen
            altered.copy(useMeaningfulFilter = frozen.useMeaningfulFilter) shouldBe frozen
            val observations = linkedMapOf<String, Boolean>()
            fun record(label: String, response: DecisionResponse) {
                withClue("STATE_UNCHANGED_AFTER_$label") {
                    game.state.toString() shouldBe originalState
                }
                observations[label] = response.shouldBeInstanceOf<YesNoResponse>().choice
            }
            record("explicitLegacy", explicitResponder(YesNoDecisionStrategy.LEGACY_SIMULATE_BOTH_V1)
                .respond(state, decision, game.player1Id))
            record("explicitCompleted", explicitResponder(YesNoDecisionStrategy.COMPLETED_BRANCH_V1)
                .respond(state, decision, game.player1Id))
            record("alteredCompanion", AIPlayer.create(cardRegistry, game.player1Id, altered)
                .respondToDecision(state, decision))
            record("alteredReusable", AIPlayer.Factory(cardRegistry).create(game.player1Id, altered)
                .respondToDecision(state, decision))
            println("IZZET_LEGACY_FACTORY_SAME_ID_CONTROL $observations")
            observations["explicitLegacy"] shouldBe true
            observations["explicitCompleted"] shouldBe false
            observations["alteredCompanion"] shouldBe false
            observations["alteredReusable"] shouldBe false
        }
    }
}
