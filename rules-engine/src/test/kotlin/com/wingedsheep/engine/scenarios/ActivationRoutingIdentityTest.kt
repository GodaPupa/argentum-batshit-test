package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.GameObjectFilter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Identical snapshot/action inputs must produce identical authoritative state and events. */
class ActivationRoutingIdentityTest : ScenarioTestBase() {
    private val simple = card("Activation Routing Source") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 2
        activatedAbility { cost = Costs.Mana("{1}"); effect = Effects.GainLife(1) }
    }
    private val choosing = card("Activation Routing Choice Source") {
        manaCost = "{0}"; typeLine = "Creature — Wizard"; power = 1; toughness = 2
        activatedAbility {
            cost = Costs.Composite(Costs.Mana("{1}"), Costs.Sacrifice(GameObjectFilter.Artifact))
            effect = Effects.GainLife(1)
        }
    }
    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }
    private fun setup() = scenario().withPlayers("Activator", "Opponent")
        .withCardOnBattlefield(1, "Forest")
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Plains")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.fixed() = apply { state = state.copy(rng = GameRng.seeded(0xFE000081)) }

    init {
        cardRegistry.register(listOf(simple, choosing))

        test("fully bound activation replays full state and events without consuming RNG") {
            val game = setup().withCardOnBattlefield(1, simple.name).build().fixed()
            val source = game.findPermanent(simple.name)!!
            val before = game.state
            val action = ActivateAbility(game.player1Id, source, simple.activatedAbilities.single().id)
            val first = game.execute(action)
            first.error shouldBe null
            game.state = before
            val replay = game.execute(action)
            replay.error shouldBe null
            json.encodeToString(first) shouldBe json.encodeToString(replay)
            first.state.rng shouldBe before.rng
            first.state.nextRoutingId shouldBe before.nextRoutingId + 1
            val stackAbility = first.state.getEntity(first.state.stack.single())!!
                .get<ActivatedAbilityOnStackComponent>()!!
            stackAbility.objectReferences.resolutionKey shouldBe "r${before.nextRoutingId}"
            stackAbility.objectReferences.origin shouldBe before.objectRef(source)
            stackAbility.objectReferences.source shouldBe before.objectRef(source)
            first.state.nextEntityId shouldBe before.nextEntityId + 1
        }

        test("cost-choice pause and resumed activation each replay their exact routing state") {
            val game = setup().withCardOnBattlefield(1, choosing.name)
                .withCardOnBattlefield(1, "Bonesplitter").withCardOnBattlefield(1, "Ichor Wellspring")
                .build().fixed()
            val source = game.findPermanent(choosing.name)!!
            val fodder = game.findPermanent("Bonesplitter")!!
            val forest = game.findPermanent("Forest")!!
            val before = game.state
            val action = ActivateAbility(game.player1Id, source, choosing.activatedAbilities.single().id)
            val first = game.execute(action)
            first.error shouldBe null
            first.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
            first.state.stack.isEmpty() shouldBe true
            first.state.getEntity(forest)!!.has<TappedComponent>() shouldBe false
            first.state.rng shouldBe before.rng
            first.state.nextRoutingId shouldBe before.nextRoutingId + 1
            game.state = before
            val replay = game.execute(action)
            json.encodeToString(first) shouldBe json.encodeToString(replay)

            val pending = first.state
            game.state = pending
            val completed = game.selectCards(listOf(fodder))
            completed.error shouldBe null
            completed.state.pendingDecision shouldBe null
            game.state = pending
            val completedReplay = game.selectCards(listOf(fodder))
            completedReplay.error shouldBe null
            json.encodeToString(completed) shouldBe json.encodeToString(completedReplay)
            completed.state.rng shouldBe before.rng
            completed.state.nextRoutingId shouldBe pending.nextRoutingId + 1
            val stackAbility = completed.state.getEntity(completed.state.stack.single())!!
                .get<ActivatedAbilityOnStackComponent>()!!
            stackAbility.objectReferences.resolutionKey shouldBe "r${pending.nextRoutingId}"
            stackAbility.objectReferences.origin shouldBe before.objectRef(source)
        }
    }
}
