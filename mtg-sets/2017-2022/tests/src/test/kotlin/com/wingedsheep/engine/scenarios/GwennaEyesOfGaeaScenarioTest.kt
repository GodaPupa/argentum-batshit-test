package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.bro.cards.GwennaEyesOfGaea
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GwennaEyesOfGaeaScenarioTest : FunSpec({
    val creatureSpell = CardDefinition.creature(
        name = "Gwenna Creature Fixture",
        manaCost = ManaCost.parse("{G}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 2,
        toughness = 2,
    )
    val noncreatureSpell = CardDefinition.sorcery(
        name = "Gwenna Sorcery Fixture",
        manaCost = ManaCost.parse("{G}{G}"),
        oracleText = "",
    )
    val abilityCreature = card("Gwenna Ability Fixture") {
        manaCost = "{1}{G}"
        colorIdentity = "G"
        typeLine = "Creature — Elf"
        power = 1
        toughness = 1
        activatedAbility {
            cost = Costs.Mana("{G}{G}")
            effect = Effects.GainLife(1)
        }
    }
    val largeCreature = CardDefinition.creature(
        name = "Gwenna Large Creature Fixture",
        manaCost = ManaCost.ZERO,
        subtypes = setOf(Subtype("Beast")),
        power = 5,
        toughness = 5,
    )

    fun fixture(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + GwennaEyesOfGaea + creatureSpell + noncreatureSpell + abilityCreature + largeCreature)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.activateGwennaForTwoGreen(player: EntityId, gwenna: EntityId) {
        removeSummoningSickness(gwenna)
        submit(ActivateAbility(player, gwenna, GwennaEyesOfGaea.activatedAbilities.single().id))
        repeat(2) {
            val decision = state.pendingDecision!!
            submitDecision(player, ColorChosenResponse(decision.id, Color.GREEN))
        }
        state.pendingDecision shouldBe null
        state.getEntity(player)!!.get<ManaPoolComponent>()!!.restrictedMana.size shouldBe 2
    }

    test("mana ability makes exactly two restricted mana that can cast a creature but not a sorcery") {
        run {
            val d = fixture()
            val me = d.activePlayer!!
            val gwenna = d.putCreatureOnBattlefield(me, GwennaEyesOfGaea.name)
            d.activateGwennaForTwoGreen(me, gwenna)
            val creature = d.putCardInHand(me, creatureSpell.name)
            d.submit(CastSpell(me, creature, paymentStrategy = PaymentStrategy.FromPool)).outcome shouldBe Outcome.Done
        }
        run {
            val d = fixture()
            val me = d.activePlayer!!
            val gwenna = d.putCreatureOnBattlefield(me, GwennaEyesOfGaea.name)
            d.activateGwennaForTwoGreen(me, gwenna)
            val sorcery = d.putCardInHand(me, noncreatureSpell.name)
            d.submit(CastSpell(me, sorcery, paymentStrategy = PaymentStrategy.FromPool)).outcome shouldNotBe Outcome.Done
        }
    }

    test("restricted mana can pay an activated ability of a creature source") {
        val d = fixture()
        val me = d.activePlayer!!
        val gwenna = d.putCreatureOnBattlefield(me, GwennaEyesOfGaea.name)
        val source = d.putCreatureOnBattlefield(me, abilityCreature.name)
        d.activateGwennaForTwoGreen(me, gwenna)
        d.submit(ActivateAbility(me, source, abilityCreature.activatedAbilities.single().id)).outcome shouldBe Outcome.Done
    }

    test("casting a power-five creature puts a counter on Gwenna and untaps it") {
        val d = fixture()
        val me = d.activePlayer!!
        val gwenna = d.putCreatureOnBattlefield(me, GwennaEyesOfGaea.name)
        d.tapPermanent(gwenna)
        d.isTapped(gwenna) shouldBe true
        val creature = d.putCardInHand(me, largeCreature.name)
        d.submit(CastSpell(me, creature, paymentStrategy = PaymentStrategy.FromPool)).outcome shouldBe Outcome.Done
        d.bothPass()
        d.state.projectedState.getPower(gwenna) shouldBe 3
        d.isTapped(gwenna) shouldBe false
    }
})
