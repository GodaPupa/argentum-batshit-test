package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Seed-free Gate 11 pilot policy for Drinkme's frozen Spy Combo opponent. */
internal object SpyComboAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(SpyQuirionAdvisor)
        registry.register(SpyDefenderManaAdvisor)
        registry.register(SpyLandGrantAdvisor)
        registry.register(SpyGatecreeperAdvisor)
        registry.register(SpyLandcyclerAdvisor)
        registry.register(SpyWindingWayAdvisor)
        registry.register(SpyLeadAdvisor)
        registry.register(SpyMesmericFiendAdvisor)
        registry.register(SpyBalustradeAdvisor)
        registry.register(SpyDreadReturnAdvisor)
        registry.register(SpyLotlethAdvisor)
    }
}

private val SPY_SACRIFICE_ORDER = listOf(
    "Gatecreeper Vine",
    "Mesmeric Fiend",
    "Masked Vandal",
    "Saruli Caretaker",
    "Wall of Roots",
    "Quirion Ranger",
    "Overgrown Battlement",
    "Sagu Wildling",
    "Generous Ent",
    "Nyxborn Hydra",
    "Balustrade Spy",
)

private object SpyQuirionAdvisor : CardAdvisor {
    override val cardNames = setOf("Quirion Ranger")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is ActivateAbility) return null
        val hasTappedEngine = context.projected.getBattlefieldControlledBy(context.playerId).any { id ->
            context.state.getEntity(id)?.has<TappedComponent>() == true &&
                context.state.spyName(id) in setOf("Overgrown Battlement", "Saruli Caretaker")
        }
        return if (hasTappedEngine) context.passScore + 18.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? {
        if (state.projectedState.getController(targetId) != playerId) return -100.0
        if (state.getEntity(targetId)?.has<TappedComponent>() != true) return -30.0
        return when (state.spyName(targetId)) {
            "Overgrown Battlement" -> 100.0
            "Saruli Caretaker" -> 85.0
            else -> 10.0
        }
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            val chosen = decision.options.maxByOrNull { id ->
                val name = context.state.spyName(id)
                val tapped = context.state.getEntity(id)?.has<TappedComponent>() == true
                when {
                    tapped && name == "Forest" -> 100
                    name == "Forest" -> 80
                    else -> 0
                }
            } ?: return@let null
            CardsSelectedResponse(decision.id, listOf(chosen))
        }
}

private object SpyDefenderManaAdvisor : CardAdvisor {
    override val cardNames = setOf("Wall of Roots", "Overgrown Battlement", "Saruli Caretaker")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is ActivateAbility) return null
        val payoffInHand = context.state.getZone(context.playerId, Zone.HAND).any { id ->
            context.state.spyName(id) in setOf(
                "Balustrade Spy", "Lead the Stampede", "Winding Way", "Mesmeric Fiend",
                "Nyxborn Hydra", "Sagu Wildling", "Generous Ent"
            )
        }
        val defenders = context.projected.getBattlefieldControlledBy(context.playerId).count { id ->
            context.state.spyName(id) in setOf(
                "Wall of Roots", "Overgrown Battlement", "Saruli Caretaker", "Gatecreeper Vine"
            )
        }
        return when (context.state.spyName((context.action.action as ActivateAbility).sourceId)) {
            "Overgrown Battlement" ->
                if (defenders >= 2 && payoffInHand) context.passScore + 16.0 else null
            "Saruli Caretaker" ->
                if (payoffInHand) context.passScore + 11.0 else null
            "Wall of Roots" ->
                if (payoffInHand) context.passScore + 10.0 else null
            else -> null
        }
    }
}

