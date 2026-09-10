package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.scg.cards.CarrionFeeder
import com.wingedsheep.mtg.sets.definitions.shm.cards.SafeholdElite
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Deterministic rules proofs for the Project X sacrifice engine. */
class ProjectXComboScenarioTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCard(SafeholdElite)
    }

    fun GameTestDriver.counter(id: EntityId, type: CounterType): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(type) ?: 0

    fun GameTestDriver.sacrificeToFeeder(player: EntityId, feeder: EntityId, victim: EntityId) =
        submit(ActivateAbility(player, feeder, CarrionFeeder.activatedAbilities.single().id,
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(victim))))

    /** Resolve one loop, choosing the returned Elite for Ivy and the opponent for Noble. */
    fun GameTestDriver.finishCycle(player: EntityId, opponent: EntityId) {
        var safety = 0
        while ((stackSize > 0 || pendingDecision != null) && safety++ < 30) {
            // A lethal Noble trigger ends the game immediately; remaining stack objects do not
            // resolve and no player can take another priority action.
            if (getLifeTotal(player) <= 0 || getLifeTotal(opponent) <= 0) break
            val decision = pendingDecision
            if (decision is ChooseTargetsDecision) {
                val elite = findPermanent(player, "Safehold Elite")
                val legal = decision.legalTargets[0].orEmpty()
                val chosen = when {
                    elite != null && elite in legal -> elite
                    opponent in legal -> opponent
                    else -> legal.first()
                }
                submitTargetSelection(decision.playerId, listOf(chosen))
            } else {
                bothPass()
            }
        }
        safety shouldBe 30.coerceAtMost(safety)
    }

    test("Feeder Elite and Denizen repeat, cancel opposing counters, and grow Feeder") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        val opponent = if (player == d.player1) d.player2 else d.player1
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val feeder = d.putCreatureOnBattlefield(player, "Carrion Feeder")
        d.putCreatureOnBattlefield(player, "Ivy Lane Denizen")
        var elite = d.putCreatureOnBattlefield(player, "Safehold Elite")

        repeat(3) {
            d.sacrificeToFeeder(player, feeder, elite).isSuccess shouldBe true
            d.finishCycle(player, opponent)
            elite = d.findPermanent(player, "Safehold Elite")!!
            d.counter(elite, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
            d.counter(elite, CounterType.MINUS_ONE_MINUS_ONE) shouldBe 0
        }
        d.counter(feeder, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
    }

    test("Falkenrath Noble makes each iteration deterministic drain") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), startingLife = 3, skipMulligans = true)
        val player = d.activePlayer!!
        val opponent = if (player == d.player1) d.player2 else d.player1
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val feeder = d.putCreatureOnBattlefield(player, "Carrion Feeder")
        d.putCreatureOnBattlefield(player, "Ivy Lane Denizen")
        d.putCreatureOnBattlefield(player, "Falkenrath Noble")
        var elite = d.putCreatureOnBattlefield(player, "Safehold Elite")

        repeat(3) {
            val sacrifice = d.sacrificeToFeeder(player, feeder, elite)
            // Paying the sacrifice cost creates Noble's targeted death trigger, so the accepted
            // command pauses for that target rather than returning a terminal success result.
            sacrifice.error shouldBe null
            d.finishCycle(player, opponent)
            if (it < 2) elite = d.findPermanent(player, "Safehold Elite")!!
        }
        d.getLifeTotal(opponent) shouldBe 0
        d.getLifeTotal(player) shouldBe 6
    }

    test("Essence Warden makes each returned Elite gain life") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        val opponent = if (player == d.player1) d.player2 else d.player1
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val feeder = d.putCreatureOnBattlefield(player, "Carrion Feeder")
        d.putCreatureOnBattlefield(player, "Ivy Lane Denizen")
        d.putCreatureOnBattlefield(player, "Essence Warden")
        var elite = d.putCreatureOnBattlefield(player, "Safehold Elite")
        val life = d.getLifeTotal(player)

        repeat(3) {
            d.sacrificeToFeeder(player, feeder, elite).isSuccess shouldBe true
            d.finishCycle(player, opponent)
            elite = d.findPermanent(player, "Safehold Elite")!!
        }
        d.getLifeTotal(player) shouldBe life + 3
    }

    test("Wirewood Herald can tutor the canonical Safehold Elite Elf role") {
        val d = driver()
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
        val player = d.activePlayer!!
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val herald = d.putCreatureOnBattlefield(player, "Wirewood Herald")
        val elite = d.putCardOnTopOfLibrary(player, "Safehold Elite")
        val bolt = d.putCardInHand(player, "Lightning Bolt")
        d.giveMana(player, Color.RED, 1)
        d.castSpell(player, bolt, listOf(herald)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        d.submitYesNo(player, true)
        d.submitCardSelection(player, listOf(elite))
        d.bothPass()
        (d.findCardInHand(player, "Safehold Elite") != null) shouldBe true
    }
})
