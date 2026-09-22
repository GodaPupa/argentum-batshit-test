package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ArtfulDodgeScenarioTest : ScenarioTestBase() {

    init {
        context("Artful Dodge") {

            test("target creature cannot be blocked this turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Artful Dodge")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardOnBattlefield(1, "Savannah Lions")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val attacker = game.findPermanent("Savannah Lions").shouldNotBeNull()
                val cast = game.castSpell(1, "Artful Dodge", attacker)
                withClue("Artful Dodge cast should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Savannah Lions" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val block = game.declareBlockers(mapOf("Grizzly Bears" to listOf("Savannah Lions")))
                withClue("Artful Dodge must make the attacker unblockable") {
                    block.error.shouldNotBeNull()
                }
            }

            test("flashback cast resolves and exiles Artful Dodge") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Artful Dodge")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardOnBattlefield(1, "Savannah Lions")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val spellId = game.findCardsInGraveyard(1, "Artful Dodge").single()
                val creature = game.findPermanent("Savannah Lions").shouldNotBeNull()

                val cast = game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = spellId,
                        targets = listOf(ChosenTarget.Permanent(creature)),
                        useAlternativeCost = true,
                        alternativeCostType = AlternativeCostType.FLASHBACK,
                    )
                )
                withClue("Artful Dodge flashback should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                game.isInExile(1, "Artful Dodge") shouldBe true
            }
        }
    }
}