private object SpyLandGrantAdvisor : CardAdvisor {
    override val cardNames = setOf("Land Grant")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 15.0

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val chosen = listOf("Forest", "Swamp").asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { decision.cards[it]?.name == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        is SelectCardsDecision -> {
            val info = decision.cardInfo ?: return null
            val chosen = listOf("Forest", "Swamp").asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { info[it]?.name == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        else -> null
    }
}

private object SpyGatecreeperAdvisor : CardAdvisor {
    override val cardNames = setOf("Gatecreeper Vine")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 9.0

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val hand = context.state.getZone(context.playerId, Zone.HAND)
            val needsBlack = hand.any { id ->
                context.state.spyName(id) in setOf("Balustrade Spy", "Mesmeric Fiend", "Lotleth Giant")
            }
            val preference = if (needsBlack) listOf("Swamp", "Forest") else listOf("Forest", "Swamp")
            val chosen = preference.asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { decision.cards[it]?.name == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        is SelectCardsDecision -> {
            val info = decision.cardInfo ?: return null
            val hand = context.state.getZone(context.playerId, Zone.HAND)
            val needsBlack = hand.any { id ->
                context.state.spyName(id) in setOf("Balustrade Spy", "Mesmeric Fiend", "Lotleth Giant")
            }
            val preference = if (needsBlack) listOf("Swamp", "Forest") else listOf("Forest", "Swamp")
            val chosen = preference.asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { info[it]?.name == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        else -> null
    }
}

private object SpyLandcyclerAdvisor : CardAdvisor {
    override val cardNames = setOf("Generous Ent", "Sagu Wildling", "Troll of Khazad-dûm")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is TypecycleCard) return null
        val battlefieldLands = context.projected.getBattlefieldControlledBy(context.playerId).count { id ->
            context.state.getEntity(id)?.get<CardComponent>()?.isLand == true
        }
        val handLands = context.state.getZone(context.playerId, Zone.HAND).count { id ->
            context.state.getEntity(id)?.get<CardComponent>()?.isLand == true
        }
        return if (battlefieldLands + handLands <= 1) context.passScore + 17.0 else null
    }
}

private object SpyWindingWayAdvisor : CardAdvisor {
    override val cardNames = setOf("Winding Way")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 11.0

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseOptionDecision)?.let { decision ->
            val idx = decision.options.indexOf("Creature")
            if (idx >= 0) OptionChosenResponse(decision.id, idx) else null
        }
}

private object SpyLeadAdvisor : CardAdvisor {
    override val cardNames = setOf("Lead the Stampede")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 12.0

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            val creatures = decision.options.filter { id ->
                context.state.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature == true
            }.take(decision.maxSelections)
            CardsSelectedResponse(decision.id, creatures)
        }
}

private object SpyMesmericFiendAdvisor : CardAdvisor {
    override val cardNames = setOf("Mesmeric Fiend")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 13.0

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is ChooseTargetsDecision -> {
            val requirement = decision.targetRequirements.singleOrNull() ?: return null
            val legal = decision.legalTargets[requirement.index].orEmpty()
            val opponent = legal.firstOrNull { context.state.isOpponentOf(it, context.playerId) }
                ?: return null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(opponent)))
        }
        is SelectCardsDecision -> {
            val priority = listOf(
                "Ashnod's Altar", "Pactdoll Terror", "Myr Retriever", "Golem Foundry",
                "Crop Rotation", "Malevolent Rumble", "Eviscerator's Insight", "Weather the Storm"
            )
            val chosen = priority.asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { id -> context.state.spyName(id) == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        else -> null
    }
}

private object SpyBalustradeAdvisor : CardAdvisor {
    override val cardNames = setOf("Balustrade Spy")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 24.0

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseTargetsDecision)?.let { decision ->
            val requirement = decision.targetRequirements.singleOrNull() ?: return@let null
            val legal = decision.legalTargets[requirement.index].orEmpty()
            if (context.playerId !in legal) return@let null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(context.playerId)))
        }
}

internal object SpyDreadReturnAdvisor : CardAdvisor {
    override val cardNames = setOf("Dread Return")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 30.0

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? =
        when (state.spyName(targetId)) {
            "Lotleth Giant" -> 100.0
            "Balustrade Spy" -> 45.0
            else -> 5.0
        }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            if (decision.maxSelections < 3) return@let null
            val selected = SPY_SACRIFICE_ORDER.asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { id -> context.state.spyName(id) == wanted } }
                .distinct()
                .take(3)
                .toList()
            if (selected.size == 3) CardsSelectedResponse(decision.id, selected) else null
        }
}

private object SpyLotlethAdvisor : CardAdvisor {
    override val cardNames = setOf("Lotleth Giant")
    override fun evaluateCast(context: CastContext): Double {
        val creatures = context.state.getGraveyard(context.playerId).count { id ->
            context.state.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature == true
        }
        return context.passScore + 16.0 + creatures.coerceAtMost(20)
    }
}

private fun GameState.spyName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
