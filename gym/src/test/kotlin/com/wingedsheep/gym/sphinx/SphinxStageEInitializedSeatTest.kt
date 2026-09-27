package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.InitializationResult
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.ObservationBoundaryException
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Prospective mechanical bank: two checks x four frozen lists x two seats = 16 cases.
 * The other 60-Island seat is fixed construction data, not an opponent package. No policy is
 * rehearsed, no outcome is sought, and this fixture entropy cannot be an experimental seed.
 */
class SphinxStageEInitializedSeatTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val identities = listOf("reconstructed-v01", "reconstructed-hybrid",
        "closest-no-approach-v01", "serpico-terror-benchmark")
    private val lists = identities.associateWith {
        Files.readAllBytes(root.resolve("sphinx-approach/decks/$it.csv"))
    }
    private val epoch = ActorEpoch("stage-e-initial-seat-construction-v1", "fixed-initial-seat", 0)
    private val observation = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun initialize(bytes: ByteArray, ownSeat: Int, startingSeat: Int = ownSeat): InitializationResult {
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val ownNames = own.cards.flatMap { (name, count) -> List(count) { name } }
        val seats = (0..1).map { seat -> PlayerConfig(
            name = "Construction seat $seat",
            deck = Deck(if (seat == ownSeat) ownNames else List(60) { "Island" }),
            playerId = EntityId.of("stage-e-initial-seat-$seat"),
        ) }
        return GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = seats, startingHandSize = 7, skipMulligans = false,
            useHandSmoother = false, startingPlayerIndex = startingSeat,
            seed = 0x5350_4849_4E58_0001L,
        ))
    }

    private fun input(state: GameState, actor: EntityId, current: ActorEpoch): ActorInput =
        observation.build(state, actor, completeActorLegalActions(state, actor, enumerator),
            current, 0x5350_0001L)

    init {
        identities.forEach { identity ->
            (0..1).forEach { seat ->
                test("IB1 $identity seat$seat binds the real initialized 60 and rejects every other frozen list") {
                    val bytes = lists.getValue(identity)
                    val initialized = initialize(bytes, seat)
                    val before = initialized.state
                    val actor = initialized.playerIds[seat]
                    val bound = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)
                    bound.actorId shouldBe actor
                    bound.ownDeckSha256 shouldBe SphinxStageEOwnDeck.fromFrozenCsv(bytes).sha256
                    before.getHand(actor).size shouldBe 7
                    before.getLibrary(actor).size shouldBe 53
                    lists.filterKeys { it != identity }.values.forEach { wrongFrozenList ->
                        shouldThrow<IllegalArgumentException> {
                            SphinxStageEInitializedSeat.bindOpening(initialized, actor, wrongFrozenList, epoch)
                        }
                    }
                    shouldThrow<IllegalArgumentException> {
                        SphinxStageEInitializedSeat.bindOpening(
                            initialized, initialized.playerIds[1 - seat], bytes, epoch,
                        )
                    }
                    initialized.state shouldBe before
                }

                test("IB2 $identity seat$seat binds actor and trial across a real opening action without selecting it") {
                    val bytes = lists.getValue(identity)
                    val initialized = initialize(bytes, seat)
                    val actor = initialized.playerIds[seat]
                    val bound = SphinxStageEInitializedSeat.bindOpening(initialized, actor, bytes, epoch)
                    val opening = input(initialized.state, actor, epoch)
                    val keep = opening.legalActions.indexOfFirst { it.action == KeepHand(actor) }
                    require(keep >= 0)
                    bound.decideCurrentCast(opening, epoch, keep, SphinxStageEComponentCall.CURRENT_CAST)
                        .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
                    listOf(epoch.copy(sourceVersion = "different-source"), epoch.copy(trialId = "different-trial"))
                        .forEach { wrongEpoch ->
                            shouldThrow<IllegalArgumentException> {
                                bound.decideCurrentCast(opening, wrongEpoch, keep,
                                    SphinxStageEComponentCall.CURRENT_CAST)
                            }
                        }

                    // Explicit boundary action, independent of any pilot choice. Never advance to a game.
                    val kept = actionProcessor.process(initialized.state, KeepHand(actor)).result
                    kept.error shouldBe null
                    val other = initialized.playerIds[1 - seat]
                    kept.state.getEntity(actor)!!.get<MulliganStateComponent>()!!.hasKept shouldBe true
                    kept.state.getEntity(other)!!.get<MulliganStateComponent>()!!.hasKept shouldBe false
                    // Mulligans are simultaneous: keeping changes this seat's state, not priority.
                    kept.state.priorityPlayerId shouldBe initialized.state.priorityPlayerId
                    completeActorLegalActions(kept.state, other, enumerator)
                        .any { it.action == KeepHand(other) } shouldBe true
                    shouldThrow<IllegalArgumentException> {
                        SphinxStageEInitializedSeat.bindOpening(initialized.copy(state = kept.state),
                            actor, bytes, epoch)
                    }
                    val nextEpoch = epoch.copy(step = 1)
                    // A real opposite-start opening supplies an authentic other-actor payload.
                    // This tests this binding's rejection; it does not qualify simultaneous-mulligan
                    // routing, which the canonical observation adapter still must implement.
                    val oppositeStart = initialize(bytes, seat, startingSeat = 1 - seat)
                    val otherInput = input(oppositeStart.state, other, nextEpoch)
                    shouldThrow<ObservationBoundaryException> {
                        bound.decideCurrentCast(otherInput, nextEpoch, 0,
                            SphinxStageEComponentCall.CURRENT_CAST)
                    }
                    shouldThrow<ObservationBoundaryException> {
                        bound.decideCurrentCast(opening, nextEpoch, keep,
                            SphinxStageEComponentCall.CURRENT_CAST)
                    }
                    kept.state.gameOver shouldBe false
                    kept.state.winnerId shouldBe null
                }
            }
        }
    }
}
