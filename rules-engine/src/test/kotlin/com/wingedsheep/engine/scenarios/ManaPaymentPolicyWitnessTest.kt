package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibility
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibilityResult
import com.wingedsheep.engine.mechanics.mana.ManaPaymentRequest
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.costs.CostAtom
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** The shared planner obeys an external material policy; it never supplies tactical rankings. */
class ManaPaymentPolicyWitnessTest : FunSpec({
    val altar = card("Witness Material Choice Altar") {
        typeLine = "Artifact"
        activatedAbility {
            cost = Costs.Sacrifice(GameObjectFilter.Creature)
            effect = Effects.AddColorlessMana(2)
            manaAbility = true
        }
    }
    val creature = card("Witness Material Choice Creature") {
        typeLine = "Artifact Creature — Construct"
        power = 1
        toughness = 1
    }
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(altar, creature))
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        putPermanentOnBattlefield(activePlayer!!, altar.name)
    }
    fun assess(d: GameTestDriver, request: ManaPaymentRequest) =
        ManaPaymentFeasibility(d.cardRegistry).assess(d.state, d.activePlayer!!, request)

    test("funding sacrifice follows the caller's selected material instead of planner identity order") {
        val d = game()
        val ids = List(2) { d.putPermanentOnBattlefield(d.activePlayer!!, creature.name) }
        val selected = ids.maxBy { it.value }
        val request = ManaPaymentRequest(ManaCost.parse("{2}"), selectFundingSacrifice = { options ->
            options.sourceId shouldBe d.state.getBattlefield().single {
                d.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == altar.name
            }
            options.eligibleMaterials.toSet() shouldBe ids.toSet()
            selected
        })
        val result = assess(d, request).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.funding.single().action.costPayment!!.sacrificedPermanents shouldBe listOf(selected)
        d.submitSuccess(result.funding.single().action)
        (selected in d.state.getBattlefield()) shouldBe false
        d.state.getEntity(d.activePlayer!!)!!.get<ManaPoolComponent>()!!.colorless shouldBe 2
    }
    test("a declined funding choice is unsupported and never an exhaustive negative") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        val plain = ManaPaymentRequest(ManaCost.parse("{2}"))
        assess(d, plain).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        assess(d, plain.copy(selectFundingSacrifice = { null }))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
        // The same fail-closed distinction applies before search when final materials are absent.
        assess(d, plain.copy(finalSacrificeCost = CostAtom.Sacrifice(GameObjectFilter.Land),
            selectFundingSacrifice = { null })).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
    }
    test("the callback cannot consume or replace the original final sacrifice") {
        val d = game()
        val reserved = d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        val available = d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        val request = ManaPaymentRequest(ManaCost.parse("{2}"),
            finalSacrificeCost = CostAtom.Sacrifice(GameObjectFilter.Creature),
            boundFinalSacrifices = listOf(reserved), selectFundingSacrifice = { options ->
                options.eligibleMaterials shouldBe listOf(available)
                available
            })
        val result = assess(d, request).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.finalSacrifices shouldBe listOf(reserved)
        d.submitSuccess(result.funding.single().action)
        (reserved in d.state.getBattlefield()) shouldBe true
    }
    test("a policy response outside current legal materials fails closed without submitting an action") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        val before = d.state
        val result = assess(d, ManaPaymentRequest(ManaCost.parse("{2}"),
            selectFundingSacrifice = { EntityId("not-an-offered-material") }))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
        result.reasons.any { "outside the current legal options" in it } shouldBe true
        d.state shouldBe before
    }
    val colorFilter = card("Witness Policy Color Filter") {
        typeLine = "Artifact"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
            effect = Effects.AddAnyColorMana(1)
            manaAbility = true
        }
    }
    test("a frozen black-only funding choice cannot certify an unchosen green payment") {
        val d = game()
        d.registerCard(colorFilter)
        d.putPermanentOnBattlefield(d.activePlayer!!, colorFilter.name)
        d.giveColorlessMana(d.activePlayer!!, 1)
        val request = ManaPaymentRequest(ManaCost.parse("{G}"))
        assess(d, request).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        assess(d, request.copy(allowFundingAction = {
            it.action.manaColorChoice == com.wingedsheep.sdk.core.Color.BLACK
        })).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
    }
    test("an allowed color witness executes through the real selected funding action") {
        val d = game()
        d.registerCard(colorFilter)
        val source = d.putPermanentOnBattlefield(d.activePlayer!!, colorFilter.name)
        val material = d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        val result = assess(d, ManaPaymentRequest(ManaCost.parse("{G}"), allowFundingAction = {
            if (it.action.sourceId != source) true else {
                it.pool.colorless shouldBe 2
                it.consumedMaterials shouldBe setOf(material)
                it.tappedSources shouldBe emptySet()
                it.action.manaColorChoice == com.wingedsheep.sdk.core.Color.GREEN
            }
        })).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.funding.size shouldBe 2
        result.funding.forEach { d.submitSuccess(it.action) }
        (material in d.state.getBattlefield()) shouldBe false
        d.state.getEntity(d.activePlayer!!)!!.get<ManaPoolComponent>()!!.green shouldBe 1
    }
    test("an action-policy refusal stays unsupported while an already floated payment needs no action") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        val request = ManaPaymentRequest(ManaCost.parse("{2}"), allowFundingAction = { false })
        assess(d, request).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
        d.giveColorlessMana(d.activePlayer!!, 2)
        assess(d, request).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
            .funding shouldBe emptyList()
    }

})
