package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.ManaPaymentWindow
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.sdk.model.EntityId

/** London setup is its own action surface; the generic enumerator does not enumerate it. */
internal fun completeActorLegalActions(state: GameState, actor: EntityId, enumerator: LegalActionEnumerator): List<LegalAction> {
    if (state.pendingDecision != null) return if (ManaPaymentWindow.openFor(state, actor) != null)
        enumerator.enumerateManaAbilities(state, actor, EnumerationMode.FULL) else emptyList()
    val mulligan = state.getEntity(actor)?.get<MulliganStateComponent>()
    if (mulligan != null && !mulligan.hasKept) return buildList {
        add(LegalAction(KeepHand(actor), "KeepHand", "Keep this hand"))
        if (mulligan.canMulligan) add(LegalAction(TakeMulligan(actor), "TakeMulligan", "Take a London mulligan"))
    }
    if (mulligan != null && mulligan.hasKept && mulligan.cardsToBottom > 0) return listOf(
        LegalAction(BottomCards(actor, emptyList()), "BottomCards", "Choose and order cards for the bottom of your library")
    )
    return enumerator.enumerate(state, actor, EnumerationMode.FULL)
}

