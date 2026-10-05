package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Exact-pair combat-policy bridge for C2P05. These are isolated deterministic positions using the
 * frozen Pest and Monster card definitions and current production policies. They are not official
 * games, use no official seed namespace, create no claim, and expose no outcome.
 */
class PestCurrentPairC2P05PolicyCombatTest : ScenarioTestBase() {
    private val pestProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val monsterProfile = PestMonsterTronPolicy.profile

    private fun fixture(seed: Long) = scenario().withPlayers("Pest", "Monster").withRngSeed(seed)

    private fun pest(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, pestProfile)
    private fun monster(game: TestGame, player: EntityId = game.player2Id) =
        AIPlayer.create(cardRegistry, player, monsterProfile)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun actionSourceName(game: TestGame, action: Any): String? = when (action) {
        is CastSpell -> name(game, action.cardId)
        else -> null
    }

    init {
        test("Pest current policy attacks with real trample creature into an empty board") {
            val game = fixture(0xC205_0001L)
                .withCardOnBattlefield(1, "Pest Mascot", summoningSickness = false)
                .build()
            val attacker = game.findPermanent("Pest Mascot")!!
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

            val action = pest(game).chooseAction(game.state).shouldBeInstanceOf<DeclareAttackers>()
            action.attackers.containsKey(attacker) shouldBe true
            action.attackers[attacker] shouldBe game.player2Id
        }

        test("Monster current policy attacks with real Bramble Wurm trample") {
            val game = fixture(0xC205_0002L)
                .withCardOnBattlefield(1, "Bramble Wurm", summoningSickness = false)
                .build()
            val attacker = game.findPermanent("Bramble Wurm")!!
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

            val action = monster(game, game.player1Id).chooseAction(game.state)
                .shouldBeInstanceOf<DeclareAttackers>()
            action.attackers.containsKey(attacker) shouldBe true
            action.attackers[attacker] shouldBe game.player2Id
        }

        test("Monster current policy blocks real Pest trample with Boulderbranch Golem") {
            val game = fixture(0xC205_0003L)
                .withCardOnBattlefield(1, "Pest Mascot", summoningSickness = false)
                .withCardOnBattlefield(2, "Boulderbranch Golem", summoningSickness = false)
                .build()
            val attacker = game.findPermanent("Pest Mascot")!!
            val blocker = game.findPermanent("Boulderbranch Golem")!!
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.execute(DeclareAttackers(game.player1Id, mapOf(attacker to game.player2Id))).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

            val action = monster(game).chooseAction(game.state).shouldBeInstanceOf<DeclareBlockers>()
            action.blockers[blocker] shouldBe listOf(attacker)
        }

        test("Pest current policy uses real reach creature to block Monster flyer") {
            val game = fixture(0xC205_0004L)
                .withActivePlayer(2)
                .withCardOnBattlefield(1, "Generous Ent", summoningSickness = false)
                .withCardOnBattlefield(2, "Rooftop Percher", summoningSickness = false)
                .build()
            val attacker = game.findPermanent("Rooftop Percher")!!
            val blocker = game.findPermanent("Generous Ent")!!
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.execute(DeclareAttackers(game.player2Id, mapOf(attacker to game.player1Id))).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

            val action = pest(game).chooseAction(game.state).shouldBeInstanceOf<DeclareBlockers>()
            action.blockers[blocker] shouldBe listOf(attacker)
        }

        test("Monster current policy supplies the two real blockers required by Pest menace") {
            val game = fixture(0xC205_0005L)
                .withLifeTotal(2, 2)
                .withCardOnBattlefield(1, "Blood Researcher", summoningSickness = false)
                .withCardOnBattlefield(2, "Rooftop Percher", summoningSickness = false)
                .withCardOnBattlefield(2, "Rooftop Percher", summoningSickness = false)
                .build()
            val attacker = game.findPermanent("Blood Researcher")!!
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.execute(DeclareAttackers(game.player1Id, mapOf(attacker to game.player2Id))).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

            val action = monster(game).chooseAction(game.state).shouldBeInstanceOf<DeclareBlockers>()
            action.blockers.values.count { attacker in it } shouldBe 2
        }

        test("current post-block Pest window exposes exact preboard instant response") {
            val game = fixture(0xC205_0006L)
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(1, "Pest Mascot", summoningSickness = false)
                .withCardOnBattlefield(2, "Rooftop Percher", summoningSickness = false)
                .build()
            val attacker = game.findPermanent("Pest Mascot")!!
            val blocker = game.findPermanent("Rooftop Percher")!!
            game.advanceToPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.execute(DeclareAttackers(game.player1Id, mapOf(attacker to game.player2Id))).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            game.execute(DeclareBlockers(game.player2Id, mapOf(blocker to listOf(attacker)))).error shouldBe null

            game.state.priorityPlayerId shouldBe game.player1Id
            game.getLegalActions(1).any { actionSourceName(game, it.action) == "Cast Down" } shouldBe true
        }
    }
}
