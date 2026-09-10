package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.identity.RingBearerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Bug: "Meneldor — Bilbo's Ring still attached." When Meneldor, Swift Savior deals combat damage
 * to a player it exiles the equipped creature and immediately returns it. The returning permanent
 * is a NEW object (CR 400.7), so the Equipment/Aura that was on it must fall off: an Equipment
 * becomes unattached and stays on the battlefield (CR 704.5n), an Aura is put into its owner's
 * graveyard (CR 704.5m).
 *
 * The earlier direct-`moveToZone` repro bypassed the state-based actions, so it couldn't catch this;
 * this drives the *real* pipeline (a combat-damage trigger blinks the host through the stack, then
 * priority is passed so the SBA runs). It is a genuine regression: `moveToZone` reuses the host's
 * EntityId across the exile→battlefield round-trip, so the unattached-permanents SBA — which keyed
 * purely off the host id still being on the battlefield — saw the returned same-id object and left
 * the attachment in place.
 */
class MeneldorBilbosRingDetachScenarioTest : ScenarioTestBase() {

    /**
     * Meneldor attacks alone, connects, and its combat-damage trigger blinks [host]. A
     * battlefield-tied marker on [host] proves the blink really happened (CR 400.7 strips it).
     * Returns the host's id after the blink (reused, but a new object).
     */
    private fun TestGame.meneldorBlinks(host: EntityId): EntityId {
        // Stamp a battlefield-tied marker so we can confirm the host blinked (and didn't no-op).
        state = state.updateEntity(host) { c -> c.with(RingBearerComponent(ownerId = player1Id)) }

        declareAttackers(mapOf("Meneldor, Swift Savior" to 2)).error shouldBe null

        // Advance to combat damage and pass priority until Meneldor's combat-damage trigger pauses
        // for its "up to one target creature you own" choice.
        passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
        var iterations = 0
        while (state.pendingDecision == null && state.step != Step.POSTCOMBAT_MAIN && iterations++ < 20) {
            passPriority()
        }
        selectTargets(listOf(host))
        resolveStack()
        // A priority pass so the unattached-permanents SBA runs.
        passPriority()

        val after = findPermanent("Grizzly Bears")
        withClue("the host actually blinked: CR 400.7 strips the battlefield-tied marker") {
            (after != null && state.getEntity(after)?.has<RingBearerComponent>() == false) shouldBe true
        }
        return after!!
    }

    init {
        context("Meneldor blinks an attached creature -> the attachment falls off (CR 704.5m-n)") {

            test("Equipment (Bilbo's Ring) becomes unattached and stays on the battlefield") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Meneldor, Swift Savior", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(1, "Bilbo's Ring")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val host = game.findPermanent("Grizzly Bears")!!
                val ring = game.findPermanent("Bilbo's Ring")!!
                game.state = game.state
                    .updateEntity(ring) { c -> c.with(AttachedToComponent(host)) }
                    .updateEntity(host) { c -> c.with(AttachmentsComponent(listOf(ring))) }

                val returned = game.meneldorBlinks(host)

                withClue("Bilbo's Ring is unattached after its host left the battlefield") {
                    game.state.getEntity(ring)?.has<AttachedToComponent>() shouldBe false
                }
                withClue("the Equipment stays on the battlefield (CR 704.5n)") {
                    game.isOnBattlefield("Bilbo's Ring") shouldBe true
                }
                withClue("the returned creature is not silently re-equipped") {
                    val reattached = game.state.getEntity(returned)
                        ?.get<AttachmentsComponent>()?.attachedIds ?: emptyList()
                    reattached.contains(ring) shouldBe false
                }
            }

            test("Aura (Unholy Strength) is put into its owner's graveyard") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Meneldor, Swift Savior", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(1, "Unholy Strength")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val host = game.findPermanent("Grizzly Bears")!!
                val aura = game.findPermanent("Unholy Strength")!!
                game.state = game.state
                    .updateEntity(aura) { c -> c.with(AttachedToComponent(host)) }
                    .updateEntity(host) { c -> c.with(AttachmentsComponent(listOf(aura))) }

                game.meneldorBlinks(host)

                withClue("the Aura's host left, so it goes to the graveyard (CR 704.5m)") {
                    game.isOnBattlefield("Unholy Strength") shouldBe false
                    game.isInGraveyard(1, "Unholy Strength") shouldBe true
                }
            }
        }
    }
}
