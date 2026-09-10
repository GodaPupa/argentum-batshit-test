package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for two Tarkir: Dragonstorm cards built from existing primitives.
 *
 *  - Sunset Strikemaster (TDM #126) — {1}{R} Human Monk, 3/1.
 *      "{T}: Add {R}.
 *       {2}{R}, {T}, Sacrifice this creature: It deals 6 damage to target creature with flying."
 *
 *  - Reigning Victor (TDM #216) — {2/R}{2/W}{2/B} Orc Warrior, 3/3, Mobilize 1.
 *      "When this creature enters, target creature gets +1/+0 and gains indestructible until end of
 *       turn."
 */
class TdmSunsetReigningScenarioTest : ScenarioTestBase() {

    // Sunset Strikemaster's sacrifice ability is the second activated ability (the first is the
    // {T}: Add {R} mana ability).
    private val strikemasterSacAbilityId =
        cardRegistry.getCard("Sunset Strikemaster")!!.activatedAbilities[1].id

    init {
        cardRegistry.register(
            CardDefinition.creature(
                name = "Test Flyer",
                manaCost = ManaCost.parse("{4}{U}"),
                subtypes = setOf(Subtype("Bird")),
                power = 4,
                toughness = 4,
                keywords = setOf(Keyword.FLYING),
            )
        )
        cardRegistry.register(
            CardDefinition.creature(
                name = "Test Groundling",
                manaCost = ManaCost.parse("{2}{G}"),
                subtypes = setOf(Subtype("Bear")),
                power = 3,
                toughness = 3,
            )
        )

        context("Sunset Strikemaster") {

            test("sacrifice ability deals 6 damage to a flying creature, killing it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sunset Strikemaster", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Mountain", 3) // {2}{R}
                    .withCardOnBattlefield(2, "Test Flyer", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val strikemaster = game.findPermanent("Sunset Strikemaster")!!
                val flyer = game.findPermanent("Test Flyer")!!

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = strikemaster,
                        abilityId = strikemasterSacAbilityId,
                        targets = listOf(ChosenTarget.Permanent(flyer)),
                    )
                )
                withClue("Activating the sacrifice ability should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                withClue("Sunset Strikemaster was sacrificed as a cost and left the battlefield") {
                    game.isOnBattlefield("Sunset Strikemaster") shouldBe false
                }
                withClue("6 damage kills the 4/4 flyer") {
                    game.isOnBattlefield("Test Flyer") shouldBe false
                }
            }
        }

        context("Reigning Victor") {

            test("ETB gives target creature +1/+0 and indestructible until end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Reigning Victor")
                    .withLandsOnBattlefield(1, "Mountain", 6) // {2/R}{2/W}{2/B} paid as 6 generic
                    .withCardOnBattlefield(1, "Test Groundling", summoningSickness = false)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Reigning Victor").error shouldBe null
                game.resolveStack() // creature enters → ETB trigger on stack, asks for a target

                val groundling = game.findPermanent("Test Groundling")!!

                withClue("Base groundling is 3/3 with no indestructible before the trigger resolves") {
                    game.state.projectedState.getPower(groundling) shouldBe 3
                    game.getClientState(1).cards[groundling]?.keywords?.contains(Keyword.INDESTRUCTIBLE) shouldBe false
                }

                val select = game.selectTargets(listOf(groundling))
                withClue("Selecting the target creature should succeed: ${select.error}") {
                    select.error shouldBe null
                }
                game.resolveStack()

                withClue("Target creature gets +1/+0 (power 3 -> 4)") {
                    game.state.projectedState.getPower(groundling) shouldBe 4
                }
                withClue("Toughness is unchanged (+1/+0)") {
                    game.state.projectedState.getToughness(groundling) shouldBe 3
                }
                withClue("Target creature gains indestructible") {
                    game.getClientState(1).cards[groundling]?.keywords?.contains(Keyword.INDESTRUCTIBLE) shouldBe true
                }
            }
        }
    }
}
