package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.CardDefinition
import kotlinx.serialization.Serializable

internal const val FEROCITY_BUNDLE_DEPENDENCY = "ferocity/runtime-card-definition-bundle/v1"
internal const val FEROCITY_CLASSPATH_DEPENDENCY = "ferocity/runtime-classpath/v1"
internal const val FEROCITY_JAVA_DEPENDENCY = "ferocity/runtime-java-executable/v1"

@Serializable
internal enum class FerocityDefinitionOriginKind {
    QUALIFIED_CANONICAL, PREDEFINED_TOKEN, VERIFIED_TEXT_PRERELEASE, DETERMINISTIC_FIXTURE,
}

@Serializable
internal data class FerocityDefinitionOrigin(
    val kind: FerocityDefinitionOriginKind,
    val dependencyKey: String,
    val sourceSha256: String,
)

@Serializable
internal data class FerocityArchivedDefinition(
    val definition: FerocityPayload,
    val origin: FerocityDefinitionOrigin,
)

/** Raw runtime definitions; wire JSON and generated AbilityIds are never normalized or regenerated. */
@Serializable
internal data class FerocityDefinitionBundle(
    val schemaVersion: Int = 1,
    val sourceCommit: String,
    val sourceTreeSha256: String,
    val serializerSha256: String,
    val definitionsInRegistrationOrder: List<FerocityArchivedDefinition>,
    val registryBindings: Map<String, String>,
    val externalDeckAliases: Map<String, String>,
)

internal data class FerocityRestoredDefinitions(
    val registry: CardRegistry,
    val bundle: FerocityDefinitionBundle,
) {
    fun resolveDeckName(sourceName: String): String {
        val lookup = bundle.externalDeckAliases[sourceName] ?: sourceName
        require(lookup in bundle.registryBindings) { "Unadmitted deck name or alias: $sourceName" }
        return lookup
    }
}

/** The caller supplies reviewed, finite source objects. No corpus discovery or fallback occurs here. */
internal fun captureFerocityDefinitions(
    orderedDefinitions: List<Pair<CardDefinition, FerocityDefinitionOrigin>>,
    sourceCommit: String,
    sourceTreeSha256: String,
    serializerSha256: String,
    externalDeckAliases: Map<String, String> = emptyMap(),
): FerocityDefinitionBundle {
    require(orderedDefinitions.isNotEmpty()) { "An exact nonempty definition pool is required" }
    val entries = orderedDefinitions.map { (card, origin) ->
        requireSupportedDefinitionIdentity(card)
        FerocityArchivedDefinition(FerocityJournalCodec.card(card), origin)
    }
    require(entries.map { it.definition.canonicalSha256 }.distinct().size == entries.size) {
        "Duplicate definition payload; declare its aliases against one archived entry"
    }
    val registry = CardRegistry()
    orderedDefinitions.forEach { registry.register(it.first) }
    val lookups = orderedDefinitions.flatMap { intrinsicDefinitionKeys(it.first) }.toSortedSet()
    val bindings = lookups.associateWith { FerocityJournalCodec.card(registry.requireCard(it)).canonicalSha256 }
    requireExternalAliases(externalDeckAliases, bindings.keys)
    return FerocityDefinitionBundle(1, sourceCommit, sourceTreeSha256, serializerSha256,
        entries, bindings, externalDeckAliases.toSortedMap())
}

/** Reconstruct only the archived definitions, then audit all names after every registration. */
internal fun restoreFerocityDefinitions(
    bundle: FerocityDefinitionBundle,
    admittedPins: FerocitySourcePins,
): FerocityRestoredDefinitions {
    require(bundle.schemaVersion == 1) { "Unsupported definition bundle schema" }
    require(bundle.sourceCommit == admittedPins.sourceCommit && bundle.sourceTreeSha256 == admittedPins.sourceTreeSha256 &&
        bundle.serializerSha256 == admittedPins.serializerSha256) { "Definition bundle source or serializer is not admitted" }
    require(bundle.registryBindings == admittedPins.cardDefinitionSha256) { "Definition lookup bindings differ from admission" }
    require(bundle.definitionsInRegistrationOrder.isNotEmpty()) { "Empty definition bundle" }
    require(bundle.definitionsInRegistrationOrder.size <= 512) { "Definition pool exceeds this bounded loader" }
    val registry = CardRegistry() // Intentionally no parent or live corpus overlay.
    val declaredKeys = mutableSetOf<String>()
    val seenPayloads = mutableSetOf<String>()
    bundle.definitionsInRegistrationOrder.forEach { entry ->
        val origin = entry.origin
        require(origin.dependencyKey.startsWith("ferocity/card-source/") && origin.dependencyKey.length > "ferocity/card-source/".length) {
            "Unrecognized definition source namespace"
        }
        requireSha256(origin.sourceSha256)
        require(admittedPins.dependencySha256[origin.dependencyKey] == origin.sourceSha256) {
            "Definition source is missing or differs from admission: ${origin.dependencyKey}"
        }
        val card = FerocityJournalCodec.restore(CardDefinition.serializer(), entry.definition)
        requireSupportedDefinitionIdentity(card)
        require(seenPayloads.add(entry.definition.canonicalSha256)) { "Duplicate archived definition" }
        declaredKeys.addAll(intrinsicDefinitionKeys(card))
        registry.register(card)
    }
    require(declaredKeys == bundle.registryBindings.keys) { "Missing or extra intrinsic definition lookup binding" }
    require(seenPayloads == bundle.registryBindings.values.toSet()) { "An archived definition has no admitted lookup" }
    require(registry.allCardNames() == declaredKeys.filterTo(mutableSetOf()) { key ->
        registry.getCard(key)?.name == key
    }) { "Implicit registry identity expansion is not admitted" }
    bundle.registryBindings.forEach { (lookup, expected) ->
        requireSha256(expected)
        require(FerocityJournalCodec.card(registry.requireCard(lookup)).canonicalSha256 == expected) {
            "Final registry alias differs from admitted payload: $lookup"
        }
    }
    requireExternalAliases(bundle.externalDeckAliases, declaredKeys)
    // The frozen runner makes its own private registry in sorted lookup order. Refuse an alias
    // arrangement that this existing admitted boundary cannot reproduce; do not alter its order.
    pinnedRegistry(registry, admittedPins)
    return FerocityRestoredDefinitions(registry, bundle)
}

private fun intrinsicDefinitionKeys(card: CardDefinition): Set<String> {
    val collector = card.metadata.collectorNumber
    val set = card.setCode
    return buildSet {
        add(card.name)
        if (collector != null) add(if (set != null) "${card.name}#$set-$collector" else "${card.name}#$collector")
    }
}

private fun requireSupportedDefinitionIdentity(card: CardDefinition) {
    require(card.name.isNotBlank())
    require(card.backFace == null && !card.meldResult) {
        "Double-faced or meld identity expansion needs a separately qualified definition bundle successor"
    }
    require(!card.name.startsWith("token:")) {
        "Inline runtime tokens need reviewed provenance; do not manufacture a registry CardDefinition"
    }
}

private fun requireExternalAliases(aliases: Map<String, String>, intrinsicKeys: Set<String>) {
    aliases.forEach { (source, target) ->
        require(source.isNotBlank() && source !in intrinsicKeys && target in intrinsicKeys) {
            "Ambiguous, shadowing, chained or unadmitted external deck alias: $source"
        }
    }
}
