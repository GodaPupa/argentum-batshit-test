package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.model.EntityId

/**
 * Public-board tactical choices for the first artifact/Red cell. The bounded assignment search
 * chooses actions; its utility is never a game outcome, a deck score, or experimental evidence.
 * No registry, engine state, hidden card, future draw, or authoritative random stream is read.
 */
internal object ActorCombatPlanner {
    private const val BLOCK_BEAM = 64
    private data class CombatEstimate(
        val playerDamage: Int, val attackerLoss: Double, val defenderLoss: Double,
        val deadAttackers: Set<EntityId>, val deadBlockers: Set<EntityId>,
        val attackerLifeGain: Int, val defenderLifeGain: Int,
    )

    fun attackers(input: ActorInput, option: ActorLegalAction, aggressive: Boolean,
                  value: (EntityFeatures) -> Double): GameAction {
        val cards = ActorPublicCards(input)
        require(option.action is DeclareAttackers)
        val eligible = option.validAttackers.orEmpty().map(cards::card)
        if (eligible.isEmpty()) return DeclareAttackers(input.actorId, emptyMap())
        require(option.validAttackTargets.orEmpty().all { it == cards.opponentId }) {
            "First-cell combat policy requires one opposing player defender"
        }
        val opposingBlockers = cards.opponentBoard.filter { it.creature && !it.tapped }
        val mandatory = option.mandatoryAttackers.orEmpty().toSet()
        val sets = linkedSetOf<Set<EntityId>>()
        fun add(group: List<EntityFeatures>) { sets += group.map { it.entityId }.toSet() + mandatory }
        add(emptyList()); add(eligible); add(eligible.filter { "FLYING" in it.keywords })
        add(eligible.filter { a -> opposingBlockers.none { canBlock(it, a) } })
        add(eligible.filter { a -> opposingBlockers.filter { canBlock(it, a) }.all { b ->
            val estimate = estimate(listOf(a), listOf(b), mapOf(b.entityId to a.entityId), value)
            estimate.defenderLoss >= estimate.attackerLoss
        } })
        eligible.forEach { add(listOf(it)) }
        var best = mandatory
        var bestUtility = Double.NEGATIVE_INFINITY
        for (ids in sets) {
            val attackers = eligible.filter { it.entityId in ids }
            val blocks = bestBlocks(attackers, opposingBlockers, cards.opponentLife, value)
            val combat = estimate(attackers, opposingBlockers, blocks, value)
            val dealt = combat.playerDamage
            val immediateLethal = dealt >= cards.opponentLife + combat.defenderLifeGain
            var utility = if (immediateLethal) 1_000_000.0 else
                dealt * (if (aggressive) 2.8 else 1.6) + combat.defenderLoss - combat.attackerLoss +
                    combat.attackerLifeGain * 0.8 - combat.defenderLifeGain
            if (!immediateLethal && ids.isNotEmpty()) {
                // A public-board race check: these are possible next-turn attackers, not a
                // prediction of an unknown spell or a claim the opponent must take this line.
                val nextAttackers = cards.opponentBoard.filter {
                    it.creature && it.entityId !in combat.deadBlockers && "DEFENDER" !in it.keywords
                }.map { it.copy(keywords = it.keywords - "LIFELINK") }
                val held = cards.ownBoard.filter {
                    it.creature && !it.tapped && it.entityId !in combat.deadAttackers &&
                        (it.entityId !in ids || "VIGILANCE" in it.keywords)
                }.map { it.copy(keywords = it.keywords - "LIFELINK") }
                // No first-cell creature has native lifelink: Toxin's grant ends this turn.
                val nextLife = cards.ownLife + combat.attackerLifeGain
                val reply = estimate(nextAttackers, held,
                    bestBlocks(nextAttackers, held, nextLife, value), value)
                if (reply.playerDamage >= nextLife + reply.defenderLifeGain) utility -= 1_000_000.0
            }
            if (utility > bestUtility || (utility == bestUtility && ids.size < best.size)) {
                bestUtility = utility; best = ids
            }
        }
        return DeclareAttackers(input.actorId, best.associateWith { cards.opponentId })
    }

