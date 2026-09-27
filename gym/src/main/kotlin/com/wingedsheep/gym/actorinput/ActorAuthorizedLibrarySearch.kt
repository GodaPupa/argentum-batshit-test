package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.ResolvedLibrarySearchPortion
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectFromCollectionContinuation
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/**
 * The engine may attach source proof to a current search question. The actor boundary grants only
 * that question's eligible offered order; it never forwards the proof, a library index, or a
 * continuation to a pilot. The trusted runner binds the sanitized question to ActorEpoch.
 */
internal fun verifiedAuthorizedLibrarySearchOrder(
    state: GameState,
    actor: EntityId,
    decision: SelectCardsDecision,
): Boolean {
    val proof = decision.authorizedLibrarySearch ?: return false
    val suspension = state.peekContinuation() as? Suspension ?: return false
    if (suspension.question !== decision) return false
    val answer = suspension.answer as? SelectFromCollectionContinuation ?: return false
    val refs = answer.objectReferences
    val offered = decision.options
    if (proof.decisionId != decision.id ||
        proof.chooserId != decision.playerId || proof.actorId != actor ||
        actor != decision.playerId || state.actorFor(decision.playerId) != actor ||
        proof.libraryOwner != actor ||
        proof.sourceOrigin.entityId != decision.context.sourceId ||
        proof.sourceOrigin.entityId != answer.sourceId ||
        proof.sourceOrigin != refs.origin || !refs.captured ||
        proof.resolutionKey.isBlank() || proof.resolutionKey != refs.resolutionKey ||
        proof.offeredHandles != offered || answer.playerId != actor ||
        answer.allCards != offered || offered.isEmpty() ||
        offered.size != offered.toSet().size ||
        decision.nonSelectableOptions.isNotEmpty() ||
        decision.cardInfo?.keys != offered.toSet()
    ) return false

    val library = state.getLibrary(proof.libraryOwner)
    val portion = when (val scope = proof.portion) {
        ResolvedLibrarySearchPortion.Whole -> library
        is ResolvedLibrarySearchPortion.Top -> {
            if (scope.count < 0) return false
            library.take(scope.count)
        }
    }
    val handles = offered.toSet()
    // The search may offer a filtered subset. Its eligible cards must remain in this permitted
    // portion, in physical relative order; unrelated hidden library positions stay excluded.
    return portion.filter { it in handles } == offered &&
        offered.all { state.objectRef(it) != null }
}
