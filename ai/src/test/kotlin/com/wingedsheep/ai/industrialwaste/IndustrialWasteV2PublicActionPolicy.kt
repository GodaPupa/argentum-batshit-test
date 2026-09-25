package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment

/**
 * Prospective development-action component for R1. No one-ply simulation or
 * generic evaluator is consulted. The input is the real engine's legal action
 * list; only supported ordinary cast, land and activation shapes are materialized.
 * Combat targets the sole passive opponent with only engine-offered attackers.
 * Complete runner integration and metric extraction remain separate gates.
 */
internal object IndustrialWasteV2PublicActionPolicy {
    fun choose(state: GameState, player: EntityId, legal: List<LegalAction>): GameAction {
        val candidates = legal.filter { it.affordable }.mapNotNull { entry ->
            bind(state, player, entry)?.let { action -> Triple(score(state, player, entry, action), action, entry) }
        }
        return candidates.sortedWith(compareByDescending<Triple<Int, GameAction, LegalAction>> { it.first }
            .thenBy { it.second.toString() }).firstOrNull()?.second
            ?: error("No supported legal action; R1 readiness must fail closed")
    }

    private fun bind(state: GameState, player: EntityId, entry: LegalAction): GameAction? {
        var action = entry.action
        if (action is DeclareAttackers) {
            val attackers = entry.validAttackers.orEmpty()
            val opponent = entry.validAttackTargets.orEmpty().singleOrNull { it in state.getOpponents(player) }
            if (opponent == null && attackers.isNotEmpty()) return null
            return action.copy(attackers = if (opponent == null) emptyMap() else attackers.associateWith { opponent })
        }
        if (action is DeclareBlockers) return action.copy(blockers = emptyMap())
        if (action !is CastSpell && action !is ActivateAbility && action !is PlayLand && action !is PassPriority) return null
        if (entry.hasXCost || entry.targetRequirements?.size?.let { it > 1 } == true) return null
        val source = when (action) {
            is CastSpell -> action.cardId
            is ActivateAbility -> action.sourceId
            else -> null
        }
        val sourceName = source?.let { state.name(it) }
        if (entry.requiresTargets) {
            if (sourceName !in setOf("Blood Fountain", "Dross Skullbomb")) return null
            val legalTargets = entry.validTargets.orEmpty()
                .filter { it in state.getZone(player, Zone.GRAVEYARD) }
                .sortedWith(compareByDescending<EntityId> {
                    industrialWasteV2VisiblePriority(state, player, state.name(it))
                }.thenBy { it.toString() })
                .take(entry.targetCount)
            if (legalTargets.size < entry.minTargets) return null
            val chosen = legalTargets.map { ChosenTarget.Card(it, player, Zone.GRAVEYARD) }
            action = when (action) {
                is CastSpell -> action.copy(targets = chosen)
                is ActivateAbility -> action.copy(targets = chosen)
                else -> return null
            }
        }
        val cost = entry.additionalCostInfo
        if (cost != null && cost.costType != "SacrificeSelf") {
            if (cost.costType != "SacrificePermanent") return null
            val selected = cost.validSacrificeTargets.sortedWith(compareByDescending<EntityId> {
                sacrificePriority(state, player, sourceName, it)
            }.thenBy { it.toString() }).take(cost.sacrificeCount)
            if (selected.size < cost.sacrificeCount || selected.any { sacrificePriority(state, player, sourceName, it) < 0 }) return null
            val payment = AdditionalCostPayment(sacrificedPermanents = selected)
            action = when (action) {
                is CastSpell -> action.copy(additionalCostPayment = payment)
                is ActivateAbility -> action.copy(costPayment = payment)
                else -> return null
            }
        }
        if (entry.requiresManaColorChoice && action is ActivateAbility) {
            val available = entry.availableManaColors ?: Color.entries
            if (available.isEmpty()) return null
            val hand = state.getZone(player, Zone.HAND).mapNotNull { state.getEntity(it)?.get<CardComponent>() }
            val wanted = listOf(Color.BLACK, Color.GREEN).firstOrNull { color ->
                hand.any { it.manaCost.toString().contains(if (color == Color.BLACK) "{B}" else "{G}") }
            }
            action = action.copy(manaColorChoice = wanted?.takeIf { it in available } ?: available.first())
        }
        return action
    }

