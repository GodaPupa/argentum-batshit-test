package com.wingedsheep.ai.engine.advisor

import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.isOpponentTo
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId

/**
 * Small card-name-agnostic policy for irreversible resource conversions that a one-ply score can
 * otherwise overvalue. It is deliberately conservative: recognized bad conversions are floored
 * just below passing, and every unrecognized action returns null to preserve the normal evaluator.
 */
internal object ResourceDisciplineAdvisor : CardAdvisor {
    override val cardNames: Set<String> = emptySet()

    override fun evaluateCast(context: CastContext): Double? {
        if (shouldHoldReducedRateFaceBurn(context)) return context.passScore - 1.0
        if (shouldHoldSelfOnlyDamageSweep(context)) return context.passScore - 1.0
        return null
    }

    /**
     * Hold conditional burn that is currently resolving below its printed ceiling unless the
     * reduced-rate shot plus conservative visible follow-up is already a credible lethal line.
     * Full-rate damage, immediate lethal, and exact visible reduced-rate lethal remain untouched.
     */
    private fun shouldHoldReducedRateFaceBurn(context: CastContext): Boolean {
        val cast = context.action.action as? CastSpell ?: return false
        val target = cast.targets.singleOrNull() as? ChosenTarget.Player ?: return false
        if (!context.state.isOpponentTo(target.playerId, context.playerId)) return false

        val source = context.state.getEntity(cast.cardId)?.get<CardComponent>() ?: return false
        val printedDamage = fixedDamageNumbers(source)
        if (printedDamage.size < 2 || !source.oracleText.contains("instead", ignoreCase = true)) return false

        val result = context.simulator.simulate(context.state, cast)
        if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) return false
        val beforeLife = context.state.lifeTotal(target.playerId)
        val afterLife = result.state.lifeTotal(target.playerId)
        val dealt = beforeLife - afterLife
        val ceiling = printedDamage.maxOrNull() ?: return false
        if (dealt <= 0 || dealt >= ceiling || dealt >= beforeLife) return false

        val conservativeFollowUp = context.state.getHand(context.playerId)
            .asSequence()
            .filterNot { it == cast.cardId }
            .mapNotNull { context.state.getEntity(it)?.get<CardComponent>() }
            .sumOf(::conservativeVisibleFaceDamage)

        // Spending a low-efficiency interaction spell now is justified when the visible cards
        // already close the game at the rates they can conservatively be expected to deal.
        if (dealt + conservativeFollowUp >= beforeLife) return false

        // If the same damage could instead remove a meaningful opposing creature, preserving the
        // interaction is at least as important as preserving its future full-rate mode. Target
        // selection normally chooses that creature already; this guard prevents a face conversion
        // from winning solely on immediate life-total movement.
        val hasMeaningfulKillableCreature = context.state.getBattlefield().any { id ->
            val controller = context.projected.getController(id) ?: return@any false
            if (!context.state.isOpponentTo(controller, context.playerId) || !context.projected.isCreature(id)) {
                return@any false
            }
            val toughness = context.projected.getToughness(id) ?: return@any false
            val marked = context.state.getEntity(id)?.get<DamageComponent>()?.amount ?: 0
            val power = context.projected.getPower(id) ?: 0
            toughness - marked <= dealt && power + toughness >= MEANINGFUL_CREATURE_STATS
        }
        if (hasMeaningfulKillableCreature) return true

        // The remaining case is pure speculative face conversion. Holding keeps both the interaction
        // option and any future conditional ceiling (metalcraft and analogous printed "instead"
        // upgrades) available.
        return true
    }

    /**
     * Reject an artifact-paid damage sweep when the simulated activation damages/removes only our
     * own creatures. Incidental draws or similar payment-trigger compensation do not turn a
     * one-sided self-sweep into a productive board action.
     */
    private fun shouldHoldSelfOnlyDamageSweep(context: CastContext): Boolean {
        val activation = context.action.action as? ActivateAbility ?: return false
        if (activation.costPayment?.sacrificedPermanents.isNullOrEmpty()) return false
        val source = context.state.getEntity(activation.sourceId)?.get<CardComponent>() ?: return false
        val text = source.oracleText.lowercase()
        if (!("damage to each creature" in text || "damage to each creature without" in text)) return false

        val result = context.simulator.simulate(context.state, activation)
        if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) return false
        val after = result.state

        val creatures = context.state.getBattlefield().filter { context.projected.isCreature(it) }
        val opponentAffected = creatures.any { id ->
            val controller = context.projected.getController(id) ?: return@any false
            context.state.isOpponentTo(controller, context.playerId) && wasAffected(context.state, after, id)
        }
        if (opponentAffected) return false

        val ownAffected = creatures.any { id ->
            context.projected.getController(id) == context.playerId && wasAffected(context.state, after, id)
        }
        return ownAffected
    }

    private fun wasAffected(before: GameState, after: GameState, id: EntityId): Boolean {
        if (id !in after.getBattlefield()) return true
        val beforeDamage = before.getEntity(id)?.get<DamageComponent>()?.amount ?: 0
        val afterDamage = after.getEntity(id)?.get<DamageComponent>()?.amount ?: 0
        return afterDamage > beforeDamage
    }

    private fun fixedDamageNumbers(card: CardComponent): List<Int> =
        DAMAGE_REGEX.findAll(card.oracleText).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

    private fun conservativeVisibleFaceDamage(card: CardComponent): Int {
        val text = card.oracleText.lowercase()
        if ("damage" !in text || !("target" in text || "opponent" in text || "player" in text)) return 0
        val values = fixedDamageNumbers(card)
        if (values.isEmpty()) return 0
        return if (values.size >= 2 && "instead" in text) values.minOrNull() ?: 0 else values.maxOrNull() ?: 0
    }

    private val DAMAGE_REGEX = Regex("\\bdeals?\\s+(\\d+)\\s+damage\\b", RegexOption.IGNORE_CASE)
    private const val MEANINGFUL_CREATURE_STATS = 4
}
