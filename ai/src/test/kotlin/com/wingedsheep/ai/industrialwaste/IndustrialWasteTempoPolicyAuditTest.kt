package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteTempoPolicyAuditTest : ScenarioTestBase() {
    private val profile = AiProfile.LEGACY_V0.copy(
        id = "industrial-waste-tempo-a-readiness",
        advisorModules = listOf(IndustrialWasteAdvisorModule),
        considerAdvisedManaAbilities = true,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    init {
        test("Ancient Grudge prioritizes Myr Enforcer over low-value artifacts") {
            val game = scenario().withPlayers().withRngSeed(0x7E4F_0001L)
                .withCardInHand(1, "Ancient Grudge")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(2, "Myr Enforcer")
                .withCardOnBattlefield(2, "Ichor Wellspring")
                .withCardOnBattlefield(2, "Vault of Whispers")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Ancient Grudge"
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>()
            name(game, target.entityId) shouldBe "Myr Enforcer"
        }

        test("Ancient Grudge prioritizes Refurbished Familiar over Wellspring and artifact land") {
            val game = scenario().withPlayers().withRngSeed(0x7E4F_0002L)
                .withCardInHand(1, "Ancient Grudge")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(2, "Refurbished Familiar")
                .withCardOnBattlefield(2, "Ichor Wellspring")
                .withCardOnBattlefield(2, "Vault of Whispers")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Ancient Grudge"
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>()
            name(game, target.entityId) shouldBe "Refurbished Familiar"
        }
    }
}
