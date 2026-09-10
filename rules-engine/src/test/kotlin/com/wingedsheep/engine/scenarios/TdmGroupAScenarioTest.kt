package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the TDM "group A" cards:
 *  - Fleeting Effigy (#108): {R} Elemental 2/2, Haste; returns to hand at your end step;
 *    {2}{R}: +2/+0 until end of turn.
 *  - Fire-Rim Form (#107): {1}{R} Aura, Flash, Enchant creature; ETB grants first strike
 *    until end of turn; enchanted creature gets +2/+0.
 *  - Lightfoot Technique (#14): {1}{W} Instant; +1/+1 counter on target creature, it gains
 *    flying and indestructible until end of turn.
 *  - Riverwalk Technique (#54): {3}{U} Instant; choose one — put target nonland permanent on
 *    top/bottom of library, or counter target noncreature spell.
 */
class TdmGroupAScenarioTest : ScenarioTestBase() {

    private val effigyPumpAbilityId =
        cardRegistry.getCard("Fleeting Effigy")!!.activatedAbilities.first().id

    init {
        context("Fleeting Effigy") {
            test("has haste and its {2}{R} ability gives +2/+0 until end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Fleeting Effigy", tapped = false, summoningSickness = true)
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val effigy = game.findPermanent("Fleeting Effigy")!!
                withClue("Fleeting Effigy has haste") {
                    game.state.projectedState.hasKeyword(effigy, Keyword.HASTE) shouldBe true
                }

                val activation = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = effigy,
                        abilityId = effigyPumpAbilityId,
                    )
                )
                withClue("Activating the pump ability should succeed: ${activation.error}") {
                    activation.error shouldBe null
                }
                game.resolveStack()

                withClue("Fleeting Effigy is a 4/2 after the pump") {
                    game.state.projectedState.getPower(effigy) shouldBe 4
                    game.state.projectedState.getToughness(effigy) shouldBe 2
                }
            }

            test("returns to its owner's hand at the beginning of its controller's end step") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Fleeting Effigy", tapped = false, summoningSickness = true)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("Fleeting Effigy left the battlefield") {
                    game.findPermanent("Fleeting Effigy") shouldBe null
                }
                withClue("Fleeting Effigy is back in its owner's hand") {
                    game.isInHand(1, "Fleeting Effigy") shouldBe true
                }
            }
        }

        context("Fire-Rim Form") {
            test("flashes onto a creature, grants first strike (EOT) and a static +2/+0") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Fire-Rim Form")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 to enchant
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                withClue("Casting Fire-Rim Form on the Bear should succeed") {
                    game.castSpell(1, "Fire-Rim Form", targetId = bear).error shouldBe null
                }
                game.resolveStack() // aura enters → ETB trigger
                game.resolveStack() // resolve the first-strike trigger

                withClue("Enchanted Bear is a 4/2 from the static +2/+0") {
                    game.state.projectedState.getPower(bear) shouldBe 4
                    game.state.projectedState.getToughness(bear) shouldBe 2
                }
                withClue("Enchanted Bear has first strike until end of turn") {
                    game.state.projectedState.hasKeyword(bear, Keyword.FIRST_STRIKE) shouldBe true
                }
            }
        }

        context("Lightfoot Technique") {
            test("puts a +1/+1 counter on target creature and grants flying + indestructible EOT") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Lightfoot Technique")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 target
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                withClue("Casting Lightfoot Technique targeting the Bear should succeed") {
                    game.castSpell(1, "Lightfoot Technique", targetId = bear).error shouldBe null
                }
                game.resolveStack()

                withClue("Bear has a +1/+1 counter") {
                    game.state.getEntity(bear)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }
                withClue("Bear is a 3/3 with the counter") {
                    game.state.projectedState.getPower(bear) shouldBe 3
                    game.state.projectedState.getToughness(bear) shouldBe 3
                }
                withClue("Bear has flying and indestructible until end of turn") {
                    game.state.projectedState.hasKeyword(bear, Keyword.FLYING) shouldBe true
                    game.state.projectedState.hasKeyword(bear, Keyword.INDESTRUCTIBLE) shouldBe true
                }
            }
        }

        context("Riverwalk Technique") {
            test("mode 1 puts a target nonland permanent on top of its owner's library") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Riverwalk Technique")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardOnBattlefield(2, "Grizzly Bears") // opponent's nonland permanent
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                withClue("Casting Riverwalk Technique (mode 1) on the Bear should succeed") {
                    game.castSpellWithMode(1, "Riverwalk Technique", modeIndex = 0, targetId = bear)
                        .error shouldBe null
                }
                game.resolveStack()

                // The owner (Player2) chooses top or bottom of library.
                val decision = game.getPendingDecision()
                withClue("Owner faces a top-or-bottom library choice") {
                    (decision is ChooseOptionDecision) shouldBe true
                }
                decision as ChooseOptionDecision
                withClue("The Bear's owner makes the choice") {
                    decision.playerId shouldBe game.player2Id
                }
                game.submitDecision(OptionChosenResponse(decision.id, 0)) // top of library
                game.resolveStack()

                withClue("Bear left the battlefield") {
                    game.findPermanent("Grizzly Bears") shouldBe null
                }
                withClue("Bear is on top of Player2's library") {
                    game.state.getLibrary(game.player2Id).first().let { top ->
                        game.state.getEntity(top)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name
                    } shouldBe "Grizzly Bears"
                }
            }

            test("mode 2 counters a target noncreature spell") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Riverwalk Technique")
                    .withCardInHand(2, "Lightning Bolt")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardOnBattlefield(1, "Grizzly Bears") // Bolt's target
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                withClue("Player2 casts Lightning Bolt at the Bear") {
                    game.castSpell(2, "Lightning Bolt", targetId = bear).error shouldBe null
                }
                // Active player (Player2) passes priority so Player1 can respond.
                game.execute(PassPriority(game.player2Id))

                val bolt = game.state.stack.first { id ->
                    game.state.getEntity(id)
                        ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Lightning Bolt"
                }
                withClue("Player1 counters the Bolt with Riverwalk Technique (mode 2)") {
                    game.execute(
                        CastSpell(
                            playerId = game.player1Id,
                            cardId = game.findCardsInHand(1, "Riverwalk Technique").first(),
                            targets = listOf(ChosenTarget.Spell(bolt)),
                            chosenModes = listOf(1),
                            modeTargetsOrdered = listOf(listOf(ChosenTarget.Spell(bolt))),
                        )
                    ).error shouldBe null
                }

                // Drain the stack via priority passes.
                var iterations = 0
                while (game.state.stack.isNotEmpty() && iterations < 20) {
                    val priorityPlayer = game.state.priorityPlayerId ?: break
                    val r = game.execute(PassPriority(priorityPlayer))
                    if (r.error != null) break
                    iterations++
                }

                withClue("Lightning Bolt was countered — Bear survives") {
                    game.findPermanent("Grizzly Bears") shouldBe bear
                }
                withClue("Lightning Bolt is in Player2's graveyard") {
                    game.isInGraveyard(2, "Lightning Bolt") shouldBe true
                }
            }
        }
    }
}
