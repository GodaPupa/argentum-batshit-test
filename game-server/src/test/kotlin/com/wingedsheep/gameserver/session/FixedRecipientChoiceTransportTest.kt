package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Transport shape only; engine occurrence legality is qualified separately. */
class FixedRecipientChoiceTransportTest : ScenarioTestBase() {
    init {
        test("routing and JSON retain distinct occurrence labels for the same preview card") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Hill Giant").build()
            val card = game.findPermanent("Hill Giant")!!
            val raw = ChooseOptionDecision(
                id = "occurrence-1", playerId = game.player1Id,
                prompt = "Choose the target occurrence to change",
                context = DecisionContext(sourceId = card),
                options = listOf("Group 1, target 1, division 2", "Group 2, target 1, division 5"),
                optionCardIds = mapOf(0 to listOf(card), 1 to listOf(card)),
            )
            val routed = raw.withClientRoutingId("epoch:occurrence-1")
                .shouldBeInstanceOf<ChooseOptionDecision>()
            routed shouldBe raw.copy(id = "epoch:occurrence-1")
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            json.decodeFromString<PendingDecision>(json.encodeToString<PendingDecision>(routed)) shouldBe routed
            raw.id shouldBe "occurrence-1"
            raw.options.size shouldBe 2
        }

        test("nested routing changes only identity and enrichment preserves occurrence payload") {
            val game = scenario().withPlayers().build()
            val raw = ChooseOptionDecision(
                id = "occurrence-2", playerId = game.player1Id,
                prompt = "Choose the next target occurrence",
                context = DecisionContext(),
                options = listOf("Group 1, target 1", "Group 3, target 2"),
            )
            val routed = raw.withClientRoutingId("epoch:occurrence-2")
            DecisionEnricher(cardRegistry).enrich(routed, game.state, game.player1Id) shouldBe
                raw.copy(id = "epoch:occurrence-2")
            raw.withClientRoutingId("other-epoch:occurrence-2") shouldBe
                raw.copy(id = "other-epoch:occurrence-2")
        }

