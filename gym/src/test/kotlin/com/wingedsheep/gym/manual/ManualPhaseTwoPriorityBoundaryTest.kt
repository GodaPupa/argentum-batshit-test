package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

/** Four excluded real-engine boundary traces, not frozen-deck games or full-pilot acceptance. */
class ManualPhaseTwoPriorityBoundaryTest : ScenarioTestBase() {
    init {
        listOf(ManualPhaseTwoPilotRole.SPORT, ManualPhaseTwoPilotRole.RACE).forEachIndexed { gearIndex, role ->
            listOf(false, true).forEach { isProtected ->
                test("$role real helper priority boundary and replay protected=$isProtected") {
                    val names = listOf("Kinnan, Bonder Prodigy", "Shorikai, Genesis Engine",
                        "Animar, Soul of Elements", "Animar, Soul of Elements")
                    val lists = listOf(
                        List(49) { "Forest" } + List(49) { "Island" } + "Pact of Negation",
                        List(98) { "Island" } + "Counterspell",
                        List(97) { "Island" } + listOf("An Offer You Can't Refuse", "Pact of Negation"),
                        List(99) { "Island" })
                    val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                        players = names.indices.map { i ->
                            PlayerConfig("EXCLUDED_PRIORITY_$i", Deck(lists[i]), commanderCardNames = listOf(names[i]))
                        }, format = Format.Commander(), startingPlayerIndex = 0, skipMulligans = false,
                        useHandSmoother = false,
                        seed = 0x4d545052494f5230L + gearIndex * 2 + if (isProtected) 1 else 0,
                    ))
                    val seats = initialized.playerIds
                    var opening = initialized.state
                    // Trusted excluded construction only. Policies receive only masked observations.
                    seats.forEachIndexed { i, player ->
                        val all = opening.getHand(player) + opening.getLibrary(player)
                        fun named(id: EntityId) = opening.requireEntity(id).get<CardComponent>()!!.name
                        val spells = all.filter { named(it) !in setOf("Island", "Forest") }
                        val lands = if (i == 0) {
                            listOf(all.first { named(it) == "Forest" }) +
                                all.filter { named(it) == "Island" }.take(6 - spells.size)
                        } else all.filter { named(it) == "Island" }.take(7 - spells.size)
                        val hand = spells + lands
                        hand.size shouldBe 7
                        opening = opening.copy(zones = opening.zones +
                            (ZoneKey(player, Zone.HAND) to hand) +
                            (ZoneKey(player, Zone.LIBRARY) to all.filter { it !in hand }))
                    }
                    val adapter = PhaseTwoTelemetryAdapter(opening, actionProcessor,
                        "cdc6f5965c15e2624ef5f667e0b1ba2debc01b05", seats, 2)
                    fun act(action: GameAction) { adapter.process(action).error shouldBe null }
                    seats.forEach { act(KeepHand(it)) }
                    val enumerator = LegalActionEnumerator.create(cardRegistry)
                    var reached = false
                    repeat(600) {
                        if (!reached) {
                            val state = adapter.state
                            if (state.turnNumber == 9 && state.phase == Phase.PRECOMBAT_MAIN &&
                                state.activePlayerId == seats[0] && state.priorityPlayerId == seats[0] &&
                                state.pendingDecision == null && state.stack.isEmpty()) {
                                reached = true
                            } else {
                                val pending = state.pendingDecision
                                if (pending != null) {
                                    require(pending is SelectCardsDecision && state.phase == Phase.ENDING)
                                    val own = state.getHand(pending.playerId).toSet()
                                    require(pending.options.all { it in own })
                                    act(SubmitDecision(pending.playerId,
                                        CardsSelectedResponse(pending.id, pending.options.sortedBy {
                                            if (state.requireEntity(it).get<CardComponent>()!!.name in
                                                setOf("Island", "Forest")) 0 else 1
                                        }.take(pending.minSelections))))
                                } else {
                                    val actor = requireNotNull(state.priorityPlayerId)
                                    val offers = enumerator.enumerate(state, actor, EnumerationMode.ACTIONS_ONLY)
                                    val land = if (state.activePlayerId == actor && state.phase.isMainPhase)
                                        offers.filter { it.action is PlayLand }.minByOrNull { offer ->
                                            val wantsForest = actor == seats[0] && state.getBattlefield().none {
                                                val card = state.requireEntity(it).get<CardComponent>()
                                                card != null && card.ownerId == actor && card.name == "Forest"
                                            }
                                            val card = state.requireEntity((offer.action as PlayLand).cardId)
                                                .get<CardComponent>()!!.name
                                            if (card == if (wantsForest) "Forest" else "Island") 0 else 1
                                        } else null
                                    val choice = land ?: offers.firstOrNull {
                                        it.action is DeclareAttackers || it.action is DeclareBlockers
                                    } ?: offers.first { it.action is PassPriority }
                                    act(choice.action)
                                }
                            }
                        }
                    }
                    reached shouldBe true
                    fun ownCard(player: EntityId, name: String) = adapter.state.getHand(player).single {
                        adapter.state.requireEntity(it).get<CardComponent>()!!.name == name
                    }
                    val commander = adapter.state.requireEntity(seats[0])
                        .get<CommanderRegistryComponent>()!!.commanderIds.single()
                    act(CastSpell(seats[0], commander))
                    val threat = adapter.state.stack.last()
                    act(PassPriority(seats[0]))
                    act(CastSpell(seats[1], ownCard(seats[1], "Counterspell"),
                        targets = listOf(ChosenTarget.Spell(threat))))
                    val helper = adapter.state.stack.last()
                    act(PassPriority(seats[1]))
                    if (isProtected) {
                        act(PassPriority(seats[2]))
                        act(PassPriority(seats[3]))
                        act(CastSpell(seats[0], ownCard(seats[0], "Pact of Negation"),
                            targets = listOf(ChosenTarget.Spell(helper))))
                        act(PassPriority(seats[0]))
                        act(PassPriority(seats[1]))
                    }
                    adapter.state.priorityPlayerId shouldBe seats[2]
                    val beforeSize = adapter.state.stack.size
                    val boundary = PhaseTwoPilotBoundary(adapter, cardRegistry, seats.associateWith { seat ->
                        if (seat == seats[2]) ManualPhaseTwoPriorityConservationPolicy(role)
                        else PhaseTwoPilotPolicy { _, _ -> error("Fixture expects only Manual's strategic turn") }
                    })
                    boundary.step().error shouldBe null
                    adapter.state.stack.size shouldBe if (isProtected) beforeSize + 1 else beforeSize
                    if (isProtected) {
                        adapter.state.requireEntity(adapter.state.stack.last()).get<CardComponent>()!!.name shouldBe
                            "An Offer You Can't Refuse"
                    }
                    adapter.stop("RESOURCE_CAP", "EXCLUDED_STRATEGIC_BOUNDARY_FIXTURE_STOP")
                    val trace = adapter.finish()
                    trace.steps.all { it.accepted == true } shouldBe true
                    PhaseTwoTelemetryAdapter.replay(trace, actionProcessor) shouldBe trace
                }
            }
        }
    }
}
