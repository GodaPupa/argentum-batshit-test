package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario test for Farrel's Mantle (Fallen Empires).
 *
 * "Whenever enchanted creature attacks and isn't blocked, its controller may have it deal damage
 * equal to its power plus 2 to another target creature. If that player does, the attacking creature
 * assigns no combat damage this turn."
 *
 * The trigger used to be unreachable: `BecomesUnblockedEvent` was matched for the SELF binding
 * only, and the attachment detector had no route for it either, so the Mantle was a {2}{W}
 * do-nothing Aura. Nothing failed — an ability that never fires looks exactly like an ability whose
 * condition was not met.
 *
 * The Aura's controller chooses the trigger's target. If the Aura enchants an opponent's creature,
 * that creature's controller makes the separate "may" choice during resolution.
 */
class FarrelsMantleScenarioTest : ScenarioTestBase() {


    // A 3/3 so "power plus 2" is 5 — distinguishable from the base power and from the +2 alone.
    private val mantleBearer = card("Mantle Bearer") {
        manaCost = "{2}{W}"
        typeLine = "Creature — Human Soldier"
        power = 3
        toughness = 3
    }
    private val bystander = card("Mantle Bystander") {
        manaCost = "{4}"
        typeLine = "Creature — Golem"
        power = 0
        toughness = 6
    }

    init {
        cardRegistry.register(mantleBearer)
        cardRegistry.register(bystander)

        context("Farrel's Mantle") {

            test("an unblocked enchanted attacker may deal its power plus 2 instead of combat damage") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Mantle Bearer", summoningSickness = false)
                    .withCardAttachedTo(1, "Farrel's Mantle", "Mantle Bearer")
                    .withCardOnBattlefield(2, "Mantle Bystander")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Mantle Bearer" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers().error shouldBe null

                withClue("the trigger must actually fire — this is the bug that made the card inert") {
                    game.hasPendingDecision() shouldBe true
                }
                val target = game.findPermanent("Mantle Bystander")!!
                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                targetDecision.playerId shouldBe game.player1Id
                targetDecision.legalTargets.getValue(0) shouldContain target
                targetDecision.targetRequirements.single().minTargets shouldBe 1
                game.selectTargets(listOf(target)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
                game.answerYesNo(true).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 0

                withClue("3 power plus 2 = 5 damage to the chosen creature") {
                    game.state.getEntity(target)?.get<DamageComponent>()?.amount shouldBe 5
                }

                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                withClue("having done so, the attacker assigns no combat damage") {
                    game.getLifeTotal(2) shouldBe 20
                }
            }

            test("declining leaves ordinary combat damage intact") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Mantle Bearer", summoningSickness = false)
                    .withCardAttachedTo(1, "Farrel's Mantle", "Mantle Bearer")
                    .withCardOnBattlefield(2, "Mantle Bystander")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Mantle Bearer" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers().error shouldBe null

                val target = game.findPermanent("Mantle Bystander")!!
                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                targetDecision.playerId shouldBe game.player1Id
                targetDecision.legalTargets.getValue(0) shouldContain target
                targetDecision.targetRequirements.single().minTargets shouldBe 1
                game.selectTargets(listOf(target)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
                game.answerYesNo(false).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 0

                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                withClue("declined, so the 3/3 connects normally") {
                    game.getLifeTotal(2) shouldBe 17
                    val bystanderId = game.findPermanent("Mantle Bystander")!!
                    game.state.getEntity(bystanderId)?.get<DamageComponent>()?.amount ?: 0 shouldBe 0
                }
            }

            test("on an opponent's creature, it is that creature's controller who chooses") {
                val game = scenario()
                    .withPlayers("Mantle owner", "Creature owner")
                    // Player 2's creature, player 1's Aura. Player 2 attacks with it, so "its
                    // controller may" is player 2 — not the Aura's controller.
                    .withCardOnBattlefield(2, "Mantle Bearer", summoningSickness = false)
                    .withCardAttachedTo(1, "Farrel's Mantle", "Mantle Bearer")
                    .withCardOnBattlefield(1, "Mantle Bystander")
                    .withLifeTotal(1, 20)
                    .withActivePlayer(2)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Mantle Bearer" to 1)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers().error shouldBe null

                val target = game.findPermanent("Mantle Bystander")!!
                val targetDecision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
                withClue("the Aura's controller chooses the triggered ability's target") {
                    targetDecision.playerId shouldBe game.player1Id
                }
                targetDecision.legalTargets.getValue(0) shouldContain target
                targetDecision.targetRequirements.single().minTargets shouldBe 1
                game.selectTargets(listOf(target)).error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 1
                game.passPriority().error shouldBe null

                val mayDecision = game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                withClue("the enchanted creature's controller makes the resolution-time may choice") {
                    mayDecision.playerId shouldBe game.player2Id
                }
                game.answerYesNo(true).error shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.getPendingDecision() shouldBe null
                game.state.stack.size shouldBe 0
                game.state.getEntity(target)?.get<DamageComponent>()?.amount shouldBe 5
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.getLifeTotal(1) shouldBe 20
            }

            test("a blocked enchanted attacker does not trigger") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Mantle Bearer", summoningSickness = false)
                    .withCardAttachedTo(1, "Farrel's Mantle", "Mantle Bearer")
                    .withCardOnBattlefield(2, "Mantle Bystander")
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Mantle Bearer" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareBlockers(mapOf("Mantle Bystander" to listOf("Mantle Bearer")))

                withClue("\"isn't blocked\" is the whole condition") {
                    game.hasPendingDecision() shouldBe false
                }
            }
        }
    }
}
