package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiDecisionInsight
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.DecisionSubmittedEvent
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.HexproofFromComponent
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seedless Gate 2 policy fixtures for Pest Control v1.0 versus the approved SoterX Mono Red
 * Madness maindeck. These are isolated positions, not games or experimental sample evidence.
 */
class PestControlMonoRedMadnessDecisionTest : ScenarioTestBase() {

    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING

    private fun seeded() = scenario().withPlayers("Mono Red", "Pest Control").withRngSeed(0x0E57_600DL)

    private fun ai(game: TestGame, player: EntityId = game.player1Id) =
        AIPlayer.create(cardRegistry, player, profile)

    private fun aiWithInsights(game: TestGame, captured: MutableList<AiDecisionInsight>) =
        AIPlayer.create(
            cardRegistry,
            game.player1Id,
            profile,
            insightSink = { _, insight -> captured += insight },
        )

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun chosenTargetId(action: CastSpell): EntityId? = when (val target = action.targets.singleOrNull()) {
        is ChosenTarget.Permanent -> target.entityId
        is ChosenTarget.Player -> target.playerId
        is ChosenTarget.Card -> target.cardId
        is ChosenTarget.Spell -> target.spellEntityId
        null -> null
    }

    private fun TestGame.advanceToDecision() {
        while (state.pendingDecision == null && state.stack.isNotEmpty()) {
            execute(PassPriority(state.priorityPlayerId!!)).error.shouldBeNull()
        }
    }

    private fun TestGame.addFloatingMana(player: EntityId, color: Color, amount: Int = 1) {
        state = state.updateEntity(player) { container ->
            val pool = container.get<ManaPoolComponent>() ?: ManaPoolComponent()
            container.with(pool.add(color, amount))
        }
    }

    init {
        test("Highway Robbery chooses Done when every destructive mode has zero payoff") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInHand(1, "Highway Robbery")
                .withCardInHand(1, "Lightning Bolt")
                .build()

            game.castSpell(1, "Highway Robbery").error.shouldBeNull()
            game.resolveStack()

            val decision = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<OptionChosenResponse>()

            withClue(
                "discarding Lightning Bolt or sacrificing a Mountain draws zero cards and has " +
                    "no payoff, so Highway Robbery must select the legal Done branch",
            ) {
                decision.options[response.optionIndex] shouldBe "Done"
            }
            game.execute(
                com.wingedsheep.engine.core.SubmitDecision(game.player1Id, response),
            ).error.shouldBeNull()
            game.resolveStack()
            withClue("the restraint branch must preserve Bolt and every Mountain without drawing") {
                game.findCardsInHand(1, "Lightning Bolt").size shouldBe 1
                game.findPermanents("Mountain").size shouldBe 3
                game.state.getLibrary(game.player1Id).size shouldBe 0
            }
        }

        test("Highway Robbery chooses a destructive mode when completed available draws pay for it") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInHand(1, "Highway Robbery")
                .withCardInHand(1, "Mountain")
                .withCardInLibrary(1, "Lightning Bolt")
                .withCardInLibrary(1, "Guttersnipe")
                .build()

            game.castSpell(1, "Highway Robbery").error.shouldBeNull()
            game.resolveStack()

