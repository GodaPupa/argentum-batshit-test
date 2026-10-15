package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CombatResolutionDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.afr.cards.Owlbear
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Prospective fixed mechanics fixtures only; not an official Position-1 sample or admission. */
class OwlbearTrampleScenarioTest : FunSpec({
    test("Owlbear exposes the frozen cost and existing trample keyword") {
        Owlbear.name shouldBe "Owlbear"
        Owlbear.manaCost shouldBe ManaCost.parse("{3}{G}{G}")
        Owlbear.typeLine.isCreature shouldBe true
        (Keyword.TRAMPLE in Owlbear.keywords) shouldBe true
    }

    test("blocked Owlbear assigns lethal to a two-toughness blocker and two excess damage to its defender") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(Owlbear)
        driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val attacker = requireNotNull(driver.activePlayer)
        val defender = if (attacker == driver.player1) driver.player2 else driver.player1
        // Direct placement isolates combat; the separate ETB fixture owns trigger qualification.
        val owlbear = driver.putPermanentOnBattlefield(attacker, "Owlbear")
        val blocker = driver.putPermanentOnBattlefield(defender, "Grizzly Bears")
        val lifeBefore = requireNotNull(driver.state.getEntity(defender)?.get<LifeTotalComponent>()).life

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(attacker, listOf(owlbear), defender).isSuccess shouldBe true
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
        driver.declareBlockers(defender, mapOf(blocker to listOf(owlbear))).isSuccess shouldBe true

        // Do not use an auto-resolving helper here: inspect the actual damage edges first.
        var passes = 0
        while (driver.state.pendingDecision !is CombatResolutionDecision && passes < 12) {
            require(driver.state.pendingDecision == null) { "Unexpected noncombat decision" }
            val result = driver.passPriority(requireNotNull(driver.priorityPlayer))
            require(result.isSuccess || result.isPaused) { "Priority action rejected" }
            passes++
        }
        val decision = requireNotNull(driver.state.pendingDecision as? CombatResolutionDecision)
        decision.playerId shouldBe attacker
        decision.edges.any { it.sourceId == owlbear && it.targetId == blocker } shouldBe true
        decision.edges.any { it.sourceId == owlbear && it.targetId == defender } shouldBe true
        driver.submitCombatDamage(mapOf(
            (owlbear to blocker) to 2,
            (owlbear to defender) to 2,
            (blocker to owlbear) to 2,
        )).isSuccess shouldBe true

        requireNotNull(driver.state.getEntity(defender)?.get<LifeTotalComponent>()).life shouldBe lifeBefore - 2
        driver.state.getZone(ZoneKey(defender, Zone.GRAVEYARD)).contains(blocker) shouldBe true
        driver.state.getZone(ZoneKey(attacker, Zone.BATTLEFIELD)).contains(owlbear) shouldBe true
    }
})
