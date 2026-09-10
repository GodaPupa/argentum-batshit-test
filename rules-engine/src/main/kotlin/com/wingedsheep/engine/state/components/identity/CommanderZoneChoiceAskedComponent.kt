package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import kotlinx.serialization.Serializable

/**
 * Marker attached to a commander after the CR 903.9a state-based action has prompted the owner
 * about diverting it from a non-command zone. The marker is purely a "we already asked this
 * stay" gate so the SBA doesn't re-prompt every iteration when the owner declines.
 *
 * Cleared on every zone transition of the commander (see [com.wingedsheep.engine.handlers.effects
 * .ZoneTransitionService.moveToZone]) so the next entry into a non-command zone produces a fresh
 * prompt, matching the rule's "put into that zone since the last time state-based actions were
 * checked" wording.
 */
@Serializable
data object CommanderZoneChoiceAskedComponent : Component
