package com.wingedsheep.engine.handlers.effects.drawing

import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.DrawUpToEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Regression for delayed effects that freeze a player as a concrete entity id. */
class DrawUpToSpecificEntityPlayerTest : FunSpec({
    test("SpecificEntity player with a nonempty library pauses for that player's draw count") {
        val controller = EntityId("controller")
        val victim = EntityId("victim")
        val library = listOf(EntityId("card-1"), EntityId("card-2"), EntityId("card-3"))
        val state = GameState(
            zones = mapOf(ZoneKey(victim, Zone.LIBRARY) to library),
            turnOrder = listOf(controller, victim),
        )

        val result = DrawUpToExecutor().execute(
            state,
            DrawUpToEffect(2, EffectTarget.SpecificEntity(victim)),
            EffectContext(sourceId = null, controllerId = controller, targets = emptyList()),
        )

        result.error shouldBe null
        result.isPaused shouldBe true
        val decision = result.state.pendingDecision.shouldBeInstanceOf<ChooseNumberDecision>()
        decision.playerId shouldBe victim
        decision.minValue shouldBe 0
        decision.maxValue shouldBe 2
    }
})
