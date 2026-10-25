package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.mechanics.targeting.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.*
import io.kotest.matchers.shouldBe

/** Internal relational contract; public occurrence selection remains a separate gate. */
class FixedRecipientPlayerRelationTest : ScenarioTestBase() {
    init {
        for (owned in listOf(false, true)) {
            test("FRP same selected player permits replacement owned=$owned") {
                val game = scenario().withPlayers("One", "Two")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Hill Giant").build()
                val old = game.findPermanent("Grizzly Bears")!!
                val replacement = game.findPermanent("Hill Giant")!!
                val before = TargetSlotPlan(listOf(
                    TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Player(game.player1Id)))),
                    TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Permanent(old), game.state.objectRef(old))))))
                val address = TargetSlotAddress(1, 0)
                val after = before.replacingRecipient(address, ChosenTarget.Permanent(replacement), game.state.objectRef(replacement))!!
                val filter = if (owned) GameObjectFilter.Creature.ownedByTargetPlayer() else GameObjectFilter.Creature.targetPlayerControls()
                TargetValidator().replacementIsLegal(game.state, before, after, address,
                    listOf(TargetPlayer(), TargetCreature(filter = TargetFilter(filter))),
                    game.player2Id, old, emptySet(), emptySet(), setOf("CREATURE"), TargetingSourceType.ABILITY, null) shouldBe true
            }
            test("FRP changing player cannot invalidate retained permanent owned=$owned") {
                val game = scenario().withPlayers("One", "Two")
                    .withCardOnBattlefield(1, "Grizzly Bears").build()
                val old = game.findPermanent("Grizzly Bears")!!
                val before = TargetSlotPlan(listOf(
                    TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Player(game.player1Id)))),
                    TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Permanent(old), game.state.objectRef(old))))))
                val address = TargetSlotAddress(0, 0)
                val after = before.replacingRecipient(address, ChosenTarget.Player(game.player2Id), null)!!
                val filter = if (owned) GameObjectFilter.Creature.ownedByTargetPlayer() else GameObjectFilter.Creature.targetPlayerControls()
                TargetValidator().replacementIsLegal(game.state, before, after, address,
                    listOf(TargetPlayer(), TargetCreature(filter = TargetFilter(filter))),
                    game.player1Id, old, emptySet(), emptySet(), setOf("CREATURE"), TargetingSourceType.ABILITY, null) shouldBe false
            }
        }
    }
}
