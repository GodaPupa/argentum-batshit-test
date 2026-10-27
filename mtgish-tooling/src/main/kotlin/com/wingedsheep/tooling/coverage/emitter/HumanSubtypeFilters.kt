package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.J
import kotlinx.serialization.json.JsonElement

// IsCreatureType names a subtype, not the Creature card type. Limit this repair to the
// two observed permanent-filter shapes; explicit card types and extra predicates retain
// their existing emitter paths. JsonElement equality also rejects extra annotations.
private val bareHuman = J.parseToJsonElement("""{"_Permanents":"IsCreatureType","args":"Human"}""")
private val anotherHumanYouControl = J.parseToJsonElement("""{"_Permanents":"And","args":[{"_Permanents":"Other","args":{"_Permanent":"ThisPermanent"}},{"_Permanents":"And","args":[{"_Permanents":"IsCreatureType","args":"Human"},{"_Permanents":"ControlledByAPlayer","args":{"_Players":"SinglePlayer","args":{"_Player":"You"}}}]}]}""")

internal fun humanBlockerFilter(node: JsonElement?): String? =
    if (node == bareHuman) "GameObjectFilter.Permanent.withSubtype(Subtype.HUMAN)" else null

internal fun humanEntryFilter(node: JsonElement?): String? =
    if (node == anotherHumanYouControl) "GameObjectFilter.Permanent.withSubtype(Subtype.HUMAN).youControl()" else null
