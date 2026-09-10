package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Flanking (CR 702.25) as a composable, content-agnostic primitive.
 *
 * Flanking is a keyword ability that represents a triggered ability (CR 702.25b): "Whenever
 * this creature becomes blocked by a creature without flanking, that blocking creature gets
 * -1/-1 until end of turn." Cards carry it as the bare [Keyword.FLANKING] keyword — the
 * engine synthesizes [blockedByNonFlankerTrigger] for any creature that has the keyword
 * (intrinsic or granted), exactly the way [Suspend.countdownAbility] and the ward trigger are
 * derived, so no per-card wiring is needed and future flanking cards work for free.
 *
 * The filtered "becomes blocked by a creature without flanking" trigger fires once per matching
 * blocker with that blocker as the triggering entity (see TriggerDetector's filtered
 * BecomesBlocked SELF path), so [EffectTarget.TriggeringEntity] resolves to the blocker and each
 * non-flanking blocker independently gets -1/-1. A blocker that also has flanking is excluded by
 * the filter and is left unweakened (CR 702.25c). Multiple blockers each take their own -1/-1.
 */
object Flanking {

    /**
     * The synthesized triggered ability the engine grants to any creature that has
     * [Keyword.FLANKING]. Functions only while the creature is on the battlefield.
     */
    val blockedByNonFlankerTrigger: TriggeredAbility = TriggeredAbility(
        id = AbilityId("flanking"),
        trigger = EventPattern.BecomesBlockedEvent(
            filter = GameObjectFilter.Creature.withoutKeyword(Keyword.FLANKING),
        ),
        binding = TriggerBinding.SELF,
        effect = ModifyStatsEffect(
            powerModifier = -1,
            toughnessModifier = -1,
            target = EffectTarget.TriggeringEntity,
        ),
        descriptionOverride = "Whenever this creature becomes blocked by a creature without " +
            "flanking, that blocking creature gets -1/-1 until end of turn.",
    )
}
