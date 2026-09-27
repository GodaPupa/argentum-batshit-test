package com.wingedsheep.gym.actorinput

import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.gym.contract.PlayerView
import com.wingedsheep.gym.contract.StackItemView
import com.wingedsheep.gym.contract.ZoneView
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ManaExpiry
import com.wingedsheep.sdk.scripting.effects.ManaRestriction
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import kotlinx.serialization.Serializable

/** Actor-visible data only. No state, registry, continuation, evaluator, or future randomness. */
@Serializable
data class ActorObservation(
    val turnNumber: Int,
    val phase: Phase,
    val step: Step,
    val activePlayerId: EntityId?,
    val priorityPlayerId: EntityId?,
    val players: List<PlayerView>,
    val zones: List<ZoneView>,
    /** Bottom to top. Declared X and modes are public casting/activation choices. */
    val stack: List<ActorStackItem>,
    val combat: ActorCombatState,
    val turnResources: List<ActorPlayerResources>,
    val spellsCastThisTurn: Int,
    val permanentsSacrificedThisTurn: Int,
    val damageCantBePreventedThisTurn: Boolean,
    val nonlandPermanentLeftBattlefieldThisTurn: Boolean,
    /** Cards the CURRENT private decision permits looking at, absent from general zone knowledge. */
    val decisionCards: List<EntityFeatures>,
)

/** Current public combat assignments; neither tapping nor the unfilled action template encodes them. */
@Serializable
data class ActorCombatState(
    val creatures: List<ActorCombatCreature>,
    val playersWhoDeclaredAttackers: List<EntityId>,
    val playersWhoDeclaredBlockers: List<EntityId>,
)

@Serializable
data class ActorCombatCreature(
    val entityId: EntityId,
    val attackingDefenderId: EntityId?,
    val attackingBandId: String?,
    val blockingAttackerIds: List<EntityId>,
    /** Remains true if blockers subsequently leave combat; an empty list alone is ambiguous. */
    val wasBlocked: Boolean,
    val blockerIds: List<EntityId>,
    val orderedBlockerIds: List<EntityId>,
    val orderedAttackerIds: List<EntityId>,
    val assignedDamage: Map<EntityId, Int>,
    val dealtFirstStrikeDamage: Boolean,
)

@Serializable
data class ActorStackItem(
    val view: StackItemView,
    val sourceId: EntityId? = null,
    val xValue: Int? = null,
    val chosenModes: List<Int> = emptyList(),
    val source: ActorStackSource? = null,
    /** Null for abilities, and for older serialized actor inputs that predate this projection. */
    val spell: ActorSpellCharacteristics? = null,
)

@Serializable
data class ActorRestrictedMana(
    val color: Color?,
    val restriction: ManaRestriction,
    val riders: Set<ManaSpellRider>,
    val expiry: ManaExpiry,
)

@Serializable
data class ActorPlayerResources(
    val playerId: EntityId,
    val landDropsRemaining: Int,
    val landDropsPerTurn: Int,
    val mulligansTaken: Int?,
    val hasKept: Boolean?,
    val cardsToBottom: Int?,
    val spellsCastThisTurn: Int,
    val cardsDrawnThisTurn: Int,
    val creaturesDiedThisTurn: Int,
    val lifeGainedThisTurn: Int,
    val lifeLostThisTurn: Int,
    val restrictedMana: List<ActorRestrictedMana>,
    val manaBySubtype: Map<String, Int>,
    /** Public mana-source handles; the source may already have been sacrificed. */
    val manaBySource: Map<EntityId, Int>,
)
