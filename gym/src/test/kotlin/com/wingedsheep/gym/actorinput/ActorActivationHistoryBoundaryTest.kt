package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.targeting.TargetSlotPlan
import com.wingedsheep.engine.mechanics.targeting.TargetInstanceSelection
import com.wingedsheep.engine.mechanics.targeting.TargetSlot
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class ActorActivationHistoryBoundaryTest : FunSpec({
    val player = EntityId.of("actor")
    val source = EntityId.of("public-source")
    val action = ActivateAbility(playerId = player, sourceId = source, abilityId = AbilityId("fixture"))
    val allowed = setOf(player, source)
    fun rejectsHistory(value: ActivateAbility) {
        val error = shouldThrow<ObservationBoundaryException> { requireAccessible(value, allowed) }
        error.failure shouldBe BoundaryFailure.UNSUPPORTED_SCHEMA
        error.message shouldBe "Internal activation target history cannot cross actor boundary"
    }
    test("AH01 absent internal history preserves ordinary activation access") {
        requireAccessible(action, allowed)
        action.originalTargetSlots shouldBe null
    }
    test("AH02 ordinary activation still rejects inaccessible source") {
        shouldThrow<ObservationBoundaryException> {
            requireAccessible(action, setOf(player))
        }.failure shouldBe BoundaryFailure.INACCESSIBLE_REFERENCE
    }
    test("AH03 even empty internal history is rejected explicitly") {
        rejectsHistory(action.copy(originalTargetSlots = TargetSlotPlan(emptyList())))
    }
    test("AH04 public recipient cannot authorize internal history") {
        rejectsHistory(action.copy(originalTargetSlots = TargetSlotPlan(listOf(
            TargetInstanceSelection(listOf(TargetSlot(ChosenTarget.Player(player))))))))
    }
    test("AH05 serialized internal history remains rejected") {
        val forged = action.copy(originalTargetSlots = TargetSlotPlan(emptyList()))
        val encoded = Json.encodeToString(ActivateAbility.serializer(), forged)
        rejectsHistory(Json.decodeFromString(ActivateAbility.serializer(), encoded))
    }
})
