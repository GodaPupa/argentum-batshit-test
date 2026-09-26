package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

/** Six fixed actor-boundary mechanics cases; no pilot or research game is run here. */
class FerocityTriggerOrderObservationTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("trigger-order-projection-v1", "fixed-trigger-order", 0)
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }

    init {
        // The harness appends TestCards to the corpus; fix all three source identities explicitly.
        cardRegistry.register(listOf(
            com.wingedsheep.mtg.sets.definitions.lea.cards.LightningBolt,
            com.wingedsheep.mtg.sets.definitions.rtr.cards.Guttersnipe,
            com.wingedsheep.mtg.sets.definitions.vow.cards.KessigFlamebreather,
        ))

        for (seat in listOf(1, 2)) {
            test("real Red simultaneous triggers retain the proved label and selected order seat $seat") {
                val game = redGroup(seat, 1, 202609261300L + seat)
                val actor = player(game, seat)
                val opponent = player(game, 3 - seat)
                val raw = game.state.pendingDecision as ChooseOptionDecision
                val projected = input(game.state)
                val question = projected.decision as ChooseOptionDecision
                question.context.sourceId shouldBe null
                question.context.sourceName shouldBe "Triggered abilities"
                question.context.abilityIdentity shouldBe null
                question.options shouldBe raw.options
                question.optionCardIds shouldBe raw.optionCardIds
                question.playerId shouldBe actor
                projected.legalActions shouldBe emptyList()
                projected.verifyBinding(epoch, actor)
                shouldThrow<ObservationBoundaryException> {
                    adapter.build(game.state, opponent, emptyList(), epoch, 1300)
                }.failure shouldBe BoundaryFailure.WRONG_ACTOR
                projected.canonicalJson().contains("TriggerOrderingContinuation") shouldBe false
                projected.canonicalJson().contains("objectReferences") shouldBe false

                val flamebreather = game.findPermanent("Kessig Flamebreather")!!
                val guttersnipe = game.findPermanent("Guttersnipe")!!
                val first = question.optionCardIds!!.entries.single { it.value == listOf(flamebreather) }.key
                game.submitDecision(OptionChosenResponse(question.id, first)).error shouldBe null
                game.state.pendingDecision shouldBe null
                game.state.stack.size shouldBe 3 // Bolt, smaller trigger, larger trigger.
                game.state.stack.drop(1).map {
                    game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!.sourceId
                } shouldBe listOf(flamebreather, guttersnipe)
                val events = settle(game)
                val damage = events.filterIsInstance<DamageDealtEvent>()
                damage.map { it.amount } shouldBe listOf(2, 1, 3)
                damage.take(2).map { it.sourceId } shouldBe listOf(guttersnipe, flamebreather)
                damage.all { it.targetId == opponent && !it.isCombatDamage } shouldBe true
                game.getLifeTotal(seat) shouldBe 20
                game.getLifeTotal(3 - seat) shouldBe 14
                game.state.gameOver shouldBe false
            }
        }

        test("successive real three-trigger choices preserve distinct same-name sources and ordering") {
            val game = redGroup(1, 2, 202609261303L)
            val initial = ordering(game.state)
            initial.chosen shouldBe emptyList()
            initial.remaining.size shouldBe 3
            val first = input(game.state).decision as ChooseOptionDecision
            first.options.count { "Guttersnipe" in it } shouldBe 2
            first.optionCardIds!!.values.flatten().distinct().size shouldBe 3
            game.submitDecision(OptionChosenResponse(first.id, 0)).error shouldBe null
            val second = input(game.state).decision as ChooseOptionDecision
            second.id shouldNotBe first.id
            second.context.sourceName shouldBe "Triggered abilities"
            second.options.size shouldBe 2
            val intermediate = ordering(game.state)
            intermediate.chosen shouldBe initial.remaining.take(1)
            intermediate.remaining shouldBe initial.remaining.drop(1)
            game.submitDecision(OptionChosenResponse(second.id, 0)).error shouldBe null
            game.state.pendingDecision shouldBe null
            game.state.stack.drop(1).map {
                game.state.getEntity(it)!!.get<TriggeredAbilityOnStackComponent>()!!.sourceId
            } shouldBe initial.remaining.map { it.sourceId }
            val events = settle(game)
            events.filterIsInstance<DamageDealtEvent>().sumOf { it.amount } shouldBe 8
            game.getLifeTotal(2) shouldBe 12
            game.state.gameOver shouldBe false
        }

        test("system-label lookalike without its typed continuation is rejected while unrelated names stay concealed") {
            val game = redGroup(1, 1, 202609261304L)
            val raw = game.state.pendingDecision as ChooseOptionDecision
            val spoof = replace(game.state, LegendRuleContinuation(game.player1Id, emptyList())) { it }
            shouldThrow<ObservationBoundaryException> { input(spoof) }.failure shouldBe BoundaryFailure.INCOMPLETE_INPUT
            val unrelated = replace(game.state, LegendRuleContinuation(game.player1Id, emptyList())) {
                it.copy(prompt = "Choose an option", context = DecisionContext(sourceName = "Private arbitrary label"),
                    options = listOf("One", "Two"), optionCardIds = null)
            }
            val hidden = input(unrelated)
            hidden.decision!!.context.sourceName shouldBe null
            hidden.canonicalJson().contains("Private arbitrary label") shouldBe false
            raw.context.sourceName shouldBe "Triggered abilities"
        }

        test("forged controller option preview private source and missing provenance fail without leaking labels") {
            val game = redGroup(1, 1, 202609261305L)
            val state = game.state
            val original = ordering(state)
            val first = original.remaining.first()
            val hiddenId = state.getHand(game.player2Id).first()
            val wrongOwner = replace(state, original.copy(remaining = original.remaining.map { it.copy(controllerId = game.player2Id) })) { it }
            val alteredOption = replace(state, original) { it.copy(options = it.options + "Secret injected option") }
            val alteredPreview = replace(state, original) { it.copy(optionCardIds = mapOf(0 to listOf(hiddenId))) }
            val wrongLabel = replace(state, original) { it.copy(context = it.context.copy(sourceName = "Secret injected label")) }
            val missingOrigin = replace(state, original.copy(remaining = listOf(first.copy(objectReferences = ObjectReferenceEnvironment())) + original.remaining.drop(1))) { it }
            val oldOrigin = requireNotNull(first.objectReferences.origin)
            val wrongOrigin = replace(state, original.copy(remaining = listOf(first.copy(
                objectReferences = first.objectReferences.copy(origin = ObjectRef(oldOrigin.entityId, oldOrigin.generation + 900)),
            )) + original.remaining.drop(1))) { it }
            val privateSource = replace(state, original.copy(remaining = listOf(first.copy(
                sourceId = hiddenId, sourceName = "Secret hidden card",
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = state.objectRef(hiddenId)),
            )) + original.remaining.drop(1))) { it }
            // The first-cell engine keeps PendingTrigger.sourceId nonnullable. A missing
            // source must fail while decoding authoritative input, before projection.
            val encodedTrigger = json.encodeToJsonElement(PendingTrigger.serializer(), first).jsonObject
            val noSource = JsonObject(encodedTrigger + ("sourceId" to JsonNull))
            shouldThrow<SerializationException> {
                json.decodeFromJsonElement(PendingTrigger.serializer(), noSource)
            }
            val sourceFaceDown = state.updateEntity(requireNotNull(first.sourceId)) { it.with(FaceDownComponent) }
            listOf(wrongOwner, alteredOption, alteredPreview, wrongLabel, missingOrigin, wrongOrigin,
                privateSource, sourceFaceDown).forEach { malformed ->
                val error = shouldThrow<ObservationBoundaryException> { input(malformed) }
                (error.failure in setOf(BoundaryFailure.INCOMPLETE_INPUT, BoundaryFailure.UNSUPPORTED_PUBLIC_MECHANIC)) shouldBe true
                error.message.orEmpty().contains("Secret") shouldBe false
            }
        }

        test("real ordering question roundtrips and ignores hidden cards library order and authoritative RNG") {
            val game = redGroup(1, 1, 202609261306L)
            val state = game.state
            val before = input(state).canonicalJson()
            val encoded = json.encodeToString(GameState.serializer(), state)
            val restored = json.decodeFromString(GameState.serializer(), encoded)
            json.encodeToString(GameState.serializer(), restored) shouldBe encoded
            input(restored).canonicalJson() shouldBe before
            var changed = state.copy(rng = GameRng(Long.MIN_VALUE))
            val opponentHand = state.getHand(game.player2Id).first()
            val otherPrivateCard = state.getEntity(state.getLibrary(game.player2Id).first())!!.get<CardComponent>()!!
            changed = changed.updateEntity(opponentHand) { it.with(otherPrivateCard) }
            for (owner in listOf(game.player1Id, game.player2Id)) {
                val ids = state.getLibrary(owner)
                val cards = ids.map { state.getEntity(it)!!.get<CardComponent>()!! }
                ids.forEachIndexed { index, id -> changed = changed.updateEntity(id) { it.with(cards[(index + 1) % cards.size]) } }
                changed = changed.copy(zones = changed.zones + (ZoneKey(owner, Zone.LIBRARY) to ids.reversed()))
            }
            input(changed).canonicalJson() shouldBe before
            input(changed).policyRngState shouldBe 1300L
            ordering(state).remaining.size shouldBe 2
        }
    }

    private fun redGroup(seat: Int, guttersnipes: Int, seed: Long): TestGame {
        val builder = scenario().withPlayers().withRngSeed(seed)
            .withCardOnBattlefield(seat, "Kessig Flamebreather")
            .withCardInHand(seat, "Lightning Bolt")
            .withLandsOnBattlefield(seat, "Mountain", 1)
            .withCardInHand(1, "Forest").withCardInHand(2, "Swamp")
            .withCardInLibrary(1, "Island").withCardInLibrary(1, "Forest").withCardInLibrary(1, "Plains")
            .withCardInLibrary(2, "Mountain").withCardInLibrary(2, "Island").withCardInLibrary(2, "Plains")
            .withActivePlayer(seat).withPriorityPlayer(seat)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(guttersnipes) { builder.withCardOnBattlefield(seat, "Guttersnipe") }
        val game = builder.build()
        val bolt = game.findCardsInHand(seat, "Lightning Bolt").single()
        game.execute(CastSpell(player(game, seat), bolt, listOf(ChosenTarget.Player(player(game, 3 - seat))))).error shouldBe null
        game.state.pendingDecision shouldNotBe null
        game.state.pendingDecision!!::class shouldBe ChooseOptionDecision::class
        ordering(game.state).remaining.size shouldBe guttersnipes + 1
        return game
    }

    private fun player(game: TestGame, seat: Int): EntityId = if (seat == 1) game.player1Id else game.player2Id

    private fun input(state: GameState): ActorInput {
        val actor = state.pendingDecision?.playerId ?: state.priorityPlayerId!!
        val menu = if (state.pendingDecision == null) enumerator.enumerate(state, actor) else emptyList()
        return adapter.build(state, actor, menu, epoch, 1300)
    }

    private fun ordering(state: GameState): TriggerOrderingContinuation =
        (state.continuationStack.last() as Suspension).answer as TriggerOrderingContinuation

    /** Explicit malformed fixtures are newly routed questions and are never submitted for play. */
    private fun replace(state: GameState, answer: AnswerContinuation, change: (ChooseOptionDecision) -> ChooseOptionDecision): GameState {
        val old = state.pendingDecision as ChooseOptionDecision
        return state.copy(continuationStack = state.continuationStack.dropLast(1))
            .suspendForDecision({ id -> change(old).copy(id = id) }, answer).state
    }

    private fun settle(game: TestGame): List<GameEvent> {
        val events = game.resolveStack().flatMap { result -> result.error shouldBe null; result.events }
        game.state.pendingDecision shouldBe null
        game.state.stack shouldBe emptyList()
        return events
    }
}
