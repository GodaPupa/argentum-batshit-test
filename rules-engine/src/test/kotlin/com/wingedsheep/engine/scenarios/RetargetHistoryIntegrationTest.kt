package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AbilityFizzledEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.effects.stack.ContestedRetargetLogic
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Real retarget finalization and stack resolution; no complete retarget-choice/card admission. */
class RetargetHistoryIntegrationTest : ScenarioTestBase() {
    init {
        data class Fixture(val state: GameState, val ability: EntityId, val a: EntityId, val b: EntityId, val c: EntityId)
        fun fixture(single: Boolean = false): Fixture {
            val game = scenario().withPlayers("One", "Two")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(1, "Llanowar Elves").build()
            val a = requireNotNull(game.findPermanent("Grizzly Bears"))
            val b = requireNotNull(game.findPermanent("Hill Giant"))
            val c = requireNotNull(game.findPermanent("Llanowar Elves"))
            var state = game.state
            for (id in listOf(a,b,c)) state = state.updateEntity(id) { it.with(BattlefieldEntryTimestampComponent(1L)) }
            val chosen = if (single) listOf(ChosenTarget.Permanent(a)) else listOf(ChosenTarget.Permanent(a), ChosenTarget.Permanent(b))
            val original = TargetsComponent.capture(state, chosen)
            // Supply a later battlefield visit to exercise the existing resolver's stamp check.
            state = state.updateEntity(a) { it.with(BattlefieldEntryTimestampComponent(9L)) }
            val ability = EntityId("retarget-history-ability")
            state = state.withEntity(ability, ComponentContainer.of(
                ActivatedAbilityOnStackComponent(sourceId=b, sourceName="History fixture", controllerId=state.turnOrder[0],
                    effect=Effects.Composite(Effects.Destroy(EffectTarget.ContextTarget(0)),
                        Effects.Destroy(EffectTarget.ContextTarget(1)))), original)).pushToStack(ability)
            return Fixture(state, ability, a, b, c)
        }
        fun finish(f: Fixture, chosen: List<ChosenTarget>, state: GameState = f.state): GameState {
            val original = requireNotNull(state.getEntity(f.ability)?.get<TargetsComponent>())
            return ContestedRetargetLogic.advance(state, f.ability, state.turnOrder[0], state.turnOrder[0],
                emptyList(), original.targets, chosen, original.targets.size, f.b).state
        }
        test("HI01 live retarget finalization leaves an old target illegal during actual ability resolution") {
            val f = fixture()
            val changed = finish(f, listOf(ChosenTarget.Permanent(f.a), ChosenTarget.Permanent(f.c)))
            val result = StackResolver(cardRegistry).resolveTop(changed)
            result.error shouldBe null
            (f.a in result.state.getBattlefield()) shouldBe true
            (f.c in result.state.getBattlefield()) shouldBe false
        }
        test("HI02 live keep-all is byte-identical and an already-illegal only target still fizzles") {
            val f = fixture(single=true)
            val unchanged = finish(f, listOf(ChosenTarget.Permanent(f.a)))
            (unchanged === f.state) shouldBe true
            val result = StackResolver(cardRegistry).resolveTop(unchanged)
            result.error shouldBe null
            result.events.filterIsInstance<AbilityFizzledEvent>().size shouldBe 1
            (f.a in result.state.getBattlefield()) shouldBe true
        }
        test("HI03 an unrepresentable old and new visit leaves the live stack object untouched") {
            val f = fixture()
            val unchanged = finish(f, listOf(ChosenTarget.Permanent(f.a), ChosenTarget.Permanent(f.a)))
            (unchanged === f.state) shouldBe true
        }
        test("HI04 serialized retarget history reproduces the same actual resolution state and events") {
            val f = fixture()
            val changed = finish(f, listOf(ChosenTarget.Permanent(f.a), ChosenTarget.Permanent(f.c)))
            val json = Json { serializersModule=engineSerializersModule; allowStructuredMapKeys=true; encodeDefaults=true }
            val bytes = json.encodeToString(GameState.serializer(), changed)
            val loaded = json.decodeFromString(GameState.serializer(), bytes)
            json.encodeToString(GameState.serializer(), loaded) shouldBe bytes
            val direct = StackResolver(cardRegistry).resolveTop(changed)
            val replay = StackResolver(cardRegistry).resolveTop(loaded)
            direct.error shouldBe null
            replay.error shouldBe null
            replay.state shouldBe direct.state
            replay.events shouldBe direct.events
        }
    }
}
