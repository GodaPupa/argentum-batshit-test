package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.ai.insight.AiDecisionInsight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Decision regressions: real engine, unchanged cards, deterministic production profile. */
class BoneShardsJointValuationTest : ScenarioTestBase() {
    private fun seeded() = scenario().withPlayers().withRngSeed(2026101904L)
    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
    private fun TestGame.resolveStackWith(player: AIPlayer) {
        resolveStack()
        while (state.pendingDecision != null) {
            val decision = requireNotNull(state.pendingDecision)
            submitDecision(player.respondToDecision(state, decision)).error shouldBe null
            resolveStack()
        }
    }

    init {
        test("preserves a lone Warden rather than paying it to invalidate the only target") {
            val game = seeded().withTurnNumber(30)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(1, "Essence Warden").build()
            val insights = mutableListOf<AiDecisionInsight>()
            val player = AIPlayer.create(cardRegistry, game.player1Id, AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
                insightSink = { _, insight -> insights += insight })
            player.chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
            val option = insights.last().options.single { it.cardName == "Bone Shards" }
            option.productionAdmissible shouldBe false
            option.friendlyRemovalAudit!!.additionalCosts.single().entities.single().id shouldBe
                option.friendlyRemovalAudit!!.targetId
        }

        test("keeps opposing removal and productive Carrier Thrall sacrifice") {
            val game = seeded().withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(1, "Carrier Thrall")
                .withCardOnBattlefield(2, "Craw Wurm").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            action.targets shouldBe listOf(ChosenTarget.Permanent(game.findPermanent("Craw Wurm")!!))
            action.additionalCostPayment!!.sacrificedPermanents shouldBe listOf(game.findPermanent("Carrier Thrall")!!)
        }

        test("keeps discard removal without sacrificing a valuable friendly creature") {
            val game = seeded().withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards").withCardInHand(1, "Forest")
                .withCardOnBattlefield(1, "Blood Researcher")
                .withCardOnBattlefield(2, "Craw Wurm").build()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            action.targets shouldBe listOf(ChosenTarget.Permanent(game.findPermanent("Craw Wurm")!!))
            action.additionalCostPayment!!.discardedCards shouldBe game.findCardsInHand(1, "Forest")
            action.additionalCostPayment!!.sacrificedPermanents shouldBe emptyList()
        }

        test("permits sacrificing the target when its death creates lethal") {
            val game = seeded().withLandsOnBattlefield(1, "Swamp", 1)
                .withLifeTotal(2, 1).withCardInHand(1, "Bone Shards")
                .withCardOnBattlefield(1, "Blood Artist").build()
            val player = ai(game)
            val action = player.chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            val artist = game.findPermanent("Blood Artist")!!
            action.targets shouldBe listOf(ChosenTarget.Permanent(artist))
            action.additionalCostPayment!!.sacrificedPermanents shouldBe listOf(artist)
            game.execute(action).error shouldBe null
            game.resolveStackWith(player)
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player1Id
        }

        test("does not invent a target when neither battlefield has a creature") {
            val game = seeded().withLandsOnBattlefield(1, "Swamp", 1)
                .withCardInHand(1, "Bone Shards").withCardInHand(1, "Cast Down").build()
            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }
    }
}
