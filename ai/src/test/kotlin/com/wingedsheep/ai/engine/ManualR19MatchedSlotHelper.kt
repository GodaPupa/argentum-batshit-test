package com.wingedsheep.ai.engine

/**
 * R19 test-only deck-slot constructor. This changes one *name at its original index*
 * rather than re-sorting the challenger by card type.
 *
 * The returned list contains the commander and all 99 library cards. It preserves
 * the positions of 99 shared deck cards (the commander plus 98 shared library cards).
 * It does not change production deck parsing, seeding, shuffling, or gameplay.
 */
internal object ManualR19MatchedSlotHelper {
    fun matchedChallengerNames(
        controlNames: List<String>,
        replace: String,
        with: String,
    ): List<String> {
        val index = controlNames.indexOf(replace)
        require(index >= 0) { "$replace not found in control list" }
        require(controlNames.count { it == replace } == 1) { "$replace must be unique" }
        require(with !in controlNames) { "$with already present" }
        return controlNames.toMutableList().apply { this[index] = with }
    }
}
