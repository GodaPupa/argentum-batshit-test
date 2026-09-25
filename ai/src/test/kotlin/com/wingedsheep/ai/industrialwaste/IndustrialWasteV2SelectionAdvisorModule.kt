package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Prospective R1 selection capability, shared by all four frozen lists.
 * This supplements (and never edits) the historical IndustrialWasteAdvisorModule.
 * It does not qualify casting, mana planning, mulligans, telemetry or a complete pilot.
 * The ranking reads only our hand, our battlefield, our graveyard and cards offered
 * by the current legal decision. It neither simulates nor inspects any library order.
 */
internal object IndustrialWasteV2SelectionAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(IndustrialWasteV2SelectionAdvisor)
    }
}

internal object IndustrialWasteV2SelectionAdvisor : CardAdvisor {
    override val cardNames = setOf(
        "Ancient Stirrings", "Malevolent Rumble", "Myr Kinsmith",
        "Blood Fountain", "Dross Skullbomb",
    )

    override fun respondToDecision(context: AdvisorDecisionContext) = with(context) {
        val view = selectionView(state, playerId)
        when (val choice = decision) {
            is SearchLibraryDecision -> CardsSelectedResponse(
                choice.id,
                view.rank(choice.options) { choice.cards[it]?.name }
                    .take(choice.maxSelections),
            )
            is SelectCardsDecision -> CardsSelectedResponse(
                choice.id,
                view.rank(choice.options) { choice.cardInfo?.get(it)?.name ?: state.visibleName(it) }
                    .take(choice.maxSelections),
            )
            is ReorderLibraryDecision -> OrderedResponse(
                choice.id,
                view.rank(choice.cards) { choice.cardInfo[it]?.name },
            )
            is ChooseTargetsDecision -> {
                // These exact recursion cards have independent, ordinary graveyard targets.
                // Preserve the engine's target bounds and never select an unoffered card.
                val selected = mutableSetOf<EntityId>()
                TargetsResponse(choice.id, choice.targetRequirements.associate { requirement ->
                    val ranked = view.rank(choice.legalTargets[requirement.index].orEmpty()) {
                        state.visibleName(it)
                    }.filterNot(selected::contains).take(requirement.maxTargets)
                    selected += ranked
                    requirement.index to ranked
                })
            }
            is YesNoDecision -> if (sourceCardName == "Myr Kinsmith") {
                YesNoResponse(choice.id, choice = true)
            } else null
            else -> null
        }
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? {
        // This hook is only used for the two recursion cards' legally enumerated targets.
        // Do not let a caller probe an unseen opponent card through a ranking query.
        if (targetId !in state.getZone(playerId, Zone.GRAVEYARD)) return null
        return selectionView(state, playerId).score(state.visibleName(targetId)).toDouble()
    }
}

private val IW_V2_TRON = setOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")
private val IW_V2_DIRECT_GREEN = setOf("Forest", "Tree of Tales", "Darkmoss Bridge")
private val IW_V2_DIRECT_BLACK = setOf("Swamp", "Vault of Whispers", "Darkmoss Bridge")
private val IW_V2_LANDS = IW_V2_TRON + IW_V2_DIRECT_GREEN + IW_V2_DIRECT_BLACK + "Conduit Pylons"
private val IW_V2_GREEN_SPELLS = setOf("Ancient Stirrings", "Malevolent Rumble", "Crop Rotation")
private val IW_V2_BLACK_SPELLS = setOf(
    "Blood Fountain", "Dross Skullbomb", "Eviscerator's Insight", "Fanatical Offering", "Pactdoll Terror",
)

/** No fields can contain opponent cards or a future draw order. */
private data class SelectionView(
    val accessible: List<String>,
    val hand: List<String>,
    val graveyard: List<String>,
) {
    fun score(name: String?): Int {
        if (name == null) return -1000
        val lands = accessible.count { it in IW_V2_LANDS }
        val needsGreen = hand.any { it in IW_V2_GREEN_SPELLS } && accessible.none { it in IW_V2_DIRECT_GREEN }
        val needsBlack = hand.any { it in IW_V2_BLACK_SPELLS } && accessible.none { it in IW_V2_DIRECT_BLACK }
        val missingTron = IW_V2_TRON - accessible.toSet()
        return when {
            name in IW_V2_LANDS && lands < 2 -> 150
            name in IW_V2_DIRECT_GREEN && needsGreen -> 145
            name in IW_V2_DIRECT_BLACK && needsBlack -> 140
            missingTron.size == 1 && name in missingTron -> 130
            name == "Ashnod's Altar" && name !in accessible -> 120
            name == "Myr Retriever" && (accessible + graveyard).count { it == name } < 2 -> 115
            name == "Myr Retriever" && accessible.any { it == "Ashnod's Altar" } -> 110
            name in setOf("Pactdoll Terror", "Golem Foundry") &&
                accessible.none { it in setOf("Pactdoll Terror", "Golem Foundry") } -> 105
            name in setOf("Chromatic Star", "Prophetic Prism") && (needsGreen || needsBlack) -> 95
            name in setOf("Malevolent Rumble", "Ancient Stirrings", "Ichor Wellspring") -> 80
            name in setOf("Candy Trail", "Chromatic Star", "Eviscerator's Insight", "Fanatical Offering") -> 70
            name in setOf("Blood Fountain", "Dross Skullbomb") -> 60
            name == "Myr Kinsmith" -> 55
            name in missingTron && missingTron.size < 3 -> 50
            name in IW_V2_LANDS -> if (lands < 4) 45 else 10
            else -> 30
        }
    }

    fun rank(options: List<EntityId>, name: (EntityId) -> String?): List<EntityId> =
        options.sortedWith(compareByDescending<EntityId> { score(name(it)) }
            .thenBy { name(it).orEmpty() }.thenBy { it.toString() })
}

private fun selectionView(state: GameState, player: EntityId): SelectionView {
    val hand = state.getZone(player, Zone.HAND).mapNotNull(state::visibleName)
    return SelectionView(
        accessible = state.projectedState.getBattlefieldControlledBy(player)
            .mapNotNull(state::visibleName) + hand,
        hand = hand,
        graveyard = state.getZone(player, Zone.GRAVEYARD).mapNotNull(state::visibleName),
    )
}

private fun GameState.visibleName(id: EntityId): String? = getEntity(id)?.get<CardComponent>()?.name
