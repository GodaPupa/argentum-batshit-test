package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.evaluation.BoardPresence
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.IntentTag
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.ModalLegalEnumeration
import com.wingedsheep.engine.legalactions.TargetInfo
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment

/**
 * Picking targets for a spell or ability without simulating anything.
 *
 * Lifted out of `Strategist` in Phase 7, where it gained a second caller: a rollout
 * ([com.wingedsheep.ai.engine.rollout.PlayoutPolicy]) has to fill targets on every spell it plays
 * and is forbidden from simulating to do it — anything that simulates *inside* a playout makes the
 * playout quadratic. The Strategist still refines the targets it actually commits to by simulation
 * (`chooseCommittedTargets`); this is the heuristic floor both paths start from.
 *
 * The one thing everything here protects is that an action the AI submits is *legal*. Phase 1
 * measured the AI proposing ~0.9 illegal actions per game, 889 of 945 being "No valid targets
 * available", and [fillableRequirements] is where that was fixed.
 */
object TargetSelection {

    /**
     * Heuristic desirability of a target: higher = better. Opponent removal targets rank highest.
     *
     * @param intents Phase 6's card knowledge. On [IntentCatalog.NONE] an opponent's non-creature
     *   permanent falls back to the pre-Phase-6 flat `0.0`.
     */
    fun rank(
        state: GameState,
        entityId: EntityId,
        playerId: EntityId,
        intents: IntentCatalog = IntentCatalog.NONE,
    ): Double {
        val projected = state.projectedState
        val controller = projected.getController(entityId)
        val isOpponent = controller != null && state.isOpponentTo(controller, playerId)
        val isPlayer = state.getEntity(entityId)?.get<PlayerComponent>() != null
        val card = state.getEntity(entityId)?.get<CardComponent>()

        return if (isPlayer) {
            if (isOpponent) 5.0 else -5.0
        } else if (projected.isCreature(entityId)) {
            val value = if (card != null) {
                BoardPresence.permanentValue(state, projected, entityId, card, intents)
            } else 0.0
            if (isOpponent) value + 10.0 else -value
        } else if (card != null && intents.isEnabled) {
            val value = BoardPresence.permanentValue(state, projected, entityId, card, intents)
            if (isOpponent) value + 10.0 else -value
        } else {
            0.0
        }
    }

    fun rankForAction(
        state: GameState,
        action: LegalAction,
        info: TargetInfo,
        entityId: EntityId,
        playerId: EntityId,
        intents: IntentCatalog = IntentCatalog.NONE,
    ): Double {
        val isPlayer = state.getEntity(entityId)?.get<PlayerComponent>() != null
        if (isPlayer && isGraveyardPlayerTarget(action, info)) {
            return graveyardTargetValue(state, entityId, playerId, intents)
        }
        return rank(state, entityId, playerId, intents)
    }

    fun isGraveyardPlayerTarget(action: LegalAction, info: TargetInfo): Boolean {
        val text = "${action.description} ${action.targetDescription.orEmpty()} ${info.description}".lowercase()
        return "graveyard" in text && ("target player" in text || "player's graveyard" in text)
    }

    /**
     * Benefit to [playerId] of exiling [targetPlayerId]'s graveyard.
     *
     * Immediate stack denial is valued directly. Prospective value exists only when public state
     * exposes a concrete way to use the graveyard: visible recursion, a graveyard-synergy resource,
     * or a card with intrinsic text that functions from the graveyard. Merely containing creatures
     * or other cards no longer creates speculative value by itself.
     */
    internal fun graveyardTargetValue(
        state: GameState,
        targetPlayerId: EntityId,
        playerId: EntityId,
        intents: IntentCatalog,
    ): Double {
        val graveyard = state.getGraveyard(targetPlayerId)
        val graveyardSet = graveyard.toSet()
        val ownerIsOpponent = state.isOpponentTo(targetPlayerId, playerId)

        var immediate = 0.0
        state.stack.forEach { stackId ->
            val stackObject = state.getEntity(stackId) ?: return@forEach
            val controller = stackObject.get<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>()
                ?.casterId
                ?: stackObject.get<com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent>()
                    ?.controllerId
                ?: stackObject.get<com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent>()
                    ?.controllerId
                ?: stackObject.get<com.wingedsheep.engine.state.components.stack.AbilityOnStackComponent>()
                    ?.controllerId
                ?: return@forEach
            val usesThisGraveyard = stackObject.get<com.wingedsheep.engine.state.components.stack.TargetsComponent>()
                ?.targets.orEmpty().any { target ->
                    target is ChosenTarget.Card && target.cardId in graveyardSet
                }
            if (usesThisGraveyard) {
                immediate += if (state.isOpponentTo(controller, playerId)) 20.0 else -20.0
            }
        }

        val visibleResources = state.getHand(targetPlayerId) + state.getBattlefield(targetPlayerId)
        val recursionCapability = visibleResources.sumOf { id ->
            val card = state.getEntity(id)?.get<CardComponent>() ?: return@sumOf 0.0
            val intent = intents.forName(card.name)
            when {
                intent != null && (IntentTag.RECURSION in intent || IntentTag.DEATH_RETURN in intent) -> 2.0
                hasConcreteGraveyardUseText(card) -> 0.5
                else -> 0.0
            }
        }.coerceAtMost(4.0)

        val intrinsicUtility = graveyard.sumOf { id ->
            val card = state.getEntity(id)?.get<CardComponent>() ?: return@sumOf 0.0
            if (functionsFromOwnGraveyard(card)) 2.0 else 0.0
        }

        val recursionTargets = if (recursionCapability > 0.0) {
            graveyard.sumOf { id ->
                val card = state.getEntity(id)?.get<CardComponent>() ?: return@sumOf 0.0
                if (card.typeLine.isCreature) 1.0 else 0.0
            } * recursionCapability
        } else {
            0.0
        }

        val prospective = intrinsicUtility + recursionTargets
        return immediate + if (ownerIsOpponent) prospective else -prospective
    }

