package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.GrantedReplacementEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.giftEffect
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GiftKind
import com.wingedsheep.sdk.scripting.MultiplyTokenCreation
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Twelve fixed admission fixtures. Corrupted states/events are labeled, never matchup evidence. */
class FerocityInlineTokenProvenanceTest : ScenarioTestBase() {
    private val fixtureSeed = 202609260901L
    private val digest = FerocityJournalCodec.sha("inline-token-fixed-fixture-only-no-research-admission")
    private val sourceCommit = "53c68e958ae5ca9ab8b4e02613ea819134228ec2" // base identity; fixture pins do not admit a build
    private val slay = card("Ferocity Fish Boundary Slay Fixture") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { val creature = target("creature", Targets.Creature); effect = Effects.Destroy(creature) }
    }
    private val untap = card("Ferocity Fish Boundary Untap Fixture") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { val creature = target("creature", Targets.Creature); effect = Effects.Untap(creature) }
    }
    private val evidenceRoot by lazy {
        var repo = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(repo.resolve("settings.gradle.kts"))) repo = requireNotNull(repo.parent)
        val base = repo.resolve("ferocity-recycling/evidence/inline-token-provenance")
        Files.createDirectories(base)
        Files.createTempDirectory(base, "fixed-").also { println("FEROCITY_INLINE_TOKEN_FIXED_EVIDENCE=$it") }
    }
    private val classPath by lazy { currentFerocityClassPath().map(::captureFerocityRuntimePath) }
    private val javaExecutable by lazy { Path.of(System.getProperty("java.home"), "bin", "java").toRealPath() }

    private data class Setup(val game: TestGame, val admission: FerocityInlineTokenAdmission,
                             val pins: FerocitySourcePins, val bundle: FerocityDefinitionBundle)
    private data class Transition(val before: GameState, val action: GameAction, val after: GameState,
                                  val events: List<GameEvent>)
    private data class Creation(val setup: Setup, val transition: Transition, val fish: EntityId)

    init {
        cardRegistry.register(listOf(slay, untap))

        test("actual Brew gift creates one admitted tapped Fish with exact source and token object proofs") {
            val data = created()
            val tracker = tracker(data.setup)
            accept(tracker, data.transition)
            val proof = tracker.verifiedProofs.single()
            proof.createdObject shouldBe data.transition.after.objectRef(data.fish)
            proof.sourceObject shouldBe data.transition.before.objectRef(data.transition.before.stack.last())
            proof.casterId shouldBe data.setup.game.player1Id
            proof.recipientId shouldBe data.setup.game.player2Id
            proof.sourceDefinitionSha256 shouldBe data.setup.admission.sourceDefinitionSha256
            proof.descriptorSha256 shouldBe data.setup.admission.createTokenDescriptor.canonicalSha256
            proof.admissionSha256 shouldBe ferocityInlineTokenAdmissionSha256(data.setup.admission)
            proof.initialBaseCard.cardDefinitionId shouldBe "token:Fish"
            proof.initialBaseCard.baseStats shouldBe CreatureStats(1, 1)
            proof.initialBaseCard.colors shouldBe setOf(Color.BLUE)
            data.transition.after.getEntity(data.fish)!!.has<TappedComponent>() shouldBe true
            requireStateCardsPinned(data.transition.after, data.setup.pins, cardRegistry, tracker)
            cardRegistry.getCard("token:Fish") shouldBe null
        }

        test("actual nongift Brew creates no Fish or causal proof") {
            val data = setup()
            val tracker = tracker(data)
            tracked(data.game, tracker, brewAction(data.game, gift = false))
            finish(data.game, tracker)
            tracker.verifiedProofs shouldBe emptyList()
            fish(data.game.state) shouldBe emptyList()
            data.game.handSize(1) shouldBe 6 // Four unused fixture spells plus the two drawn cards.
        }

        test("real untap and continuous bonus preserve the same admitted token base identity") {
            val data = created()
            val game = data.setup.game
            val tracker = tracker(data.setup)
            accept(tracker, data.transition)
            val original = tracker.verifiedProofs.single()
            tracked(game, tracker, CastSpell(game.player1Id, game.findCardsInHand(1, untap.name).single(),
                listOf(ChosenTarget.Permanent(data.fish))))
            finish(game, tracker)
            game.state.getEntity(data.fish)!!.has<TappedComponent>() shouldBe false
            tracked(game, tracker, CastSpell(game.player1Id, game.findCardsInHand(1, "Giant Growth").single(),
                listOf(ChosenTarget.Permanent(data.fish))))
            finish(game, tracker)
            game.state.projectedState.getPower(data.fish) shouldBe 4
            game.state.projectedState.getToughness(data.fish) shouldBe 4
            tracker.requireState(game.state)
            tracker.verifiedProofs.single() shouldBe original
            game.state.getEntity(data.fish)!!.get<CardComponent>() shouldBe original.initialBaseCard
        }

        test("actual token death retires its incarnation and grants no new returned identity") {
            val data = created()
            val game = data.setup.game
            val tracker = tracker(data.setup)
            accept(tracker, data.transition)
            val proof = tracker.verifiedProofs.single()
            tracked(game, tracker, CastSpell(game.player1Id, game.findCardsInHand(1, slay.name).single(),
                listOf(ChosenTarget.Permanent(data.fish))))
            val events = finish(game, tracker)
            game.state.getEntity(data.fish) shouldBe null
            game.state.objectRef(data.fish) shouldBe null
            events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == data.fish && it.toZone == Zone.GRAVEYARD }
                .oldObject shouldBe proof.createdObject
            tracker.verifiedProofs shouldBe listOf(proof) // Immutable historical proof, not permission for a later object.
            tracker.requireState(game.state)
        }

        test("actual counter and whole-spell fizzle produce no Fish or imaginary gift proof") {
            val counter = setup()
            val counterTracker = tracker(counter)
            val brew = brewAction(counter.game)
            tracked(counter.game, counterTracker, brew)
            tracked(counter.game, counterTracker, PassPriority(counter.game.state.priorityPlayerId!!))
            tracked(counter.game, counterTracker, CastSpell(counter.game.player2Id,
                counter.game.findCardsInHand(2, "Counterspell").single(), listOf(ChosenTarget.Spell(brew.cardId))))
            finish(counter.game, counterTracker)
            counterTracker.verifiedProofs shouldBe emptyList()
            fish(counter.game.state) shouldBe emptyList()

            val fizzle = setup()
            val fizzleTracker = tracker(fizzle)
            tracked(fizzle.game, fizzleTracker, brewAction(fizzle.game))
            tracked(fizzle.game, fizzleTracker, CastSpell(fizzle.game.player1Id,
                fizzle.game.findCardsInHand(1, "Gilded Light").single()))
            tracked(fizzle.game, fizzleTracker, CastSpell(fizzle.game.player1Id,
                fizzle.game.findCardsInHand(1, slay.name).single(),
                listOf(ChosenTarget.Permanent(fizzle.game.findPermanent("Grizzly Bears")!!))))
            finish(fizzle.game, fizzleTracker)
            fizzleTracker.verifiedProofs shouldBe emptyList()
            fish(fizzle.game.state) shouldBe emptyList()
        }

        test("synthetic missing gift marker source recipient and prior stack-source corruption fail visibly") {
            val data = created(); val t = data.transition
            val source = t.before.stack.last()
            val gift = t.events.filterIsInstance<GiftGivenEvent>().single()
            val sourceIdentity = t.before.objectIdentities.getValue(source)
            val mismatches = listOf(
                t.copy(events = t.events.filterNot { it is GiftGivenEvent }),
                t.copy(events = t.events.map { if (it == gift) gift.copy(sourceId = data.fish) else it }),
                t.copy(events = t.events.map { if (it == gift) gift.copy(controllerId = data.setup.game.player2Id) else it }),
                t.copy(before = t.before.updateEntity(source) { entity ->
                    entity.with(entity.get<SpellOnStackComponent>()!!.copy(giftRecipient = null))
                }),
                t.copy(before = t.before.copy(stack = emptyList())),
                t.copy(before = t.before.copy(objectIdentities = t.before.objectIdentities +
                    (source to sourceIdentity.copy(generation = sourceIdentity.generation + 100)))),
            )
            mismatches.forEach { changed ->
                val tracker = tracker(data.setup)
                shouldThrow<IllegalArgumentException> { accept(tracker, changed) }
                tracker.verifiedProofs shouldBe emptyList()
            }
        }

        test("synthetic missing or wrong new object and reused token incarnation cannot inherit proof") {
            val data = created(); val t = data.transition
            val entry = t.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == data.fish && it.fromZone == null }
            val originalObject = requireNotNull(entry.newObject)
            listOf(entry.copy(newObject = null), entry.copy(newObject = originalObject.copy(generation = originalObject.generation + 100)))
                .forEach { bad -> shouldThrow<IllegalArgumentException> {
                    accept(tracker(data.setup), t.copy(events = t.events.map { if (it == entry) bad else it }))
                } }
            val tracker = tracker(data.setup); accept(tracker, t)
            val identity = t.after.objectIdentities.getValue(data.fish)
            val returned = t.after.copy(objectIdentities = t.after.objectIdentities +
                (data.fish to identity.copy(generation = identity.generation + 100)))
            shouldThrow<IllegalArgumentException> { tracker.requireState(returned) }
        }

        test("synthetic changed Fish base characteristics owner controller and entry markers all reject") {
            val data = created(); val t = data.transition
            val base = t.after.getEntity(data.fish)!!.get<CardComponent>()!!
            val badCards = listOf(base.copy(baseStats = CreatureStats(2, 1)), base.copy(colors = setOf(Color.RED)),
                base.copy(baseKeywords = setOf(Keyword.PROWESS)), base.copy(ownerId = data.setup.game.player1Id),
                base.copy(cardDefinitionId = "token:DifferentFish"), base.copy(name = "Different Fish"))
            val badStates = badCards.map { card -> t.after.updateEntity(data.fish) { it.with(card) } } + listOf(
                t.after.updateEntity(data.fish) { it.with(ControllerComponent(data.setup.game.player1Id)) },
                t.after.updateEntity(data.fish) { it.without<TappedComponent>() },
                t.after.updateEntity(data.fish) { it.without<TokenComponent>() },
            )
            badStates.forEach { changed -> shouldThrow<IllegalArgumentException> {
                accept(tracker(data.setup), t.copy(after = changed))
            } }
            val known = tracker(data.setup); accept(known, t)
            shouldThrow<IllegalArgumentException> {
                known.requireState(t.after.updateEntity(data.fish) { it.with(base.copy(cardDefinitionId = "Grizzly Bears")) })
            }
        }

        test("synthetic extra unknown or duplicate inline births fail without partially accepted proof") {
            val data = created(); val t = data.transition
            val entry = t.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == data.fish && it.fromZone == null }
            val unknown = EntityId.of("fixture-unknown-inline-token")
            val unknownBirth = entry.copy(entityId = unknown, entityName = "Unqualified Token", newObject = null)
            listOf(t.copy(events = t.events + entry), t.copy(events = t.events + unknownBirth)).forEach { changed ->
                val tracker = tracker(data.setup)
                shouldThrow<IllegalArgumentException> { accept(tracker, changed) }
                tracker.verifiedProofs shouldBe emptyList()
            }
        }

        test("unproved initial Fish fails before pilot input and a preserved post-step denial replays as invalid") {
            val data = created(); val state = data.transition.after
            shouldThrow<IllegalArgumentException> { tracker(data.setup).requireState(state) }
            val root = directory("initial-denial")
            val spec = spec(data.setup.pins, "initial-denial")
            var pilotCalls = 0
            val pilot = FerocityPilot { pilotCalls++; error("Unproved initial token reached pilot") }
            val summary = FerocityTrialRunner(cardRegistry, spec.pins, data.setup.admission) { 0L }
                .runRestoredFixture(root, spec, FerocityTrialLimits(3, 10, 1000),
                    mapOf(data.setup.game.player1Id to pilot, data.setup.game.player2Id to pilot), "Synthetic preloaded-token denial") {
                    FerocityFixtureStart(state, state.turnOrder)
                }
            summary.reason shouldBe FerocityStopReason.INITIALIZATION_FAILURE
            pilotCalls shouldBe 0

            // Default deny is a real attempted prefix: record the actual RESULT first, then fault.
            val denied = recordGift("post-step-denial", admissionEnabled = false)
            denied.summary.reason shouldBe FerocityStopReason.OBSERVATION_FAILURE
            val journal = readFerocityJournal(denied.root, denied.spec.namespace, denied.spec.trialId)
            journal.records.filterIsInstance<FerocityResult>().last().status shouldBe FerocitySubmissionStatus.APPLIED
            journal.records.filterIsInstance<FerocityFault>().single().phase shouldBe "post-step card admission"
            val before = Files.readAllBytes(denied.summary.journalPath).toList()
            val replay = FerocityTrialReplay(cardRegistry, denied.spec.pins).verify(denied.root, denied.spec.namespace, denied.spec.trialId)
            replay.status shouldBe FerocityReplayStatus.VERIFIED_UNRESOLVED
            replay.recordedEndReason shouldBe FerocityStopReason.OBSERVATION_FAILURE
            replay.recordedWinner shouldBe null
            Files.readAllBytes(denied.summary.journalPath).toList() shouldBe before
        }

        test("unadmitted descriptor source dynamic count and active replacement fail closed") {
            val data = created(); val admission = data.setup.admission
            val descriptor = giftEffect(GiftKind.TAPPED_FISH) as CreateTokenEffect
            val variants = listOf(
                admission.copy(recipeId = "future-token/v2"), admission.copy(schemaVersion = 2),
                admission.copy(sourceDefinitionLookup = "Grizzly Bears"),
                admission.copy(runtimeDependencySha256 = emptyMap()),
                admission.copy(createTokenDescriptor = FerocityJournalCodec.payload(Effect.serializer(), descriptor.copy(count = DynamicAmount.XValue))),
                admission.copy(createTokenDescriptor = FerocityJournalCodec.payload(Effect.serializer(), descriptor.copy(keywords = setOf(Keyword.PROWESS)))),
                admission.copy(createTokenDescriptor = FerocityJournalCodec.payload(Effect.serializer(), descriptor.copy(power = 2))),
            )
            variants.forEach { bad ->
                val repinned = data.setup.pins.copy(dependencySha256 = data.setup.pins.dependencySha256 +
                    (FEROCITY_INLINE_TOKEN_DEPENDENCY to ferocityInlineTokenAdmissionSha256(bad)))
                shouldThrow<IllegalArgumentException> { verifyFerocityInlineTokenAdmission(bad, repinned, cardRegistry) }
            }
            val t = data.transition
            val replacement = GrantedReplacementEffect(data.setup.game.player2Id, data.setup.game.player2Id,
                MultiplyTokenCreation(), Duration.EndOfTurn)
            val replaced = t.before.copy(grantedReplacementEffects = t.before.grantedReplacementEffects + replacement)
            shouldThrow<IllegalArgumentException> { accept(tracker(data.setup), t.copy(before = replaced)) }
        }

        test("fresh JVM reconstructs exact Fish provenance from real Brew prefix without allocating or rewriting") {
            val data = recordGift("fresh-gift", admissionEnabled = true, captureRuntime = true)
            data.summary.reason shouldBe FerocityStopReason.CAP_ACTIONS
            val original = Files.readAllBytes(data.summary.journalPath).toList()
            val replayer = FerocityTrialReplay(cardRegistry, data.spec.pins, data.setup.admission)
            replayer.verify(data.root, data.spec.namespace, data.spec.trialId).verifiedSubmissions shouldBe 3
            replayer.verifiedInlineTokenProofs.size shouldBe 1
            val expectedProofDigest = FerocityJournalCodec.sha(FerocityJournalCodec.canonical(
                ListSerializer(FerocityInlineTokenProof.serializer()), replayer.verifiedInlineTokenProofs))
            val request = FerocityFreshReplayRequest(1, data.bundlePath.toString(), data.bundleHash, data.spec.pins,
                classPath, javaExecutable.toString(), data.root.toString(), data.spec.namespace, data.spec.trialId,
                data.root.resolve("fresh-replay-receipt.json").toString(), data.setup.admission)
            val requestPath = data.root.resolve("fresh-replay-request.json")
            val requestHash = ferocityWriteNewJson(requestPath, FerocityFreshReplayRequest.serializer(), request)
            val command = listOf(javaExecutable.toString(), "-cp", classPath.joinToString(java.io.File.pathSeparator) { it.path },
                "com.wingedsheep.gym.ferocity.FerocityFreshReplayFixtureMain", requestPath.toString(), requestHash)
            ferocityWriteNew(data.root.resolve("child-command.json"), FerocityJournalCodec.canonical(
                ListSerializer(String.serializer()), command).toByteArray())
            val child = ProcessBuilder(command).directory(data.root.toFile()).redirectOutput(data.root.resolve("child.stdout").toFile())
                .redirectError(data.root.resolve("child.stderr").toFile()).start()
            if (!child.waitFor(30, TimeUnit.SECONDS)) {
                child.destroyForcibly()
                require(child.waitFor(5, TimeUnit.SECONDS)) { "Fish replay fixture child did not stop" }
                error("Fish replay child exceeded fixed30-second limit; all files retained at ${data.root}")
            }
            ferocityWriteNew(data.root.resolve("child-exit.txt"), child.exitValue().toString().toByteArray())
            child.exitValue() shouldBe 0
            val resultPath = Path.of(request.outputPath)
            val receipt = ferocityReadExactJson(resultPath, ferocityFileSha256(resultPath), FerocityFreshReplayReceipt.serializer())
            receipt.processId shouldNotBe ProcessHandle.current().pid()
            receipt.inlineTokenProofCount shouldBe 1
            receipt.inlineTokenProofsSha256 shouldBe expectedProofDigest
            receipt.inlineTokenAdmissionSha256 shouldBe ferocityInlineTokenAdmissionSha256(data.setup.admission)
            receipt.replayStatus shouldBe FerocityReplayStatus.VERIFIED_UNRESOLVED.name
            receipt.verifiedSubmissions shouldBe 3
            receipt.newGameplayGames shouldBe 0
            receipt.recordedWinner shouldBe null
            Files.readAllBytes(data.summary.journalPath).toList() shouldBe original
            Files.list(data.root.resolve("allocations")).use { it.count() } shouldBe 1L
        }
    }

    private fun setup(): Setup {
        val game = scenario().withPlayers().withRngSeed(fixtureSeed)
            .withCardInHand(1, "Sazacap's Brew").withCardInHand(1, "Forest")
            .withCardInHand(1, slay.name).withCardInHand(1, untap.name)
            .withCardInHand(1, "Giant Growth").withCardInHand(1, "Gilded Light")
            .withCardInHand(2, "Counterspell")
            .withLandsOnBattlefield(1, "Mountain", 2).withLandsOnBattlefield(1, "Plains", 2)
            .withLandsOnBattlefield(1, "Forest", 1).withLandsOnBattlefield(2, "Island", 2)
            .withCardOnBattlefield(1, "Grizzly Bears")
            .apply { repeat(8) { withCardInLibrary(1, "Mountain"); withCardInLibrary(2, "Island") } }
            .withActivePlayer(1).withPriorityPlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
        val cards = game.state.entities.values.mapNotNull { it.get<CardComponent>()?.cardDefinitionId }.distinct()
            .map(cardRegistry::requireCard).distinctBy { FerocityJournalCodec.card(it).canonicalSha256 }
        val bundle = captureFerocityDefinitions(cards.map { it to FerocityDefinitionOrigin(
            FerocityDefinitionOriginKind.DETERMINISTIC_FIXTURE, "ferocity/card-source/inline-fixed", digest) },
            sourceCommit, digest, digest)
        val admission = FerocityInlineTokenAdmission(recipeId = FEROCITY_GIFT_FISH_RECIPE,
            sourceDefinitionLookup = "Sazacap's Brew",
            sourceDefinitionSha256 = FerocityJournalCodec.card(cardRegistry.requireCard("Sazacap's Brew")).canonicalSha256,
            createTokenDescriptor = FerocityJournalCodec.payload(Effect.serializer(), giftEffect(GiftKind.TAPPED_FISH)),
            runtimeDependencySha256 = FEROCITY_INLINE_TOKEN_SOURCE_KEYS.associateWith { digest })
        val deps = mapOf("ferocity/card-source/inline-fixed" to digest, FEROCITY_BUNDLE_DEPENDENCY to digest,
            FEROCITY_INLINE_TOKEN_DEPENDENCY to ferocityInlineTokenAdmissionSha256(admission)) + admission.runtimeDependencySha256
        val pins = FerocitySourcePins(sourceCommit, digest, deps, mapOf("fixed-deck" to digest), mapOf("fixed-pilot" to digest),
            bundle.registryBindings, digest, digest, digest, digest)
        return Setup(game, admission, pins, bundle)
    }

    private fun brewAction(game: TestGame, gift: Boolean = true): CastSpell = CastSpell(game.player1Id,
        game.findCardsInHand(1, "Sazacap's Brew").single(),
        listOfNotNull(ChosenTarget.Player(game.player1Id), if (gift) ChosenTarget.Permanent(game.findPermanent("Grizzly Bears")!!) else null),
        giftRecipient = if (gift) game.player2Id else null,
        additionalCostPayment = AdditionalCostPayment(discardedCards = game.findCardsInHand(1, "Forest")))

    private fun created(): Creation {
        val data = setup(); val game = data.game
        game.execute(brewAction(game)).error shouldBe null
        var creation: Transition? = null
        var steps = 0
        while (game.state.stack.isNotEmpty() && game.state.pendingDecision == null && steps++ < 10) {
            val before = game.state; val action = PassPriority(before.priorityPlayerId!!)
            val result = game.execute(action); result.error shouldBe null
            if (result.events.any { it is GiftGivenEvent }) creation = Transition(before, action, game.state, result.events)
        }
        game.state.stack shouldBe emptyList(); game.state.pendingDecision shouldBe null
        val transition = requireNotNull(creation) { "Actual Brew did not produce the expected gift transition" }
        return Creation(data, transition, fish(game.state).single())
    }

    private fun tracker(data: Setup) = FerocityInlineTokenTracker(data.admission, data.pins, cardRegistry)
    private fun accept(tracker: FerocityInlineTokenTracker, t: Transition) = tracker.acceptApplied(t.before, t.action, t.after, t.events)
    private fun tracked(game: TestGame, tracker: FerocityInlineTokenTracker, action: GameAction): List<GameEvent> {
        val before = game.state; val result = game.execute(action)
        result.error shouldBe null
        tracker.acceptApplied(before, action, game.state, result.events)
        return result.events
    }
    private fun finish(game: TestGame, tracker: FerocityInlineTokenTracker): List<GameEvent> {
        val events = mutableListOf<GameEvent>(); var count = 0
        while (game.state.stack.isNotEmpty() && game.state.pendingDecision == null && count++ < 15) {
            events += tracked(game, tracker, PassPriority(game.state.priorityPlayerId!!))
        }
        game.state.stack shouldBe emptyList(); game.state.pendingDecision shouldBe null
        return events
    }
    private fun fish(state: GameState): List<EntityId> = state.getBattlefield().filter {
        state.getEntity(it)?.get<CardComponent>()?.cardDefinitionId == "token:Fish"
    }
    private fun directory(name: String): Path = evidenceRoot.resolve(name).also { Files.createDirectory(it) }
    private fun spec(pins: FerocitySourcePins, id: String) = FerocityTrialSpec(
        "ferocity-recycling/deterministic-fixtures/inline-token-v1", id, "fixed-$id", FerocityTrialStage.DETERMINISTIC_FIXTURE,
        fixtureSeed, mapOf("player-1" to 9101L, "player-2" to 9202L), pins)

    private data class RecordedGift(val root: Path, val setup: Setup, val spec: FerocityTrialSpec,
                                    val bundlePath: Path, val bundleHash: String, val summary: FerocityRunSummary)
    private fun recordGift(id: String, admissionEnabled: Boolean, captureRuntime: Boolean = false): RecordedGift {
        val data = setup(); val root = directory(id); val bundlePath = root.resolve("definitions.json")
        val bundleHash = writeFerocityBundle(bundlePath, data.bundle)
        val deps = data.pins.dependencySha256 + (FEROCITY_BUNDLE_DEPENDENCY to bundleHash) + if (captureRuntime) mapOf(
            FEROCITY_CLASSPATH_DEPENDENCY to ferocityClassPathDigest(classPath),
            FEROCITY_JAVA_DEPENDENCY to ferocityFileSha256(javaExecutable)) else emptyMap()
        val pins = data.pins.copy(dependencySha256 = deps); val spec = spec(pins, id)
        val pilot = FerocityPilot { input ->
            val gift = input.legalActions.singleOrNull { it.actionType == "CastWithGift" }
            val action = if (gift == null) input.legalActions.single { it.action is PassPriority }.action else {
                val template = gift.action as CastSpell
                val targets = requireNotNull(gift.targetRequirements)
                val ownCreature = targets[1].validTargets.single()
                val ownHand = input.observation.zones.flatMap { it.cards }
                val forest = ownHand.single { it.name == "Forest" && it.entityId in gift.additionalCostInfo!!.validDiscardTargets }.entityId
                template.copy(targets = listOf(ChosenTarget.Player(input.actorId), ChosenTarget.Permanent(ownCreature)),
                    additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(forest)))
            }
            ActorProposal(input.bindingHash, action, input.policyRngState)
        }
        val summary = FerocityTrialRunner(cardRegistry, pins, if (admissionEnabled) data.admission else null) { 0L }
            .runRestoredFixture(root, spec, FerocityTrialLimits(3, 10, 1000),
                mapOf(data.game.player1Id to pilot, data.game.player2Id to pilot), "Fixed actual Brew causal token replay prefix") {
                FerocityFixtureStart(data.game.state, data.game.state.turnOrder)
            }
        return RecordedGift(root, data, spec, bundlePath, bundleHash, summary)
    }
}
