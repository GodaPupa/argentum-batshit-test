package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.legalactions.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlin.reflect.full.memberProperties

/**
 * Detached, serializable copies of the complete audited legal-action parameter domain.
 * Engine LegalAction is deliberately not handed to a pilot. Source-schema checks fail visibly
 * if an engine field is added before this projection is reviewed and extended.
 */

@Serializable
data class ActorLegalAction(
    val action: GameAction,
    val actionType: String,
    val description: String,
    val affordable: Boolean = true,
    val validTargets: List<EntityId>? = null,
    val requiresTargets: Boolean = false,
    val targetCount: Int = 1,
    val minTargets: Int = targetCount,
    val targetDescription: String? = null,
    val targetRequirements: List<ActorTargetInfo>? = null,
    val xConstrainsTargetManaValue: Boolean = false,
    val xConstrainsTargetManaValueExactly: Boolean = false,
    val xConstrainsTargetPower: Boolean = false,
    val xConstrainsTargetCount: Boolean = false,
    val validAttackers: List<EntityId>? = null,
    val mandatoryAttackers: List<EntityId>? = null,
    val validAttackTargets: List<EntityId>? = null,
    val validBlockers: List<EntityId>? = null,
    val blockerMaxBlockCounts: Map<EntityId, Int>? = null,
    val mandatoryBlockerAssignments: Map<EntityId, List<EntityId>>? = null,
    val manaCostString: String? = null,
    val manaCostPerExtraTarget: String? = null,
    val hasXCost: Boolean = false,
    val maxAffordableX: Int? = null,
    val minX: Int = 0,
    val additionalCostInfo: ActorAdditionalCostData? = null,
    val hasConvoke: Boolean = false,
    val convokeCreatures: List<ActorConvokeCreatureData>? = null,
    val hasDelve: Boolean = false,
    val delveCards: List<ActorDelveCardData>? = null,
    val minDelveNeeded: Int? = null,
    val hasTapForGeneric: Boolean = false,
    val tapForGenericPermanents: List<ActorTapForGenericPermanentData>? = null,
    val tapForGenericAmount: Int? = null,
    val tapForGenericLabel: String? = null,
    val tapForGenericRequired: Boolean? = null,
    val hasHarmonize: Boolean = false,
    val harmonizeCreatures: List<ActorHarmonizeCreatureData>? = null,
    val isManaAbility: Boolean = false,
    val requiresManaColorChoice: Boolean = false,
    val availableManaColors: List<Color>? = null,
    val autoTapPreview: List<EntityId>? = null,
    val requiresDamageDistribution: Boolean = false,
    val totalDamageToDistribute: Int? = null,
    val minDamagePerTarget: Int? = null,
    val sourceZone: String? = null,
    val castsTransformed: Boolean = false,
    val tapForPower: Boolean = false,
    val tapForPowerRequired: Int? = null,
    val tapForPowerCreatures: List<ActorTapForPowerCreatureData>? = null,
    val maxRepeatableActivations: Int? = null,
    val requiresForage: Boolean = false,
    val additionalLifeCost: Int = 0,
    val modalEnumeration: ActorModalLegalEnumeration? = null,
    val holdPriority: Boolean = false,
)

@Serializable
data class ActorModalLegalEnumeration(
    val chooseCount: Int,
    val minChooseCount: Int,
    val allowRepeat: Boolean,
    val additionalManaCostPerExtraMode: String? = null,
    val additionalCostPerExtraMode: ActorAdditionalCostData? = null,
    val modes: List<ActorModalEnumerationMode>,
    val unavailableIndices: List<Int>,
)

@Serializable
data class ActorModalEnumerationMode(
    val index: Int,
    val description: String,
    val available: Boolean,
    val additionalManaCost: String? = null,
    val additionalCostInfo: ActorAdditionalCostData? = null,
    val targetRequirements: List<ActorTargetInfo> = emptyList(),
)

@Serializable
data class ActorTargetInfo(
    val index: Int,
    val description: String,
    val minTargets: Int,
    val maxTargets: Int,
    val validTargets: List<EntityId>,
    val targetZone: String? = null,
    val mustDifferFromEarlier: Boolean = false,
    val xConstrainsManaValue: Boolean = false,
    val xConstrainsManaValueExactly: Boolean = false,
    val xConstrainsPower: Boolean = false,
    val xConstrainsCount: Boolean = false,
)

@Serializable
data class ActorConvokeCreatureData(
    val entityId: EntityId,
    val name: String,
    val colors: Set<Color>,
)

@Serializable
data class ActorTapForGenericPermanentData(
    val entityId: EntityId,
    val name: String,
    val isCreature: Boolean,
)

@Serializable
data class ActorHarmonizeCreatureData(
    val entityId: EntityId,
    val name: String,
    val power: Int,
)

