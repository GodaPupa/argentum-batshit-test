package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seed-free Gate 7 policy probes for the exact sourced Mono-Blue Terror opponent.
 *
 * These fixtures audit whether the production candidate can execute the deck's identity-critical
 * lines. They allocate no experimental namespace and are not matchup-strength evidence.
 */
class IndustrialWasteMonoBluePolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = baseProfile.copy(
        id = "mono-blue-terror-gate-7-policy-audit",
        advisorModules = baseProfile.advisorModules + MonoBlueTerrorAdvisorModule,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun handCard(game: TestGame, playerId: EntityId, cardName: String): EntityId =
        game.state.getHand(playerId).first { name(game, it) == cardName }

    private fun seeded() = scenario().withPlayers().withRngSeed(0x1A57_B107L)

    private fun putSpellOnStackAndPass(
        game: TestGame,
        cardName: String,
        targets: List<ChosenTarget> = emptyList(),
    ) {
        val card = handCard(game, game.player2Id, cardName)
        game.execute(CastSpell(playerId = game.player2Id, cardId = card, targets = targets)).error shouldBe null
        game.execute(PassPriority(game.player2Id)).error shouldBe null
    }

    init {
        test("production profile Thought Scours itself when self-mill advances Tolarian Terror") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(1, "Thought Scour")
                .withCardInHand(1, "Tolarian Terror")
                .withCardInLibrary(1, "Mental Note")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Island")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Thought Scour"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Player>().playerId shouldBe game.player1Id
        }

        test("production profile casts Tolarian Terror at its graveyard-reduced colored floor") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(1, "Tolarian Terror")
                .withCardInGraveyard(1, "Ponder")
                .withCardInGraveyard(1, "Brainstorm")
                .withCardInGraveyard(1, "Thought Scour")
                .withCardInGraveyard(1, "Mental Note")
                .withCardInGraveyard(1, "Counterspell")
                .withCardInGraveyard(1, "Dispel")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Tolarian Terror"
        }

        test("production profile casts Cryptic Serpent at its graveyard-reduced colored floor") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInHand(1, "Cryptic Serpent")
                .withCardInGraveyard(1, "Ponder")
                .withCardInGraveyard(1, "Brainstorm")
                .withCardInGraveyard(1, "Thought Scour")
                .withCardInGraveyard(1, "Mental Note")
                .withCardInGraveyard(1, "Counterspell")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Cryptic Serpent"
        }

        test("production profile uses Counterspell on a meaningful opposing engine spell") {
            val game = seeded()
                .withActivePlayer(2)
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInHand(1, "Counterspell")
                .withLandsOnBattlefield(2, "Swamp", 4)
                .withCardInHand(2, "Pactdoll Terror")
                .build()

            putSpellOnStackAndPass(game, "Pactdoll Terror")
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Counterspell"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Spell>()
        }

        test("production profile uses Force Spike when a large threat cannot pay the tax") {
            val game = seeded()
                .withActivePlayer(2)
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(1, "Force Spike")
                .withLandsOnBattlefield(2, "Swamp", 4)
                .withCardInHand(2, "Pactdoll Terror")
                .build()

            putSpellOnStackAndPass(game, "Pactdoll Terror")
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Force Spike"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Spell>()
        }

        test("production profile uses Dispel to stop immediately lethal burn") {
            val game = seeded()
                .withActivePlayer(2)
                .withLifeTotal(1, 3)
                .withLandsOnBattlefield(1, "Island", 1)
                .withCardInHand(1, "Dispel")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withCardInHand(2, "Lightning Bolt")
                .build()

            putSpellOnStackAndPass(
                game,
                "Lightning Bolt",
                listOf(ChosenTarget.Player(game.player1Id)),
            )
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Dispel"
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Spell>()
        }

        test("production profile deploys Murmuring Mystic before spending a same-turn Ponder") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Island", 5)
                .withCardInHand(1, "Murmuring Mystic")
                .withCardInHand(1, "Ponder")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Island")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Murmuring Mystic"
        }
    }
}
