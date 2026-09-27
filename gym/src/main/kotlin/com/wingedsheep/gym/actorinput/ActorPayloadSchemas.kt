package com.wingedsheep.gym.actorinput

import kotlin.reflect.full.memberProperties

/** Exact audited payload shapes. No package-wide or unknown-type fallback is permitted. */
internal object ActorPayloadSchemas {
    private val fields: Map<String, Set<String>> = mapOf(
        "com.wingedsheep.engine.core.ActivateAbility" to setOf("playerId", "sourceId", "abilityId", "targets", "costPayment", "manaColorChoice", "xValue", "repeatCount", "paymentStrategy", "alternativePayment", "damageDistribution", "opponentTargetsChosen"),
        "com.wingedsheep.engine.core.BatchYesNoResponse" to setOf("decisionId", "choice", "applyToAll"),
        "com.wingedsheep.engine.core.BottomCards" to setOf("playerId", "cardIds"),
        "com.wingedsheep.engine.core.BudgetModalResponse" to setOf("decisionId", "selectedModeIndices"),
        "com.wingedsheep.engine.core.CancelDecisionResponse" to setOf("decisionId"),
        "com.wingedsheep.engine.core.CardsSelectedResponse" to setOf("decisionId", "selectedCards"),
        "com.wingedsheep.engine.core.CastSpell" to setOf("playerId", "cardId", "targets", "xValue", "paymentStrategy", "alternativePayment", "additionalCostPayment", "castFaceDown", "declaredCostSlot", "wasWaterbendPaid", "giftRecipient", "splicedCardIds", "damageDistribution", "castForPrototype", "useAlternativeCost", "chosenModes", "modalSelectionCompleted", "modeTargetsOrdered", "modeDamageDistribution", "graveyardLifeCost", "graveyardCastRider", "conspiredCreatures", "casualtyCreature", "faceIndex", "useWithoutPayingManaCost", "alternativeCostType"),
        "com.wingedsheep.engine.core.ChooseManaColor" to setOf("playerId", "color"),
        "com.wingedsheep.engine.core.ColorChosenResponse" to setOf("decisionId", "color"),
        "com.wingedsheep.engine.core.CombatResolutionResponse" to setOf("decisionId", "edges", "orderedBlockers", "orderedAttackers"),
        "com.wingedsheep.engine.core.Concede" to setOf("playerId"),
        "com.wingedsheep.engine.core.CrewVehicle" to setOf("playerId", "vehicleId", "crewCreatures"),
        "com.wingedsheep.engine.core.CycleCard" to setOf("playerId", "cardId", "paymentStrategy", "xValue"),
        "com.wingedsheep.engine.core.DamageAssignmentResponse" to setOf("decisionId", "assignments"),
        "com.wingedsheep.engine.core.DamageEdgeAmount" to setOf("edgeId", "amount"),
        "com.wingedsheep.engine.core.DeclareAttackers" to setOf("playerId", "attackers", "bands"),
        "com.wingedsheep.engine.core.DeclareBlockers" to setOf("playerId", "blockers"),
        "com.wingedsheep.engine.core.DistributionResponse" to setOf("decisionId", "distribution"),
        "com.wingedsheep.engine.core.ForetellCard" to setOf("playerId", "cardId", "paymentStrategy"),
        "com.wingedsheep.engine.core.GraveyardCastRiderSelection" to setOf("entersWithCounter", "addedSubtype", "exileInsteadOfGraveyard"),
        "com.wingedsheep.engine.core.KeepHand" to setOf("playerId"),
        "com.wingedsheep.engine.core.ManaSourcesSelectedResponse" to setOf("decisionId", "selectedSources", "autoPay", "waterbendPermanents", "declined"),
        "com.wingedsheep.engine.core.ModesChosenResponse" to setOf("decisionId", "selectedModes"),
        "com.wingedsheep.engine.core.NumberChosenResponse" to setOf("decisionId", "number"),
        "com.wingedsheep.engine.core.OptionChosenResponse" to setOf("decisionId", "optionIndex"),
        "com.wingedsheep.engine.core.OrderBlockers" to setOf("playerId", "attackerId", "orderedBlockers"),
        "com.wingedsheep.engine.core.OrderedResponse" to setOf("decisionId", "orderedObjects"),
        "com.wingedsheep.engine.core.PassPriority" to setOf("playerId"),
        "com.wingedsheep.engine.core.PaymentStrategy.AutoPay" to setOf(),
        "com.wingedsheep.engine.core.PaymentStrategy.Explicit" to setOf("manaAbilitiesToActivate", "phyrexianLifePayments"),
        "com.wingedsheep.engine.core.PaymentStrategy.FromPool" to setOf(),
        "com.wingedsheep.engine.core.PilesSplitResponse" to setOf("decisionId", "piles"),
        "com.wingedsheep.engine.core.PlayLand" to setOf("playerId", "cardId", "asBackFace"),
        "com.wingedsheep.engine.core.PlotCard" to setOf("playerId", "cardId", "paymentStrategy"),
        "com.wingedsheep.engine.core.ReplacementChosenResponse" to setOf("decisionId", "fromIndex", "toIndex"),
        "com.wingedsheep.engine.core.SaddleMount" to setOf("playerId", "mountId", "saddleCreatures"),
        "com.wingedsheep.engine.core.SubmitDecision" to setOf("playerId", "response"),
        "com.wingedsheep.engine.core.SuspendCardFromHand" to setOf("playerId", "cardId", "paymentStrategy"),
        "com.wingedsheep.engine.core.TakeMulligan" to setOf("playerId"),
        "com.wingedsheep.engine.core.TargetsResponse" to setOf("decisionId", "selectedTargets"),
        "com.wingedsheep.engine.core.TurnFaceUp" to setOf("playerId", "sourceId", "paymentStrategy", "costTargetIds", "xValue", "procedureIndex"),
        "com.wingedsheep.engine.core.TypecycleCard" to setOf("playerId", "cardId", "paymentStrategy"),
        "com.wingedsheep.engine.core.UnlockRoomDoor" to setOf("playerId", "roomId", "faceId", "paymentStrategy"),
        "com.wingedsheep.engine.core.YesNoResponse" to setOf("decisionId", "choice"),
        "com.wingedsheep.engine.state.components.identity.RoomFaceId" to setOf("value"),
        "com.wingedsheep.engine.state.components.stack.ChosenTarget.Card" to setOf("cardId", "ownerId", "zone"),
        "com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent" to setOf("entityId"),
        "com.wingedsheep.engine.state.components.stack.ChosenTarget.Player" to setOf("playerId"),
        "com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell" to setOf("spellEntityId"),
        "com.wingedsheep.gym.actorinput.ActorAdditionalCostData" to setOf("description", "costType", "validSacrificeTargets", "sacrificeCount", "costAfterSacrifice", "validTapTargets", "tapCount", "tapBatchMaxActivations", "validDiscardTargets", "discardCount", "validBounceTargets", "bounceCount", "validExileTargets", "exileMinCount", "exileMaxCount", "exileMinTotalWeight", "exileCardWeights", "exileWeightUnit", "exileWeightPerTarget", "validBeholdTargets", "beholdCount", "validRevealTargets", "revealCount", "counterRemovalCreatures", "validBlightTargets", "blightAmount", "blightVariableMaxX", "payXLifeMaxX", "distributedCounterRemovalTotal", "validCraftMaterials", "craftMinCount", "craftMaxCount", "tapForPowerCreatures", "tapForPowerRequired"),
        "com.wingedsheep.gym.actorinput.ActorCombatCreature" to setOf("entityId", "attackingDefenderId", "attackingBandId", "blockingAttackerIds", "wasBlocked", "blockerIds", "orderedBlockerIds", "orderedAttackerIds", "assignedDamage", "dealtFirstStrikeDamage"),
        "com.wingedsheep.gym.actorinput.ActorCombatState" to setOf("creatures", "playersWhoDeclaredAttackers", "playersWhoDeclaredBlockers"),
        "com.wingedsheep.gym.actorinput.ActorConvokeCreatureData" to setOf("entityId", "name", "colors"),
        "com.wingedsheep.gym.actorinput.ActorCounterRemovalCreatureData" to setOf("entityId", "name", "availableCounters", "availableCountersByType", "imageUri"),
        "com.wingedsheep.gym.actorinput.ActorDelveCardData" to setOf("entityId", "name", "imageUri"),
        "com.wingedsheep.gym.actorinput.ActorHarmonizeCreatureData" to setOf("entityId", "name", "power"),
        "com.wingedsheep.gym.actorinput.ActorLegalAction" to setOf("action", "actionType", "description", "affordable", "validTargets", "requiresTargets", "targetCount", "minTargets", "targetDescription", "targetRequirements", "xConstrainsTargetManaValue", "xConstrainsTargetManaValueExactly", "xConstrainsTargetPower", "xConstrainsTargetCount", "validAttackers", "mandatoryAttackers", "validAttackTargets", "validBlockers", "blockerMaxBlockCounts", "mandatoryBlockerAssignments", "manaCostString", "manaCostPerExtraTarget", "hasXCost", "maxAffordableX", "minX", "additionalCostInfo", "hasConvoke", "convokeCreatures", "hasDelve", "delveCards", "minDelveNeeded", "hasTapForGeneric", "tapForGenericPermanents", "tapForGenericAmount", "tapForGenericLabel", "tapForGenericRequired", "hasHarmonize", "harmonizeCreatures", "isManaAbility", "requiresManaColorChoice", "availableManaColors", "autoTapPreview", "requiresDamageDistribution", "totalDamageToDistribute", "minDamagePerTarget", "sourceZone", "castsTransformed", "tapForPower", "tapForPowerRequired", "tapForPowerCreatures", "maxRepeatableActivations", "requiresForage", "additionalLifeCost", "modalEnumeration", "holdPriority", "basicBluePayment"),
        "com.wingedsheep.gym.actorinput.ActorModalEnumerationMode" to setOf("index", "description", "available", "additionalManaCost", "additionalCostInfo", "targetRequirements"),
        "com.wingedsheep.gym.actorinput.ActorModalLegalEnumeration" to setOf("chooseCount", "minChooseCount", "allowRepeat", "additionalManaCostPerExtraMode", "additionalCostPerExtraMode", "modes", "unavailableIndices"),
        "com.wingedsheep.gym.actorinput.ActorObjectIdentity" to setOf("entityId", "generation"),
        "com.wingedsheep.gym.actorinput.ActorSourceCharacteristics" to setOf("name", "controllerId", "ownerId", "power", "toughness", "colors", "types", "subtypes", "keywords", "faceDown", "deathtouch", "lifelink"),
        "com.wingedsheep.gym.actorinput.ActorStackSource" to setOf("origin", "mode", "originalObjectIsCurrent", "currentVisibleObject", "characteristics"),
        "com.wingedsheep.gym.actorinput.ActorSpellCharacteristics" to setOf("ownerId", "manaValue", "faceDown", "counterable", "counterabilityUnavailableReason"),
        "com.wingedsheep.gym.actorinput.ActorBasicBluePayment" to setOf("status", "reason", "availableBlueMana", "totalMana", "blueRemainingAfterPayment", "bluePoolAfterPayment", "tappedSourceIds"),
        "com.wingedsheep.gym.actorinput.ActorTapForGenericPermanentData" to setOf("entityId", "name", "isCreature"),
        "com.wingedsheep.gym.actorinput.ActorTapForPowerCreatureData" to setOf("entityId", "name", "power", "canAttack"),
        "com.wingedsheep.gym.actorinput.ActorTargetInfo" to setOf("index", "description", "minTargets", "maxTargets", "validTargets", "targetZone", "mustDifferFromEarlier", "xConstrainsManaValue", "xConstrainsManaValueExactly", "xConstrainsPower", "xConstrainsCount"),
        "com.wingedsheep.sdk.scripting.AbilityId" to setOf("value"),
        "com.wingedsheep.sdk.scripting.AdditionalCostPayment" to setOf("sacrificedPermanents", "discardedCards", "lifePaid", "exiledCards", "variableCostPermanents", "beheldCards", "revealedCards", "tappedPermanents", "bouncedPermanents", "blightTargets", "blightAmount", "payXLifeAmount", "distributedCounterRemovals", "isEmpty"),
        "com.wingedsheep.sdk.scripting.AlternativePaymentChoice" to setOf("delvedCards", "convokedCreatures", "harmonizeCreature", "tapForGenericPermanents", "isEmpty", "delveReduction", "convokeGenericReduction"),
        "com.wingedsheep.sdk.scripting.ConvokePayment" to setOf("color"),
        "com.wingedsheep.sdk.scripting.DistributedCounterRemoval" to setOf("entityId", "counterType", "count"),
    )
    private val enumValues: Map<String, Set<String>> = mapOf(
        "com.wingedsheep.gym.actorinput.ActorBasicBluePaymentStatus" to setOf("PLANNED", "UNPAYABLE", "UNSUPPORTED"),
        "com.wingedsheep.engine.core.AlternativeCostType" to setOf("FLASHBACK", "HARMONIZE", "MAYHEM", "ESCAPE", "DISTURB", "WARP", "DASH", "EVOKE", "BESTOW", "EMERGE", "SNEAK", "WEB_SLINGING", "IMPENDING", "CLEAVE", "MIRACLE", "SELF_ALTERNATIVE", "GRANTED", "MODAL_BACK_FACE"),
        "com.wingedsheep.engine.core.DamageEdgeDirection" to setOf("ATTACKER_TO_BLOCKER", "BLOCKER_TO_ATTACKER", "ATTACKER_TO_PLAYER", "ATTACKER_TO_PLANESWALKER", "ATTACKER_TO_BATTLE"),
        "com.wingedsheep.engine.core.DecisionPhase" to setOf("CASTING", "RESOLUTION", "COMBAT", "STATE_BASED", "TRIGGER"),
        "com.wingedsheep.engine.core.ResolutionTargetKind" to setOf("PLAYER", "PLANESWALKER", "BATTLE"),
        "com.wingedsheep.gym.actorinput.ActorSourceMode" to setOf("LIVE_BATTLEFIELD", "DEPARTED_BATTLEFIELD"),
        "com.wingedsheep.sdk.core.Color" to setOf("WHITE", "BLUE", "BLACK", "RED", "GREEN"),
        "com.wingedsheep.sdk.core.CounterType" to setOf("PLUS_ONE_PLUS_ONE", "MINUS_ONE_MINUS_ONE", "PLUS_ONE_PLUS_ZERO", "PLUS_ZERO_PLUS_ONE", "PLUS_TWO_PLUS_ZERO", "PLUS_ZERO_PLUS_TWO", "MINUS_ONE_MINUS_ZERO", "MINUS_ZERO_MINUS_ONE", "PLUS_ONE_PLUS_TWO", "PLUS_TWO_PLUS_TWO", "MINUS_TWO_MINUS_TWO", "LOYALTY", "DEFENSE", "CHARGE", "GEM", "POISON", "SILVER", "GOLD", "PLAGUE", "TRAP", "FATE", "DEPLETION", "EGG", "LORE", "AIM", "STUN", "SHIELD", "FINALITY", "SUPPLY", "FLYING", "FIRST_STRIKE", "DOUBLE_STRIKE", "VIGILANCE", "LIFELINK", "INDESTRUCTIBLE", "DEATHTOUCH", "TRAMPLE", "HEXPROOF", "REACH", "HASTE", "MENACE", "STASH", "CROAK", "BLIGHT", "COIN", "FLOOD", "CHORUS", "DREAM", "QUEST", "GROWTH", "TIME", "FEATHER", "HOURGLASS", "DECAYED", "HOPE", "VERSE", "INFLUENCE", "BURDEN", "LOOT", "WIND", "NEST", "PAGE", "HOOFPRINT", "MANNEQUIN", "REV", "BLOODSTAIN", "BLOOD", "SOUL", "DIVINITY", "DOOM", "POSSESSION", "FIRE", "CONQUEROR", "NET", "LANDMARK", "DREAD", "SPORE", "INCUBATION", "FELLOWSHIP", "BAIT", "BORE", "POINT", "WISH", "REVIVAL", "INGENUITY", "FILM", "SKEWER", "ENERGY", "ICE", "OMEN", "SUSPECT", "PLAN", "INVASION", "UNLOCK", "HARNESS", "HONE", "STORAGE", "HUNGER", "SLIME", "JAVELIN", "CREDIT", "CUBE", "TIDE", "JUDGMENT"),
        "com.wingedsheep.sdk.core.Zone" to setOf("LIBRARY", "HAND", "BATTLEFIELD", "GRAVEYARD", "STACK", "EXILE", "COMMAND", "SIDEBOARD"),
        "com.wingedsheep.sdk.scripting.ChoiceSlot" to setOf("COLOR", "CREATURE_TYPE", "LAND_TYPE", "MODE", "CARD_NAME", "CARD_TYPE", "CREATURE", "KICKED", "BARGAINED", "EVIDENCE_COLLECTED", "TEAMWORK", "SNEAK", "WEB_SLUNG", "WEB_SLUNG_RETURNED_MV", "MAYHEM_CAST", "BLIGHT_AMOUNT", "WATERBEND_PAID", "GIFT_PROMISED", "OPPONENT", "CHOSEN_NUMBER"),
    )

    fun verify(value: Any) {
        if (ActorQuestionSchemas.verifyIfKnown(value)) return
        val type = value::class
        val expected = fields[type.qualifiedName]
            ?: fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Unaudited payload type: ${type.qualifiedName}")
        if (type.memberProperties.map { it.name }.toSet() != expected) {
            fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Payload data fields changed: ${type.qualifiedName}")
        }
    }

    fun verifyEnum(value: Enum<*>) {
        val expected = enumValues[value::class.qualifiedName]
            ?: fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Unaudited payload enum")
        if (value.name !in expected) fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Unaudited enum value")
    }
}
