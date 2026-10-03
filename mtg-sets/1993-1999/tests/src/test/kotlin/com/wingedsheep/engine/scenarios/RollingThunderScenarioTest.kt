package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Rolling Thunder (TMP 198) — semantic qualification for Izzet Position-1 Batch T.
 *
 * Pins the reusable X-value divided-damage cast-time rail:
 *  - any number of AnyTarget objects may be announced;
 *  - the announced allocation must sum to X;
 *  - every chosen target receives at least one;
 *  - X=0 permits no targets.
 */
class RollingThunderScenarioTest : ScenarioTestBase() {

    private fun TestGame.handCardId(playerNumber: Int, name: String): EntityId {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getHand(playerId).first {
            state.getEntity(it)?.get<CardComponent>()?.name == name
        }
    }

    init {
        test("X is divided across more than three targets and the announced shares resolve unchanged") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Rolling Thunder")
                .withLandsOnBattlefield(1, "Mountain", 7)
                .withCardOnBattlefield(2, "Llanowar Elves")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val rollingThunder = game.handCardId(1, "Rolling Thunder")
            val elf = game.findPermanent("Llanowar Elves") ?: error("elf missing")
            val bears = game.findPermanent("Grizzly Bears") ?: error("bears missing")
            val giant = game.findPermanent("Hill Giant") ?: error("giant missing")
            val lifeBefore = game.getLifeTotal(2)

            val cast = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = rollingThunder,
                    xValue = 5,
                    targets = listOf(
                        ChosenTarget.Permanent(elf),
                        ChosenTarget.Permanent(bears),
                        ChosenTarget.Permanent(giant),
                        ChosenTarget.Player(game.player2Id),
                    ),
                    damageDistribution = mapOf(
                        elf to 1,
                        bears to 1,
                        giant to 1,
                        game.player2Id to 2,
                    )
                )
            )
            withClue("Rolling Thunder should cast with four targets: ${cast.error}") { cast.error shouldBe null }

            game.resolveStack()

            game.getLifeTotal(2) shouldBe lifeBefore - 2
            game.findPermanent("Llanowar Elves") shouldBe null
            game.findPermanent("Grizzly Bears") shouldNotBe null
            game.findPermanent("Hill Giant") shouldNotBe null
        }

        test("cast-time distribution must sum to the chosen X") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Rolling Thunder")
                .withLandsOnBattlefield(1, "Mountain", 6)
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val rollingThunder = game.handCardId(1, "Rolling Thunder")
            val bears = game.findPermanent("Grizzly Bears") ?: error("bears missing")
            val cast = game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = rollingThunder,
                    xValue = 4,
                    targets = listOf(
                        ChosenTarget.Permanent(bears),
                        ChosenTarget.Player(game.player2Id),
                    ),
                    damageDistribution = mapOf(
                        bears to 1,
                        game.player2Id to 2,
                    )
                )
            )

            withClue("3 points cannot satisfy a Rolling Thunder cast with X=4") {
                cast.error shouldBe "Total distributed damage (3) must equal 4"
            }
        }

        test("X zero accepts zero targets and rejects a target") {
            val legal = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Rolling Thunder")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val legalCard = legal.handCardId(1, "Rolling Thunder")
            val legalCast = legal.execute(
                CastSpell(
                    playerId = legal.player1Id,
                    cardId = legalCard,
                    xValue = 0,
                    targets = emptyList()
                )
            )
            withClue("X=0 with no targets is legal: ${legalCast.error}") { legalCast.error shouldBe null }
            legal.resolveStack()

            val illegal = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Rolling Thunder")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(2, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val illegalCard = illegal.handCardId(1, "Rolling Thunder")
            val illegalCast = illegal.execute(
                CastSpell(
                    playerId = illegal.player1Id,
                    cardId = illegalCard,
                    xValue = 0,
                    targets = listOf(ChosenTarget.Player(illegal.player2Id))
                )
            )
            illegalCast.error shouldBe "Cannot choose targets when total divided damage is 0"
        }
    }
}
