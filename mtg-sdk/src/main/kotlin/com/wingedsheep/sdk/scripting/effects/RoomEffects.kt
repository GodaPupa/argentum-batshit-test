package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Room / Door Effects (CR 709.5)
// =============================================================================

/**
 * Resolution-time "unlock a door" effect (CR 709.5f).
 *
 * "To unlock half of a permanent, a player chooses a locked half of that permanent, and that
 * permanent is given the appropriate unlocked designation." This is the spell/ability form of
 * unlocking — distinct from the [unlock cost] special action (CR 709.5e), which pays a half's
 * mana cost. The resolving controller pays nothing.
 *
 * The [target] is the Room whose door is unlocked. Model it with an "up to one target Room you
 * control with a locked door" `TargetObject` (`TargetFilter.…hasLockedDoor()`) so a fully-unlocked
 * Room is never a legal target and the optional ("up to one") form lets the controller choose no
 * target. If the target Room still has a single locked door at resolution it is unlocked directly;
 * if it has more than one locked door (a Room that entered without being cast, CR 709.5d), the
 * controller chooses which one to unlock (CR 709.5f).
 *
 * Unlocking emits the same `DoorUnlockedEvent` (and `RoomFullyUnlockedEvent` when it completes the
 * Room) the unlock-cost special action emits, so face-scoped "When you unlock this door" triggers
 * (CR 709.5h) and "fully unlock" / Eerie triggers fire identically.
 *
 * Authored via [com.wingedsheep.sdk.dsl.Effects.UnlockDoor].
 */
@SerialName("UnlockDoor")
@Serializable
data class UnlockDoorEffect(
    val target: EffectTarget = EffectTarget.ContextTarget(0)
) : Effect {
    override val description: String = "unlock a locked door of ${target.description} Room"
}

/**
 * Resolution-time "lock a door" effect (CR 709.5g) — the twin of [UnlockDoorEffect].
 *
 * "To lock half of a permanent, a player chooses an unlocked half of that permanent, and that
 * permanent loses the appropriate unlocked designation." Locking turns off that half's name, mana
 * cost, and rules text (CR 709.5) — the half becomes inert exactly as if it had never been unlocked.
 *
 * Unlike unlocking, locking is *not* a trigger source: there is no "when you lock this door" ability
 * in the rules, and locking can never "fully unlock" a Room. So this effect emits only a
 * `DoorLockedEvent` (for the log / animation); it deliberately does not fire the `DoorUnlockedEvent`
 * / `RoomFullyUnlockedEvent` family. That asymmetry is why lock and unlock are two separate effects
 * rather than one `lock: Boolean` flag.
 *
 * The [target] is the Room whose door is locked. When the target Room has more than one unlocked
 * door (a fully-unlocked Room), the controller chooses which one to lock at resolution (CR 709.5g);
 * with a single unlocked door there is no choice and it is locked directly; with none, this resolves
 * as a harmless no-op.
 *
 * Authored via [com.wingedsheep.sdk.dsl.Effects.LockDoor], and paired with [UnlockDoorEffect] by
 * [com.wingedsheep.sdk.dsl.Effects.LockOrUnlockDoor] for "lock or unlock a door" (Keys to the House).
 */
@SerialName("LockDoor")
@Serializable
data class LockDoorEffect(
    val target: EffectTarget = EffectTarget.ContextTarget(0)
) : Effect {
    override val description: String = "lock an unlocked door of ${target.description} Room"
}
