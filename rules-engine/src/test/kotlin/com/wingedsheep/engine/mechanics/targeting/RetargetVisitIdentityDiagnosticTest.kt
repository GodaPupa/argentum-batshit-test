package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.*
import io.kotest.matchers.shouldBe

/** Required behavior diagnostic; intentionally isolated from promotion when not yet supported. */
class RetargetVisitIdentityDiagnosticTest : ScenarioTestBase() {
    private val spell = card("Two visits diagnostic") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { target("first", Targets.Creature); target("second", Targets.Creature); effect = Effects.DrawCards(1) }
    }
    init {
        cardRegistry.register(spell)
        test("one target slot can choose a new visit while another retains the old visit of the same entity") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, spell.name).build()
            val permanent = g.findPermanent("Grizzly Bears")!!
            val id = g.state.getHand(g.player1Id).single()
            val target = ChosenTarget.Permanent(permanent)
            g.execute(CastSpell(g.player1Id, id, listOf(target, target))).error shouldBe null
            val oldStamp = g.state.getEntity(permanent)!!.get<BattlefieldEntryTimestampComponent>()?.timestamp ?: 0L
            // The existing identity contract explicitly treats a changed battlefield-entry stamp
            // as a new visit. Retarget only the first slot; the second remains the departed object.
            val returned = g.state.updateEntity(permanent) { it.with(BattlefieldEntryTimestampComponent(oldStamp + 1)) }
            FixedDestinationRetarget.legalSlots(returned, id, target) shouldBe listOf(0, 1)
            val result = FixedDestinationRetarget.replaceSlot(returned, id, 0, target)
            result.events.filterIsInstance<BecomesTargetEvent>().map { it.targetEntityId } shouldBe listOf(permanent)
            // A per-entity stamp cannot represent the different visits of these two slots.
            // The required first assertion fails before an incorrect global refresh can pass.
        }
    }
}
