package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Composition-only regression. The separately received Snap component is unchanged; this proves
 * WholeActor routes an already-qualified unique-target Snap offer to exactly that component.
 */
class SphinxStageEWholeActorSnapRoutingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_SNAP_RECEIVING_BUDGET_20260928.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    init {
        test("whole actor routes the exact existing Snap component without changing its proposal") {
            val identity = "reconstructed-v01"
            val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
            val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
            val actor = EntityId.of("whole-snap-actor")
            val opponent = EntityId.of("whole-snap-opponent")
            val epoch0 = ActorEpoch("stage-e-whole-snap-routing-v1", "snap-route", 0)
            val initialized = GameInitializer(cardRegistry).initializeGame(
                GameConfig(
                    players = listOf(
                        PlayerConfig("Frozen Sphinx actor",
                            Deck(own.cards.flatMap { (card, count) -> List(count) { card } }),
                            playerId = actor),
                        PlayerConfig("Excluded target seat",
                            Deck(List(59) { "Island" } + "Grizzly Bears"),
                            playerId = opponent),
                    ),
                    startingHandSize = 7,
                    skipMulligans = false,
                    useHandSmoother = false,
                    startingPlayerIndex = 0,
                    seed = 0x5350_5748_4F4C_0001L,
                )
            )
            val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor))
                .sortedBy { it.value }
            val snap = all.first { name(initialized.state, it) == "Snap" }
            val islands = all.filter { name(initialized.state, it) == "Island" }.take(2)
            val fillers = all.filter { it != snap && it !in islands && name(initialized.state, it) != "Island" }.take(4)
            fillers.size shouldBe 4
            val hand = listOf(snap) + islands + fillers
            val arranged = initialized.copy(
                state = initialized.state.copy(
                    zones = initialized.state.zones +
                        (ZoneKey(actor, Zone.HAND) to hand) +
                        (ZoneKey(actor, Zone.LIBRARY) to all.filter { it !in hand })
                )
            )
            val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch0)

            var state = arranged.state
            fun advance(action: GameAction) {
                val result = actionProcessor.process(state, action).result
                result.error shouldBe null
                state = result.state
            }
            advance(KeepHand(actor))
            advance(KeepHand(opponent))
            islands.forEach {
                state = ZoneTransitionService.moveToZone(
                    state, it, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
                ).state
            }
            val bear = (state.getHand(opponent) + state.getLibrary(opponent))
                .first { name(state, it) == "Grizzly Bears" }
            state = ZoneTransitionService.moveToZone(
                state, bear, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = opponent)
            ).state.copy(
                phase = Phase.ENDING,
                step = Step.END,
                activePlayerId = opponent,
                priorityPlayerId = actor,
            )

            val epoch = epoch0.copy(step = 1)
            val input = adapter.build(
                state, actor, completeActorLegalActions(state, actor, enumerator),
                epoch, 0x5350_5748_4F4C_0002L,
            )
            val index = input.legalActions.indexOfFirst { (it.action as? CastSpell)?.cardId == snap }
            require(index >= 0)

            val direct = seat.decideCurrentCast(input, epoch, index, SphinxStageEComponentCall.SNAP)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val whole = SphinxStageEWholeActor.decide(input, epoch, seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()

            whole.proposal shouldBe direct.proposal
            whole.reason shouldBe direct.reason
        }
    }
}
