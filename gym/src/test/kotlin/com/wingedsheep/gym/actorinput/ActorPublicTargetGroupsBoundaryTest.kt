package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.targeting.TargetSlotPlan
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class ActorPublicTargetGroupsBoundaryTest : FunSpec({
    val player = EntityId.of("actor")
    val spell = EntityId.of("spell")
    test("AP01 grouped public intent checks every nested entity reference") {
        val action = CastSpell(player, spell, targetGroups = listOf(emptyList(), listOf(ChosenTarget.Player(player))))
        requireAccessible(action, setOf(player, spell))
        shouldThrow<ObservationBoundaryException> {
            requireAccessible(action.copy(targetGroups = listOf(listOf(ChosenTarget.Player(EntityId.of("hidden"))))), setOf(player, spell))
        }.failure shouldBe BoundaryFailure.INACCESSIBLE_REFERENCE
    }
    test("AP02 internal spell history never crosses actor boundary") {
        shouldThrow<ObservationBoundaryException> {
            requireAccessible(CastSpell(player, spell, originalTargetSlots = TargetSlotPlan(emptyList())), setOf(player, spell))
        }.message shouldBe "Internal spell target history cannot cross actor boundary"
    }
})
