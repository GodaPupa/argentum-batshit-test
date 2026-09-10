package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Regression test for a decision-path enters-the-battlefield / legend-rule ordering bug.
 *
 * Ghalta, Stampede Tyrant (LCI) — {5}{G}{G}{G} Legendary Creature — Elder Dinosaur 12/12
 *   Trample
 *   When Ghalta enters, put any number of creature cards from your hand onto the battlefield.
 *
 * Scenario: cast Ghalta A. Its ETB (a decision-driven "put creatures from your hand" effect) puts a
 * second Ghalta B onto the battlefield. Ghalta B duplicates the legendary already present, so the
 * legend rule (state-based action, CR 704.5j) immediately puts one copy into the graveyard. Per
 * CR 603.6a the ability triggers the moment Ghalta B enters — before the legend-rule SBA removes it —
 * so Ghalta B's own ETB must still fire and let the player put a further creature onto the
 * battlefield.
 *
 * Before the fix, the entering-and-putting happened while resuming the ETB's "choose creatures"
 * decision ([com.wingedsheep.engine.handlers.actions.decision.SubmitDecisionHandler]), whose success
 * path checked SBAs *before* detecting the resolution's triggers — and, on the SBA pause, never
 * detected them at all — dropping Ghalta B's ETB entirely.
 * [com.wingedsheep.engine.handlers.actions.priority.PassPriorityHandler] already handled the
 * non-decision path correctly (detect pre-SBA, defer beneath the legend-rule continuation); this
 * test pins the decision path to the same behaviour.
 */
class GhaltaStampedeTyrantLegendRuleTest : ScenarioTestBase() {
    init {
        test("a just-entered duplicate Ghalta's ETB still fires even though the legend rule removes it") {
            val game = scenario()
                .withPlayers("Alice", "Bob")
                .withLandsOnBattlefield(1, "Forest", 8)
                .withCardsInHand(1, "Ghalta, Stampede Tyrant", 2)
                .withCardInHand(1, "Grizzly Bears")
                .withActivePlayer(1)
                .withPriorityPlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            // Cast the first Ghalta.
            game.castSpell(1, "Ghalta, Stampede Tyrant").error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()

            // Resolve the spell and its ETB trigger; resolution pauses on the ETB's
            // "choose any number of creature cards from your hand" decision.
            game.resolveStack()
            game.hasPendingDecision() shouldBe true

            // Capture the first Ghalta now, while it is the only copy on the battlefield.
            val firstGhalta = game.findPermanent("Ghalta, Stampede Tyrant")
            firstGhalta shouldNotBe null

            // Ghalta A's ETB: put ONLY the second Ghalta from hand onto the battlefield, keeping
            // Grizzly Bears in hand as the payload for Ghalta B's own ETB.
            val secondGhaltaInHand = game.findCardsInHand(1, "Ghalta, Stampede Tyrant").first()
            game.selectCards(listOf(secondGhaltaInHand)).error shouldBe null

            // Two Ghaltas are now on the battlefield → the legend rule pauses for a choice.
            game.hasPendingDecision() shouldBe true

            // Keep the first (older) Ghalta; the just-entered duplicate is put into the graveyard.
            game.selectCards(listOf(firstGhalta!!)).error shouldBe null

            // The removed Ghalta's ETB must still fire. Resolve until it pauses on its own
            // "choose creatures from your hand" decision.
            if (!game.hasPendingDecision()) game.resolveStack()
            game.hasPendingDecision() shouldBe true

            // Put Grizzly Bears onto the battlefield via the dead Ghalta's ETB.
            val bears = game.findCardsInHand(1, "Grizzly Bears").first()
            game.selectCards(listOf(bears)).error shouldBe null
            game.resolveStack()

            // Proof the ETB fired: Grizzly Bears is on the battlefield, exactly one Ghalta remains,
            // and the duplicate is in the graveyard.
            game.isOnBattlefield("Grizzly Bears") shouldBe true
            game.findPermanents("Ghalta, Stampede Tyrant").size shouldBe 1
            game.isInGraveyard(1, "Ghalta, Stampede Tyrant") shouldBe true
        }
    }
}
