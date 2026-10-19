package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.composite.payManaCostFromPool
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Resolution-payment mechanic: actual solver, pool debit, and a real Pact upkeep continuation. */
class ManualResolutionPaymentRegressionTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        fun auraBoard() = scenario().withPlayers().withCardOnBattlefield(1, "Forest")
            .withCardAttachedTo(1, "Wild Growth", "Forest").build()
        fun pay(game: TestGame, cost: String): EffectResult {
            val result = payManaCostFromPool(game.state, game.player1Id, ManaCost.parse(cost), cardRegistry)
            println("R5_RESOLUTION_PAYMENT cost=$cost success=${result.isSuccess} events=${result.events} error=${result.error}")
            check(result.isSuccess)
            game.state = result.state
            return result
        }
        fun pool(game: TestGame) = requireNotNull(game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>())
        test("original aura quote reconstruction fails but actual repaired payment spends both mana") {
            val game = auraBoard()
            val cost = ManaCost.parse("{G}{G}")
            val quote = requireNotNull(ManaSolver(cardRegistry).solve(game.state, game.player1Id, cost))
            // Exact old reconstruction: printed production only, one colored mana regardless of amount.
            var legacyPool = ManaPool()
            for ((_, production) in quote.manaProduced) {
                val printedColor = production.color
                legacyPool = if (printedColor != null)
                    legacyPool.add(printedColor) else legacyPool.addColorless(production.colorless)
            }
            legacyPool.pay(cost) shouldBe null
            println("R5_ORIGINAL_PAYMENT_FAILURE quote=$quote reconstructed=$legacyPool cost=$cost")
            val result = pay(game, "{G}{G}")
            result.events.filterIsInstance<ManaSpentEvent>().single().green shouldBe 2
            pool(game).green shouldBe 0
            game.state.getEntity(game.findPermanent("Forest")!!)?.has<TappedComponent>() shouldBe true
        }
        test("printed three colored mana is reconstructed at its full amount") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Gilded Lotus").build()
            val result = pay(game, "{U}{U}{U}")
            result.events.filterIsInstance<ManaSpentEvent>().single().blue shouldBe 3
            pool(game).blue shouldBe 0
            game.state.getEntity(game.findPermanent("Gilded Lotus")!!)?.has<TappedComponent>() shouldBe true
        }
        test("partial floating mana plus aura source preserves unspent bonus exactly once") {
            val game = auraBoard()
            game.state = game.state.updateEntity(game.player1Id) { it.with(ManaPoolComponent(green = 1)) }
            val result = pay(game, "{G}{G}")
            result.events.filterIsInstance<ManaSpentEvent>().single().green shouldBe 2
            pool(game).green shouldBe 1
        }
        test("one mana payment leaves the aura bonus floating") {
            val game = auraBoard()
            val result = pay(game, "{G}")
            result.events.filterIsInstance<ManaSpentEvent>().single().green shouldBe 1
            pool(game).green shouldBe 1
        }
        test("printed Sol Ring excess is not counted twice") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Sol Ring").build()
            val result = pay(game, "{1}")
            result.events.filterIsInstance<ManaSpentEvent>().single().colorless shouldBe 1
            pool(game).colorless shouldBe 1
        }
        test("actual Pact upkeep yes spends five mana from four sources including Wild Growth") {
            val game = scenario().withPlayers().withActivePlayer(2)
                .withCardInHand(2, "Grizzly Bears").withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(1, "Pact of Negation").withLandsOnBattlefield(1, "Island", 3)
                .withCardOnBattlefield(1, "Forest").withCardAttachedTo(1, "Wild Growth", "Forest").build()
            game.castSpell(2, "Grizzly Bears").error shouldBe null
            val target = game.state.stack.last()
            game.passPriority().error shouldBe null
            game.execute(CastSpell(game.player1Id, game.findCardsInHand(1, "Pact of Negation").single(), listOf(ChosenTarget.Spell(target)))).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            val result = game.answerYesNo(true)
            println("R5_ACTUAL_PACT_PAYMENT events=${result.events} error=${result.error}")
            result.error shouldBe null
            val spent = result.events.filterIsInstance<ManaSpentEvent>().single()
            spent.total shouldBe 5
            check(spent.blue >= 2)
            game.state.projectedState.getBattlefieldControlledBy(game.player1Id).count {
                game.state.getEntity(it)?.has<TappedComponent>() == true
            } shouldBe 4
            pool(game).blue shouldBe 0
            pool(game).green shouldBe 0
            game.state.gameOver shouldBe false
            game.state.delayedTriggers.size shouldBe 0
        }
    }
}