    fun blockers(input: ActorInput, option: ActorLegalAction,
                 value: (EntityFeatures) -> Double): GameAction {
        val cards = ActorPublicCards(input)
        require(option.action is DeclareBlockers)
        require(option.blockerMaxBlockCounts.orEmpty().values.all { it == 1 }) {
            "First-cell combat policy has no multi-attacker blocking mechanic"
        }
        require(option.mandatoryBlockerAssignments.isNullOrEmpty()) {
            "First-cell combat policy requires explicit development for mandatory blocker assignments"
        }
        val attackers = input.observation.combat.creatures.filter { it.attackingDefenderId == input.actorId }
            .map { cards.card(it.entityId) }
        val blockers = option.validBlockers.orEmpty().map(cards::card)
        val assignments = bestBlocks(attackers, blockers, cards.ownLife, value)
        return DeclareBlockers(input.actorId, assignments.mapValues { listOf(it.value) })
    }

    /** First choice maximizes removal value among damage recipients; every supplied id is retained. */
    fun order(input: ActorInput, decision: OrderObjectsDecision,
              value: (EntityFeatures) -> Double): DecisionResponse {
        val cards = ActorPublicCards(input)
        require(decision.objects.all { cards.card(it).creature }) {
            "Combat order helper received a non-combat object order"
        }
        return OrderedResponse(decision.id, decision.objects.sortedWith(
            compareByDescending<EntityId> { value(cards.card(it)) }.thenBy { it.toString() }))
    }

    /** The engine supplied legal damage minima/defaults, including deathtouch and trample. */
    fun damage(decision: AssignDamageDecision): DecisionResponse =
        DamageAssignmentResponse(decision.id, decision.defaultAssignments)

    /**
     * Current CR 510.1c/d permits a free division among blockers. Enumerate at most 4096 full
     * distributions per source, respecting the separately required trample lethal assignment.
     * Every returned edge belongs to this actor; the opponent's assignments remain theirs.
     */
    fun combatDamage(input: ActorInput, decision: CombatResolutionDecision,
                     value: (EntityFeatures) -> Double): DecisionResponse {
        val cards = ActorPublicCards(input)
        require(decision.playerId == input.actorId && decision.coChooserId == null) {
            "First-cell combat policy has no banding co-chooser"
        }
        val chosen = mutableListOf<DamageEdgeAmount>()
        for (edges in decision.edges.filter { it.editableBy == input.actorId }.groupBy { it.sourceId }.values) {
            val budget = edges.maxOf { it.maximum }
            var best = edges.map { it.amount }
            var bestUtility = Double.NEGATIVE_INFINITY
            var visited = 0
            fun legal(amounts: List<Int>): Boolean = amounts.sum() == budget &&
                amounts.indices.all { amounts[it] in 0..edges[it].maximum } &&
                edges.indices.all { drain -> !edges[drain].isTrampleDrain || amounts[drain] == 0 ||
                    edges.indices.filter { edges[it].direction == DamageEdgeDirection.ATTACKER_TO_BLOCKER }
                        .all { amounts[it] >= edges[it].lethal } }
            fun consider(amounts: List<Int>) {
                if (!legal(amounts)) return
                val utility = edges.indices.sumOf { i ->
                    val edge = edges[i]
                    if (edge.targetId == cards.opponentId) amounts[i] * 3.0
                    else if (edge.targetId == input.actorId) -amounts[i] * 3.0
                    else {
                        val target = cards.card(edge.targetId)
                        val removal = if (amounts[i] >= edge.lethal && "INDESTRUCTIBLE" !in target.keywords)
                            value(target) else 0.0
                        if (target.controllerId == input.actorId) -removal else removal
                    }
                }
                if (utility > bestUtility) { bestUtility = utility; best = amounts.toList() }
            }
            // The legal engine defaults are one explicit candidate, including a bounded-search
            // completion if an unusually wide public board has more than 4096 distributions.
            consider(best)
            fun visit(index: Int, left: Int, prefix: List<Int>) {
                if (visited >= 4096) return
                if (index == edges.lastIndex) {
                    visited++; consider(prefix + left); return
                }
                for (amount in 0..minOf(left, edges[index].maximum)) {
                    visit(index + 1, left - amount, prefix + amount)
                    if (visited >= 4096) break
                }
            }
            visit(0, budget, emptyList())
            require(bestUtility.isFinite()) { "No complete combat damage allocation in the supplied domain" }
            chosen += edges.indices.map { DamageEdgeAmount(edges[it].id, best[it]) }
        }
        return CombatResolutionResponse(decision.id, chosen)
    }

