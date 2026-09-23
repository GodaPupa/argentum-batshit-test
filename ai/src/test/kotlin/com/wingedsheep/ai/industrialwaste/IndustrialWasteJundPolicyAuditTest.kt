package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seed-free Gate 8 policy probes for the exact sourced Jund Wildfire opponent.
 *
 * No official Gate 8 seed namespace is allocated here. These fixtures only qualify the opponent
 * policy required by gate-8-jund-wildfire-readiness-v1.md.
 */
class IndustrialWasteJundPolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = baseProfile.copy(
        id = "jund-wildfire-gate-8-policy-audit",
        advisorModules = baseProfile.advisorModules + JundWildfireAdvisorModule,
        considerAdvisedManaAbilities = true,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun battlefieldCard(game: TestGame, playerId: EntityId, cardName: String): EntityId =
        game.state.getBattlefield(playerId).first { name(game, it) == cardName }

    private fun handCard(game: TestGame, playerId: EntityId, cardName: String): EntityId =
        game.state.getZone(playerId, Zone.HAND).first { name(game, it) == cardName }

    private fun seeded() = scenario().withPlayers().withRngSeed(0x1A57_B108L)

    init {
        test("Cleansing Wildfire targets its own indestructible bridge for value") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardOnBattlefield(1, "Drossforge Bridge")
                .withCardInHand(1, "Cleansing Wildfire")
                .withCardInLibrary(1, "Swamp")
                .withLandsOnBattlefield(2, "Island", 1)
                .build()

            val bridge = battlefieldCard(game, game.player1Id, "Drossforge Bridge")
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Cleansing Wildfire"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>().entityId shouldBe bridge
        }

        test("draw spell sacrifices Ichor Wellspring instead of live Familiar Lembas or Chrysalis") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Reckoner's Bargain")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(1, "Lembas")
                .withCardOnBattlefield(1, "Refurbished Familiar")
                .withCardOnBattlefield(1, "Writhing Chrysalis")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Reckoner's Bargain"
            val sacrificed = action.additionalCostPayment?.sacrificedPermanents?.singleOrNull()
            sacrificed?.let { name(game, it) } shouldBe "Ichor Wellspring"
        }

        test("Makeshift Munitions converts a Chrysalis Spawn into lethal reach") {
            val game = seeded()
                .withLifeTotal(2, 1)
                .withCardOnBattlefield(1, "Makeshift Munitions")
                .withLandsOnBattlefield(1, "Mountain", 4)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Writhing Chrysalis")
                .build()

            val chrysalis = handCard(game, game.player1Id, "Writhing Chrysalis")
            game.execute(CastSpell(playerId = game.player1Id, cardId = chrysalis)).error shouldBe null
            game.resolveStack()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Makeshift Munitions"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
            val sacrificed = action.costPayment?.sacrificedPermanents?.singleOrNull()
            sacrificed?.let { name(game, it) } shouldBe "Eldrazi Spawn"
        }

        test("Cast Down removes the opposing payoff instead of expendable fodder") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withCardInHand(1, "Cast Down")
                .withCardOnBattlefield(2, "Shambling Ghast")
                .withCardOnBattlefield(2, "Pactdoll Terror")
                .build()

            val pactdoll = battlefieldCard(game, game.player2Id, "Pactdoll Terror")
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Cast Down"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>().entityId shouldBe pactdoll
        }

        test("metalcraft Galvanic Blast sends four damage to a lethal opponent") {
            val game = seeded()
                .withLifeTotal(2, 4)
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(1, "Lembas")
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInHand(1, "Galvanic Blast")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Galvanic Blast"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }
    }
}
