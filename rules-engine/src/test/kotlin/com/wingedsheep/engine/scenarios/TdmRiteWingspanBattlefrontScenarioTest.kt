package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for three Tarkir: Dragonstorm cards:
 *  - Rite of Renewal (#153): return up to two permanent cards from your graveyard to hand,
 *    shuffle up to four cards from graveyards into their libraries, then exile itself.
 *  - Wingspan Stride (#66): Aura granting +1/+1 and flying with a {2}{U} self-bounce.
 *  - United Battlefront (#32): look at top seven, put up to two noncreature/nonland permanent
 *    cards with mana value 3 or less onto the battlefield, rest on the bottom at random.
 */
class TdmRiteWingspanBattlefrontScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private val wingspanBounceId =
        cardRegistry.getCard("Wingspan Stride")!!.activatedAbilities.first().id

    init {
        context("Rite of Renewal") {
            test("returns two permanent cards to hand, shuffles graveyard cards into library, and exiles itself") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Rite of Renewal")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    // Permanent cards eligible for return-to-hand.
                    .withCardInGraveyard(1, "Glory Seeker")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    // A nonpermanent (instant) — eligible for the shuffle clause but not the return clause.
                    .withCardInGraveyard(1, "Shock")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Rite of Renewal")
                withClue("Casting Rite of Renewal should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // First selection: up to two permanent cards from your graveyard to hand.
                withClue("Should prompt to choose permanent cards to return to hand") {
                    game.hasPendingDecision() shouldBe true
                }
                val glory = game.findCardsInGraveyard(1, "Glory Seeker").first()
                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").first()
                game.selectCards(listOf(glory, bears))
                game.resolveStack()

                // Second selection: up to four cards from graveyards into their library.
                withClue("Should prompt to choose cards to shuffle into library") {
                    game.hasPendingDecision() shouldBe true
                }
                val shock = game.findCardsInGraveyard(1, "Shock").first()
                game.selectCards(listOf(shock))
                game.resolveStack()

                withClue("Glory Seeker and Grizzly Bears should be in hand") {
                    game.findCardsInHand(1, "Glory Seeker").size shouldBe 1
                    game.findCardsInHand(1, "Grizzly Bears").size shouldBe 1
                }
                withClue("Shock should have left the graveyard (shuffled into library)") {
                    game.findCardsInGraveyard(1, "Shock").size shouldBe 0
                }
                withClue("Rite of Renewal should be exiled, not in the graveyard") {
                    game.state.getExile(game.player1Id).any {
                        game.state.getEntity(it)?.let { e ->
                            e.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name
                        } == "Rite of Renewal"
                    } shouldBe true
                    game.findCardsInGraveyard(1, "Rite of Renewal").size shouldBe 0
                }
            }

            test("both selections may be declined; the card still exiles itself") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Rite of Renewal")
                    .withLandsOnBattlefield(1, "Forest", 4)
                    .withCardInGraveyard(1, "Glory Seeker")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Rite of Renewal")
                game.resolveStack()

                // Decline the return-to-hand selection.
                withClue("Should prompt to return permanent cards") {
                    game.hasPendingDecision() shouldBe true
                }
                game.skipSelection()
                game.resolveStack()

                // Decline the shuffle selection.
                withClue("Should prompt to shuffle cards into library") {
                    game.hasPendingDecision() shouldBe true
                }
                game.skipSelection()
                game.resolveStack()

                withClue("Glory Seeker stays in the graveyard when nothing is chosen") {
                    game.findCardsInGraveyard(1, "Glory Seeker").size shouldBe 1
                }
                withClue("Rite of Renewal is still exiled") {
                    game.findCardsInGraveyard(1, "Rite of Renewal").size shouldBe 0
                }
            }
        }

        context("Wingspan Stride") {
            test("grants +1/+1 and flying, then can bounce itself to hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Wingspan Stride")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 vanilla
                    // 4 Islands: {U} for the cast leaves 3 for the {2}{U} bounce.
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val gloryId = game.findPermanent("Glory Seeker")!!

                val cast = game.castSpell(1, "Wingspan Stride", gloryId)
                withClue("Casting Wingspan Stride targeting Glory Seeker should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Enchanted creature gets +1/+1 (2/2 -> 3/3)") {
                    projector.getProjectedPower(game.state, gloryId) shouldBe 3
                    projector.getProjectedToughness(game.state, gloryId) shouldBe 3
                }
                withClue("Enchanted creature has flying") {
                    game.state.projectedState.hasKeyword(gloryId, Keyword.FLYING) shouldBe true
                }

                val stride = game.findPermanent("Wingspan Stride")!!
                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = stride,
                        abilityId = wingspanBounceId
                    )
                )
                withClue("Activating the {2}{U} bounce should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStack()

                withClue("Wingspan Stride returns to its owner's hand") {
                    game.findCardsInHand(1, "Wingspan Stride").size shouldBe 1
                    game.isOnBattlefield("Wingspan Stride") shouldBe false
                }
                withClue("Glory Seeker reverts to 2/2 with no flying once the Aura is gone") {
                    projector.getProjectedPower(game.state, gloryId) shouldBe 2
                    projector.getProjectedToughness(game.state, gloryId) shouldBe 2
                    game.state.projectedState.hasKeyword(gloryId, Keyword.FLYING) shouldBe false
                }
            }
        }

        context("United Battlefront") {
            test("puts up to two matching permanents onto the battlefield; rest go to the bottom") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "United Battlefront")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    // Eligible: noncreature, nonland permanent with MV <= 3 (Enchantment, MV 2).
                    .withCardInLibrary(1, "Test Enchantment")
                    .withCardInLibrary(1, "Rite of Renewal") // Sorcery — NOT a permanent, ineligible
                    .withCardInLibrary(1, "Glory Seeker")    // Creature — ineligible
                    .withCardInLibrary(1, "Forest")          // Land — ineligible
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "United Battlefront")
                withClue("Casting United Battlefront should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Should prompt to choose permanents to put onto the battlefield") {
                    game.hasPendingDecision() shouldBe true
                }
                val enchantInLib = game.findCardsInLibrary(1, "Test Enchantment").first()
                game.selectCards(listOf(enchantInLib))
                game.resolveStack()

                withClue("Test Enchantment should be on the battlefield") {
                    game.isOnBattlefield("Test Enchantment") shouldBe true
                }
                withClue("The other six looked-at cards should be back in the library (on the bottom)") {
                    game.librarySize(1) shouldBe 6
                }
            }

            test("nothing eligible — all seven cards go to the bottom") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "United Battlefront")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withCardInLibrary(1, "Glory Seeker")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "United Battlefront")
                game.resolveStack()

                // With no eligible card, either no decision is presented or it is skippable.
                if (game.hasPendingDecision()) {
                    game.skipSelection()
                    game.resolveStack()
                }

                withClue("All seven cards remain in the library") {
                    game.librarySize(1) shouldBe 7
                }
                withClue("Nothing entered the battlefield") {
                    game.isOnBattlefield("Glory Seeker") shouldBe false
                }
            }
        }
    }
}
