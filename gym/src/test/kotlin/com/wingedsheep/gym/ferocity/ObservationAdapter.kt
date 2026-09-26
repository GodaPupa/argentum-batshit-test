package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.mechanics.mana.ManaPaymentWindow
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.combat.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.player.*
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.view.Visibility
import com.wingedsheep.gym.contract.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Trusted projection seam, NOT a pilot dependency. GameState is accepted only here. The shared
 * Visibility authority decides ordinary identity access; the shared builder supplies projected
 * characteristics. Its pending-decision menu and revealAll mode are deliberately never used.
 */
class ObservationAdapter(registry: CardRegistry) {
    private val visibility = Visibility(registry)
    private val builder = ObservationBuilder(registry)

    fun build(
        state: GameState,
        actor: EntityId,
        legalActions: List<LegalAction>,
        epoch: ActorEpoch,
        policyRngState: Long,
    ): ActorInput {
        if (state.gameOver) fail(BoundaryFailure.TERMINAL_STATE, "Terminal states do not request pilot actions")
        val expectedActor = state.pendingDecision?.playerId ?: state.priorityPlayerId
        if (actor != expectedActor || actor !in state.turnOrder) {
            fail(BoundaryFailure.WRONG_ACTOR, "Only the current decision/priority actor may observe")
        }
        val pending = state.pendingDecision
        val invalidDecisionMenu = pending != null && legalActions.isNotEmpty() &&
            (ManaPaymentWindow.openFor(state, actor) == null || legalActions.any { !it.isManaAbility })
        if (invalidDecisionMenu || (pending == null && legalActions.isEmpty())) {
            fail(BoundaryFailure.INCOMPLETE_INPUT, "Supply the complete current menu; a pending payment permits mana abilities only")
        }
        if (legalActions.any { it.action.playerId != actor }) {
            fail(BoundaryFailure.WRONG_ACTOR, "Legal action belongs to another actor")
        }
        checkPublicMechanics(state)

        // This is a projection-only copy: no continuation is executed or submitted back to play.
        // It avoids the old builder folding large numeric domains into an incomplete action menu.
        val viewState = if (pending == null) state else state.copy(continuationStack = emptyList())
        val base = gameView(viewState, actor)
        val zones = normalizeZones(base.zones, actor, state)
        val ordinary = zones.flatMap { it.cards }.associateBy { it.entityId }
        val stack = base.stack.map { item ->
            val entity = state.getEntity(item.entityId)
            val spell = entity?.get<SpellOnStackComponent>()
            val trigger = entity?.get<TriggeredAbilityOnStackComponent>()
            val activation = entity?.get<ActivatedAbilityOnStackComponent>()
            ActorStackItem(item, trigger?.sourceId ?: activation?.sourceId,
                spell?.xValue ?: trigger?.xValue ?: activation?.xValue,
                spell?.chosenModes ?: trigger?.chosenModes ?: emptyList(),
                entity?.let { projectStackSource(state, it, ordinary) })
        }

        val authorizedLooks = pending?.let { currentOwnLibraryLooks(state, actor, it) } ?: emptySet()
        var lookState = viewState
        authorizedLooks.forEach { id ->
            lookState = lookState.updateEntity(id) { container ->
                container.with((container.get<RevealedToComponent>() ?: RevealedToComponent(emptySet())).withPlayer(actor))
            }
        }
        val lookedCards = if (authorizedLooks.isEmpty()) emptyList() else {
            gameView(lookState, actor).zones
                .flatMap { it.cards }.filter { it.entityId in authorizedLooks }
                .map { enrichKnownCard(it, lookState) }.sortedBy { it.entityId.value }
        }
        if (lookedCards.map { it.entityId }.toSet() != authorizedLooks) {
            fail(BoundaryFailure.UNAUTHORIZED_LOOK, "Current decision look could not be represented")
        }
        val features = ordinary + lookedCards.associateBy { it.entityId }
        val publicHandles = state.turnOrder.toSet() + ordinary.keys + state.stack +
            stack.flatMap { listOfNotNull(it.sourceId) + it.view.targets }
        val allowedHandles = publicHandles + authorizedLooks
        stack.forEach { requireAccessible(it.source, publicHandles) }
        val safeNames = features.mapValues { it.value.name } +
            base.players.associate { it.id to it.name } +
            stack.associate { it.view.entityId to it.view.name } +
            stack.mapNotNull { item -> item.sourceId?.let { it to item.view.name } }.toMap()
        val sanitizedDecision = pending?.let {
            val ordering = VerifiedTriggerOrderQuestion.inspect(state, it, features)
            sanitizeDecision(it, features, safeNames, allowedHandles, ordering)
        }
        val menu = legalActions.map { action ->
            val copied = action.actorCopy()
            requireAccessible(copied, publicHandles)
            copied
        }
        val combat = publicCombat(state)
        requireAccessible(combat, publicHandles)

        val observation = ActorObservation(
            state.turnNumber, state.phase, state.step, state.activePlayerId, state.priorityPlayerId,
            base.players, zones, stack, combat, playerResources(state), state.spellsCastThisTurn,
            state.permanentsSacrificedThisTurn, state.damageCantBePreventedThisTurn,
            state.nonlandPermanentLeftBattlefieldThisTurn, lookedCards,
        )
        return ActorInputCodec.seal(ActorInput(epoch, actor, observation, sanitizedDecision, menu, policyRngState))
    }

