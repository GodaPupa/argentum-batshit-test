package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.gym.contract.LegalActionView
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

/** Excluded fixed observation; this does not qualify a complete gear or opponent pilot. */
class ManualPhaseTwoOpeningActionPolicyTest : FunSpec({
    val actor = EntityId.of("manual-seat")
    val land = EntityId.of("physical-forest")
    fun observation(actions: List<LegalActionView>, hidden: Boolean = false): TrainingObservation {
        val seats = listOf(actor, EntityId.of("opponent-one"), EntityId.of("opponent-two"),
            EntityId.of("opponent-three"))
        val card = EntityFeatures(entityId = land, cardDefinitionId = "Forest", name = "Forest",
            zone = Zone.HAND, ownerId = actor, controllerId = null,
            types = setOf("LAND"), subtypes = setOf("FOREST"), colors = emptySet(),
            keywords = emptySet(), manaCost = "", manaValue = 0, power = null, toughness = null)
        return TrainingObservation(
            schemaHash = "excluded-fixture", perspectivePlayerId = actor, agentToAct = actor,
            turnNumber = 1, phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor,
            players = seats.map { seat -> PlayerView(seat, seat.value, 40,
                if (seat == actor) 1 else 7, 92, 0, 0, ManaPoolView(),
                seat == actor, seat == actor, seat == actor, false) },
            zones = listOf(ZoneView(actor, Zone.HAND, hidden, 1, if (hidden) emptyList() else listOf(card))) +
                seats.map { ZoneView(it, Zone.LIBRARY, true, 92, emptyList()) },
            stack = emptyList(), pendingDecision = null, legalActions = actions,
            terminated = false, winnerId = null, stateDigest = "excluded-fixture")
    }
    val landAction = LegalActionView(actionId = 7, kind = "PlayLand",
        description = "Play Forest", affordable = true, sourceEntityId = land)
    val pass = LegalActionView(actionId = 8, kind = "PassPriority",
        description = "Pass priority", affordable = true)

    for (role in ManualPhaseTwoPilotRole.entries) {
        test("$role chooses the unique own physical land action before pass") {
            ManualPhaseTwoOpeningActionPolicy(role).choose(
                observation(listOf(pass, landAction)), PhaseTwoPilotPrompt.Engine) shouldBe
                PhaseTwoPilotChoice.Action(7)
        }
    }
    test("ambiguous duplicate physical land offer fails closed") {
        val another = landAction.copy(actionId = 9, sourceEntityId = land)
        val view = observation(listOf(pass, landAction, another))
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoOpeningActionPolicy(ManualPhaseTwoPilotRole.RACE).choose(
                view, PhaseTwoPilotPrompt.Engine)
        }
    }
    test("hidden own hand cannot drive a physical action") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoOpeningActionPolicy(ManualPhaseTwoPilotRole.OPPONENT).choose(
                observation(listOf(pass, landAction), hidden = true), PhaseTwoPilotPrompt.Engine)
        }
    }
})
