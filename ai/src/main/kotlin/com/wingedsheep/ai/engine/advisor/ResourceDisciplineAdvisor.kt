package com.wingedsheep.ai.engine.advisor

import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.isOpponentTo
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
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
        if (shouldHoldInertGraveyardExile(context)) return context.passScore - 1.0
        if (shouldHoldSelfOnlyDamageSweep(context)) return context.passScore - 1.0
        return null
    }

    /**
     * Hold conditional burn that is currently resolving below its printed ceiling unless the
     * reduced-rate shot plus conservative visible follow-up is already a credible lethal line.
     * Full-rate damage, immediate lethal, and exact visible reduced-rate lethal remain untouched.
     *
     * The current rate is derived from visible card text/game state when the printed conditional
     * can be evaluated directly. That matters while another spell is already on the stack: a
     * one-ply simulation may stop with the burn itself still pending, which must not turn a
     * speculative below-rate face shot into an unguarded action.
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
        val simulatedDamage = (beforeLife - result.state.lifeTotal(target.playerId)).coerceAtLeast(0)
        val dealt = currentConditionalDamage(context, source, printedDamage) ?: simulatedDamage
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
     * Infer the currently visible rate for common threshold-based conditional damage without
     * relying on the simulated spell to have resolved. This is intentionally property/text based:
     * it recognizes an artifact-count threshold, not any card name or matchup.
     */
    private fun currentConditionalDamage(
        context: CastContext,
        source: CardComponent,
        printedDamage: List<Int>,
    ): Int? {
        val thresholdMatch = ARTIFACT_THRESHOLD_REGEX.find(source.oracleText) ?: return null
        val threshold = parseNumberToken(thresholdMatch.groupValues[1]) ?: return null
        val artifactCount = context.state.getBattlefield(context.playerId).count { id ->
            val card = context.state.getEntity(id)?.get<CardComponent>() ?: return@count false
            card.typeLine.toString().contains("artifact", ignoreCase = true)
        }
        return if (artifactCount >= threshold) {
            printedDamage.maxOrNull()
        } else {
            printedDamage.minOrNull()
        }
    }

    /**
     * Hold targeted graveyard exile when a non-empty opposing graveyard has no concrete current or
     * intrinsic graveyard use. Merely containing creatures, spells, or a recursion spell is not a
     * reason to fire graveyard hate. A stack object actually using a card in that graveyard, or a
     * card with intrinsic from-graveyard functionality, remains actionable.
     *
     * Empty-graveyard activations are deliberately left to the normal evaluator so a separate
     * cantrip rider can still justify cashing the permanent without pretending the graveyard itself
     * had strategic value.
     */
    private fun shouldHoldInertGraveyardExile(context: CastContext): Boolean {
        val activation = context.action.action as? ActivateAbility ?: return false
        val target = activation.targets.singleOrNull() as? ChosenTarget.Player ?: return false
        if (!context.state.isOpponentTo(target.playerId, context.playerId)) return false

        val source = context.state.getEntity(activation.sourceId)?.get<CardComponent>() ?: return false
        val text = source.oracleText.lowercase()
        if ("graveyard" !in text || "exile" !in text || "target player" !in text) return false

        val graveyard = context.state.getGraveyard(target.playerId)
        if (graveyard.isEmpty()) return false
        val graveyardSet = graveyard.toSet()

        val stackUsesGraveyard = context.state.stack.any { stackId ->
            context.state.getEntity(stackId)?.get<TargetsComponent>()?.targets.orEmpty().any { chosen ->
                chosen is ChosenTarget.Card && chosen.cardId in graveyardSet
            }
        }
        if (stackUsesGraveyard) return false

        val intrinsicUtility = graveyard.any { id ->
            val card = context.state.getEntity(id)?.get<CardComponent>() ?: return@any false
            functionsFromOwnGraveyard(card)
        }
        return !intrinsicUtility
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

    private fun functionsFromOwnGraveyard(card: CardComponent): Boolean {
        val text = card.oracleText.lowercase()
        val name = card.name.lowercase()
        return listOf("flashback", "escape", "jump-start", "retrace", "disturb").any { it in text } ||
            "you may cast this card from your graveyard" in text ||
            ("$name is in your graveyard" in text && "return" in text) ||
            "return $name from your graveyard" in text
    }

    private fun parseNumberToken(token: String): Int? = token.toIntOrNull() ?: when (token.lowercase()) {
        "one" -> 1
        "two" -> 2
        "three" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "seven" -> 7
        "eight" -> 8
        "nine" -> 9
        "ten" -> 10
        else -> null
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
    private val ARTIFACT_THRESHOLD_REGEX = Regex(
        "\\bif\\s+you\\s+control\\s+([a-z]+|\\d+)\\s+or\\s+more\\s+artifacts\\b",
        RegexOption.IGNORE_CASE,
    )
    private const val MEANINGFUL_CREATURE_STATS = 4
}
