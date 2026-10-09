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
import kotlinx.serialization.json.*
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.actorinput.ActorInputCodec
import io.kotest.assertions.throwables.shouldThrowAny

/** Excluded engine-backed completed-record correspondence; no operational opponent package. */
class SphinxCompletedPreordainVerifierTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val epoch = ActorEpoch("a2505953bbd822a0df1035e1e98b2ffa8e5f3a76", "excluded-preordain-splits", 0)
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

    private val codec = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
    private fun hash(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sink() = Files.createTempDirectory("sphinx-completed-record-").toRealPath()
    private data class Case(val root: Path, val directory: Path, val pilot: SphinxStageEInitializedSeat,
        val epoch: ActorEpoch, val rng: Long, val material: SphinxPreordainOriginalMaterial,
        val pins: SphinxPreordainOriginalPins)

    private fun completed(seat: Seat, root: Path = sink()): Pair<Case, ExecutionResult> {
        val input = seat.input(); val p = proposal(seat, input)
        val response = SphinxDurablePreordainBoundary.executeOnce(root, seat.state, seat.pilot,
            input.epoch, input.policyRngState, cardRegistry, input, p)
        val directory = SphinxDurablePreordainBoundary.slot(root, input.epoch)
        val material = SphinxPreordainOriginalMaterial(
            codec.encodeToString(GameState.serializer(), seat.state).toByteArray(), input.canonicalJson().toByteArray(),
            codec.encodeToString(ActorProposal.serializer(), p).toByteArray(),
            codec.encodeToString(ExecutionResult.serializer(), response).toByteArray())
        val pins = SphinxPreordainOriginalPins(hash(material.state), hash(material.input), hash(material.proposal),
            hash(material.response), hash(Files.readAllBytes(directory.resolve("intent.json"))),
            hash(Files.readAllBytes(directory.resolve("result.json"))), seat.pilot.ownDeckSha256)
        return Case(root, directory, seat.pilot, input.epoch, input.policyRngState, material, pins) to response
    }
    private fun files(root: Path): Map<String, List<Byte>> = Files.walk(root).use { stream ->
        stream.filter { Files.isRegularFile(it) }.toList().associate {
            root.relativize(it).toString() to Files.readAllBytes(it).toList()
        }
    }
    private fun check(c: Case, success: Boolean = true) {
        val before = files(c.root)
        val evidence = System.getenv("SPHINX_VERIFIER_EVIDENCE_ROOT")?.let { Files.createTempDirectory(Path.of(it), "invocation-") }
        if (evidence != null) {
            for ((name, bytes) in mapOf("state.json" to c.material.state, "input.json" to c.material.input,
                "proposal.json" to c.material.proposal, "response.json" to c.material.response)) Files.write(evidence.resolve(name), bytes)
            Files.writeString(evidence.resolve("bindings.json"), buildJsonObject {
                put("root", c.root.toString()); put("directory", c.directory.toString())
                put("epoch", codec.encodeToJsonElement(ActorEpoch.serializer(), c.epoch)); put("policyRng", c.rng)
                put("statePin", c.pins.state); put("inputPin", c.pins.input); put("proposalPin", c.pins.proposal)
                put("responsePin", c.pins.response); put("intentPin", c.pins.intent); put("resultPin", c.pins.result)
                put("ownDeckPin", c.pins.ownDeck); put("actor", c.pilot.actorId.value)
                put("expectedSuccess", success); put("executionAuthorized", false); put("authenticatedProvenance", false)
            }.toString())
            before.forEach { (name, bytes) ->
                val path = evidence.resolve("before").resolve(name)
                Files.createDirectories(path.parent); Files.write(path, bytes.toByteArray())
            }
        }
        val outcome = runCatching { SphinxCompletedPreordainVerifier.verify(c.root, c.directory, c.epoch,
            c.rng, c.pilot, cardRegistry, c.material, c.pins) }
        val after = files(c.root)
        if (evidence != null) {
            after.forEach { (name, bytes) ->
                val path = evidence.resolve("after").resolve(name)
                Files.createDirectories(path.parent); Files.write(path, bytes.toByteArray())
            }
            Files.writeString(evidence.resolve("outcome.json"), buildJsonObject {
                put("success", outcome.isSuccess); put("expectedSuccess", success)
                put("errorClass", outcome.exceptionOrNull()?.javaClass?.name)
                put("recordsUnchanged", before == after); put("executionAuthorized", false)
            }.toString())
        }
        after shouldBe before
        outcome.isSuccess shouldBe success
    }
    private fun rewrite(c: Case, material: SphinxPreordainOriginalMaterial = c.material,
                        intentChanges: Map<String, JsonElement> = emptyMap(),
                        resultChanges: Map<String, JsonElement> = emptyMap()): Case {
        // Adversarial resealing: update all local references so semantic rejection, not just a
        // stale file digest, is necessary. This does not model authenticated evidence custody.
        val intent = codec.parseToJsonElement(Files.readString(c.directory.resolve("intent.json"))).jsonObject
        val newIntent = JsonObject(intent + mapOf("stateSha256" to JsonPrimitive(hash(material.state)),
            "inputSha256" to JsonPrimitive(hash(material.input)), "proposalSha256" to JsonPrimitive(hash(material.proposal))) + intentChanges)
            .toString().toByteArray()
        val result = codec.parseToJsonElement(Files.readString(c.directory.resolve("result.json"))).jsonObject
        val newResult = JsonObject(result + mapOf("requestSha256" to JsonPrimitive(hash(newIntent)),
            "responseSha256" to JsonPrimitive(hash(material.response))) + resultChanges).toString().toByteArray()
        Files.write(c.directory.resolve("intent.json"), newIntent); Files.write(c.directory.resolve("result.json"), newResult)
        Files.writeString(c.directory.resolve("complete.sha256"), hash(newResult) + "\n")
        return c.copy(material = material, pins = c.pins.copy(state = hash(material.state), input = hash(material.input),
            proposal = hash(material.proposal), response = hash(material.response), intent = hash(newIntent), result = hash(newResult)))
    }

    init {
        test("selection and singleton reorder full original responses verify read-only without reopening") {
            val (seat, _) = split(1, false); val state = seat.state
            val (first, response) = completed(seat)
            check(first); check(first)
            seat.state shouldBe state
            seat.state = response.state
            seat.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
            val (second, next) = completed(seat, first.root)
            check(second)
            next.state.pendingDecision shouldBe null
            val before = files(first.root)
            shouldThrowAny { SphinxDurablePreordainBoundary.executeOnce(first.root, state, seat.pilot,
                first.epoch, first.rng, cardRegistry,
                codec.decodeFromString(ActorInput.serializer(), first.material.input.toString(Charsets.UTF_8)),
                codec.decodeFromString(ActorProposal.serializer(), first.material.proposal.toString(Charsets.UTF_8))) }
            files(first.root) shouldBe before
        }
        test("missing incomplete faulted and unexpected record layouts fail without repair or reservation") {
            for (mode in listOf("intent.json", "result.json", "complete.sha256", "fault", "extra", "empty", "absent")) {
                val (c, _) = completed(split(1, false).first)
                when (mode) {
                    "fault" -> Files.writeString(c.directory.resolve("fault.txt"), "EXCLUDED_FAULT\n")
                    "extra" -> Files.writeString(c.directory.resolve("extra.txt"), "x")
                    "empty", "absent" -> {
                        Files.list(c.directory).use { it.toList().forEach(Files::delete) }
                        if (mode == "absent") Files.delete(c.directory)
                    }
                    else -> Files.delete(c.directory.resolve(mode))
                }
                check(c, false)
                if (mode == "absent") Files.exists(c.directory) shouldBe false
            }
        }
        test("each independent original pin and missing semantic material fail reference-only certification") {
            val (c, _) = completed(split(1, false).first)
            for (pins in listOf(c.pins.copy(state = "0".repeat(64)), c.pins.copy(input = "0".repeat(64)),
                c.pins.copy(proposal = "0".repeat(64)), c.pins.copy(response = "0".repeat(64)),
                c.pins.copy(intent = "0".repeat(64)), c.pins.copy(result = "0".repeat(64)), c.pins.copy(ownDeck = "0".repeat(64))))
                check(c.copy(pins = pins), false)
            for (material in listOf(c.material.copy(state = byteArrayOf()), c.material.copy(input = byteArrayOf()),
                c.material.copy(proposal = byteArrayOf()), c.material.copy(response = byteArrayOf())))
                check(c.copy(material = material), false)
        }
        test("wrong namespace trial step source actor and policy RNG fail with original bytes unchanged") {
            val (c, _) = completed(split(1, false).first)
            for (bad in listOf(c.copy(root = sink()), c.copy(epoch = c.epoch.copy(step = c.epoch.step + 1)),
                c.copy(epoch = c.epoch.copy(trialId = "other-trial")), c.copy(epoch = c.epoch.copy(sourceVersion = "other-source")),
                c.copy(rng = c.rng + 1))) check(bad, false)
            val changed = rewrite(c, intentChanges = mapOf("actor" to JsonPrimitive("other-actor")))
            check(changed, false)
        }
        test("resealed source-question and proposal changes fail current trusted projection and WholeActor choice") {
            val (c, _) = completed(split(1, false).first)
            val input = codec.decodeFromString(ActorInput.serializer(), c.material.input.toString(Charsets.UTF_8))
            val p = codec.decodeFromString(ActorProposal.serializer(), c.material.proposal.toString(Charsets.UTF_8))
            val q = input.decision as SelectCardsDecision
            val badInput = ActorInputCodec.seal(input.copy(decision = q.copy(context = q.context.copy(sourceId = EntityId.of("wrong-source")))))
            val badProposal = p.copy(inputBindingHash = badInput.bindingHash)
            check(rewrite(c, c.material.copy(input = badInput.canonicalJson().toByteArray(),
                proposal = codec.encodeToString(ActorProposal.serializer(), badProposal).toByteArray())), false)
            check(rewrite(c, c.material.copy(proposal = codec.encodeToString(ActorProposal.serializer(),
                p.copy(nextPolicyRngState = p.nextPolicyRngState + 1)).toByteArray())), false)
        }
        test("resealed full response corruption fails even when stored response reference and completion agree") {
            val (c, _) = completed(split(1, false).first)
            val response = codec.decodeFromString(ExecutionResult.serializer(), c.material.response.toString(Charsets.UTF_8))
            val altered = codec.encodeToString(ExecutionResult.serializer(), response.copy(state = response.state.copy(turnNumber = 999))).toByteArray()
            check(rewrite(c, c.material.copy(response = altered)), false)
            val state = codec.decodeFromString(GameState.serializer(), c.material.state.toString(Charsets.UTF_8))
            val changedState = codec.encodeToString(GameState.serializer(), state.copy(turnNumber = 999)).toByteArray()
            check(rewrite(c, c.material.copy(state = changedState)), false)
        }
        test("resealed authority promotion extra fields classification and noncanonical records fail") {
            for ((key, value) in listOf("executionAuthorized" to JsonPrimitive(true),
                "authenticatedProvenance" to JsonPrimitive(true), "extra" to JsonPrimitive("x"))) {
                val (c, _) = completed(split(1, false).first)
                check(rewrite(c, intentChanges = mapOf(key to value)), false)
            }
            for ((key, value) in listOf("classification" to JsonPrimitive("REJECTED"), "executionAuthorized" to JsonPrimitive(true))) {
                val (c, _) = completed(split(1, false).first)
                check(rewrite(c, resultChanges = mapOf(key to value)), false)
            }
            val (c, _) = completed(split(1, false).first)
            check(rewrite(c, c.material.copy(input = " ".toByteArray() + c.material.input)), false)
        }
        test("truncated records wrong completion and symlink records fail without modifying source evidence") {
            for (mode in listOf("truncated", "completion", "symlink")) {
                val (c, _) = completed(split(1, false).first)
                val p = c.directory.resolve("result.json")
                when (mode) {
                    "truncated" -> Files.write(p, Files.readAllBytes(p).copyOf(10))
                    "completion" -> Files.writeString(c.directory.resolve("complete.sha256"), "0".repeat(64) + "\n")
                    else -> {
                        val saved = c.root.resolve("original-result.json"); Files.move(p, saved)
                        Files.createSymbolicLink(p, saved)
                    }
                }
                check(c, false)
            }
        }
    }
}