@Serializable
data class ActorDelveCardData(
    val entityId: EntityId,
    val name: String,
    val imageUri: String? = null,
)

@Serializable
data class ActorTapForPowerCreatureData(
    val entityId: EntityId,
    val name: String,
    val power: Int,
    val canAttack: Boolean = true,
)

@Serializable
data class ActorAdditionalCostData(
    val description: String,
    val costType: String,
    val validSacrificeTargets: List<EntityId> = emptyList(),
    val sacrificeCount: Int = 1,
    val costAfterSacrifice: Map<EntityId, String> = emptyMap(),
    val validTapTargets: List<EntityId> = emptyList(),
    val tapCount: Int = 0,
    val tapBatchMaxActivations: Int = 1,
    val validDiscardTargets: List<EntityId> = emptyList(),
    val discardCount: Int = 0,
    val validBounceTargets: List<EntityId> = emptyList(),
    val bounceCount: Int = 0,
    val validExileTargets: List<EntityId> = emptyList(),
    val exileMinCount: Int = 0,
    val exileMaxCount: Int = 0,
    val exileMinTotalWeight: Int = 0,
    val exileCardWeights: Map<EntityId, Int> = emptyMap(),
    val exileWeightUnit: String = "",
    val exileWeightPerTarget: Map<EntityId, Int> = emptyMap(),
    val validBeholdTargets: List<EntityId> = emptyList(),
    val beholdCount: Int = 0,
    val validRevealTargets: List<EntityId> = emptyList(),
    val revealCount: Int = 0,
    val counterRemovalCreatures: List<ActorCounterRemovalCreatureData> = emptyList(),
    val validBlightTargets: List<EntityId> = emptyList(),
    val blightAmount: Int = 0,
    val blightVariableMaxX: Int = 0,
    val payXLifeMaxX: Int = 0,
    val distributedCounterRemovalTotal: Int = 0,
    val validCraftMaterials: List<EntityId> = emptyList(),
    val craftMinCount: Int = 1,
    val craftMaxCount: Int? = null,
    val tapForPowerCreatures: List<ActorTapForPowerCreatureData> = emptyList(),
    val tapForPowerRequired: Int = 0,
)

@Serializable
data class ActorCounterRemovalCreatureData(
    val entityId: EntityId,
    val name: String,
    val availableCounters: Int,
    val availableCountersByType: Map<String, Int> = emptyMap(),
    val imageUri: String? = null,
)

internal fun LegalAction.actorCopy(): ActorLegalAction {
    val expected = setOf("action", "actionType", "description", "affordable", "validTargets", "requiresTargets", "targetCount", "minTargets", "targetDescription", "targetRequirements", "xConstrainsTargetManaValue", "xConstrainsTargetManaValueExactly", "xConstrainsTargetPower", "xConstrainsTargetCount", "validAttackers", "mandatoryAttackers", "validAttackTargets", "validBlockers", "blockerMaxBlockCounts", "mandatoryBlockerAssignments", "manaCostString", "manaCostPerExtraTarget", "hasXCost", "maxAffordableX", "minX", "additionalCostInfo", "hasConvoke", "convokeCreatures", "hasDelve", "delveCards", "minDelveNeeded", "hasTapForGeneric", "tapForGenericPermanents", "tapForGenericAmount", "tapForGenericLabel", "tapForGenericRequired", "hasHarmonize", "harmonizeCreatures", "isManaAbility", "requiresManaColorChoice", "availableManaColors", "autoTapPreview", "requiresDamageDistribution", "totalDamageToDistribute", "minDamagePerTarget", "sourceZone", "castsTransformed", "tapForPower", "tapForPowerRequired", "tapForPowerCreatures", "maxRepeatableActivations", "requiresForage", "additionalLifeCost", "modalEnumeration", "holdPriority", "isAffordableAction", "additionalCostType", "hasUnfillableTargetRequirement")
    if (LegalAction::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "LegalAction fields changed")
    }
    return ActorLegalAction(
        action = action,
        actionType = actionType,
        description = description,
        affordable = affordable,
        validTargets = validTargets,
        requiresTargets = requiresTargets,
        targetCount = targetCount,
        minTargets = minTargets,
        targetDescription = targetDescription,
        targetRequirements = targetRequirements?.map { it.actorCopy() },
        xConstrainsTargetManaValue = xConstrainsTargetManaValue,
        xConstrainsTargetManaValueExactly = xConstrainsTargetManaValueExactly,
        xConstrainsTargetPower = xConstrainsTargetPower,
        xConstrainsTargetCount = xConstrainsTargetCount,
        validAttackers = validAttackers,
        mandatoryAttackers = mandatoryAttackers,
        validAttackTargets = validAttackTargets,
        validBlockers = validBlockers,
        blockerMaxBlockCounts = blockerMaxBlockCounts,
        mandatoryBlockerAssignments = mandatoryBlockerAssignments,
        manaCostString = manaCostString,
        manaCostPerExtraTarget = manaCostPerExtraTarget,
        hasXCost = hasXCost,
        maxAffordableX = maxAffordableX,
        minX = minX,
        additionalCostInfo = additionalCostInfo?.actorCopy(),
        hasConvoke = hasConvoke,
        convokeCreatures = convokeCreatures?.map { it.actorCopy() },
        hasDelve = hasDelve,
        delveCards = delveCards?.map { it.actorCopy() },
        minDelveNeeded = minDelveNeeded,
        hasTapForGeneric = hasTapForGeneric,
        tapForGenericPermanents = tapForGenericPermanents?.map { it.actorCopy() },
        tapForGenericAmount = tapForGenericAmount,
        tapForGenericLabel = tapForGenericLabel,
        tapForGenericRequired = tapForGenericRequired,
        hasHarmonize = hasHarmonize,
        harmonizeCreatures = harmonizeCreatures?.map { it.actorCopy() },
        isManaAbility = isManaAbility,
        requiresManaColorChoice = requiresManaColorChoice,
        availableManaColors = availableManaColors,
        autoTapPreview = autoTapPreview,
        requiresDamageDistribution = requiresDamageDistribution,
        totalDamageToDistribute = totalDamageToDistribute,
        minDamagePerTarget = minDamagePerTarget,
        sourceZone = sourceZone,
        castsTransformed = castsTransformed,
        tapForPower = tapForPower,
        tapForPowerRequired = tapForPowerRequired,
        tapForPowerCreatures = tapForPowerCreatures?.map { it.actorCopy() },
        maxRepeatableActivations = maxRepeatableActivations,
        requiresForage = requiresForage,
        additionalLifeCost = additionalLifeCost,
        modalEnumeration = modalEnumeration?.actorCopy(),
        holdPriority = holdPriority,
    )
}

