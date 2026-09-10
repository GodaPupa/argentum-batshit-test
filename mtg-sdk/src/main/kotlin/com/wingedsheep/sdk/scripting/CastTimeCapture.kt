package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.scripting.conditions.Condition
import kotlinx.serialization.Serializable

/**
 * One "as you cast this spell" condition capture (CR 601.2i).
 *
 * A spell may carry a list of these. The engine evaluates each [condition] against the game state
 * the moment the spell finishes being cast (after costs are paid, before it goes on the stack) with
 * the caster as the controller, and records the [flag] names whose condition was true onto the spell
 * on the stack. The spell's resolving effect then branches on the frozen answer via
 * [com.wingedsheep.sdk.scripting.conditions.CastTimeFlagSet], so a later board change can't alter it.
 *
 * This is the cast-time sibling of the player-choice [ChoiceSlot] bag: those record what the player
 * *chose* as they cast; this records whether a game [condition] *held* as they cast. Declared with
 * the `captureAtCast(flag, condition)` DSL on a spell; read with `Conditions.CapturedAtCast(flag)`.
 *
 * Used by Steer Clear ("deals 4 damage instead if you controlled a Mount as you cast this spell").
 *
 * @property flag The capture name; the same name is read back by `CastTimeFlagSet`.
 * @property condition The condition to evaluate at cast time (e.g. "you control a Mount").
 */
@Serializable
data class CastTimeCapture(
    val flag: String,
    val condition: Condition
)