    private fun gameView(state: GameState, actor: EntityId): TrainingObservation =
        builder.build(state, actor, emptyList()).observation as? TrainingObservation
            ?: fail(BoundaryFailure.UNSUPPORTED_SCHEMA, "Game observation builder returned another observation type")

    private fun normalizeZones(zones: List<ZoneView>, actor: EntityId, state: GameState): List<ZoneView> {
        val battlefield = zones.filter { it.zoneType == Zone.BATTLEFIELD }.flatMap { it.cards }
        return zones.map { zone ->
            val cards = when (zone.zoneType) {
                Zone.BATTLEFIELD -> battlefield.filter { (it.controllerId ?: it.ownerId) == zone.ownerId }
                Zone.EXILE -> zone.cards.filter {
                    visibility.isCardIdentityVisibleTo(state, Zone.EXILE, it.entityId, actor)
                }
                else -> zone.cards
            }.map { enrichKnownCard(it, state) }
            // Library positions are absent even when more than one card's identity is known.
            val ordered = if (zone.hidden || zone.zoneType in setOf(Zone.HAND, Zone.BATTLEFIELD, Zone.EXILE))
                cards.sortedBy { it.entityId.value } else cards
            zone.copy(cards = ordered, size = if (zone.zoneType == Zone.BATTLEFIELD) ordered.size else zone.size)
        }
    }

    private fun enrichKnownCard(card: EntityFeatures, state: GameState): EntityFeatures {
        val normalized = normalizeCard(card)
        if (card.zone == Zone.BATTLEFIELD || card.cardDefinitionId == null) return normalized
        // This function receives only a shared-Visibility view or the explicitly authorized
        // current own-library group. Hidden raw cardInfo is never an identity authority.
        val stats = state.getEntity(card.entityId)?.get<CardComponent>()?.baseStats
        return normalized.copy(power = stats?.basePower, toughness = stats?.baseToughness)
    }

