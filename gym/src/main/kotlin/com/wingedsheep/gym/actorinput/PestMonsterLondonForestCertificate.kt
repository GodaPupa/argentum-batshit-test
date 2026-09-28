package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_MAIN_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone

/**
 * A narrow pregame existence proof for the printed Forest in the frozen Monster main.
 * The trusted runner must bind [verifiedMainSha256] to the actually loaded deck. This derives
 * only a Boolean from the complete own hand and exact deck counts; it neither reads the library
 * nor supplies a physical search target or a keep/bottom policy.
 */
object PestMonsterLondonForestCertificate {
    fun exists(input: ActorInput, verifiedMainSha256: String): Boolean {
        input.verifyBinding(input.epoch, input.actorId)
        require(verifiedMainSha256 == PEST_MONSTER_TRON_MAIN_SHA256) { "Frozen Monster main not bound" }
        val view = input.observation
        require(view.phase == Phase.BEGINNING && view.step == Step.UNTAP) { "Not London setup" }
        require(input.decision == null && input.legalActions.isNotEmpty() &&
            input.legalActions.all { it.action is KeepHand || it.action is TakeMulligan ||
                it.action is BottomCards }) { "Not the London action menu" }
        val ownZones = view.zones.filter { it.ownerId == input.actorId }
        val hand = ownZones.single { it.zoneType == Zone.HAND }
        val library = ownZones.single { it.zoneType == Zone.LIBRARY }
        val counts = PestControlTierOneMonsterTronAdmission.mainCounts
        require(counts.values.sum() == 60 && hand.size == hand.cards.size &&
            library.size == 60 - hand.size && library.cards.isEmpty()) { "Incomplete pregame information set" }
        require(ownZones.filterNot { it.zoneType == Zone.HAND || it.zoneType == Zone.LIBRARY ||
            it.zoneType == Zone.SIDEBOARD }.all { it.size == 0 } &&
            hand.cards.map { it.entityId }.distinct().size == hand.size &&
            hand.cards.all { it.cardDefinitionId != null }) { "Unaccounted own cards" }
        val visible = hand.cards.groupingBy { it.name }.eachCount()
        require(visible.all { (name, count) -> count <= (counts[name] ?: 0) }) {
            "Own hand does not match frozen Monster main"
        }
        return visible.getOrDefault("Forest", 0) < counts.getValue("Forest")
    }
}
