package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.GameAction
import kotlinx.serialization.json.*

/** Disposable qualification overlay. Never an authority or production dependency. */
object R6DiffObserver {
    private class Capture {
        val events = mutableListOf<JsonObject>()
        val counts = linkedMapOf<String, Long>()
        var overflow = false
    }
    private val current = ThreadLocal<Capture?>()
    fun begin() { check(current.get() == null); current.set(Capture()) }
    fun count(name: String) { current.get()?.let { it.counts[name] = (it.counts[name] ?: 0L) + 1L } }
    fun event(kind: String, body: JsonElement) {
        current.get()?.let {
            if (it.events.size >= 100000) it.overflow = true
            else it.events.add(buildJsonObject { put("kind", kind); put("body", body) })
        }
    }
    fun raw(actions: List<GameAction>, scores: List<Double>) {
        event("raw", buildJsonObject {
            put("actions", JsonArray(actions.map { Json.encodeToJsonElement(GameAction.serializer(), it) }))
            put("bits", JsonArray(scores.map { JsonPrimitive(it.toRawBits().toString()) }))
        })
    }
    fun result(land: String?, setup: String?, focal: String?, score: Double?, comparison: Double?) {
        event("search", buildJsonObject {
            put("null", land == null)
            put("land", land?.let(::JsonPrimitive) ?: JsonNull)
            put("setup", setup?.let(::JsonPrimitive) ?: JsonNull)
            put("focal", focal?.let(::JsonPrimitive) ?: JsonNull)
            put("scoreBits", score?.let { JsonPrimitive(it.toRawBits().toString()) } ?: JsonNull)
            put("comparisonBits", comparison?.let { JsonPrimitive(it.toRawBits().toString()) } ?: JsonNull)
        })
    }
    fun end(): JsonObject {
        val c = checkNotNull(current.get()); current.remove()
        return buildJsonObject {
            put("events", JsonArray(c.events))
            put("counts", JsonObject(c.counts.mapValues { JsonPrimitive(it.value) }))
            put("overflow", c.overflow)
        }
    }
}
