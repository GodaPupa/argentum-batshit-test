package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for four Tarkir: Dragonstorm cards:
 *  - Ringing Strike Mastery (#53): Aura — tap on enter, doesn't untap, "{5}: Untap this creature."
 *  - Unsparing Boltcaster (#130): ETB deals 5 damage to a damaged opponent creature.
 *  - Shocking Sharpshooter (#121): another creature you control enters → 1 damage to target opponent.
 *  - Dragonstorm Forecaster (#43): {2},{T} tutor for Dragonstorm Globe or Boulderborn Dragon.
 *
 * All four reuse existing SDK primitives (granted untap ability, was-dealt-damage target filter,
 * OtherCreatureEnters trigger, named-card library search) — no new engine mechanics.
 */
class TdmGroupBNoAltScenarioTest : ScenarioTestBase() {

    init {
        context("Ringing Strike Mastery") {

            test("ETB taps the enchanted creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Ringing Strike Mastery")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val creature = game.findPermanent("Grizzly Bears")!!
                val cast = game.castSpell(1, "Ringing Strike Mastery", creature)
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()
                if (game.state.stack.isNotEmpty()) game.resolveStack()

                withClue("Grizzly Bears should be tapped by the ETB trigger") {
                    game.state.getEntity(creature)!!.has<TappedComponent>() shouldBe true
                }
            }

            test("enchanted creature doesn't untap during its controller's untap step") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Ringing Strike Mastery")
                    .withCardOnBattlefield(2, "Grizzly Bears", tapped = true)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val aura = game.findPermanent("Ringing Strike Mastery")!!
                val creature = game.findPermanent("Grizzly Bears")!!
                game.state = game.state.updateEntity(aura) {
                    it.with(AttachedToComponent(creature))
                }.updateEntity(creature) {
                    it.with(AttachmentsComponent(listOf(aura)))
                }

                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                withClue("Grizzly Bears should remain tapped due to DOESNT_UNTAP") {
                    game.state.getEntity(creature)!!.has<TappedComponent>() shouldBe true
                }
            }

            test("granted {5} ability untaps the enchanted creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Ringing Strike Mastery")
                    .withCardOnBattlefield(2, "Grizzly Bears", tapped = true)
                    .withLandsOnBattlefield(2, "Island", 5)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val aura = game.findPermanent("Ringing Strike Mastery")!!
                val creature = game.findPermanent("Grizzly Bears")!!
                game.state = game.state.updateEntity(aura) {
                    it.with(AttachedToComponent(creature))
                }.updateEntity(creature) {
                    it.with(AttachmentsComponent(listOf(aura)))
                }

                val untapAbilityId = cardRegistry.getCard("Ringing Strike Mastery")!!
                    .staticAbilities.filterIsInstance<GrantActivatedAbility>().first().ability.id

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player2Id,
                        sourceId = creature,
                        abilityId = untapAbilityId
                    )
                )
                withClue("Activating the {5} untap ability should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                withClue("Grizzly Bears should be untapped after paying {5}") {
                    game.state.getEntity(creature)!!.has<TappedComponent>() shouldBe false
                }
            }
        }

        context("Unsparing Boltcaster") {

            test("ETB deals 5 damage to a damaged opponent creature, killing it") {
                // Shock the opponent's 3/3 first (marks it dealt-damage this turn and leaves it alive),
                // then enter the Boltcaster targeting it.
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Shock")
                    .withCardInHand(1, "Unsparing Boltcaster")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!
                val shock = game.castSpell(1, "Shock", giant)
                withClue("Shock should succeed: ${shock.error}") { shock.error shouldBe null }
                game.resolveStack()

                withClue("Hill Giant survives 2 damage") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }

                val cast = game.castSpell(1, "Unsparing Boltcaster")
                withClue("Boltcaster cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()
                // The ETB trigger pauses to choose its target (the damaged Hill Giant).
                if (game.hasPendingDecision()) {
                    game.selectTargets(listOf(giant))
                    game.resolveStack()
                }
                if (game.state.stack.isNotEmpty()) game.resolveStack()

                withClue("Hill Giant takes 5 more damage and dies") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }

            test("cannot be cast targeting an undamaged opponent creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Unsparing Boltcaster")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, not damaged
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // No damaged creature exists, so the ETB trigger has no legal target; the Boltcaster
                // still resolves as a creature, but no damage is dealt.
                val cast = game.castSpell(1, "Unsparing Boltcaster")
                withClue("Boltcaster cast should succeed (creature enters regardless): ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()
                if (game.state.stack.isNotEmpty()) game.resolveStack()

                withClue("Undamaged Hill Giant is untouched (no legal target for the trigger)") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
                withClue("Boltcaster itself is on the battlefield") {
                    game.isOnBattlefield("Unsparing Boltcaster") shouldBe true
                }
            }
        }

        context("Shocking Sharpshooter") {

            test("another creature you control entering deals 1 damage to target opponent") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Shocking Sharpshooter")
                    .withCardInHand(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLifeTotal(2, 20)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Grizzly Bears")
                withClue("Casting Grizzly Bears should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()
                // The Sharpshooter's triggered ability pauses to choose the target opponent.
                if (game.hasPendingDecision()) {
                    game.selectTargets(listOf(game.player2Id))
                    game.resolveStack()
                }
                if (game.state.stack.isNotEmpty()) game.resolveStack()

                withClue("Opponent takes 1 damage from the Sharpshooter trigger") {
                    game.getLifeTotal(2) shouldBe 19
                }
            }
        }

        context("Dragonstorm Forecaster") {

            test("{2},{T} tutors Boulderborn Dragon into hand") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Dragonstorm Forecaster", tapped = false, summoningSickness = false)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Boulderborn Dragon")
                    .withCardInLibrary(1, "Grizzly Bears") // a non-matching card in the library
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val forecaster = game.findPermanent("Dragonstorm Forecaster")!!
                val abilityId = cardRegistry.getCard("Dragonstorm Forecaster")!!.activatedAbilities.first().id

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = forecaster,
                        abilityId = abilityId
                    )
                )
                withClue("Activating the search ability should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                // A library search selection should be pending, restricted to the named card.
                if (game.hasPendingDecision()) {
                    val found = game.findCardsInLibrary(1, "Boulderborn Dragon")
                    game.selectCards(found)
                    game.resolveStack()
                }

                withClue("Boulderborn Dragon is now in hand") {
                    game.findCardsInHand(1, "Boulderborn Dragon").size shouldBe 1
                }
                withClue("Boulderborn Dragon left the library") {
                    game.findCardsInLibrary(1, "Boulderborn Dragon").size shouldBe 0
                }
            }
        }
    }
}
