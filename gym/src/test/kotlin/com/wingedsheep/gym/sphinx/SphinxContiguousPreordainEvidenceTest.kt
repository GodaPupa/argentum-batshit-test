package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.*
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Excluded trusted-local engine fixture: one SELECT then immediate REORDER, no operational game. */
class SphinxContiguousPreordainEvidenceTest : ScenarioTestBase() {
    private val projectRoot = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val baseEpoch = ActorEpoch("469562617a242d8d1c66f4c2f2b12a603db47e24", "excluded-contiguous-preordain", 0)
    private val observation = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val codec = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }

    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun name(state: GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name
    private fun sink() = Files.createTempDirectory("sphinx-contiguous-excluded-").toRealPath()
    private fun files(root: Path): Map<String, List<Byte>> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.toList().associate {
            root.relativize(it).toString() to Files.readAllBytes(it).toList()
        }
    }

    private inner class Seat(
        val actor: EntityId, val other: EntityId, val sourceId: EntityId,
        val pilot: SphinxStageEInitializedSeat, var state: GameState,
    ) {
        var actorStep = 1L
        fun advance(action: GameAction) {
            val response = actionProcessor.process(state, action).result
            response.error shouldBe null
            state = response.state
        }
        fun resolveToQuestion() {
            repeat(4) {
                if (state.pendingDecision == null)
                    advance(PassPriority(requireNotNull(state.priorityPlayerId)))
            }
            requireNotNull(state.pendingDecision)
        }
        fun input(): ActorInput = observation.build(state, actor,
            completeActorLegalActions(state, actor, enumerator),
            baseEpoch.copy(step = actorStep++), 0x5350_0006L)
    }

    private fun seat(): Seat {
        val deckBytes = Files.readAllBytes(projectRoot.resolve("sphinx-approach/decks/closest-no-approach-v01.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(deckBytes)
        val deck = own.cards.flatMap { (n, count) -> List(count) { n } }
        val actor = EntityId.of("stage-e-contiguous-actor")
        val other = EntityId.of("stage-e-contiguous-opponent")
        val initial = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Excluded frozen 60", Deck(deck), playerId = actor),
                PlayerConfig("Excluded passive", Deck(List(60) { "Island" }), playerId = other)),
            startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
            startingPlayerIndex = 0, seed = 0x5350_4849_4E58_0007L))
        val all = (initial.state.getHand(actor) + initial.state.getLibrary(actor)).sortedBy { it.value }
        val spell = all.first { name(initial.state, it) == "Preordain" }
        val islands = all.filter { name(initial.state, it) == "Island" }
        val hand = listOf(spell) + islands.take(3) + all.filter {
            it != spell && it !in islands.take(3) &&
                name(initial.state, it) !in setOf("Goliath Sphinx", "Counterspell", "Ponder")
        }.take(3)
        require(hand.size == 7 && hand.distinct().size == 7)
        val remainder = all.filter { it !in hand }
        val sphinx = remainder.filter { name(initial.state, it) == "Goliath Sphinx" }
        val otherCards = remainder.filter { it !in sphinx }
        val library = otherCards.take(4) + sphinx + otherCards.drop(4)
        val arranged = initial.copy(state = initial.state.copy(zones = initial.state.zones +
            (ZoneKey(actor, Zone.HAND) to hand) + (ZoneKey(actor, Zone.LIBRARY) to library)))
        val pilot = SphinxStageEInitializedSeat.bindOpening(arranged, actor, deckBytes, baseEpoch)
        val seat = Seat(actor, other, spell, pilot, arranged.state)
        seat.advance(KeepHand(actor)); seat.advance(KeepHand(other))
        islands.take(5).forEach {
            seat.state = ZoneTransitionService.moveToZone(seat.state, it, Zone.BATTLEFIELD,
                ZoneEntryOptions(controllerId = actor)).state
        }
        seat.state = seat.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor)
        val l = seat.state.getLibrary(actor)
        val low = l.filter { name(seat.state, it) == "Island" }.take(1)
        val high = l.first { name(seat.state, it) == "Counterspell" }
        val prefix = low + high
        seat.state = seat.state.copy(zones = seat.state.zones +
            (ZoneKey(actor, Zone.LIBRARY) to (prefix + l.filter { it !in prefix })))
        seat.advance(CastSpell(actor, spell)); seat.resolveToQuestion()
        seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        return seat
    }

    private fun completed(seat: Seat, root: Path): Pair<SphinxContiguousPreordainRecord, ExecutionResult> {
        val input = seat.input()
        val proposed = SphinxStageEWholeActor.decide(input, input.epoch, seat.pilot)
            .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal
        val result = SphinxDurablePreordainBoundary.executeOnce(
            root, seat.state, seat.pilot, input.epoch, input.policyRngState,
            cardRegistry, input, proposed)
        val directory = SphinxDurablePreordainBoundary.slot(root, input.epoch)
        val original = SphinxPreordainOriginalMaterial(
            codec.encodeToString(GameState.serializer(), seat.state).toByteArray(),
            input.canonicalJson().toByteArray(),
            codec.encodeToString(ActorProposal.serializer(), proposed).toByteArray(),
            codec.encodeToString(ExecutionResult.serializer(), result).toByteArray())
        val pins = SphinxPreordainOriginalPins(
            hash(original.state), hash(original.input), hash(original.proposal),
            hash(original.response), hash(Files.readAllBytes(directory.resolve("intent.json"))),
            hash(Files.readAllBytes(directory.resolve("result.json"))), seat.pilot.ownDeckSha256)
        return SphinxContiguousPreordainRecord(input.epoch, directory, input.policyRngState,
            seat.pilot, original, pins) to result
    }

    private data class PairCase(
        val root: Path, val first: SphinxContiguousPreordainRecord,
        val second: SphinxContiguousPreordainRecord,
        val declared: SphinxContiguousPreordainDeclaration,
    )
    private fun pair(changedNextState: Boolean = false): PairCase {
        val seat = seat(); val root = sink()
        val (first, response) = completed(seat, root)
        response.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
        seat.state = if (changedNextState) response.state.copy(turnNumber = response.state.turnNumber + 1)
            else response.state
        val (second, _) = completed(seat, root)
        val declared = SphinxContiguousPreordainDeclaration(
            first.epoch.sourceVersion, first.epoch.trialId, seat.actor.value,
            seat.pilot.ownDeckSha256, first.epoch.step, second.epoch.step)
        return PairCase(root, first, second, declared)
    }

    private fun check(c: PairCase, expected: Boolean) {
        val before = files(c.root)
        val outcome = runCatching {
            SphinxContiguousPreordainEvidence.verify(c.root, c.first, c.second,
                c.declared, cardRegistry)
        }
        files(c.root) shouldBe before
        outcome.isSuccess shouldBe expected
    }

    init {
        test("two independently pinned completed Preordain decisions prove direct response-state handoff") {
            val c = pair()
            check(c, true)
            check(c, true)
            c.first.directory shouldBe SphinxDurablePreordainBoundary.slot(c.root, c.first.epoch)
            c.second.directory shouldBe SphinxDurablePreordainBoundary.slot(c.root, c.second.epoch)
            shouldThrowAny { SphinxDurablePreordainBoundary.executeOnce(c.root,
                codec.decodeFromString(GameState.serializer(), c.first.original.state.toString(Charsets.UTF_8)),
                c.first.pilot, c.first.epoch, c.first.policyRng, cardRegistry,
                codec.decodeFromString(ActorInput.serializer(), c.first.original.input.toString(Charsets.UTF_8)),
                codec.decodeFromString(ActorProposal.serializer(), c.first.original.proposal.toString(Charsets.UTF_8))) }
        }
        test("real individually completed but altered next prestate cannot be stitched together") {
            check(pair(changedNextState = true), false)
        }
        test("trial source actor own deck step and intermediate declarations cannot be inferred") {
            val c = pair()
            val d = c.declared
            for (bad in listOf(d.copy(trialId = "other-trial"),
                d.copy(sourceVersion = "other-source"), d.copy(actorId = "other-actor"),
                d.copy(ownDeckSha256 = "0".repeat(64)),
                d.copy(firstStep = d.firstStep + 1), d.copy(secondStep = d.secondStep + 1),
                d.copy(interveningActionsOrEvents = 1))) check(c.copy(declared = bad), false)
            check(c.copy(second = c.second.copy(epoch = c.second.epoch.copy(trialId = "foreign"))), false)
            check(c.copy(second = c.second.copy(directory = c.first.directory)), false)
            check(c.copy(second = c.first), false)
        }
        test("missing faulted partial or modified completed records and original pins refuse read only") {
            for (kind in listOf("missing", "fault", "extra", "changed", "firstPin", "secondPin")) {
                val c = pair()
                when (kind) {
                    "missing" -> Files.delete(c.second.directory.resolve("complete.sha256"))
                    "fault" -> Files.writeString(c.second.directory.resolve("fault.txt"), "EXCLUDED_FAULT\n")
                    "extra" -> Files.writeString(c.first.directory.resolve("extra.txt"), "EXCLUDED\n")
                    "changed" -> Files.writeString(c.first.directory.resolve("result.json"), "{}")
                }
                val bad = when (kind) {
                    "firstPin" -> c.copy(first = c.first.copy(pins = c.first.pins.copy(response = "0".repeat(64))))
                    "secondPin" -> c.copy(second = c.second.copy(pins = c.second.pins.copy(state = "0".repeat(64))))
                    else -> c
                }
                check(bad, false)
            }
        }
    }
}
