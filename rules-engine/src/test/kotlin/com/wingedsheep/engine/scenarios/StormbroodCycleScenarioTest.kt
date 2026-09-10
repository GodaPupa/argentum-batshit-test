package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM "Stormbrood" Omen-Dragon cycle (TDM #178/#213/#221/#232/#234).
 *
 * Each card is a [com.wingedsheep.sdk.model.CardLayout.OMEN] double face: cast the creature
 * (Dragon) front, or cast the cheaper Omen spell face — which shuffles the card back into the
 * owner's library on resolution. These tests exercise both faces and the cycle's ETB / cast /
 * static abilities, all composed from existing SDK primitives.
 */
class StormbroodCycleScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun TestGame.castOmenFace(
        playerNumber: Int,
        cardName: String,
        targets: List<ChosenTarget>,
    ) = run {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        val cardId = state.getHand(playerId).first {
            state.getEntity(it)?.get<CardComponent>()?.name == cardName
        }
        execute(CastSpell(playerId, cardId, targets, faceIndex = 0))
    }

    init {

        // ---------------------------------------------------------------------
        // Disruptive Stormbrood // Petty Revenge
        // ---------------------------------------------------------------------
        context("Disruptive Stormbrood") {

            test("ETB destroys up to one target artifact or enchantment") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Disruptive Stormbrood")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardOnBattlefield(2, "Slate of Ancestry") // artifact
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Disruptive Stormbrood").error shouldBe null
                game.resolveStack() // creature enters → ETB asks for target

                val target = game.findPermanent("Slate of Ancestry")!!
                game.selectTargets(listOf(target)).error shouldBe null
                game.resolveStack()

                withClue("Targeted artifact is destroyed") {
                    game.isOnBattlefield("Slate of Ancestry") shouldBe false
                }
                withClue("Disruptive Stormbrood (3/3 flier) is on the battlefield") {
                    game.isOnBattlefield("Disruptive Stormbrood") shouldBe true
                }
            }
        }

        context("Petty Revenge Omen face") {
            test("destroys target creature with power 3 or less and shuffles into library") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Disruptive Stormbrood")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Grizzly Bears") // 2/2 — power 2 <= 3
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val libBefore = game.librarySize(1)
                game.castOmenFace(1, "Disruptive Stormbrood", listOf(ChosenTarget.Permanent(bears))).error shouldBe null
                game.resolveStack()

                withClue("Grizzly Bears is destroyed") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
                withClue("Omen card shuffled back into library (not graveyard)") {
                    game.graveyardSize(1) shouldBe 0
                    game.librarySize(1) shouldBe libBefore + 1
                }
            }
        }

        // ---------------------------------------------------------------------
        // Purging Stormbrood // Absorb Essence
        // ---------------------------------------------------------------------
        context("Purging Stormbrood") {
            test("ETB removes all counters from up to one target creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Purging Stormbrood")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(1, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Seed the opponent's creature with +1/+1 counters to clear.
                val bears = game.findPermanent("Grizzly Bears")!!
                game.state = game.state.withEntity(
                    bears,
                    game.state.getEntity(bears)!!
                        .with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2))),
                )

                game.castSpell(1, "Purging Stormbrood").error shouldBe null
                game.resolveStack()
                game.selectTargets(listOf(bears)).error shouldBe null
                game.resolveStack()

                withClue("All +1/+1 counters removed") {
                    val counters = game.state.getEntity(bears)
                        ?.get<CountersComponent>()
                        ?.counters?.get(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
                    counters shouldBe 0
                }
            }
        }

        context("Absorb Essence Omen face") {
            test("target creature gets +2/+2 and gains lifelink and hexproof") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Purging Stormbrood")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.castOmenFace(1, "Purging Stormbrood", listOf(ChosenTarget.Permanent(bears))).error shouldBe null
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Grizzly Bears is now 4/4") {
                    projected.getPower(bears) shouldBe 4
                    projected.getToughness(bears) shouldBe 4
                }
                withClue("gains lifelink and hexproof") {
                    projected.hasKeyword(bears, Keyword.LIFELINK) shouldBe true
                    projected.hasKeyword(bears, Keyword.HEXPROOF) shouldBe true
                }
            }
        }

        // ---------------------------------------------------------------------
        // Runescale Stormbrood // Chilling Screech
        // ---------------------------------------------------------------------
        context("Runescale Stormbrood") {
            test("gets +2/+0 when you cast a noncreature spell") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Runescale Stormbrood") // 2/4
                    .withCardInHand(1, "Shock") // noncreature (instant) spell
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val brood = game.findPermanent("Runescale Stormbrood")!!
                game.castSpellTargetingPlayer(1, "Shock", 2).error shouldBe null
                // Resolve the triggered ability (and Shock) so the +2/+0 buff applies.
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Runescale Stormbrood is 4/4 after casting a noncreature spell") {
                    projected.getPower(brood) shouldBe 4
                    projected.getToughness(brood) shouldBe 4
                }
            }
        }

        context("Chilling Screech Omen face") {
            test("counters target spell with mana value 2 or less") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Runescale Stormbrood")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInHand(2, "Grizzly Bears") // MV 2 creature spell
                    .withLandsOnBattlefield(2, "Forest", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Player 2 casts Grizzly Bears (MV 2); player 1 responds with Chilling Screech.
                game.castSpell(2, "Grizzly Bears").error shouldBe null
                game.passPriority() // active player passes → priority to player 1
                val bearsOnStack = game.state.stack.first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears"
                }
                game.castOmenFace(1, "Runescale Stormbrood", listOf(ChosenTarget.Spell(bearsOnStack))).error shouldBe null
                game.resolveStack()

                withClue("Grizzly Bears was countered — never reaches the battlefield") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
            }
        }

        // ---------------------------------------------------------------------
        // Twinmaw Stormbrood // Charring Bite
        // ---------------------------------------------------------------------
        context("Twinmaw Stormbrood") {
            test("ETB gains 5 life") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Twinmaw Stormbrood")
                    .withLandsOnBattlefield(1, "Plains", 6)
                    .withCardInLibrary(1, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lifeBefore = game.getLifeTotal(1)
                game.castSpell(1, "Twinmaw Stormbrood").error shouldBe null
                game.resolveStack()

                withClue("Controller gains 5 life") {
                    game.getLifeTotal(1) shouldBe lifeBefore + 5
                }
            }
        }

        context("Charring Bite Omen face") {
            test("deals 5 damage to a creature without flying") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Twinmaw Stormbrood")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3, no flying
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!
                game.castOmenFace(1, "Twinmaw Stormbrood", listOf(ChosenTarget.Permanent(giant))).error shouldBe null
                game.resolveStack()

                withClue("Hill Giant takes 5 damage and dies") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }
        }

        // ---------------------------------------------------------------------
        // Whirlwing Stormbrood // Dynamic Soar
        // ---------------------------------------------------------------------
        context("Whirlwing Stormbrood") {
            test("creature face has flash and flying") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Whirlwing Stormbrood")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val brood = game.findPermanent("Whirlwing Stormbrood")!!
                val projected = stateProjector.project(game.state)
                withClue("Whirlwing Stormbrood has flying and flash") {
                    projected.hasKeyword(brood, Keyword.FLYING) shouldBe true
                    projected.hasKeyword(brood, Keyword.FLASH) shouldBe true
                }
            }
        }

        context("Dynamic Soar Omen face") {
            test("puts three +1/+1 counters on target creature you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Whirlwing Stormbrood")
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.castOmenFace(1, "Whirlwing Stormbrood", listOf(ChosenTarget.Permanent(bears))).error shouldBe null
                game.resolveStack()

                withClue("Grizzly Bears has three +1/+1 counters") {
                    val counters = game.state.getEntity(bears)
                        ?.get<CountersComponent>()
                        ?.counters?.get(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
                    counters shouldBe 3
                }
            }
        }
    }
}