            val decision = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<OptionChosenResponse>()
            withClue("two available draws must preserve a useful completed destructive branch") {
                decision.options[response.optionIndex] shouldBe "Discard a card, then draw two cards"
            }
        }

        test("Highway Robbery can choose beneficial land sacrifice when no discard exists") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInHand(1, "Highway Robbery")
                .withCardInLibrary(1, "Lightning Bolt")
                .withCardInLibrary(1, "Guttersnipe")
                .build()

            game.castSpell(1, "Highway Robbery").error.shouldBeNull()
            game.resolveStack()

            val decision = game.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<OptionChosenResponse>()
            withClue("a payable land sacrifice for two available draws must remain selectable") {
                decision.options[response.optionIndex] shouldBe "Sacrifice a land, then draw two cards"
            }
        }

        test("Faithless Looting is held when zero draws would only discard retained value") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(1, "Faithless Looting")
                .withCardInHand(1, "Lightning Bolt")
                .withLifeTotal(2, 20)
                .build()

            val action = ai(game).chooseAction(game.state)

            withClue(
                "with an empty library, Looting cannot replace the retained Bolt and must not " +
                    "turn its mandatory discard into pure resource loss",
            ) {
                (action is CastSpell && cardName(game, action.cardId) == "Faithless Looting")
                    .shouldBeFalse()
            }
        }

        test("Grab the Prize is held when its mandatory discard and zero draws lose material") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Lightning Bolt")
                .withLifeTotal(2, 20)
                .build()

            val action = ai(game).chooseAction(game.state)

            withClue(
                "two incidental damage does not justify discarding Bolt and drawing zero cards",
            ) {
                (action is CastSpell && cardName(game, action.cardId) == "Grab the Prize")
                    .shouldBeFalse()
            }
        }

        test("accepts a profitable Fiery Temper madness cast") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val outlet = game.findCardsInHand(1, "Grab the Prize").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()

            game.execute(
                CastSpell(
                    game.player1Id,
                    outlet,
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).isSuccess.shouldBeTrue()
            game.advanceToDecision()

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice shouldBe true
        }

        test("declines Fiery Temper madness when the outlet consumed all payable red mana") {
            val game = seeded()
                .withLifeTotal(2, 20)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val outlet = game.findCardsInHand(1, "Grab the Prize").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()

            game.execute(
                CastSpell(
                    game.player1Id,
                    outlet,
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).isSuccess.shouldBeTrue()
            game.advanceToDecision()

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice shouldBe false
        }

        test("accepts Fiery Temper madness with sufficient floating red mana") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val outlet = game.findCardsInHand(1, "Grab the Prize").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()

            game.execute(
                CastSpell(
                    game.player1Id,
                    outlet,
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).isSuccess.shouldBeTrue()
            game.advanceToDecision()
            game.addFloatingMana(game.player1Id, Color.RED)

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice shouldBe true
        }

        test("declines Fiery Temper madness when the remaining Mountain is tapped") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val tappedMountain = game.findPermanents("Mountain").first()
            game.state = game.state.updateEntity(tappedMountain) { it.with(TappedComponent) }
            val outlet = game.findCardsInHand(1, "Grab the Prize").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()

            game.execute(
                CastSpell(
                    game.player1Id,
                    outlet,
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).isSuccess.shouldBeTrue()
            game.advanceToDecision()

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice shouldBe false
        }

        test("declines Fiery Temper madness when only wrong-color mana remains") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            val outlet = game.findCardsInHand(1, "Grab the Prize").single()
            val temper = game.findCardsInHand(1, "Fiery Temper").single()

            game.execute(
                CastSpell(
                    game.player1Id,
                    outlet,
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(temper)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).isSuccess.shouldBeTrue()
            game.advanceToDecision()
            game.addFloatingMana(game.player1Id, Color.GREEN)

            ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice shouldBe false
        }

        test("discards Sneaky Snacker when Grab the Prize supplies the third draw") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInHand(1, "Guttersnipe")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(CardsDrawnThisTurnComponent(1))
            }

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Grab the Prize"
            val discarded = action.additionalCostPayment?.discardedCards?.single()!!
            cardName(game, discarded) shouldBe "Sneaky Snacker"
        }

        test("Melded Moxite accepts the deterministic Sneaky Snacker third-draw payoff") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Melded Moxite")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Lightning Bolt")
                .withCardInLibrary(1, "Lava Dart")
                .build()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(CardsDrawnThisTurnComponent(1))
            }
            game.castSpell(1, "Melded Moxite").error.shouldBeNull()
            if (game.state.pendingDecision is SelectManaSourcesDecision) {
                game.submitManaSourcesAutoPay().error.shouldBeNull()
            }
            game.resolveStack()

            val agent = ai(game)
            val accept = agent.respondToDecision(
                game.state,
                game.state.pendingDecision!!,
            ).shouldBeInstanceOf<YesNoResponse>()
            withClue(
                "Moxite positive payoff expected ACCEPT but observed choice=${accept.choice}; " +
                    "hand=${game.state.getHand(game.player1Id).mapNotNull { cardName(game, it) }}; " +
                    "librarySize=${game.state.getLibrary(game.player1Id).size}; " +
                    "cardsDrawnThisTurn=1; available untapped Mountains=0 after paying {1}{R}",
            ) {
                accept.choice.shouldBeTrue()
            }

            val accepted = game.answerYesNo(true)
            accepted.error.shouldBeNull()
            val resolutionEvents = accepted.events + game.resolveStack().flatMap { it.events }
            withClue(
                "ChooseExactly(1) must auto-select the sole Sneaky Snacker without exposing " +
                    "a follow-up selection decision",
            ) {
                game.state.pendingDecision.shouldBeNull()
                resolutionEvents.filterIsInstance<CardsDiscardedEvent>()
                    .flatMap { it.cardNames } shouldBe listOf("Sneaky Snacker")
            }
            withClue("Moxite must draw exactly two cards and reach the third draw this turn") {
                resolutionEvents.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
                game.state.getEntity(game.player1Id)!!
                    .get<CardsDrawnThisTurnComponent>()!!.count shouldBe 3
            }
            withClue("the deterministic third-draw trigger must return Sneaky Snacker") {
                game.isOnBattlefield("Sneaky Snacker").shouldBeTrue()
            }
        }

        test("Melded Moxite declines discarding Lightning Bolt for zero available draws") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Melded Moxite")
                .withCardInHand(1, "Lightning Bolt")
                .build()
            game.castSpell(1, "Melded Moxite").error.shouldBeNull()
            if (game.state.pendingDecision is SelectManaSourcesDecision) {
                game.submitManaSourcesAutoPay().error.shouldBeNull()
            }
            game.resolveStack()

            val legalDiscardOptions = game.state.getHand(game.player1Id).mapNotNull { cardName(game, it) }
            val decline = ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>()
            withClue(
                "Moxite restraint expected DECLINE but observed choice=${decline.choice}; " +
                    "legal discard options=$legalDiscardOptions; librarySize=0; " +
                    "available draws=0; available untapped Mountains=0 after paying {1}{R}",
            ) {
                decline.choice.shouldBeFalse()
            }
        }

        test("Melded Moxite completed branch selects the deterministic payoff among legal discards") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Melded Moxite")
                .withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Lava Dart")
                .withCardInLibrary(1, "Guttersnipe")
                .build()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(CardsDrawnThisTurnComponent(1))
            }
            game.castSpell(1, "Melded Moxite").error.shouldBeNull()
            if (game.state.pendingDecision is SelectManaSourcesDecision) {
                game.submitManaSourcesAutoPay().error.shouldBeNull()
            }
            game.resolveStack()

            val agent = ai(game)
            agent.respondToDecision(game.state, game.state.pendingDecision!!)
                .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeTrue()
            game.answerYesNo(true).error.shouldBeNull()
            val decision = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val selected = agent.respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.single()

            withClue("the follow-up selection must be the branch whose third draw returns Snacker") {
                cardName(game, selected) shouldBe "Sneaky Snacker"
            }
        }

        test("Melded Moxite does not credit a Snacker return when too few cards can be drawn") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Melded Moxite")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInLibrary(1, "Mountain")
                .build()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(CardsDrawnThisTurnComponent(1))
            }
            game.castSpell(1, "Melded Moxite").error.shouldBeNull()
            if (game.state.pendingDecision is SelectManaSourcesDecision) {
                game.submitManaSourcesAutoPay().error.shouldBeNull()
            }
            game.resolveStack()

            withClue("one available draw reaches only draw two and supplies no material discard payoff") {
                ai(game).respondToDecision(game.state, game.state.pendingDecision!!)
                    .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeFalse()
            }
        }

        test("Melded Moxite no-candidate tie is gameplay-equivalent under legacy acceptance") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Melded Moxite")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Lava Dart")
                .build()
            game.castSpell(1, "Melded Moxite").error.shouldBeNull()
            if (game.state.pendingDecision is SelectManaSourcesDecision) {
                game.submitManaSourcesAutoPay().error.shouldBeNull()
            }
            game.resolveStack()

            val decision = game.state.pendingDecision!!
            val simulator = GameSimulator(cardRegistry)
            val accept = simulator.simulateDecision(game.state, YesNoResponse(decision.id, true))
            val decline = simulator.simulateDecision(game.state, YesNoResponse(decision.id, false))

            withClue("auto-completed no-candidate acceptance must be outcome-equivalent to decline") {
                accept::class shouldBe decline::class
                accept.state shouldBe decline.state
                val acceptAudit = accept.events.filterIsInstance<DecisionSubmittedEvent>()
                    .filter { it.decisionId == decision.id }
                val declineAudit = decline.events.filterIsInstance<DecisionSubmittedEvent>()
                    .filter { it.decisionId == decision.id }
                acceptAudit.single().description shouldBe "(Melded Moxite) Chose Yes"
                declineAudit.single().description shouldBe "(Melded Moxite) Chose No"
                accept.events.filterNot {
                    it is DecisionSubmittedEvent && it.decisionId == decision.id
                } shouldBe decline.events.filterNot {
                    it is DecisionSubmittedEvent && it.decisionId == decision.id
                }
            }
            withClue("the outcome-neutral legacy score tie currently resolves to accept") {
                ai(game).respondToDecision(game.state, decision)
                    .shouldBeInstanceOf<YesNoResponse>().choice.shouldBeTrue()
            }
        }

        test("Melded Moxite completed-branch policy is invariant to hidden library order") {
            fun decide(library: List<String>): Pair<Boolean, String?> {
                val setup = seeded()
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInHand(1, "Melded Moxite")
                    .withCardInHand(1, "Lightning Bolt")
                    .withCardInHand(1, "Sneaky Snacker")
                library.forEach { setup.withCardInLibrary(1, it) }
                val game = setup.build()
                game.state = game.state.updateEntity(game.player1Id) {
                    it.with(CardsDrawnThisTurnComponent(1))
                }
                game.castSpell(1, "Melded Moxite").error.shouldBeNull()
                if (game.state.pendingDecision is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().error.shouldBeNull()
                }
                game.resolveStack()

                val agent = ai(game)
                val accept = agent.respondToDecision(game.state, game.state.pendingDecision!!)
                    .shouldBeInstanceOf<YesNoResponse>().choice
                if (!accept) return false to null
                game.answerYesNo(true).error.shouldBeNull()
                val discard = agent.respondToDecision(game.state, game.state.pendingDecision!!)
                    .shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.single()
                return true to cardName(game, discard)
            }

            val first = decide(listOf("Mountain", "Lava Dart", "Guttersnipe"))
            val permuted = decide(listOf("Guttersnipe", "Mountain", "Lava Dart"))

            first shouldBe (true to "Sneaky Snacker")
            permuted shouldBe first
        }

        test("Melded Moxite converts an otherwise idle artifact but yields to stronger development") {
            val productive = seeded()
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardOnBattlefield(1, "Melded Moxite")
                .build()
            val activation = ai(productive).chooseAction(productive.state)
                .shouldBeInstanceOf<ActivateAbility>()
            cardName(productive, activation.sourceId) shouldBe "Melded Moxite"

            val restrained = seeded()
                .withLandsOnBattlefield(1, "Mountain", 3)
                .withCardOnBattlefield(1, "Melded Moxite")
                .withCardInHand(1, "Guttersnipe")
                .build()
            val stronger = ai(restrained).chooseAction(restrained.state).shouldBeInstanceOf<CastSpell>()
            cardName(restrained, stronger.cardId) shouldBe "Guttersnipe"
        }

        test("deploys each spell-damage engine before a profitable follow-up spell") {
            data class SetupCase(
                val engine: String,
                val followUp: String,
                val mountains: Int,
                val opponentLife: Int,
            )

            val setupCases = listOf(
                SetupCase("Guttersnipe", "Lightning Bolt", mountains = 4, opponentLife = 4),
                SetupCase("Kessig Flamebreather", "Lightning Bolt", mountains = 3, opponentLife = 4),
                SetupCase("Guttersnipe", "Faithless Looting", mountains = 4, opponentLife = 2),
                SetupCase("Kessig Flamebreather", "Faithless Looting", mountains = 3, opponentLife = 1),
                SetupCase("Guttersnipe", "Grab the Prize", mountains = 5, opponentLife = 3),
                SetupCase("Kessig Flamebreather", "Grab the Prize", mountains = 4, opponentLife = 3),
                SetupCase("Guttersnipe", "Highway Robbery", mountains = 5, opponentLife = 2),
                SetupCase("Kessig Flamebreather", "Highway Robbery", mountains = 4, opponentLife = 1),
                // Melded Moxite is a noncreature artifact: it triggers Flamebreather, not Guttersnipe.
                SetupCase("Kessig Flamebreather", "Melded Moxite", mountains = 4, opponentLife = 1),
            )
            setupCases.forEach { case ->
                val game = seeded()
                    .withLandsOnBattlefield(1, "Mountain", case.mountains)
                    .withCardInHand(1, case.engine)
                    .withCardInHand(1, case.followUp)
                    .withCardInHand(1, "Sneaky Snacker")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withLifeTotal(2, case.opponentLife)
                    .build()

                val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
                withClue(
                    "${case.engine} before ${case.followUp} makes the nonlethal-alone follow-up lethal",
                ) {
                    cardName(game, action.cardId) shouldBe case.engine
                }
            }
        }

        test("takes an immediate Lightning Bolt win instead of unnecessary engine setup") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Lightning Bolt")
                .withLifeTotal(2, 2)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Lightning Bolt"
            chosenTargetId(action) shouldBe game.player2Id
        }

        test("executes the bounded Guttersnipe Bolt sequence through terminal resolution") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Lightning Bolt")
                .withLifeTotal(2, 4)
                .build()
            val agent = ai(game)

            val setup = agent.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, setup.cardId) shouldBe "Guttersnipe"
            game.execute(setup).error.shouldBeNull()
            game.resolveStack()

            val payoff = agent.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, payoff.cardId) shouldBe "Lightning Bolt"
            chosenTargetId(payoff) shouldBe game.player2Id
            game.execute(payoff).error.shouldBeNull()
            game.resolveStack()

            game.state.gameOver.shouldBeTrue()
            game.state.winnerId shouldBe game.player1Id
            withClue("Guttersnipe's two and Bolt's three are each applied exactly once") {
                game.state.lifeTotal(game.player2Id) shouldBe -1
            }
        }

        test("does not award bounded lethal priority to unavailable or nonlethal continuations") {
            data class RestraintCase(
                val label: String,
                val setup: String,
                val payoff: String,
                val mountains: Int,
                val opponentLife: Int,
            )

            listOf(
                RestraintCase("insufficient combined mana", "Guttersnipe", "Lightning Bolt", 3, 4),
                RestraintCase("life above combined damage", "Guttersnipe", "Lightning Bolt", 4, 6),
                RestraintCase("Guttersnipe does not trigger from an artifact", "Guttersnipe", "Melded Moxite", 5, 1),
            ).forEach { case ->
                val game = seeded()
                    .withLandsOnBattlefield(1, "Mountain", case.mountains)
                    .withCardInHand(1, case.setup)
                    .withCardInHand(1, case.payoff)
                    .withLifeTotal(2, case.opponentLife)
                    .build()
                val captured = mutableListOf<AiDecisionInsight>()

                aiWithInsights(game, captured).chooseAction(game.state)

                withClue(case.label) {
                    captured.last().options.none {
                        it.note == "lethal policy: legal bounded same-turn continuation wins the game"
                    }.shouldBeTrue()
                }
            }
        }

        test("burn prioritizes each active Pest engine over a strategically irrelevant body") {
            listOf("Essence Warden", "Blood Researcher", "Pest Mascot").forEach { payoff ->
                val game = seeded()
                    .withActivePlayer(2)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInHand(1, "Lightning Bolt")
                    .withCardOnBattlefield(2, payoff)
                    .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                    .withLandsOnBattlefield(2, if (payoff == "Essence Warden") "Swamp" else "Forest", 2)
                    .withCardInHand(2, if (payoff == "Essence Warden") "Carrier Thrall" else "Weather the Storm")
                    .withLifeTotal(2, 20)
                    .build()

                val pendingValue = if (payoff == "Essence Warden") "Carrier Thrall" else "Weather the Storm"
                game.castSpell(2, pendingValue).isSuccess.shouldBeTrue()
                game.execute(PassPriority(game.player2Id)).error.shouldBeNull()
                game.state.priorityPlayerId shouldBe game.player1Id

                val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
                withClue(payoff) { cardName(game, chosenTargetId(action)!!) shouldBe payoff }
            }
        }

        test("visible burn decision is invariant to opponent hidden hand and library permutations") {
            fun chooseTarget(
                opponentHand: String,
                opponentLibrary: List<String>,
            ): Pair<String?, String?> {
                val setup = seeded()
                    .withActivePlayer(2)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInHand(1, "Lightning Bolt")
                    .withCardOnBattlefield(2, "Essence Warden")
                    .withCardOnBattlefield(2, "Mons's Goblin Raiders")
                    .withLandsOnBattlefield(2, "Swamp", 2)
                    .withCardInHand(2, "Carrier Thrall")
                    .withCardInHand(2, opponentHand)
                    .withLifeTotal(2, 20)
                opponentLibrary.forEach { setup.withCardInLibrary(2, it) }
                val game = setup.build()
                game.castSpell(2, "Carrier Thrall").isSuccess.shouldBeTrue()
                game.execute(PassPriority(game.player2Id)).error.shouldBeNull()
                game.state.priorityPlayerId shouldBe game.player1Id

                val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
                return cardName(game, action.cardId) to cardName(game, chosenTargetId(action)!!)
            }

            val weatherHidden = chooseTarget(
                opponentHand = "Weather the Storm",
                opponentLibrary = listOf("Forest", "Carrier Thrall"),
            )
            val weatherInLibrary = chooseTarget(
                opponentHand = "Forest",
                opponentLibrary = listOf("Carrier Thrall", "Weather the Storm"),
            )
            val permutedLibrary = chooseTarget(
                opponentHand = "Forest",
                opponentLibrary = listOf("Weather the Storm", "Carrier Thrall"),
            )

            withClue(
                "identical visible states and known deck composition must produce the same semantic " +
                    "action regardless of the opponent's hidden hand identity or library order",
            ) {
                weatherHidden shouldBe ("Lightning Bolt" to "Essence Warden")
                weatherInLibrary shouldBe weatherHidden
                permutedLibrary shouldBe weatherHidden
            }
        }

        test("Pest removal answers a visibly lethal attacking Sneaky Snacker") {
            val combat = seeded()
                .withActivePlayer(1)
                .withPriorityPlayer(2)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .withLifeTotal(2, 2)
                .withLandsOnBattlefield(2, "Swamp", 2)
                .withCardInHand(2, "Cast Down")
                .withCardOnBattlefield(1, "Sneaky Snacker", summoningSickness = false)
                .build()
            val snacker = combat.findPermanent("Sneaky Snacker")!!
            combat.state = combat.state.updateEntity(combat.player1Id) {
                it.with(AttackersDeclaredThisCombatComponent)
            }.updateEntity(snacker) {
                it.with(AttackingComponent(combat.player2Id))
            }
            val survivalRemoval = ai(combat, combat.player2Id).chooseAction(combat.state)
                .shouldBeInstanceOf<CastSpell>()
            withClue("the unblocked two-power Snacker is deterministic lethal") {
                cardName(combat, survivalRemoval.cardId) shouldBe "Cast Down"
                chosenTargetId(survivalRemoval) shouldBe snacker
            }
        }

        test("holds Fireblast and Lava Dart sacrifice costs until their value is decisive") {
            val fireblast = seeded()
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInHand(1, "Fireblast")
                .withLifeTotal(2, 20)
                .build()
            val fireblastAction = ai(fireblast).chooseAction(fireblast.state)
            (fireblastAction is CastSpell && cardName(fireblast, fireblastAction.cardId) == "Fireblast")
                .shouldBeFalse()

            val dart = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInGraveyard(1, "Lava Dart")
                .withLifeTotal(2, 20)
                .build()
            val dartAction = ai(dart).chooseAction(dart.state)
            (dartAction is CastSpell && cardName(dart, dartAction.cardId) == "Lava Dart")
                .shouldBeFalse()

            val lethal = seeded()
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withLifeTotal(2, 1)
                .build()
            val lethalAction = ai(lethal).chooseAction(lethal.state).shouldBeInstanceOf<CastSpell>()
            cardName(lethal, lethalAction.cardId) shouldBe "Lava Dart"
            chosenTargetId(lethalAction) shouldBe lethal.player2Id
        }

        test("an already-winning pass baseline does not justify sacrificing a Mountain") {
            val game = seeded()
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Lightning Bolt")
                .withCardInGraveyard(1, "Lava Dart")
                .build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("Lava Dart does not target its own valuable creature without a superior payoff") {
            val game = seeded()
                .withLifeTotal(2, 20)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withCardOnBattlefield(1, "Kessig Flamebreather")
                .build()

            val action = ai(game).chooseAction(game.state)
            (action is CastSpell && cardName(game, action.cardId) == "Lava Dart").shouldBeFalse()
        }

        test("Lava Dart does not spend a Mountain for one damage on an undamaged five-five") {
            val game = seeded()
                .withLifeTotal(2, 20)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withCardOnBattlefield(2, "Colossapede")
                .build()

            val action = ai(game).chooseAction(game.state)
            (action is CastSpell && cardName(game, action.cardId) == "Lava Dart").shouldBeFalse()
        }

        test("Lava Dart spends its Mountain when one damage finishes a damaged creature") {
            val game = seeded()
                .withLifeTotal(2, 20)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withCardOnBattlefield(2, "Colossapede")
                .build()
            val target = game.findPermanent("Colossapede")!!
            game.state = game.state.updateEntity(target) { it.with(DamageComponent(4)) }

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Lava Dart"
            chosenTargetId(action) shouldBe target
            action.additionalCostPayment?.sacrificedPermanents?.size shouldBe 1
        }

        test("flashes back Lava Dart when removing an attacker is required for survival") {
            val game = seeded()
                .withActivePlayer(2)
                .withPriorityPlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .withLifeTotal(1, 1)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withCardOnBattlefield(2, "Essence Warden", summoningSickness = false)
                .build()
            val warden = game.findPermanent("Essence Warden")!!
            game.state = game.state.updateEntity(game.player2Id) {
                it.with(AttackersDeclaredThisCombatComponent)
            }.updateEntity(warden) {
                it.with(AttackingComponent(game.player1Id))
            }

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            withClue(
                "passing leaves an unblocked one-power attacker lethal, so sacrificing the sole " +
                    "Mountain is concretely productive survival removal",
            ) {
                cardName(game, action.cardId) shouldBe "Lava Dart"
                chosenTargetId(action) shouldBe warden
                action.additionalCostPayment?.sacrificedPermanents?.size shouldBe 1
            }
        }

        test("Mono Red receives and uses the lethal response window over Weather the Storm") {
            val game = seeded()
                .withActivePlayer(2)
                .withLifeTotal(2, 3)
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(2, "Weather the Storm")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInHand(1, "Lightning Bolt")
                .build()

            game.castSpell(2, "Weather the Storm").isSuccess.shouldBeTrue()
            game.execute(PassPriority(game.player2Id)).isSuccess.shouldBeTrue()
            game.state.priorityPlayerId shouldBe game.player1Id

            val response = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, response.cardId) shouldBe "Lightning Bolt"
            chosenTargetId(response) shouldBe game.player2Id
        }

        test("Mono Red waits through safe windows then uses Fireblast at the final survival boundary") {
            val game = seeded()
                .withActivePlayer(2)
                .withLifeTotal(1, 2)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Fireblast")
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardOnBattlefield(2, "Pest Mascot", summoningSickness = false)
                .withCardInHand(2, "Weather the Storm")
                .build()
            val mascot = game.findPermanent("Pest Mascot")!!
            val redAi = ai(game)

            game.state.phase shouldBe Phase.PRECOMBAT_MAIN
            game.state.step shouldBe Step.PRECOMBAT_MAIN
            game.state.activePlayerId shouldBe game.player2Id
            game.state.lifeTotal(game.player1Id) shouldBe 2
            game.state.lifeTotal(game.player2Id) shouldBe 20
            game.state.stack shouldBe emptyList()
            game.state.controlledBattlefield(game.player1Id).count { id ->
                game.state.projectedState.isCreature(id)
            } shouldBe 0
            game.state.controlledBattlefield(game.player2Id).filter { id ->
                game.state.projectedState.isCreature(id)
            } shouldBe listOf(mascot)
            game.findPermanents("Mountain").size shouldBe 2
            game.findPermanents("Mountain").all { id ->
                game.state.getEntity(id)?.has<TappedComponent>() != true
            }.shouldBeTrue()

            game.castSpell(2, "Weather the Storm").isSuccess.shouldBeTrue()
            game.execute(PassPriority(game.player2Id)).error.shouldBeNull()
            game.state.priorityPlayerId shouldBe game.player1Id
            game.state.stack.mapNotNull { id -> cardName(game, id) } shouldBe listOf("Weather the Storm")

            val fireblastModes = GameSimulator(cardRegistry).getLegalActions(game.state, game.player1Id)
                .filter { legal ->
                    val cast = legal.action as? CastSpell
                    cast != null && cardName(game, cast.cardId) == "Fireblast"
                }
            fireblastModes.size shouldBe 1
            fireblastModes.single().affordable.shouldBeTrue()
            val advertisedFireblast = fireblastModes.single().action.shouldBeInstanceOf<CastSpell>()
            advertisedFireblast.useAlternativeCost.shouldBeTrue()
            advertisedFireblast.alternativeCostType shouldBe com.wingedsheep.engine.core.AlternativeCostType.SELF_ALTERNATIVE
            fireblastModes.single().additionalCostInfo?.sacrificeCount shouldBe 2

            // Weather is not the final window. Consult production at every later Mono Red priority
            // stop, and preserve each pass until Pest has actually committed Mascot as an attacker.
            var windows = 0
            while (!(game.state.step == Step.DECLARE_ATTACKERS &&
                    game.state.priorityPlayerId == game.player1Id &&
                    game.state.getEntity(mascot)?.has<AttackingComponent>() == true)
            ) {
                check(windows++ < 40) {
                    "Never reached the final post-attack priority window: ${game.state.phase}/${game.state.step}"
                }
                val priority = checkNotNull(game.state.priorityPlayerId)
                val action = when {
                    game.state.step == Step.DECLARE_ATTACKERS &&
                        priority == game.player2Id &&
                        game.state.getEntity(mascot)?.has<AttackingComponent>() != true ->
                        DeclareAttackers(game.player2Id, mapOf(mascot to game.player1Id))
                    priority == game.player1Id ->
                        redAi.chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
                    else -> PassPriority(priority)
                }
                game.execute(action).error.shouldBeNull()
            }

            val mustActState = game.state
            mustActState.phase shouldBe Phase.COMBAT
            mustActState.step shouldBe Step.DECLARE_ATTACKERS
            mustActState.activePlayerId shouldBe game.player2Id
            mustActState.priorityPlayerId shouldBe game.player1Id
            mustActState.getEntity(mascot)?.get<AttackingComponent>()?.defenderId shouldBe game.player1Id

            // Declining at this exact boundary is lethal through the authoritative combat flow.
            game.execute(PassPriority(game.player1Id)).error.shouldBeNull()
            var combatTransitions = 0
            var blockerDeclarations = 0
            while (!game.state.gameOver && game.state.phase == Phase.COMBAT) {
                check(combatTransitions++ < 40) { "Combat did not settle after the final pass" }
                val priority = checkNotNull(game.state.priorityPlayerId)
                val blockersDeclared = game.state.getEntity(game.player1Id)
                    ?.has<BlockersDeclaredThisCombatComponent>() == true
                val action = if (
                    game.state.step == Step.DECLARE_BLOCKERS &&
                    priority == game.player1Id &&
                    !blockersDeclared
                ) {
                    blockerDeclarations++
                    DeclareBlockers(game.player1Id, emptyMap())
                } else {
                    PassPriority(priority)
                }
                game.execute(action).error.shouldBeNull()
            }
            blockerDeclarations shouldBe 1
            game.state.gameOver.shouldBeTrue()
            game.state.winnerId shouldBe game.player2Id

            // Restore the immutable must-act snapshot, take the survival action, and follow the
            // same authoritative priority/combat path far enough to prove that lethal is prevented.
            game.state = mustActState
            val response = redAi.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, response.cardId) shouldBe "Fireblast"
            chosenTargetId(response) shouldBe mascot
            response.useAlternativeCost.shouldBeTrue()
            response.additionalCostPayment?.sacrificedPermanents?.size shouldBe 2
            game.execute(response).error.shouldBeNull()

            combatTransitions = 0
            var survivalBlockerDeclarations = 0
            while (!game.state.gameOver && game.state.phase == Phase.COMBAT) {
                check(combatTransitions++ < 40) { "Combat did not settle after survival removal" }
                val priority = checkNotNull(game.state.priorityPlayerId)
                val blockersDeclared = game.state.getEntity(game.player1Id)
                    ?.has<BlockersDeclaredThisCombatComponent>() == true
                val action = if (
                    game.state.step == Step.DECLARE_BLOCKERS &&
                    priority == game.player1Id &&
                    !blockersDeclared
                ) {
                    survivalBlockerDeclarations++
                    DeclareBlockers(game.player1Id, emptyMap())
                } else {
                    PassPriority(priority)
                }
                game.execute(action).error.shouldBeNull()
            }
            survivalBlockerDeclarations shouldBe 0
            game.state.gameOver.shouldBeFalse()
            game.state.lifeTotal(game.player1Id) shouldBe 2
            game.findPermanent("Pest Mascot") shouldBe null
        }

        test("Fireblast sacrifices persist into later mana legality") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInHand(1, "Fireblast")
                .withCardInHand(1, "Grab the Prize")
                .withLifeTotal(2, 4)
                .build()

            val fireblast = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, fireblast.cardId) shouldBe "Fireblast"
            fireblast.useAlternativeCost.shouldBeTrue()
            fireblast.additionalCostPayment?.sacrificedPermanents?.size shouldBe 2
            game.execute(fireblast).error.shouldBeNull()

            game.findPermanents("Mountain").size shouldBe 2
            val laterGrab = GameSimulator(cardRegistry).getLegalActions(game.state, game.player1Id)
                .filter { legal ->
                    val cast = legal.action as? CastSpell
                    cast != null && cardName(game, cast.cardId) == "Grab the Prize"
                }
            withClue("the two sacrificed Mountains cannot be reused to pay for a later Grab") {
                laterGrab.none { it.affordable }.shouldBeTrue()
            }
        }

        test("Blood Researcher menace is valued against a single blocker") {
            val game = seeded()
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .withLifeTotal(2, 2)
                .withCardOnBattlefield(1, "Blood Researcher", summoningSickness = false)
                .withCardOnBattlefield(2, "Guttersnipe", summoningSickness = false)
                .build()
            val researcher = game.findPermanent("Blood Researcher")!!

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<DeclareAttackers>()
            withClue("one blocker cannot legally block a menace attacker, so Researcher is lethal") {
                action.attackers[researcher] shouldBe game.player2Id
            }
        }

        test("imminent-combat survival override preserves restraint and winning lines") {
            fun attackedState(
                redLife: Int,
                pestLife: Int = 20,
                attackers: List<String>,
                redHand: List<String>,
                mountains: Int = 2,
            ): TestGame {
                var builder = seeded()
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .withLifeTotal(1, redLife)
                    .withLifeTotal(2, pestLife)
                    .withLandsOnBattlefield(1, "Mountain", mountains)
                attackers.forEach { builder = builder.withCardOnBattlefield(2, it, summoningSickness = false) }
                redHand.forEach { builder = builder.withCardInHand(1, it) }
                val game = builder.build()
                game.state = game.state.updateEntity(game.player2Id) { it.with(AttackersDeclaredThisCombatComponent) }
                attackers.forEach { name ->
                    val id = game.findPermanent(name)!!
                    game.state = game.state.updateEntity(id) { it.with(AttackingComponent(game.player1Id)) }
                }
                return game
            }

            val nonlethal = attackedState(3, attackers = listOf("Pest Mascot"), redHand = listOf("Fireblast"))
            run {
                val action = ai(nonlethal).chooseAction(nonlethal.state)
                (action is CastSpell && cardName(nonlethal, action.cardId) == "Fireblast").shouldBeFalse()
            }

            val stillLethal = attackedState(
                redLife = 2,
                attackers = listOf("Pest Mascot", "Guttersnipe"),
                redHand = listOf("Fireblast"),
            )
            val ineffective = ai(stillLethal).chooseAction(stillLethal.state)
            (ineffective is CastSpell && cardName(stillLethal, ineffective.cardId) == "Fireblast").shouldBeFalse()

            val cheaper = attackedState(
                redLife = 2,
                attackers = listOf("Pest Mascot"),
                redHand = listOf("Fireblast", "Lightning Bolt"),
            )
            val cheaperAction = ai(cheaper).chooseAction(cheaper.state).shouldBeInstanceOf<CastSpell>()
            cardName(cheaper, cheaperAction.cardId) shouldBe "Lightning Bolt"
            chosenTargetId(cheaperAction) shouldBe cheaper.findPermanent("Pest Mascot")

            val winNow = attackedState(
                redLife = 2,
                pestLife = 4,
                attackers = listOf("Pest Mascot"),
                redHand = listOf("Fireblast"),
            )
            val winningAction = ai(winNow).chooseAction(winNow.state).shouldBeInstanceOf<CastSpell>()
            cardName(winNow, winningAction.cardId) shouldBe "Fireblast"
            chosenTargetId(winningAction) shouldBe winNow.player2Id

            val protected = attackedState(
                redLife = 2,
                attackers = listOf("Pest Mascot"),
                redHand = listOf("Fireblast"),
            )
            val protectedMascot = protected.findPermanent("Pest Mascot")!!
            protected.state = protected.state.updateEntity(protectedMascot) {
                it.with(HexproofFromComponent(colors = setOf(Color.RED)))
            }
            val protectedAction = ai(protected).chooseAction(protected.state)
            (protectedAction is CastSpell && chosenTargetId(protectedAction) == protectedMascot)
                .shouldBeFalse()
        }
    }
}
