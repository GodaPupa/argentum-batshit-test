package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.jud.cards.PrismaticStrands
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PrismaticStrandsScenarioTest : FunSpec({
    val white = card("Strands Fixture White") {
        manaCost = "{W}"
        typeLine = "Creature — Human"
        power = 1
        toughness = 1
    }
    val red = card("Strands Fixture Red") {
        manaCost = "{R}"
        typeLine = "Creature — Elemental"
        power = 3
        toughness = 3
        keywords(Keyword.LIFELINK)
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(PrismaticStrands, white, red))
        initMirrorMatch(Deck.of("Plains" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun choose(d: GameTestDriver, color: Color) {
        d.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
        val pending = d.pendingDecision as ChooseColorDecision
        pending.playerId shouldBe d.player1
        d.submitDecision(d.player1, ColorChosenResponse(pending.id, color)).isSuccess shouldBe true
    }
    fun castShield(d: GameTestDriver, color: Color = Color.RED): EntityId {
        val strands = d.putCardInHand(d.player1, PrismaticStrands.name)
        d.giveMana(d.player1, Color.WHITE, 1)
        d.giveColorlessMana(d.player1, 2)
        d.castSpell(d.player1, strands).isSuccess shouldBe true
        d.pendingDecision shouldBe null // color is a resolution choice, not a cast target.
        d.bothPass()
        choose(d, color)
        return strands
    }
    fun flashback(d: GameTestDriver, strands: EntityId, tapped: List<EntityId>) = d.submit(CastSpell(
        playerId = d.player1, cardId = strands, useAlternativeCost = true,
        alternativeCostType = AlternativeCostType.FLASHBACK,
        additionalCostPayment = AdditionalCostPayment(tappedPermanents = tapped),
        paymentStrategy = PaymentStrategy.AutoPay))
    fun bolt(d: GameTestDriver, target: EntityId, player: EntityId = d.player1) {
        val spell = d.putCardInHand(player, "Lightning Bolt")
        d.giveMana(player, Color.RED, 1)
        d.castSpell(player, spell, listOf(target)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
    }

    test("normal resolution protects both players and creatures from actual red spells throughout the turn") {
        val d = fixture()
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val strands = castShield(d)
        d.state.getGraveyard(d.player1).contains(strands) shouldBe true
        for (target in listOf(d.player1, d.player2, victim)) bolt(d, target)
        d.getLifeTotal(d.player1) shouldBe 20
        d.getLifeTotal(d.player2) shouldBe 20
        d.state.getEntity(victim)!!.get<DamageComponent>() shouldBe null
        d.events.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
    }
    test("an unchosen source color is not prevented") {
        val d = fixture()
        castShield(d, Color.BLUE)
        bolt(d, d.player2)
        d.getLifeTotal(d.player2) shouldBe 17
        d.events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 3
    }
    test("actual combat prevents only chosen-color attackers and grants no lifelink for prevented damage") {
        val d = fixture()
        val redAttacker = d.putCreatureOnBattlefield(d.player1, red.name)
        val greenAttacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.removeSummoningSickness(redAttacker)
        d.removeSummoningSickness(greenAttacker)
        castShield(d)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(d.player1, listOf(redAttacker, greenAttacker), d.player2).isSuccess shouldBe true
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        d.declareBlockers(d.player2, emptyMap()).isSuccess shouldBe true
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.getLifeTotal(d.player2) shouldBe 18
        d.getLifeTotal(d.player1) shouldBe 20
        d.events.filterIsInstance<DamageDealtEvent>().map { it.sourceId to it.amount } shouldBe listOf(greenAttacker to 2)
    }
    test("unpreventable actual combat passes through the chosen-color group shield") {
        val d = fixture()
        val attacker = d.putCreatureOnBattlefield(d.player1, red.name)
        d.removeSummoningSickness(attacker)
        castShield(d)
        d.replaceState(d.state.copy(damageCantBePreventedThisTurn = true))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(d.player1, listOf(attacker), d.player2).isSuccess shouldBe true
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        d.declareBlockers(d.player2, emptyMap()).isSuccess shouldBe true
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.getLifeTotal(d.player2) shouldBe 17
        d.getLifeTotal(d.player1) shouldBe 23
        d.events.filterIsInstance<DamageDealtEvent>().single().amount shouldBe 3
    }
    test("flashback taps a newly entered white creature without mana, persists its choice, and exiles the spell") {
        val d = fixture()
        val strands = d.putCardInGraveyard(d.player1, PrismaticStrands.name)
        val payer = d.putCreatureOnBattlefield(d.player1, white.name)
        d.state.getEntity(payer)!!.has<SummoningSicknessComponent>() shouldBe true
        flashback(d, strands, listOf(payer)).isSuccess shouldBe true
        d.isTapped(payer) shouldBe true
        d.bothPass()
        d.pendingDecision.shouldBeInstanceOf<ChooseColorDecision>()
        d.replaceState(SerializationTestSupport.roundTrip(d.state))
        choose(d, Color.RED)
        d.state.getZone(ZoneKey(d.player1, Zone.EXILE)).contains(strands) shouldBe true
        d.state.getGraveyard(d.player1).contains(strands) shouldBe false
        d.replaceState(SerializationTestSupport.roundTrip(d.state))
        bolt(d, d.player2)
        d.getLifeTotal(d.player2) shouldBe 20
    }
    test("flashback rejects missing, green, opposing, noncreature or already tapped payment atomically") {
        val d = fixture()
        val strands = d.putCardInGraveyard(d.player1, PrismaticStrands.name)
        val green = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val opposing = d.putCreatureOnBattlefield(d.player2, white.name)
        val land = d.putLandOnBattlefield(d.player1, "Plains")
        val tapped = d.putCreatureOnBattlefield(d.player1, white.name)
        d.tapPermanent(tapped)
        for (payment in listOf(emptyList(), listOf(green), listOf(opposing), listOf(land), listOf(tapped))) {
            val before = d.state
            flashback(d, strands, payment).isSuccess shouldBe false
            d.state shouldBe before
        }
    }
    test("countering a flashback spell exiles it and creates neither color choice nor shield") {
        val d = fixture()
        val strands = d.putCardInGraveyard(d.player1, PrismaticStrands.name)
        val payer = d.putCreatureOnBattlefield(d.player1, white.name)
        flashback(d, strands, listOf(payer)).isSuccess shouldBe true
        val counter = d.putCardInHand(d.player1, "Counterspell")
        d.giveMana(d.player1, Color.BLUE, 2)
        d.castSpell(d.player1, counter, listOf(strands)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
        d.pendingDecision shouldBe null
        d.state.getZone(ZoneKey(d.player1, Zone.EXILE)).contains(strands) shouldBe true
        d.state.floatingEffects shouldBe emptyList()
        bolt(d, d.player2)
        d.getLifeTotal(d.player2) shouldBe 17
    }
    test("the shield expires at turn end and does not stop next-turn red damage") {
        val d = fixture()
        castShield(d)
        d.passPriorityUntil(Step.UPKEEP)
        d.activePlayer shouldBe d.player2
        bolt(d, d.player1, player = d.player2)
        d.getLifeTotal(d.player1) shouldBe 17
    }
})
