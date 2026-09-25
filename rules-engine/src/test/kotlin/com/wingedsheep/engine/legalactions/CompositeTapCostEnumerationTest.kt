package com.wingedsheep.engine.legalactions

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** A source cannot pay both its separate tap symbol and a second permanent-tap cost. */
class CompositeTapCostEnumerationTest : ScenarioTestBase() {
    private fun actions(game: TestGame): List<LegalAction> {
        val services = EngineServices(cardRegistry)
        return LegalActionEnumerator(
            services.cardRegistry, services.manaSolver, services.costCalculator,
            services.predicateEvaluator, services.conditionEvaluator, services.turnManager,
        ).enumerate(game.state, game.player1Id)
    }

    private fun setup() = scenario().withPlayers("P1", "P2").withRngSeed(0x5045_5354_5350L)

    init {
        val separateTap = card("Separate Tap Fixture") {
            manaCost = "{G}"
            typeLine = "Creature — Dryad"
            power = 0
            toughness = 3
            activatedAbility {
                cost = Costs.Composite(Costs.Tap, Costs.TapPermanents(count = 1, filter = GameObjectFilter.Creature))
                effect = Effects.AddMana(Color.GREEN)
                manaAbility = true
                timing = TimingRule.ManaAbility
            }
        }
        val batchOnly = card("Tap Batch Fixture") {
            manaCost = "{G}"
            typeLine = "Creature — Dryad"
            power = 0
            toughness = 3
            activatedAbility {
                cost = Costs.Composite(Costs.Mana("{0}"), Costs.TapPermanents(count = 1, filter = GameObjectFilter.Creature))
                effect = Effects.AddMana(Color.GREEN)
                manaAbility = true
                timing = TimingRule.ManaAbility
            }
        }
        cardRegistry.register(listOf(separateTap, batchOnly))

        test("a separate source tap and another tap cannot be paid by a lone creature") {
            val game = setup().withCardOnBattlefield(1, separateTap.name).build()
            val source = game.findPermanent(separateTap.name)!!
            actions(game).any { (it.action as? ActivateAbility)?.sourceId == source } shouldBe false
        }

        test("the complete offered tap pool excludes the reserved source and executes legally") {
            val game = setup().withCardOnBattlefield(1, separateTap.name)
                .withCardOnBattlefield(1, "Gatecreeper Vine").build()
            val source = game.findPermanent(separateTap.name)!!
            val helper = game.findPermanent("Gatecreeper Vine")!!
            val offered = actions(game).single { (it.action as? ActivateAbility)?.sourceId == source }
            offered.additionalCostInfo!!.tapCount shouldBe 1
            offered.additionalCostInfo!!.validTapTargets shouldBe listOf(helper)
            val action = offered.action.shouldBeInstanceOf<ActivateAbility>()
            val before = game.state
            val forged = game.execute(action.copy(costPayment = AdditionalCostPayment(tappedPermanents = listOf(source))))
            forged.error shouldNotBe null
            forged.events shouldBe emptyList()
            game.state shouldBe before
            game.execute(action.copy(costPayment = AdditionalCostPayment(tappedPermanents = listOf(helper)))).error shouldBe null
            game.state.getEntity(source)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(helper)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        }

        test("a composite without a separate tap symbol still permits tapping its source") {
            val game = setup().withCardOnBattlefield(1, batchOnly.name).build()
            val source = game.findPermanent(batchOnly.name)!!
            val offered = actions(game).single { (it.action as? ActivateAbility)?.sourceId == source }
            offered.additionalCostInfo!!.validTapTargets shouldBe listOf(source)
            val action = offered.action.shouldBeInstanceOf<ActivateAbility>()
            game.execute(action.copy(costPayment = AdditionalCostPayment(tappedPermanents = listOf(source)))).error shouldBe null
            game.state.getEntity(source)!!.has<TappedComponent>() shouldBe true
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        }
    }
}
