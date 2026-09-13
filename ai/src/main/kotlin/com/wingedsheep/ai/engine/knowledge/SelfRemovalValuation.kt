package com.wingedsheep.ai.engine.knowledge

import com.wingedsheep.ai.engine.isOpponentTo
import com.wingedsheep.ai.engine.evaluation.BoardPresence
import com.wingedsheep.ai.insight.AuditCost
import com.wingedsheep.ai.insight.AuditEntity
import com.wingedsheep.ai.insight.FriendlyRemovalAudit
import com.wingedsheep.engine.core.AbilityTriggeredEvent
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.core.ManaAddedEvent
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Zone

/**
 * Values the exceptional case where one-card removal is aimed at its controller's own permanent.
 *
 * SHARED ARGENTUM CHANGE: yes
 *
 * Legality is deliberately not a veto: a death trigger, deterministic lethal, prevention of a
 * worse result, or a real engine/resource transition can make self-removal correct. But replacing
 * a permanent with generic death value is not free. The resolved leaf must beat preserving the
 * permanent, the removal card, its mana, and its future interaction option by a positive fair-trade
 * margin. This operates entirely on ownership, intent, cost, and resulting-state value.
 */
object SelfRemovalValuation {
    fun assess(
        state: GameState,
        leafState: GameState,
        events: List<GameEvent>,
        playerId: EntityId,
        intent: CardIntent,
        card: CardComponent,
        cast: CastSpell,
        manaCost: String?,
        legalTargetIds: List<EntityId>?,
        leafScore: Double,
        passScore: Double,
        boardPresenceWeight: Double,
    ): FriendlyRemovalAudit? {
        if (card.isCreature) return null
        val permanentTargets = cast.targets.filterIsInstance<ChosenTarget.Permanent>().map { it.entityId }
        val friendly = permanentTargets.filter { state.projectedState.getController(it) == playerId }
        val opposing = permanentTargets.filter { target ->
            state.projectedState.getController(target)?.let { state.isOpponentTo(it, playerId) } == true
        }
        if (friendly.size != 1 || opposing.isNotEmpty()) return null

        val targetId = friendly.single()
        val policyRecognizedRemoval = intent.tags.any { it in ONE_CARD_REMOVAL }
        // Some targeted/modal actions are fully materialized and simulated even when the broad
        // card-intent fold does not classify the wrapper that contains their removal effect. Audit
        // the concrete resolved outcome independently: this is observability only. `shouldHold`
        // below still receives the unchanged intent and therefore remains the production policy.
        val targetActuallyRemoved = events.filterIsInstance<ZoneChangeEvent>().any {
            it.entityId == targetId && it.fromZone == Zone.BATTLEFIELD && it.toZone != Zone.BATTLEFIELD
        }
        val lineWinsGame = leafState.gameOver && leafState.winnerId == playerId
        if (!policyRecognizedRemoval && !targetActuallyRemoved && !lineWinsGame) return null
        val target = entity(state, targetId)
        val requiredMargin = boardPresenceWeight *
            Patience.FAIR_TRADE_VALUE_PER_MANA * card.manaValue.coerceAtLeast(1)
        val lethal = lineWinsGame
        val shouldHold = policyRecognizedRemoval && shouldHold(
            state, leafState, playerId, intent, card, cast, leafScore, passScore, boardPresenceWeight,
        )
        val explicitPaymentIds = (cast.paymentStrategy as? PaymentStrategy.Explicit)
            ?.manaAbilitiesToActivate.orEmpty()
        val paymentIds = explicitPaymentIds.ifEmpty {
            state.projectedState.getBattlefieldControlledBy(playerId).filter { id ->
                state.getEntity(id)?.has<TappedComponent>() != true &&
                    leafState.getEntity(id)?.has<TappedComponent>() == true
            }
        }
        val additional = cast.additionalCostPayment
        val costs = buildList {
            fun addEntities(kind: String, ids: List<EntityId>) {
                if (ids.isNotEmpty()) add(AuditCost(kind, ids.map { entity(state, it) }))
            }
            addEntities("sacrifice", additional?.sacrificedPermanents.orEmpty())
            addEntities("discard", additional?.discardedCards.orEmpty())
            addEntities("exile", additional?.exiledCards.orEmpty())
            addEntities("variable-permanent", additional?.variableCostPermanents.orEmpty())
            addEntities("behold", additional?.beheldCards.orEmpty())
            addEntities("reveal", additional?.revealedCards.orEmpty())
            addEntities("tap", additional?.tappedPermanents.orEmpty())
            addEntities("return-to-hand", additional?.bouncedPermanents.orEmpty())
            addEntities("blight", additional?.blightTargets.orEmpty())
            additional?.blightAmount?.takeIf { it > 0 }?.let { add(AuditCost("blight-amount", amount = it)) }
            additional?.lifePaid?.takeIf { it > 0 }?.let { add(AuditCost("life", amount = it)) }
            additional?.payXLifeAmount?.takeIf { it > 0 }?.let { add(AuditCost("x-life", amount = it)) }
            additional?.distributedCounterRemovals.orEmpty().forEach {
                add(AuditCost("remove-${it.counterType}-counter", listOf(entity(state, it.entityId)), it.count))
            }
        }
        val created = events.filterIsInstance<ZoneChangeEvent>()
            .filter { it.toZone == Zone.BATTLEFIELD && it.fromZone != Zone.BATTLEFIELD }
            .map { AuditEntity(it.entityId, it.entityName, leafState.projectedState.getController(it.entityId)) }
        val effects = buildList {
            events.filterIsInstance<LifeChangedEvent>().forEach {
                add("life ${it.playerId}: ${it.oldLife}->${it.newLife} (${it.reason})")
            }
            events.filterIsInstance<CountersAddedEvent>().forEach {
                add("${it.entityName.ifEmpty { it.entityId.toString() }} +${it.amount} ${it.counterType}")
            }
            events.filterIsInstance<ManaAddedEvent>().forEach {
                add("mana ${it.playerId}: +${it.total} from ${it.sourceName ?: it.sourceId}")
            }
        }
        val boardAfter = leafState.projectedState.getBattlefieldControlledBy(playerId).sumOf { id ->
            val permanent = leafState.getEntity(id)?.get<CardComponent>() ?: return@sumOf 0.0
            BoardPresence.permanentValue(leafState, leafState.projectedState, id, permanent, intentCatalog())
        }
        val alternatives = legalTargetIds.orEmpty()
            .filter { id -> state.projectedState.getController(id)?.let { state.isOpponentTo(it, playerId) } == true }
            .map { entity(state, it) }
        val friendlyAlternatives = legalTargetIds.orEmpty()
            .filter { it != targetId && state.projectedState.getController(it) == playerId }
            .map { entity(state, it) }
        val boardStateAfter = leafState.projectedState.getBattlefieldControlledBy(playerId).map {
            entity(leafState, it)
        }
        return FriendlyRemovalAudit(
            turnNumber = state.turnNumber,
            removalAction = "cast ${card.name}",
            targetId = targetId,
            targetName = target.name,
            targetControllerId = playerId,
            targetBattlefieldValueBefore = target.battlefieldValue ?: 0.0,
            manaCost = manaCost,
            manaSources = paymentIds.map { entity(state, it) },
            lifePaid = (additional?.lifePaid ?: 0) + cast.graveyardLifeCost,
            additionalCostMode = costs.firstOrNull()?.kind ?: "none",
            additionalCosts = costs,
            removalResourceValueConsumed = card.manaValue.toDouble(),
            futureInteractionOpportunityCost = requiredMargin,
            deathTriggersCreated = events.filterIsInstance<AbilityTriggeredEvent>().map { it.description },
            resourcesCreated = created,
            resultingBoardValue = boardAfter,
            immediateEngineEffects = effects,
            deterministicLethal = lethal,
            resourceTransitionBenefit = created.isNotEmpty() || events.any { it is ManaAddedEvent },
            passHoldValue = passScore,
            opposingTargetAlternatives = alternatives,
            friendlyTargetAlternatives = friendlyAlternatives,
            resultingBoardState = boardStateAfter,
            resolvedLineValue = leafScore,
            netVersusHold = leafScore - passScore,
            requiredFairTradeMargin = requiredMargin,
            fairTradeSurplus = leafScore - passScore - requiredMargin,
            policyApplied = policyRecognizedRemoval,
            policyDisposition = when {
                !policyRecognizedRemoval -> "not applied: targeted-removal intent was not classified"
                shouldHold -> "reject: downstream value does not clear fair-trade margin"
                lethal -> "allow: deterministic lethal"
                else -> "allow: downstream value clears fair-trade margin"
            },
        )
    }

