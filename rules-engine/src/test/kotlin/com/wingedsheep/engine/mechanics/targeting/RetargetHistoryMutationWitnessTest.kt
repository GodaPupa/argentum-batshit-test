package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RetargetHistoryMutationWitnessTest : FunSpec({
    val a = EntityId("a"); val b = EntityId("b"); val c = EntityId("c")
    val state = GameState(entities = listOf(a,b,c).associateWith { ComponentContainer.of(BattlefieldEntryTimestampComponent(9L)) },
        zones = mapOf(ZoneKey(EntityId("owner"), Zone.BATTLEFIELD) to listOf(a,b,c)))
    val old = listOf(ChosenTarget.Permanent(a), ChosenTarget.Permanent(b))
    val next = listOf(ChosenTarget.Permanent(a), ChosenTarget.Permanent(c))
    test("HM01 changing another recipient never refreshes an unchanged old visit") {
        val p = TargetsComponent(old, targetEntryStamps = mapOf(a to 1L, b to 2L))
        val q = requireNotNull(p.withRetargetedHistory(state, next))
        q.targetEntryStamps[a] shouldBe 1L
    }
    test("HM02 changing another recipient never invents an unchanged historical visit") {
        val p = TargetsComponent(old)
        val q = requireNotNull(p.withRetargetedHistory(state, next))
        q.targetEntryStamps[a] shouldBe null
    }
})