internal fun ModalLegalEnumeration.actorCopy(): ActorModalLegalEnumeration {
    val expected = setOf("chooseCount", "minChooseCount", "allowRepeat", "additionalManaCostPerExtraMode", "additionalCostPerExtraMode", "modes", "unavailableIndices")
    if (ModalLegalEnumeration::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "ModalLegalEnumeration fields changed")
    }
    return ActorModalLegalEnumeration(
        chooseCount = chooseCount,
        minChooseCount = minChooseCount,
        allowRepeat = allowRepeat,
        additionalManaCostPerExtraMode = additionalManaCostPerExtraMode,
        additionalCostPerExtraMode = additionalCostPerExtraMode?.actorCopy(),
        modes = modes.map { it.actorCopy() },
        unavailableIndices = unavailableIndices,
    )
}

internal fun ModalEnumerationMode.actorCopy(): ActorModalEnumerationMode {
    val expected = setOf("index", "description", "available", "additionalManaCost", "additionalCostInfo", "targetRequirements")
    if (ModalEnumerationMode::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "ModalEnumerationMode fields changed")
    }
    return ActorModalEnumerationMode(
        index = index,
        description = description,
        available = available,
        additionalManaCost = additionalManaCost,
        additionalCostInfo = additionalCostInfo?.actorCopy(),
        targetRequirements = targetRequirements.map { it.actorCopy() },
    )
}

internal fun TargetInfo.actorCopy(): ActorTargetInfo {
    val expected = setOf("index", "description", "minTargets", "maxTargets", "validTargets", "targetZone", "mustDifferFromEarlier", "xConstrainsManaValue", "xConstrainsManaValueExactly", "xConstrainsPower", "xConstrainsCount")
    if (TargetInfo::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "TargetInfo fields changed")
    }
    return ActorTargetInfo(
        index = index,
        description = description,
        minTargets = minTargets,
        maxTargets = maxTargets,
        validTargets = validTargets,
        targetZone = targetZone,
        mustDifferFromEarlier = mustDifferFromEarlier,
        xConstrainsManaValue = xConstrainsManaValue,
        xConstrainsManaValueExactly = xConstrainsManaValueExactly,
        xConstrainsPower = xConstrainsPower,
        xConstrainsCount = xConstrainsCount,
    )
}

internal fun ConvokeCreatureData.actorCopy(): ActorConvokeCreatureData {
    val expected = setOf("entityId", "name", "colors")
    if (ConvokeCreatureData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "ConvokeCreatureData fields changed")
    }
    return ActorConvokeCreatureData(
        entityId = entityId,
        name = name,
        colors = colors,
    )
}

