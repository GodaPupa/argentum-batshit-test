package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.gym.contract.ManaPoolView
import com.wingedsheep.gym.contract.PlayerView
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.gym.contract.ZoneView
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Synthetic four-seat, actor-masked, no initializer, no new games or seed. */
class ManualPhaseTwoForcedPregamePolicyTest : FunSpec({
    val actor = EntityId.of("manual")
    val farm = EntityId.of("farm")
    val rogsi = EntityId.of("rogsi")
    val kinnan = EntityId.of("kinnan")
    val seats = listOf(actor, farm, rogsi, kinnan)
    val animar = EntityId.of("animar")
    val seven = (0..6).map { EntityId.of("hand-" + it) }

    fun card(id: EntityId, name: String, owner: EntityId, zone: Zone, kind: String) =
        EntityFeatures(entityId = id, cardDefinitionId = name, name = name, zone = zone,
            ownerId = owner, controllerId = null, types = setOf(kind),
            subtypes = emptySet(), colors = emptySet(), keywords = emptySet(),
            manaCost = "", manaValue = 0, power = null, toughness = null)

    fun observation(
        hiddenOwnHand: Boolean = false,
        visibleOpponentHand: Boolean = false,
        missingOwnCommander: Boolean = false,
        acting: EntityId = actor,
    ): TrainingObservation {
        val ownHand = seven.mapIndexed { idx, id ->
            card(id, if (idx < 3) "Forest" else "Llanowar Elves", actor, Zone.HAND,
                if (idx < 3) "LAND" else "CREATURE")
        }
        val zones = listOf(
            ZoneView(actor, Zone.HAND, hiddenOwnHand, 7, if (hiddenOwnHand) emptyList() else ownHand),
            ZoneView(actor, Zone.LIBRARY, true, 92, emptyList()),
            ZoneView(actor, Zone.COMMAND, false, if (missingOwnCommander) 0 else 1,
                if (missingOwnCommander) emptyList() else listOf(
                    card(animar, "Animar, Soul of Elements", actor, Zone.COMMAND, "CREATURE"))),
        ) + seats.drop(1).flatMap { opp ->
            listOf(
                ZoneView(opp, Zone.HAND, !visibleOpponentHand,
                    7, if (visibleOpponentHand) listOf(card(EntityId.of(opp.value + "-secret"),
                        "Secret", opp, Zone.HAND, "INSTANT")) else emptyList()),
                ZoneView(opp, Zone.LIBRARY, true, 92, emptyList()),
                ZoneView(opp, Zone.COMMAND, false, 1, listOf(
                    card(EntityId.of(opp.value + "-commander"), opp.value + " commander",
                        opp, Zone.COMMAND, "CREATURE"))),
            )
        }
        return TrainingObservation(
            schemaHash = "excluded-r8-forced-pregame",
            perspectivePlayerId = actor, agentToAct = acting, turnNumber = 1,
            phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = farm, priorityPlayerId = farm,
            players = seats.map { id ->
                PlayerView(id, id.value, 40, 7, 92, 0, 0, ManaPoolView(),
                    id == actor, id == actor, false, false)
            },
            zones = zones, stack = emptyList(), pendingDecision = null,
            legalActions = emptyList(), terminated = false, winnerId = null,
            stateDigest = "excluded-r8-forced-pregame",
        )
    }

    val roles = listOf(
        ManualPhaseTwoPilotRole.CRUISE,
        ManualPhaseTwoPilotRole.SPORT,
        ManualPhaseTwoPilotRole.RACE,
    )
    for (role in roles) {
        test("$role forced last mulligan keeps without ordinary priority") {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(), PhaseTwoPilotPrompt.Mulligan(false)
            ) shouldBe PhaseTwoPilotChoice.Keep
        }
        test("$role rejects even bottom-all because bottom order remains strategic") {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(), PhaseTwoPilotPrompt.Bottom(7)
                )
            }
        }
        test("$role refuses voluntary R3 keep or mulligan") {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(), PhaseTwoPilotPrompt.Mulligan(true)
                )
            }
        }
        test("$role refuses nontrivial R3 bottom order") {
            shouldThrow<IllegalArgumentException> {
                ManualPhaseTwoPartialGearPilot(role).choose(
                    observation(), PhaseTwoPilotPrompt.Bottom(1)
                )
            }
        }
    }
    test("all roles reject hidden physical own hand") {
        for (role in roles) shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(hiddenOwnHand = true), PhaseTwoPilotPrompt.Mulligan(false)
            )
        }
    }
    test("all roles reject leaked opponent secret hand") {
        for (role in roles) shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(visibleOpponentHand = true), PhaseTwoPilotPrompt.Mulligan(false)
            )
        }
    }
    test("all roles reject lost actual commander") {
        for (role in roles) shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(missingOwnCommander = true), PhaseTwoPilotPrompt.Mulligan(false)
            )
        }
    }
    test("pregame actor binding cannot be overridden") {
        for (role in roles) shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPartialGearPilot(role).choose(
                observation(acting = kinnan), PhaseTwoPilotPrompt.Mulligan(false)
            )
        }
    }
    test("an opponent pilot cannot inherit Manual forced pregame") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoForcedPregamePolicy(ManualPhaseTwoPilotRole.OPPONENT).choose(
                observation(), PhaseTwoPilotPrompt.Mulligan(false)
            )
        }
    }
})
