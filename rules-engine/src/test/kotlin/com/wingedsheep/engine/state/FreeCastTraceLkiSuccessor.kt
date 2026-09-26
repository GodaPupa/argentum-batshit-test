package com.wingedsheep.engine.state

import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Explicit expected-state successor to the captured free-cast trace, version 1.
 * The historical JSON stays unchanged. Destruction now captures four additional public facts
 * about the original Sol Ring; every other historical entity, payload and outcome is compared.
 * These constants are established by the captured pre-destruction state, not the replay result.
 */
internal fun GameState.withFreeCastTraceLkiSuccessor(fixture: String, step: Int): GameState {
    if (fixture != "free-cast-target" || step != 3) return this
    val solRing = EntityId.of("card-1002")
    val prior = requireNotNull(getEntity(solRing)?.get<LastKnownPermanentComponent>())
    check(prior.snapshot.entityId == solRing && prior.snapshot.cardDefinitionId == "Sol Ring")
    check(prior.snapshot.name == null && prior.snapshot.objectRef == null &&
        prior.snapshot.colors == null && prior.snapshot.ownerId == null) {
        "The captured free-cast fixture changed; review its explicit LKI successor"
    }
    return updateEntity(solRing) { it.with(prior.copy(snapshot = prior.snapshot.copy(
        name = "Sol Ring",
        objectRef = ObjectRef(solRing, 2),
        colors = emptySet(),
        ownerId = EntityId.of("player-2"),
    ))) }
}
