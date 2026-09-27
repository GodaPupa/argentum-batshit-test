package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.lci.cards.FanaticalOffering
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Fanatical Offering (LCI #105) — {1}{B} Instant
 *
 * "As an additional cost to cast this spell, sacrifice an artifact or creature.
 *  Draw two cards and create a Map token."
 *
 * Tests:
 *  1. Sacrificing a creature as the additional cost draws two cards and creates a Map token.
 *  2. Sacrificing an artifact as the additional cost draws two cards and creates a Map token.
 */
class FanaticalOfferingScenarioTest : FunSpec({

    fun newDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCards(PredefinedTokens.allTokens)
        driver.registerCard(FanaticalOffering)
        // 20 Swamps to pay {1}{B} and supply the library for drawing
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    fun driverWithProducedMap(): GameTestDriver {
        val driver = newDriver()
        val me = driver.activePlayer!!
        val sacrifice = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
        val offering = driver.putCardInHand(me, "Fanatical Offering")
        driver.giveColorlessMana(me, 1)
        driver.giveMana(me, Color.BLACK, 1)
        driver.submitSuccess(CastSpell(
            me, offering,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrifice)),
            paymentStrategy = PaymentStrategy.FromPool
        ))
        driver.bothPass().isSuccess shouldBe true
        driver.state.stack.isEmpty() shouldBe true
        driver.state.pendingDecision shouldBe null
        checkNotNull(driver.findPermanent(me, "Map"))
        return driver
    }

    fun mapAction(driver: GameTestDriver, creature: EntityId): ActivateAbility {
        val me = driver.activePlayer!!
        return ActivateAbility(
            me, driver.findPermanent(me, "Map")!!, PredefinedTokens.Map.activatedAbilities.first().id,
            targets = listOf(ChosenTarget.Permanent(creature)), paymentStrategy = PaymentStrategy.FromPool
        )
    }

    fun counters(driver: GameTestDriver, creature: EntityId): Int =
        driver.state.getEntity(creature)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    test("sacrificing a creature draws two cards and creates a Map token") {
        val driver = newDriver()
        val me = driver.activePlayer!!

        // Put a creature on the battlefield to sacrifice
        val bear = driver.putCreatureOnBattlefield(me, "Grizzly Bears")

        // Record hand size before casting (spell will leave hand, then 2 drawn)
        val spell = driver.putCardInHand(me, "Fanatical Offering")
        val handBefore = driver.getHandSize(me)

        driver.giveColorlessMana(me, 1)
        driver.giveMana(me, Color.BLACK, 1)

        val result = driver.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(bear)),
                paymentStrategy = PaymentStrategy.FromPool
            )
        )
        result.isSuccess shouldBe true
        driver.bothPass()

        // The sacrificed creature is gone from the battlefield
        driver.findPermanent(me, "Grizzly Bears") shouldBe null

        // Drew two cards: hand went from handBefore (includes Fanatical Offering) to
        // handBefore - 1 (spell cast) + 2 (draw) = handBefore + 1
        driver.getHandSize(me) shouldBe handBefore + 1

        // A Map token was created on the caster's battlefield
        driver.findPermanent(me, "Map") shouldNotBe null
    }

    test("sacrificing an artifact draws two cards and creates a Map token") {
        val driver = newDriver()
        val me = driver.activePlayer!!

        // Put an artifact creature on the battlefield to sacrifice (proves the artifact branch)
        val artifact = driver.putCreatureOnBattlefield(me, "Artifact Creature")

        val spell = driver.putCardInHand(me, "Fanatical Offering")
        val handBefore = driver.getHandSize(me)

        driver.giveColorlessMana(me, 1)
        driver.giveMana(me, Color.BLACK, 1)

        val result = driver.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(artifact)),
                paymentStrategy = PaymentStrategy.FromPool
            )
        )
        result.isSuccess shouldBe true
        driver.bothPass()

        // The sacrificed artifact creature is gone from the battlefield
        driver.findPermanent(me, "Artifact Creature") shouldBe null

        // Drew two cards
        driver.getHandSize(me) shouldBe handBefore + 1

        // A Map token was created
        driver.findPermanent(me, "Map") shouldNotBe null
    }

    test("produced Map pays mana tap and sacrifice before an own creature explores a land") {
        val driver = driverWithProducedMap()
        val me = driver.activePlayer!!
        val creature = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
        val land = driver.putCardOnTopOfLibrary(me, "Forest")
        val handBefore = driver.getHand(me).toSet()
        driver.giveColorlessMana(me, 1)
        val action = mapAction(driver, creature)
        driver.submitSuccess(action)
        driver.state.getEntity(me)!!.get<ManaPoolComponent>()!!.total shouldBe 0
        driver.findPermanent(me, "Map") shouldBe null
        driver.getHand(me).toSet() shouldBe handBefore
        driver.state.getZone(ZoneKey(me, Zone.LIBRARY)).first() shouldBe land
        counters(driver, creature) shouldBe 0
        driver.state.stack.size shouldBe 1

        val beforeRepeat = driver.state
        val repeat = driver.submit(action)
        repeat.isSuccess shouldBe false
        repeat.isPaused shouldBe false
        repeat.newState shouldBe beforeRepeat
        driver.bothPass().isSuccess shouldBe true
        driver.getHand(me).toSet() shouldBe handBefore + land
        counters(driver, creature) shouldBe 0
        driver.state.pendingDecision shouldBe null
        driver.state.stack.isEmpty() shouldBe true
    }

    for (mill in listOf(false, true)) {
        test("produced Map explores a nonland with keep on top=${!mill}") {
            val driver = driverWithProducedMap()
            val me = driver.activePlayer!!
            val creature = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
            val revealed = driver.putCardOnTopOfLibrary(me, "Grizzly Bears")
            val handBefore = driver.getHand(me).toSet()
            driver.giveColorlessMana(me, 1)
            driver.submitSuccess(mapAction(driver, creature))
            driver.bothPass()
            driver.state.pendingDecision shouldNotBe null
            counters(driver, creature) shouldBe 1
            driver.submitYesNo(me, !mill).isSuccess shouldBe true
            driver.state.pendingDecision shouldBe null
            driver.state.stack.isEmpty() shouldBe true
            driver.getHand(me).toSet() shouldBe handBefore
            (revealed in driver.state.getZone(ZoneKey(me, Zone.GRAVEYARD))) shouldBe mill
            (driver.state.getZone(ZoneKey(me, Zone.LIBRARY)).first() == revealed) shouldBe !mill
            counters(driver, creature) shouldBe 1
            driver.findPermanent(me, "Map") shouldBe null
        }
    }

    test("produced Map exploring an empty library gives one counter without drawing or a choice") {
        val driver = driverWithProducedMap()
        val me = driver.activePlayer!!
        val creature = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
        driver.state.getZone(ZoneKey(me, Zone.LIBRARY)).toList().forEach(driver::moveToGraveyard)
        val handBefore = driver.getHand(me).toSet()
        driver.giveColorlessMana(me, 1)
        driver.submitSuccess(mapAction(driver, creature))
        driver.bothPass().isSuccess shouldBe true
        counters(driver, creature) shouldBe 1
        driver.getHand(me).toSet() shouldBe handBefore
        driver.state.getZone(ZoneKey(me, Zone.LIBRARY)).isEmpty() shouldBe true
        driver.state.pendingDecision shouldBe null
        driver.state.stack.isEmpty() shouldBe true
        driver.state.gameOver shouldBe false
    }

    for (failure in listOf("unfunded", "tapped", "opponent creature", "outside main", "nonempty stack")) {
        test("produced Map rejects $failure without paying costs or exploring") {
            val driver = driverWithProducedMap()
            val me = driver.activePlayer!!
            val creature = driver.putCreatureOnBattlefield(
                if (failure == "opponent creature") driver.getOpponent(me) else me, "Grizzly Bears"
            )
            if (failure == "outside main") driver.passPriorityUntil(Step.BEGIN_COMBAT)
            if (failure == "nonempty stack") {
                val sacrifice = driver.putCreatureOnBattlefield(me, "Grizzly Bears")
                val offering = driver.putCardInHand(me, "Fanatical Offering")
                driver.giveColorlessMana(me, 1)
                driver.giveMana(me, Color.BLACK, 1)
                driver.submitSuccess(CastSpell(
                    me, offering,
                    additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrifice)),
                    paymentStrategy = PaymentStrategy.FromPool
                ))
                driver.state.stack.size shouldBe 1
            }
            if (failure != "unfunded") driver.giveColorlessMana(me, 1)
            if (failure == "tapped") driver.tapPermanent(driver.findPermanent(me, "Map")!!)
            val before = driver.state
            val result = driver.submit(mapAction(driver, creature))
            result.isSuccess shouldBe false
            result.isPaused shouldBe false
            result.newState shouldBe before
            driver.state shouldBe before
        }
    }
})
