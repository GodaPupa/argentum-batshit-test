package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Reproduces "Bug 13": when [Atomic Microsizer] (Artifact — Equipment, attached to
 * Chrome Companion) becomes a 0/0 Robot **artifact creature** via Tezzeret, Cruel
 * Captain's -7 emblem ("...put three +1/+1 counters on target artifact you control.
 * If it's not a creature, it becomes a 0/0 Robot artifact creature."), the engine
 * fails to un-attach the now-creature Equipment from Chrome Companion.
 *
 * Per CR 301.5c / 704.5p: a creature can't be an Equipment that's attached to
 * another permanent (and Microsizer lacks reconfigure), so as a state-based action
 * the Equipment should auto-unattach the moment it becomes a creature.
 *
 * Symptom in-game: Chrome Companion still gets the +1/+0 static buff (2/1 -> 3/1)
 * **and** the 3/3 Microsizer attacks alongside it for 6 combat damage. After the
 * fix: Microsizer is unattached, Chrome Companion drops back to base 2/1, and the
 * Microsizer is a standalone 3/3 artifact creature.
 *
 * Setup choice: rather than walking Tezzeret onto the battlefield, casting him,
 * activating -3 / +1 to ramp loyalty, we pre-attach Microsizer to Chrome Companion
 * (matching the post-equip board), seed Tezzeret with 7 loyalty counters, and
 * activate the -7 directly. The emblem's begin-of-combat trigger then targets
 * Microsizer, applying counters + `BecomeCreature` — the exact bug-triggering path.
 */
class EquipmentAsCreatureUnattachTest : ScenarioTestBase() {

    init {
        context("Equipment becomes a creature -> SBA must unattach (CR 301.5c / 704.5p)") {

            test("Atomic Microsizer attached to Chrome Companion auto-unattaches after Tezzeret's -7 emblem turns it into a 3/3 Robot artifact creature") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Tezzeret, Cruel Captain")
                    .withCardOnBattlefield(1, "Chrome Companion", summoningSickness = false)
                    .withCardOnBattlefield(1, "Atomic Microsizer", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val tezzeret = game.findPermanent("Tezzeret, Cruel Captain")!!
                val companion = game.findPermanent("Chrome Companion")!!
                val microsizer = game.findPermanent("Atomic Microsizer")!!

                // Seed Tezzeret with enough loyalty to activate -7.
                game.state = game.state.updateEntity(tezzeret) { c ->
                    c.with(CountersComponent().withAdded(CounterType.LOYALTY, 7))
                }

                // Attach Microsizer to Chrome Companion (post-equip board state).
                game.state = game.state
                    .updateEntity(microsizer) { c -> c.with(AttachedToComponent(companion)) }
                    .updateEntity(companion) { c ->
                        val existing = c.get<AttachmentsComponent>()?.attachedIds ?: emptyList()
                        c.with(AttachmentsComponent(existing + microsizer))
                    }

                // Sanity: static "Equipped creature gets +1/+0" already projects -> 3/1.
                run {
                    val projected = game.state.projectedState
                    withClue("Pre-emblem: Chrome Companion is buffed to 3/1 by Microsizer's static ability") {
                        projected.getPower(companion) shouldBe 3
                        projected.getToughness(companion) shouldBe 1
                    }
                    withClue("Pre-emblem: Microsizer is not (yet) a creature") {
                        projected.isCreature(microsizer) shouldBe false
                    }
                }

                // Activate -7 -> grants the global emblem trigger.
                val minus7 = cardRegistry.getCard("Tezzeret, Cruel Captain")!!
                    .script.activatedAbilities[2]
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = tezzeret,
                        abilityId = minus7.id
                    )
                ).error shouldBe null
                game.resolveStack()

                withClue("emblem is granted (Tezzeret dies to 0-loyalty SBA, emblem persists)") {
                    game.state.globalGrantedTriggeredAbilities.size shouldBe 1
                }

                // Walk into begin-combat -> emblem's "at the beginning of combat" trigger fires
                // and asks for a target. Microsizer is the only legal artifact target we control
                // (Chrome Companion is an artifact creature too — we have to point the emblem at
                // Microsizer to drive the bug).
                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.selectTargets(listOf(microsizer))
                game.resolveStack()

                val projected = game.state.projectedState

                // (1) Microsizer should no longer carry AttachedToComponent — SBA unattached it.
                withClue(
                    "Microsizer should be unattached (CR 704.5p) once it became a creature " +
                        "(observed AttachedToComponent.targetId = " +
                        "${game.state.getEntity(microsizer)?.get<AttachedToComponent>()?.targetId})"
                ) {
                    game.state.getEntity(microsizer)?.has<AttachedToComponent>() shouldBe false
                }

                // (2) Chrome Companion's reverse-link must be cleaned up.
                withClue(
                    "Chrome Companion's AttachmentsComponent should NOT list Microsizer " +
                        "(observed = " +
                        "${game.state.getEntity(companion)?.get<AttachmentsComponent>()?.attachedIds})"
                ) {
                    val attached = game.state.getEntity(companion)
                        ?.get<AttachmentsComponent>()?.attachedIds ?: emptyList()
                    attached.contains(microsizer) shouldBe false
                }

                // (3) With the Equipment gone, Chrome Companion drops back to base 2/1.
                withClue(
                    "Chrome Companion should lose Microsizer's +1/+0 buff and project to 2 power " +
                        "(observed power = ${projected.getPower(companion)})"
                ) {
                    projected.getPower(companion) shouldBe 2
                }

                // (4) Microsizer is now a standalone 3/3 artifact creature (base 0/0 + three
                //     +1/+1 counters from the emblem), retaining its artifact type.
                withClue(
                    "Microsizer should project to a 3/3 creature retaining the artifact type " +
                        "(observed power=${projected.getPower(microsizer)}, " +
                        "isCreature=${projected.isCreature(microsizer)}, " +
                        "hasType ARTIFACT=${projected.hasType(microsizer, "ARTIFACT")})"
                ) {
                    projected.isCreature(microsizer) shouldBe true
                    projected.hasType(microsizer, "ARTIFACT") shouldBe true
                    projected.getPower(microsizer) shouldBe 3
                }
            }

