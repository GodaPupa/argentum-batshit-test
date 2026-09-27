package com.wingedsheep.gym.pest

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.modules.PestMonsterTronAdvisorModule
import com.wingedsheep.ai.engine.evaluation.BoardEvaluator
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.UnsupportedPolicyInput
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed prospective receiving questions only. No official seed, calibration or game result. */
class PestMonsterTronAuthorizedSearchReceivingTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val epoch = ActorEpoch("pest-monster-search-receiver-v1", "fixed-question", 0)
    private val projection = ObservationAdapter(cardRegistry)
    private val component = PestMonsterTronActorDecisions(epoch, p1)
    private val advisors = CardAdvisorRegistry().also(PestMonsterTronAdvisorModule::register)
    private val simulator = GameSimulator(cardRegistry)
    private val unusedEvaluator = BoardEvaluator { _, _, _ ->
        error("The frozen Monster advisor must not invoke an evaluator")
    }

    init {
        test("MS01 Map missing-piece search chooses the identical physical offered card") {
            val game = seeded().withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withCardInLibrary(1, "Urza's Tower").withCardInLibrary(1, "Forest").build()
            activateMap(game)
            assertPhysicalResponse(game.state, "Expedition Map", "Urza's Tower")
        }

        test("MS02 Map fallback retains verified physical offer order despite reversed entity handles") {
            val game = seeded().withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withLandsOnBattlefield(1, "Urza's Tower", 1)
                .withCardInLibrary(1, "Urza's Mine").withCardInLibrary(1, "Urza's Tower").build()
            game.state = game.state.copy(zones = game.state.zones +
                (ZoneKey(p1, Zone.LIBRARY) to game.state.getLibrary(p1).sortedByDescending { it.value }))
            activateMap(game)
            val raw = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            raw.options shouldNotBe raw.options.sortedBy { it.value }
            val search = verifiedSearch(game.state)
            val visible = search.input
            visible.decision.shouldBeInstanceOf<SelectCardsDecision>().options shouldBe raw.options
            val legacy = legacy(game.state, "Expedition Map", raw).shouldBeInstanceOf<CardsSelectedResponse>()
            component.respond(visible) shouldBe PestMonsterTronActorDecision.NoOverride
            proposed(search).response shouldBe legacy
            legacy.selectedCards shouldBe listOf(raw.options.first())
            // Archived MC10 remains a failure of the older sorted projection. This is a new
            // source-specific receiving assertion; it does not retroactively accept MC10.
        }

        test("MS03 paid Crop search chooses the identical missing-piece physical card") {
            val game = seeded().withCardInHand(1, "Crop Rotation")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withCardInLibrary(1, "Urza's Tower").withCardInLibrary(1, "Forest").build()
            val crop = game.state.getHand(p1).single()
            val forest = game.state.getBattlefield(p1).first { name(game.state, it) == "Forest" }
            game.execute(CastSpell(p1, crop,
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(forest))))
                .error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            assertPhysicalResponse(game.state, "Crop Rotation", "Urza's Tower")
        }

        test("MS04 Cascade Crop public sacrifice stays separate from its later library search") {
            val game = seeded().withCardInHand(1, "Maelstrom Colossus")
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withLandsOnBattlefield(1, "Urza's Tower", 1)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Crop Rotation").withCardInLibrary(1, "Forest").build()
            game.castSpell(1, "Maelstrom Colossus").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val consent = game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            game.execute(SubmitDecision(p1, YesNoResponse(consent.id, true))).error shouldBe null
            val raw = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            raw.cardInfo shouldBe null
            val visible = input(game.state)
            val answer = proposed(visible)
            answer.response shouldBe legacy(game.state, "Crop Rotation", raw)
            name(game.state, answer.response.shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.single()) shouldBe "Forest"
        }

        test("MS05 pair seam requires a real actor response and validates the typed question") {
            val game = seeded().withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Urza's Tower").build()
            activateMap(game)
            val search = verifiedSearch(game.state)
            val visible = search.input
            val pair = PestMonsterPairDecisionBoundary(epoch, p2, p1,
                pestPilot = PestMonsterPairDecisionPilot { null },
                monsterPilot = PestMonsterPairDecisionPilot { incoming ->
                    require(incoming.bindingHash == search.input.bindingHash)
                    (component.respond(search) as? PestMonsterTronActorDecision.Proposed)?.proposal
                })
            pair.respond(visible).action shouldBe proposed(search)
            shouldThrow<UnsupportedPolicyInput> {
                PestMonsterPairDecisionBoundary(epoch, p2, p1,
                    PestMonsterPairDecisionPilot { null }, PestMonsterPairDecisionPilot { null })
                    .respond(visible)
            }
        }

    }

    private fun seeded() = scenario().withPlayers("Monster Tron", "Pest Control")
        .withRngSeed(0x5EED_1E55L)

    private fun activateMap(game: TestGame) {
        val map = game.state.getBattlefield(p1).single { name(game.state, it) == "Expedition Map" }
        val ability = cardRegistry.getCard("Expedition Map")!!.activatedAbilities.single()
        game.execute(ActivateAbility(p1, map, ability.id)).error shouldBe null
        game.resolveStack().forEach { it.error shouldBe null }
    }

    private fun input(state: GameState): ActorInput =
        projection.build(state, p1, emptyList(), epoch, 991L)

    private fun verifiedSearch(state: GameState): PestMonsterVerifiedSearch =
        PestMonsterVerifiedSearch.project(projection, state, p1, emptyList(), epoch, 991L)

    private fun proposed(input: ActorInput): SubmitDecision =
        (component.respond(input) as PestMonsterTronActorDecision.Proposed).proposal.action
            .shouldBeInstanceOf<SubmitDecision>()

    private fun proposed(search: PestMonsterVerifiedSearch): SubmitDecision =
        (component.respond(search) as PestMonsterTronActorDecision.Proposed).proposal.action
            .shouldBeInstanceOf<SubmitDecision>()

    private fun assertPhysicalResponse(state: GameState, source: String, selectedName: String) {
        val raw = state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        val search = verifiedSearch(state)
        val visible = search.input
        visible.decision.shouldBeInstanceOf<SelectCardsDecision>().options shouldBe raw.options
        component.respond(visible) shouldBe PestMonsterTronActorDecision.NoOverride
        val answer = proposed(search)
        answer.response shouldBe legacy(state, source, raw)
        name(state, answer.response.shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.single()) shouldBe selectedName
    }

    private fun legacy(state: GameState, source: String, question: PendingDecision): DecisionResponse? =
        advisors.getAdvisor(source)!!.respondToDecision(AdvisorDecisionContext(
            state, state.projectedState, p1, question, source, unusedEvaluator, simulator))

    private fun name(state: GameState, id: EntityId): String? =
        state.getEntity(id)?.get<CardComponent>()?.name
}
