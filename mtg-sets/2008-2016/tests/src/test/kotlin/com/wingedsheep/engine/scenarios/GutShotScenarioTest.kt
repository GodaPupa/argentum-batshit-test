package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Fixed real-action regression fixtures, excluded from all official Pest allocations. */
class GutShotScenarioTest : ScenarioTestBase() {
    private fun fixture(): ScenarioBuilder = scenario().withPlayers().withRngSeed(9250925023L)
        .withCardInHand(1, "Gut Shot")

    private fun TestGame.payLife(target: ChosenTarget) = execute(CastSpell(
        player1Id, findCardsInHand(1, "Gut Shot").single(), listOf(target),
        paymentStrategy = PaymentStrategy.Explicit(emptyList(), phyrexianLifePayments = listOf(Color.RED))
    ))

    private fun TestGame.damageEvents(): List<DamageDealtEvent> {
        val results = resolveStack()
        results.forEach { it.error shouldBe null }
        state.stack.size shouldBe 0
        hasPendingDecision() shouldBe false
        return results.flatMap { it.events }.filterIsInstance<DamageDealtEvent>()
    }

    init {
        test("one red mana pays the Phyrexian symbol without paying life") {
            val game = fixture().withLandsOnBattlefield(1, "Mountain", 1).build()
            val spell = game.findCardsInHand(1, "Gut Shot").single()
            game.execute(CastSpell(game.player1Id, spell, listOf(ChosenTarget.Player(game.player2Id)),
                paymentStrategy = PaymentStrategy.Explicit(listOf(game.findPermanent("Mountain")!!))))
                .error shouldBe null
            game.getLifeTotal(1) shouldBe 20
            val damage = game.damageEvents().single()
            damage.sourceId shouldBe spell
            damage.amount shouldBe 1
            damage.isCombatDamage shouldBe false
            game.getLifeTotal(2) shouldBe 19
        }

        test("two life pays the symbol with no mana while the spell remains red") {
            val game = fixture().build()
            game.getLegalActions(1).any {
                it.actionType == "CastSpell" && it.description.contains("Gut Shot") && it.isAffordable
            } shouldBe true
            game.payLife(ChosenTarget.Player(game.player2Id)).error shouldBe null
            game.getLifeTotal(1) shouldBe 18
            cardRegistry.getCard("Gut Shot")!!.colors shouldBe setOf(Color.RED)
            cardRegistry.getCard("Gut Shot")!!.manaCost.cmc shouldBe 1
            game.damageEvents().single().amount shouldBe 1
            game.getLifeTotal(2) shouldBe 19
        }

        test("a player at one life cannot pay two life and a rejected cast changes nothing") {
            val game = fixture().withLifeTotal(1, 1).build()
            val before = game.state
            game.payLife(ChosenTarget.Player(game.player2Id)).error shouldNotBe null
            game.state shouldBe before
            game.isInHand(1, "Gut Shot") shouldBe true
            game.getLifeTotal(1) shouldBe 1
            game.getLifeTotal(2) shouldBe 20
        }

        test("paying from exactly two life is legal but the caster loses before spell damage") {
            val game = fixture().withLifeTotal(1, 2).build()
            game.payLife(ChosenTarget.Player(game.player2Id)).error shouldBe null
            game.getLifeTotal(1) shouldBe 0
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player2Id
            game.getLifeTotal(2) shouldBe 20
        }

        test("one actual damage kills a one toughness creature") {
            val game = fixture().withCardOnBattlefield(2, "Llanowar Elves").build()
            game.payLife(ChosenTarget.Permanent(game.findPermanent("Llanowar Elves")!!)).error shouldBe null
            game.damageEvents().single().amount shouldBe 1
            game.isInGraveyard(2, "Llanowar Elves") shouldBe true
            game.getLifeTotal(1) shouldBe 18
            game.getLifeTotal(2) shouldBe 20
        }

        test("a planeswalker is a legal target and damage removes one loyalty counter") {
            val game = fixture().withCardOnBattlefield(2, "Jace Beleren").build()
            val jace = game.findPermanent("Jace Beleren")!!
            game.state.getEntity(jace)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY) shouldBe 3
            game.payLife(ChosenTarget.Permanent(jace)).error shouldBe null
            game.damageEvents().single().amount shouldBe 1
            game.state.getEntity(jace)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY) shouldBe 2
            game.getLifeTotal(2) shouldBe 20
        }

        test("a battle is a legal target and damage removes one defense counter") {
            val game = fixture().withCardOnBattlefield(2, "Invasion of Innistrad").build()
            game.checkStateBasedActions()
            val battle = game.findPermanent("Invasion of Innistrad")!!
            game.state.getEntity(battle)!!.get<CountersComponent>()!!.getCount(CounterType.DEFENSE) shouldBe 5
            game.payLife(ChosenTarget.Permanent(battle)).error shouldBe null
            game.damageEvents().single().amount shouldBe 1
            game.state.getEntity(battle)!!.get<CountersComponent>()!!.getCount(CounterType.DEFENSE) shouldBe 4
            game.getLifeTotal(2) shouldBe 20
        }

        test("a noncreature land is illegal and does not spend the life cost") {
            val game = fixture().withLandsOnBattlefield(2, "Forest", 1).build()
            val before = game.state
            game.payLife(ChosenTarget.Permanent(game.findPermanent("Forest")!!)).error shouldNotBe null
            game.state shouldBe before
            game.getLifeTotal(1) shouldBe 20
        }

        test("a creature returned in response takes no damage and the paid life is not refunded") {
            val game = fixture().withCardOnBattlefield(2, "Llanowar Elves")
                .withCardInHand(2, "Unsummon").withLandsOnBattlefield(2, "Island", 1).build()
            val victim = game.findPermanent("Llanowar Elves")!!
            game.payLife(ChosenTarget.Permanent(victim)).error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpell(2, "Unsummon", victim).error shouldBe null
            game.damageEvents() shouldBe emptyList()
            game.isInHand(2, "Llanowar Elves") shouldBe true
            game.getLifeTotal(1) shouldBe 18
            game.getLifeTotal(2) shouldBe 20
        }
    }
}
