package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seed-free Gate 6 policy probes for the exact sourced Grixis Affinity opponent.
 *
 * These are readiness fixtures only. They are not matchup evidence and do not consume or reserve
 * any Industrial Waste experimental seed namespace.
 */
class IndustrialWasteGrixisPolicyAuditTest : ScenarioTestBase() {
    private val profile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun seeded() = scenario().withPlayers().withRngSeed(0x1A57_AFF1L)

    init {
        test("Grixis deploys Refurbished Familiar at the affinity colored floor") {
            val game = seeded()
                .withCardInHand(1, "Refurbished Familiar")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(1, "Blood Fountain")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Refurbished Familiar"
        }

        test("Grixis deploys Utrom Monitor at the affinity colored floor") {
            val game = seeded()
                .withCardInHand(1, "Utrom Monitor")
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(1, "Blood Fountain")
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Utrom Monitor"
        }

        test("reduced-rate Galvanic Blast is held when visible reach is not lethal") {
            val game = seeded()
                .withLifeTotal(2, 6)
                .withCardInHand(1, "Galvanic Blast")
                .withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(1, "Vault of Whispers")
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<PassPriority>()
        }

        test("Galvanic Blast remains available when conservative visible reach is lethal") {
            val game = seeded()
                .withLifeTotal(2, 4)
                .withCardInHand(1, "Galvanic Blast")
                .withCardInHand(1, "Galvanic Blast")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Galvanic Blast"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("Nihil Spellbomb answers concrete opposing recursion already on the stack") {
            val game = seeded()
                .withActivePlayer(2)
                .withCardOnBattlefield(1, "Nihil Spellbomb")
                .withCardInHand(2, "Unearth")
                .withCardInGraveyard(2, "Myr Retriever")
                .withLandsOnBattlefield(2, "Swamp", 1)
                .build()

            game.castSpellTargetingGraveyardCard(2, "Unearth", 2, "Myr Retriever").error shouldBe null
            game.execute(PassPriority(game.player2Id)).error shouldBe null

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Nihil Spellbomb"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player2Id
        }

        test("Krark-Clan Shaman cashes Wellspring when the sweep hits opposing creatures") {
            val game = seeded()
                .withCardOnBattlefield(1, "Krark-Clan Shaman")
                .withCardOnBattlefield(1, "Ichor Wellspring")
                .withCardOnBattlefield(2, "Myr Retriever")
                .withCardOnBattlefield(2, "Myr Kinsmith")
                .withCardInLibrary(1, "Swamp")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Krark-Clan Shaman"
            name(game, action.costPayment!!.sacrificedPermanents.single()) shouldBe "Ichor Wellspring"
        }
    }
}
