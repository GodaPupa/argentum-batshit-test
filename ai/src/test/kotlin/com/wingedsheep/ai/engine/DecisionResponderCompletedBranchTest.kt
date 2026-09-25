package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionSubmittedEvent
import com.wingedsheep.engine.core.PriorityChangedEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.IfYouDoEffect
import com.wingedsheep.sdk.scripting.effects.LoseLifeEffect
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Shared decision-policy controls for exact-one auto-completion and genuine up-to-one choices. */
class DecisionResponderCompletedBranchTest : ScenarioTestBase() {

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

    private fun ai(game: TestGame) =
        AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun life(game: TestGame): Int =
        game.state.getEntity(game.player1Id)!!.get<LifeTotalComponent>()!!.life

    private fun resolveOptionalEtb(game: TestGame, cardName: String) {
        game.castSpell(1, cardName).error.shouldBeNull()
        game.resolveStack()
        game.state.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.YesNoDecision>()
    }

    private fun acceptAndRequireUpToOne(game: TestGame, agent: AIPlayer): SelectCardsDecision {
        val accept = agent.respondToDecision(game.state, game.state.pendingDecision!!)
            .shouldBeInstanceOf<YesNoResponse>()
        accept.choice.shouldBeTrue()
        game.answerYesNo(true).error.shouldBeNull()
        return game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>().also {
            withClue("the synthetic continuation must genuinely expose min=0, max=1") {
                it.minSelections shouldBe 0
                it.maxSelections shouldBe 1
            }
        }
    }

    init {
        cardRegistry.register(
            listOf(
                exactOneBeneficialSingleton,
                exactOneBeneficialEmpty,
                upToOneBeneficialSingleton,
                upToOneBeneficialEmpty,
                upToOneDecline,
                strongCandidate,
                weakCandidate,
                upToOneMultipleCandidates,
            )
        )

        test("exact one singleton auto-completes without a follow-up decision") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialSingleton.name)
                .withCardInHand(1, "Mountain")
                .build()
            val candidate = game.findCardsInHand(1, "Mountain").single()
            resolveOptionalEtb(game, exactOneBeneficialSingleton.name)

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeTrue()
            game.answerYesNo(true).error.shouldBeNull()

            game.state.pendingDecision.shouldBeNull()
            game.state.getGraveyard(game.player1Id).contains(candidate).shouldBeTrue()
            life(game) shouldBe 15
        }

        test("exact one empty result can produce a superior if-you-dont branch without a follow-up") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialEmpty.name)
                .build()
            resolveOptionalEtb(game, exactOneBeneficialEmpty.name)

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeTrue()
            game.answerYesNo(true).error.shouldBeNull()

            game.state.pendingDecision.shouldBeNull()
            life(game) shouldBe 15
        }

        test("exact one no-candidate no-payoff acceptance is outcome-equivalent to declining") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, exactOneBeneficialSingleton.name)
                .build()
            resolveOptionalEtb(game, exactOneBeneficialSingleton.name)
            val decision = game.state.pendingDecision!!
            val simulator = GameSimulator(cardRegistry)
            val accept = simulator.simulateDecision(game.state, YesNoResponse(decision.id, true))
            val decline = simulator.simulateDecision(game.state, YesNoResponse(decision.id, false))

            accept.shouldBeInstanceOf<SimulationResult.Terminal>()
            decline.shouldBeInstanceOf<SimulationResult.Terminal>()
            val acceptDecisionEvents = accept.events.filterIsInstance<DecisionSubmittedEvent>()
                .filter { it.decisionId == decision.id }
            val declineDecisionEvents = decline.events.filterIsInstance<DecisionSubmittedEvent>()
                .filter { it.decisionId == decision.id }
            withClue("the authentic audit traces must retain the submitted outer responses") {
                acceptDecisionEvents.single().description shouldBe
                    "(Exact One Beneficial Singleton) Chose Yes"
                declineDecisionEvents.single().description shouldBe
                    "(Exact One Beneficial Singleton) Chose No"
            }

            val acceptGameplayEvents = accept.events.filterNot {
                it is DecisionSubmittedEvent && it.decisionId == decision.id
            }
            val declineGameplayEvents = decline.events.filterNot {
                it is DecisionSubmittedEvent && it.decisionId == decision.id
            }
            withClue("audit-distinct traces must be gameplay-outcome-equivalent") {
                acceptGameplayEvents shouldBe declineGameplayEvents
                acceptGameplayEvents shouldBe listOf(PriorityChangedEvent(game.state.activePlayerId!!))
            }
            accept.state.priorityPlayerId shouldBe game.state.activePlayerId
            accept.state.stackResolutionPendingPriority shouldBe false
            accept.state shouldBe decline.state
            accept.state.getHand(game.player1Id) shouldBe decline.state.getHand(game.player1Id)
            accept.state.getLibrary(game.player1Id) shouldBe decline.state.getLibrary(game.player1Id)
            accept.state.getGraveyard(game.player1Id) shouldBe decline.state.getGraveyard(game.player1Id)
            accept.state.getExile(game.player1Id) shouldBe decline.state.getExile(game.player1Id)
            accept.state.getBattlefield() shouldBe decline.state.getBattlefield()
            accept.state.pendingDecision.shouldBeNull()
            decline.state.pendingDecision.shouldBeNull()
            withClue("legacy tie behavior currently returns accept but has no gameplay outcome") {
                ai(game).respondToDecision(game.state, decision)
                    .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeTrue()
            }
        }

        test("genuine up-to-one branch chooses a beneficial legal singleton") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, upToOneBeneficialSingleton.name)
                .withCardInHand(1, "Mountain")
                .build()
            val candidate = game.findCardsInHand(1, "Mountain").single()
            resolveOptionalEtb(game, upToOneBeneficialSingleton.name)

            val response = ai(game).let { agent ->
                val selection = acceptAndRequireUpToOne(game, agent)
                agent.respondToDecision(game.state, selection).shouldBeInstanceOf<CardsSelectedResponse>()
            }
            response.selectedCards shouldBe listOf(candidate)
        }

        test("genuine up-to-one branch preserves a superior beneficial empty selection") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, upToOneBeneficialEmpty.name)
                .withCardInHand(1, "Mountain")
                .build()
            resolveOptionalEtb(game, upToOneBeneficialEmpty.name)

            val response = ai(game).let { agent ->
                val selection = acceptAndRequireUpToOne(game, agent)
                agent.respondToDecision(game.state, selection).shouldBeInstanceOf<CardsSelectedResponse>()
            }
            response.selectedCards.shouldBeEmpty()
        }

        test("genuine up-to-one branch declines when harmful singleton and empty equals decline") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, upToOneDecline.name)
                .withCardInHand(1, "Mountain")
                .build()
            resolveOptionalEtb(game, upToOneDecline.name)

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeFalse()
        }

        test("genuine up-to-one branch preserves the superior singleton among multiple candidates") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, upToOneMultipleCandidates.name)
                .withCardInHand(1, strongCandidate.name)
                .withCardInHand(1, weakCandidate.name)
                .build()
            val strong = game.findCardsInHand(1, strongCandidate.name).single()
            resolveOptionalEtb(game, upToOneMultipleCandidates.name)

            val response = ai(game).let { agent ->
                val selection = acceptAndRequireUpToOne(game, agent)
                selection.options.size shouldBe 2
                agent.respondToDecision(game.state, selection).shouldBeInstanceOf<CardsSelectedResponse>()
            }
            response.selectedCards shouldBe listOf(strong)
            cardName(game, response.selectedCards.single()) shouldBe strongCandidate.name
        }
    }
}
