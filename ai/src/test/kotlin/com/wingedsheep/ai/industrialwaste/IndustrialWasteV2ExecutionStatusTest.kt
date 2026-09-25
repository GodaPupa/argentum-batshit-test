package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Existing excluded seed, semantic setups only. No corpus, allocation or candidate result is read. */
class IndustrialWasteV2ExecutionStatusTest : FunSpec({
    fun driver() = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { registerCards(it.cards); registerCards(it.basicLands) }
        initMirrorMatch(Deck.of("Forest" to 60), seed = 9_250_925_005L)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun submit(game: GameTestDriver, tracker: IndustrialWasteV2ExecutionTracker, action: GameAction) =
        tracker.submit(action) { before, actual ->
            before shouldBe game.state
            game.submit(actual)
        }
    fun responder(game: GameTestDriver) = DecisionResponder(GameSimulator(game.cardRegistry),
        AIPlayer.defaultEvaluator(), CardAdvisorRegistry().also { IndustrialWasteV2PilotAdvisorModule.register(it) })

    test("initialization does not count but both players London submissions each count once") {
        val game = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { registerCards(it.cards); registerCards(it.basicLands) }
            initMirrorMatch(Deck.of("Forest" to 60), skipMulligans = false, seed = 9_250_925_005L)
        }
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        tracker.snapshot().submittedActions shouldBe 0
        submit(game, tracker, TakeMulligan(game.player1))!!.error shouldBe null
        submit(game, tracker, KeepHand(game.player1))!!.error shouldBe null
        submit(game, tracker, KeepHand(game.player2))!!.error shouldBe null
        submit(game, tracker, BottomCards(game.player1, listOf(game.state.getHand(game.player1).first())))!!.error shouldBe null
        val status = tracker.snapshot()
        status.submittedActions shouldBe 4
        status.acceptedActions shouldBe 4
        status.status shouldBe IndustrialWasteV2StopStatus.RUNNING
        status.ownTurnsStarted shouldBe 1
        status.ownTurnsCompleted shouldBe 0
    }

    test("PLAY and DRAW own eighth turns include end and cleanup decisions before turn-cap status") {
      for (seat in 0..1) {
        val game = driver()
        val observed = if (seat == 0) game.player1 else game.player2
        game.putCardInHand(observed, "Forest")
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, observed)
        val simulator = GameSimulator(game.cardRegistry)
        val responder = responder(game)
        var sawOwnEighthMain = false
        var sawOwnEighthEnd = false
        var sawOwnEighthCleanupDecision = false
        while (tracker.snapshot().status == IndustrialWasteV2StopStatus.RUNNING) {
            val state = game.state
            val turn = state.getEntity(observed)!!.get<PlayerTurnsTakenComponent>()!!.count
            if (state.activePlayerId == observed && turn == 8) {
                if (state.step == Step.PRECOMBAT_MAIN) sawOwnEighthMain = true
                if (state.step == Step.END) sawOwnEighthEnd = true
                if (state.step == Step.CLEANUP && state.pendingDecision != null) sawOwnEighthCleanupDecision = true
                tracker.snapshot().turnCapReached shouldBe false
            }
            val decision = state.pendingDecision
            var action = if (decision != null) SubmitDecision(decision.playerId, responder.respond(state, decision, decision.playerId))
                else IndustrialWasteV2PublicActionPolicy.choosePassive(state, state.priorityPlayerId!!,
                    simulator.getLegalActions(state, state.priorityPlayerId!!))
            // Keep eight cards for a genuine T8 cleanup discard, including when acting second.
            if (turn == 8 && action is PlayLand && action.playerId == observed) action = PassPriority(action.playerId)
            submit(game, tracker, action)!!.error shouldBe null
        }
        sawOwnEighthMain shouldBe true
        sawOwnEighthEnd shouldBe true
        sawOwnEighthCleanupDecision shouldBe true
        val status = tracker.snapshot()
        status.status shouldBe IndustrialWasteV2StopStatus.TURN_CAP
        status.ownTurnsStarted shouldBe 8
        status.ownTurnsCompleted shouldBe 8
        status.acceptedActions shouldBe status.submittedActions
        status.engineGameOver shouldBe false
        status.engineWinnerId shouldBe null
        status.actionCapReached shouldBe false
        var called = false
        shouldThrow<IllegalStateException> { tracker.submit(PassPriority(game.player2)) { _, _ -> called = true; error("must not execute") } }
        called shouldBe false
      }
    }

    test("four thousand actual neutral-loop submissions stop without resolving an extra action") {
        val game = driver()
        game.replaceState(game.state.copy(zones = game.state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
        val player = game.player1
        val altar = game.putPermanentOnBattlefield(player, "Ashnod's Altar")
        game.putPermanentOnBattlefield(player, "Myr Retriever")
        game.putCardInGraveyard(player, "Myr Retriever")
        game.putCardInGraveyard(player, "Ichor Wellspring") // real competing target requires a decision
        val ability = game.cardRegistry.requireCard("Ashnod's Altar").activatedAbilities.single()
        val responder = responder(game)
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, player)
        val terminalReplay = IndustrialWasteV2ExecutionTracker(game.state, player)
        var decisionCount = 0
        var passCount = 0
        var beforeFinal = game.state
        while (tracker.snapshot().submittedActions < 4000) {
            tracker.snapshot().status shouldBe IndustrialWasteV2StopStatus.RUNNING
            val state = game.state
            val decision = state.pendingDecision
            val inHand = state.getHand(player).firstOrNull { state.getEntity(it)!!.get<CardComponent>()!!.name == "Myr Retriever" }
            val action = when {
                decision != null -> SubmitDecision(decision.playerId, responder.respond(state, decision, decision.playerId))
                state.stack.isNotEmpty() -> PassPriority(state.priorityPlayerId!!)
                inHand != null -> CastSpell(player, inHand)
                else -> ActivateAbility(player, altar, ability.id, costPayment = AdditionalCostPayment(
                    sacrificedPermanents = listOf(state.projectedState.getBattlefieldControlledBy(player).single {
                        state.getEntity(it)!!.get<CardComponent>()!!.name == "Myr Retriever"
                    })))
            }
            if (action is SubmitDecision) decisionCount++
            if (action is PassPriority) passCount++
            val before = game.state
            val result = submit(game, tracker, action)!!
            result.error shouldBe null
            if (tracker.snapshot().submittedActions < 4000) {
                // Replay the same accepted status transcript; this is not a second engine replay.
                terminalReplay.submit(action) { replayBefore, _ -> replayBefore shouldBe before; result }
                beforeFinal = game.state
            }
        }
        val status = tracker.snapshot()
        status.status shouldBe IndustrialWasteV2StopStatus.ACTION_CAP
        status.submittedActions shouldBe 4000
        status.acceptedActions shouldBe 4000
        (decisionCount > 0 && passCount > 0) shouldBe true
        status.ownTurnsStarted shouldBe 1
        status.ownTurnsCompleted shouldBe 0
        status.engineGameOver shouldBe false
        status.engineWinnerId shouldBe null
        status.pendingDecisionAtStop shouldBe (game.state.pendingDecision != null)
        status.stackDepthAtStop shouldBe game.state.stack.size
        Json.decodeFromString<IndustrialWasteV2ExecutionStatus>(Json.encodeToString(status)) shouldBe status
        shouldThrow<IllegalStateException> { tracker.submit(Concede(player)) { _, action -> game.submit(action) } }
        tracker.snapshot() shouldBe status
        // Branch this excluded semantic fixture at action 3999, then perform a real concession.
        // It proves that terminal resolution on the final permitted action takes precedence.
        game.replaceState(beforeFinal)
        submit(game, terminalReplay, Concede(game.player2))!!.error shouldBe null
        terminalReplay.snapshot().submittedActions shouldBe 4000
        terminalReplay.snapshot().acceptedActions shouldBe 4000
        terminalReplay.snapshot().actionCapReached shouldBe true
        terminalReplay.snapshot().status shouldBe IndustrialWasteV2StopStatus.REAL_TERMINAL
        terminalReplay.snapshot().engineWinnerId shouldBe player.toString()
    }

    test("real concession is terminal with its actual engine winner and reason") {
        val game = driver()
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        submit(game, tracker, Concede(game.player2))!!.events.filterIsInstance<GameEndedEvent>().single().winnerId shouldBe game.player1
        val status = tracker.snapshot()
        status.status shouldBe IndustrialWasteV2StopStatus.REAL_TERMINAL
        status.engineWinnerId shouldBe game.player1.toString()
        status.engineEndReason shouldBe GameEndReason.CONCESSION.name
        status.submittedActions shouldBe 1
        status.acceptedActions shouldBe 1
    }

    test("real SBA settlement of a zero-life setup produces draw and never a synthetic winner") {
        val game = driver()
        game.replaceState(game.state.updateEntity(game.player1) { it.with(LifeTotalComponent(0)) }
            .updateEntity(game.player2) { it.with(LifeTotalComponent(0)) })
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        // Life zero alone is not a terminal classification; a real action must settle SBAs.
        tracker.snapshot().status shouldBe IndustrialWasteV2StopStatus.RUNNING
        submit(game, tracker, Concede(game.player1))!!.error shouldBe null
        tracker.snapshot().status shouldBe IndustrialWasteV2StopStatus.DRAW
        tracker.snapshot().engineWinnerId shouldBe null
        tracker.snapshot().engineEndReason shouldBe GameEndReason.DRAW.name
    }

    test("rejected action counts once stops exposure and preserves last accepted state") {
        val game = driver()
        val before = game.state
        val tracker = IndustrialWasteV2ExecutionTracker(before, game.player1)
        val other = before.turnOrder.single { it != before.priorityPlayerId }
        submit(game, tracker, PassPriority(other))!!.isSuccess shouldBe false
        tracker.snapshot().status shouldBe IndustrialWasteV2StopStatus.REJECTED_ACTION
        tracker.snapshot().submittedActions shouldBe 1
        tracker.snapshot().acceptedActions shouldBe 0
        tracker.state shouldBe before
        game.state shouldBe before
        shouldThrow<IllegalStateException> { submit(game, tracker, PassPriority(before.priorityPlayerId!!)) }
    }

    test("throwing executor records a submitted exception without retry or engine winner") {
        val game = driver()
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        var calls = 0
        tracker.submit(PassPriority(game.player1)) { _, _ -> calls++; error("excluded injected executor fault") } shouldBe null
        calls shouldBe 1
        tracker.snapshot().status shouldBe IndustrialWasteV2StopStatus.EXCEPTION
        tracker.snapshot().submittedActions shouldBe 1
        tracker.snapshot().acceptedActions shouldBe 0
        tracker.snapshot().engineWinnerId shouldBe null
        tracker.state shouldBe game.state
    }

    test("unresolved observer overrides provisional terminal while preserving actual terminal diagnostics") {
        val game = driver()
        val tracker = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        submit(game, tracker, Concede(game.player2))
        tracker.markUnresolved("fixed fixture missing mana checkpoint evidence")
        tracker.snapshot().status shouldBe IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY
        tracker.snapshot().engineGameOver shouldBe true
        tracker.snapshot().engineWinnerId shouldBe game.player1.toString()
        tracker.snapshot().acceptedActions shouldBe 1
    }

    test("forged terminal and missing turn-event provenance remain unresolved") {
        val game = driver()
        val missingEnd = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        missingEnd.submit(PassPriority(game.player1)) { state, _ -> ExecutionResult.success(state.copy(gameOver = true, winnerId = game.player1)) }
        missingEnd.snapshot().status shouldBe IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY
        val missingTurn = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        missingTurn.submit(PassPriority(game.player1)) { state, _ -> ExecutionResult.success(state.copy(turnNumber = state.turnNumber + 1)) }
        missingTurn.snapshot().status shouldBe IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY
        val counterJump = IndustrialWasteV2ExecutionTracker(game.state, game.player1)
        counterJump.submit(PassPriority(game.player1)) { state, _ -> ExecutionResult.success(state.updateEntity(game.player1) {
            it.with(PlayerTurnsTakenComponent(8))
        }) }
        counterJump.snapshot().status shouldBe IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY
    }

    test("synthetic simultaneous boundary precedence preserves genuine terminal and quarantines faults") {
        val classify = IndustrialWasteV2ExecutionTracker.Companion::classify
        classify(null, true, true, true, true) shouldBe IndustrialWasteV2StopStatus.REAL_TERMINAL
        classify(null, true, false, true, true) shouldBe IndustrialWasteV2StopStatus.DRAW
        classify(null, false, false, true, true) shouldBe IndustrialWasteV2StopStatus.ACTION_CAP
        listOf(IndustrialWasteV2StopStatus.EXCEPTION, IndustrialWasteV2StopStatus.REJECTED_ACTION,
            IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY).forEach { fault ->
            classify(fault, true, true, true, true) shouldBe fault
        }
    }
})