    fun shouldHold(
        state: GameState,
        leafState: GameState,
        playerId: EntityId,
        intent: CardIntent,
        card: CardComponent,
        cast: CastSpell,
        leafScore: Double,
        passScore: Double,
        boardPresenceWeight: Double,
    ): Boolean {
        if (leafState.gameOver && leafState.winnerId == playerId) return false
        if (card.isCreature || intent.tags.none { it in ONE_CARD_REMOVAL }) return false
        val permanentTargets = cast.targets.filterIsInstance<ChosenTarget.Permanent>().map { it.entityId }
        val friendly = permanentTargets.filter { state.projectedState.getController(it) == playerId }
        val opposing = permanentTargets.filter { target ->
            state.projectedState.getController(target)?.let { state.isOpponentTo(it, playerId) } == true
        }
        if (friendly.size != 1 || opposing.isNotEmpty()) return false
        val requiredPositiveMargin = boardPresenceWeight *
            Patience.FAIR_TRADE_VALUE_PER_MANA * card.manaValue.coerceAtLeast(1)
        return leafScore <= passScore + requiredPositiveMargin
    }

    private fun entity(state: GameState, id: EntityId): AuditEntity {
        val card = state.getEntity(id)?.get<CardComponent>()
        val value = card?.let { BoardPresence.permanentValue(state, state.projectedState, id, it) }
        return AuditEntity(id, card?.name ?: id.toString(), state.projectedState.getController(id), value)
    }

    private fun intentCatalog(): IntentCatalog = IntentCatalog.NONE

    private val ONE_CARD_REMOVAL = setOf(
        IntentTag.REMOVAL, IntentTag.EXILE_REMOVAL, IntentTag.NEUTRALIZE,
    )
}
