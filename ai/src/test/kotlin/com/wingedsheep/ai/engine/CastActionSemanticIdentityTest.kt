package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class CastActionSemanticIdentityTest : ScenarioTestBase() {
    init {
        test("physical copies with the same complete cast semantics are interchangeable") {
            val game = twoWeatherState()
            val actions = weatherActions(game)
            val firstPhysicalId = (actions[0].action as CastSpell).cardId
            val secondPhysicalId = (actions[1].action as CastSpell).cardId
            (firstPhysicalId != secondPhysicalId).shouldBeTrue()
            CastActionSemanticIdentity.equivalent(game.state, actions[0], actions[1]).shouldBeTrue()
            (actions[0].action as CastSpell).cardId.shouldBe(firstPhysicalId)
            (actions[1].action as CastSpell).cardId.shouldBe(secondPhysicalId)

            val first = actions[0].copy(action = (actions[0].action as CastSpell).copy(
                paymentStrategy = PaymentStrategy.Explicit(listOf(game.findPermanent("Forest")!!)),
            ))
            CastActionSemanticIdentity.equivalent(game.state, first, actions[1]).shouldBeTrue()
        }

        test("different definitions are not interchangeable merely because both are casts") {
            val game = scenario().withPlayers()
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Follow the Lumarets")
                .build()
            val casts = GameSimulator(cardRegistry).getLegalActions(game.state, game.player1Id)
                .filter { it.action is CastSpell }
            CastActionSemanticIdentity.equivalent(game.state, casts[0], casts[1]).shouldBeFalse()
        }

        test("different modes targets X and cost paths remain strategically distinct") {
            val game = twoWeatherState()
            val base = weatherActions(game)[0]
            val otherCopy = weatherActions(game)[1]
            val cast = otherCopy.action as CastSpell
            val target = game.findPermanent("Forest")!!

            listOf(
                cast.copy(chosenModes = listOf(1)),
                cast.copy(targets = listOf(ChosenTarget.Permanent(target))),
                cast.copy(xValue = 2),
                cast.copy(useAlternativeCost = true, alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE),
                cast.copy(additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(target))),
            ).forEach { different ->
                CastActionSemanticIdentity.equivalent(
                    game.state,
                    base,
                    otherCopy.copy(action = different),
                ).shouldBeFalse()
            }
        }

        test("different zone permission and payable cost metadata remain distinct") {
            val game = twoWeatherState()
            val (first, second) = weatherActions(game)
            CastActionSemanticIdentity.equivalent(
                game.state, first, second.copy(sourceZone = "GRAVEYARD"),
            ).shouldBeFalse()
            CastActionSemanticIdentity.equivalent(
                game.state, first, second.copy(manaCostString = "{2}{G}"),
            ).shouldBeFalse()
            CastActionSemanticIdentity.equivalent(
                game.state, first, second.copy(affordable = false),
            ).shouldBeFalse()
        }
    }

    private fun twoWeatherState() = scenario().withPlayers()
        .withLandsOnBattlefield(1, "Forest", 3)
        .withCardInHand(1, "Weather the Storm")
        .withCardInHand(1, "Weather the Storm")
        .build()

    private fun weatherActions(game: TestGame) = GameSimulator(cardRegistry)
        .getLegalActions(game.state, game.player1Id)
        .filter { legal ->
            val cast = legal.action as? CastSpell
            cast != null && game.state.getEntity(cast.cardId)
                ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Weather the Storm"
        }
}
