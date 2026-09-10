package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Player-attached registry of designated commanders.
 *
 * Phase 1 always carries a single id. Modelling it as a list now means Partner / Background /
 * Friends Forever (Phase 4) just append without a schema change.
 */
@Serializable
data class CommanderRegistryComponent(
    val commanderIds: List<EntityId>,
) : Component
