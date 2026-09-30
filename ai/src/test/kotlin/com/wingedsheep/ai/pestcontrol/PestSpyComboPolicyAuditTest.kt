package com.wingedsheep.ai.pestcontrol

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.PestSpyComboPolicy
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
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
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed excluded regression fixtures for the Dr_dej96 Spy pilot; never sampled matchup evidence. */
class PestSpyComboPolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = PestSpyComboPolicy.profile

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario()
        .withPlayers("Dr_dej96 Spy Combo", "Pest Control")
        .withRngSeed(0x5045_5354_5350L)

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
            action.manaColorChoice shouldBe Color.BLACK
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

        test("Mesmeric Fiend targets the opponent and takes Weather the Storm before lower-value cards") {
            val game = seeded()
                .withCardInHand(2, "Weather the Storm")
                .withCardInHand(2, "Carrier Thrall")
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
            targetResponse.selectedTargets[0].orEmpty() shouldContain game.player2Id

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
            name(game, response.selectedCards.single()) shouldBe "Weather the Storm"
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
            response.selectedTargets[0].orEmpty() shouldContain game.player1Id
        }


        test("Mesmeric Fiend policy exiles the priority engine card and linked return restores it") {
            val game = seeded()
                .withCardInHand(1, "Mesmeric Fiend")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withCardInHand(2, "Weather the Storm")
                .withCardInHand(2, "Carrier Thrall")
                .build()

            game.castSpell(1, "Mesmeric Fiend").error shouldBe null
            game.resolveStack()

            repeat(3) {
                val pending = game.getPendingDecision() ?: return@repeat
                game.submitDecision(ai(game).respondToDecision(game.state, pending)).error shouldBe null
                game.resolveStack()
            }

            game.isInExile(2, "Weather the Storm") shouldBe true
            game.isInHand(2, "Weather the Storm") shouldBe false

            val fiend = game.findPermanent("Mesmeric Fiend")!!
            game.castSpell(1, "Lightning Bolt", fiend).error shouldBe null
            game.resolveStack()

            game.isInExile(2, "Weather the Storm") shouldBe false
            game.isInHand(2, "Weather the Storm") shouldBe true
        }

        test("Lotleth Giant policy converts a lethal creature graveyard into the kill") {
            val game = seeded()
                .withCardInHand(1, "Lotleth Giant")
                .withLandsOnBattlefield(1, "Swamp", 7)
                .withCardInGraveyard(1, "Gatecreeper Vine")
                .withCardInGraveyard(1, "Wall of Roots")
                .withCardInGraveyard(1, "Quirion Ranger")
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
            val registry = CardAdvisorRegistry()
            profile.advisorModules.forEach { it.register(registry) }
            val advisor = registry.getAdvisor("Dread Return")!!
            val lotlethScore = advisor.targetPreference(game.state, lotleth, game.player1Id)!!
            val spyScore = advisor.targetPreference(game.state, spy, game.player1Id)!!
            (lotlethScore > spyScore) shouldBe true
        }

        test("Dread Return ranks every duplicate body and executes the selected flashback payment") {
            val game = seeded()
                .withCardOnBattlefield(1, "Overgrown Battlement")
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withCardInGraveyard(1, "Dread Return")
                .withCardInGraveyard(1, "Balustrade Spy")
                .withCardInGraveyard(1, "Lotleth Giant")
                .build()
            val vines = game.findPermanents("Gatecreeper Vine").toSet()
            val giant = game.findCardsInGraveyard(1, "Lotleth Giant").single()
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Dread Return"
            action.alternativeCostType shouldBe AlternativeCostType.FLASHBACK
            action.additionalCostPayment!!.sacrificedPermanents.toSet() shouldBe vines
            action.targets.single().shouldBeInstanceOf<ChosenTarget.Card>().cardId shouldBe giant
            game.execute(action).error shouldBe null
            game.resolveStack()
            game.findPermanents("Gatecreeper Vine").size shouldBe 0
            game.findPermanents("Overgrown Battlement").size shouldBe 1
            game.findPermanents("Lotleth Giant").size shouldBe 1
            game.isInExile(1, "Dread Return") shouldBe true
            game.getLifeTotal(2) shouldBe 16
        }

        test("Quirion pays with the tapped Forest even when an untapped Forest is listed first") {
            val game = seeded()
                .withCardOnBattlefield(1, "Quirion Ranger")
                .withCardOnBattlefield(1, "Overgrown Battlement", tapped = true)
                .withCardOnBattlefield(1, "Forest")
                .withCardOnBattlefield(1, "Forest", tapped = true)
                .withCardInHand(1, "Balustrade Spy")
                .build()
            val tappedForest = game.findPermanents("Forest").single {
                game.state.getEntity(it)!!.has<TappedComponent>()
            }
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Quirion Ranger"
            action.costPayment!!.bouncedPermanents shouldBe listOf(tappedForest)
            game.execute(action).error shouldBe null
            game.resolveStack()
            game.state.getHand(game.player1Id) shouldContain tappedForest
            game.state.getEntity(game.findPermanent("Overgrown Battlement")!!)!!.has<TappedComponent>() shouldBe false
        }

        test("null advisor hooks preserve the default profile action and ordinary mana filtering") {
            val game = seeded()
                .withCardOnBattlefield(1, "Wall of Roots")
                .withCardInHand(1, "Balustrade Spy")
                .build()
            val noOp = object : CardAdvisorModule {
                override fun register(registry: CardAdvisorRegistry) {
                    registry.register(object : CardAdvisor {
                        override val cardNames = setOf("Wall of Roots", "Balustrade Spy")
                    })
                }
            }
            val before = game.state
            val baseline = AIPlayer.create(cardRegistry, game.player1Id, baseProfile).chooseAction(before)
            baseline.shouldBeInstanceOf<PassPriority>()
            val noOpProfile = baseProfile.copy(advisorModules = baseProfile.advisorModules + noOp)
            AIPlayer.create(cardRegistry, game.player1Id, noOpProfile).chooseAction(before) shouldBe baseline
            noOpProfile.considerAdvisedManaAbilities shouldBe false
            baseProfile.considerAdvisedManaAbilities shouldBe false
            game.state shouldBe before
        }

        test("Spy choice is invariant to unknown opposing hand identities and future library order") {
            val game = seeded()
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Balustrade Spy")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Lotleth Giant")
                .withCardInLibrary(1, "Wall of Roots")
                .withCardInHand(2, "Cast Down")
                .withCardInLibrary(2, "Carrier Thrall")
                .withCardInLibrary(2, "Weather the Storm")
                .build()
            val original = game.state
            val handId = original.getHand(game.player2Id).single()
            val libraryId = original.getLibrary(game.player2Id).first()
            val handCard = original.getEntity(handId)!!.get<CardComponent>()!!
            val libraryCard = original.getEntity(libraryId)!!.get<CardComponent>()!!
            val hiddenFork = original
                .updateEntity(handId) { it.with(libraryCard) }
                .updateEntity(libraryId) { it.with(handCard) }
                .reorderZone(ZoneKey(game.player1Id, Zone.LIBRARY), original.getLibrary(game.player1Id).reversed())
                .reorderZone(ZoneKey(game.player2Id, Zone.LIBRARY), original.getLibrary(game.player2Id).reversed())
            val chosen = ai(game).chooseAction(original).shouldBeInstanceOf<CastSpell>()
            name(game, chosen.cardId) shouldBe "Land Grant"
            AIPlayer.create(cardRegistry, game.player1Id, profile).chooseAction(hiddenFork) shouldBe chosen
            profile.determinizeHiddenInformation shouldBe true
            game.state shouldBe original
        }
    }
}