        for (firstIndex in 0..1) {
            test("live occurrence transport preserves owner tokens replay and nested index $firstIndex") {
                val game = scenario().withPlayers().withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Hill Giant").withCardOnBattlefield(2, "Craw Wurm").build()
                val source = game.findPermanent("Grizzly Bears")!!
                val old = game.findPermanent("Hill Giant")!!
                val recipient = game.findPermanent("Craw Wurm")!!
                val chooser = game.player2Id
                val slot = TargetSlot(ChosenTarget.Permanent(old), game.state.objectRef(old))
                val original = TargetSlotPlan(List(3) { TargetInstanceSelection(listOf(slot)) })
                val plan = CurrentTargetPlan(original)
                val requirement = TargetObject(filter = TargetFilter(GameObjectFilter.Creature))
                val refs = ObjectReferenceEnvironment(captured = true,
                    origin = game.state.objectRef(source), source = game.state.objectRef(source))
                val placed = StackResolver(cardRegistry).putActivatedAbility(game.state,
                    ActivatedAbilityOnStackComponent(source, "Source", game.player1Id,
                        Effects.Destroy(EffectTarget.ContextTarget(0)), objectReferences = refs,
                        originalTargetSlots = original),
                    plan.targets, List(3) { requirement }, currentTargetPlan = plan)
                placed.error shouldBe null
                val stack = placed.state.stack.last()
                val change = Effects.ChangeTargetTo(recipient = EffectTarget.SpecificEntity(recipient))
                val offered = EngineServices(cardRegistry).effectExecutorRegistry.execute(placed.state,
                    Effects.Composite(change, Effects.Composite(change, Effects.GainLife(4))),
                    EffectContext(sourceId = null, controllerId = chooser,
                        targets = listOf(ChosenTarget.Spell(stack))))
                offered.error shouldBe null
                val raw = offered.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
                raw.options.size shouldBe 3
                val session = GameSession(cardRegistry = cardRegistry)
                val ws1 = mockk<WebSocketSession>(relaxed = true) { every { id } returns "one" }
                val ws2 = mockk<WebSocketSession>(relaxed = true) { every { id } returns "two" }
                session.injectStateForTesting(offered.state, mapOf(
                    game.player1Id to PlayerSession(ws1, game.player1Id, "One"),
                    chooser to PlayerSession(ws2, chooser, "Two")))
                val full = session.createStateUpdate(chooser, emptyList())
                    .shouldBeInstanceOf<ServerMessage.StateUpdate>()
                val wire = full.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
                wire.id shouldNotBe raw.id
                wire.copy(id = raw.id) shouldBe raw
                session.createStateUpdate(game.player1Id, emptyList())
                    .shouldBeInstanceOf<ServerMessage.StateUpdate>().pendingDecision shouldBe null
                session.clearLastSentState(chooser)
                session.createStateUpdate(chooser, emptyList())
                    .shouldBeInstanceOf<ServerMessage.StateUpdate>().pendingDecision shouldBe wire
                val stale = SubmitDecision(chooser, OptionChosenResponse(raw.id, firstIndex))
                session.executeClientAction(chooser, stale, "retry", full.interactionEpoch)
                    .shouldBeInstanceOf<GameSession.ActionResult.Failure>()
                session.getStateForTesting() shouldBe offered.state
                session.getRecordedActions() shouldBe emptyList()
                val canonicalFirst = SubmitDecision(chooser, OptionChosenResponse(raw.id, firstIndex))
                val first = session.executeClientAction(chooser,
                    SubmitDecision(chooser, OptionChosenResponse(wire.id, firstIndex)),
                    "retry", full.interactionEpoch)
                    .shouldBeInstanceOf<GameSession.ActionResult.PausedForDecision>()
                val replayFirst = actionProcessor.process(offered.state, canonicalFirst).result
                replayFirst.error shouldBe null
                first.state shouldBe replayFirst.state
                first.events shouldBe replayFirst.events
                session.getRecordedActions() shouldBe listOf(canonicalFirst)
                val rawSecond = first.state.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
                rawSecond.options.size shouldBe 2
                val next = session.createStateUpdate(chooser, first.events)
                    .shouldBeInstanceOf<ServerMessage.StateDeltaUpdate>()
                val wireSecond = next.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
                wireSecond.id shouldNotBe wire.id
                wireSecond.copy(id = rawSecond.id) shouldBe rawSecond
                val beforeIds = session.getLastMessageIdsForPersistence()
                session.executeClientAction(chooser,
                    SubmitDecision(chooser, OptionChosenResponse(wire.id, 0)),
                    "next-retry", next.interactionEpoch)
                    .shouldBeInstanceOf<GameSession.ActionResult.Failure>()
                session.getStateForTesting() shouldBe first.state
                session.getRecordedActions() shouldBe listOf(canonicalFirst)
                session.getLastMessageIdsForPersistence() shouldBe beforeIds
                val canonicalSecond = SubmitDecision(chooser, OptionChosenResponse(rawSecond.id, 1))
                val done = session.executeClientAction(chooser,
                    SubmitDecision(chooser, OptionChosenResponse(wireSecond.id, 1)),
                    "next-retry", next.interactionEpoch)
                    .shouldBeInstanceOf<GameSession.ActionResult.Success>()
                val replaySecond = actionProcessor.process(replayFirst.state, canonicalSecond).result
                replaySecond.error shouldBe null
                done.state shouldBe replaySecond.state
                done.events shouldBe replaySecond.events
                done.state.pendingDecision shouldBe null
                done.state.continuationStack shouldBe emptyList()
                done.state.lifeTotal(chooser) shouldBe offered.state.lifeTotal(chooser) + 4
                session.getRecordedActions() shouldBe listOf(canonicalFirst, canonicalSecond)
            }
        }
    }
}
