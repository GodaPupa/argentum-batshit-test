package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.model.EntityId
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaGetter

internal fun decisionCardGroups(decision: PendingDecision): Set<EntityId> = when (decision) {
    is SelectCardsDecision -> (decision.options + decision.nonSelectableOptions).toSet()
    is SearchLibraryDecision -> decision.options.toSet()
    is ReorderLibraryDecision -> decision.cards.toSet()
    is OrderObjectsDecision -> decision.objects.toSet()
    is SplitPilesDecision -> decision.cards.toSet()
    else -> emptySet()
}

internal fun decisionCardInfo(decision: PendingDecision): Map<EntityId, SearchCardInfo> = when (decision) {
    is SelectCardsDecision -> decision.cardInfo.orEmpty()
    is SearchLibraryDecision -> decision.cards
    is ReorderLibraryDecision -> decision.cardInfo
    is OrderObjectsDecision -> decision.cardInfo.orEmpty()
    is SplitPilesDecision -> decision.cardInfo.orEmpty()
    else -> emptyMap()
}

/** All eighteen current question types retain their full constraints and choice domains. */
internal fun sanitizeDecision(
    decision: PendingDecision,
    features: Map<EntityId, EntityFeatures>,
    names: Map<EntityId, String>,
    allowedHandles: Set<EntityId>,
    triggerOrderProof: VerifiedTriggerOrderQuestion? = null,
): PendingDecision {
    fun name(id: EntityId): String = names[id]
        ?: fail(BoundaryFailure.INACCESSIBLE_REFERENCE, "Decision refers to an unavailable named object")
    val old = decision.context
    val context = old.copy(
        sourceName = old.sourceId?.let(::name) ?: triggerOrderProof?.sourceNameFor(decision),
        // This is optional UI auto-yield metadata, not a choice constraint. Its definition ID can
        // identify a face-down source; pilots bind answers to the current decision ID instead.
        abilityIdentity = null,
    )
    val oldSourceName = old.sourceName
    val safeSourceName = context.sourceName
    val prompt = if (oldSourceName != null && safeSourceName != null)
        decision.prompt.replace(oldSourceName, safeSourceName) else decision.prompt
    fun info(ids: Set<EntityId>): Map<EntityId, SearchCardInfo> = ids.sortedBy { it.value }.associateWith { id ->
        val card = features[id] ?: fail(BoundaryFailure.UNAUTHORIZED_LOOK, "Question card has no authorized view")
        SearchCardInfo(
            name = card.name,
            manaCost = card.manaCost,
            typeLine = card.types.sorted().joinToString(" ") +
                if (card.subtypes.isEmpty()) "" else " — " + card.subtypes.sorted().joinToString(" "),
            imageUri = null,
            colors = card.colors.map { color -> when (color) {
                "WHITE" -> "W"; "BLUE" -> "U"; "BLACK" -> "B"; "RED" -> "R"; "GREEN" -> "G"
                else -> fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Unrecognized projected color")
            } }.sorted(),
            power = card.power,
        )
    }
    val cardInfo = info(decisionCardInfo(decision).keys)
    val result: PendingDecision = when (decision) {
        is ChooseTargetsDecision -> decision.copy(context = context, prompt = prompt)
        is SelectCardsDecision -> decision.copy(context = context, prompt = prompt,
            options = decision.options.sortedBy { it.value },
            nonSelectableOptions = decision.nonSelectableOptions.sortedBy { it.value },
            cardInfo = if (decision.cardInfo == null) null else cardInfo)
        is YesNoDecision -> decision.copy(context = context, prompt = prompt)
        is BatchYesNoDecision -> decision.copy(context = context, prompt = prompt)
        is ChooseModeDecision -> decision.copy(context = context, prompt = prompt)
        is ChooseColorDecision -> decision.copy(context = context, prompt = prompt)
        is ChooseNumberDecision -> decision.copy(context = context, prompt = prompt)
        is DistributeDecision -> decision.copy(context = context, prompt = prompt)
        is OrderObjectsDecision -> decision.copy(context = context, prompt = prompt,
            cardInfo = if (decision.cardInfo == null) null else cardInfo)
        is SplitPilesDecision -> decision.copy(context = context, prompt = prompt,
            cardInfo = if (decision.cardInfo == null) null else cardInfo)
        is ChooseOptionDecision -> decision.copy(context = context, prompt = prompt)
        is ChooseReplacementDecision -> decision.copy(context = context, prompt = prompt)
        is AssignDamageDecision -> decision.copy(context = context, prompt = prompt)
        is SearchLibraryDecision -> decision.copy(context = context, prompt = prompt,
            options = decision.options.sortedBy { it.value }, cards = cardInfo)
        is ReorderLibraryDecision -> decision.copy(context = context, prompt = prompt, cardInfo = cardInfo)
        is SelectManaSourcesDecision -> decision.copy(context = context, prompt = prompt,
            availableSources = decision.availableSources.map { it.copy(name = name(it.entityId)) },
            waterbendPermanents = decision.waterbendPermanents.map { it.copy(name = name(it.entityId)) })
        is BudgetModalDecision -> decision.copy(context = context, prompt = prompt)
        is CombatResolutionDecision -> decision.copy(context = context, prompt = prompt,
            attackers = decision.attackers.map { it.copy(name = name(it.id)) },
            blockers = decision.blockers.map { it.copy(name = name(it.id)) },
            defenders = decision.defenders.map { it.copy(name = name(it.id)) })
    }
    requireAccessible(result, allowedHandles)
    return result
}

/**
 * Audit every nested entity reference, including alternative payments, target maps, modal costs,
 * sacrifice/discard domains, and decision metadata. Only constructor data in the audited data
 * namespaces can cross. State/continuation/registry/environment/callback objects fail visibly.
 */
internal fun requireAccessible(value: Any?, allowed: Set<EntityId>) {
    fun visit(item: Any?) {
        when (item) {
            null, is String, is Number, is Boolean, is Char -> return
            is Enum<*> -> ActorPayloadSchemas.verifyEnum(item)
            is EntityId -> if (item !in allowed)
                fail(BoundaryFailure.INACCESSIBLE_REFERENCE, "Actor payload contains an inaccessible entity reference")
            is Map<*, *> -> item.forEach { (key, entry) -> visit(key); visit(entry) }
            is Iterable<*> -> item.forEach(::visit)
            else -> {
                ActorPayloadSchemas.verify(item)
                val type = item::class
                if ((!type.isData && !type.isValue && type.objectInstance == null) ||
                    item is ContinuationFrame || item is AnswerContinuation || item is Function<*>) {
                    fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Actor payload contains a non-data type")
                }
                val constructorFields = type.primaryConstructor?.parameters?.mapNotNull { it.name }.orEmpty().toSet()
                type.memberProperties.filter { it.name in constructorFields }.forEach { field ->
                    // Kotlin reflection can box a null nullable inline value as EntityId(null)
                    // instead of returning null. Read the JVM getter's actual null first; a
                    // non-null value still follows the same typed, reference-checked traversal.
                    val nullableInline = field.returnType.isMarkedNullable &&
                        (field.returnType.classifier as? KClass<*>)?.isValue == true
                    val absentInline = if (nullableInline) {
                        val getter = field.javaGetter ?: fail(BoundaryFailure.UNSUPPORTED_SCHEMA,
                            "Nullable inline actor field has no audited JVM getter")
                        getter.invoke(item) == null
                    } else false
                    visit(if (absentInline) null else field.getter.call(item))
                }
            }
        }
    }
    visit(value)
}