internal fun TapForGenericPermanentData.actorCopy(): ActorTapForGenericPermanentData {
    val expected = setOf("entityId", "name", "isCreature")
    if (TapForGenericPermanentData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "TapForGenericPermanentData fields changed")
    }
    return ActorTapForGenericPermanentData(
        entityId = entityId,
        name = name,
        isCreature = isCreature,
    )
}

internal fun HarmonizeCreatureData.actorCopy(): ActorHarmonizeCreatureData {
    val expected = setOf("entityId", "name", "power")
    if (HarmonizeCreatureData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "HarmonizeCreatureData fields changed")
    }
    return ActorHarmonizeCreatureData(
        entityId = entityId,
        name = name,
        power = power,
    )
}

internal fun DelveCardData.actorCopy(): ActorDelveCardData {
    val expected = setOf("entityId", "name", "imageUri")
    if (DelveCardData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "DelveCardData fields changed")
    }
    return ActorDelveCardData(
        entityId = entityId,
        name = name,
        imageUri = imageUri,
    )
}

internal fun TapForPowerCreatureData.actorCopy(): ActorTapForPowerCreatureData {
    val expected = setOf("entityId", "name", "power", "canAttack")
    if (TapForPowerCreatureData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "TapForPowerCreatureData fields changed")
    }
    return ActorTapForPowerCreatureData(
        entityId = entityId,
        name = name,
        power = power,
        canAttack = canAttack,
    )
}

internal fun AdditionalCostData.actorCopy(): ActorAdditionalCostData {
    val expected = setOf("description", "costType", "validSacrificeTargets", "sacrificeCount", "costAfterSacrifice", "validTapTargets", "tapCount", "tapBatchMaxActivations", "validDiscardTargets", "discardCount", "validBounceTargets", "bounceCount", "validExileTargets", "exileMinCount", "exileMaxCount", "exileMinTotalWeight", "exileCardWeights", "exileWeightUnit", "exileWeightPerTarget", "validBeholdTargets", "beholdCount", "validRevealTargets", "revealCount", "counterRemovalCreatures", "validBlightTargets", "blightAmount", "blightVariableMaxX", "payXLifeMaxX", "distributedCounterRemovalTotal", "validCraftMaterials", "craftMinCount", "craftMaxCount", "tapForPowerCreatures", "tapForPowerRequired")
    if (AdditionalCostData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "AdditionalCostData fields changed")
    }
    return ActorAdditionalCostData(
        description = description,
        costType = costType,
        validSacrificeTargets = validSacrificeTargets,
        sacrificeCount = sacrificeCount,
        costAfterSacrifice = costAfterSacrifice,
        validTapTargets = validTapTargets,
        tapCount = tapCount,
        tapBatchMaxActivations = tapBatchMaxActivations,
        validDiscardTargets = validDiscardTargets,
        discardCount = discardCount,
        validBounceTargets = validBounceTargets,
        bounceCount = bounceCount,
        validExileTargets = validExileTargets,
        exileMinCount = exileMinCount,
        exileMaxCount = exileMaxCount,
        exileMinTotalWeight = exileMinTotalWeight,
        exileCardWeights = exileCardWeights,
        exileWeightUnit = exileWeightUnit,
        exileWeightPerTarget = exileWeightPerTarget,
        validBeholdTargets = validBeholdTargets,
        beholdCount = beholdCount,
        validRevealTargets = validRevealTargets,
        revealCount = revealCount,
        counterRemovalCreatures = counterRemovalCreatures.map { it.actorCopy() },
        validBlightTargets = validBlightTargets,
        blightAmount = blightAmount,
        blightVariableMaxX = blightVariableMaxX,
        payXLifeMaxX = payXLifeMaxX,
        distributedCounterRemovalTotal = distributedCounterRemovalTotal,
        validCraftMaterials = validCraftMaterials,
        craftMinCount = craftMinCount,
        craftMaxCount = craftMaxCount,
        tapForPowerCreatures = tapForPowerCreatures.map { it.actorCopy() },
        tapForPowerRequired = tapForPowerRequired,
    )
}

internal fun CounterRemovalCreatureData.actorCopy(): ActorCounterRemovalCreatureData {
    val expected = setOf("entityId", "name", "availableCounters", "availableCountersByType", "imageUri")
    if (CounterRemovalCreatureData::class.memberProperties.map { it.name }.toSet() != expected) {
        throw ObservationBoundaryException(BoundaryFailure.UNSUPPORTED_SCHEMA, "CounterRemovalCreatureData fields changed")
    }
    return ActorCounterRemovalCreatureData(
        entityId = entityId,
        name = name,
        availableCounters = availableCounters,
        availableCountersByType = availableCountersByType,
        imageUri = imageUri,
    )
}
