package com.wingedsheep.engine.state.components.stack

import com.wingedsheep.engine.state.Component
import com.wingedsheep.engine.state.ObjectRef
import kotlinx.serialization.Serializable

/** Combined consumed-mana contribution, owned by one exact spell incarnation. */
@Serializable
data class CommanderManaEntryCountersComponent(
    val count: Int,
    val spellRef: ObjectRef,
) : Component
