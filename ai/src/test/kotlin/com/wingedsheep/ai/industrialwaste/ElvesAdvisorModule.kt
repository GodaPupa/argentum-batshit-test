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
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttackingComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Seed-free Gate 10 pilot policy for Mogged's frozen Elves opponent. */
internal object ElvesAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(ElvesQuirionAdvisor)
        registry.register(ElvesPriestAdvisor)
        registry.register(ElvesWindingWayAdvisor)
        registry.register(ElvesLeadAdvisor)
        registry.register(ElvesMaskedVandalAdvisor)
        registry.register(ElvesTimberwatchAdvisor)
        registry.register(ElvesAvengingHunterAdvisor)
        registry.register(ElvesLandGrantAdvisor)
        registry.register(ElvesGingerbreadCabinAdvisor)
    }
}

private val LOW_VALUE_ELVES = listOf(
    "Elvish Mystic", "Fyndhorn Elves", "Llanowar Elves", "Quirion Ranger", "Masked Vandal"
)

private object ElvesQuirionAdvisor : CardAdvisor {
    override val cardNames = setOf("Quirion Ranger")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is ActivateAbility) return null
        val hasTappedEngine = context.projected.getBattlefieldControlledBy(context.playerId).any { id ->
            context.state.getEntity(id)?.has<TappedComponent>() == true &&
                context.state.cardName(id) in setOf("Priest of Titania", "Timberwatch Elf")
        }
        return if (hasTappedEngine) context.passScore + 18.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? {
        if (state.projectedState.getController(targetId) != playerId) return -100.0
        if (state.getEntity(targetId)?.has<TappedComponent>() != true) return -20.0
        return when (state.cardName(targetId)) {
            "Priest of Titania" -> 100.0
            "Timberwatch Elf" -> 90.0
            else -> 20.0
        }
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            val selected = decision.options.maxByOrNull { id ->
                val tapped = context.state.getEntity(id)?.has<TappedComponent>() == true
                val name = context.state.cardName(id)
                when {
                    tapped && name == "Gingerbread Cabin" -> 100
                    tapped && name in setOf("Forest", "Snow-Covered Forest") -> 90
                    tapped -> 80
                    name in setOf("Forest", "Snow-Covered Forest", "Gingerbread Cabin") -> 30
                    else -> 0
                }
            } ?: return@let null
            CardsSelectedResponse(decision.id, listOf(selected))
        }
}

private object ElvesPriestAdvisor : CardAdvisor {
    override val cardNames = setOf("Priest of Titania")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is ActivateAbility) return null
        val elfLike = context.projected.getBattlefieldControlledBy(context.playerId).count { id ->
            context.state.cardName(id) in (
                LOW_VALUE_ELVES + listOf("Priest of Titania", "Timberwatch Elf")
            )
        }
        val hasPayoff = context.state.getZone(context.playerId, Zone.HAND).any { id ->
            context.state.cardName(id) in setOf(
                "Avenging Hunter", "Lead the Stampede", "Nyxborn Hydra", "Sagu Wildling"
            )
        }
        return if (elfLike >= 3 && hasPayoff) context.passScore + 14.0 else null
    }
}

private object ElvesWindingWayAdvisor : CardAdvisor {
    override val cardNames = setOf("Winding Way")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 10.0

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseOptionDecision)?.let { decision ->
            val idx = decision.options.indexOf("Creature")
            if (idx >= 0) OptionChosenResponse(decision.id, idx) else null
        }
}

private object ElvesLeadAdvisor : CardAdvisor {
    override val cardNames = setOf("Lead the Stampede")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 11.0

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            val creatures = decision.options.filter { id ->
                context.state.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature == true
            }.take(decision.maxSelections)
            CardsSelectedResponse(decision.id, creatures)
        }
}

private object ElvesMaskedVandalAdvisor : CardAdvisor {
    override val cardNames = setOf("Masked Vandal")

    override fun evaluateCast(context: CastContext): Double? {
        val opponent = context.state.turnOrder.firstOrNull {
            context.state.isOpponentOf(it, context.playerId)
        } ?: return null
        val hasEngineTarget = context.projected.getBattlefieldControlledBy(opponent).any { id ->
            context.state.cardName(id) in setOf("Ashnod's Altar", "Golem Foundry", "Pactdoll Terror")
        }
        return if (hasEngineTarget) context.passScore + 20.0 else null
    }

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is ChooseTargetsDecision -> {
            val requirement = decision.targetRequirements.singleOrNull() ?: return null
            val legal = decision.legalTargets[requirement.index].orEmpty()
            val chosen = legal.maxByOrNull { id ->
                when (context.state.cardName(id)) {
                    "Ashnod's Altar" -> 100
                    "Golem Foundry" -> 90
                    "Pactdoll Terror" -> 80
                    "Ichor Wellspring" -> 40
                    "Candy Trail" -> 20
                    else -> 10
                }
            } ?: return null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(chosen)))
        }
        is SelectCardsDecision -> {
            val chosen = LOW_VALUE_ELVES.asSequence().mapNotNull { wanted ->
                decision.options.firstOrNull { id -> context.state.cardName(id) == wanted }
            }.firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        else -> null
    }
}

private object ElvesTimberwatchAdvisor : CardAdvisor {
    override val cardNames = setOf("Timberwatch Elf")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is ActivateAbility || context.state.phase != Phase.COMBAT) return null
        val attacker = context.projected.getBattlefieldControlledBy(context.playerId).any { id ->
            context.state.getEntity(id)?.has<AttackingComponent>() == true
        }
        return if (attacker) context.passScore + 22.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? {
        if (state.projectedState.getController(targetId) != playerId) return -100.0
        return if (state.getEntity(targetId)?.has<AttackingComponent>() == true) 100.0 else 15.0
    }
}

private object ElvesAvengingHunterAdvisor : CardAdvisor {
    override val cardNames = setOf("Avenging Hunter")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 18.0
}

private object ElvesLandGrantAdvisor : CardAdvisor {
    override val cardNames = setOf("Land Grant")
    override fun evaluateCast(context: CastContext): Double = context.passScore + 13.0

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val chosen = listOf("Snow-Covered Forest", "Forest", "Gingerbread Cabin")
                .asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { decision.cards[it]?.name == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        is SelectCardsDecision -> {
            val info = decision.cardInfo ?: return null
            val chosen = listOf("Snow-Covered Forest", "Forest", "Gingerbread Cabin")
                .asSequence()
                .mapNotNull { wanted -> decision.options.firstOrNull { info[it]?.name == wanted } }
                .firstOrNull() ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(chosen))
        }
        else -> null
    }
}

private object ElvesGingerbreadCabinAdvisor : CardAdvisor {
    override val cardNames = setOf("Gingerbread Cabin")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is PlayLand) return null
        val forests = context.projected.getBattlefieldControlledBy(context.playerId).count { id ->
            context.state.cardName(id) in setOf("Forest", "Snow-Covered Forest", "Gingerbread Cabin")
        }
        return if (forests >= 3) context.passScore + 12.0 else context.passScore - 8.0
    }
}

private fun GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
