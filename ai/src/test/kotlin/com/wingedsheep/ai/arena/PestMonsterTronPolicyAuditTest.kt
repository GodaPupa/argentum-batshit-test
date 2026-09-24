package com.wingedsheep.ai.arena

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SearchCardInfo
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Seed-free public-state policy probes for mehanske's exact Monster Tron identity.
 * These tests consume no official seed namespace and expose no game outcome.
 */
class PestMonsterTronPolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = baseProfile.copy(
        id = "pest-monster-tron-policy-audit",
        advisorModules = baseProfile.advisorModules + PestMonsterTronAdvisorModule,
        considerAdvisedManaAbilities = true,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)

    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name

    private fun seeded() = scenario().withPlayers("Monster Tron", "Pest Control")
        .withRngSeed(0x5EED_1E55L)

    init {
        test("Crop Rotation preserves a live Tron piece then selects the missing piece") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .build()

            val forest = game.state.getBattlefield(game.player1Id)
                .single { name(game, it) == "Forest" }
            val mine = game.state.getBattlefield(game.player1Id)
                .single { name(game, it) == "Urza's Mine" }

            val sacrifice = SelectCardsDecision(
                id = "crop-sacrifice",
                playerId = game.player1Id,
                prompt = "Sacrifice a land",
                context = DecisionContext(sourceName = "Crop Rotation"),
                options = listOf(mine, forest),
                minSelections = 1,
                maxSelections = 1,
            )
            ai(game).respondToDecision(game.state, sacrifice)
                .shouldBeInstanceOf<CardsSelectedResponse>()
                .selectedCards.single() shouldBe forest

            val tower = EntityId.of("tower-option")
            val redundantMine = EntityId.of("mine-option")
            val search = SearchLibraryDecision(
                id = "crop-search",
                playerId = game.player1Id,
                prompt = "Search for a land",
                context = DecisionContext(sourceName = "Crop Rotation"),
                options = listOf(redundantMine, tower),
                minSelections = 1,
                maxSelections = 1,
                cards = mapOf(
                    redundantMine to SearchCardInfo("Urza's Mine", "", "Land"),
                    tower to SearchCardInfo("Urza's Tower", "", "Land"),
                ),
                filterDescription = "land card",
            )
            ai(game).respondToDecision(game.state, search)
                .shouldBeInstanceOf<CardsSelectedResponse>()
                .selectedCards.single() shouldBe tower
        }

        test("Expedition Map activates and tutors the missing Tron piece") {
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

            val decision = game.getPendingDecision() ?: error("Expedition Map search decision missing")
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Urza's Tower"
        }

        test("Ancient Stirrings selection prefers a missing Tron piece over a redundant colorless card") {
            val game = seeded()
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .build()
            val tower = EntityId.of("stirrings-tower")
            val map = EntityId.of("stirrings-map")
            val decision = SearchLibraryDecision(
                id = "stirrings-choice",
                playerId = game.player1Id,
                prompt = "Choose a colorless card",
                context = DecisionContext(sourceName = "Ancient Stirrings"),
                options = listOf(map, tower),
                minSelections = 1,
                maxSelections = 1,
                cards = mapOf(
                    map to SearchCardInfo("Expedition Map", "{1}", "Artifact"),
                    tower to SearchCardInfo("Urza's Tower", "", "Land"),
                ),
                filterDescription = "colorless card",
            )

            ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
                .selectedCards.single() shouldBe tower
        }

        test("Bonder's Ornament takes the draw mode with four other mana sources and a low hand") {
            val game = seeded()
                .withCardOnBattlefield(1, "Bonder's Ornament", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInLibrary(1, "Bramble Wurm")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Bonder's Ornament"
            val drawAbility = cardRegistry.getCard("Bonder's Ornament")!!.activatedAbilities
                .first { !it.isManaAbility }
            action.abilityId shouldBe drawAbility.id
        }

        test("Bojuka Bog targets the opponent graveyard") {
            val game = seeded()
                .withCardInHand(1, "Bojuka Bog")
                .withCardInGraveyard(1, "Forest")
                .withCardInGraveyard(2, "Carrier Thrall")
                .withCardInGraveyard(2, "Blood Researcher")
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
