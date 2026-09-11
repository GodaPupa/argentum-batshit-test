package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** General priority policy for damage that is already pending on the stack. */
class PendingLethalAwarenessTest : ScenarioTestBase() {
    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId) = game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario().withPlayers().withRngSeed(0x1E7A_1A11L)

    init {
        test("unstoppable pending lethal strongly prefers passing over unrelated removal") {
            val game = seeded().withLifeTotal(2, 3)
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Cast Down")
                .withLandsOnBattlefield(1, "Mountain", 1).withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(2, "Grizzly Bears").build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("pending lethal that still requires protection is protected") {
            val game = seeded().withLifeTotal(2, 3)
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Counterspell")
                .withLandsOnBattlefield(1, "Mountain", 1).withLandsOnBattlefield(1, "Island", 2)
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.execute(PassPriority(game.player1Id)).error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Lightning Bolt").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Counterspell"
        }

        test("nonlethal pending damage does not suppress a worthwhile action") {
            val game = seeded().withLifeTotal(2, 4)
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Cast Down")
                .withLandsOnBattlefield(1, "Mountain", 1).withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(2, "Craw Wurm").build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Cast Down"
        }

        test("lethal triggered ability damage prefers passing") {
            val game = seeded().withLifeTotal(2, 1)
                .withCardOnBattlefield(1, "Kessig Flamebreather")
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Cast Down")
                .withLandsOnBattlefield(1, "Mountain", 1).withLandsOnBattlefield(1, "Swamp", 2)
                .withCardOnBattlefield(2, "Grizzly Bears").build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Lightning Bolt", bear).error shouldBe null

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("Game 8 structure passes with lethal activated-ability damage pending") {
            val game = seeded().withLifeTotal(2, 1)
                .withCardOnBattlefield(1, "Makeshift Munitions")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardInHand(1, "Cast Down")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(2, "Shambling Ghast").build()
            val munitions = game.findPermanent("Makeshift Munitions")!!
            val wellspring = game.findPermanent("Ichor Wellspring")!!
            val abilityId = cardRegistry.requireCard("Makeshift Munitions").script.activatedAbilities.single().id
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = munitions,
                    abilityId = abilityId,
                    targets = listOf(ChosenTarget.Player(game.player2Id)),
                    costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(wellspring)),
                    paymentStrategy = PaymentStrategy.AutoPay,
                )
            ).error shouldBe null

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("an action that changes a nonwinning pending stack remains strategically justified") {
            val game = seeded().withLifeTotal(2, 3)
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Counterspell")
                .withLandsOnBattlefield(1, "Mountain", 1).withLandsOnBattlefield(1, "Island", 2)
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).build()
            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.execute(PassPriority(game.player1Id)).error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Lightning Bolt").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null

            ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
        }
    }
}
