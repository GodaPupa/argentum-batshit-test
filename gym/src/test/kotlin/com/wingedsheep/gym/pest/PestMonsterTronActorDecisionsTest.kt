package com.wingedsheep.gym.pest

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.modules.PestMonsterTronAdvisorModule
import com.wingedsheep.ai.engine.evaluation.BoardEvaluator
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.BoundaryFailure
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.ObservationBoundaryException
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Prospective mechanical receiving bank, not games or strategic development. These fixtures use
 * the existing Monster audit fixture RNG constant; they do not allocate official seeds.
 * MC10 is deliberately diagnostic: it exposes a known information-interface ordering difference
 * and does not accept that difference as an equivalent or newly authorized frozen policy.
 */
class PestMonsterTronActorDecisionsTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val epoch = ActorEpoch("pest-monster-actor-component-v1", "fixed-receiving-fixture", 0)
    private val projection = ObservationAdapter(cardRegistry)
    private val component = PestMonsterTronActorDecisions(epoch, p1)
    private val advisors = CardAdvisorRegistry().also(PestMonsterTronAdvisorModule::register)
    private val simulator = GameSimulator(cardRegistry)
    private val unusedEvaluator = BoardEvaluator { _, _, _ ->
        error("The frozen Monster advisor component must not invoke an evaluator")
    }

    init {
        test("MC01 actual Expedition Map resolves the unique missing-piece response") {
            val game = mapFixture()
            activateMap(game)
            val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val answer = proposed(input(game.state))
            answer.response shouldBe legacy(game.state, "Expedition Map", question)
            name(game.state, selected(answer)) shouldBe "Urza's Tower"
            game.execute(answer).error shouldBe null
            game.state.getHand(p1).map { name(game.state, it) } shouldContain "Urza's Tower"
            game.state.getGraveyard(p1).map { name(game.state, it) } shouldContain "Expedition Map"
        }

        test("MC02 normal Crop Rotation pays an explicit sacrifice and resolves its actual search") {
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
            resolveUntilChoice(game)
            val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val answer = proposed(input(game.state))
            answer.response shouldBe legacy(game.state, "Crop Rotation", question)
            name(game.state, selected(answer)) shouldBe "Urza's Tower"
            game.execute(answer).error shouldBe null
            game.state.getGraveyard(p1) shouldContain forest
            game.state.getBattlefield(p1).map { name(game.state, it) } shouldContain "Urza's Tower"
        }

        test("MC03 real Cascade into Crop Rotation offers and accepts the unique non-Tron sacrifice") {
            val game = seeded().withCardInHand(1, "Maelstrom Colossus")
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withLandsOnBattlefield(1, "Urza's Tower", 1)
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Crop Rotation").withCardInLibrary(1, "Forest").build()
            game.castSpell(1, "Maelstrom Colossus").error shouldBe null
            resolveUntilChoice(game)
            val consent = game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            game.execute(SubmitDecision(p1, YesNoResponse(consent.id, true))).error shouldBe null
            val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            question.context.sourceName shouldBe "Crop Rotation"
            question.cardInfo shouldBe null
            val answer = proposed(input(game.state))
            answer.response shouldBe legacy(game.state, "Crop Rotation", question)
            val forest = selected(answer)
            name(game.state, forest) shouldBe "Forest"
            game.execute(answer).error shouldBe null
            game.state.getGraveyard(p1) shouldContain forest
            game.state.stack.map { name(game.state, it) } shouldContain "Crop Rotation"
        }

        test("MC04 actual Ancient Stirrings uses the offered authorized group and retains its reorder question") {
            val game = stirringsFixture(listOf("Expedition Map", "Urza's Tower", "Crop Rotation",
                "Bonder's Ornament", "Bramble Wurm"))
            val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val before = input(game.state)
            val answer = proposed(before)
            answer.response shouldBe legacy(game.state, "Ancient Stirrings", question)
            name(game.state, selected(answer)) shouldBe "Urza's Tower"
            before.observation.decisionCards.map { it.name }.toSet() shouldBe
                setOf("Expedition Map", "Urza's Tower", "Crop Rotation", "Bonder's Ornament", "Bramble Wurm")
            game.execute(answer).error shouldBe null
            game.state.getHand(p1).map { name(game.state, it) } shouldContain "Urza's Tower"
            game.state.pendingDecision shouldNotBe null
            component.respond(input(game.state)) shouldBe PestMonsterTronActorDecision.NoOverride
        }

        test("MC05 actual Bojuka Bog targets and exiles the public opponent graveyard") {
            val game = bogFixture()
            val question = game.state.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            val answer = proposed(input(game.state))
            answer.response shouldBe legacy(game.state, "Bojuka Bog", question)
            answer.response.shouldBeInstanceOf<TargetsResponse>().selectedTargets.values.flatten() shouldBe listOf(p2)
            game.execute(answer).error shouldBe null
            resolveUntilChoice(game)
            game.state.getGraveyard(p2) shouldBe emptyList()
            game.state.getExile(p2).map { name(game.state, it) }.toSet() shouldBe
                setOf("Carrier Thrall", "Blood Researcher")
            game.state.getGraveyard(p1).map { name(game.state, it) } shouldContain "Forest"
        }

        test("MC06 offered Stirrings group without a Tron land preserves the legacy null defer") {
            val game = stirringsFixture(listOf("Expedition Map", "Bonder's Ornament", "Crop Rotation",
                "Ancient Stirrings", "Bramble Wurm"))
            val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            legacy(game.state, "Ancient Stirrings", question) shouldBe null
            component.respond(input(game.state)) shouldBe PestMonsterTronActorDecision.NoOverride
            game.state.pendingDecision shouldBe question
        }

        test("MC07 unavailable hand and future-library changes cannot alter Bog input or response") {
            val game = bogFixture()
            val base = game.state
            var changed = base
            val replacement = base.getEntity(base.getGraveyard(p1).single())!!.require<CardComponent>()
            (base.getHand(p2) + base.getLibrary(p1) + base.getLibrary(p2)).forEach { id ->
                changed = changed.updateEntity(id) { it.with(replacement) }
            }
            listOf(p1, p2).forEach { player ->
                changed = changed.copy(zones = changed.zones +
                    (ZoneKey(player, Zone.LIBRARY) to changed.getLibrary(player).reversed()))
            }
            val original = input(base)
            val perturbed = input(changed)
            original.canonicalJson() shouldBe perturbed.canonicalJson()
            component.respond(original) shouldBe component.respond(perturbed)
        }

        test("MC08 a legitimate own-hand Tron piece changes the missing-piece choice") {
            val game = seeded().withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(1, "Forest", 2).withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withCardInHand(1, "Forest").withCardInLibrary(1, "Urza's Tower")
                .withCardInLibrary(1, "Urza's Power Plant").build()
            activateMap(game)
            val base = game.state
            val plant = base.getLibrary(p1).single { name(base, it) == "Urza's Power Plant" }
            val plantCard = base.getEntity(plant)!!.require<CardComponent>()
            val changed = base.updateEntity(base.getHand(p1).single()) { it.with(plantCard) }
            name(base, selected(proposed(input(base)))) shouldBe "Urza's Power Plant"
            name(changed, selected(proposed(input(changed)))) shouldBe "Urza's Tower"
            input(base).canonicalJson() shouldNotBe input(changed).canonicalJson()
        }

        test("MC09 stale actor epoch and edited policy-state bindings are rejected before a choice") {
            val before = input(bogFixture().state)
            shouldThrow<ObservationBoundaryException> {
                PestMonsterTronActorDecisions(epoch, p2).respond(before)
            }.failure shouldBe BoundaryFailure.STALE_INPUT
            shouldThrow<ObservationBoundaryException> {
                PestMonsterTronActorDecisions(epoch.copy(step = 1), p1).respond(before)
            }.failure shouldBe BoundaryFailure.STALE_INPUT
            shouldThrow<ObservationBoundaryException> {
                component.respond(before.copy(policyRngState = before.policyRngState + 1))
            }.failure shouldBe BoundaryFailure.STALE_INPUT
        }

        test("MC10 diagnostic raw library fallback order differs from canonical order and remains unaccepted") {
            val game = seeded().withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
                .withLandsOnBattlefield(1, "Urza's Mine", 1)
                .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
                .withLandsOnBattlefield(1, "Urza's Tower", 1)
                .withCardInLibrary(1, "Urza's Mine").withCardInLibrary(1, "Urza's Tower").build()
            // A whole-library search must not disclose its shuffled order. Intentionally put the
            // larger public handle first to distinguish raw collection order from canonical order.
            game.state = game.state.copy(zones = game.state.zones +
                (ZoneKey(p1, Zone.LIBRARY) to game.state.getLibrary(p1).sortedByDescending { it.value }))
            activateMap(game)
            val raw = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            val visible = input(game.state)
            val legacyAnswer = legacy(game.state, "Expedition Map", raw).shouldBeInstanceOf<CardsSelectedResponse>()
            val actorAnswer = proposed(visible).response.shouldBeInstanceOf<CardsSelectedResponse>()
            legacyAnswer.selectedCards shouldBe listOf(raw.options.first())
            actorAnswer.selectedCards shouldBe listOf(raw.options.minBy { it.value })
            legacyAnswer.selectedCards shouldNotBe actorAnswer.selectedCards
            println("PEST_MONSTER_ORDER_DIAGNOSTIC raw=${name(game.state, legacyAnswer.selectedCards.single())} " +
                "canonical=${name(game.state, actorAnswer.selectedCards.single())} " +
                "disposition=REQUIRES_PROSPECTIVE_PROTOCOL_DISPOSITION no_response_submitted=true")
        }
    }

    private fun seeded() = scenario().withPlayers("Monster Tron", "Pest Control")
        .withRngSeed(0x5EED_1E55L)

    private fun mapFixture() = seeded()
        .withCardOnBattlefield(1, "Expedition Map", summoningSickness = false)
        .withLandsOnBattlefield(1, "Forest", 2)
        .withLandsOnBattlefield(1, "Urza's Mine", 1)
        .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
        .withCardInLibrary(1, "Urza's Tower").withCardInLibrary(1, "Forest").build()

    private fun activateMap(game: TestGame) {
        val map = game.state.getBattlefield(p1).single { name(game.state, it) == "Expedition Map" }
        val ability = cardRegistry.getCard("Expedition Map")!!.activatedAbilities.single()
        game.execute(ActivateAbility(p1, map, ability.id)).error shouldBe null
        resolveUntilChoice(game)
    }

    private fun stirringsFixture(top: List<String>): TestGame {
        val builder = seeded().withCardInHand(1, "Ancient Stirrings")
            .withLandsOnBattlefield(1, "Forest", 1)
            .withLandsOnBattlefield(1, "Urza's Mine", 1)
            .withLandsOnBattlefield(1, "Urza's Power Plant", 1)
        top.forEach { builder.withCardInLibrary(1, it) }
        val game = builder.build()
        game.castSpell(1, "Ancient Stirrings").error shouldBe null
        resolveUntilChoice(game)
        return game
    }

    private fun bogFixture(): TestGame {
        val game = seeded().withCardInHand(1, "Bojuka Bog")
            .withCardInHand(2, "Carrier Thrall")
            .withCardInGraveyard(1, "Forest")
            .withCardInGraveyard(2, "Carrier Thrall").withCardInGraveyard(2, "Blood Researcher")
            .withCardInLibrary(1, "Urza's Mine").withCardInLibrary(1, "Urza's Tower")
            .withCardInLibrary(2, "Swamp").withCardInLibrary(2, "Weather the Storm").build()
        game.execute(PlayLand(p1, game.state.getHand(p1).single())).error shouldBe null
        resolveUntilChoice(game)
        return game
    }

    private fun resolveUntilChoice(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
    }

    private fun input(state: GameState): ActorInput {
        state.pendingDecision!!.playerId shouldBe p1
        return projection.build(state, p1, emptyList(), epoch, 991L)
    }

    private fun proposed(input: ActorInput): SubmitDecision {
        val proposal = component.respond(input).shouldBeInstanceOf<PestMonsterTronActorDecision.Proposed>().proposal
        proposal.inputBindingHash shouldBe input.bindingHash
        proposal.nextPolicyRngState shouldBe input.policyRngState
        return proposal.action.shouldBeInstanceOf<SubmitDecision>()
    }

    private fun legacy(state: GameState, source: String, question: PendingDecision): DecisionResponse? =
        advisors.getAdvisor(source)!!.respondToDecision(AdvisorDecisionContext(
            state, state.projectedState, p1, question, source, unusedEvaluator, simulator))

    private fun selected(action: SubmitDecision): EntityId =
        action.response.shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.single()

    private fun name(state: GameState, id: EntityId): String? = state.getEntity(id)?.get<CardComponent>()?.name
}
