package com.wingedsheep.engine.event

import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/**
 * Card-characteristic predicates read one captured entry instant. No arm looks up the eventual
 * entrant. Null is unknown (uncaptured characteristic or a predicate needing relational/action
 * context); compositional negation must preserve it. Departure and resolution readers stay separate.
 */
internal fun matchesEntryCardPredicate(
    predicate: CardPredicate,
    entry: BattlefieldEntrySnapshot,
    chosenSubtype: String? = null,
): Boolean? {
    val snapshot = entry.characteristics
    val type = snapshot.typeLine
    val power = snapshot.power
    val toughness = snapshot.toughness
    val manaValue = entry.manaValue
    fun hasSubtype(name: String) = !snapshot.wasFaceDown &&
        (snapshot.subtypes.any { it.equals(name, ignoreCase = true) } ||
            (Keyword.CHANGELING.name in snapshot.keywords && name in Subtype.ALL_CREATURE_TYPES))
    fun child(value: CardPredicate) = matchesEntryCardPredicate(value, entry, chosenSubtype)
    return when (predicate) {
        CardPredicate.IsCreature -> type?.isCreature
        CardPredicate.IsLand -> type?.isLand
        CardPredicate.IsArtifact -> type?.isArtifact
        CardPredicate.IsEnchantment -> type?.isEnchantment
        CardPredicate.IsPlaneswalker -> type?.cardTypes?.contains(CardType.PLANESWALKER)
        is CardPredicate.HasCardType -> type?.cardTypes?.contains(predicate.cardType)
        CardPredicate.IsInstant -> type?.isInstant
        CardPredicate.IsSorcery -> type?.isSorcery
        CardPredicate.IsPermanent -> type?.isPermanent
        CardPredicate.IsBasicLand -> type?.isLand?.let { it && "BASIC" in snapshot.supertypes }
        CardPredicate.IsNonland -> type?.isLand?.not()
        CardPredicate.IsNoncreature -> type?.isCreature?.not()
        CardPredicate.IsNonartifact -> type?.isArtifact?.not()
        CardPredicate.IsNonenchantment -> type?.isEnchantment?.not()
        CardPredicate.IsLegendary -> "LEGENDARY" in snapshot.supertypes
        CardPredicate.IsNonlegendary -> "LEGENDARY" !in snapshot.supertypes
        CardPredicate.IsSnow -> "SNOW" in snapshot.supertypes
        CardPredicate.IsToken -> snapshot.wasToken
        CardPredicate.IsNontoken -> !snapshot.wasToken
        CardPredicate.IsDoubleFaced -> entry.isDoubleFaced
        CardPredicate.HasAdventure -> entry.hasAdventure
        is CardPredicate.HasColor -> predicate.color.name in entry.colors
        is CardPredicate.NotColor -> predicate.color.name !in entry.colors
        CardPredicate.IsColorless -> entry.colors.isEmpty()
        CardPredicate.IsColored -> entry.colors.isNotEmpty()
        CardPredicate.IsMulticolored -> entry.colors.size > 1
        CardPredicate.IsMonocolored -> entry.colors.size == 1
        is CardPredicate.HasSubtype -> hasSubtype(predicate.subtype.value)
        is CardPredicate.NotSubtype -> !hasSubtype(predicate.subtype.value)
        is CardPredicate.HasAnyOfSubtypes -> predicate.subtypes.any { hasSubtype(it.value) }
        is CardPredicate.HasBasicLandType -> hasSubtype(predicate.landType)
        CardPredicate.HasChosenSubtype -> chosenSubtype?.let(::hasSubtype)
        is CardPredicate.HasKeyword -> predicate.keyword.name in snapshot.keywords
        is CardPredicate.NotKeyword -> predicate.keyword.name !in snapshot.keywords
        is CardPredicate.NameEquals -> !snapshot.wasFaceDown && snapshot.name == predicate.name
        is CardPredicate.OriginallyPrintedInSet -> entry.originalSetCode?.equals(predicate.setCode, ignoreCase = true)
        is CardPredicate.ManaValueAtLeast -> manaValue >= predicate.min
        is CardPredicate.ManaValueAtMost -> manaValue <= predicate.max
        is CardPredicate.ManaValueEquals -> manaValue == predicate.value
        CardPredicate.ManaValueIsEven -> manaValue % 2 == 0
        CardPredicate.ManaValueIsOdd -> manaValue % 2 == 1
        CardPredicate.HasXInManaCost -> !snapshot.wasFaceDown && entry.manaCost.hasX
        is CardPredicate.ColoredManaSymbolsAtLeast -> !snapshot.wasFaceDown &&
            entry.manaCost.coloredSymbolCount(predicate.colors.toSet()) >= predicate.min
        is CardPredicate.PowerAtLeast -> power?.let { it >= predicate.min }
        is CardPredicate.PowerAtMost -> power?.let { it <= predicate.max }
        is CardPredicate.PowerEquals -> power?.let { it == predicate.value }
        is CardPredicate.ToughnessAtLeast -> toughness?.let { it >= predicate.min }
        is CardPredicate.ToughnessAtMost -> toughness?.let { it <= predicate.max }
        is CardPredicate.ToughnessEquals -> toughness?.let { it == predicate.value }
        is CardPredicate.PowerOrToughnessAtLeast ->
            if (power == null || toughness == null) null else power >= predicate.min || toughness >= predicate.min
        is CardPredicate.PowerOrToughnessAtMost ->
            if (power == null || toughness == null) null else power <= predicate.max || toughness <= predicate.max
        is CardPredicate.TotalPowerAndToughnessAtMost ->
            if (power == null || toughness == null) null else power + toughness <= predicate.max
        CardPredicate.ToughnessGreaterThanPower ->
            if (power == null || toughness == null) null else toughness > power
        CardPredicate.PowerGreaterThanBase -> if (snapshot.wasFaceDown) false
            else entry.copiablePower?.let { base -> power?.let { it > base } }
        is CardPredicate.Not -> child(predicate.predicate)?.not()
        is CardPredicate.And -> {
            val results = predicate.predicates.map(::child)
            when { results.any { it == false } -> false; results.any { it == null } -> null; else -> true }
        }
        is CardPredicate.Or -> {
            val results = predicate.predicates.map(::child)
            when { results.any { it == true } -> true; results.any { it == null } -> null; else -> false }
        }
        // Relative/chosen/action/ability predicates require more than entrant characteristics.
        // They are not qualified by this entry capability and must never read a later entrant.
        else -> null
    }
}
