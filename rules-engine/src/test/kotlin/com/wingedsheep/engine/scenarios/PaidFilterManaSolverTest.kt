package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Engine payment regressions. Inline sources isolate filter costs without card-corpus dependencies. */
class PaidFilterManaSolverTest : FunSpec({
    fun filter(name: String, activationCost: Int, freeColorless: Boolean = false) = card(name) {
        typeLine = "Artifact"
        manaCost = "{2}"
        if (freeColorless) activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddColorlessMana(1)
            manaAbility = true
        }
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{$activationCost}"), Costs.Tap)
            effect = Effects.AddAnyColorMana(1)
            manaAbility = true
        }
    }
    val prism = filter("Paid Filter Fixture", 1)
    val boulder = filter("Expensive Filter Fixture", 2)
    val mixed = filter("Mixed Free Filter Fixture", 1, freeColorless = true)
    val triple = card("Triple Colorless Fixture") {
        typeLine = "Artifact"
        manaCost = "{3}"
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddColorlessMana(3)
            manaAbility = true
        }
    }
    val doubleColorless = card("Double Colorless Fixture") {
        typeLine = "Land — Cave"
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddColorlessMana(2)
            manaAbility = true
        }
    }
    fun redSource(name: String, restriction: ManaRestriction? = null) = card(name) {
        typeLine = "Artifact"
        manaCost = "{3}"
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.AddMana(Color.RED, 2, restriction = restriction)
            manaAbility = true
        }
    }
    val doubleRed = redSource("Double Red Fixture")
    val restrictedRed = redSource("Restricted Red Fixture", ManaRestriction.CreatureSpellsOnly)
    val payloads = listOf("{B}", "{1}{B}", "{R}{B}", "{B}{B}").map { price ->
        card("Filter Payload $price") {
            typeLine = "Creature — Construct"
            manaCost = price
            power = 1
            toughness = 1
        }
    }
    val activationPayload = card("Filter Activation Fixture") {
        typeLine = "Artifact"
        manaCost = "{1}"
        for (price in listOf("{1}{B}", "{R}{B}", "{B}{B}")) activatedAbility {
            cost = Costs.Mana(price)
            effect = Effects.GainLife(1)
        }
    }
    fun game() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(prism, boulder, mixed, triple, doubleColorless, doubleRed, restrictedRed, activationPayload) + payloads)
        initMirrorMatch(Deck.of("Mountain" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun pool(d: GameTestDriver) = d.state.getEntity(d.activePlayer!!)!!.get<ManaPoolComponent>()!!.total

    for (price in listOf("{1}", "{B}")) test("an unfunded filter cannot pay $price") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, prism.name)
        val before = d.state
        val solver = ManaSolver(d.cardRegistry)
        solver.solve(d.state, player, ManaCost.parse(price)) shouldBe null
        solver.canPay(d.state, player, ManaCost.parse(price)) shouldBe false
        d.state shouldBe before
    }
    test("two unfunded filters cannot finance one another") {
        val d = game()
        val player = d.activePlayer!!
        repeat(2) { d.putPermanentOnBattlefield(player, prism.name) }
        ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{B}")) shouldBe null
        ManaSolver(d.cardRegistry).canPay(d.state, player, ManaCost.parse("{B}")) shouldBe false
    }
    test("free colorless mode of a mixed filter remains ordinary mana") {
        val d = game()
        val player = d.activePlayer!!
        val id = d.putPermanentOnBattlefield(player, mixed.name)
        val solution = ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{1}")).shouldNotBeNull()
        solution.sources.map { it.entityId } shouldBe listOf(id)
        solution.manaProduced.getValue(id).colorless shouldBe 1
        solution.manaProduced.getValue(id).color shouldBe null
    }
    test("a real source funds the filter before its colored output is used by a cast") {
        val d = game()
        val player = d.activePlayer!!
        val filter = d.putPermanentOnBattlefield(player, prism.name)
        val land = d.putLandOnBattlefield(player, "Mountain")
        val solution = ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{B}")).shouldNotBeNull()
        solution.sources.map { it.entityId } shouldBe listOf(land, filter)
        solution.manaProduced.getValue(land).amount shouldBe 0
        val spell = d.putCardInHand(player, "Filter Payload {B}")
        d.castSpell(player, spell).isSuccess shouldBe true
        d.isTapped(land) shouldBe true
        d.isTapped(filter) shouldBe true
        pool(d) shouldBe 0
    }
    test("excluding either the filter or its only funder removes the colored plan") {
        val d = game()
        val player = d.activePlayer!!
        val filter = d.putPermanentOnBattlefield(player, prism.name)
        val land = d.putLandOnBattlefield(player, "Mountain")
        val solver = ManaSolver(d.cardRegistry)
        for (excluded in listOf(filter, land)) {
            solver.solve(d.state, player, ManaCost.parse("{B}"), excludeSources = setOf(excluded)) shouldBe null
            solver.canPay(d.state, player, ManaCost.parse("{B}"), excludeSources = setOf(excluded)) shouldBe false
        }
    }
    test("a two-mana filter requires two independently available funding units") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, boulder.name)
        d.putLandOnBattlefield(player, "Mountain")
        val solver = ManaSolver(d.cardRegistry)
        solver.solve(d.state, player, ManaCost.parse("{B}")) shouldBe null
        d.putLandOnBattlefield(player, "Mountain")
        solver.solve(d.state, player, ManaCost.parse("{B}")).shouldNotBeNull()
        d.castSpell(player, d.putCardInHand(player, "Filter Payload {B}")).isSuccess shouldBe true
        pool(d) shouldBe 0

        // The same two units can come from one source. They are spent on the filter, not on
        // the spell; the actual cast event must retain neither its source id nor Cave subtype.
        val e = game()
        val caster = e.activePlayer!!
        val funder = e.putLandOnBattlefield(caster, doubleColorless.name)
        val fundedFilter = e.putPermanentOnBattlefield(caster, boulder.name)
        val cast = e.castSpell(caster, e.putCardInHand(caster, "Filter Payload {B}"))
        cast.isSuccess shouldBe true
        val event = cast.events.filterIsInstance<SpellCastEvent>().single()
        event.spentManaSourceIds shouldBe setOf(fundedFilter)
        event.spentManaSubtypes.isEmpty() shouldBe true
        e.isTapped(funder) shouldBe true
        pool(e) shouldBe 0
    }
    for (activate in listOf(false, true)) test("multi-mana funding returns correct excess for ${if (activate) "ability" else "spell"} payment") {
        val d = game()
        val player = d.activePlayer!!
        val funder = d.putPermanentOnBattlefield(player, triple.name)
        val filter = d.putPermanentOnBattlefield(player, prism.name)
        val solution = ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{1}{B}")).shouldNotBeNull()
        solution.sources.map { it.entityId } shouldBe listOf(funder, filter)
        solution.manaProduced.getValue(funder).amount shouldBe 0
        solution.manaProduced.getValue(funder).colorless shouldBe 2
        if (activate) {
            val source = d.putPermanentOnBattlefield(player, activationPayload.name)
            d.submit(ActivateAbility(player, source, activationPayload.activatedAbilities[0].id)).isSuccess shouldBe true
        } else d.castSpell(player, d.putCardInHand(player, "Filter Payload {1}{B}")).isSuccess shouldBe true
        pool(d) shouldBe 1
    }
    for (activate in listOf(false, true)) test("previous colored excess funding is deducted once for ${if (activate) "ability" else "spell"} payment") {
        val d = game()
        val player = d.activePlayer!!
        val red = d.putPermanentOnBattlefield(player, doubleRed.name)
        d.putPermanentOnBattlefield(player, prism.name)
        val solution = ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{R}{B}")).shouldNotBeNull()
        solution.manaProduced.getValue(red).amount shouldBe 1
        if (activate) {
            val source = d.putPermanentOnBattlefield(player, activationPayload.name)
            d.submit(ActivateAbility(player, source, activationPayload.activatedAbilities[1].id)).isSuccess shouldBe true
        } else d.castSpell(player, d.putCardInHand(player, "Filter Payload {R}{B}")).isSuccess shouldBe true
        pool(d) shouldBe 0
    }
    test("two filters may share distinct already-funded units without creating a cycle") {
        val d = game()
        val player = d.activePlayer!!
        val funder = d.putPermanentOnBattlefield(player, triple.name)
        repeat(2) { d.putPermanentOnBattlefield(player, prism.name) }
        val source = d.putPermanentOnBattlefield(player, activationPayload.name)
        val solution = ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{B}{B}")).shouldNotBeNull()
        solution.manaProduced.getValue(funder).colorless shouldBe 1
        d.submit(ActivateAbility(player, source, activationPayload.activatedAbilities[2].id)).isSuccess shouldBe true
        pool(d) shouldBe 1
    }
    test("creature-spell-only excess cannot pay a filter's activation cost") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, restrictedRed.name)
        d.putPermanentOnBattlefield(player, prism.name)
        val context = SpellPaymentContext(isCreature = true, cardTypes = setOf(CardType.CREATURE))
        ManaSolver(d.cardRegistry).solve(d.state, player, ManaCost.parse("{R}{B}"), spellContext = context) shouldBe null
    }
    test("serialized funded-filter cast replays identical state and ordered events") {
        val d = game()
        val player = d.activePlayer!!
        d.putPermanentOnBattlefield(player, triple.name)
        d.putPermanentOnBattlefield(player, prism.name)
        val spell = d.putCardInHand(player, "Filter Payload {1}{B}")
        val action: GameAction = CastSpell(player, spell)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), d.state))
        val replayAction = json.decodeFromString(GameAction.serializer(), json.encodeToString(GameAction.serializer(), action))
        val processor = ActionProcessor(d.cardRegistry)
        val first = processor.process(d.state, action).result
        val second = processor.process(restored, replayAction).result
        first.error shouldBe null
        second shouldBe first
        first.state.getEntity(player)!!.get<ManaPoolComponent>()!!.total shouldBe 1
    }
})
