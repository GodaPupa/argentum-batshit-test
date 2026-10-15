package com.wingedsheep.sdk.serialization

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PreventTargetingSerializationTest : FunSpec({
    for (players in listOf(Player.EachOpponent, Player.Each, Player.You)) {
        test("targeting restriction round trips with $players and an explicit duration") {
            val original = Effects.PreventTargeting(EffectTarget.Controller, players, Duration.Permanent)
            val json = CardSerialization.json
            val encoded = json.encodeToString(Effect.serializer(), original)
            json.decodeFromString(Effect.serializer(), encoded) shouldBe original
        }
    }
})
