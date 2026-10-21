package com.wingedsheep.engine.event

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.core.spec.style.FunSpec

/**
 * SPORT S2: predeclared source-only metadata regression.
 * No card casting, turn advance, game initializer, new Commander game, or
 * policy change. EXACT delayed trigger matching and its synthesized step
 * event must agree. R5/R9/CONTROL/CURRENT are not touched by this test.
 */
class ManualSportS2DelayedTriggerMetadataTest : FunSpec({
    val pactOwner=EntityId("sport-s2-pact-owner")
    val opponent=EntityId("sport-s2-opponent")
    val source=EntityId("sport-s2-pact-object")
    val detector=TriggerDetector(CardRegistry())

    fun originalDelayed(step:Step,notBefore:Int=7):DelayedTriggeredAbility =
        DelayedTriggeredAbility(
            id="sport-s2-fixed-delayed-upkeep",
            effect=CompositeEffect(emptyList()),
            fireAtStep=step,
            sourceId=source,
            sourceName="Pact of Negation",
            controllerId=pactOwner,
            fireOnPlayerId=pactOwner,
            notBeforeTurn=notBefore,
        )
    fun game(active:EntityId=pactOwner,turn:Int=7,delayed:DelayedTriggeredAbility):GameState =
        GameState(
            activePlayerId=active,
            turnOrder=listOf(pactOwner,opponent),
            turnNumber=turn,
            delayedTriggers=listOf(delayed),
        )

    test("SPORT-S2-POS-UPKEEP: true Pact next upkeep trigger displays actual UPKEEP step") {
        val state=game(delayed=originalDelayed(Step.UPKEEP))
        val (triggers,consumed)=detector.detectDelayedTriggers(state,Step.UPKEEP)
        check(triggers.size==1 && consumed==setOf("sport-s2-fixed-delayed-upkeep"))
        val one=triggers.single()
        check(one.controllerId==pactOwner && one.sourceName=="Pact of Negation")
        check(one.triggerContext.step==Step.UPKEEP)
        check(one.ability.trigger==EventPattern.StepEvent(Step.UPKEEP,Player.Each)){
            "Delayed UPKEEP synthesized metadata wrongly says END"
        }
        check(one.ability.description.contains("upkeep",ignoreCase=true)){
            "Delayed UPKEEP event UI/log description hides the correct step"
        }
        check(detector.detectDelayedTriggers(state,Step.END).first.isEmpty()) {
            "Pact UPKEEP delayed trigger incorrectly fired at END"
        }
    }

    test("SPORT-S2-NEG-END: legitimate end-step delayed trigger retains END description") {
        val state=game(delayed=originalDelayed(Step.END))
        val (triggers,consumed)=detector.detectDelayedTriggers(state,Step.END)
        check(triggers.size==1 && consumed==setOf("sport-s2-fixed-delayed-upkeep"))
        val one=triggers.single()
        check(one.triggerContext.step==Step.END)
        check(one.ability.trigger==EventPattern.StepEvent(Step.END,Player.Each))
        check(one.ability.description.contains("End Step",ignoreCase=true))
        check(detector.detectDelayedTriggers(state,Step.UPKEEP).first.isEmpty())
    }

    test("SPORT-S2-NEG-WRONG-PLAYER: do not invent debt on opponent's upkeep") {
        val state=game(active=opponent,delayed=originalDelayed(Step.UPKEEP))
        val (triggers,consumed)=detector.detectDelayedTriggers(state,Step.UPKEEP)
        check(triggers.isEmpty() && consumed.isEmpty())
    }

    test("SPORT-S2-NEG-NOT-BEFORE: respect deferred next-turn minimum") {
        val state=game(turn=7,delayed=originalDelayed(Step.UPKEEP,notBefore=8))
        val (triggers,consumed)=detector.detectDelayedTriggers(state,Step.UPKEEP)
        check(triggers.isEmpty() && consumed.isEmpty())
        val (after,consumedAfter)=detector.detectDelayedTriggers(
            game(turn=8,delayed=originalDelayed(Step.UPKEEP,notBefore=8)),Step.UPKEEP)
        check(after.size==1 && consumedAfter.size==1)
    }
})
