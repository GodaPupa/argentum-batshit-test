package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seed-free Gate 9 policy probes for the exact sourced PinoIo_Cosmico Monster Tron opponent.
 *
 * No official Gate 9 seed namespace is allocated or consumed here.
 */
class IndustrialWasteMonsterTronPolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = baseProfile.copy(
        id = "monster-tron-gate-9-policy-audit",
        advisorModules = baseProfile.advisorModules + MonsterTronAdvisorModule,
        considerAdvisedManaAbilities = true,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun seeded() = scenario().withPlayers("Monster Tron", "Industrial Waste")
        .withRngSeed(0x1A57_B109L)

    init {
        test("Crop Rotation preserves live Tron pieces and tutors the missing piece") {
            val game = seeded()
                .withCardInHand(1, "Crop Rotation")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withCardInLibrary(1, "Urza's Tower")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Crop Rotation"
            val sacrificed = action.additionalCostPayment?.sacrificedPermanents?.singleOrNull()
            (sacrificed?.let { name(game, it) } in setOf("Forest", "Swamp")) shouldBe true

            game.execute(action).error shouldBe null
            game.resolveStack()
            val decision = game.getPendingDecision().shouldBeInstanceOf<SearchLibraryDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Urza's Tower"
        }

        test("Expedition Map tutors the missing Tron piece rather than a redundant land") {
            val game = seeded()
                .withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withCardInLibrary(1, "Urza's Tower")
                .withCardInLibrary(1, "Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Expedition Map"
            game.execute(action).error shouldBe null
            game.resolveStack()

            val decision = game.getPendingDecision().shouldBeInstanceOf<SearchLibraryDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Urza's Tower"
        }

        test("Nyxborn Hydra develops a material X threat with sufficient mana") {
            val game = seeded()
                .withCardInHand(1, "Nyxborn Hydra")
                .withLandsOnBattlefield(1, "Forest", 6)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Nyxborn Hydra"
            (action.xValue ?: 0) shouldBeGreaterThanOrEqualTo 3
        }

        test("Pulse of Murasa stabilizes low life by recurring a premium threat") {
            val game = seeded()
                .withLifeTotal(1, 5)
                .withCardInHand(1, "Pulse of Murasa")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInGraveyard(1, "Nyxborn Hydra")
                .withCardInGraveyard(1, "Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Pulse of Murasa"
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Card>()
            name(game, target.cardId) shouldBe "Nyxborn Hydra"
        }

        test("Bonder's Ornament spends the draw mode when four other mana sources are available") {
            val game = seeded()
                .withCardOnBattlefield(1, "Bonder's Ornament", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInLibrary(1, "Nyxborn Hydra")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Bonder's Ornament"
            val drawAbility = cardRegistry.getCard("Bonder's Ornament")!!.activatedAbilities
                .first { !it.isManaAbility }
            action.abilityId shouldBe drawAbility.id
        }

        test("Bojuka Bog points its ETB at the opponent graveyard, not its controller") {
            val game = seeded()
                .withCardInHand(1, "Bojuka Bog")
                .withCardInGraveyard(1, "Forest")
                .withCardInGraveyard(2, "Myr Retriever")
                .withCardInGraveyard(2, "Ichor Wellspring")
                .build()

            val play = ai(game).chooseAction(game.state).shouldBeInstanceOf<PlayLand>()
            name(game, play.cardId) shouldBe "Bojuka Bog"
            game.execute(play).error shouldBe null
            game.resolveStack()

            val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<TargetsResponse>()
            response.selectedTargets.values.flatten().single() shouldBe game.player2Id
        }
    }
}
