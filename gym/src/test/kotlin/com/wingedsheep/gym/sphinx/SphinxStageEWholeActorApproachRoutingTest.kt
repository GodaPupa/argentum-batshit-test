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
 * Composition-only regression. The accepted Stage-E deployment policy already contains the
 * Sphinx's Approach timing/graveyard/unseen-Sphinx rule. This proves WholeActor routes one real
 * current Approach offer to that exact existing component without inventing a new ranking rule.
 */
class SphinxStageEWholeActorApproachRoutingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    init {
        test("whole actor routes the exact existing Sphinx's Approach deployment component") {
            val identity = "reconstructed-v01"
            val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
            val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
            val actor = EntityId.of("whole-approach-actor")
            val opponent = EntityId.of("whole-approach-opponent")
            val epoch0 = ActorEpoch("stage-e-whole-approach-routing-v1", "approach-route", 0)
            val initialized = GameInitializer(cardRegistry).initializeGame(
                GameConfig(
                    players = listOf(
                        PlayerConfig(
                            "Frozen Sphinx actor",
                            Deck(own.cards.flatMap { (card, count) -> List(count) { card } }),
                            playerId = actor,
                        ),
                        PlayerConfig(
                            "Excluded target seat",
                            Deck(List(60) { "Island" }),
                            playerId = opponent,
                        ),
                    ),
                    startingHandSize = 7,
                    skipMulligans = false,
                    useHandSmoother = false,
                    startingPlayerIndex = 0,
                    seed = 0x5350_4150_5052_0001L,
                )
            )
            val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor))
                .sortedBy { it.value }
            val approaches = all.filter { name(initialized.state, it) == "Sphinx's Approach" }
            val islands = all.filter { name(initialized.state, it) == "Island" }
            approaches.size shouldBe 18
            islands.size shouldBe 20
            val castApproach = approaches.first()
            val graveApproaches = approaches.drop(1).take(4)
            val handIslands = islands.take(6)
            val hand = listOf(castApproach) + handIslands
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
            handIslands.take(3).forEach {
                state = ZoneTransitionService.moveToZone(
                    state, it, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
                ).state
            }
            graveApproaches.forEach {
                state = ZoneTransitionService.moveToZone(
                    state, it, Zone.GRAVEYARD, ZoneEntryOptions(controllerId = actor)
                ).state
            }
            state = state.copy(
                phase = Phase.ENDING,
                step = Step.END,
                activePlayerId = opponent,
                priorityPlayerId = actor,
            )

            val epoch = epoch0.copy(step = 1)
            val input = adapter.build(
                state,
                actor,
                completeActorLegalActions(state, actor, enumerator),
                epoch,
                0x5350_4150_5052_0002L,
            )
            val index = input.legalActions.indexOfFirst {
                (it.action as? CastSpell)?.cardId == castApproach
            }
            require(index >= 0)

            val direct = seat.decideCurrentCast(
                input, epoch, index, SphinxStageEComponentCall.DEPLOYMENT
            ).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val whole = SphinxStageEWholeActor.decide(input, epoch, seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()

            whole.proposal shouldBe direct.proposal
            whole.reason shouldBe direct.reason
        }
    }
}
