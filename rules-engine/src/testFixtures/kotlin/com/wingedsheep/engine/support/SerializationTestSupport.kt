package com.wingedsheep.engine.support

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Test-only serialization bridge for scenario modules that consume the engine test fixtures. */
object SerializationTestSupport {
    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
    }

    fun roundTrip(state: GameState): GameState =
        json.decodeFromString(json.encodeToString(state))
}
