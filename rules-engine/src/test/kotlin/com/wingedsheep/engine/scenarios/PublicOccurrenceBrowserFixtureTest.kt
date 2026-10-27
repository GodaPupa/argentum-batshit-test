package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Export real engine suspensions for the existing server snapshot endpoint; no corpus registration. */
class PublicOccurrenceBrowserFixtureTest : ScenarioTestBase() {
    init {
        for (count in listOf(2, 3)) {
            test("UI fixture exports serialized public occurrence count=$count") {
                val game = scenario().withPlayers("UI One", "UI Two")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Hill Giant")
                    .withCardOnBattlefield(2, "Craw Wurm")
                    .withCardInHand(1, "Forest").withCardInHand(2, "Island").build()
                val source = game.findPermanent("Grizzly Bears")!!
                val old = game.findPermanent("Hill Giant")!!
                val recipient = game.findPermanent("Craw Wurm")!!
                val state = game.state
                val selection = TargetSlotPlan(List(count) { TargetInstanceSelection(listOf(
                    TargetSlot(ChosenTarget.Permanent(old), state.objectRef(old)))) })
                val plan = CurrentTargetPlan(selection)
                val references = ObjectReferenceEnvironment(captured = true,
                    origin = state.objectRef(source), source = state.objectRef(source))
                val placed = StackResolver(cardRegistry).putActivatedAbility(state,
                    ActivatedAbilityOnStackComponent(source, "Grizzly Bears", game.player1Id,
                        Effects.GainLife(0), objectReferences = references, originalTargetSlots = selection),
                    plan.targets, List(count) { TargetObject(filter = TargetFilter(GameObjectFilter.Creature)) },
                    currentTargetPlan = plan)
                placed.error shouldBe null
                val stack = placed.state.stack.last()
                val change = Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(recipient))
                val effect = if (count == 2) change else Effects.Composite(change, change, Effects.GainLife(4))
                val offered = EngineServices(cardRegistry).effectExecutorRegistry.execute(placed.state, effect,
                    EffectContext(sourceId = source, controllerId = game.player2Id,
                        targets = listOf(ChosenTarget.Spell(stack))))
                offered.error shouldBe null
                val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
                val encoded = json.encodeToString(GameState.serializer(), offered.state)
                val restored = json.decodeFromString(GameState.serializer(), encoded)
                val question = restored.pendingDecision as ChooseOptionDecision
                question.playerId shouldBe game.player2Id
                question.options.size shouldBe count
                question.options.distinct().size shouldBe count
                question.optionCardIds shouldBe (0 until count).associateWith { listOf(old) }
                restored.getEntity(stack)!!.currentTargetPlan() shouldBe plan
                val output = Path.of(System.getenv("MANUAL_UI_FIXTURE_DIR") ?: "build/manual-ui-fixtures")
                Files.createDirectories(output)
                Files.writeString(output.resolve("occurrences-$count.json"), encoded)
                Files.writeString(output.resolve("occurrences-$count-meta.json"), buildJsonObject {
                    put("stack", stack.value); put("old", old.value); put("recipient", recipient.value)
                    put("chooser", game.player2Id.value); put("nonowner", game.player1Id.value)
                    put("options", JsonArray(question.options.map(::JsonPrimitive)))
                    put("initialLife", restored.lifeTotal(game.player2Id))
                }.toString())
            }
        }
    }
}
