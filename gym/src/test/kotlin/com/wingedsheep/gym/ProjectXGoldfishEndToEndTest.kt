package com.wingedsheep.gym

import com.wingedsheep.ai.solitaire.ProjectXSolitaireAgent
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.engine.core.LifeChangeReason
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/** Recognition regressions through the same GameEnvironment state boundary as the goldfish run. */
class ProjectXGoldfishEndToEndTest : ScenarioTestBase() {
    init {
        test("Gym path reports every primary-loop outcome without iterating the loop") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Essence Warden")
                .withCardOnBattlefield(1, "Falkenrath Noble")
                .build()
            val environment = GameEnvironment.create(cardRegistry)
            environment.restore(game.state, listOf(game.player1Id, game.player2Id))

            val outcome = ProjectXSolitaireAgent(cardRegistry, game.player1Id).outcome(environment.state)

            outcome.completeInfiniteEngine.shouldBeTrue()
            outcome.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
            outcome.arbitraryLife.shouldBeTrue()
            outcome.nobleDeterministicLethal.shouldBeTrue()
            environment.state.stack.isEmpty().shouldBeTrue()
        }

        test("Gym path keeps huge Feeder separate from immediate combat lethal while summoning sick") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder", summoningSickness = true)
                .withCardOnBattlefield(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .build()
            val environment = GameEnvironment.create(cardRegistry)
            environment.restore(game.state, listOf(game.player1Id, game.player2Id))

            val outcome = ProjectXSolitaireAgent(cardRegistry, game.player1Id).outcome(environment.state)

            outcome.completeInfiniteEngine.shouldBeTrue()
            outcome.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
            outcome.immediateDeterministicLethal.shouldBeFalse()
        }

        test("Gym path recognizes the validated Evolution Witness secondary loop symbolically") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Carrion Feeder")
                .withCardOnBattlefield(1, "Ivy Lane Denizen")
                .withCardOnBattlefield(1, "Evolution Witness")
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel")
                .withCardInHand(1, "Quirion Ranger")
                .build()
            val environment = GameEnvironment.create(cardRegistry)
            environment.restore(game.state, listOf(game.player1Id, game.player2Id))

            val outcome = ProjectXSolitaireAgent(cardRegistry, game.player1Id).outcome(environment.state)

            outcome.secondaryWitnessLoop.shouldBeTrue()
            outcome.arbitrarilyLargeCarrionFeeder.shouldBeTrue()
            environment.state.stack.isEmpty().shouldBeTrue()
        }

        test("terminal reporting distinguishes combat from triggered life loss") {
            val game = scenario().withPlayers().build()
            val ended = GameEndedEvent(game.player1Id, GameEndReason.LIFE_ZERO)

            classifyTerminal(
                listOf(DamageDealtEvent(game.player1Id, game.player2Id, 20, true, targetIsPlayer = true), ended),
                ended,
                deterministicComboAlreadyRecognized = false,
            ) shouldBe "COMBAT_LETHAL"
            classifyTerminal(
                listOf(LifeChangedEvent(game.player2Id, 1, 0, LifeChangeReason.LIFE_LOSS), ended),
                ended,
                deterministicComboAlreadyRecognized = false,
            ) shouldBe "TRIGGERED_OR_ABILITY_LETHAL"
        }

        test("Winding Way agent choice, accepted rules choice, and report label agree for both modes") {
            fun exercise(landCount: Int, expected: String): String {
                val game = scenario().withPlayers().withLandsOnBattlefield(1, "Forest", landCount).build()
                val environment = GameEnvironment.create(cardRegistry)
                environment.restore(game.state, listOf(game.player1Id, game.player2Id))
                val decision = ChooseOptionDecision(
                    id = "winding-$landCount",
                    playerId = game.player1Id,
                    prompt = "Choose creature or land",
                    context = DecisionContext(sourceName = "Winding Way"),
                    options = listOf("Creature", "Land"),
                )
                val response = ProjectXSolitaireAgent(cardRegistry, game.player1Id)
                    .respondToDecision(environment.state, decision) as OptionChosenResponse
                val telemetry = SelectionTelemetry("Winding Way", 2)
                recordAgentOptionChoice(telemetry, decision, response)
                acceptRulesModeChoice(telemetry)
                return renderSelectionLine(telemetry).also {
                    telemetry.agentChoice shouldBe expected
                    telemetry.rulesChoice shouldBe expected
                }
            }

            exercise(1, "Land") shouldBe "T2:agent=Land:rules=Land:hand=[]:grave=[]"
            exercise(3, "Creature") shouldBe "T2:agent=Creature:rules=Creature:hand=[]:grave=[]"
        }

        test("Herald availability reports only legally searchable missing roles") {
            val game = scenario().withPlayers()
                .withCardInLibrary(1, "Carrion Feeder")
                .withCardInLibrary(1, "Safehold Elite")
                .withCardInLibrary(1, "Ivy Lane Denizen")
                .build()
            isHeraldSearchableRole(game.state, game.player1Id, "Safehold Elite").shouldBeTrue()
            isHeraldSearchableRole(game.state, game.player1Id, "Ivy Lane Denizen").shouldBeTrue()
            isHeraldSearchableRole(game.state, game.player1Id, "Carrion Feeder").shouldBeFalse()
        }

        test("bottleneck telemetry separates color, Birchlore, tapland, and total-mana constraints") {
            fun categories(game: TestGame): Set<String> =
                classifyManaConstraints(
                    game.state, game.player1Id,
                    ProjectXSolitaireAgent(cardRegistry, game.player1Id).analyzer,
                    LegalActionEnumerator.create(cardRegistry), 2,
                ).map { it.category }.toSet()

            val color = scenario().withPlayers()
                .withCardInHand(1, "Carrion Feeder")
                .withLandsOnBattlefield(1, "Forest", 1)
                .build()
            categories(color) shouldBe setOf("GENUINE_COLOR_UNCASTABLE")

            val birchlore = scenario().withPlayers()
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel")
                .withCardInHand(1, "Carrion Feeder")
                .build()
            categories(birchlore) shouldBe setOf("BIRCHLORE_MANA_AVAILABLE")

            val quirion = scenario().withPlayers()
                .withCardOnBattlefield(1, "Quirion Ranger", tapped = true)
                .withCardOnBattlefield(1, "Birchlore Rangers")
                .withCardOnBattlefield(1, "Nettle Sentinel", tapped = true)
                .withCardOnBattlefield(1, "Forest")
                .withCardInHand(1, "Carrion Feeder")
                .build()
            categories(quirion) shouldBe setOf("QUIRION_SEQUENCE_AVAILABLE")

            val tapland = scenario().withPlayers()
                .withCardInHand(1, "Safehold Elite")
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Khalni Garden", tapped = true)
                .build()
            categories(tapland) shouldBe setOf("TAPPED_LAND_TEMPO")

            val total = scenario().withPlayers().withCardInHand(1, "Winding Way").build()
            categories(total) shouldBe setOf("INSUFFICIENT_TOTAL_MANA")
        }
    }
}
