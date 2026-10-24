package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.actions.spell.CastSpellHandler
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.model.CastTimeCreatureTypeSource
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import io.kotest.matchers.shouldBe
import com.wingedsheep.engine.handlers.actions.decision.DecisionValidators
import com.wingedsheep.sdk.scripting.targets.TargetSpell
import kotlinx.serialization.json.Json

class ModalSpellRecoveryBoundaryTest : ScenarioTestBase() {
    init {
        val services = EngineServices(cardRegistry)
        val handler = CastSpellHandler.create(services)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        val one = Mode(Effects.GainLife(1), listOf(AnyTarget()))
        val optional = Mode(Effects.GainLife(1), listOf(AnyTarget(minCount = 0, optional = true), AnyTarget()))
        data class Fixture(val state: GameState, val player: EntityId, val spell: EntityId, val target: EntityId)
        fun fixture(modes: List<Mode>, picks: List<Int>, tracked: Boolean = true, cost: Boolean = false, forced: Boolean = false, typeChoice: Boolean = false, entry: Boolean = false): Fixture {
            val definition = card("Original modal spell fixture") {
                manaCost = "{0}"
                typeLine = "Sorcery"
                if (cost) additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.Creature))
                if (typeChoice) {
                    additionalCost(Costs.additional.PayLife(2))
                    castTimeCreatureTypeChoice = CastTimeCreatureTypeSource.GRAVEYARD
                }
                spell { effect = ModalEffect(modes, chooseCount = picks.size, allowRepeat = true) }
            }
            cardRegistry.register(definition)
            val builder = scenario().withPlayers("One", "Two")
                .withCardInHand(1, definition.name).withCardOnBattlefield(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            if (cost && !forced) builder.withCardOnBattlefield(1, "Hill Giant")
            if (typeChoice) builder.withCardInGraveyard(1, "Hill Giant")
            val game = builder.build()
            val spellId = game.state.getHand(game.player1Id).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == definition.name
            }
            val action = CastSpell(game.player1Id, spellId, paymentStrategy = PaymentStrategy.FromPool)
            if (entry) return Fixture(game.state, game.player1Id, spellId, requireNotNull(game.findPermanent("Grizzly Bears")))
            val r = handler.presentCastModalTargetDecision(game.state, spellId, game.player1Id, definition.name,
                action, modes, picks, emptyList(), 0,
                if (tracked) OriginalModalSpellCapture(picks, emptyList()) else null)
            r.error shouldBe null
            return Fixture(r.state, game.player1Id, spellId, requireNotNull(game.findPermanent("Grizzly Bears")))
        }
        fun answer(f: Fixture, groups: Map<Int, List<EntityId>>) = actionProcessor.process(f.state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(f.state.pendingDecision).id, groups))).result
        fun capture(f: Fixture, state: GameState) = requireNotNull(state.getEntity(f.spell)?.get<SpellOnStackComponent>()).originalModalCapture

        test("RG01 nonrepeatable selection still requires enough distinct modes") {
            val definition = card("Nonrepeatable selection boundary") {
                manaCost = "{0}"; typeLine = "Sorcery"
                spell { effect = ModalEffect(listOf(one), chooseCount = 2, allowRepeat = false) }
            }
            cardRegistry.register(definition)
            val g = scenario().withPlayers("One", "Two").withCardInHand(1, definition.name)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val id = g.state.getHand(g.player1Id).single()
            val r = actionProcessor.process(g.state, CastSpell(g.player1Id, id, paymentStrategy = PaymentStrategy.FromPool)).result
            r.error shouldBe "No legal mode selection available for ${definition.name}"
            r.state shouldBe g.state
        }
        test("RG02 repeatable selection with no legal mode remains rejected") {
            val f = fixture(listOf(Mode(Effects.GainLife(1), listOf(TargetSpell()))), listOf(0, 0), entry = true)
            val r = actionProcessor.process(f.state, CastSpell(f.player, f.spell, paymentStrategy = PaymentStrategy.FromPool)).result
            r.error shouldBe "No legal mode selection available for Original modal spell fixture"
            r.state shouldBe f.state
        }
        test("RG03 ordinary card selection without a matching unpaid cast frame cannot cancel") {
            val f = fixture(listOf(one), listOf(0), cost = true)
            val paused = answer(f, mapOf(0 to listOf(f.target))).state
            val question = requireNotNull(paused.pendingDecision) as SelectCardsDecision
            val cancel = CancelDecisionResponse(question.id)
            DecisionValidators.validate(question, cancel, null) shouldBe "This decision cannot be cancelled"
            val empty = paused.copy(continuationStack = emptyList())
            val ordinary = empty.suspendForDecision(
                question = { id -> question.copy(id = id) },
                answer = (f.state.peekContinuation() as Suspension).answer).state
            DecisionValidators.validate(requireNotNull(ordinary.pendingDecision),
                CancelDecisionResponse(requireNotNull(ordinary.pendingDecision).id), ordinary) shouldBe "This decision cannot be cancelled"
        }
        test("RG04 unrelated question cannot inherit unpaid cast cancellation authority") {
            val f = fixture(listOf(one), listOf(0), cost = true)
            val paused = answer(f, mapOf(0 to listOf(f.target))).state
            val question = (requireNotNull(paused.pendingDecision) as SelectCardsDecision).copy(id = "unrelated")
            DecisionValidators.validate(question, CancelDecisionResponse(question.id), paused) shouldBe "This decision cannot be cancelled"
        }
        test("RG05 serialized unpaid cast cancellation replays without payment or provenance") {
            val f = fixture(listOf(one), listOf(0), cost = true)
            val paused = answer(f, mapOf(0 to listOf(f.target))).state
            val loaded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), paused))
            val action = SubmitDecision(f.player, CancelDecisionResponse(requireNotNull(paused.pendingDecision).id))
            val direct = actionProcessor.process(paused, action).result
            val replay = actionProcessor.process(loaded, action).result
            direct.error shouldBe null
            replay.error shouldBe null
            replay.state shouldBe direct.state
            replay.events shouldBe direct.events
            direct.state.pendingDecision shouldBe null
            direct.state.getHand(f.player) shouldBe f.state.getHand(f.player)
            direct.state.getBattlefield() shouldBe f.state.getBattlefield()
            direct.state.getEntity(f.spell)?.get<SpellOnStackComponent>() shouldBe null
        }
    }
}
