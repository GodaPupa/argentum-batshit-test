package com.wingedsheep.gym.ferocity

import kotlin.reflect.full.memberProperties

/** Reviewed question-data schemas. A new source field requires boundary review before use. */
internal object ActorQuestionSchemas {
    private val fields: Map<String, Set<String>> = mapOf(
        "AssignDamageDecision" to setOf("id", "playerId", "prompt", "context", "attackerId", "availablePower", "orderedTargets", "defenderId", "minimumAssignments", "defaultAssignments", "hasTrample", "hasDeathtouch"),
        "BatchYesNoDecision" to setOf("id", "playerId", "prompt", "context", "count", "yesText", "noText"),
        "BudgetModalDecision" to setOf("id", "playerId", "prompt", "context", "budget", "modes"),
        "BudgetModeOption" to setOf("cost", "description"),
        "ChooseColorDecision" to setOf("id", "playerId", "prompt", "context", "availableColors"),
        "ChooseModeDecision" to setOf("id", "playerId", "prompt", "context", "modes", "minModes", "maxModes"),
        "ChooseNumberDecision" to setOf("id", "playerId", "prompt", "context", "minValue", "maxValue"),
        "ChooseOptionDecision" to setOf("id", "playerId", "prompt", "context", "options", "defaultSearch", "optionCardIds", "optionMetadata", "canCancel"),
        "ChooseReplacementDecision" to setOf("id", "playerId", "prompt", "context", "fromOptions", "toOptions", "fromMetadata", "toMetadata", "allowedToByFrom", "defaultFromIndex"),
        "ChooseTargetsDecision" to setOf("id", "playerId", "prompt", "context", "targetRequirements", "legalTargets", "canCancel"),
        "CombatResolutionDecision" to setOf("id", "playerId", "prompt", "context", "firstStrike", "attackers", "blockers", "defenders", "edges", "coChooserId"),
        "ConditionalSelectionMinimum" to setOf("requiredSelections", "minimumSelections", "matchingOptions", "requiredMatches", "description"),
        "DamageEdge" to setOf("id", "sourceId", "targetId", "direction", "amount", "maximum", "lethal", "orderConstrained", "isTrampleDrain", "editableBy"),
        "DecisionContext" to setOf("sourceId", "sourceName", "phase", "triggeringEntityId", "inlineOnTrigger", "effectHint", "subjectEntityId", "abilityIdentity"),
        "DistributeDecision" to setOf("id", "playerId", "prompt", "context", "totalAmount", "targets", "minPerTarget", "maxPerTarget", "allowPartial"),
        "ManaSourceOption" to setOf("entityId", "name", "producesColors", "producesColorless", "requiresSacrifice", "requiresTappingAnotherPermanent", "manaAmount"),
        "ModeOption" to setOf("index", "text", "available"),
        "OptionMetadata" to setOf("id", "description", "iconKey"),
        "OrderObjectsDecision" to setOf("id", "playerId", "prompt", "context", "objects", "cardInfo"),
        "ReorderLibraryDecision" to setOf("id", "playerId", "prompt", "context", "cards", "cardInfo"),
        "ResolutionAttacker" to setOf("id", "name", "power", "toughness", "hasTrample", "hasDeathtouch", "hasFirstStrike", "hasDoubleStrike", "dealsDamageThisStep", "bandId", "attackedDefenderId", "blockedByIds", "markedDamage"),
        "ResolutionBlocker" to setOf("id", "name", "power", "toughness", "hasDeathtouch", "hasFirstStrike", "hasDoubleStrike", "dealsDamageThisStep", "blockedAttackerIds", "orderedAttackers", "markedDamage"),
        "ResolutionDefender" to setOf("id", "kind", "name", "lifeOrLoyaltyOrDefense"),
        "SearchCardInfo" to setOf("name", "manaCost", "typeLine", "imageUri", "colors", "power"),
        "SearchLibraryDecision" to setOf("id", "playerId", "prompt", "context", "options", "minSelections", "maxSelections", "cards", "filterDescription"),
        "SelectCardsDecision" to setOf("id", "playerId", "prompt", "context", "options", "minSelections", "maxSelections", "ordered", "cardInfo", "useTargetingUI", "selectedLabel", "remainderLabel", "nonSelectableOptions", "onePerCardType", "onePerColor", "availableColors", "onePerCardName", "onePerBasicLandType", "onePerPower", "maxTotalManaValue", "minTotalManaValue", "maxTotalPower", "conditionalMinimums"),
        "SelectManaSourcesDecision" to setOf("id", "playerId", "prompt", "context", "availableSources", "requiredCost", "autoPaySuggestion", "canDecline", "waterbendPermanents"),
        "SplitPilesDecision" to setOf("id", "playerId", "prompt", "context", "cards", "numberOfPiles", "pileLabels", "cardInfo"),
        "TargetRequirementInfo" to setOf("index", "description", "minTargets", "maxTargets", "sameOwner", "totalManaValueAtMost", "differentNames", "differentControllers"),
        "WaterbendPermanentChoice" to setOf("entityId", "name", "isCreature"),
        "YesNoDecision" to setOf("id", "playerId", "prompt", "context", "yesText", "noText", "hint"),
    )

    fun verifyIfKnown(value: Any): Boolean {
        val type = value::class
        if (type.qualifiedName != "com.wingedsheep.engine.core.${type.simpleName}") return false
        val expected = fields[type.simpleName] ?: return false
        if (type.memberProperties.map { it.name }.toSet() != expected) {
            fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Question data fields changed: ${type.simpleName}")
        }
        return true
    }
}
