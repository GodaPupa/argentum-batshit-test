package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import com.wingedsheep.gym.actorinput.ActorInputCodec
import io.kotest.assertions.throwables.shouldThrowAny

/** New excluded engine-backed reorder continuation qualification; accepted source remains unchanged. */
class SphinxDurablePreordainBoundaryTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val epoch = ActorEpoch("7839b78a100e0aa9d0a6c73ff7c85e90b9a253c6", "excluded-preordain-splits", 0)
    private val observation = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun name(state: GameState, id: EntityId) = state.getEntity(id)!!.get<CardComponent>()!!.name

    private inner class Seat(val actor: EntityId, val other: EntityId, val sourceId: EntityId,
                             val pilot: SphinxStageEInitializedSeat, var state: GameState) {
        var actorStep = 1L

        fun advance(action: GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }

        fun resolveToQuestion() {
            repeat(4) {
                if (state.pendingDecision == null) advance(PassPriority(requireNotNull(state.priorityPlayerId)))
            }
            requireNotNull(state.pendingDecision)
        }

        fun input(): ActorInput = observation.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch.copy(step = actorStep++), 0x5350_0006L)

        fun proposed(): SubmitDecision {
            val input = input()
            return pilot.decideVisibleChoice(input, input.epoch)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal.action
                .shouldBeInstanceOf<SubmitDecision>()
        }
    }

    private fun setup(identity: String, sourceName: String, landsOnBoard: Int): Seat {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val names = own.cards.flatMap { (name, count) -> List(count) { name } }
        val actor = EntityId.of("stage-e-continuation-actor")
        val other = EntityId.of("stage-e-continuation-opponent")
        val initial = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Actual frozen 60", Deck(names), playerId = actor),
                PlayerConfig("Passive excluded seat", Deck(List(60) { "Island" }), playerId = other)),
            startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
            startingPlayerIndex = 0, seed = 0x5350_4849_4E58_0007L,
        ))
        val all = (initial.state.getHand(actor) + initial.state.getLibrary(actor)).sortedBy { it.value }
        val source = all.first { name(initial.state, it) == sourceName }
        val islands = all.filter { name(initial.state, it) == "Island" }
        val hand = listOf(source) + islands.take(3) + all.filter {
            it != source && it !in islands.take(3) &&
                name(initial.state, it) !in setOf("Goliath Sphinx", "Counterspell", "Ponder")
        }.take(3)
        require(hand.size == 7 && hand.distinct().size == 7)
        val remainder = all.filter { it !in hand }
        val sphinx = remainder.filter { name(initial.state, it) == "Goliath Sphinx" }
        val otherCards = remainder.filter { it !in sphinx }
        val library = otherCards.take(4) + sphinx + otherCards.drop(4)
        val arranged = initial.copy(state = initial.state.copy(zones = initial.state.zones +
            (ZoneKey(actor, Zone.HAND) to hand) + (ZoneKey(actor, Zone.LIBRARY) to library)))
        val pilot = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)
        val seat = Seat(actor, other, source, pilot, arranged.state)
        seat.advance(KeepHand(actor))
        seat.advance(KeepHand(other))
        islands.take(landsOnBoard).forEach {
            seat.state = ZoneTransitionService.moveToZone(seat.state, it, Zone.BATTLEFIELD,
                ZoneEntryOptions(controllerId = actor)).state
        }
        seat.state = seat.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor)
        return seat
    }

    private fun pending(source: String = "Preordain"): Seat {
        val seat = setup("closest-no-approach-v01", source, 1)
        seat.advance(CastSpell(seat.actor, seat.sourceId)); seat.resolveToQuestion()
        return seat
    }
    private fun proposal(seat: Seat, input: ActorInput) =
        SphinxStageEWholeActor.decide(input, input.epoch, seat.pilot)
            .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal
    private fun boundary(seat: Seat, input: ActorInput) = SphinxStageEPreordainBoundary(
        seat.state, seat.pilot, input.epoch, input.policyRngState, cardRegistry)
    private fun split(bottomCount: Int, reverse: Boolean): Pair<Seat, List<EntityId>> {
        val seat = setup("closest-no-approach-v01", "Preordain", 5)
        val library = seat.state.getLibrary(seat.actor)
        val low = library.filter { name(seat.state, it) == "Island" }.take(bottomCount)
        low.size shouldBe bottomCount
        val high = library.first { name(seat.state, it) == "Counterspell" }
        val top = (if (bottomCount == 1) low + high else low).let { if (reverse) it.reversed() else it }
        val prefix = if (bottomCount == 2) top + high else top
        seat.state = seat.state.copy(zones = seat.state.zones +
            (ZoneKey(seat.actor, Zone.LIBRARY) to (prefix + library.filter { it !in prefix })))
        seat.advance(CastSpell(seat.actor, seat.sourceId)); seat.resolveToQuestion()
        seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        return seat to low
    }

    private fun sink() = Files.createTempDirectory("sphinx-excluded-consumption-").toRealPath()
    private fun execute(root: Path, seat: Seat, input: ActorInput,
                        p: com.wingedsheep.gym.actorinput.ActorProposal = proposal(seat, input)) =
        SphinxDurablePreordainBoundary.executeOnce(root, seat.state, seat.pilot, input.epoch,
            input.policyRngState, cardRegistry, input, p)
    private fun files(path: Path) = Files.list(path).use { it.toList().associate {
        p -> p.fileName.toString() to Files.readAllBytes(p).toList() } }

    init {
        test("accepted selection and singleton reorder each reserve and seal their exact step") {
            val (seat, _) = split(1, false); val root = sink()
            val first = seat.input(); val p = proposal(seat, first)
            val before = seat.state
            val result = execute(root, seat, first, p)
            result shouldBe actionProcessor.process(before, p.action).result
            val dir = SphinxDurablePreordainBoundary.slot(root, first.epoch)
            files(dir).keys shouldBe setOf("intent.json", "result.json", "complete.sha256")
            val original = files(dir)
            shouldThrowAny { execute(root, seat, first, p) }
            files(dir) shouldBe original
            seat.state = result.state
            val next = seat.input()
            next.decision.shouldBeInstanceOf<ReorderLibraryDecision>()
            seat.state = execute(root, seat, next).state
            seat.state.pendingDecision shouldBe null
            files(SphinxDurablePreordainBoundary.slot(root, next.epoch)).keys shouldBe
                setOf("intent.json", "result.json", "complete.sha256")
        }
        test("resealed wrong source consumes stable slot across newly constructed owners") {
            val (seat, _) = split(1, false); val root = sink(); val before = seat.state
            val input = seat.input(); val p = proposal(seat, input); val q = input.decision as SelectCardsDecision
            val bad = ActorInputCodec.seal(input.copy(decision = q.copy(
                context = q.context.copy(sourceId = EntityId.of("different-source")))))
            shouldThrowAny { SphinxDurablePreordainBoundary.executeOnce(root, seat.state, seat.pilot,
                input.epoch, input.policyRngState, cardRegistry, bad, p.copy(inputBindingHash = bad.bindingHash)) }
            val dir = SphinxDurablePreordainBoundary.slot(root, input.epoch)
            files(dir).keys shouldBe setOf("intent.json", "fault.txt")
            shouldThrowAny { execute(root, seat, input, p) }
            seat.state shouldBe before
        }
        test("stale epoch and changed proposal fail without returned state and forbid repair") {
            for (change in listOf("epoch", "proposal")) {
                val (seat, _) = split(2, false); val root = sink(); val before = seat.state
                val input = seat.input(); val p = proposal(seat, input)
                val bad = if (change == "epoch") ActorInputCodec.seal(input.copy(epoch = input.epoch.copy(step = 99))) else input
                val badP = if (change == "proposal") p.copy(nextPolicyRngState = p.nextPolicyRngState + 1) else p
                shouldThrowAny { SphinxDurablePreordainBoundary.executeOnce(root, seat.state, seat.pilot,
                    input.epoch, input.policyRngState, cardRegistry, bad, badP) }
                shouldThrowAny { execute(root, seat, input, p) }
                seat.state shouldBe before
            }
        }
        test("empty reserved slot is uncertainty not permission to initialize another owner") {
            val (seat, _) = split(2, false); val root = sink(); val input = seat.input()
            val dir = SphinxDurablePreordainBoundary.slot(root, input.epoch)
            Files.createDirectory(dir)
            shouldThrowAny { execute(root, seat, input) }
            files(dir).isEmpty() shouldBe true
        }
        test("missing completion and altered retained intent never reopen a consumed decision") {
            for (mode in listOf("completion", "intent")) {
                val (seat, _) = split(2, false); val root = sink(); val input = seat.input(); val p = proposal(seat, input)
                execute(root, seat, input, p)
                val dir = SphinxDurablePreordainBoundary.slot(root, input.epoch)
                if (mode == "completion") Files.delete(dir.resolve("complete.sha256"))
                else Files.writeString(dir.resolve("intent.json"), "tampered")
                val retained = files(dir)
                shouldThrowAny { execute(root, seat, input, p) }
                files(dir) shouldBe retained
            }
        }
        test("two concurrent owners in one namespace admit at most one real response") {
            val (seat, _) = split(2, true); val root = sink(); val input = seat.input(); val p = proposal(seat, input)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val ready = java.util.concurrent.CountDownLatch(1)
                val tasks = (1..2).map { pool.submit(java.util.concurrent.Callable {
                    ready.await()
                    runCatching { execute(root, seat, input, p) }.isSuccess
                }) }
                ready.countDown()
                tasks.count { it.get() } shouldBe 1
                files(SphinxDurablePreordainBoundary.slot(root, input.epoch)).keys shouldBe
                    setOf("intent.json", "result.json", "complete.sha256")
            } finally { pool.shutdownNow() }
        }
    }
}
