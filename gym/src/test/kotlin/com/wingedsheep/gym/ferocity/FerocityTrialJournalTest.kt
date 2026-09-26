package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

/** Fixed persistence/protocol fixtures only. No matchup samples or policy qualification. */
class FerocityTrialJournalTest : ScenarioTestBase() {
    private val p1 = EntityId.of("player-1")
    private val p2 = EntityId.of("player-2")
    private val fixtureSeed = 2026092621L
    private val oneAction = FerocityTrialLimits(maxSubmittedActions = 1, maxCompletedPlayerTurns = 10, maxRuntimeMillis = 1000)
    private val passPilot = FerocityPilot { input ->
        ActorProposal(input.bindingHash, input.legalActions.first { it.action is PassPriority }.action, input.policyRngState)
    }
    private val passPilots = mapOf(p1 to passPilot, p2 to passPilot)
    private val activationFixture = card("Ferocity Journal Activation Fixture") {
        manaCost = "{1}"
        typeLine = "Creature — Construct"
        power = 1
        toughness = 1
        activatedAbility { cost = Costs.Tap; effect = Effects.GainLife(1) }
    }

    init {
        cardRegistry.register(activationFixture)
        test("complete hidden state and RNG survive exact serializer restoration") {
            val state = fixture().copy(rng = GameRng(Long.MIN_VALUE))
            val payload = FerocityJournalCodec.state(state)
            val restored = FerocityJournalCodec.state(payload)
            restored shouldBe state
            restored.getLibrary(p1) shouldBe state.getLibrary(p1)
            restored.getHand(p2) shouldBe state.getHand(p2)
            restored.rng.state shouldBe Long.MIN_VALUE
            FerocityJournalCodec.state(restored) shouldBe payload
        }

        test("state payload rejects changed bytes and unknown fields") {
            val payload = FerocityJournalCodec.state(fixture())
            shouldThrow<IllegalArgumentException> { FerocityJournalCodec.state(payload.copy(wireJson = payload.wireJson + " ")) }
            val objectValue = FerocityJournalCodec.json.parseToJsonElement(payload.wireJson).jsonObject
            val unknown = JsonObject(objectValue + ("unreviewedFutureField" to JsonPrimitive(true)))
            val rehashed = FerocityJournalCodec.payload(JsonElement.serializer(), unknown)
            shouldThrow<Exception> { FerocityJournalCodec.state(rehashed) }
        }

        test("one real priority action records full data and replays as unresolved cap") {
            val root = directory()
            val spec = spec("one-action")
            val summary = runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "Fixed main-phase pass") { start() }
            summary.reason shouldBe FerocityStopReason.CAP_ACTIONS
            summary.submittedActions shouldBe 1
            summary.winnerId shouldBe null
            val journal = readFerocityJournal(root, spec.namespace, spec.trialId)
            journal.records.map { it::class.simpleName } shouldBe listOf("FerocityHeader", "FerocityInitialized", "FerocityIntent", "FerocityResult", "FerocityEnd")
            val intent = journal.records.filterIsInstance<FerocityIntent>().single()
            val result = journal.records.filterIsInstance<FerocityResult>().single()
            intent.actorInput!!.verifyBinding(ActorEpoch(spec.pins.sourceCommit, spec.trialId, 0), p1)
            FerocityJournalCodec.action(intent.action) shouldBe PassPriority(p1)
            result.status shouldBe FerocitySubmissionStatus.APPLIED
            result.engineStepAfter shouldBe 1
            val beforeBytes = Files.readAllBytes(summary.journalPath).toList()
            val replay = replay(spec).verify(root, spec.namespace, spec.trialId)
            replay.status shouldBe FerocityReplayStatus.VERIFIED_UNRESOLVED
            replay.verifiedSubmissions shouldBe 1
            replay.newGameplayGames shouldBe 0
            Files.readAllBytes(summary.journalPath).toList() shouldBe beforeBytes
        }

