package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.TargetingRestrictionCreatedEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.handlers.effects.permanent.protection.PreventTargetingExecutor
import com.wingedsheep.engine.legalactions.utils.TargetEnumerationUtils
import com.wingedsheep.engine.mechanics.targeting.FloatingTargetingRestriction
import com.wingedsheep.engine.mechanics.targeting.TargetValidator
import com.wingedsheep.engine.mechanics.StateBasedActionChecker
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.view.ClientStateTransformer
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.PreventTargetingEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class PreventTargetingScenarioTest : FunSpec({
    fun game() = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.initMirrorMatch(Deck.of("Forest" to 40), startingPlayer = 0)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("generic permanent target blocks announcement selection and direct enumeration") {
        val d = game()
        val land = d.putLandOnBattlefield(d.player2, "Forest")
        val result = PreventTargetingExecutor().execute(d.state,
            PreventTargetingEffect(EffectTarget.SpecificEntity(land)), EffectContext(sourceId = null, controllerId = d.player1))
        val state = result.newState
        for (sourceType in listOf(TargetingSourceType.SPELL, TargetingSourceType.ABILITY)) {
            (TargetValidator().validateTargets(state, listOf(ChosenTarget.Permanent(land)),
                listOf(Targets.Permanent), d.player2, targetingSourceType = sourceType) != null) shouldBe true
        }
        (land in TargetFinder().findLegalTargets(state, Targets.Permanent, d.player2)) shouldBe false
        (land in TargetEnumerationUtils(PredicateEvaluator()).findValidPermanentTargets(state, d.player2,
            TargetFilter.Permanent)) shouldBe false
        (land in TargetFinder().findLegalTargets(state, Targets.Permanent, d.player1)) shouldBe true
    }

    test("generic player target is enforced and visible through state serialization") {
        val d = game()
        val result = PreventTargetingExecutor().execute(d.state,
            PreventTargetingEffect(EffectTarget.Controller), EffectContext(sourceId = null, controllerId = d.player1))
        val state = SerializationTestSupport.roundTrip(result.newState)
        (d.player1 in TargetFinder().findLegalTargets(state, Targets.Player, d.player2)) shouldBe false
        (d.player1 in TargetEnumerationUtils(PredicateEvaluator()).findValidTargets(state, d.player2,
            Targets.Any)) shouldBe false
        (d.player1 in TargetFinder().findLegalTargets(state, Targets.Player, d.player1)) shouldBe true
        (TargetValidator().validateTargets(state, listOf(ChosenTarget.Player(d.player1)), listOf(Targets.Player),
            d.player2, targetingSourceType = TargetingSourceType.ABILITY) != null) shouldBe true
        ClientStateTransformer(d.cardRegistry).transform(state, d.player2).players
            .single { it.playerId == d.player1 }.activeEffects.count { it.name == "Targeting restricted" } shouldBe 1
    }

    test("parameterized all-player restriction includes its own controller") {
        val d = game()
        val target = d.putCreatureOnBattlefield(d.player2, "Centaur Courser")
        val result = PreventTargetingExecutor().execute(d.state,
            PreventTargetingEffect(EffectTarget.SpecificEntity(target), Player.Each, Duration.Permanent),
            EffectContext(sourceId = null, controllerId = d.player1))
        listOf(d.player1, d.player2).forEach { FloatingTargetingRestriction.prevents(result.newState, target, it) shouldBe true }
        result.newState.floatingEffects.last().duration shouldBe Duration.Permanent
    }

    test("unresolved and missing targets produce no restriction and no invented event") {
        val d = game()
        for (target in listOf(EffectTarget.ContextTarget(0), EffectTarget.SpecificEntity(EntityId("absent")))) {
            val result = PreventTargetingExecutor().execute(d.state, PreventTargetingEffect(target),
                EffectContext(sourceId = null, controllerId = d.player1))
            result.newState shouldBe d.state
            result.events shouldBe emptyList()
        }
    }

    test("restriction creation event round trips through the engine polymorphic event registry") {
        val d = game()
        val event: GameEvent = TargetingRestrictionCreatedEvent(d.player1, setOf(d.player2), null)
        val json = Json { serializersModule = engineSerializersModule }
        val encoded = json.encodeToString(GameEvent.serializer(), event)
        json.decodeFromString(GameEvent.serializer(), encoded) shouldBe event
    }

    test("source-tapped duration gates both card and player reads before SBA and never revives after pruning") {
        for (playerTarget in listOf(false, true)) {
            val d = game()
            val source = d.putLandOnBattlefield(d.player1, "Forest")
            val target = if (playerTarget) d.player1 else d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
            val initial = d.state.updateEntity(source) { it.with(TappedComponent) }
            val restricted = PreventTargetingExecutor().execute(initial,
                PreventTargetingEffect(EffectTarget.SpecificEntity(target), duration = Duration.WhileSourceTapped),
                EffectContext(sourceId = source, controllerId = d.player1)).newState
            FloatingTargetingRestriction.prevents(restricted, target, d.player2) shouldBe true
            val untapped = restricted.updateEntity(source) { it.without<TappedComponent>() }
            FloatingTargetingRestriction.prevents(untapped, target, d.player2) shouldBe false
            val visible = ClientStateTransformer(d.cardRegistry).transform(untapped, d.player2)
            val badgeCount = if (playerTarget) visible.players.single { it.playerId == target }.activeEffects
                .count { it.name == "Targeting restricted" }
            else visible.cards.getValue(target).activeEffects.count { it.name == "Targeting restricted" }
            badgeCount shouldBe 0
            val pruned = StateBasedActionChecker(cardRegistry = d.cardRegistry).checkAndApply(untapped).newState
            val retapped = pruned.updateEntity(source) { it.with(TappedComponent) }
            FloatingTargetingRestriction.prevents(retapped, target, d.player2) shouldBe false
        }
    }

    test("affected-tapped duration stops immediately and stays ended when the object retaps") {
        val d = game()
        val target = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val initial = d.state.updateEntity(target) { it.with(TappedComponent) }
        val restricted = PreventTargetingExecutor().execute(initial,
            PreventTargetingEffect(EffectTarget.SpecificEntity(target), duration = Duration.WhileAffectedTapped),
            EffectContext(sourceId = null, controllerId = d.player1)).newState
        FloatingTargetingRestriction.prevents(restricted, target, d.player2) shouldBe true
        val untapped = restricted.updateEntity(target) { it.without<TappedComponent>() }
        FloatingTargetingRestriction.prevents(untapped, target, d.player2) shouldBe false
        val pruned = StateBasedActionChecker(cardRegistry = d.cardRegistry).checkAndApply(untapped).newState
        FloatingTargetingRestriction.prevents(pruned.updateEntity(target) { it.with(TappedComponent) }, target, d.player2) shouldBe false
    }

    test("controller-keyed duration uses projected control and expires permanently after control is lost") {
        val d = game()
        val target = d.putCreatureOnBattlefield(d.player1, "Centaur Courser")
        val restricted = PreventTargetingExecutor().execute(d.state,
            PreventTargetingEffect(EffectTarget.SpecificEntity(target), duration = Duration.WhileControlledByController),
            EffectContext(sourceId = null, controllerId = d.player1)).newState
        FloatingTargetingRestriction.prevents(restricted, target, d.player2) shouldBe true
        val changed = restricted.updateEntity(target) { it.with(ControllerComponent(d.player2)) }
        FloatingTargetingRestriction.prevents(changed, target, d.player2) shouldBe false
        val pruned = StateBasedActionChecker(cardRegistry = d.cardRegistry).checkAndApply(changed).newState
        val regained = pruned.updateEntity(target) { it.with(ControllerComponent(d.player1)) }
        FloatingTargetingRestriction.prevents(regained, target, d.player2) shouldBe false
    }
})
