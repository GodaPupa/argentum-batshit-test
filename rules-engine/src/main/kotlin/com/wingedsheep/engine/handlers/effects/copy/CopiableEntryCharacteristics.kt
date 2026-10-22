package com.wingedsheep.engine.handlers.effects.copy

import com.wingedsheep.engine.event.GrantedActivatedAbility
import com.wingedsheep.engine.event.GrantedStaticAbility
import com.wingedsheep.engine.event.GrantedTriggeredAbility
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.FaceDownModeComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration

/** Capture creation/printing/copy values, never the eventual layer projection or later grants. */
fun copiableEntryCard(state: GameState, entityId: EntityId): CardComponent? {
    val entity = state.getEntity(entityId) ?: return null
    val card = entity.get<CardComponent>() ?: return null
    if (!entity.has<FaceDownComponent>()) return card
    val ward = entity.get<FaceDownModeComponent>()?.mode?.faceDownWard
    // CR 708.2a: the face-down values are copiable, but the copy itself is face up. Do not retain
    // definition identity, art or ability text of the concealed face in the resulting component.
    return card.copy(
        cardDefinitionId = "copiable:face-down",
        name = "",
        manaCost = ManaCost.ZERO,
        typeLine = TypeLine.parse("Creature"),
        oracleText = "",
        baseStats = CreatureStats(2, 2),
        baseKeywords = if (ward == null) emptySet() else setOf(Keyword.WARD),
        baseFlags = emptySet(),
        colors = emptySet(),
        spellEffect = null,
        imageUri = null,
        backFaceImageUri = null,
        printingSetCode = null,
        hasNonManaActivatedAbility = false,
        hasActivatedAbility = false,
        originalSetCode = null,
        hasAdventure = false,
        isDoubleFaced = false,
        manaValueOverride = null,
        copiableTokenAbilities = null,
        copiableFaceDownWard = ward,
    )
}

/** Install creation-time token text for a new copied entrant before entry observers run. */
fun installCopiableTokenAbilities(
    state: GameState,
    entityId: EntityId,
    staticAbilityHandler: StaticAbilityHandler,
): GameState {
    val entity = state.getEntity(entityId) ?: return state
    if (entity.has<FaceDownComponent>()) return state
    val abilities = entity.get<CardComponent>()?.copiableTokenAbilities ?: return state
    val withStatics = state.updateEntity(entityId) { container ->
        staticAbilityHandler.addContinuousEffectComponentFromAbilities(container, abilities.staticAbilities)
    }
    return withStatics.copy(
        grantedTriggeredAbilities = withStatics.grantedTriggeredAbilities + abilities.triggeredAbilities.map {
            GrantedTriggeredAbility(entityId, it, Duration.Permanent)
        },
        grantedActivatedAbilities = withStatics.grantedActivatedAbilities + abilities.activatedAbilities.map {
            GrantedActivatedAbility(entityId, it, Duration.Permanent)
        },
        grantedStaticAbilities = withStatics.grantedStaticAbilities + abilities.staticAbilities.map {
            GrantedStaticAbility(entityId, it, Duration.Permanent)
        },
    )
}
