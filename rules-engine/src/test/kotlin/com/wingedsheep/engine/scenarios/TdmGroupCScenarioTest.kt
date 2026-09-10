package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM "group C" batch of spells:
 *  - Heritage Reclamation (#145): modal {1}{G} instant — destroy artifact / destroy enchantment /
 *    exile up to one card from a graveyard and draw.
 *  - Nature's Rhythm (#150): {X}{G}{G} sorcery — search library for a creature with MV X or less,
 *    put it onto the battlefield. (Harmonize keyword is display + alt-cost; the graveyard-cast X UX
 *    prompt is a known engine enumerator gap, noted in the card file.)
 */
class TdmGroupCScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Heritage Reclamation") {
            test("mode 1 destroys a target artifact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Heritage Reclamation")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardOnBattlefield(2, "Abzan Monument")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val artifactId = game.findPermanent("Abzan Monument")!!
                val cast = game.castSpellWithMode(1, "Heritage Reclamation", modeIndex = 0, targetId = artifactId)
                withClue("Casting Heritage Reclamation (mode 1) should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("The artifact should have been destroyed") {
                    game.isOnBattlefield("Abzan Monument") shouldBe false
                }
                withClue("The artifact should be in its owner's graveyard") {
                    game.findCardsInGraveyard(2, "Abzan Monument").size shouldBe 1
                }
            }

            test("mode 3 exiles a card from a graveyard and draws a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Heritage Reclamation")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInGraveyard(2, "Forest")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val graveyardCardId = game.findCardsInGraveyard(2, "Forest").first()
                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = game.findCardsInHand(1, "Heritage Reclamation").first(),
                        chosenModes = listOf(2),
                        modeTargetsOrdered = listOf(
                            listOf(ChosenTarget.Card(graveyardCardId, game.player2Id, Zone.GRAVEYARD))
                        )
                    )
                )
                withClue("Casting Heritage Reclamation (mode 3) should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("The targeted card should have left the graveyard") {
                    game.findCardsInGraveyard(2, "Forest").size shouldBe 0
                }
                withClue("The targeted card should now be in exile") {
                    game.state.getExile(game.player2Id).count {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Forest"
                    } shouldBe 1
                }
                withClue("The caster should have drawn the Island") {
                    game.findCardsInHand(1, "Island").size shouldBe 1
                }
            }
        }

        context("Nature's Rhythm") {
            test("with X=2, searches a creature with MV <= 2 onto the battlefield (higher-MV creature excluded)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Nature's Rhythm")
                    .withLandsOnBattlefield(1, "Forest", 4) // {X}{G}{G} with X=2
                    .withCardInLibrary(1, "Grizzly Bears")  // MV 2 — eligible
                    .withCardInLibrary(1, "Whiptail Wurm")  // MV 7 — excluded by MV X or less
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castXSpell(1, "Nature's Rhythm", xValue = 2)
                withClue("Casting Nature's Rhythm with X=2 should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Should prompt a library search") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision() as SelectCardsDecision
                val cardInfo = decision.cardInfo!!
                withClue("Only the MV-2 Grizzly Bears should be a legal choice; Whiptail Wurm (MV 7) is excluded") {
                    cardInfo.values.map { it.name }.toSet() shouldBe setOf("Grizzly Bears")
                }

                val bearsId = cardInfo.entries.first { it.value.name == "Grizzly Bears" }.key
                game.selectCards(listOf(bearsId))

                withClue("Grizzly Bears should be on the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
                withClue("Whiptail Wurm should remain in the library") {
                    game.findCardsInLibrary(1, "Whiptail Wurm").size shouldBe 1
                }
            }
        }

        context("Roamer's Routine") {
            test("searches a basic land onto the battlefield tapped") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Roamer's Routine")
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardInLibrary(1, "Mountain")  // basic land — eligible
                    .withCardInLibrary(1, "Grizzly Bears") // nonland — excluded
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Roamer's Routine")
                withClue("Casting Roamer's Routine should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Should prompt a library search") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision() as SelectCardsDecision
                val cardInfo = decision.cardInfo!!
                withClue("Only the basic land Mountain should be a legal choice") {
                    cardInfo.values.map { it.name }.toSet() shouldBe setOf("Mountain")
                }

                val mountainId = cardInfo.entries.first { it.value.name == "Mountain" }.key
                game.selectCards(listOf(mountainId))

                withClue("Mountain should be on the battlefield") {
                    game.isOnBattlefield("Mountain") shouldBe true
                }
                val mountainOnBattlefield = game.findPermanent("Mountain")!!
                withClue("The fetched Mountain should enter tapped") {
                    game.state.getEntity(mountainOnBattlefield)?.has<TappedComponent>() shouldBe true
                }
            }
        }

        context("Fresh Start") {
            test("enchanted creature gets -5/-0 and loses all abilities") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Fresh Start")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(2, "Wind Drake") // 2/2 flyer
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val windDrake = game.findPermanent("Wind Drake")!!
                withClue("Wind Drake should have flying before Fresh Start") {
                    stateProjector.getProjectedKeywords(game.state, windDrake) shouldBe setOf(Keyword.FLYING)
                }

                val cast = game.castSpell(1, "Fresh Start", windDrake)
                withClue("Casting Fresh Start should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Fresh Start should be on the battlefield") {
                    game.isOnBattlefield("Fresh Start") shouldBe true
                }
                withClue("Wind Drake power should be -3 (2 - 5)") {
                    stateProjector.getProjectedPower(game.state, windDrake) shouldBe -3
                }
                withClue("Wind Drake toughness should be unchanged at 2") {
                    stateProjector.getProjectedToughness(game.state, windDrake) shouldBe 2
                }
                withClue("Wind Drake should have lost flying (all abilities)") {
                    stateProjector.getProjectedKeywords(game.state, windDrake) shouldBe emptySet()
                }
            }
        }

        context("Focus the Mind") {
            test("draws three cards then discards one") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Focus the Mind")
                    .withLandsOnBattlefield(1, "Island", 5) // full {4}{U}
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Focus the Mind")
                withClue("Casting Focus the Mind should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                // Resolution draws three, then prompts to discard one.
                withClue("Should prompt a discard after drawing") {
                    game.hasPendingDecision() shouldBe true
                }
                val discard = game.getPendingDecision() as SelectCardsDecision
                game.selectCards(listOf(discard.options.first()))
                game.resolveStack()

                // Net: +3 drawn, -1 discarded = +2 cards in hand (Focus the Mind itself left the hand).
                withClue("Hand should hold the net 2 drawn cards") {
                    game.handSize(1) shouldBe 2
                }
                withClue("Exactly one card should have been discarded to the graveyard") {
                    game.state.getGraveyard(game.player1Id).count {
                        val name = game.state.getEntity(it)?.get<CardComponent>()?.name
                        name != "Focus the Mind"
                    } shouldBe 1
                }
            }

            test("costs {2} less if you've cast another spell this turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Summer Bloom")
                    .withCardInHand(1, "Focus the Mind")
                    .withLandsOnBattlefield(1, "Forest", 2)  // {1}{G} for Summer Bloom (no target)
                    .withLandsOnBattlefield(1, "Island", 3)  // reduced {2}{U} for Focus the Mind
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // First spell of the turn — Summer Bloom ({1}{G}, no target).
                val firstCast = game.castSpell(1, "Summer Bloom")
                withClue("Casting Summer Bloom should succeed: ${firstCast.error}") {
                    firstCast.error shouldBe null
                }
                game.resolveStack()
                if (game.hasPendingDecision()) {
                    game.skipSelection()
                    game.resolveStack()
                }

                // Now Focus the Mind should be castable for its reduced {2}{U} cost (3 Islands left).
                val cast = game.castSpell(1, "Focus the Mind")
                withClue("Focus the Mind should be castable for its reduced cost: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()
                withClue("Focus the Mind should resolve (prompting the discard)") {
                    game.hasPendingDecision() shouldBe true
                }
                val discard = game.getPendingDecision() as SelectCardsDecision
                game.selectCards(listOf(discard.options.first()))
                game.resolveStack()

                withClue("Focus the Mind should have resolved into the graveyard") {
                    game.findCardsInGraveyard(1, "Focus the Mind").size shouldBe 1
                }
            }
        }
    }
}
