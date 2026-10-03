package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.continuations.CardSpecificContinuationResumer
import com.wingedsheep.engine.handlers.effects.stack.ChangeOneTargetToExecutor
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.effects.ChangeOneTargetToEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FixedDestinationEffectTest : ScenarioTestBase() {
    private val source = card("Fixed Destination") { manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 2 }
    private val spell = card("Two independent targets") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("first", Targets.Creature); target("second", Targets.Creature); effect = Effects.DrawCards(1) }
    }
    init {
        listOf(source, spell).forEach(cardRegistry::register)
        test("multiple legal slots pause and the selected slot alone changes") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, source.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, spell.name).build()
            val destination = g.findPermanent(source.name)!!
            val old = g.findPermanent("Grizzly Bears")!!
            val id = g.state.getHand(g.player1Id).single()
            g.execute(CastSpell(g.player1Id, id, listOf(ChosenTarget.Permanent(old), ChosenTarget.Permanent(old)))).error shouldBe null
            val result = ChangeOneTargetToExecutor().execute(g.state, ChangeOneTargetToEffect(),
                EffectContext(sourceId = destination, controllerId = g.player1Id, targets = listOf(ChosenTarget.Spell(id))))
            result.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>().options.size shouldBe 2
            val continuation = FixedDestinationRetargetContinuation(g.state.objectRef(id)!!, g.state.objectRef(destination)!!,
                listOf(0, 1), listOf(ChosenTarget.Permanent(old), ChosenTarget.Permanent(old)))
            val resumed = CardSpecificContinuationResumer(EngineServices(cardRegistry)).resumeFixedDestinationRetarget(
                g.state, continuation, OptionChosenResponse("choice", 1)) { state, events -> ExecutionResult.success(state, events) }
            resumed.state.getEntity(id)!!.get<TargetsComponent>()!!.targets shouldBe listOf(ChosenTarget.Permanent(old), ChosenTarget.Permanent(destination))
            resumed.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 1
        }
        test("a stale destination visit makes paused retarget do nothing") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, source.name)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, spell.name).build()
            val destination = g.findPermanent(source.name)!!
            val old = g.findPermanent("Grizzly Bears")!!
            val id = g.state.getHand(g.player1Id).single()
            val targets = listOf(ChosenTarget.Permanent(old), ChosenTarget.Permanent(old))
            g.execute(CastSpell(g.player1Id, id, targets)).error shouldBe null
            val currentRef = g.state.objectRef(destination)!!
            val continuation = FixedDestinationRetargetContinuation(g.state.objectRef(id)!!,
                ObjectRef(destination, currentRef.generation - 1), listOf(0, 1), targets)
            val result = CardSpecificContinuationResumer(EngineServices(cardRegistry)).resumeFixedDestinationRetarget(
                g.state, continuation, OptionChosenResponse("choice", 0)) { state, events -> ExecutionResult.success(state, events) }
            result.state shouldBe g.state
            result.events shouldBe emptyList()
        }
    }
}
