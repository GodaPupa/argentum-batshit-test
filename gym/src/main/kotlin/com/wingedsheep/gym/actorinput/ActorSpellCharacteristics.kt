package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Public characteristics of the current spell, without its definition, script or private origin. */
@Serializable
data class ActorSpellCharacteristics(
    val ownerId: EntityId,
    val manaValue: Int,
    val faceDown: Boolean,
    /** Null when the current engine predicate still depends on an unqualified source surface. */
    val counterable: Boolean?,
    val counterabilityUnavailableReason: String? = null,
)

internal fun projectSpellCharacteristics(
    state: GameState,
    spellId: EntityId,
    resolver: StackResolver,
    registry: CardRegistry,
): ActorSpellCharacteristics? {
    val entity = state.getEntity(spellId) ?: return null
    val spell = entity.get<SpellOnStackComponent>() ?: return null
    if (spellId !in state.stack) fail(BoundaryFailure.INCOMPLETE_INPUT, "Spell projection is absent from the stack")
    val card = entity.get<CardComponent>()
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Stack spell has no card characteristics")
    val owner = card.ownerId
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Stack spell lacks public ownership")
    val faceDown = spell.castFaceDown || entity.has<FaceDownComponent>()
    val faceIndex = spell.faceIndex
    // Announced X and the selected face are public on the stack. Split/Room/Adventure faceIndex
    // does not rewrite CardComponent, so use the same selected-face cost read as the cast path.
    // Prototype and transform characteristics already reside on the current CardComponent.
    val manaValue = if (faceDown) 0 else if (faceIndex != null) {
        val face = registry.getCard(card.cardDefinitionId)?.cardFaces?.getOrNull(faceIndex)
            ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Stack spell selected face lacks public characteristics")
        face.manaCost.withXAs(spell.xValue ?: 0).cmc
    } else card.manaValue + (spell.xValue ?: 0) * card.manaCost.xCount
    // The inherited resolver still scans raw source definitions. Never query it when a concealed
    // permanent could make that Boolean identify its hidden definition, or when lost abilities
    // invalidate that scan. This explicit unknown is identical for all concealed source identities.
    val counterabilityGap = when {
        faceDown -> "Counterability of a face-down spell needs canonical qualification"
        state.getBattlefield().any { state.getEntity(it)?.has<FaceDownComponent>() == true } ->
            "Counterability with a face-down battlefield source needs canonical qualification"
        state.getBattlefield().any { state.projectedState.hasLostAllAbilities(it) } ->
            "Counterability with removed battlefield abilities needs canonical qualification"
        else -> null
    }
    return ActorSpellCharacteristics(owner, manaValue, faceDown,
        if (counterabilityGap == null) resolver.isSpellCounterable(state, spellId) else null, counterabilityGap)
}