    private fun bestBlocks(attackers: List<EntityFeatures>, blockers: List<EntityFeatures>, life: Int,
                           value: (EntityFeatures) -> Double): Map<EntityId, EntityId> {
        if (attackers.isEmpty() || blockers.isEmpty()) return emptyMap()
        var beam = listOf(emptyMap<EntityId, EntityId>())
        for (blocker in blockers.sortedWith(compareByDescending<EntityFeatures>(value).thenBy { it.entityId.toString() })) {
            val eligible = attackers.filter { canBlock(blocker, it) }
            val choices = beam.flatMap { partial -> listOf(partial) + eligible.map {
                partial + (blocker.entityId to it.entityId)
            } }
            beam = choices.distinct().sortedByDescending { assignment ->
                defenseUtility(estimate(attackers, blockers, assignment, value), life)
            }.take(BLOCK_BEAM)
        }
        return beam.filter { assignment -> attackers.all { attacker ->
            "MENACE" !in attacker.keywords || assignment.values.count { it == attacker.entityId } != 1
        } }.maxByOrNull { defenseUtility(estimate(attackers, blockers, it, value), life) }.orEmpty()
    }

    private fun defenseUtility(e: CombatEstimate, life: Int): Double =
        (if (e.playerDamage >= life + e.defenderLifeGain) -1_000_000.0 else 0.0) -
            e.playerDamage * 1.8 + e.defenderLifeGain * 1.8 - e.attackerLifeGain * 0.8 +
            e.attackerLoss - e.defenderLoss

    private fun estimate(attackers: List<EntityFeatures>, blockers: List<EntityFeatures>,
                         assignment: Map<EntityId, EntityId>, value: (EntityFeatures) -> Double): CombatEstimate {
        val deadA = linkedSetOf<EntityId>(); val deadB = linkedSetOf<EntityId>()
        var playerDamage = 0
        var attackerLifeGain = 0
        var defenderLifeGain = 0
        for (attacker in attackers) {
            require(attacker.keywords.none { it in setOf("FIRST_STRIKE", "DOUBLE_STRIKE", "BANDING", "INFECT", "WITHER") }) {
                "Combat timing/damage keyword needs a qualified policy extension"
            }
            val assigned = blockers.filter { assignment[it.entityId] == attacker.entityId }
            assigned.forEach { b -> require(b.keywords.none {
                it in setOf("FIRST_STRIKE", "DOUBLE_STRIKE", "BANDING", "INFECT", "WITHER")
            }) { "Combat timing/damage keyword needs a qualified policy extension" } }
            if ("LIFELINK" in attacker.keywords) attackerLifeGain += attacker.p
            if (assigned.isEmpty()) { playerDamage += attacker.p; continue }
            defenderLifeGain += assigned.filter { "LIFELINK" in it.keywords }.sumOf { it.p }
            val suffered = assigned.sumOf { it.p }
            if ("INDESTRUCTIBLE" !in attacker.keywords && (suffered >= attacker.remainingToughness ||
                    assigned.any { it.p > 0 && "DEATHTOUCH" in it.keywords })) deadA += attacker.entityId
            var remaining = attacker.p
            for (blocker in assigned.sortedByDescending(value)) {
                val lethal = if ("DEATHTOUCH" in attacker.keywords) 1 else blocker.remainingToughness
                val damage = minOf(remaining, lethal)
                if (damage > 0 && damage >= lethal && "INDESTRUCTIBLE" !in blocker.keywords) deadB += blocker.entityId
                remaining -= damage
            }
            if ("TRAMPLE" in attacker.keywords) playerDamage += remaining
        }
        return CombatEstimate(playerDamage,
            attackers.filter { it.entityId in deadA }.sumOf(value),
            blockers.filter { it.entityId in deadB }.sumOf(value), deadA, deadB,
            attackerLifeGain, defenderLifeGain)
    }

    private fun canBlock(blocker: EntityFeatures, attacker: EntityFeatures): Boolean {
        require((blocker.keywords + attacker.keywords).none {
            it in setOf("SHADOW", "HORSEMANSHIP", "FEAR", "INTIMIDATE") || it.startsWith("PROTECTION")
        }) { "Combat evasion/protection needs a qualified policy extension" }
        if (!blocker.creature || blocker.tapped) return false
        if ("UNBLOCKABLE" in attacker.keywords) return false
        return "FLYING" !in attacker.keywords || "FLYING" in blocker.keywords || "REACH" in blocker.keywords
    }
    private val EntityFeatures.creature get() = "CREATURE" in types
    private val EntityFeatures.p get() = (power ?: 0).coerceAtLeast(0)
    private val EntityFeatures.remainingToughness get() = ((toughness ?: 0) - damageMarked).coerceAtLeast(1)
}
