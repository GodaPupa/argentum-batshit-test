package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe

class FixedRecipientMutationWitnessTest : ScenarioTestBase() {
    init {
        for (axis in listOf("recipient-visit", "source-color")) test("FM $axis") {
            val game = scenario().withPlayers("One", "Two").withCardOnBattlefield(1, "Hill Giant")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Craw Wurm").build()
            val source = game.findPermanent("Hill Giant")!!
            val old = game.findPermanent("Grizzly Bears")!!
            val recipient = game.findPermanent("Craw Wurm")!!
            val plan = CurrentTargetPlan(TargetSlotPlan(listOf(TargetInstanceSelection(listOf(
                TargetSlot(ChosenTarget.Permanent(old), game.state.objectRef(old)))))))
            val origin = game.state.objectRef(source)!!
            val queued = StackResolver(cardRegistry).putTriggeredAbility(game.state,
                TriggeredAbilityOnStackComponent(source, "Hill Giant", game.player1Id, Effects.Destroy(EffectTarget.ContextTarget(0)), "Witness",
                    objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin), originalTargetSlots = plan.selections),
                plan.targets, listOf(Targets.Creature), currentTargetPlan = plan)
            queued.error shouldBe null
            val id = queued.state.stack.last()
            if (axis == "source-color") {
                val state = queued.state.addFloatingEffect(Layer.ABILITY, SerializableModification.GrantKeyword("PROTECTION_FROM_RED"),
                    setOf(recipient), Duration.EndOfTurn, EffectContext(sourceId = source, controllerId = game.player1Id))
                FixedRecipientRetargeting.offer(state, id, ChosenTarget.Permanent(recipient), game.player2Id) shouldBe null
            } else {
                val offer = requireNotNull(FixedRecipientRetargeting.offer(queued.state, id, ChosenTarget.Permanent(recipient), game.player2Id))
                val identity = queued.state.objectIdentities.getValue(recipient)
                val state = queued.state.copy(objectIdentities = queued.state.objectIdentities + (recipient to identity.copy(generation = identity.generation + 1)))
                val result = FixedRecipientRetargeting.apply(state, offer, offer.addresses.single())
                result.state shouldBe state
                result.events shouldBe emptyList()
            }
        }
    }
}