            test("after the Equipment animates and unattaches, combat deals separate (not doubled) damage") {
                // End-to-end version of the bug as it was reported ("Artifact Equipment
                // attacks, gives separate damage"). The state-based check above proves the
                // Equipment unattaches; this plays combat all the way to damage to prove the
                // observable symptom is gone: Chrome Companion no longer carries Microsizer's
                // +1/+0, and Microsizer attacks on its own as a 3/3. Bob should take exactly
                // 2 (Companion) + 3 (Microsizer) = 5, NOT 3 + 3 = 6 (the bug, where the
                // Equipment both buffed its host AND attacked).
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "Tezzeret, Cruel Captain")
                    .withCardOnBattlefield(1, "Chrome Companion", summoningSickness = false)
                    .withCardOnBattlefield(1, "Atomic Microsizer", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val tezzeret = game.findPermanent("Tezzeret, Cruel Captain")!!
                val companion = game.findPermanent("Chrome Companion")!!
                val microsizer = game.findPermanent("Atomic Microsizer")!!

                game.state = game.state.updateEntity(tezzeret) { c ->
                    c.with(CountersComponent().withAdded(CounterType.LOYALTY, 7))
                }
                game.state = game.state
                    .updateEntity(microsizer) { c -> c.with(AttachedToComponent(companion)) }
                    .updateEntity(companion) { c ->
                        val existing = c.get<AttachmentsComponent>()?.attachedIds ?: emptyList()
                        c.with(AttachmentsComponent(existing + microsizer))
                    }

                val minus7 = cardRegistry.getCard("Tezzeret, Cruel Captain")!!
                    .script.activatedAbilities[2]
                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = tezzeret,
                        abilityId = minus7.id
                    )
                ).error shouldBe null
                game.resolveStack()

                // Begin combat: the emblem trigger animates Microsizer; the SBA unattaches it.
                game.passUntilPhase(Phase.COMBAT, Step.BEGIN_COMBAT)
                game.selectTargets(listOf(microsizer))
                game.resolveStack()

                withClue("precondition: Microsizer unattached before attackers are declared") {
                    game.state.getEntity(microsizer)?.has<AttachedToComponent>() shouldBe false
                }

                // Both attack: the host (now base 2/1) and the standalone 3/3 Microsizer.
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(
                    mapOf("Chrome Companion" to 2, "Atomic Microsizer" to 2)
                ).error shouldBe null

                game.passUntilPhase(Phase.COMBAT, Step.COMBAT_DAMAGE)
                game.resolveStack()
                if (game.state.pendingDecision != null) {
                    game.submitDefaultCombatDamage()
                    game.resolveStack()
                }

                withClue(
                    "Bob should take 2 (Companion, buff gone) + 3 (Microsizer) = 5, not 6 " +
                        "(observed life = ${game.getLifeTotal(2)})"
                ) {
                    game.getLifeTotal(2) shouldBe 15
                }
            }
        }
    }
}
