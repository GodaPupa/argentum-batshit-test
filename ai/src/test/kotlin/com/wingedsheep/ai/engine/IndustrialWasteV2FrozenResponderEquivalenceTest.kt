package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.evaluation.BoardEvaluator
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.mana.OptionalCastAffordability
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed mechanical equivalence controls. No candidate list, corpus member or pilot tuning. */
class IndustrialWasteV2FrozenResponderEquivalenceTest : ScenarioTestBase() {
    private val exactOneBeneficialSingleton = card("Exact One Beneficial Singleton") {
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

    private val exactOneBeneficialEmpty = card("Exact One Beneficial Empty") {
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

    private val upToOneBeneficialSingleton = chooseUpToOneCard(
        name = "Up To One Beneficial Singleton",
        ifChosen = Effects.GainLife(10),
    )

    private val upToOneBeneficialEmpty = chooseUpToOneCard(
        name = "Up To One Beneficial Empty",
        ifChosen = LoseLifeEffect(DynamicAmount.Fixed(10), EffectTarget.Controller),
        ifEmpty = Effects.GainLife(10),
    )

    private val upToOneDecline = chooseUpToOneCard(
        name = "Up To One Decline",
        ifChosen = LoseLifeEffect(DynamicAmount.Fixed(10), EffectTarget.Controller),
    )

    private val strongCandidate = card("Completed Branch Strong Candidate") {
        manaCost = "{7}"
        typeLine = "Creature — Test"
        power = 8
        toughness = 8
    }

    private val weakCandidate = card("Completed Branch Weak Candidate") {
        manaCost = "{0}"
        typeLine = "Creature — Test"
        power = 0
        toughness = 1
    }

    private val upToOneMultipleCandidates = card("Up To One Multiple Candidates") {
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
                            MoveCollectionEffect(
                                from = "selected",
                                destination = CardDestination.ToZone(Zone.BATTLEFIELD),
                            ),
                        )
                    ),
                    ifYouDo = Effects.GainLife(1),
                    successCriterion = SuccessCriterion.CollectionNonEmpty("selected"),
                )
            )
        }
    }

    private fun chooseUpToOneCard(
        name: String,
        ifChosen: Effect,
        ifEmpty: Effect? = null,
    ) = card(name) {
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
                    ifYouDo = ifChosen,
                    ifYouDont = ifEmpty,
                    successCriterion = SuccessCriterion.CollectionNonEmpty("selected"),
                )
            )
        }
    }

    private fun seeded() = scenario().withPlayers("Chooser", "Opponent").withRngSeed(0xC0A1_0001L)

    private fun resolveOptionalEtb(game: TestGame, name: String) {
        game.castSpell(1, name).error.shouldBeNull()
        game.resolveStack()
        game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
    }

    private fun legacy(advisors: CardAdvisorRegistry = CardAdvisorRegistry(),
                       evaluator: BoardEvaluator = AIPlayer.defaultEvaluator()) = DecisionResponder(
        GameSimulator(cardRegistry), evaluator, advisors,
        yesNoStrategy = YesNoDecisionStrategy.LEGACY_SIMULATE_BOTH_V1,
    )

    /** The original six-line comparison evaluated against the same real engine snapshot. */
    private fun assertEquivalent(
        game: TestGame,
        advisors: CardAdvisorRegistry = CardAdvisorRegistry(),
    ): Pair<DecisionResponder, YesNoResponse> {
        val before = game.state
        val player = game.player1Id
        val decision = before.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        val evaluator = AIPlayer.defaultEvaluator()
        val expectedViews = mutableListOf<Pair<GameState, EntityId>>()
        val simulator = GameSimulator(cardRegistry)
        fun evaluateResult(result: SimulationResult, playerId: EntityId): Double =
            result.scoreOrRankLast {
                expectedViews += it to playerId
                evaluator.evaluate(it, it.projectedState, playerId)
            }
        val yesResult = simulator.simulateDecision(before, YesNoResponse(decision.id, true))
        val noResult = simulator.simulateDecision(before, YesNoResponse(decision.id, false))
        val yesScore = evaluateResult(yesResult, player)
        val noScore = evaluateResult(noResult, player)
        val expected = YesNoResponse(decision.id, yesScore >= noScore)

        val actualViews = mutableListOf<Pair<GameState, EntityId>>()
        val responder = legacy(advisors, BoardEvaluator { state, projected, playerId ->
            actualViews += state to playerId
            evaluator.evaluate(state, projected, playerId)
        })
        val actual = responder.respond(before, decision, player).shouldBeInstanceOf<YesNoResponse>()
        responder.yesNoStrategy shouldBe YesNoDecisionStrategy.LEGACY_SIMULATE_BOTH_V1
        actual shouldBe expected
        expectedViews.size shouldBe 2
        actualViews shouldBe expectedViews
        game.state shouldBe before
        return responder to actual
    }

    private fun assertNoCommittedFollowUp(game: TestGame, responder: DecisionResponder) {
        game.answerYesNo(true).error.shouldBeNull()
        val before = game.state
        val selection = before.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        selection.minSelections shouldBe 0
        selection.maxSelections shouldBe 1
        responder.respond(before, selection, game.player1Id) shouldBe
            legacy().respond(before, selection, game.player1Id)
        game.state shouldBe before
    }

    init {
        cardRegistry.register(listOf(
            exactOneBeneficialSingleton, exactOneBeneficialEmpty,
            upToOneBeneficialSingleton, upToOneBeneficialEmpty, upToOneDecline,
            strongCandidate, weakCandidate, upToOneMultipleCandidates,
        ))

        test("legacy exact-one beneficial singleton retains both original evaluation views") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialSingleton.name).withCardInHand(1, "Mountain").build()
            resolveOptionalEtb(game, exactOneBeneficialSingleton.name)
            assertEquivalent(game).second.choice shouldBe true
        }

        test("legacy exact-one empty beneficial branch retains both original evaluation views") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialEmpty.name).build()
            resolveOptionalEtb(game, exactOneBeneficialEmpty.name)
            assertEquivalent(game).second.choice shouldBe true
        }

        test("legacy no-candidate no-payoff tie retains greater-than-or-equal acceptance") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialSingleton.name).build()
            resolveOptionalEtb(game, exactOneBeneficialSingleton.name)
            assertEquivalent(game).second.choice shouldBe true
        }

        test("legacy up-to-one beneficial singleton stores no completed follow-up") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, upToOneBeneficialSingleton.name).withCardInHand(1, "Mountain").build()
            resolveOptionalEtb(game, upToOneBeneficialSingleton.name)
            val (responder, answer) = assertEquivalent(game)
            answer.choice shouldBe true
            assertNoCommittedFollowUp(game, responder)
        }

        test("legacy up-to-one beneficial empty branch stores no completed follow-up") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, upToOneBeneficialEmpty.name).withCardInHand(1, "Mountain").build()
            resolveOptionalEtb(game, upToOneBeneficialEmpty.name)
            val (responder, answer) = assertEquivalent(game)
            answer.choice shouldBe true
            assertNoCommittedFollowUp(game, responder)
        }

        test("legacy harmful singleton keeps its original tie behavior without default-policy repair") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, upToOneDecline.name).withCardInHand(1, "Mountain").build()
            resolveOptionalEtb(game, upToOneDecline.name)
            val (responder, answer) = assertEquivalent(game)
            answer.choice shouldBe true
            assertNoCommittedFollowUp(game, responder)
        }

        test("legacy multiple candidates retain independent generic selection rather than a stored branch") {
            val game = seeded().withLifeTotal(1, 5).withCardInHand(1, upToOneMultipleCandidates.name)
                .withCardInHand(1, strongCandidate.name).withCardInHand(1, weakCandidate.name).build()
            resolveOptionalEtb(game, upToOneMultipleCandidates.name)
            val (responder, answer) = assertEquivalent(game)
            answer.choice shouldBe true
            assertNoCommittedFollowUp(game, responder)
        }

        test("legacy advisor answer remains first and bypasses generic simulation") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialSingleton.name).withCardInHand(1, "Mountain").build()
            resolveOptionalEtb(game, exactOneBeneficialSingleton.name)
            val before = game.state
            val decision = before.pendingDecision!!
            var calls = 0
            val advisors = CardAdvisorRegistry().apply { register(object : CardAdvisor {
                override val cardNames = setOf(exactOneBeneficialSingleton.name)
                override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse {
                    calls++
                    context.state shouldBe before
                    context.playerId shouldBe game.player1Id
                    return YesNoResponse(context.decision.id, false)
                }
            }) }
            val responder = legacy(advisors, BoardEvaluator { _, _, _ -> error("Advisor must answer first") })
            responder.respond(before, decision, game.player1Id) shouldBe YesNoResponse(decision.id, false)
            calls shouldBe 1
            game.state shouldBe before
        }

        test("legacy advisor deferral retains original two-simulation generic evaluation") {
            val game = seeded().withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialSingleton.name).withCardInHand(1, "Mountain").build()
            resolveOptionalEtb(game, exactOneBeneficialSingleton.name)
            var calls = 0
            val advisors = CardAdvisorRegistry().apply { register(object : CardAdvisor {
                override val cardNames = setOf(exactOneBeneficialSingleton.name)
                override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
                    calls++
                    return null
                }
            }) }
            assertEquivalent(game, advisors).second.choice shouldBe true
            calls shouldBe 1
        }

        test("legacy unaffordable optional cast retains the original evaluated decline without preflight") {
            val game = seeded().withLifeTotal(2, 20).withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize").withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain").withCardInLibrary(1, "Mountain").build()
            val outlet = game.findCardsInHand(1, "Grab the Prize").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()
            game.execute(CastSpell(game.player1Id, outlet,
                additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                paymentStrategy = PaymentStrategy.AutoPay)).error.shouldBeNull()
            while (game.state.pendingDecision == null && game.state.stack.isNotEmpty()) {
                game.execute(PassPriority(game.state.priorityPlayerId!!)).error.shouldBeNull()
            }
            OptionalCastAffordability.canPayPendingMayCast(game.state, game.player1Id, cardRegistry) shouldBe false
            // The preserved oracle and adapter both decline this exact real state. The original
            // outside-the-oracle `true` assertion failed; answer and ordered view equivalence stay required.
            assertEquivalent(game).second.choice shouldBe false
        }
    }
}
