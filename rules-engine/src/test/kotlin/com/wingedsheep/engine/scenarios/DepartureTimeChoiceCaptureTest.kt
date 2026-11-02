package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Tests the actual departure service, with independently constructed complete expected frames. */
class DepartureTimeChoiceCaptureTest : ScenarioTestBase() {
    private val selected = mapOf(ChoiceSlot.SELF_ALTERNATIVE to ChoiceValue.Flag)
    private val variants = listOf("queue", "target", "distribution", "may", "opponent", "batch", "order")

    init {
        cardRegistry.register(com.wingedsheep.sdk.dsl.card("Departure Characteristics Probe") {
            manaCost = "{U}"; typeLine = "Artifact Creature — Merfolk"; power = 2; toughness = 2
        })
        test("DT departure characteristics include colors types subtypes and counters") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Departure Characteristics Probe").build()
            val source = game.findPermanent("Departure Characteristics Probe")!!
            game.state = game.state.updateEntity(source) { it
                .with(com.wingedsheep.engine.state.components.battlefield.CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3)))
                .with(CastChoicesComponent(chosen = selected)) }
            val projected = EntitySnapshot.fromProjection(source, game.state)
            projected.power shouldBe 5
            game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND).state
            roundTrip(game)
            val captured = game.state.getEntity(source)!!.get<LastKnownPermanentComponent>()!!.snapshot
            captured.power shouldBe 5
            captured.toughness shouldBe projected.toughness
            captured.colors shouldBe projected.colors
            captured.subtypes shouldBe projected.subtypes
            captured.typeLine shouldBe projected.typeLine
            captured.counters shouldBe projected.counters
            captured.objectRef shouldBe projected.objectRef
            captured.castChoices shouldBe selected
        }
        for (variant in variants) for (paid in listOf(false, true)) for (mode in listOf("missing", "genuine", "stale")) {
            test("DT continuation=$variant selected=$paid capture=$mode") {
                val game = board()
                val source = game.findPermanent("Grizzly Bears")!!
                val target = game.findPermanent("Hill Giant")!!
                game.state = game.state.updateEntity(source) {
                    it.with(CastChoicesComponent(chosen = if (paid) selected else emptyMap()))
                }
                val origin = game.state.objectRef(source)!!
                val refs = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin)
                val prior = if (mode == "genuine") EntitySnapshot.fromProjection(source, game.state)
                    .copy(power = 71, colors = setOf("BLUE"), castChoices = if (paid) emptyMap() else selected) else null
                val trigger = PendingTrigger(
                    ability = TriggeredAbility.create(trigger = Triggers.EntersBattlefield.event, effect = Effects.GainLife(3)),
                    sourceId = source, sourceName = "Departure transport", controllerId = game.player1Id,
                    sourceBattlefieldTimestamp = 191L, objectReferences = refs, triggerContext = TriggerContext(),
                    placementOrderChosen = true, opponentTargetChooserId = game.player2Id,
                    lastKnownSourceSnapshot = prior)
                val unrelated = trigger.copy(sourceId = target, objectReferences = ObjectReferenceEnvironment(
                    captured = true, origin = game.state.objectRef(target), source = game.state.objectRef(target)),
                    lastKnownSourceSnapshot = null)
                val decisionContext = DecisionContext(sourceId = source, sourceName = "original", phase = DecisionPhase.TRIGGER)
                val question: PendingDecision = when (variant) {
                    "target" -> ChooseTargetsDecision("retained-question", game.player1Id, "retained prompt", decisionContext,
                        listOf(TargetRequirementInfo(0, "a creature")), mapOf(0 to listOf(target)))
                    "distribution" -> DistributeDecision("retained-question", game.player1Id, "retained prompt", decisionContext,
                        totalAmount = 3, targets = listOf(target), minPerTarget = 1)
                    "batch" -> BatchYesNoDecision("retained-question", game.player1Id, "retained prompt", decisionContext, count = 3)
                    "opponent", "order" -> ChooseOptionDecision("retained-question", game.player1Id, "retained prompt", decisionContext,
                        options = listOf("first", "second"), optionCardIds = mapOf(0 to listOf(source), 1 to listOf(target)))
                    else -> YesNoDecision("retained-question", game.player1Id, "retained prompt", decisionContext,
                        yesText = "keep yes", noText = "keep no", hint = "keep hint")
                }
                fun frame(lki: EntitySnapshot?): ContinuationFrame {
                    val pending = trigger.copy(lastKnownSourceSnapshot = lki)
                    val answer: AnswerContinuation = when (variant) {
                        "target" -> TriggeredAbilityContinuation(source, "original", controllerId = game.player1Id,
                            effect = Effects.GainLife(3), description = "original description", objectReferences = refs,
                            sourceBattlefieldTimestamp = 191L, lastKnownSourceSnapshot = lki,
                            targetRequirements = listOf(TargetCreature()), capturedEntityIds = listOf(target, source),
                            triggerDamageAmount = 7, triggerCounterCount = 8, xValue = 9)
                        "distribution" -> TriggerDamageDistributionContinuation(source, "original", controllerId = game.player1Id,
                            effect = Effects.GainLife(3), description = "distribution description", objectReferences = refs,
                            sourceBattlefieldTimestamp = 191L, lastKnownSourceSnapshot = lki,
                            selectedTargets = listOf(ChosenTarget.Permanent(target)), targetRequirements = listOf(TargetCreature()),
                            totalDamage = 3, triggerDamageAmount = 7, capturedEntityIds = listOf(source, target))
                        "may" -> MayTriggerContinuation(pending, TargetCreature())
                        "opponent" -> TriggerOpponentChooserContinuation(pending, TargetCreature(), listOf(game.player2Id))
                        "batch" -> BatchMayTriggerContinuation(listOf(unrelated, pending, pending))
                        "order" -> TriggerOrderingContinuation(listOf(pending, unrelated), listOf(unrelated, pending))
                        else -> return PendingTriggersContinuation(listOf(unrelated, pending, pending), settleAfterManaPayment = true)
                    }
                    return Suspension(question, answer)
                }
                if (mode == "stale") {
                    game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND).state
                    game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.BATTLEFIELD).state
                        .updateEntity(source) { it.with(CastChoicesComponent(chosen = if (paid) emptyMap() else selected)) }
                    game.state.isCurrentObject(origin) shouldBe false
                }
                val beforeFrames = listOf(frame(prior))
                game.state = game.state.copy(continuationStack = beforeFrames)
                roundTrip(game)
                val routing = game.state.nextRoutingId
                game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND).state
                val departure = game.state.getEntity(source)!!.get<LastKnownPermanentComponent>()!!.snapshot
                val expected = when (mode) { "missing" -> departure; "genuine" -> prior; else -> null }
                if (mode == "missing") {
                    departure.objectRef shouldBe origin
                    departure.castChoices shouldBe if (paid) selected else emptyMap()
                    departure.power shouldBe 2
                }
                game.state.continuationStack shouldBe listOf(frame(expected))
                game.state.nextRoutingId shouldBe routing
                if (variant != "queue") game.state.pendingDecision shouldBe question
                roundTrip(game)
                // Subsequent moves and a fresh opposite choice may not replace the first departure.
                game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.EXILE).state
                game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.BATTLEFIELD).state
                    .updateEntity(source) { it.with(CastChoicesComponent(chosen = if (paid) emptyMap() else selected)) }
                game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.GRAVEYARD).state
                game.state.continuationStack shouldBe listOf(frame(expected))
                roundTrip(game)
            }
        }

        for (paid in listOf(false, true)) {
            test("DT distribution resumes after serialized departure selected=$paid") {
                val game = board()
                val source = game.findPermanent("Grizzly Bears")!!
                val target = game.findPermanent("Hill Giant")!!
                game.state = game.state.updateEntity(source) { it.with(CastChoicesComponent(chosen = if (paid) selected else emptyMap())) }
                val origin = game.state.objectRef(source)!!
                val answer = TriggerDamageDistributionContinuation(source, "Distribution probe", controllerId = game.player1Id,
                    effect = Effects.GainLife(3), description = "gain life", selectedTargets = listOf(ChosenTarget.Permanent(target)),
                    targetRequirements = listOf(TargetCreature()), totalDamage = 3,
                    objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin),
                    interveningIf = CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE))
                val pause = game.state.suspendForDecision(
                    { id -> DistributeDecision(id, game.player1Id, "assign three", DecisionContext(sourceId = source),
                        totalAmount = 3, targets = listOf(target), minPerTarget = 1) }, answer)
                pause.error shouldBe null
                pause.isPaused shouldBe true
                game.state = pause.state
                roundTrip(game)
                val question = game.state.pendingDecision!!
                game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.HAND).state
                game.state = ZoneTransitionService.moveToZone(game.state, source, Zone.EXILE).state
                roundTrip(game)
                game.state.pendingDecision shouldBe question
                game.execute(SubmitDecision(game.player1Id, DistributionResponse(question.id, mapOf(target to 3)))).error shouldBe null
                game.state.stack.size shouldBe 1
                roundTrip(game)
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.stack.size shouldBe 0
                game.getLifeTotal(1) shouldBe if (paid) 23 else 20
            }
        }
    }

    private fun board(): TestGame {
        var b = scenario().withPlayers("Player", "Opponent")
            .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Hill Giant")
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(5) { b = b.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain") }
        return b.build()
    }

    private fun roundTrip(game: TestGame) {
        val before = game.state
        game.state = SerializationTestSupport.roundTrip(before)
        game.state shouldBe before
    }
}
