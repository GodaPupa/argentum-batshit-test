package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
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
 * Real-engine excluded integration for the sole-action Animar deployment seam.
 * No competing strategic fork is represented or qualified here.
 */
class ManualPhaseTwoUniqueCommanderCastIntegrationTest : ScenarioTestBase() {
    init {
        listOf(
            ManualPhaseTwoPilotRole.CRUISE,
            ManualPhaseTwoPilotRole.SPORT,
            ManualPhaseTwoPilotRole.RACE,
        ).forEachIndexed { roleIndex, role ->
            test("$role casts physical Animar from command when it is the sole strategic action and replays") {
                val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                    players = (0..3).map { seat ->
                        PlayerConfig(
                            "EXCLUDED_COMMAND_$seat",
                            Deck(
                                listOf("Forest", "Island", "Mountain") +
                                    List(96) { "Counterspell" }
                            ),
                            commanderCardNames = listOf("Animar, Soul of Elements"),
                        )
                    },
                    format = Format.Commander(),
                    startingPlayerIndex = 0,
                    skipMulligans = false,
                    useHandSmoother = false,
                    seed = 0x4d54434f4d4d0000L + roleIndex,
                ))
                val seats = initialized.playerIds
                val actor = seats[0]
                var opening = initialized.state

                // Excluded trusted construction: every hand has only Counterspells; actor has G/U/R lands in play.
                seats.forEach { player ->
                    val all = opening.getHand(player) + opening.getLibrary(player)
                    fun named(id: EntityId) = opening.requireEntity(id).get<CardComponent>()!!.name
                    val hand = all.filter { named(it) == "Counterspell" }.take(7)
                    hand.size shouldBe 7
                    opening = opening.copy(zones = opening.zones +
                        (ZoneKey(player, Zone.HAND) to hand) +
                        (ZoneKey(player, Zone.LIBRARY) to all.filter { it !in hand }))
                }

                fun named(id: EntityId) = opening.requireEntity(id).get<CardComponent>()!!.name
                val actorLibrary = opening.getLibrary(actor)
                val manaLands = listOf("Forest", "Island", "Mountain").map { land ->
                    actorLibrary.single { named(it) == land }
                }
                manaLands.forEach { id ->
                    opening = ZoneTransitionService.moveToZone(
                        opening, id, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
                    ).state
                }

                val adapter = PhaseTwoTelemetryAdapter(
                    opening, actionProcessor, "ac9125d5f3c3a70c990261549a9c62b910e99ad0", seats, 100 + roleIndex
                )
                fun act(action: GameAction) { adapter.process(action).error shouldBe null }
                seats.forEach { act(KeepHand(it)) }

                var reached = false
                var guard = 0
                while (!reached && guard++ < 64) {
                    val state = adapter.state
                    reached = state.phase == Phase.PRECOMBAT_MAIN &&
                        state.activePlayerId == actor &&
                        state.priorityPlayerId == actor &&
                        state.pendingDecision == null &&
                        state.stack.isEmpty()
                    if (!reached) act(PassPriority(requireNotNull(state.priorityPlayerId)))
                }
                reached shouldBe true

                val policies = seats.associateWith { seat ->
                    if (seat == actor) ManualPhaseTwoUniqueCommanderCastPolicy(role)
                    else PhaseTwoPilotPolicy { _, _ -> error("Fixture expects only the active Manual actor") }
                }
                val boundary = PhaseTwoPilotBoundary(adapter, cardRegistry, policies)
                boundary.step().error shouldBe null

                val stackNames = adapter.state.stack.mapNotNull { id ->
                    adapter.state.getEntity(id)?.get<CardComponent>()?.name
                }
                stackNames.contains("Animar, Soul of Elements") shouldBe true

                adapter.stop("RESOURCE_CAP", "EXCLUDED_UNIQUE_COMMANDER_CAST_FIXTURE_STOP")
                val trace = adapter.finish()
                PhaseTwoTelemetryAdapter.replay(trace, actionProcessor) shouldBe trace
            }
        }
    }
}
