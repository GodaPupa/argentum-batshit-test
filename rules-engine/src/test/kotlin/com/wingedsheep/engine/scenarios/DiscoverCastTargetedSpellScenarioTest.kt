package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.identity.PlayWithoutPayingCostComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Regression test: a discover (CR 701.57) free-cast of a non-modal *targeted* spell must pause
 * for target selection.
 *
 * The discover resumer synthesizes a `CastSpell` with no targets and invoked
 * `CastSpellHandler.execute` directly — bypassing `validate`, which is what normally rejects a
 * targeted spell with `targets = []`. A discovered Zombify therefore went on the stack
 * untargeted and resolved doing nothing. The fix surfaces the same `ChooseTargetsDecision` the
 * synthesized-free-cast executor ([com.wingedsheep.sdk.scripting.effects.CastFromCollectionWithoutPayingCostEffect])
 * uses; if a required slot has no legal targets, the cast can't initiate (CR 601.2c) and the
 * discovered card goes to the controller's hand instead.
 *
 * Cascade (CR 702.85) shares the fixed code path; the last test pins it there too.
 */
class DiscoverCastTargetedSpellScenarioTest : ScenarioTestBase() {

    init {
        context("discover free-casting a targeted spell") {

            test("Trumpeting Carnosaur's discover 5 hitting Zombify prompts for a target and reanimates it") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Trumpeting Carnosaur")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    // Top of library: Zombify (mana value 4 <= 5) is the discover hit.
                    .withCardInLibrary(1, "Zombify")
                    .withCardInLibrary(1, "Mountain")
                    .build()

                val cast = game.castSpell(1, "Trumpeting Carnosaur")
                withClue("Casting Trumpeting Carnosaur should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack() // Carnosaur resolves; its ETB discover 5 pauses on cast-or-hand.

                val yesNo = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                val chose = game.submitDecision(YesNoResponse(yesNo.id, choice = true))
                withClue("Choosing to cast the discovered card should succeed: ${chose.error}") {
                    chose.error shouldBe null
                }

                // The free cast must pause for Zombify's target — the bug was putting it on the
                // stack untargeted, so it resolved doing nothing.
                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                withClue("The creature card in the graveyard must be offered as a legal target") {
                    targetDecision.legalTargets[0].orEmpty() shouldContain bears
                }

                val picked = game.submitDecision(TargetsResponse(targetDecision.id, mapOf(0 to listOf(bears))))
                withClue("Submitting the target should succeed: ${picked.error}") {
                    picked.error shouldBe null
                }
                game.resolveStack() // Zombify resolves.

                withClue("Zombify returned the targeted creature card to the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
                withClue("The cast Zombify went to the graveyard") {
                    game.isInGraveyard(1, "Zombify") shouldBe true
                }
            }

            test("discovering a targeted spell with no legal targets puts it into hand instead") {
                // No creature card in any graveyard — Zombify has no legal target, so the cast
                // can't initiate (CR 601.2c) and the discovered card falls back to hand.
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Trumpeting Carnosaur")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    .withCardInLibrary(1, "Zombify")
                    .withCardInLibrary(1, "Mountain")
                    .build()

                val cast = game.castSpell(1, "Trumpeting Carnosaur")
                withClue("Casting Trumpeting Carnosaur should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                val yesNo = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                val chose = game.submitDecision(YesNoResponse(yesNo.id, choice = true))
                withClue("Choosing to cast should succeed even when the cast can't initiate: ${chose.error}") {
                    chose.error shouldBe null
                }

                withClue("With no legal target the discovered Zombify goes to hand") {
                    game.isInHand(1, "Zombify") shouldBe true
                }
                withClue("Nothing named Zombify is on the stack or resolved") {
                    game.isInGraveyard(1, "Zombify") shouldBe false
                }

                // The abandoned cast must not leak its free-cast grant: the stamp is zone-agnostic
                // and lives until end-of-turn cleanup, so a leftover one would let the player cast
                // Zombify from hand for {0} later this turn.
                val zombify = game.findCardsInHand(1, "Zombify").single()
                withClue("The card in hand carries no free-cast stamp") {
                    game.state.getEntity(zombify)?.has<PlayWithoutPayingCostComponent>() shouldBe false
                }
                withClue("No may-play permission is left covering the card") {
                    game.state.mayPlayPermissions.none { zombify in it.cardIds } shouldBe true
                }
            }

            test("cascade hitting a targeted spell prompts for a target too") {
                // Quandrix, the Proof — {4}{G}{U}, Cascade. Zombify (mana value 4 < 6) is the
                // cascade hit. (Not Annoyed Altisaur: its bare CASCADE keyword is display-only.)
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Quandrix, the Proof")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Zombify")
                    .withCardInLibrary(1, "Mountain")
                    .build()

                val cast = game.castSpell(1, "Quandrix, the Proof")
                withClue("Casting Quandrix, the Proof should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack() // Cascade trigger resolves and pauses on may-cast.

                val yesNo = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                val chose = game.submitDecision(YesNoResponse(yesNo.id, choice = true))
                withClue("Choosing to cast the cascade hit should succeed: ${chose.error}") {
                    chose.error shouldBe null
                }

                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                val picked = game.submitDecision(TargetsResponse(targetDecision.id, mapOf(0 to listOf(bears))))
                withClue("Submitting the target should succeed: ${picked.error}") {
                    picked.error shouldBe null
                }
                game.resolveStack() // Zombify, then Quandrix, resolve.

                withClue("Zombify returned the targeted creature card to the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }
        }
    }
}
