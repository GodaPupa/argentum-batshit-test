package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Fixed engineering fixtures, excluded from experiment games and seed allocation. */
class AbilitySourceCharacteristicsTest : ScenarioTestBase() {
    init {
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val restrictions = linkedMapOf(
            "none" to "", "color" to "PROTECTION_FROM_RED",
            "subtype" to "PROTECTION_FROM_SUBTYPE_ZOMBIE",
            "cardtype" to "PROTECTION_FROM_CARDTYPE_ARTIFACT",
            "abilitytype" to "CANT_BE_TARGETED_BY_CARDTYPE_ARTIFACT_SOURCE_ABILITIES",
        )
        for (kind in listOf("trigger", "activation")) for (visit in listOf("live", "removed", "returned")) {
            for ((restriction, keyword) in restrictions) test("SC $kind $visit $restriction") {
                val game = scenario().withPlayers("One", "Two")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Craw Wurm").build()
                val source = game.findPermanent("Grizzly Bears")!!
                val target = game.findPermanent("Craw Wurm")!!
                val origin = game.state.objectRef(source)!!
                val selection = TargetSlotPlan(listOf(TargetInstanceSelection(listOf(
                    TargetSlot(ChosenTarget.Permanent(target), game.state.objectRef(target))))))
                val plan = CurrentTargetPlan(selection)
                val references = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin)
                val resolver = StackResolver(cardRegistry)
                val effect = Effects.Destroy(EffectTarget.ContextTarget(0))
                val queued = if (kind == "activation") resolver.putActivatedAbility(game.state,
                    ActivatedAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect,
                        objectReferences = references, originalTargetSlots = selection),
                    plan.targets, listOf(Targets.Creature), currentTargetPlan = plan)
                else resolver.putTriggeredAbility(game.state,
                    TriggeredAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id, effect, "Source characteristics",
                        objectReferences = references, originalTargetSlots = selection),
                    plan.targets, listOf(Targets.Creature), currentTargetPlan = plan)
                queued.error shouldBe null
                val stackId = queued.state.stack.last()
                val context = EffectContext(sourceId = source, controllerId = game.player1Id)
                // Change all three characteristics AFTER stacking; printed card data stays green/Bear/nonartifact.
                var state = queued.state.addFloatingEffect(Layer.COLOR,
                    SerializableModification.ChangeColor(setOf("RED")), setOf(source), Duration.EndOfTurn, context)
                    .addFloatingEffect(Layer.TYPE, SerializableModification.AddSubtype("Zombie"), setOf(source), Duration.EndOfTurn, context)
                    .addFloatingEffect(Layer.TYPE, SerializableModification.AddType("ARTIFACT"), setOf(source), Duration.EndOfTurn, context)
                state.getEntity(source)!!.get<CardComponent>()!!.colors shouldBe setOf(Color.GREEN)
                state.projectedState.getColors(source) shouldBe setOf("RED")
                state.projectedState.getSubtypes(source).contains("Zombie") shouldBe true
                state.projectedState.getTypes(source).contains("ARTIFACT") shouldBe true
                val snapshot = EntitySnapshot.fromProjection(source, state)
                if (visit != "live") {
                    state = state.updateEntity(stackId) { container ->
                        if (kind == "activation") container.with(container.get<ActivatedAbilityOnStackComponent>()!!
                            .copy(lastKnownSourceSnapshot = snapshot))
                        else container.with(container.get<TriggeredAbilityOnStackComponent>()!!
                            .copy(lastKnownSourceSnapshot = snapshot))
                    }
                    if (visit == "removed") state = state.removeEntity(source)
                    else {
                        val zone = state.logicalZone(source)!!
                        val grave = ZoneKey(game.player1Id, Zone.GRAVEYARD)
                        state = state.removeFromZone(zone, source).addToZone(grave, source)
                            .removeFromZone(grave, source).addToZone(zone, source)
                            .copy(floatingEffects = emptyList())
                            .updateEntity(source) { it.with(BattlefieldEntryTimestampComponent(99999L)) }
                        state.isCurrentObject(origin) shouldBe false
                        state.projectedState.getColors(source) shouldBe setOf("GREEN")
                        state.projectedState.getSubtypes(source).contains("Zombie") shouldBe false
                        state.projectedState.getTypes(source).contains("ARTIFACT") shouldBe false
                    }
                }
                if (keyword.isNotEmpty()) state = state.addFloatingEffect(Layer.ABILITY,
                    SerializableModification.GrantKeyword(keyword), setOf(target), Duration.EndOfTurn, context)
                val encoded = json.encodeToString(GameState.serializer(), state)
                state = json.decodeFromString(GameState.serializer(), encoded)
                json.encodeToString(GameState.serializer(), state) shouldBe encoded
                state.getEntity(stackId)!!.currentTargetPlan() shouldBe plan
                val result = resolver.resolveTop(state)
                result.error shouldBe null
                result.state.stack shouldBe emptyList()
                (target in result.state.getBattlefield()) shouldBe (restriction != "none")
            }
        }
    }
}