        test("actual terminal submission at action cap is retained as terminal") {
            val root = directory()
            val spec = spec("terminal-at-cap")
            // Deliberate engine-concession fixture, never a competitive pilot policy.
            val concede = FerocityPilot { input -> ActorProposal(input.bindingHash, Concede(input.actorId), input.policyRngState) }
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, mapOf(p1 to concede, p2 to concede),
                "Fixed terminal-concession accounting fixture") { start() }
            result.reason shouldBe FerocityStopReason.TERMINAL_WIN
            result.winnerId shouldBe p2
            val report = replay(spec).verify(root, spec.namespace, spec.trialId)
            report.status shouldBe FerocityReplayStatus.VERIFIED_TERMINAL
            report.recordedWinner shouldBe p2
            report.newGameplayGames shouldBe 0
        }

        test("actual nonmana activation preserves deterministic resolution identity during replay") {
            val root = directory()
            val spec = spec("activation-routing")
            val activate = FerocityPilot { input ->
                val action = input.legalActions.single { it.action is ActivateAbility && !it.isManaAbility }.action
                ActorProposal(input.bindingHash, action, input.policyRngState)
            }
            val state = scenario().withPlayers().withRngSeed(fixtureSeed)
                .withCardOnBattlefield(1, activationFixture.name).build().state
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, mapOf(p1 to activate, p2 to activate),
                "Fixed activation routing-identity regression") { start(state) }
            result.reason shouldBe FerocityStopReason.CAP_ACTIONS
            // This is expected to expose the historical EntityId.generate() activation UUID.
            // Qualify with the independently reviewed newRoutingId repair; never omit identity fields.
            replay(spec).verify(root, spec.namespace, spec.trialId).verifiedSubmissions shouldBe 1
        }

        test("engine rejected action remains paid submission and is never retried") {
            val root = directory()
            val spec = spec("rejection")
            var calls = 0
            val invalid = FerocityPilot { input ->
                calls++
                val creature = input.observation.zones.flatMap { it.cards }.first { it.name == "Grizzly Bears" }
                ActorProposal(input.bindingHash, PlayLand(input.actorId, creature.entityId), input.policyRngState)
            }
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, mapOf(p1 to invalid, p2 to invalid),
                "Fixed illegal creature-as-land submission") { start() }
            result.reason shouldBe FerocityStopReason.ENGINE_REJECTION
            calls shouldBe 1
            result.winnerId shouldBe null
            val journal = readFerocityJournal(root, spec.namespace, spec.trialId)
            val intent = journal.records.filterIsInstance<FerocityIntent>().single()
            val response = journal.records.filterIsInstance<FerocityResult>().single()
            response.status shouldBe FerocitySubmissionStatus.REJECTED
            response.afterState shouldBe intent.beforeState
            response.engineStepAfter shouldBe 1
            (response.rejection != null) shouldBe true
            replay(spec).verify(root, spec.namespace, spec.trialId).status shouldBe FerocityReplayStatus.VERIFIED_UNRESOLVED
        }

        test("bad proposal binding is preserved with zero engine submissions") {
            val root = directory()
            val spec = spec("bad-binding")
            val invalid = FerocityPilot { input -> ActorProposal("stale-binding", PassPriority(input.actorId), 777) }
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, mapOf(p1 to invalid, p2 to invalid),
                "Fixed stale proposal fixture") { start() }
            result.reason shouldBe FerocityStopReason.INVALID_PROPOSAL
            result.submittedActions shouldBe 0
            val fault = readFerocityJournal(root, spec.namespace, spec.trialId).records.filterIsInstance<FerocityFault>().single()
            fault.proposal!!.inputBindingHash shouldBe "stale-binding"
            fault.proposal.nextPolicyRngState shouldBe 777L
            (fault.actorInput != null) shouldBe true
        }

        test("pilot exception remains an unresolved fault with its actor input") {
            val root = directory()
            val spec = spec("policy-failure")
            val broken = FerocityPilot { throw IllegalStateException("deliberate fixed policy failure") }
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, mapOf(p1 to broken, p2 to broken),
                "Fixed policy failure fixture") { start() }
            result.reason shouldBe FerocityStopReason.POLICY_FAILURE
            result.submittedActions shouldBe 0
            val fault = readFerocityJournal(root, spec.namespace, spec.trialId).records.filterIsInstance<FerocityFault>().single()
            fault.failure.message shouldBe "deliberate fixed policy failure"
            fault.actorInput!!.actorId shouldBe p1
            result.winnerId shouldBe null
        }

        test("claim is readable before initializer and failure consumes its identity") {
            val root = directory()
            val spec = spec("failed-initialization")
            var calls = 0
            val initialize = {
                calls++
                Files.exists(FerocityTrialJournal.claimPath(root, spec.namespace, spec.trialId)) shouldBe true
                val raw = readFerocityJournal(root, spec.namespace, spec.trialId)
                raw.records.single()::class shouldBe FerocityHeader::class
                throw IllegalStateException("deliberate setup failure")
            }
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "Fixed setup failure", initialize)
            result.reason shouldBe FerocityStopReason.INITIALIZATION_FAILURE
            result.winnerId shouldBe null
            shouldThrow<Exception> { runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "No retry", initialize) }
            calls shouldBe 1
            replay(spec).verify(root, spec.namespace, spec.trialId).status shouldBe FerocityReplayStatus.VERIFIED_INITIALIZATION_FAILURE
        }

        test("duplicate allocation alias preserves the failed alias claim without reroll") {
            val root = directory()
            val spec = spec("original-allocation")
            FerocityTrialJournal.claimNew(root, spec).close()
            val alias = spec.copy(trialId = "renamed-retry", gameSeed = spec.gameSeed + 1)
            shouldThrow<Exception> { FerocityTrialJournal.claimNew(root, alias) }
            Files.exists(FerocityTrialJournal.claimPath(root, alias.namespace, alias.trialId)) shouldBe true
            Files.exists(FerocityTrialJournal.journalPath(root, alias.namespace, alias.trialId)) shouldBe false
            // The successful original reservation remains readable and was not overwritten.
            readFerocityJournal(root, spec.namespace, spec.trialId).claim.spec shouldBe spec
        }

        test("durable INTENT is visible before actual submission and unmatched intent stays incomplete") {
            val root = directory()
            val spec = spec("intent-only")
            val state = fixture()
            FerocityTrialJournal.claimNew(root, spec).use { journal ->
                journal.append(FerocityHeader(journal.claimSha256, oneAction, "Fixed crash boundary", null))
                journal.append(FerocityInitialized(FerocityJournalCodec.state(state), FerocityJournalCodec.events(emptyList()), listOf(p1, p2), 0))
                journal.append(FerocityIntent(1, 0, FerocityJournalCodec.state(state), FerocityJournalCodec.action(PassPriority(p1)), null, null))
                val persisted = readFerocityJournal(root, spec.namespace, spec.trialId)
                persisted.interruptedIntent!!.submission shouldBe 1
                // A real call may have happened after this durable write; no RESULT was persisted.
                val env = GameEnvironment.create(pinnedRegistry(cardRegistry, spec.pins))
                env.restore(state, listOf(p1, p2))
                submitExactlyOneRecorded(env, PassPriority(p1), 1).status shouldBe FerocitySubmissionStatus.APPLIED
            }
            val bytes = Files.readAllBytes(FerocityTrialJournal.journalPath(root, spec.namespace, spec.trialId)).toList()
            val report = replay(spec).verify(root, spec.namespace, spec.trialId)
            report.status shouldBe FerocityReplayStatus.VERIFIED_INCOMPLETE_PREFIX
            report.verifiedSubmissions shouldBe 0
            report.unresolvedIntent shouldBe 1
            report.recordedWinner shouldBe null
            Files.readAllBytes(FerocityTrialJournal.journalPath(root, spec.namespace, spec.trialId)).toList() shouldBe bytes
        }

        test("claim-only interruption is not classified as a game") {
            val root = directory()
            val spec = spec("claim-only")
            FerocityTrialJournal.claimNew(root, spec).close()
            val result = replay(spec).verify(root, spec.namespace, spec.trialId)
            result.status shouldBe FerocityReplayStatus.VERIFIED_INCOMPLETE_PREFIX
            result.verifiedSubmissions shouldBe 0
            result.newGameplayGames shouldBe 0
        }

        test("truncated journal tail is rejected and preserved byte for byte") {
            val root = directory()
            val spec = spec("truncated")
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "Fixed truncation fixture") { start() }
            val damaged = Files.readAllBytes(result.journalPath).dropLast(1).toByteArray()
            Files.write(result.journalPath, damaged)
            shouldThrow<IllegalArgumentException> { readFerocityJournal(root, spec.namespace, spec.trialId) }
            Files.readAllBytes(result.journalPath).toList() shouldBe damaged.toList()
        }

        test("altered hash chain fails without replacement evidence") {
            val root = directory()
            val spec = spec("hash-tamper")
            val result = runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "Fixed chain fixture") { start() }
            val lines = Files.readAllLines(result.journalPath).toMutableList()
            val envelope = FerocityJournalCodec.readEnvelope(lines[2])
            lines[2] = FerocityJournalCodec.envelopeLine(envelope.copy(previousSha256 = "0".repeat(64)))
            val text = lines.joinToString("\n", postfix = "\n")
            Files.writeString(result.journalPath, text)
            shouldThrow<IllegalArgumentException> { replay(spec).verify(root, spec.namespace, spec.trialId) }
            Files.readString(result.journalPath) shouldBe text
        }

        test("replay rejects different runtime source or registry definition pins") {
            val root = directory()
            val spec = spec("different-pins")
            runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "Fixed pin fixture") { start() }
            val changed = spec.pins.copy(sourceTreeSha256 = "f".repeat(64))
            shouldThrow<IllegalArgumentException> { FerocityTrialReplay(cardRegistry, changed).verify(root, spec.namespace, spec.trialId) }
            val badCards = spec.pins.copy(cardDefinitionSha256 = spec.pins.cardDefinitionSha256 + ("Swamp" to "f".repeat(64)))
            shouldThrow<IllegalArgumentException> { pinnedRegistry(cardRegistry, badCards) }
        }

        test("completed turn and runtime caps never award a winner") {
            val root = directory()
            val turnSpec = spec("turn-cap")
            val turnResult = runner(turnSpec).runRestoredFixture(root, turnSpec, oneAction.copy(maxCompletedPlayerTurns = 2),
                passPilots, "Fixed turn-cap fixture") { start(fixture().copy(turnNumber = 3)) }
            turnResult.reason shouldBe FerocityStopReason.CAP_COMPLETED_TURNS
            turnResult.submittedActions shouldBe 0
            turnResult.winnerId shouldBe null
            val runtimeSpec = spec("runtime-cap")
            var tick = 0L
            val timed = FerocityTrialRunner(cardRegistry, runtimeSpec.pins) { (tick++).times(2_000_000L) }
            val runtimeResult = timed.runRestoredFixture(root, runtimeSpec, oneAction.copy(maxRuntimeMillis = 1), passPilots,
                "Fixed deterministic clock-cap fixture") { start() }
            runtimeResult.reason shouldBe FerocityStopReason.CAP_RUNTIME
            runtimeResult.submittedActions shouldBe 0
            runtimeResult.winnerId shouldBe null
        }

        test("real engine throw publishes no stale step events") {
            val env = GameEnvironment.create(cardRegistry)
            // Actual stepExactlyOne rejects the uninitialized environment by throwing before
            // an ExecutionResult exists. This qualifies the exception boundary, not a game.
            val result = submitExactlyOneRecorded(env, PassPriority(p1), 1)
            result.status shouldBe FerocitySubmissionStatus.THREW
            result.events shouldBe null
            result.failure!!.type shouldBe IllegalStateException::class.java.name
            result.engineStepAfter shouldBe 0
            FerocityJournalCodec.state(result.afterState) shouldBe GameState()
        }

        test("fixed GameConfig records every option and a real London mulligan replays") {
            val root = directory()
            val spec = spec("configured-mulligan")
            val config = GameConfig(
                players = listOf(PlayerConfig("Fixture One", Deck.of("Swamp" to 60), playerId = p1),
                    PlayerConfig("Fixture Two", Deck.of("Mountain" to 60), playerId = p2)),
                startingPlayerIndex = 0, skipMulligans = false, useHandSmoother = false, seed = spec.gameSeed,
            )
            val captured = FerocityRecordedGameConfig.capture(config)
            captured.toEngine() shouldBe config
            val mulligan = FerocityPilot { input ->
                input.legalActions.map { it.action::class }.toSet() shouldBe setOf(KeepHand::class, TakeMulligan::class)
                ActorProposal(input.bindingHash, input.legalActions.single { it.action is TakeMulligan }.action, input.policyRngState)
            }
            val result = runner(spec).runConfiguredFixture(root, spec, config, oneAction, mapOf(p1 to mulligan, p2 to mulligan))
            result.reason shouldBe FerocityStopReason.CAP_ACTIONS
            val journal = readFerocityJournal(root, spec.namespace, spec.trialId)
            val header = journal.records.first() as FerocityHeader
            FerocityJournalCodec.restore(FerocityRecordedGameConfig.serializer(), header.initializationConfig!!) shouldBe captured
            val initial = FerocityJournalCodec.state(journal.initialized!!.state)
            val after = FerocityJournalCodec.state(journal.records.filterIsInstance<FerocityResult>().single().afterState)
            after.rng shouldNotBe initial.rng
            replay(spec).verify(root, spec.namespace, spec.trialId).verifiedSubmissions shouldBe 1
        }

        test("research-stage attempts cannot consume allocations through the fixture entry") {
            val root = directory()
            val spec = spec("not-admitted").copy(stage = FerocityTrialStage.DEVELOPMENT)
            var called = false
            shouldThrow<IllegalArgumentException> {
                runner(spec).runRestoredFixture(root, spec, oneAction, passPilots, "Unadmitted research") { called = true; start() }
            }
            called shouldBe false
            Files.exists(FerocityTrialJournal.claimPath(root, spec.namespace, spec.trialId)) shouldBe false
        }

        test("policy capability accepts only actor data") {
            val choose = FerocityPilot::class.java.methods.single { it.name == "choose" }
            choose.parameterTypes.toList() shouldBe listOf(ActorInput::class.java)
            choose.returnType shouldBe ActorProposal::class.java
        }
    }

    private fun fixture(): GameState = scenario().withPlayers().withRngSeed(fixtureSeed)
        .withCardInHand(1, "Swamp").withCardInHand(1, "Grizzly Bears")
        .withCardInHand(2, "Mountain").withCardInLibrary(1, "Swamp")
        .withCardInLibrary(1, "Grizzly Bears").withCardInLibrary(2, "Mountain")
        .withCardInLibrary(2, "Swamp").build().state

    private fun start(state: GameState = fixture()) = FerocityFixtureStart(state, listOf(p1, p2))
    private fun directory(): Path = Files.createTempDirectory("ferocity-journal-fixed-")
    private fun runner(spec: FerocityTrialSpec) = FerocityTrialRunner(cardRegistry, spec.pins) { 0L }
    private fun replay(spec: FerocityTrialSpec) = FerocityTrialReplay(cardRegistry, spec.pins)
    private fun spec(id: String): FerocityTrialSpec {
        val digest = FerocityJournalCodec.sha("fixed-fixture-non-admission-receipt")
        return FerocityTrialSpec("ferocity-recycling/deterministic-fixtures/journal-v1", id, "allocation-$id",
            FerocityTrialStage.DETERMINISTIC_FIXTURE, fixtureSeed, mapOf(p1.value to 1001L, p2.value to 2002L),
            FerocitySourcePins("c771be5679cc1e9b25363c95ef50979961ac9f16", digest,
                mapOf("fixture-dependency-identity" to digest), mapOf("fixture-deck" to digest), mapOf("fixture-policy" to digest),
                listOf("Swamp", "Mountain", "Grizzly Bears", activationFixture.name).associateWith { FerocityJournalCodec.card(cardRegistry.requireCard(it)).canonicalSha256 },
                digest, digest, digest, digest))
    }
}
