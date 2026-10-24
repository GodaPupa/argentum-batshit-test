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
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ModalSpellCaptureTest : ScenarioTestBase() {
    init {
        val services = EngineServices(cardRegistry)
        val handler = CastSpellHandler.create(services)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
        val one = Mode(Effects.GainLife(1), listOf(AnyTarget()))
        val optional = Mode(Effects.GainLife(1), listOf(AnyTarget(minCount = 0, optional = true), AnyTarget()))
        data class Fixture(val state: GameState, val player: EntityId, val spell: EntityId, val target: EntityId)
        fun fixture(modes: List<Mode>, picks: List<Int>, tracked: Boolean = true): Fixture {
            val definition = card("Original modal spell fixture") {
                manaCost = "{0}"
                typeLine = "Sorcery"
                spell { effect = ModalEffect(modes, chooseCount = picks.size, allowRepeat = true) }
            }
            cardRegistry.register(definition)
            val game = scenario().withPlayers("One", "Two")
                .withCardInHand(1, definition.name).withCardOnBattlefield(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val spellId = game.state.getHand(game.player1Id).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == definition.name
            }
            val action = CastSpell(game.player1Id, spellId, paymentStrategy = PaymentStrategy.FromPool)
            val r = handler.presentCastModalTargetDecision(game.state, spellId, game.player1Id, definition.name,
                action, modes, picks, emptyList(), 0,
                if (tracked) OriginalModalSpellCapture(picks, emptyList()) else null)
            r.error shouldBe null
            return Fixture(r.state, game.player1Id, spellId, requireNotNull(game.findPermanent("Grizzly Bears")))
        }
        fun answer(f: Fixture, groups: Map<Int, List<EntityId>>) = actionProcessor.process(f.state,
            SubmitDecision(f.player, TargetsResponse(requireNotNull(f.state.pendingDecision).id, groups))).result
        fun capture(f: Fixture, state: GameState) = requireNotNull(state.getEntity(f.spell)?.get<SpellOnStackComponent>()).originalModalCapture

        test("SC01 omitted optional groups retain original positions on the cast spell") {
            val f = fixture(listOf(optional), listOf(0))
            val r = answer(f, mapOf(1 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(capture(f, r.state)).plans.single().instances.map { it.slots.size } shouldBe listOf(0, 1)
        }
        test("SC02 repeated choices retain separate original object visits") {
            val f = fixture(listOf(one), listOf(0, 0))
            val first = answer(f, mapOf(0 to listOf(f.target)))
            val old = requireNotNull(f.state.objectRef(f.target))
            val changed = first.state.copy(objectIdentities = first.state.objectIdentities +
                (f.target to first.state.objectIdentities.getValue(f.target).copy(generation = old.generation + 1)))
            val r = answer(f.copy(state = changed), mapOf(0 to listOf(f.target)))
            r.error shouldBe null
            val c = requireNotNull(capture(f, r.state))
            c.chosenModeIndices shouldBe listOf(0, 0)
            c.plans.map { it.instances.single().slots.single().objectRef } shouldBe listOf(old, changed.objectRef(f.target))
        }
        test("SC03 serialized pending target capture replays identically") {
            val f = fixture(listOf(one), listOf(0, 0))
            val first = answer(f, mapOf(0 to listOf(f.target))).state
            val encoded = json.encodeToString(GameState.serializer(), first)
            val decoded = json.decodeFromString(GameState.serializer(), encoded)
            val direct = answer(f.copy(state = first), mapOf(0 to listOf(f.target)))
            val replay = answer(f.copy(state = decoded), mapOf(0 to listOf(f.target)))
            direct.error shouldBe null
            replay.state shouldBe direct.state
            replay.events shouldBe direct.events
        }
        test("SC04 cancellation discards pending capture without attaching to the hand card") {
            val f = fixture(listOf(one), listOf(0, 0))
            val first = answer(f, mapOf(0 to listOf(f.target))).state
            val r = actionProcessor.process(first, SubmitDecision(f.player,
                CancelDecisionResponse(requireNotNull(first.pendingDecision).id))).result
            r.error shouldBe null
            r.state.getHand(f.player).contains(f.spell) shouldBe true
            r.state.getEntity(f.spell)?.get<SpellOnStackComponent>() shouldBe null
            r.state.pendingDecision shouldBe null
        }
        test("SC05 targetless modes retain their own original ordinal") {
            val f = fixture(listOf(Mode(Effects.GainLife(1)), one), listOf(0, 1))
            val r = answer(f, mapOf(0 to listOf(f.target)))
            r.error shouldBe null
            requireNotNull(capture(f, r.state)).plans.map { it.size } shouldBe listOf(0, 1)
        }
        test("SC06 legacy continuation remains explicitly untracked") {
            val f = fixture(listOf(one), listOf(0), tracked = false)
            val bytes = json.encodeToString(GameState.serializer(), f.state)
            bytes.contains("originalModalCapture") shouldBe false
            val r = answer(f.copy(state = json.decodeFromString(GameState.serializer(), bytes)), mapOf(0 to listOf(f.target)))
            r.error shouldBe null
            capture(f, r.state) shouldBe null
        }
        test("SC07 public cast action has no trusted provenance field") {
            val descriptor = CastSpell.serializer().descriptor
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.contains("originalModalCapture") shouldBe false
        }
        test("SC08 failed attachment never marks an uncast card") {
            val f = fixture(listOf(one), listOf(0))
            val c = OriginalModalSpellCapture(listOf(0), listOf(captureTargetSlots(f.state, 1,
                mapOf(0 to listOf(ChosenTarget.Permanent(f.target))))))
            attachOriginalModalSpellCapture(f.state, f.spell, c) shouldBe f.state
        }
    }
}
