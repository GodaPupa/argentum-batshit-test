package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.Assign
import com.wingedsheep.tooling.coverage.Block
import com.wingedsheep.tooling.coverage.Call
import com.wingedsheep.tooling.coverage.Composite
import com.wingedsheep.tooling.coverage.Dsl
import com.wingedsheep.tooling.coverage.Stmt
import com.wingedsheep.tooling.coverage.Sub
import com.wingedsheep.tooling.coverage.arg
import com.wingedsheep.tooling.coverage.call
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonObject

/**
 * Whole-card spell shortcuts — multi-action shapes recognised as one named `Patterns.*` rather
 * than rendered action-by-action. [spellBlock] tries each before falling back to the generic path.
 * A `String?` shortcut yields a one-line `effect =`; a `List<String>?` shortcut yields a whole block.
 */
internal fun EmitCtx.eachplayerMaydraw(card: JsonObject): Dsl? {
    val rules = card["Rules"]
    if (!jsonContains(rules, "_Action", "EachPlayerActions") || !jsonContains(rules, "_Action", "DrawUptoNumberCards")) return null
    val blob = compact(rules)
    val mx = Regex(""""DrawUptoNumberCards".*?"args":\s*(\d+)""").find(blob) ?: return null
    val life = Regex(""""GainLifeForEach".*?"args":\s*(\d+)""").find(blob)
    val parts = mutableListOf(arg("maxCards", mx.groupValues[1]))
    if (life != null) parts.add(arg("lifePerCardNotDrawn", life.groupValues[1]))
    return Call("Patterns.Hand.eachPlayerMayDraw", parts)
}

/** Each player discards any number, then draws that many; you draw 1 (Flux). */
internal fun EmitCtx.fluxEffect(card: JsonObject): Dsl? {
    val blob = compact(card["Rules"])
    if ("TheNumberOfCardsDiscardedByPlayerThisWay" in blob && "DiscardAnyNumberOfCards" in blob) {
        val bonus = if ("\"DrawACard\"" in blob) 1 else 0
        return call("Patterns.Hand.eachPlayerDiscardsDraws", arg("controllerBonusDraw", "$bonus"))
    }
    return null
}

/** Each player shuffles their hand into their library, then draws that many (Winds of Change). */
internal fun EmitCtx.windsEffect(card: JsonObject): Dsl? {
    val blob = compact(card["Rules"])
    if ("ShuffleHandIntoLibrary" in blob && "NumCardsShuffledIntoLibraryThisWay" in blob) {
        return call("Patterns.Hand.wheelEffect", arg("Player.Each"))
    }
    return null
}

/** Take an extra turn, then lose at that turn's end step (Last Chance / Final Fortune). */
internal fun EmitCtx.extraTurnEffect(card: JsonObject): Dsl? {
    val (_, actions) = extractEnvelope(card["Rules"])
    if (actions == null) return null
    val hasExtra = actions.any { it.strField("_Action") == "TakeAnExtraTurn" }
    val loseAfter = actions.any { it.strField("_Action") == "CreateFutureTrigger" && jsonContains(it, "_Action", "LoseTheGame") }
    if (hasExtra && loseAfter) return call("TakeExtraTurnEffect", arg("loseAtEndStep", "true"))
    return null
}

/**
 * Distributed-damage spells (TargetedDistributed): `N damage divided as you choose among one or
 * <max> targets`, optionally followed by more actions ("Draw a card." — Electrolyze).
 *
 * Recovers the total and the target-count cap, picks the target requirement by the distributed-target
 * shape (`…AnyTargets` -> AnyTarget; `…TargetPermanents` -> TargetCreature, the Forked-Lightning form),
 * renders the leading `DividedDamageEffect(total, 1, max)`, and composes any trailing actions through
 * the generic renderer. If a trailing action can't be rendered exactly, decline so the card scaffolds
 * rather than dropping it.
 */
internal fun EmitCtx.distributedSpell(card: JsonObject): List<Stmt>? {
    val blob = compact(card["Rules"])
    if ("\"TargetedDistributed\"" !in blob) return null
    val total = Regex(""""DistributeNumberAmongTargets","args":\{"_GameNumber":"Integer","args":(\d+)""").find(blob) ?: return null
    val anyMax = Regex(""""BetweenOneAndNumberAnyTargets","args":\{"_GameNumber":"Integer","args":(\d+)""").find(blob)
    val permMax = Regex(""""BetweenOneAndNumberTargetPermanents","args":\[\{"_GameNumber":"Integer","args":(\d+)""").find(blob)
    val (target, m) = when {
        anyMax != null -> call("AnyTarget", arg("count", anyMax.groupValues[1]), arg("minCount", "1")) to anyMax.groupValues[1]
        permMax != null -> call("TargetCreature", arg("count", permMax.groupValues[1]), arg("minCount", "1")) to permMax.groupValues[1]
        else -> return null
    }
    val divided = call("DividedDamageEffect", arg("totalDamage", total.groupValues[1]), arg("minTargets", "1"), arg("maxTargets", m))
    // Compose any actions after the distributed-damage one (e.g. Electrolyze's trailing "Draw a card").
    val (_, actions) = extractEnvelope(card["Rules"])
    val trailing = actions?.dropWhile { it.strField("_Action") != "SpellDealsDistributedDamage" }?.drop(1).orEmpty()
    val effect: Dsl = if (trailing.isEmpty()) {
        divided
    } else {
        val rest = renderEffectList(trailing, null) ?: return null
        val parts = mutableListOf<Dsl>(divided)
        if (rest is Composite) parts.addAll(rest.parts) else parts.add(rest)
        Composite(parts)
    }
    return listOf(Sub(Block("spell", listOf(
        Assign("target", target),
        Assign("effect", effect),
    ))))
}

/** Draw the difference between target opponent's hand and yours (Balance of Power). */
internal fun EmitCtx.balanceEffect(card: JsonObject): List<Stmt>? {
    val blob = compact(card["Rules"])
    if ("NumCardsInHandIs" in blob && "\"Minus\"" in blob && "TheNumberOfCardsInPlayersHand" in blob) {
        return listOf(Sub(Block("spell", listOf(
            Assign("target", call("TargetOpponent")),
            Assign("effect", call("DrawCardsEffect", arg(call("DynamicAmounts.handSizeDifferenceFromTargetOpponent")))),
        ))))
    }
    return null
}