    private fun score(state: GameState, player: EntityId, entry: LegalAction, action: GameAction): Int {
        val board = state.projectedState.getBattlefieldControlledBy(player).mapNotNull { state.name(it) }
        val hand = state.getZone(player, Zone.HAND).mapNotNull { state.name(it) }
        val grave = state.getZone(player, Zone.GRAVEYARD).mapNotNull { state.name(it) }
        val accessible = board + hand + grave
        val loop = "Ashnod's Altar" in board && accessible.count { it == "Myr Retriever" } >= 2
        // The R1 passive fixture has no creatures or interaction. Once the public
        // board supplies lethal power for the next legal attack, stop making
        // further Foundry loops. New tokens must still wait for legal combat;
        // this preference is never itself a lethal or loop certificate.
        val opponent = state.getOpponents(player).singleOrNull()
        val life = opponent?.let { state.getEntity(it)?.get<LifeTotalComponent>()?.life }
        val publicPower = state.projectedState.getBattlefieldControlledBy(player)
            .filter { state.projectedState.isCreature(it) }
            .sumOf { (state.projectedState.getPower(it) ?: 0).coerceAtLeast(0) }
        val foundryCombatReady = "Golem Foundry" in board && "Pactdoll Terror" !in board &&
            life != null && publicPower >= life
        return when (action) {
            is PassPriority -> 0
            is DeclareAttackers, is DeclareBlockers -> 900
            is PlayLand -> {
                val name = state.name(action.cardId)
                val missingTron = TRON - board.toSet()
                when {
                    missingTron.size == 1 && name in missingTron -> 1100
                    name in GREEN && board.none { it in GREEN } -> 1080
                    name in BLACK && board.none { it in BLACK } -> 1060
                    name in missingTron -> 1040
                    else -> 1000
                }
            }
            is CastSpell -> when (val name = state.name(action.cardId)) {
                "Pactdoll Terror", "Golem Foundry" -> if (loop && board.none { it in PAYOFFS }) 800 else 180
                "Myr Retriever" -> if (loop && grave.any { it == name } && board.any { it in PAYOFFS }) 750 else 240
                "Ashnod's Altar" -> if (name !in board) 450 else 80
                "Ichor Wellspring", "Chromatic Star", "Candy Trail", "Giant's Boulder", "Prophetic Prism" -> 300
                "Malevolent Rumble", "Ancient Stirrings", "Expedition Map", "Myr Kinsmith" -> 260
                "Eviscerator's Insight", "Fanatical Offering" -> 230
                "Blood Fountain", "Dross Skullbomb" -> 220
                "Crop Rotation" -> 190
                else -> -100
            }
            is ActivateAbility -> when (state.name(action.sourceId)) {
                "Ashnod's Altar" -> if (loop && board.any { it in PAYOFFS } && !foundryCombatReady) 760 else -100
                "Golem Foundry" -> 780
                "Blood Fountain" -> if (action.targets.isEmpty()) -100 else if (grave.any { it == "Myr Retriever" }) 500 else 250
                "Dross Skullbomb" -> if (entry.requiresTargets && grave.any { it == "Myr Retriever" }) 500 else 160
                "Chromatic Star" -> 250
                "Candy Trail" -> 170
                "Expedition Map" -> 280
                else -> -100 // Mana filters are paid by the engine's planner, never activated speculatively.
            }
            else -> -1000
        }
    }

