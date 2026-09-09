package com.wingedsheep.gym

import com.wingedsheep.engine.core.AbilityTriggeredEvent
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmokeTriggerTelemetryTest : FunSpec({

    test("Guttersnipe combat damage does not become trigger damage") {
        val telemetry = SmokeTriggerTelemetry()

        telemetry.record(damage("Guttersnipe", amount = 2, combat = true))

        telemetry.guttersnipeTriggers shouldBe 0
        telemetry.guttersnipeDamage shouldBe 0
    }

    test("Flamebreather combat and triggered damage remain separate") {
        val telemetry = SmokeTriggerTelemetry()

        telemetry.record(damage("Kessig Flamebreather", amount = 1, combat = true))
        telemetry.record(trigger("Kessig Flamebreather"))
        telemetry.record(damage("Kessig Flamebreather", amount = 1, combat = false))

        telemetry.flamebreatherTriggers shouldBe 1
        telemetry.flamebreatherDamage shouldBe 1
    }

    test("an unrelated trigger granted to Flamebreather is not counted as its damage trigger") {
        val telemetry = SmokeTriggerTelemetry()

        telemetry.record(
            trigger(
                "Kessig Flamebreather",
                "When this creature dies, return it to the battlefield tapped under its owner's control.",
            )
        )

        telemetry.flamebreatherTriggers shouldBe 0
        telemetry.flamebreatherDamage shouldBe 0
    }

    test("created trigger is counted when the game ends before its damage resolves") {
        val telemetry = SmokeTriggerTelemetry()

        telemetry.record(trigger("Guttersnipe"))
        telemetry.record(GameEndedEvent(winnerId = PLAYER, reason = GameEndReason.LIFE_ZERO))

        telemetry.guttersnipeTriggers shouldBe 1
        telemetry.guttersnipeDamage shouldBe 0
    }
})

private val SOURCE = EntityId("source")
private val PLAYER = EntityId("player")

private fun trigger(
    sourceName: String,
    description: String = when (sourceName) {
        "Kessig Flamebreather" -> "you casts a noncreature spell, deal 1 damage to each opponent."
        "Guttersnipe" -> "you casts a instant or sorcery spell, deal 2 damage to each opponent."
        else -> "test trigger"
    },
) = AbilityTriggeredEvent(
    sourceId = SOURCE,
    sourceName = sourceName,
    controllerId = PLAYER,
    description = description,
)

private fun damage(sourceName: String, amount: Int, combat: Boolean) = DamageDealtEvent(
    sourceId = SOURCE,
    targetId = PLAYER,
    amount = amount,
    isCombatDamage = combat,
    sourceName = sourceName,
    targetIsPlayer = true,
)
