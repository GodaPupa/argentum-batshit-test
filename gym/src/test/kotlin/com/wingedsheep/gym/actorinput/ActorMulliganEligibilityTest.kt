package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Fourteen fixed mechanical checks. Prescribed setup actions are not mulligan pilot decisions. */
class ActorMulliganEligibilityTest : FunSpec({
    val plain = card("Actor Setup Plain") { manaCost = "{1}"; typeLine = "Artifact" }
    val alternate = card("Actor Setup Alternate") { manaCost = "{2}"; typeLine = "Artifact" }
    val leyline = plain.copy(name = "Actor Setup Opening Choice", script = plain.script.copy(mayStartOnBattlefield = true))
    val registry = CardRegistry().also { r -> listOf(plain, alternate, leyline).forEach(r::register) }
    val processor = ActionProcessor(registry)
    val enumerator = LegalActionEnumerator(registry, ManaSolver(registry),
        CostCalculator(registry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(registry))
    val adapter = ObservationAdapter(registry)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }
    fun encoded(state: GameState) = json.encodeToString(GameState.serializer(), state)
    fun restored(state: GameState) = json.decodeFromString(GameState.serializer(), encoded(state))
    fun setup(start: Int, players: Int = 2, openingChoices: Boolean = false): GameState =
        GameInitializer(registry).initializeGame(GameConfig(
            players = (1..players).map { PlayerConfig("Setup seat $it",
                Deck(List(40) { if (openingChoices) leyline.name else plain.name })) },
            skipMulligans = false, startingPlayerIndex = start, seed = 2026092609L,
        )).state
    fun mulligan(state: GameState, actor: EntityId) = state.getEntity(actor)!!.require<MulliganStateComponent>()
    fun epoch(step: Long = 0) = ActorEpoch("canonical-mulligan-receiving-v1", "fixed-mechanical-input", step)
    fun input(state: GameState, actor: EntityId, step: Long = 0): ActorInput = adapter.build(
        state, actor, completeActorLegalActions(state, actor, enumerator), epoch(step), 991L,
    )
    fun apply(state: GameState, action: GameAction): ExecutionResult = processor.process(state, action).result
        .also { it.error shouldBe null }
    fun assertKeepMenu(state: GameState, actor: EntityId) {
        val view = input(state, actor)
        view.legalActions.map { it.action } shouldBe buildList {
            add(KeepHand(actor))
            if (mulligan(state, actor).canMulligan) add(TakeMulligan(actor))
        }
        view.legalActions.forEach { it.action.playerId shouldBe actor }
        view.observation.zones.filter { it.ownerId != actor && it.zoneType == Zone.HAND }
            .flatMap { it.cards }.size shouldBe 0
        view.verifyBinding(epoch(), actor)
    }

    for (start in 0..1) {
        test("MU01 real initializer admits each own setup menu with starting seat $start") {
            val state = setup(start)
            state.turnNumber shouldBe 1
            state.phase shouldBe Phase.BEGINNING
            state.step shouldBe Step.UNTAP
            actorsEligibleForInput(state) shouldBe state.turnOrder
            state.turnOrder.forEach { assertKeepMenu(state, it) }
            val outsider = EntityId.of("not-a-seat")
            shouldThrow<ObservationBoundaryException> {
                completeActorLegalActions(state, outsider, enumerator)
            }.failure shouldBe BoundaryFailure.WRONG_ACTOR
        }

        test("MU02 real KeepHand retains priority and exposes only the remaining unkept seat starting $start") {
            val before = setup(start)
            val first = before.turnOrder[0]
            val other = before.turnOrder[1]
            val stale = input(before, first)
            val after = apply(before, KeepHand(first)).state
            mulligan(after, first).hasKept shouldBe true
            after.priorityPlayerId shouldBe before.priorityPlayerId
            actorsEligibleForInput(after) shouldBe listOf(other)
            assertKeepMenu(after, other)
            shouldThrow<ObservationBoundaryException> { input(after, first) }.failure shouldBe BoundaryFailure.WRONG_ACTOR
            shouldThrow<ObservationBoundaryException> { stale.verifyBinding(epoch(1), first) }
                .failure shouldBe BoundaryFailure.STALE_INPUT
        }

        test("MU03 real redraw and own kept-bottom choice survive serialization starting $start") {
            val before = setup(start)
            val first = before.turnOrder[0]
            val redrawn = apply(before, TakeMulligan(first)).state
            mulligan(redrawn, first).mulligansTaken shouldBe 1
            redrawn.getHand(first).size shouldBe 7
            actorsEligibleForInput(redrawn) shouldBe before.turnOrder
            assertKeepMenu(redrawn, first)
            val kept = apply(redrawn, KeepHand(first)).state
            mulligan(kept, first).cardsToBottom shouldBe 1
            actorsEligibleForInput(kept) shouldBe before.turnOrder
            val view = input(kept, first)
            view.legalActions.map { it.action } shouldBe listOf(BottomCards(first, emptyList()))
            input(restored(kept), first).canonicalJson() shouldBe view.canonicalJson()
            actorsEligibleForInput(restored(kept)) shouldBe actorsEligibleForInput(kept)
        }

        test("MU04 prescribed final bottom returns to sole ordinary priority starting $start") {
            val before = setup(start)
            val first = before.turnOrder[0]
            val other = before.turnOrder[1]
            val redrawn = apply(before, TakeMulligan(first)).state
            val kept = apply(redrawn, KeepHand(first)).state
            val bothKept = apply(kept, KeepHand(other)).state
            actorsEligibleForInput(bothKept) shouldBe listOf(first)
            val bottom = BottomCards(first, listOf(bothKept.getHand(first).first()))
            val after = apply(bothKept, bottom)
            val replay = apply(restored(bothKept), bottom)
            encoded(replay.state) shouldBe encoded(after.state)
            replay.events shouldBe after.events
            after.state.getHand(first).size shouldBe 6
            mulligan(after.state, first).cardsToBottom shouldBe 0
            val priority = requireNotNull(after.state.priorityPlayerId)
            actorsEligibleForInput(after.state) shouldBe listOf(priority)
            input(after.state, priority).legalActions.any { it.action is KeepHand || it.action is BottomCards } shouldBe false
            shouldThrow<ObservationBoundaryException> {
                input(after.state, after.state.turnOrder.single { it != priority })
            }.failure shouldBe BoundaryFailure.WRONG_ACTOR
        }

        test("MU05 real serialized opening typed question takes precedence over setup and priority starting $start") {
            var state = setup(start, openingChoices = true)
            val order = state.turnOrder
            order.forEach { state = apply(state, KeepHand(it)).state }
            val question = state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            actorsEligibleForInput(state) shouldBe listOf(question.playerId)
            val view = input(state, question.playerId)
            view.decision shouldBe question
            view.legalActions shouldBe emptyList()
            input(restored(state), question.playerId).canonicalJson() shouldBe view.canonicalJson()
            shouldThrow<ObservationBoundaryException> {
                input(state, order.single { it != question.playerId })
            }.failure shouldBe BoundaryFailure.WRONG_ACTOR
            val no = SubmitDecision(question.playerId, YesNoResponse(question.id, false))
            val result = apply(state, no)
            val replay = apply(restored(state), no)
            encoded(replay.state) shouldBe encoded(result.state)
            replay.events shouldBe result.events
        }

        test("MU06 setup input ignores unavailable identities order and authoritative RNG starting $start") {
            val state = setup(start)
            for (actor in state.turnOrder) {
                var changed = state
                val source = state.getEntity(state.getHand(actor).first())!!.require<CardComponent>()
                    .copy(cardDefinitionId = alternate.name, name = alternate.name, manaCost = alternate.manaCost)
                val hidden = state.turnOrder.flatMap { owner ->
                    state.getLibrary(owner) + if (owner != actor) state.getHand(owner) else emptyList()
                }
                hidden.forEach { id -> changed = changed.updateEntity(id) { container ->
                    container.with(container.require<CardComponent>().copy(
                        cardDefinitionId = alternate.name, name = alternate.name, manaCost = alternate.manaCost,
                    ))
                } }
                state.turnOrder.forEach { owner -> changed = changed.copy(zones = changed.zones +
                    (ZoneKey(owner, Zone.LIBRARY) to changed.getLibrary(owner).reversed())) }
                changed = changed.copy(rng = GameRng.seeded(2026092610L))
                input(changed, actor).canonicalJson() shouldBe input(state, actor).canonicalJson()
                val ownChanged = state.updateEntity(state.getHand(actor).first()) { it.with(source) }
                input(ownChanged, actor).canonicalJson() shouldNotBe input(state, actor).canonicalJson()
            }
        }

        test("MU07 four-player free mulligan exposes every remaining seat without scheduling a pilot starting $start") {
            var state = setup(start, players = 4)
            val order = state.turnOrder
            actorsEligibleForInput(state) shouldBe order
            order.forEach { assertKeepMenu(state, it) }
            state = apply(state, TakeMulligan(order[0])).state
            mulligan(state, order[0]).mulligansTaken shouldBe 1
            mulligan(state, order[0]).cardsToBottom shouldBe 0
            state = apply(state, KeepHand(order[0])).state
            actorsEligibleForInput(state) shouldBe order.drop(1)
            order.drop(1).forEach { actor ->
                assertKeepMenu(state, actor)
                state = apply(state, KeepHand(actor)).state
            }
            order.forEach { state.getHand(it).size shouldBe 7 }
            actorsEligibleForInput(state) shouldBe listOf(requireNotNull(state.priorityPlayerId))
        }
    }
})