    /** Exact inert 60-Forest fixture policy; no opponent cards or future draws are inspected. */
    fun choosePassive(state: GameState, player: EntityId, legal: List<LegalAction>): GameAction {
        require(state.getZone(player, Zone.HAND).all { state.name(it) == "Forest" })
        val offered = legal.filter { it.affordable }
        offered.map { it.action }.filterIsInstance<PlayLand>()
            .minByOrNull { it.cardId.toString() }?.let { return it }
        offered.map { it.action }.filterIsInstance<DeclareAttackers>()
            .firstOrNull()?.let { return it.copy(attackers = emptyMap()) }
        offered.map { it.action }.filterIsInstance<DeclareBlockers>()
            .firstOrNull()?.let { return it.copy(blockers = emptyMap()) }
        return offered.map { it.action }.filterIsInstance<PassPriority>().firstOrNull()
            ?: error("Passive fixture has no supported legal action")
    }

    private fun sacrificePriority(state: GameState, player: EntityId, source: String?, id: EntityId): Int {
        val name = state.name(id)
        val board = state.projectedState.getBattlefieldControlledBy(player).mapNotNull { state.name(it) }
        if (source == "Crop Rotation") return when {
            name in TRON && board.count { it == name } > 1 -> 100
            name in TRON -> -100
            name in GREEN && board.count { it in GREEN } <= 1 -> -80
            name in BLACK && board.count { it in BLACK } <= 1 -> -80
            else -> 40
        }
        if (source == "Ashnod's Altar") return if (name == "Myr Retriever") 120 else -100
        return when (name) {
            "Ichor Wellspring" -> 100
            "Chromatic Star" -> 90
            "Blood", "Map", "Eldrazi Spawn" -> 80
            "Candy Trail", "Dross Skullbomb" -> 60
            "Giant's Boulder", "Prophetic Prism" -> 40
            else -> -100
        }
    }

    /** London decision uses only the offered opening hand; after three mulligans keep four. */
    fun keepOpeningHand(names: List<String>, mulligansTaken: Int): Boolean {
        require(mulligansTaken in 0..3)
        if (mulligansTaken == 3) return true
        val lands = names.count { it in LANDS }
        val cheapEngine = names.any { it in setOf("Candy Trail", "Chromatic Star", "Ichor Wellspring", "Expedition Map", "Malevolent Rumble", "Ancient Stirrings", "Blood Fountain", "Dross Skullbomb", "Giant's Boulder") }
        val greenDig = names.any { it in setOf("Malevolent Rumble", "Ancient Stirrings") }
        return lands in 2..4 && cheapEngine && (!greenDig || names.any { it in GREEN } || names.any { it in setOf("Chromatic Star", "Giant's Boulder", "Conduit Pylons", "Prophetic Prism") })
    }

    fun bottomOpeningHand(cards: Map<EntityId, String>, count: Int): List<EntityId> {
        require(count in 0..3 && count <= cards.size)
        val keep = cards.entries.sortedWith(compareByDescending<Map.Entry<EntityId, String>> {
            when (it.value) {
                in GREEN -> 120
                in BLACK -> 115
                in TRON -> 110
                "Conduit Pylons" -> 100
                "Chromatic Star", "Candy Trail", "Ichor Wellspring", "Malevolent Rumble", "Ancient Stirrings", "Expedition Map" -> 90
                "Myr Retriever", "Ashnod's Altar" -> 70
                else -> 30
            }
        }.thenBy { it.value }.thenBy { it.key.toString() })
        val targetLands = if (cards.size - count <= 5) 2 else 3
        val excessLands = keep.filter { it.value in LANDS }.drop(targetLands).map { it.key }
        val rest = keep.asReversed().filter { it.key !in excessLands && it.value !in LANDS }.map { it.key }
        val fallbackLands = keep.asReversed().filter { it.value in LANDS && it.key !in excessLands }.map { it.key }
        return (excessLands + rest + fallbackLands).distinct().take(count)
    }

    private val TRON = setOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")
    private val GREEN = setOf("Forest", "Tree of Tales", "Darkmoss Bridge")
    private val BLACK = setOf("Swamp", "Vault of Whispers", "Darkmoss Bridge")
    private val LANDS = TRON + GREEN + BLACK + "Conduit Pylons"
    private val PAYOFFS = setOf("Pactdoll Terror", "Golem Foundry")
    private fun GameState.name(id: EntityId): String? = getEntity(id)?.get<CardComponent>()?.name
}