    private fun hasConcreteGraveyardUseText(card: CardComponent): Boolean {
        val text = card.oracleText.lowercase()
        return "graveyard" in text && (
            "return target" in text ||
                "cast target" in text ||
                "play target" in text ||
                "cards in your graveyard" in text ||
                "card in your graveyard" in text
            )
    }

    private fun functionsFromOwnGraveyard(card: CardComponent): Boolean {
        val text = card.oracleText.lowercase()
        val name = card.name.lowercase()
        return listOf("flashback", "escape", "jump-start", "retrace", "disturb").any { it in text } ||
            "you may cast this card from your graveyard" in text ||
            ("$name is in your graveyard" in text && "return" in text) ||
            ("return $name from your graveyard" in text)
    }

    fun fillHeuristically(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
        fillPartialRequirements: Boolean,
        intents: IntentCatalog = IntentCatalog.NONE,
    ): GameAction {
        action.modalEnumeration?.let {
            return fillModalHeuristically(state, action, playerId, intents)
        }
        if (!action.requiresTargets) return action.action
        val baseAction = action.action
        if (targetsAlreadyFilled(baseAction) != false) return action.action
        val targetInfos = fillableRequirements(action, fillPartialRequirements) ?: return action.action

        val chosenTargets = mutableListOf<ChosenTarget>()
        val chosenIds = mutableSetOf<EntityId>()
        for ((index, info) in targetInfos.withIndex()) {
            val available = if (info.mustDifferFromEarlier) {
                info.validTargets.filterNot(chosenIds::contains)
            } else {
                info.validTargets
            }
            val selectedId = available.maxByOrNull { rankForAction(state, action, info, it, playerId, intents) }
                ?: return if (targetInfos.drop(index).all { it.minTargets == 0 }) {
                    applyTargets(baseAction, chosenTargets)
                } else {
                    action.action
                }
            chosenTargets += toChosenTarget(state, info, selectedId, playerId)
            chosenIds += selectedId
        }
        return applyTargets(baseAction, chosenTargets)
    }

    private fun fillModalHeuristically(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
        intents: IntentCatalog,
    ): GameAction {
        val cast = action.action as? CastSpell ?: return action.action
        val modal = action.modalEnumeration ?: return cast
        val modes = modal.modes.filter { it.available }.take(modal.chooseCount)
        if (modes.size < modal.minChooseCount) return cast

        val orderedTargets = mutableListOf<List<ChosenTarget>>()
        for (mode in modes) {
            val chosen = mutableListOf<ChosenTarget>()
            val chosenIds = mutableSetOf<EntityId>()
            for (info in mode.targetRequirements) {
                val available = if (info.mustDifferFromEarlier) {
                    info.validTargets.filterNot(chosenIds::contains)
                } else {
                    info.validTargets
                }
                if (available.isEmpty() && info.minTargets == 0) continue
                val selectedId = available.maxByOrNull { rankForAction(state, action, info, it, playerId, intents) }
                    ?: return cast
                chosen += toChosenTarget(state, info, selectedId, playerId)
                chosenIds += selectedId
            }
            orderedTargets += chosen
        }
        val withModes = cast.copy(
            chosenModes = modes.map { it.index },
            modeTargetsOrdered = orderedTargets,
            targets = orderedTargets.flatten(),
        )
        return payEscalateCost(state, withModes, modal, modes.size)
    }

