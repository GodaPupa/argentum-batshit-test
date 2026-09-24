package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SearchCardInfo
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetRequirementInfo
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Seed-free Gate 11 policy probes for Drinkme's frozen Spy Combo opponent. */
class IndustrialWasteSpyComboPolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = baseProfile.copy(
        id = "spy-combo-gate-11-policy-audit",
        advisorModules = baseProfile.advisorModules + SpyComboAdvisorModule,
        considerAdvisedManaAbilities = true,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario()
        .withPlayers("Spy Combo", "Industrial Waste")
        .withRngSeed(0x5A71_11C0L)

    init {
        test("Land Grant takes the free landless-hand line before committing a slow payoff") {
            val game = seeded()
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Balustrade Spy")
                .withCardInLibrary(1, "Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Land Grant"
            action.useAlternativeCost shouldBe true
        }

        test("Gatecreeper Vine search prioritizes Swamp when black combo pieces are stranded") {
            val game = seeded()
                .withCardInHand(1, "Balustrade Spy")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .build()
            val library = game.state.getLibrary(game.player1Id)
            val forest = library.single { name(game, it) == "Forest" }
            val swamp = library.single { name(game, it) == "Swamp" }
            val decision = SearchLibraryDecision(
                id = "gatecreeper-search",
                playerId = game.player1Id,
                prompt = "Search for a basic land card or Gate card",
                context = DecisionContext(sourceName = "Gatecreeper Vine"),
                options = listOf(forest, swamp),
                minSelections = 0,
                maxSelections = 1,
                cards = mapOf(
                    forest to SearchCardInfo("Forest", "", "Basic Land — Forest"),
                    swamp to SearchCardInfo("Swamp", "", "Basic Land — Swamp"),
                ),
                filterDescription = "basic land card or Gate card",
            )
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Swamp"
        }

        test("Quirion Ranger points an untap at Overgrown Battlement") {
            val game = seeded()
                .withCardOnBattlefield(1, "Quirion Ranger")
                .withCardOnBattlefield(1, "Overgrown Battlement", tapped = true)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInHand(1, "Balustrade Spy")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Quirion Ranger"
            val target = action.targets.single()
            val targetId = (target as com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent).entityId
            name(game, targetId) shouldBe "Overgrown Battlement"
        }

        test("Wall of Roots mana ability is actively used for a stranded payoff") {
            val game = seeded()
                .withCardOnBattlefield(1, "Wall of Roots")
                .withCardInHand(1, "Balustrade Spy")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Wall of Roots"
        }

        test("Overgrown Battlement scales a defender board before a payoff") {
            val game = seeded()
                .withCardOnBattlefield(1, "Overgrown Battlement")
                .withCardOnBattlefield(1, "Wall of Roots")
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withCardInHand(1, "Balustrade Spy")
                .build()
            val battlement = game.findPermanent("Overgrown Battlement")!!
            game.state.getEntity(battlement)?.has<TappedComponent>() shouldBe false

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Overgrown Battlement"
        }

        test("Saruli Caretaker converts a spare creature into combo mana") {
            val game = seeded()
                .withCardOnBattlefield(1, "Saruli Caretaker")
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withCardInHand(1, "Balustrade Spy")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Saruli Caretaker"
        }

        test("one-mana forestcycling is used in a one-land low-resource hand") {
            val game = seeded()
                .withCardInHand(1, "Generous Ent")
                .withLandsOnBattlefield(1, "Forest", 1)
                .build()

            ai(game).chooseAction(game.state).shouldBeInstanceOf<TypecycleCard>()
        }

        test("Winding Way chooses creature for the creature-dense Spy shell") {
            val game = seeded().build()
            val decision = ChooseOptionDecision(
                id = "spy-winding-way",
                playerId = game.player1Id,
                prompt = "Choose creature or land",
                context = DecisionContext(sourceName = "Winding Way"),
                options = listOf("Creature", "Land"),
            )
            ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<OptionChosenResponse>().optionIndex shouldBe 0
        }

        test("Lead the Stampede keeps every creature offered") {
            val game = seeded()
                .withCardInLibrary(1, "Wall of Roots")
                .withCardInLibrary(1, "Balustrade Spy")
                .withCardInLibrary(1, "Forest")
                .build()
            val options = game.state.getLibrary(game.player1Id)
            val decision = SelectCardsDecision(
                id = "spy-lead",
                playerId = game.player1Id,
                prompt = "Reveal any number of creature cards",
                context = DecisionContext(sourceName = "Lead the Stampede"),
                options = options,
                minSelections = 0,
                maxSelections = 3,
            )
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            response.selectedCards.mapNotNull { name(game, it) }.toSet() shouldBe
                setOf("Wall of Roots", "Balustrade Spy")
        }

        test("Mesmeric Fiend targets the opponent and takes Ashnod's Altar before lower-value cards") {
            val game = seeded()
                .withCardInHand(2, "Ashnod's Altar")
                .withCardInHand(2, "Candy Trail")
                .build()
            val requirement = TargetRequirementInfo(index = 0, description = "target opponent")
            val targetDecision = ChooseTargetsDecision(
                id = "fiend-target",
                playerId = game.player1Id,
                prompt = "Choose target opponent",
                context = DecisionContext(sourceName = "Mesmeric Fiend"),
                targetRequirements = listOf(requirement),
                legalTargets = mapOf(0 to listOf(game.player2Id)),
            )
            val targetResponse = ai(game).respondToDecision(game.state, targetDecision)
                .shouldBeInstanceOf<TargetsResponse>()
            targetResponse.selectedTargets[0] shouldContain game.player2Id

            val opponentHand = game.state.getHand(game.player2Id)
            val selection = SelectCardsDecision(
                id = "fiend-card",
                playerId = game.player1Id,
                prompt = "Choose a nonland card to exile",
                context = DecisionContext(sourceName = "Mesmeric Fiend"),
                options = opponentHand,
                minSelections = 1,
                maxSelections = 1,
            )
            val response = ai(game).respondToDecision(game.state, selection)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Ashnod's Altar"
        }

        test("Balustrade Spy deliberately targets its controller for self-mill") {
            val game = seeded().build()
            val requirement = TargetRequirementInfo(index = 0, description = "target player")
            val decision = ChooseTargetsDecision(
                id = "spy-target",
                playerId = game.player1Id,
                prompt = "Choose target player",
                context = DecisionContext(sourceName = "Balustrade Spy"),
                targetRequirements = listOf(requirement),
                legalTargets = mapOf(0 to listOf(game.player1Id, game.player2Id)),
            )
            val response = ai(game).respondToDecision(game.state, decision)
                .shouldBeInstanceOf<TargetsResponse>()
            response.selectedTargets[0] shouldContain game.player1Id
        }


        test("Mesmeric Fiend policy exiles the priority engine card and linked return restores it") {
            val game = seeded()
                .withCardInHand(1, "Mesmeric Fiend")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(2, "Ashnod's Altar")
                .withCardInHand(2, "Candy Trail")
                .build()

            game.castSpell(1, "Mesmeric Fiend").error shouldBe null
            game.resolveStack()

            repeat(3) {
                val pending = game.getPendingDecision() ?: return@repeat
                game.submitDecision(ai(game).respondToDecision(game.state, pending)).error shouldBe null
                game.resolveStack()
            }

            game.isInExile(2, "Ashnod's Altar") shouldBe true
            game.isInHand(2, "Ashnod's Altar") shouldBe false

            val fiend = game.findPermanent("Mesmeric Fiend")!!
            game.castSpell(1, "Lightning Bolt", fiend).error shouldBe null
            game.resolveStack()

            game.isInExile(2, "Ashnod's Altar") shouldBe false
            game.isInHand(2, "Ashnod's Altar") shouldBe true
        }

        test("Lotleth Giant policy converts a lethal creature graveyard into the kill") {
            val game = seeded()
                .withCardInHand(1, "Lotleth Giant")
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withCardInGraveyard(1, "Elvish Mystic")
                .withCardInGraveyard(1, "Fyndhorn Elves")
                .withCardInGraveyard(1, "Llanowar Elves")
                .withLifeTotal(2, 3)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Lotleth Giant"
            game.execute(action).error shouldBe null
            game.resolveStack()

            game.getLifeTotal(2) shouldBe 0
        }

        test("Dread Return preserves the kill while sacrificing three low-value bodies") {
            val game = seeded()
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withCardOnBattlefield(1, "Mesmeric Fiend")
                .withCardOnBattlefield(1, "Saruli Caretaker")
                .withCardOnBattlefield(1, "Overgrown Battlement")
                .withCardInGraveyard(1, "Lotleth Giant")
                .withCardInGraveyard(1, "Balustrade Spy")
                .build()
            val bodies = game.state.getBattlefield(game.player1Id)
            val sacrifice = SelectCardsDecision(
                id = "dread-return-sacrifice",
                playerId = game.player1Id,
                prompt = "Sacrifice three creatures",
                context = DecisionContext(sourceName = "Dread Return"),
                options = bodies,
                minSelections = 3,
                maxSelections = 3,
            )
            val response = ai(game).respondToDecision(game.state, sacrifice)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            response.selectedCards.size shouldBe 3
            response.selectedCards.mapNotNull { name(game, it) }.toSet() shouldBe
                setOf("Gatecreeper Vine", "Mesmeric Fiend", "Saruli Caretaker")

            val graveyard = game.state.getGraveyard(game.player1Id)
            val lotleth = graveyard.single { name(game, it) == "Lotleth Giant" }
            val spy = graveyard.single { name(game, it) == "Balustrade Spy" }
            val lotlethScore = SpyDreadReturnAdvisor.targetPreference(game.state, lotleth, game.player1Id)!!
            val spyScore = SpyDreadReturnAdvisor.targetPreference(game.state, spy, game.player1Id)!!
            (lotlethScore > spyScore) shouldBe true
        }
    }
}
