package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
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

    init {
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

        test("deploys each spell-damage engine before a profitable follow-up spell") {
            listOf("Guttersnipe" to 4, "Kessig Flamebreather" to 3).forEach { (engine, mountains) ->
                val game = seeded()
                    .withLandsOnBattlefield(1, "Mountain", mountains)
                    .withCardInHand(1, engine)
                    .withCardInHand(1, "Lightning Bolt")
                    .withLifeTotal(2, 20)
                    .build()

                val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
                withClue(engine) { cardName(game, action.cardId) shouldBe engine }
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
    }
}
