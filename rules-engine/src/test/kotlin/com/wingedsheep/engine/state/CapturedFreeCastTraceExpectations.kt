package com.wingedsheep.engine.state

import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

/**
 * Receiving expectation for the existing Naturalize / Sol Ring response trace. The historical
 * JSON remains unchanged, including its original snapshot defaults and migration evidence.
 * Current departure capture adds these four exact values after the third recorded action;
 * every other saved field still participates in the caller's complete state comparison.
 * Initial decode and all response payloads, targets, priority passes and round trips are unchanged.
 */
internal fun GameState.withReceivingFreeCastDepartureSnapshot(fixture: String, actionNumber: Int): GameState {
    if (fixture != "free-cast-target" || actionNumber != 3) return this

    val ringId = EntityId("card-1002")
    val captured = checkNotNull(getEntity(ringId)?.get<LastKnownPermanentComponent>())
    captured.snapshot.entityId shouldBe ringId
    captured.snapshot.name shouldBe null
    captured.snapshot.objectRef shouldBe null
    captured.snapshot.colors shouldBe null
    captured.snapshot.ownerId shouldBe null

    return updateEntity(ringId) { entity ->
        entity.with(captured.copy(snapshot = captured.snapshot.copy(
            name = "Sol Ring",
            objectRef = ObjectRef(ringId, 2),
            colors = emptySet(),
            ownerId = EntityId("player-2"),
        )))
    }
}
