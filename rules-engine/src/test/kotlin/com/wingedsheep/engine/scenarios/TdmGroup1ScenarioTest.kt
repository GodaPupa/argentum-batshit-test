package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM "group 1" batch of spells:
 *  - Dragonclaw Strike (#180): double a creature you control's P/T, then it fights up to one
 *    opponent creature.
 *  - Riverwheel Sweep (#219): tap target + 3 stun counters, then impulse-pick one of the top two.
 *  - Narset's Rebuke (#114): 5 damage to a creature, add {U}{R}{W}, exile-if-it-would-die.
 *  - Wail of War (#98): modal — opponent's creatures get -1/-1, or return up to two creature
 *    cards from your graveyard.
 */
class TdmGroup1ScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Dragonclaw Strike") {
            test("doubles your creature's P/T, then it fights and kills the opponent's creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dragonclaw Strike")
                    .withLandsOnBattlefield(1, "Mountain", 5) // {2/G}{2/U}{2/R}: only {2/R} discounts → 2+2+1
                    .withCardOnBattlefield(1, "Grizzly Bears")   // 2/2 -> doubled to 4/4
                    .withCardOnBattlefield(2, "Hill Giant")      // 3/3 victim
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mine = game.findPermanent("Grizzly Bears")!!
                val victim = game.findPermanent("Hill Giant")!!
                val cardId = game.findCardsInHand(1, "Dragonclaw Strike").first()
                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        cardId,
                        listOf(ChosenTarget.Permanent(mine), ChosenTarget.Permanent(victim))
                    )
                )
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Grizzly Bears should be doubled to 4/4") {
                    stateProjector.getProjectedPower(game.state, mine) shouldBe 4
                    stateProjector.getProjectedToughness(game.state, mine) shouldBe 4
                }
                withClue("Hill Giant (3/3) takes 4 damage and dies") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
                withClue("Grizzly Bears (now 4/4) takes 3 damage and survives") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }

            test("with no opponent creature targeted, only doubles (fight has no second target)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Dragonclaw Strike")
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mine = game.findPermanent("Grizzly Bears")!!
                val cardId = game.findCardsInHand(1, "Dragonclaw Strike").first()
                val cast = game.execute(
                    CastSpell(game.player1Id, cardId, listOf(ChosenTarget.Permanent(mine)))
                )
                withClue("Cast with no opponent creature should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Grizzly Bears should be doubled to 4/4 and survive (no fight)") {
                    stateProjector.getProjectedPower(game.state, mine) shouldBe 4
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }
        }

        context("Riverwheel Sweep") {
            test("taps the target, adds three stun counters, then impulse-picks one of two exiled cards") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Riverwheel Sweep")
                    .withLandsOnBattlefield(1, "Island", 5) // {2/U}{2/R}{2/W}: only {2/U} discounts → 1+2+2
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Wind Drake")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val victim = game.findPermanent("Hill Giant")!!
                val cast = game.castSpell(1, "Riverwheel Sweep", victim)
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Hill Giant should be tapped") {
                    game.state.getEntity(victim)?.has<TappedComponent>() shouldBe true
                }
                withClue("Hill Giant should have three stun counters") {
                    game.state.getEntity(victim)?.get<CountersComponent>()?.getCount(CounterType.STUN) shouldBe 3
                }

                withClue("Should prompt to choose one of the two exiled cards") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision() as SelectCardsDecision
                withClue("Both exiled cards should be offered") {
                    decision.options.size shouldBe 2
                }
                game.selectCards(listOf(decision.options.first()))

                withClue("Both cards should now be in exile") {
                    game.state.getExile(game.player1Id).count {
                        val name = game.state.getEntity(it)?.get<CardComponent>()?.name
                        name == "Grizzly Bears" || name == "Wind Drake"
                    } shouldBe 2
                }
            }
        }

        context("Narset's Rebuke") {
            test("deals 5 damage, adds three mana, and exiles the creature instead of it dying") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Narset's Rebuke")
                    .withLandsOnBattlefield(1, "Mountain", 5) // {4}{R}
                    .withCardOnBattlefield(2, "Hill Giant")   // 3/3 — dies to 5 damage
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val victim = game.findPermanent("Hill Giant")!!
                val cast = game.castSpell(1, "Narset's Rebuke", victim)
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Hill Giant should be gone from the battlefield") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
                withClue("Hill Giant should be exiled, not in its owner's graveyard") {
                    game.findCardsInGraveyard(2, "Hill Giant").size shouldBe 0
                    game.state.getExile(game.player2Id).count {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Hill Giant"
                    } shouldBe 1
                }
            }
        }

        context("Wail of War") {
            test("mode 1 gives the opponent's creatures -1/-1, killing a 1-toughness creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Wail of War")
                    .withLandsOnBattlefield(1, "Swamp", 3) // {2}{B}
                    .withCardOnBattlefield(2, "Willow Dryad") // 1/1 -> dies to -1/-1
                    .withCardOnBattlefield(2, "Hill Giant")   // 3/3 -> 2/2 survives
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        game.findCardsInHand(1, "Wail of War").first(),
                        listOf(ChosenTarget.Player(game.player2Id)),
                        chosenModes = listOf(0),
                        modeTargetsOrdered = listOf(listOf(ChosenTarget.Player(game.player2Id)))
                    )
                )
                withClue("Cast (mode 1) should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("The 1/1 should die to -1/-1") {
                    game.isOnBattlefield("Willow Dryad") shouldBe false
                }
                val giant = game.findPermanent("Hill Giant")!!
                withClue("Hill Giant should be a 2/2 and survive") {
                    stateProjector.getProjectedPower(game.state, giant) shouldBe 2
                    stateProjector.getProjectedToughness(game.state, giant) shouldBe 2
                }
            }

            test("mode 2 returns two creature cards from your graveyard to your hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Wail of War")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findCardsInGraveyard(1, "Grizzly Bears").first()
                val giant = game.findCardsInGraveyard(1, "Hill Giant").first()
                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        game.findCardsInHand(1, "Wail of War").first(),
                        emptyList(),
                        chosenModes = listOf(1),
                        modeTargetsOrdered = listOf(
                            listOf(
                                ChosenTarget.Card(bears, game.player1Id, Zone.GRAVEYARD),
                                ChosenTarget.Card(giant, game.player1Id, Zone.GRAVEYARD)
                            )
                        )
                    )
                )
                withClue("Cast (mode 2) should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Both creature cards should be back in hand") {
                    game.findCardsInHand(1, "Grizzly Bears").size shouldBe 1
                    game.findCardsInHand(1, "Hill Giant").size shouldBe 1
                }
                withClue("Graveyard should be empty of those creatures") {
                    game.findCardsInGraveyard(1, "Grizzly Bears").size shouldBe 0
                    game.findCardsInGraveyard(1, "Hill Giant").size shouldBe 0
                }
            }
        }
    }
}