    private fun payEscalateCost(
        state: GameState,
        cast: CastSpell,
        modal: ModalLegalEnumeration,
        chosenModeCount: Int,
    ): CastSpell {
        val info = modal.additionalCostPerExtraMode ?: return cast
        val extraModes = chosenModeCount - 1
        if (extraModes <= 0) return cast
        val existing = cast.additionalCostPayment ?: AdditionalCostPayment()
        val payment = when (info.costType) {
            "DiscardCard" -> existing.copy(
                discardedCards = info.validDiscardTargets
                    .sortedByDescending { state.getEntity(it)?.get<CardComponent>()?.isLand == true }
                    .take(info.discardCount * extraModes)
            )
            "TapPermanents" -> existing.copy(
                tappedPermanents = info.validTapTargets.take(info.tapCount * extraModes)
            )
            "SacrificePermanent" -> existing.copy(
                sacrificedPermanents = info.validSacrificeTargets.take(info.sacrificeCount * extraModes)
            )
            "BouncePermanent" -> existing.copy(
                bouncedPermanents = info.validBounceTargets.take(info.bounceCount * extraModes)
            )
            "ExileFromGraveyard" -> existing.copy(
                exiledCards = info.validExileTargets.take(info.exileMinCount * extraModes)
            )
            else -> return cast
        }
        return cast.copy(additionalCostPayment = payment)
    }

    fun targetsAlreadyFilled(baseAction: GameAction): Boolean? =
        when (baseAction) {
            is CastSpell -> baseAction.targets.isNotEmpty()
            is ActivateAbility -> baseAction.targets.isNotEmpty()
            else -> null
        }

    fun fillableRequirements(action: LegalAction, fillPartial: Boolean): List<TargetInfo>? {
        val all = targetInfosFor(action) ?: return null
        val fillable = all.takeWhile { it.validTargets.isNotEmpty() }
        if (fillable.size == all.size) return all
        if (!fillPartial) return null
        val unfilled = all.drop(fillable.size)
        if (unfilled.any { it.minTargets > 0 || it.validTargets.isNotEmpty() }) return null
        return fillable
    }

    fun targetInfosFor(action: LegalAction): List<TargetInfo>? =
        action.targetRequirements
            ?: action.validTargets?.let { targets ->
                listOf(
                    TargetInfo(
                        index = 0,
                        description = action.targetDescription ?: "",
                        minTargets = action.minTargets,
                        maxTargets = action.targetCount,
                        validTargets = targets,
                        targetZone = null
                    )
                )
            }

    fun toChosenTarget(
        state: GameState,
        info: TargetInfo,
        entityId: EntityId,
        playerId: EntityId
    ): ChosenTarget = when (info.targetZone) {
        "GRAVEYARD" -> {
            val ownerId = state.getEntity(entityId)?.get<OwnerComponent>()?.playerId ?: playerId
            ChosenTarget.Card(entityId, ownerId, Zone.GRAVEYARD)
        }
        "STACK" -> ChosenTarget.Spell(entityId)
        else -> {
            val isSpell = state.isSpellOnStack(entityId)
            val isPlayer = state.getEntity(entityId)?.get<PlayerComponent>() != null
            val cardZone = zoneOfCardTarget(state, entityId)
            when {
                isSpell -> ChosenTarget.Spell(entityId)
                isPlayer -> ChosenTarget.Player(entityId)
                cardZone != null -> {
                    val ownerId = state.getEntity(entityId)?.get<OwnerComponent>()?.playerId
                        ?: cardZone.ownerId
                    ChosenTarget.Card(entityId, ownerId, cardZone.zoneType)
                }
                else -> ChosenTarget.Permanent(entityId)
            }
        }
    }

    private fun zoneOfCardTarget(state: GameState, entityId: EntityId): ZoneKey? {
        val key = state.zones.entries.firstOrNull { entityId in it.value }?.key ?: return null
        return when (key.zoneType) {
            Zone.GRAVEYARD, Zone.EXILE, Zone.HAND, Zone.LIBRARY, Zone.COMMAND -> key
            else -> null
        }
    }

    fun applyTargets(baseAction: GameAction, targets: List<ChosenTarget>): GameAction =
        when (baseAction) {
            is CastSpell -> baseAction.copy(targets = targets)
            is ActivateAbility -> baseAction.copy(targets = targets)
            else -> baseAction
        }
}
