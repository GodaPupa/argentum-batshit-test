package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Seed-free Gate 10 policy probes for the frozen Mogged Elves opponent. */
class IndustrialWasteElvesPolicyAuditTest : ScenarioTestBase() {
    private val baseProfile = AiProfile.PRODUCTION_CANDIDATE_EXPIRING
    private val profile = baseProfile.copy(
        id = "elves-gate-10-policy-audit",
        advisorModules = baseProfile.advisorModules + ElvesAdvisorModule,
        considerAdvisedManaAbilities = true,
    )

    private fun ai(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
    private fun name(game: TestGame, id: EntityId): String? =
        game.state.getEntity(id)?.get<CardComponent>()?.name
    private fun seeded() = scenario()
        .withPlayers("Elves", "Industrial Waste")
        .withRngSeed(0x1A57_B10EL)

    init {
        test("Quirion Ranger returns a tapped Forest and points its untap at Priest of Titania") {
            val game = seeded()
                .withCardOnBattlefield(1, "Quirion Ranger")
                .withCardOnBattlefield(1, "Priest of Titania", tapped = true)
                .withCardOnBattlefield(1, "Gingerbread Cabin", tapped = true)
                .withLandsOnBattlefield(1, "Snow-Covered Forest", 1)
                .build()

            val lands = game.state.getBattlefield(game.player1Id).filter { id ->
                name(game, id) in setOf("Gingerbread Cabin", "Snow-Covered Forest")
            }
            val bounce = SelectCardsDecision(
                id = "quirion-bounce",
                playerId = game.player1Id,
                prompt = "Return a Forest you control",
                context = DecisionContext(sourceName = "Quirion Ranger"),
                options = lands,
                minSelections = 1,
                maxSelections = 1,
            )
            val response = ai(game).respondToDecision(game.state, bounce)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Gingerbread Cabin"

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Quirion Ranger"
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>()
            name(game, target.entityId) shouldBe "Priest of Titania"
        }

        test("Priest of Titania scales creature mana into Avenging Hunter") {
            val game = seeded()
                .withCardInHand(1, "Avenging Hunter")
                .withLandsOnBattlefield(1, "Snow-Covered Forest", 1)
                .withCardOnBattlefield(1, "Priest of Titania")
                .withCardOnBattlefield(1, "Quirion Ranger")
                .withCardOnBattlefield(1, "Timberwatch Elf")
                .withCardOnBattlefield(1, "Llanowar Elves")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Avenging Hunter"
            game.execute(action).error shouldBe null
            val priest = game.findPermanent("Priest of Titania")!!
            game.state.getEntity(priest)?.has<TappedComponent>() shouldBe true
        }

        test("Winding Way chooses creature for the creature-dense Elves shell") {
            val game = seeded().build()
            val decision = ChooseOptionDecision(
                id = "winding-way-choice",
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
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Snow-Covered Forest")
                .build()
            val options = game.state.getZone(game.player1Id, Zone.LIBRARY)
            val decision = SelectCardsDecision(
                id = "lead-choice",
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
                setOf("Elvish Mystic", "Fyndhorn Elves")
        }

        test("Masked Vandal spends a cheap graveyard Elf and prioritizes Ashnod's Altar") {
            val game = seeded()
                .withCardInHand(1, "Masked Vandal")
                .withLandsOnBattlefield(1, "Snow-Covered Forest", 2)
                .withCardInGraveyard(1, "Elvish Mystic")
                .withCardInGraveyard(1, "Avenging Hunter")
                .withCardOnBattlefield(2, "Ashnod's Altar")
                .withCardOnBattlefield(2, "Candy Trail")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Masked Vandal"
            game.execute(action).error shouldBe null
            game.resolveStack()

            val targetDecision = game.getPendingDecision()!!
            game.submitDecision(ai(game).respondToDecision(game.state, targetDecision)).error shouldBe null
            game.resolveStack()

            val exileDecision = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            val exileResponse = ai(game).respondToDecision(game.state, exileDecision)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            exileResponse.selectedCards.mapNotNull { name(game, it) } shouldContain "Elvish Mystic"
        }

        test("Timberwatch Elf converts an attack window into a pump on the attacker") {
            val game = seeded()
                .withCardOnBattlefield(1, "Timberwatch Elf")
                .withCardOnBattlefield(1, "Elvish Mystic")
                .withCardOnBattlefield(1, "Fyndhorn Elves")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()

            game.declareAttackers(mapOf("Elvish Mystic" to 2)).error shouldBe null
            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<ActivateAbility>()
            name(game, action.sourceId) shouldBe "Timberwatch Elf"
            val target = action.targets.single().shouldBeInstanceOf<ChosenTarget.Permanent>()
            name(game, target.entityId) shouldBe "Elvish Mystic"
        }

        test("Avenging Hunter is treated as initiative pressure") {
            val game = seeded()
                .withCardInHand(1, "Avenging Hunter")
                .withLandsOnBattlefield(1, "Snow-Covered Forest", 5)
                .withCardInLibrary(1, "Snow-Covered Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Avenging Hunter"
        }

        test("Land Grant takes the free landless-hand line and searches basic Forest before Cabin") {
            val game = seeded()
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Elvish Mystic")
                .withCardInLibrary(1, "Gingerbread Cabin")
                .withCardInLibrary(1, "Snow-Covered Forest")
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<CastSpell>()
            name(game, action.cardId) shouldBe "Land Grant"
            action.useAlternativeCost shouldBe true
            game.execute(action).error shouldBe null
            game.resolveStack()

            val search = game.getPendingDecision()!!
            val response = ai(game).respondToDecision(game.state, search)
                .shouldBeInstanceOf<CardsSelectedResponse>()
            name(game, response.selectedCards.single()) shouldBe "Snow-Covered Forest"
        }

        test("Gingerbread Cabin is preferred when three other Forests make it untapped") {
            val game = seeded()
                .withCardInHand(1, "Gingerbread Cabin")
                .withCardInHand(1, "Snow-Covered Forest")
                .withLandsOnBattlefield(1, "Snow-Covered Forest", 3)
                .build()

            val action = ai(game).chooseAction(game.state).shouldBeInstanceOf<PlayLand>()
            name(game, action.cardId) shouldBe "Gingerbread Cabin"
            game.execute(action).error shouldBe null
            game.resolveStack()
            game.state.getEntity(action.cardId)?.has<TappedComponent>() shouldBe false
        }
    }
}
