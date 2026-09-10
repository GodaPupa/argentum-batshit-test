package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderZoneChoiceAskedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * CR 903.9a "leave-in-exile" branch.
 *
 * Liminal Hold exiles a commander; the commander's owner declines the command-zone diversion;
 * Liminal Hold later leaves the battlefield and its leave-battlefield trigger returns the
 * linked-exile card to the battlefield as normal.
 *
 * The sibling tests cover the other arms of the interaction:
 *  - `CommanderZoneRedirectTest` — `alwaysDivertToCommand = true` rewrites the destination
 *    synchronously, so Liminal Hold never even gets a linked-exile reference.
 *  - `CommanderZoneChoiceCheckTest` — the SBA pause / marker semantics in isolation.
 *  - `CommanderZoneMarkerStripTest` — the marker is cleared on every commander zone change.
 */
class LiminalHoldCommanderTest : FunSpec({

    test("commander left in exile under Liminal Hold returns to the battlefield when Hold leaves") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Plains" to 30), startingLife = 40)
        // GameTestDriver.initMirrorMatch doesn't expose `format`. Swap it in directly so the
        // 903.9a SBA actually fires for the commander-tagged creature below.
        driver.replaceState(driver.state.copy(format = Format.Commander()))

        val p1 = driver.player1
        val p2 = driver.player2
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Stand-in for P2's commander: any creature tagged with CommanderComponent. The SBA
        // is keyed off CommanderComponent + Format.Commander, not card identity.
        val commander = driver.putCreatureOnBattlefield(p2, "Glory Seeker")
        driver.replaceState(
            driver.state.updateEntity(commander) { c -> c.with(CommanderComponent(ownerId = p2)) }
        )

        // P1 casts Liminal Hold targeting P2's "commander".
        val liminalHold = driver.putCardInHand(p1, "Liminal Hold")
        driver.giveMana(p1, Color.WHITE, 4)
        driver.castSpell(p1, liminalHold)
        driver.bothPass() // resolve spell → Liminal Hold enters → ETB trigger asks for target
        driver.pendingDecision shouldNotBe null
        driver.submitTargetSelection(p1, listOf(commander))
        driver.bothPass() // resolve ETB → commander exiled → 903.9a SBA pauses

        // 903.9a should now be prompting the commander's owner.
        val choicePrompt = driver.pendingDecision
        choicePrompt.shouldBeInstanceOf<YesNoDecision>()
        choicePrompt.playerId shouldBe p2

        // Decline — leave the commander in exile.
        driver.submitYesNo(p2, false)

        // Commander stays in P2's exile with the asked-marker attached so the SBA stops
        // re-prompting on every iteration while it sits there.
        driver.state.getExile(p2) shouldContain commander
        driver.findPermanent(p2, "Glory Seeker") shouldBe null
        driver.state.getEntity(commander)!!
            .has<CommanderZoneChoiceAskedComponent>() shouldBe true

        // Liminal Hold is on the battlefield with its linked-exile reference still pointing
        // at the commander — that's the link that makes the return work.
        val liminalHoldId = driver.findPermanent(p1, "Liminal Hold")!!
        val linked = driver.state.getEntity(liminalHoldId)!!.get<LinkedExileComponent>()!!
        linked.exiledIds shouldContain commander

        // After the SBA prompt resolves, priority sits with P2 (the player who just made
        // the decision). Hand it back to P1 so P1 can cast Wipe Clean.
        if (driver.state.priorityPlayerId == p2) driver.passPriority(p2)

        // Destroy Liminal Hold; its leave-battlefield trigger returns the linked-exile card.
        val wipeClean = driver.putCardInHand(p1, "Wipe Clean")
        driver.giveMana(p1, Color.WHITE, 2)
        driver.castSpellWithTargets(p1, wipeClean, listOf(ChosenTarget.Permanent(liminalHoldId)))
        driver.bothPass() // resolve Wipe Clean → Liminal Hold dies → LTB triggers
        driver.bothPass() // resolve LTB → commander returns to the battlefield

        // Commander is back on P2's battlefield — not in the command zone — and the asked
        // marker was stripped by ZoneTransitionService on the exile → battlefield move.
        driver.findPermanent(p2, "Glory Seeker") shouldNotBe null
        driver.findPermanent(p1, "Liminal Hold") shouldBe null
        driver.state.getExile(p2).contains(commander) shouldBe false

        val returned = driver.state.getEntity(commander)!!
        returned.has<CommanderZoneChoiceAskedComponent>() shouldBe false
        returned.get<CommanderComponent>() shouldNotBe null
    }
})
