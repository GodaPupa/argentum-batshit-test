package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.CardDefinition
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Trusted recorder codec; never handed to a pilot. Unknown fields and subtypes fail. */
internal object FerocityJournalCodec {
    val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun <T> payload(serializer: KSerializer<T>, value: T): FerocityPayload {
        val wire = json.encodeToString(serializer, value)
        return FerocityPayload(wire, sha(wire), sha(canonicalElement(json.parseToJsonElement(wire)).toString()))
    }

    fun <T> restore(serializer: KSerializer<T>, payload: FerocityPayload): T {
        requireSha256(payload.wireSha256)
        requireSha256(payload.canonicalSha256)
        require(sha(payload.wireJson) == payload.wireSha256) { "Wire payload digest mismatch" }
        require(sha(canonicalElement(json.parseToJsonElement(payload.wireJson)).toString()) == payload.canonicalSha256) {
            "Canonical payload digest mismatch"
        }
        val value = json.decodeFromString(serializer, payload.wireJson)
        // Strict current-format round trip additionally rejects silent migration/defaulting.
        require(json.encodeToString(serializer, value) == payload.wireJson) { "Payload is not an exact current-format round trip" }
        return value
    }

    fun state(value: GameState) = payload(GameState.serializer(), value)
    fun state(value: FerocityPayload) = restore(GameState.serializer(), value)
    fun action(value: GameAction) = payload(GameAction.serializer(), value)
    fun action(value: FerocityPayload) = restore(GameAction.serializer(), value)
    fun events(value: List<GameEvent>) = payload(ListSerializer(GameEvent.serializer()), value)
    fun events(value: FerocityPayload) = restore(ListSerializer(GameEvent.serializer()), value)
    fun card(value: CardDefinition) = payload(CardDefinition.serializer(), value)

    fun <T> canonical(serializer: KSerializer<T>, value: T): String =
        canonicalElement(json.encodeToJsonElement(serializer, value)).toString()

    fun claim(value: FerocityClaim): String = canonical(FerocityClaim.serializer(), value)

    fun envelope(index: Int, previous: String, record: FerocityJournalRecord): FerocityJournalEnvelope {
        val unsigned = FerocityJournalEnvelope(index, previous, record)
        return unsigned.copy(sha256 = sha(canonical(FerocityJournalEnvelope.serializer(), unsigned)))
    }

    fun envelopeLine(value: FerocityJournalEnvelope): String = canonical(FerocityJournalEnvelope.serializer(), value)

    fun readEnvelope(line: String): FerocityJournalEnvelope {
        val value = json.decodeFromString(FerocityJournalEnvelope.serializer(), line)
        require(envelopeLine(value) == line) { "Journal line is not canonical current-format JSON" }
        require(envelope(value.index, value.previousSha256, value.record).sha256 == value.sha256) { "Journal record digest mismatch" }
        return value
    }

    fun readClaim(text: String): FerocityClaim {
        val value = json.decodeFromString(FerocityClaim.serializer(), text)
        require(value.schemaVersion == 1 && claim(value) == text) { "Unsupported or noncanonical trial claim" }
        return value
    }

    fun sha(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun canonicalElement(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonicalElement(it.value) })
        is JsonArray -> JsonArray(value.map(::canonicalElement))
        else -> value
    }
}
