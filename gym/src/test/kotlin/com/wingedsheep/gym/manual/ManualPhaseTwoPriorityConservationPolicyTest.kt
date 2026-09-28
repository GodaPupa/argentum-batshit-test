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

import com.wingedsheep.gym.contract.StackItemView
import com.wingedsheep.gym.contract.StackItemKind
import com.wingedsheep.gym.contract.ActionParams

/** Eight predeclared excluded action choices; no full strategic competence claim. */
class ManualPhaseTwoPriorityConservationPolicyTest : FunSpec({
    val actor = EntityId.of("manual")
    val opponent = EntityId.of("kinnan")
    val helper = EntityId.of("helper")
    val fourth = EntityId.of("fourth")
    val threat = EntityId.of("threat")
    val counter = EntityId.of("helper-counter")
    val protection = EntityId.of("protection")
    val offer = EntityId.of("own-offer")
    val pact = EntityId.of("own-pact")
    fun view(isProtected: Boolean, hasOffer: Boolean = true): TrainingObservation {
        fun card(id: EntityId, name: String) = EntityFeatures(
            id, name, name, Zone.HAND, actor, null, setOf("INSTANT"), emptySet(),
            setOf("BLUE"), emptySet(), if (id == offer) "{U}" else "{0}",
            if (id == offer) 1 else 0, power = null, toughness = null)
        val cards = listOfNotNull(if (hasOffer) card(offer, "An Offer You Can't Refuse") else null,
            card(pact, "Pact of Negation"))
        val stack = listOf(
            StackItemView(threat, opponent, "Kinnan, Bonder Prodigy", StackItemKind.SPELL),
            StackItemView(counter, helper, "Counterspell", StackItemKind.SPELL, targets = listOf(threat))) +
            if (isProtected) listOf(StackItemView(protection, opponent, "Fierce Guardianship",
                StackItemKind.SPELL, targets = listOf(counter))) else emptyList()
        val actions = listOf(LegalActionView(1, "PassPriority", "Pass", true)) + cards.mapIndexed { i, c ->
            LegalActionView(i + 2, "CastSpell", c.name, true, c.entityId,
                listOf(protection), minTargets = 1, maxTargets = 1)
        }
        return TrainingObservation("excluded", actor, actor, 4, Phase.PRECOMBAT_MAIN,
            Step.PRECOMBAT_MAIN, opponent, actor,
            listOf(actor, opponent, helper, fourth).map {
                PlayerView(it, it.value, 40, if (it == actor) cards.size else 7, 80, 0, 0,
                    ManaPoolView(), it == actor, it == opponent, it == actor, false)
            },
            listOf(ZoneView(actor, Zone.HAND, false, cards.size, cards)), stack,
            null, actions, false, null, "excluded")
    }
    for (role in listOf(ManualPhaseTwoPilotRole.SPORT, ManualPhaseTwoPilotRole.RACE)) {
        test("$role conserves interaction behind an actual helper counter") {
            ManualPhaseTwoPriorityConservationPolicy(role).choose(view(false), PhaseTwoPilotPrompt.Engine) shouldBe
                PhaseTwoPilotChoice.Action(1)
        }
        test("$role answers actual protection with Offer before Pact debt") {
            ManualPhaseTwoPriorityConservationPolicy(role).choose(view(true), PhaseTwoPilotPrompt.Engine) shouldBe
                PhaseTwoPilotChoice.Action(2, ActionParams(targets = listOf(protection)))
        }
        test("$role uses sole legal Pact answer when Offer absent") {
            ManualPhaseTwoPriorityConservationPolicy(role).choose(view(true, false), PhaseTwoPilotPrompt.Engine) shouldBe
                PhaseTwoPilotChoice.Action(2, ActionParams(targets = listOf(protection)))
        }
    }
    test("Cruise does not inherit the R3-P helper policy") {
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPriorityConservationPolicy(ManualPhaseTwoPilotRole.CRUISE)
                .choose(view(false), PhaseTwoPilotPrompt.Engine)
        }
    }
    test("hidden own hand cannot authorize expenditure") {
        val original = view(true)
        shouldThrow<IllegalArgumentException> {
            ManualPhaseTwoPriorityConservationPolicy(ManualPhaseTwoPilotRole.RACE)
                .choose(original.copy(zones = original.zones.map { it.copy(hidden = true) }),
                    PhaseTwoPilotPrompt.Engine)
        }
    }
})
