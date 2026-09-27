package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibility
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibilityResult
import com.wingedsheep.engine.mechanics.mana.ManaPaymentRequest
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Finite-resource payment fixtures only. No project seed vector, pilot, deck change or gameplay. */
class ManaPaymentFeasibilityTest : FunSpec({
    val altar = card("Feasibility Creature Altar") {
        typeLine = "Artifact"
        manaCost = "{3}"
        activatedAbility {
            cost = Costs.Sacrifice(GameObjectFilter.Creature)
            effect = Effects.AddColorlessMana(2)
            manaAbility = true
        }
    }
    val creature = card("Feasibility Artifact Creature") {
        typeLine = "Artifact Creature — Construct"
        manaCost = "{2}"
        power = 1
        toughness = 1
    }
    val spawn = card("Feasibility Registered Spawn") {
        typeLine = "Creature — Eldrazi Spawn"
        manaCost = "{0}"
        power = 0
        toughness = 1
        activatedAbility {
            cost = Costs.SacrificeSelf
            effect = Effects.AddColorlessMana(1)
            manaAbility = true
        }
    }
    fun filter(name: String, price: Int, self: Boolean = false) = card(name) {
        typeLine = "Artifact"
        manaCost = "{1}"
        activatedAbility {
            cost = if (self) Costs.Composite(Costs.Mana("{$price}"), Costs.Tap, Costs.SacrificeSelf)
                else Costs.Composite(Costs.Mana("{$price}"), Costs.Tap)
            effect = Effects.AddAnyColorMana(1)
            manaAbility = true
        }
        if (self) triggeredAbility {
            trigger = Triggers.Dies
            effect = Effects.DrawCards(1)
        }
    }
    val star = filter("Feasibility Sacrifice Filter", 1, self = true)
    val prism = filter("Feasibility Reusable Filter", 1)
    val boulder = filter("Feasibility Two Mana Filter", 2)
    val tronNames = listOf("Feasibility Tower", "Feasibility Mine", "Feasibility Plant")
    val tron = tronNames.mapIndexed { index, name -> card(name) {
        typeLine = "Land"
        activatedAbility {
            cost = Costs.Tap
            effect = ConditionalEffect(
                Conditions.All(*tronNames.filter { it != name }.map {
                    Conditions.YouControl(GameObjectFilter.Land.named(it))
                }.toTypedArray()),
                Effects.AddColorlessMana(if (index == 0) 3 else 2), Effects.AddColorlessMana(1))
            manaAbility = true
        }
    } }
    val sacrificeLandSpell = card("Feasibility Land Payment Payload") {
        typeLine = "Instant"
        manaCost = "{G}"
        additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Land))
        spell { effect = Effects.GainLife(1) }
    }
    val sacrificeArtifactSpell = card("Feasibility Artifact Payment Payload") {
        typeLine = "Instant"
        manaCost = "{4}{B}"
        additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.CreatureOrArtifact))
        spell { effect = Effects.GainLife(1) }
    }
    val unsupported = card("Feasibility Unsupported Counter Source") {
        typeLine = "Artifact"
        manaCost = "{1}"
        activatedAbility {
            cost = Costs.Composite(Costs.Tap, Costs.PayLife(1))
            effect = Effects.AddMana(Color.BLACK)
            manaAbility = true
        }
    }
    val entryTappedLand = card("Feasibility Entry Tapped Artifact Land") {
        typeLine = "Artifact Land"
        replacementEffect(EntersTapped())
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddMana(Color.BLACK)
            manaAbility = true
        }
    }
    val discountedMana = card("Feasibility Discounted Activation Source") {
        typeLine = "Artifact"
        manaCost = "{1}"
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
            effect = Effects.AddMana(Color.BLACK)
            manaAbility = true
            genericCostReduction = DynamicAmount.Fixed(1)
        }
    }
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(altar, creature, spawn, star, prism, boulder,
            sacrificeLandSpell, sacrificeArtifactSpell, unsupported, discountedMana, entryTappedLand) + tron)
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun request(cost: String, sacrifice: GameObjectFilter? = null, bound: List<EntityId>? = null,
        excluded: Set<EntityId> = emptySet(), cap: Int = 100_000) = ManaPaymentRequest(
        ManaCost.parse(cost), finalSacrificeCost = sacrifice?.let { CostAtom.Sacrifice(it) },
        boundFinalSacrifices = bound, excludedManaEntities = excluded, maxSearchStates = cap)
    fun assess(d: GameTestDriver, r: ManaPaymentRequest) =
        ManaPaymentFeasibility(d.cardRegistry).assess(d.state, d.activePlayer!!, r)
    fun vector(d: GameTestDriver): ManaPool {
        val p = d.state.getEntity(d.activePlayer!!)!!.get<ManaPoolComponent>()!!
        return ManaPool(p.white, p.blue, p.black, p.red, p.green, p.colorless)
    }
    fun executeFunding(d: GameTestDriver, result: ManaPaymentFeasibilityResult.Payable) {
        for (step in result.funding) {
            vector(d) shouldBe step.poolBefore
            step.action.paymentStrategy shouldBe PaymentStrategy.FromPool
            d.submitSuccess(step.action)
            vector(d) shouldBe step.poolAfter
        }
    }

    for (price in listOf("{2}", "{3}")) test("lone unfunded Star cannot bootstrap the $price Compact checkpoint") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, star.name)
        val swamp = d.putLandOnBattlefield(d.activePlayer!!, "Swamp")
        d.tapPermanent(swamp)
        val before = d.state
        assess(d, request(price)).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
        d.state shouldBe before
    }
    test("two unfunded reusable filters have no circular black payment") {
        val d = game()
        repeat(2) { d.putPermanentOnBattlefield(d.activePlayer!!, prism.name) }
        assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
    }
    test("one Altar repeatedly sacrifices three distinct creatures for six mana") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, altar.name)
        val fodder = List(3) { d.putPermanentOnBattlefield(d.activePlayer!!, creature.name) }
        val before = d.state
        val result = assess(d, request("{6}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.funding.size shouldBe 3
        result.funding.flatMap { it.action.costPayment!!.sacrificedPermanents }.toSet() shouldBe fodder.toSet()
        d.state shouldBe before
        executeFunding(d, result)
        vector(d).colorless shouldBe 6
    }
    test("two Altars cannot share the same creature twice") {
        val d = game()
        repeat(2) { d.putPermanentOnBattlefield(d.activePlayer!!, altar.name) }
        d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        assess(d, request("{4}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
    }
    test("a reserved Spawn cannot also fund its spell through itself or Altar") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, altar.name)
        val id = d.putPermanentOnBattlefield(d.activePlayer!!, spawn.name)
        assess(d, request("{2}", GameObjectFilter.Creature, listOf(id)))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
    }
    test("a second Spawn can fund while the selected nonmana material is preserved") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, altar.name)
        val reserved = d.putPermanentOnBattlefield(d.activePlayer!!, spawn.name)
        val available = d.putPermanentOnBattlefield(d.activePlayer!!, spawn.name)
        val result = assess(d, request("{2}", GameObjectFilter.Creature, listOf(reserved)))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.finalSacrifices shouldBe listOf(reserved)
        result.funding.single().action.costPayment!!.sacrificedPermanents shouldBe listOf(available)
        executeFunding(d, result)
        (reserved in d.state.getBattlefield()) shouldBe true
    }
    test("Tron funds a reserved Prism before the same Prism pays the final spell sacrifice") {
        val d = game()
        tron.forEach { d.putLandOnBattlefield(d.activePlayer!!, it.name) }
        val id = d.putPermanentOnBattlefield(d.activePlayer!!, prism.name)
        val spell = d.putCardInHand(d.activePlayer!!, sacrificeArtifactSpell.name)
        val result = assess(d, request("{4}{B}", GameObjectFilter.CreatureOrArtifact, listOf(id)))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.finalSacrifices shouldBe listOf(id)
        executeFunding(d, result)
        d.submitSuccess(CastSpell(d.activePlayer!!, spell,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(id)),
            paymentStrategy = PaymentStrategy.FromPool))
        vector(d) shouldBe result.poolAfterFinalPayment
        (id in d.state.getZone(ZoneKey(d.activePlayer!!, Zone.GRAVEYARD))) shouldBe true
    }
    test("the one reserved Forest may tap before being sacrificed as a final cost") {
        val d = game()
        val id = d.putLandOnBattlefield(d.activePlayer!!, "Forest")
        val spell = d.putCardInHand(d.activePlayer!!, sacrificeLandSpell.name)
        val result = assess(d, request("{G}", GameObjectFilter.Land, listOf(id)))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.funding.single().action.sourceId shouldBe id
        executeFunding(d, result)
        d.submitSuccess(CastSpell(d.activePlayer!!, spell,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(id)),
            paymentStrategy = PaymentStrategy.FromPool))
        vector(d).total shouldBe 0
    }
    test("an explicitly bound final material is never replaced with another legal sacrifice") {
        val d = game()
        val filter = d.putPermanentOnBattlefield(d.activePlayer!!, star.name)
        d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        d.putLandOnBattlefield(d.activePlayer!!, "Forest")
        assess(d, request("{B}", GameObjectFilter.CreatureOrArtifact, listOf(filter)))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
        val existential = assess(d, request("{B}", GameObjectFilter.CreatureOrArtifact))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        (filter in existential.finalSacrifices) shouldBe false
    }
    test("excluded sources cannot activate or enter another mana ability's fodder pool") {
        val d = game()
        val a = d.putPermanentOnBattlefield(d.activePlayer!!, altar.name)
        val c = d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
        for (excluded in listOf(a, c)) assess(d, request("{2}", excluded = setOf(excluded)))
            .shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
    }
    test("large generic Golem fodder has a prompt exhaustive negative colored proof") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, altar.name)
        repeat(12) {
            val id = d.putPermanentOnBattlefield(d.activePlayer!!, creature.name)
            d.replaceState(d.state.updateEntity(id) { container ->
                val c = container.get<CardComponent>()!!
                container.with(c.copy(cardDefinitionId = "token:Feasibility-Golem")).with(TokenComponent)
            })
        }
        val result = assess(d, request("{B}", cap = 2)).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
        result.statesExamined shouldBe 1
        assess(d, request("{24}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
    }
    test("a freshly entered Spawn can sacrifice itself without paying a tap cost") {
        val d = game()
        val id = d.putPermanentOnBattlefield(d.activePlayer!!, spawn.name)
        val result = assess(d, request("{1}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        result.funding.single().action.sourceId shouldBe id
        executeFunding(d, result)
    }
    test("two-mana filtering consumes both funding units before output") {
        val d = game()
        repeat(2) { d.putLandOnBattlefield(d.activePlayer!!, "Forest") }
        d.putPermanentOnBattlefield(d.activePlayer!!, boulder.name)
        val result = assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        executeFunding(d, result)
        vector(d).black shouldBe 1
        vector(d).total shouldBe 1
    }
    test("Star's future death draw is never used as a payment resource") {
        val d = game()
        d.putLandOnBattlefield(d.activePlayer!!, "Forest")
        d.putPermanentOnBattlefield(d.activePlayer!!, star.name)
        val library = d.state.getZone(ZoneKey(d.activePlayer!!, Zone.LIBRARY))
        val result = assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        executeFunding(d, result)
        d.state.getZone(ZoneKey(d.activePlayer!!, Zone.LIBRARY)) shouldBe library
        d.state.stack.isNotEmpty() shouldBe true
    }
    test("unsupported nonmana activation metadata cannot produce a negative claim") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, unsupported.name)
        assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
    }
    test("an already entered tapped artifact land has a complete payment proof after untapping") {
        val d = game()
        val id = d.putLandOnBattlefield(d.activePlayer!!, entryTappedLand.name)
        d.tapPermanent(id)
        assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Impossible>()
        d.replaceState(d.state.updateEntity(id) { it.without<TappedComponent>() })
        val payable = assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        executeFunding(d, payable)
        vector(d).black shouldBe 1
    }
    test("an activation discount cannot be ignored when proving a negative") {
        val d = game()
        d.putPermanentOnBattlefield(d.activePlayer!!, discountedMana.name)
        assess(d, request("{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
    }
    test("a finite search cap produces unsupported rather than impossible") {
        val d = game()
        repeat(3) { d.putLandOnBattlefield(d.activePlayer!!, "Forest") }
        assess(d, request("{3}", cap = 1)).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Unsupported>()
    }
    test("future library order cannot change a payment witness") {
        val d = game()
        d.putLandOnBattlefield(d.activePlayer!!, "Forest")
        d.putPermanentOnBattlefield(d.activePlayer!!, prism.name)
        val first = assess(d, request("{B}"))
        val zone = ZoneKey(d.activePlayer!!, Zone.LIBRARY)
        d.replaceState(d.state.copy(zones = d.state.zones + (zone to d.state.getZone(zone).reversed())))
        assess(d, request("{B}")) shouldBe first
    }
    test("serialized funding actions replay the exact real states and ordered events") {
        val d = game()
        tron.forEach { d.putLandOnBattlefield(d.activePlayer!!, it.name) }
        d.putPermanentOnBattlefield(d.activePlayer!!, prism.name)
        val result = assess(d, request("{4}{B}")).shouldBeInstanceOf<ManaPaymentFeasibilityResult.Payable>()
        val wire = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        var replay = wire.decodeFromString(GameState.serializer(), wire.encodeToString(GameState.serializer(), d.state))
        val processor = ActionProcessor(d.cardRegistry)
        for (step in result.funding) {
            val action = wire.decodeFromString(GameAction.serializer(), wire.encodeToString(GameAction.serializer(), step.action))
            val actual = d.submitSuccess(step.action)
            val restored = processor.process(replay, action).result
            restored shouldBe actual
            replay = restored.state
        }
        replay shouldBe d.state
    }
})
