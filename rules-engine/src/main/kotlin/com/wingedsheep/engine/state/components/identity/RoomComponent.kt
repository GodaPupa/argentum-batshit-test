package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.core.ManaCost
import kotlinx.serialization.Serializable

/**
 * Stable identifier for one face of a Room permanent. Phase 2 uses the face's printed
 * name as the id, since face names are unique within a single split card. Stored as a
 * value class so the door-state set in [RoomComponent] is type-safe.
 */
@Serializable
@JvmInline
value class RoomFaceId(val value: String)

/**
 * Per-face metadata captured on the permanent at ETB. Carries everything Phase 3's
 * unlock action needs without re-reading the card definition: the face id (for
 * matching the locked-half set), the face's printed name (for UI / event log), and
 * the unlock cost (the printed mana cost — CR 709.5e).
 */
@Serializable
data class RoomFace(
    val id: RoomFaceId,
    val name: String,
    val manaCost: ManaCost,
)

/**
 * Component on a Room permanent (CR 709.5).
 *
 * Tracks the structural list of [faces] (which never change once the permanent enters
 * the battlefield) and the dynamic [unlocked] set of door designations:
 *
 *  - When a half is cast, the permanent enters with that face's id in [unlocked]
 *    (CR 709.5d).
 *  - When the Room is put on the battlefield by another effect (reanimation,
 *    Replenish, etc.), it enters with [unlocked] empty — both halves locked
 *    (CR 709.5d).
 *  - The unlock special action (Phase 3) adds face ids to [unlocked].
 *
 * Phase 2 only sets up the structure. Locked-half ability suppression and
 * door-unlock triggers are wired up in Phase 3.
 */
@Serializable
data class RoomComponent(
    val faces: List<RoomFace>,
    val unlocked: Set<RoomFaceId>,
) : Component {
    val isFullyUnlocked: Boolean get() = faces.isNotEmpty() && unlocked.size == faces.size

    fun isUnlocked(faceId: RoomFaceId): Boolean = faceId in unlocked

    /** Faces whose doors are currently locked (the unlock action targets these). */
    val lockedFaces: List<RoomFace> get() = faces.filter { it.id !in unlocked }

    /** Faces whose doors are currently unlocked (the "lock a door" effect targets these). */
    val unlockedFaces: List<RoomFace> get() = faces.filter { it.id in unlocked }
}
