package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.CardsDrawnThisTurnComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seed-free probes for the exact engines in the sourced Madness Burn opponent.
 *
 * These fixtures decide whether the frozen v0 opponent used by Gate 4 is a credible pilot for the
 * deck. They do not replay or allocate experimental seeds and cannot be used as matchup evidence.
 */
class IndustrialWasteMadnessBurnPolicyAuditTest : ScenarioTestBase() {

    private fun ai(game: TestGame, profile: AiProfile = AiProfile.LEGACY_V0) =
        AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun cardName(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun seeded() = scenario().withPlayers().withRngSeed(0x1A57_E001L)

    init {
        test("v0 discards Sneaky Snacker to Grab the Prize before the third draw") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInHand(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()
            game.state = game.state.updateEntity(game.player1Id) {
                it.with(CardsDrawnThisTurnComponent(1))
            }

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Grab the Prize"
            cardName(game, action.additionalCostPayment?.discardedCards?.single()!!) shouldBe
                "Sneaky Snacker"
        }

        test("v0 deploys Guttersnipe before spending the spells that trigger it") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 5)
                .withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Guttersnipe"
        }

        test("v0 deploys Flamebreather before a same-turn Grab the Prize") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withCardInHand(1, "Kessig Flamebreather")
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .withCardInLibrary(1, "Mountain")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            cardName(game, action.cardId) shouldBe "Kessig Flamebreather"
        }

        test("v0 converts Fireblast and Lava Dart only when their land costs are lethal") {
            val fireblast = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(1, "Fireblast")
                .withLifeTotal(2, 4)
                .build()
            val fireblastAction = ai(fireblast).chooseAction(fireblast.state)
                .shouldBeInstanceOf<CastSpell>()
            cardName(fireblast, fireblastAction.cardId) shouldBe "Fireblast"
            fireblastAction.additionalCostPayment?.sacrificedPermanents?.size shouldBe 2

            val dart = seeded()
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardInGraveyard(1, "Lava Dart")
                .withLifeTotal(2, 1)
                .build()
            val dartAction = ai(dart).chooseAction(dart.state).shouldBeInstanceOf<CastSpell>()
            cardName(dart, dartAction.cardId) shouldBe "Lava Dart"
            dartAction.additionalCostPayment?.sacrificedPermanents?.size shouldBe 1
        }
    }
}
