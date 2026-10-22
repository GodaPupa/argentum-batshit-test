package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.StaticAbility
import com.wingedsheep.sdk.scripting.TriggeredAbility
import kotlinx.serialization.Serializable

/**
 * Ability text established by a token's creation instruction (CR 707.2a). These are copiable
 * characteristics even though the token has no registered card definition. Later granted abilities
 * are deliberately absent: permanent duration alone does not make a grant copiable.
 *
 * Kept on CardComponent so copies of copies own their data after the original token is gone.
 * Null on older saved tokens denotes unavailable provenance, never permission to copy every grant.
 */
@Serializable
data class CopiableTokenAbilities(
    val staticAbilities: List<StaticAbility> = emptyList(),
    val triggeredAbilities: List<TriggeredAbility> = emptyList(),
    val activatedAbilities: List<ActivatedAbility> = emptyList(),
) {
    val isEmpty: Boolean get() = staticAbilities.isEmpty() && triggeredAbilities.isEmpty() && activatedAbilities.isEmpty()
}
