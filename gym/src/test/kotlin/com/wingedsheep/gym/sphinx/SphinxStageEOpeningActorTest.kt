package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.InitializationResult
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.ObservationBoundaryException
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

/**
 * Prospective O1-O8 x four frozen 60s. These are excluded deterministic opening fixtures,
 * not gameplay samples or a complete Stage-E pilot. Do not execute before independent
 * source, construction, collector and workflow review.
 */
class SphinxStageEOpeningActorTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val identities = listOf("reconstructed-v01", "reconstructed-hybrid",
        "closest-no-approach-v01", "serpico-terror-benchmark")
    private val lists = identities.associateWith {
        Files.readAllBytes(root.resolve("sphinx-approach/decks/$it.csv"))
    }
    private val epoch = ActorEpoch("stage-e-opening-receiving-v1", "excluded-initialized-opening", 0)
    private val observation = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val router = SphinxStageEOpeningRouter(observation, enumerator)

    private fun initialize(bytes: ByteArray, ownSeat: Int = 0, startingSeat: Int = 0): InitializationResult {
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val names = own.cards.flatMap { (name, count) -> List(count) { name } }
        val seats = (0..1).map { seat -> PlayerConfig(
            name = "Excluded opening seat $seat",
            deck = Deck(if (seat == ownSeat) names else List(60) { "Island" }),
            playerId = EntityId.of("stage-e-opening-seat-$seat"),
        ) }
        return GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = seats, startingHandSize = 7, skipMulligans = false,
            useHandSmoother = false, startingPlayerIndex = startingSeat,
            seed = 0x5350_4849_4E58_0001L,
        ))
    }

    private fun route(state: GameState, order: List<EntityId>, step: Long): ActorInput =
        router.projectCurrent(state, order, epoch.copy(step = step), 0x5350_0002L)

    private fun direct(state: GameState, actor: EntityId, step: Long): ActorInput =
        observation.build(state, actor, completeActorLegalActions(state, actor, enumerator),
            epoch.copy(step = step), 0x5350_0002L)

    private fun advance(state: GameState, action: GameAction): GameState {
        val result = actionProcessor.process(state, action).result
        result.error shouldBe null
        return result.state
    }

    private fun cardName(state: GameState, id: EntityId): String =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    /**
     * Rearrangement is excluded deterministic construction AFTER an authentic 60-card
     * initialization. It preserves the exact entity multiset and every owner; no game
     * result or experimental seed is obtained from a constructed hand.
     */
    private fun openingWithLands(initialized: InitializationResult, actor: EntityId, count: Int): InitializationResult {
        val state = initialized.state
        val all = (state.getHand(actor) + state.getLibrary(actor)).sortedBy { it.value }
        val lands = all.filter { cardName(state, it) in setOf("Island", "Snow-Covered Island") }
        val spells = all.filter { it !in lands }
        val hand = lands.take(count) + spells.take(7 - count)
        require(hand.size == 7 && hand.distinct().size == 7)
        val library = all.filter { it !in hand }
        val zones = state.zones + (ZoneKey(actor, Zone.HAND) to hand) +
            (ZoneKey(actor, Zone.LIBRARY) to library)
        return initialized.copy(state = state.copy(zones = zones))
    }

    private fun proposed(result: SphinxStageEAdapterResult): com.wingedsheep.gym.actorinput.ActorProposal =
        result.shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal

    init {
        identities.forEach { identity ->
            val bytes = lists.getValue(identity)

            test("O1 $identity routes both initialized seat orientations without trusting priority") {
                (0..1).forEach { ownSeat ->
                    val initialized = initialize(bytes, ownSeat, startingSeat = ownSeat)
                    val order = initialized.state.turnOrder
                    val actor = initialized.playerIds[ownSeat]
                    val bound = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)
                    val first = route(initialized.state, order, 0)
                    first.actorId shouldBe order.first()
                    first.epoch shouldBe epoch
                    first.legalActions.any { it.action == KeepHand(first.actorId) } shouldBe true
                    first.legalActions.any { it.action == TakeMulligan(first.actorId) } shouldBe true
                    if (actor == first.actorId) {
                        val decision = bound.decideOpening(first, epoch)
                        proposed(decision).action.playerId shouldBe actor
                    }
                }
            }

            test("O2 $identity alternates real London rounds across both eligible seats") {
                val initialized = initialize(bytes)
                val order = initialized.state.turnOrder
                var state = initialized.state
                route(state, order, 0).actorId shouldBe order[0]
                state = advance(state, TakeMulligan(order[0]))
                route(state, order, 1).actorId shouldBe order[1]
                state = advance(state, TakeMulligan(order[1]))
                route(state, order, 2).actorId shouldBe order[0]
                state.gameOver shouldBe false
            }

            test("O3 $identity defers bottoming until keeps and then uses frozen seat order") {
                val initialized = initialize(bytes)
                val order = initialized.state.turnOrder
                var state = advance(initialized.state, TakeMulligan(order[0]))
                state = advance(state, KeepHand(order[1]))
                route(state, order, 2).actorId shouldBe order[0]
                state = advance(state, KeepHand(order[0]))
                route(state, order, 3).actorId shouldBe order[0]
                state = advance(state, BottomCards(order[0], state.getHand(order[0]).take(1)))
                route(state, order, 4).actorId shouldBe state.priorityPlayerId

                var both = initialized.state
                both = advance(both, TakeMulligan(order[0]))
                both = advance(both, TakeMulligan(order[1]))
                both = advance(both, KeepHand(order[0]))
                both = advance(both, KeepHand(order[1]))
                route(both, order, 4).actorId shouldBe order[0]
                both = advance(both, BottomCards(order[0], both.getHand(order[0]).take(1)))
                route(both, order, 5).actorId shouldBe order[1]
            }

            test("O4 $identity uses the same current seven-card land predicate and forced keep") {
                val original = initialize(bytes)
                val actor = original.state.turnOrder.first()
                for ((lands, expected) in listOf(0 to TakeMulligan::class, 2 to KeepHand::class)) {
                    val arranged = openingWithLands(original, actor, lands)
                    val bound = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)
                    val input = route(arranged.state, arranged.state.turnOrder, 0)
                    val action = proposed(bound.decideOpening(input, epoch)).action
                    action::class shouldBe expected
                    action.playerId shouldBe actor
                }
                val arranged = openingWithLands(original, actor, 0)
                val cap = arranged.state.updateEntity(actor) { entity ->
                    entity.with(entity.get<MulliganStateComponent>()!!.copy(mulligansTaken = 7))
                }
                val bound = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)
                val forced = direct(cap, actor, 1)
                proposed(bound.decideOpening(forced, epoch.copy(step = 1))).action shouldBe KeepHand(actor)
            }

            test("O5 $identity binds bottom cards to current own-hand IDs and real execution") {
                val original = initialize(bytes)
                val actor = original.state.turnOrder.first()
                val bound = SphinxStageEInitializedSeat.bindOpening(original, actor, bytes, epoch)
                var state = advance(original.state, TakeMulligan(actor))
                state = advance(state, KeepHand(actor))
                val arranged = openingWithLands(original.copy(state = state), actor, 4).state
                val input = direct(arranged, actor, 2)
                val proposal = proposed(bound.decideOpening(input, epoch.copy(step = 2)))
                val bottom = proposal.action.shouldBeInstanceOf<BottomCards>()
                proposal.inputBindingHash shouldBe input.bindingHash
                proposal.nextPolicyRngState shouldBe input.policyRngState
                bottom.playerId shouldBe actor
                bottom.cardIds.size shouldBe 1
                bottom.cardIds.all { it in arranged.getHand(actor) } shouldBe true
                cardName(arranged, bottom.cardIds.single()) shouldBe "Island"
                advance(arranged, bottom).getHand(actor).size shouldBe 6
            }

            test("O6 $identity routes first ordinary priority and executes offered basic-Island land") {
                val initialized = initialize(bytes)
                val order = initialized.state.turnOrder
                val actor = order.first()
                val bound = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)
                var state = advance(initialized.state, KeepHand(order[0]))
                state = advance(state, KeepHand(order[1]))
                val firstPriority = route(state, order, 2)
                firstPriority.actorId shouldBe state.priorityPlayerId
                state = openingWithLands(initialized.copy(state = state), actor, 2).state.copy(
                    phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
                    priorityPlayerId = actor, activePlayerId = actor,
                )
                val main = route(state, order, 3)
                main.actorId shouldBe actor
                main.legalActions.any { it.action is PlayLand && it.affordable } shouldBe true
                val land = proposed(bound.decideOpening(main, epoch.copy(step = 3))).action
                    .shouldBeInstanceOf<PlayLand>()
                land.playerId shouldBe actor
                cardName(state, land.cardId) in setOf("Island", "Snow-Covered Island") shouldBe true
                advance(state, land).getBattlefield(actor).contains(land.cardId) shouldBe true
            }

            test("O7 $identity rejects wrong seat, stale step and wrong trial before proposal") {
                val initialized = initialize(bytes)
                val actor = initialized.state.turnOrder.first()
                val other = initialized.state.turnOrder.last()
                val bound = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)
                val input = route(initialized.state, initialized.state.turnOrder, 0)
                shouldThrow<ObservationBoundaryException> {
                    bound.decideOpening(input, epoch.copy(step = 1))
                }
                shouldThrow<IllegalArgumentException> {
                    bound.decideOpening(input, epoch.copy(trialId = "other"))
                }
                val opposite = direct(initialized.state, other, 0)
                shouldThrow<ObservationBoundaryException> {
                    bound.decideOpening(opposite, epoch)
                }
            }

            test("O8 $identity ignores unseen library order and opponent hidden hand identity") {
                val initialized = initialize(bytes)
                val state = initialized.state
                val order = state.turnOrder
                val actor = order.first()
                val other = order.last()
                val bound = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)
                var changed = state
                listOf(actor, other).forEach { player ->
                    changed = changed.copy(zones = changed.zones +
                        (ZoneKey(player, Zone.LIBRARY) to state.getLibrary(player).reversed()))
                }
                val hidden = state.getHand(other).first()
                changed = changed.updateEntity(hidden) { entity ->
                    entity.with(entity.get<CardComponent>()!!.copy(
                        cardDefinitionId = "Grizzly Bears", name = "Grizzly Bears"))
                }
                val before = route(state, order, 0)
                val after = route(changed, order, 0)
                after.canonicalJson() shouldBe before.canonicalJson()
                bound.decideOpening(after, epoch) shouldBe bound.decideOpening(before, epoch)
                before.observation.zones.single {
                    it.ownerId == actor && it.zoneType == Zone.LIBRARY
                }.cards shouldBe emptyList()
            }
        }
    }
}