    private fun publicCombat(state: GameState): ActorCombatState = ActorCombatState(
        creatures = state.getBattlefield().sortedBy { it.value }.mapNotNull { id ->
            val entity = state.getEntity(id) ?: return@mapNotNull null
            val attacking = entity.get<AttackingComponent>()
            val blocking = entity.get<BlockingComponent>()
            val blocked = entity.get<BlockedComponent>()
            val order = entity.get<DamageAssignmentOrderComponent>()
            val attackerOrder = entity.get<AttackerOrderComponent>()
            val damage = entity.get<DamageAssignmentComponent>()
            val firstStrike = entity.has<DealtFirstStrikeDamageComponent>()
            if (attacking == null && blocking == null && blocked == null && order == null &&
                attackerOrder == null && damage == null && !firstStrike) return@mapNotNull null
            ActorCombatCreature(id, attacking?.defenderId, attacking?.bandId,
                blocking?.blockedAttackerIds.orEmpty(), blocked != null, blocked?.blockerIds.orEmpty(),
                order?.orderedBlockers.orEmpty(), attackerOrder?.orderedAttackers.orEmpty(),
                damage?.assignments.orEmpty(), firstStrike)
        },
        playersWhoDeclaredAttackers = state.turnOrder.filter {
            state.getEntity(it)?.has<AttackersDeclaredThisCombatComponent>() == true
        },
        playersWhoDeclaredBlockers = state.turnOrder.filter {
            state.getEntity(it)?.has<BlockersDeclaredThisCombatComponent>() == true
        },
    )

    private fun playerResources(state: GameState): List<ActorPlayerResources> = state.turnOrder.map { id ->
        val entity = state.getEntity(id)
        val lands = entity?.get<LandDropsComponent>() ?: LandDropsComponent()
        val mulligan = entity?.get<MulliganStateComponent>()
        val pool = entity?.get<ManaPoolComponent>() ?: ManaPoolComponent()
        ActorPlayerResources(
            id, lands.remaining, lands.maxPerTurn, mulligan?.mulligansTaken, mulligan?.hasKept,
            mulligan?.cardsToBottom, state.playerSpellsCastThisTurn[id] ?: 0,
            entity?.get<CardsDrawnThisTurnComponent>()?.count ?: 0,
            entity?.get<CreaturesDiedThisTurnComponent>()?.count ?: 0,
            entity?.get<LifeGainedAmountThisTurnComponent>()?.amount ?: 0,
            entity?.get<LifeLostAmountThisTurnComponent>()?.amount ?: 0,
            pool.restrictedMana.map { ActorRestrictedMana(it.color, it.restriction, it.riders.sortedBy { r -> r.toString() }.toSet(), it.expiry) },
            pool.manaBySubtype.entries.associate { it.key.toString() to it.value }, pool.manaBySource,
        )
    }

    private fun currentOwnLibraryLooks(state: GameState, actor: EntityId, decision: PendingDecision): Set<EntityId> {
        val groups = decisionCardGroups(decision)
        val info = decisionCardInfo(decision)
        if (info.keys.any { it !in groups }) fail(BoundaryFailure.UNAUTHORIZED_LOOK, "Card info extends beyond current question")
        val library = state.getLibrary(actor).toSet()
        return groups.filterTo(linkedSetOf()) { id ->
            id in library && id in info && !visibility.isCardIdentityVisibleTo(state, Zone.LIBRARY, id, actor)
        }
    }

    private fun checkPublicMechanics(state: GameState) {
        // These are being implemented on separate branches. Refuse admission after integration
        // until their explicit public fields have been reviewed here; never silently hide them.
        val unqualified = state.entities.values.flatMap { it.all() }.any { component ->
            val name = component::class.simpleName.orEmpty()
            name.contains("Monarch") || name.contains("Initiative") || name.contains("Dungeon") || name.contains("Emblem")
        }
        if (unqualified) fail(BoundaryFailure.UNSUPPORTED_PUBLIC_MECHANIC, "Public designation/dungeon/emblem projection needs qualification")
    }
}

internal fun normalizeCard(card: EntityFeatures): EntityFeatures = card.copy(
    types = card.types.sorted().toSet(), subtypes = card.subtypes.sorted().toSet(),
    colors = card.colors.sorted().toSet(), keywords = card.keywords.sorted().toSet(),
    attachments = card.attachments.sortedBy { it.value },
)

internal fun fail(failure: BoundaryFailure, message: String): Nothing =
    throw ObservationBoundaryException(failure, message)
