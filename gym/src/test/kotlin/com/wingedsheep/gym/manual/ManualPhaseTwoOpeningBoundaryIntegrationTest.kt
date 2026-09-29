package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

/**
 * Excluded real-engine integration for the already-reviewed unique-land opening seam.
 * This is not a complete Cruise/Sport/Race/opponent pilot and consumes no official allocation.
 */
class ManualPhaseTwoOpeningBoundaryIntegrationTest : ScenarioTestBase() {
    init {
        ManualPhaseTwoPilotRole.entries.forEachIndexed { roleIndex, role ->
            test("$role real masked boundary plays the unique physical opening land and replays") {
                val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                    players = (0..3).map { seat ->
                        PlayerConfig(
                            "EXCLUDED_OPENING_$seat",
                            Deck(listOf("Forest") + List(98) { "Counterspell" }),
                            commanderCardNames = listOf("Animar, Soul of Elements"),
                        )
                    },
                    format = Format.Commander(),
                    startingPlayerIndex = 0,
                    skipMulligans = false,
                    useHandSmoother = false,
                    seed = 0x4d544f50454e0000L + roleIndex,
                ))
                val seats = initialized.playerIds
                var opening = initialized.state

                // Trusted excluded construction: exactly one physical Forest is visible in each own hand.
                seats.forEach { player ->
                    val all = opening.getHand(player) + opening.getLibrary(player)
                    fun named(id: EntityId) = opening.requireEntity(id).get<CardComponent>()!!.name
                    val forest = all.single { named(it) == "Forest" }
                    val hand = listOf(forest) + all.filter { named(it) == "Counterspell" }.take(6)
                    hand.size shouldBe 7
                    opening = opening.copy(zones = opening.zones +
                        (ZoneKey(player, Zone.HAND) to hand) +
                        (ZoneKey(player, Zone.LIBRARY) to all.filter { it !in hand }))
                }

                val adapter = PhaseTwoTelemetryAdapter(
                    opening, actionProcessor, "opening-boundary-integration-v1", seats, roleIndex
                )
                fun act(action: GameAction) { adapter.process(action).error shouldBe null }
                seats.forEach { act(KeepHand(it)) }

                var reached = false
                var guard = 0
                while (!reached && guard++ < 64) {
                    val state = adapter.state
                    reached = state.phase == Phase.PRECOMBAT_MAIN &&
                        state.activePlayerId == seats[0] &&
                        state.priorityPlayerId == seats[0] &&
                        state.pendingDecision == null &&
                        state.stack.isEmpty()
                    if (!reached) act(PassPriority(requireNotNull(state.priorityPlayerId)))
                }
                reached shouldBe true

                val policies = seats.associateWith { seat ->
                    if (seat == seats[0]) ManualPhaseTwoOpeningActionPolicy(role)
                    else PhaseTwoPilotPolicy { _, _ -> error("Fixture expects only the active opening actor") }
                }
                val boundary = PhaseTwoPilotBoundary(adapter, cardRegistry, policies)
                boundary.step().error shouldBe null

                adapter.state.getBattlefield(seats[0]).count {
                    adapter.state.requireEntity(it).get<CardComponent>()?.name == "Forest"
                } shouldBe 1

                adapter.stop("RESOURCE_CAP", "EXCLUDED_OPENING_BOUNDARY_FIXTURE_STOP")
                val trace = adapter.finish()
                PhaseTwoTelemetryAdapter.replay(trace, actionProcessor) shouldBe trace
            }
        }
    }
}
