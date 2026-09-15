package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.budget.BudgetPolicy
import com.wingedsheep.ai.engine.budget.DecisionBudget
import com.wingedsheep.ai.engine.budget.LegacyBudgetPolicy
import com.wingedsheep.ai.engine.evaluation.BoardEvaluator
import com.wingedsheep.ai.engine.evaluation.BoardPresence
import com.wingedsheep.ai.engine.evaluation.EvaluationWeights
import com.wingedsheep.ai.engine.knowledge.HoldPolicy
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.IntentTag
import com.wingedsheep.ai.engine.knowledge.SelfRemovalValuation
import com.wingedsheep.ai.engine.knowledge.TimingVerdict
import com.wingedsheep.ai.engine.rollout.CandidateEvaluator
import com.wingedsheep.ai.engine.rollout.PlayoutPolicy
import com.wingedsheep.ai.engine.rollout.RolloutCandidateEvaluator
import com.wingedsheep.ai.engine.rollout.StaticCandidateEvaluator
import com.wingedsheep.ai.insight.AiActionOption
import com.wingedsheep.ai.insight.AiDecisionInsight
import com.wingedsheep.ai.insight.AiDecisionKind
import com.wingedsheep.ai.insight.AiInsightLabels
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.insight.FriendlyRemovalAudit
import com.wingedsheep.ai.insight.CombatPlan
import com.wingedsheep.ai.insight.CombatPlanTrace
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.stack.AbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.ManaSymbol
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import com.wingedsheep.sdk.scripting.ConvokePayment

/**
 * Chooses which [LegalAction] to take when the AI has priority.
 *
 * **One simulation per candidate, then a leaf score.** Candidates come from the enumerator, each is
 * simulated once to the quiet state it produces, and the best-scoring one wins if it beats passing.
 *
 * What "leaf score" means is [candidateEvaluator]'s business, and that is the whole Phase 7 seam:
 * [StaticCandidateEvaluator] is one `BoardEvaluator.evaluate` call — the greedy 1-ply AI, what
 * `AiProfile.LEGACY_V0` runs — and [RolloutCandidateEvaluator] replaces it with the mean of several
 * short playouts. Everything else in this file is identical either way, including the target
 * refinement, the hold policy and the [CardAdvisorRegistry] override path, which is the point: a
 * per-card advisor keeps working, over a much better base.
 *
 * There used to be a second, multi-ply alpha-beta pass here (`Searcher`). It was
 * unreachable — its `recommendDepth` gated on "can the opponent respond?", which opened
 * with `state.priorityPlayerId != playerId` and so was always false on our own priority —
 * and it carried a `Double.MIN_VALUE / 2` "−∞" sentinel that is actually `0.0`. It was
 * deleted rather than repaired, and Phase 7 replaced the mechanism outright rather than reviving
 * it. Its one good idea, extending the search on a close call, survives as
 * `RolloutSettings.criticalHorizonBonus`.
 *
 * Combat decisions are delegated to [CombatAdvisor].
 */
class Strategist(
    private val simulator: GameSimulator,
    private val evaluator: BoardEvaluator,
    private val combatAdvisor: CombatAdvisor = CombatAdvisor(simulator, evaluator),
    private val advisorRegistry: CardAdvisorRegistry = CardAdvisorRegistry(),
    /**
     * Phase 4a: only propose actions the AI can actually take.
     *
     * Two halves. Candidates come from [MeaningfulActionFilter] instead of the ad-hoc
     * `affordable && !isManaAbility` filter, so a spell whose mandatory target slot is empty stops
     * being a candidate; and [TargetSelection.fillableRequirements] fills the slots it *can*
     * rather than abandoning the whole spell. Together they close the "889 of 945 rejected actions were
     * `CastSpell: No valid targets available`" finding Phase 1 quantified and left open.
     */
    private val useMeaningfulFilter: Boolean = false,
    /** Phase 4b. How much search each decision may spend. */
    private val budgetPolicy: BudgetPolicy = LegacyBudgetPolicy,
    /**
     * Phase 6: structural card knowledge. [IntentCatalog.NONE] is the off position and leaves
     * both consumers here — [TargetSelection.rank] and the hold policy — at their pre-Phase-6
     * behaviour.
     */
    private val intents: IntentCatalog = IntentCatalog.NONE,
    /** [AiProfile.combatTricksWaitForBlocks] — passed straight through to [HoldPolicy]. */
    private val combatTricksWaitForBlocks: Boolean = false,
    /**
     * [AiProfile.holdRemovalForBetterTargets] — passed straight through to [HoldPolicy], which
     * hands it to [com.wingedsheep.ai.engine.knowledge.RemovalPatience].
     */
    private val holdRemovalForBetterTargets: Boolean = false,
    /**
     * [AiProfile.holdCountersForBetterSpells] — passed straight through to [HoldPolicy], which
     * hands it to [com.wingedsheep.ai.engine.knowledge.CounterPatience].
     */
    private val holdCountersForBetterSpells: Boolean = false,
    /** [AiProfile.cashCantripsInTheEndStep] — passed straight through to [HoldPolicy]. */
    private val cashCantripsInTheEndStep: Boolean = false,
    /**
     * [AiProfile.holdFlashPermanentsForAmbush] — passed straight through to [HoldPolicy], which
     * hands it to [com.wingedsheep.ai.engine.knowledge.AmbushWindow].
     */
    private val holdFlashPermanentsForAmbush: Boolean = false,
    /**
     * [AiProfile.holdExpiringGrantsForCombat] — passed straight through to [HoldPolicy], which
     * hands it to [com.wingedsheep.ai.engine.knowledge.ExpiringGrantWindow].
     */
    private val holdExpiringGrantsForCombat: Boolean = false,
    /**
     * The profile's `EvaluationWeights.boardPresence`. Only [HoldPolicy] reads it, to quote a
     * patience discount in the same units the leaf score prices board value in.
     */
    private val boardPresenceWeight: Double = EvaluationWeights.DEFAULT.boardPresence,
    /**
     * Phase 7: how a candidate's post-action state is scored. Defaults to the pre-Phase-7 leaf, so
     * a caller that doesn't opt in gets the greedy 1-ply AI unchanged.
     */
    private val candidateEvaluator: CandidateEvaluator = StaticCandidateEvaluator(evaluator),
    /** Phase 8: a fair complete world, sampled once before any candidate simulation. */
    private val stateSampler: ((GameState, EntityId) -> GameState)? = null,
    /**
     * Local testing mode: where the per-candidate scores this class computes go, instead of being
     * dropped once the winner is picked. Null in production, and the only thing that reads it is a
     * null check — the numbers are already being computed either way, so recording adds no search.
     */
    private val insightSink: AiInsightSink? = null,
) {
    private val holdPolicy = HoldPolicy(
        intents,
        tricksWaitForBlocks = combatTricksWaitForBlocks,
        holdRemovalForBetterTargets = holdRemovalForBetterTargets,
        holdCountersForBetterSpells = holdCountersForBetterSpells,
        cashCantripsInTheEndStep = cashCantripsInTheEndStep,
        holdFlashPermanentsForAmbush = holdFlashPermanentsForAmbush,
        holdExpiringGrantsForCombat = holdExpiringGrantsForCombat,
        boardPresenceWeight = boardPresenceWeight,
    )

    /**
     * Positions this player has already taken a non-pass action from, oldest first.
     *
     * The AI's only memory across decisions, and it exists for one reason: a leaf score cannot see
     * that it is being handed the same position over and over. See [StateProgress] and [remember].
     */
    private val positionsActedFrom = ArrayDeque<Long>()

    /**
     * A resource that exists only to establish an expiring condition is not a complete plan. Keep
     * the proven consumer across the intervening stack resolution so a combat-step shortcut cannot
     * let the condition expire after its cost has already been paid.
     */
    private var expiringConditionCommitment: ExpiringConditionCommitment? = null

    fun chooseAction(
        state: GameState,
        legalActions: List<LegalAction>,
        playerId: EntityId
    ): LegalAction {
        val startNanos = if (insightSink != null) System.nanoTime() else 0L
        val evaluationState = stateSampler?.invoke(state, playerId) ?: state
        committedExpiringConditionFollowUp(evaluationState, legalActions, playerId)?.let { return it }
        // Combat declaration steps need the CombatAdvisor to fill in attacker/blocker maps
        // even when there's only one legal action (which is the common case — the enumerator
        // returns a single DeclareAttackers/DeclareBlockers with an empty default map).
        val combatAction = legalActions.find { it.actionType == "DeclareAttackers" || it.actionType == "DeclareBlockers" }
        if (combatAction != null) {
            val budget = budgetPolicy.budgetFor(state, playerId, listOf(combatAction))
            return handleCombatDeclaration(evaluationState, combatAction, playerId, budget, startNanos)
        }

        if (legalActions.size == 1) {
            // Nothing to compare against, so no candidate expansion — but X still has to be chosen,
            // or this shortcut would submit the one available action at the enumerator's X=0.
            // Which shapes get an X bound is [expandXCostAbilities]' decision, not a second one:
            // a targeted activated ability is left alone here for the same reason it is there, so
            // that the engine's own choose-X pause (which DecisionResponder answers by simulation)
            // keeps handling it.
            val single = legalActions.first()
            val only = if (bindsXWithoutTheEnginesHelp(single)) {
                XCostSelection.bindBestX(state, single)
            } else {
                single
            }
            return only.copy(action = chooseCommittedTargets(state, only, playerId))
        }

        val pass = legalActions.find { it.actionType == "PassPriority" }
        val affordable = expandXCostAbilities(state, preferKickerVariants(candidatesFrom(legalActions)), playerId)

        if (affordable.isEmpty()) return pass ?: legalActions.first()

        val budget = budgetPolicy.budgetFor(state, playerId, affordable)
        val here = StateProgress.digest(evaluationState)

        // ── Pass 1: one simulation per candidate, to the quiet state it leads to ──
        // The anytime contract: candidates are simulated in order and the budget only cuts the
        // tail short. `maxByOrNull` over a partial list is still a valid (if worse) answer.
        //
        // The pass is simulated first and scored alongside the rest, so an evaluator that
        // allocates effort across candidates (Phase 7's sequential halving) treats "do nothing" as
        // the real option it is rather than as a separately-computed threshold.
        val leaves = mutableListOf<LegalAction>()
        val leafStates = mutableListOf<GameState>()
        val leafEvents = mutableListOf<List<com.wingedsheep.engine.core.GameEvent>>()
        // Local testing mode only: the candidates that never reached scoring. Reading the panel
        // without them makes the AI look like it never considered a play it in fact discarded.
        val dropped = if (insightSink != null) mutableListOf<AiActionOption>() else null
        var searched = 0
        if (pass != null) {
            val passSimulation = simulator.simulate(evaluationState, pass.action)
            leaves += pass
            // The pass leaf is positional — `leafScores.first()` below is this entry — so an
            // unfinished simulation cannot drop it. Fall back to the position we are standing in,
            // which is the same "do nothing" reference the no-pass branch uses.
            leafStates += if (passSimulation is SimulationResult.StoppedAtLimit) {
                evaluationState
            } else {
                passSimulation.state
            }
            leafEvents += passSimulation.events
        }
        for (action in affordable) {
            searched++
            val (materialized, simulation) = materialize(evaluationState, action, playerId, budget, here)
            val usable = when {
                // LegalAction affordability is necessarily a preview for costs such as convoke and
                // modal/additional payments. If materializing the concrete action cannot pass the
                // authoritative processor, it is not a candidate the AI may submit.
                simulation is SimulationResult.Illegal -> false
                // Automatic resolution ran out of transitions, so the retained board is mid-flight.
                // Scoring it would rank a candidate on a position it never actually reaches.
                simulation is SimulationResult.StoppedAtLimit -> false
                simulation is SimulationResult.NeedsDecision -> true
                // A line that walks back into a position we have already acted from has accomplished
                // nothing, whatever the leaf score says — and it is not a one-off mistake, because it
                // hands us back the very position that made it look good. Aphetto Alchemist untapping
                // itself is the degenerate case (`here`); two of them untapping each other is the same
                // thing one step longer. See [StateProgress].
                else -> StateProgress.digest(simulation.state)
                    .let { leaf -> leaf != here && leaf !in positionsActedFrom }
            }
            if (usable) {
                leaves += action.copy(action = materialized)
                leafStates += simulation.state
                leafEvents += simulation.events
            } else {
                val illegal = simulation is SimulationResult.Illegal
                dropped?.add(
                    droppedOption(
                        evaluationState, action, materialized,
                        note = when {
                            illegal -> "dropped — illegal once materialized"
                            simulation is SimulationResult.StoppedAtLimit ->
                                "dropped — simulation ran out of automatic transitions"
                            else -> "dropped — leads back to a position already acted from"
                        },
                        submittable = !illegal,
                    )
                )
            }
            // Every candidate cost a simulation whether or not it survived those filters, so the
            // anytime cut is taken on all of them. Checking only after a survivor was recorded
            // would let a board full of inert candidates run straight past the budget.
            if (budget.expired()) break
        }
        if (dropped != null) {
            for (action in affordable.drop(searched)) {
                dropped += droppedOption(
                    evaluationState, action, action.action,
                    note = "not searched — decision budget expired",
                    // Never materialized, so its targets are unfilled — not something to hand the
                    // processor. Listed so the panel can say the budget, not the AI, ruled it out.
                    submittable = false,
                )
            }
        }

        // ── Pass 2: score every leaf at once ──
        val leafScores = candidateEvaluator.scoreAll(evaluationState, leafStates, playerId, budget)
        val passScore = if (pass != null) {
            leafScores.first()
        } else {
            // No pass on offer: the "do nothing" reference is the current position itself.
            candidateEvaluator.score(evaluationState, evaluationState, playerId, budget)
        }

        // ── Pass 3: per-card timing and advisor adjustments, in raw evaluator units ──
        val firstCandidate = if (pass != null) 1 else 0
        // Terminal outcomes outrank every heuristic term. Preserve a legal one-action win first;
        // only when none exists, inspect one additional same-turn strategic action from each
        // already-resolved candidate leaf. This is deliberately not a general search horizon: it
        // runs only in our main phase, considers no land play or draw assumption, and accepts a
        // sequence only when canonical resolution of the second action actually ends the game.
        // A terminal leaf is action-created lethal only when the pass baseline does not already
        // reach the same win. Otherwise any legal action taken while a state-based or pending
        // effect is already ending the game would receive lethal priority, bypassing resource and
        // friendly-removal safeguards without contributing to the outcome.
        val passAlreadyWins = pass != null && leafStates.first().isWinningTerminalFor(playerId)
        val immediateWinIndex = if (!passAlreadyWins) {
            (firstCandidate until leaves.size)
                .filter { i -> leafStates[i].isWinningTerminalFor(playerId) }
                .maxByOrNull { i -> leafScores[i] }
        } else null
        val twoActionWinIndex = if (!passAlreadyWins && immediateWinIndex == null) {
            bestTwoActionSameTurnWinIndex(
                leaves = leaves,
                leafStates = leafStates,
                firstCandidate = firstCandidate,
                playerId = playerId,
                budget = budget,
            )
        } else null
        // A one-ply leaf cannot normally see combat damage from the priority window immediately
        // after attackers are declared. Before applying resource-hold floors, recognize the narrow
        // case where visible combat is already deterministic lethal and one fully materialized,
        // legal action changes that outcome. Immediate wins stay authoritative; among survival
        // actions the ordinary leaf score still chooses the least damaging continuation.
        val survivalIndex = if (immediateWinIndex == null && twoActionWinIndex == null) {
            (firstCandidate until leaves.size)
                .filter { i -> preventsVisibleImminentCombatLethal(evaluationState, leafStates[i], playerId) }
                .maxByOrNull { i -> leafScores[i] }
        } else null

        val adjusted = (firstCandidate until leaves.size).map { i ->
            val ordinaryAdjustment = adjustScore(
                evaluationState,
                leafStates[i],
                leaves[i],
                playerId,
                leafScores[i],
                passScore,
                passAlreadyWins,
                leafEvents[i],
            )
            Triple(
                leaves[i],
                leafScores[i],
                if (i == immediateWinIndex) {
                    ordinaryAdjustment.copy(
                        score = Double.MAX_VALUE,
                        note = "lethal policy: legal immediate action wins the game",
                    )
                } else if (i == twoActionWinIndex) {
                    ordinaryAdjustment.copy(
                        score = Double.MAX_VALUE / 2,
                        note = "lethal policy: legal bounded same-turn continuation wins the game",
                    )
                } else if (i == survivalIndex) {
                    ordinaryAdjustment.copy(
                        score = Double.MAX_VALUE / 4,
                        note = "imminent-lethal policy: legal action is required to survive visible combat",
                    )
                } else if (
                    passAlreadyWins &&
                    leafStates[i].gameOver &&
                    leafStates[i].winnerId == leafStates.first().winnerId &&
                    ordinaryAdjustment.score > passScore
                ) {
                    // Once passing already ends the game for the same winner, post-win board or
                    // resource improvements cannot make spending an action better than passing.
                    // Lower action scores remain lower so ordinary hold/audit evidence is retained.
                    ordinaryAdjustment.copy(
                        score = passScore,
                        note = "terminal policy: same-winner action capped at winning pass",
                    )
                } else {
                    ordinaryAdjustment
                },
            )
        }
        val scored = adjusted.map { (action, _, adjustment) -> action to adjustment.score }

        // On the opponent's end step, unspent mana is about to be wasted. Reduce the pass threshold
        // so the AI is more willing to use instants rather than letting mana evaporate.
        //
        // Phase 6 retires this blanket discount for agents with card knowledge: [HoldPolicy] makes
        // the same point per card, and better — a removal spell gets a *larger* end-step bonus,
        // while a pump that is about to wear off in cleanup gets a penalty instead of an
        // encouragement. Keeping both would double-count the first and cancel the second.
        val adjustedPassScore =
            if (!holdPolicy.isEnabled && !state.isActiveTurnFor(playerId) && state.step == Step.END) {
                passScore - 1.5
            } else {
                passScore
            }

        val best = scored.maxByOrNull { it.second }
        val takeAction = best != null && best.second > adjustedPassScore
        val chosen = if (takeAction) {
            remember(here)
            // Fill in targets on the returned action so the processor can execute it.
            // The committed target is chosen by simulation (not just the heuristic) so the
            // AI sees the real resolved board, including effects already on the stack.
            best.first
        } else {
            pass ?: legalActions.first()
        }

        if (takeAction) {
            val chosenIndex = leaves.indexOf(best.first)
            if (chosenIndex >= 0) {
                rememberExpiringConditionFollowUp(
                    evaluationState,
                    leafStates[chosenIndex],
                    best.first,
                    playerId,
                )
            }
        }

        if (insightSink != null) {
            recordPriorityInsight(
                state, evaluationState, playerId, startNanos,
                pass = pass, passScore = passScore, adjustedPassScore = adjustedPassScore,
                adjusted = adjusted, dropped = dropped.orEmpty(),
                chosenAction = if (takeAction) best.first else null,
            )
        }
        return chosen
    }

    /**
     * Find a proven terminal continuation one strategic action beyond the ordinary candidate leaf.
     *
     * Every first leaf was produced by the authoritative simulator above. Each second action is
     * enumerated from that resulting state, fully materialized with the same canonical payment and
     * target machinery, and resolved to the same quiet boundary. Enumerating every first action
     * compares both orders naturally; a nonterminal reverse order receives no lethal priority.
     */
    private fun bestTwoActionSameTurnWinIndex(
        leaves: List<LegalAction>,
        leafStates: List<GameState>,
        firstCandidate: Int,
        playerId: EntityId,
        budget: DecisionBudget,
    ): Int? {
        val initiallyAvailableSources = (firstCandidate until leaves.size)
            .mapNotNull { index -> strategicActionSource(leaves[index].action) }
            .toSet()
        return (firstCandidate until leafStates.size).firstOrNull { index ->
            if (strategicActionSource(leaves[index].action) == null) return@firstOrNull false
            val afterFirst = leafStates[index]
            if (!afterFirst.isActiveTurnFor(playerId) ||
                afterFirst.step !in setOf(Step.PRECOMBAT_MAIN, Step.POSTCOMBAT_MAIN) ||
                afterFirst.pendingDecision != null || afterFirst.stack.isNotEmpty()
            ) {
                return@firstOrNull false
            }

            val here = StateProgress.digest(afterFirst)
            val continuations = expandXCostAbilities(
                afterFirst,
                preferKickerVariants(candidatesFrom(simulator.getLegalActions(afterFirst, playerId))),
                playerId,
            ).filter { continuation ->
                strategicActionSource(continuation.action) in initiallyAvailableSources
            }

            continuations.any { continuation ->
                val (_, result) = materialize(afterFirst, continuation, playerId, budget, here)
                result !is SimulationResult.Illegal &&
                    result !is SimulationResult.StoppedAtLimit &&
                    result.state.isWinningTerminalFor(playerId)
            }
        }
    }

    private fun strategicActionSource(action: GameAction): EntityId? = when (action) {
        is CastSpell -> action.cardId
        is ActivateAbility -> action.sourceId
        else -> null
    }

    private fun GameState.isWinningTerminalFor(playerId: EntityId): Boolean =
        gameOver && winnerId in teamOf(playerId)

    /**
     * Whether [after] turns a publicly determined lethal attack into a nonlethal one at the final
     * priority window after attackers are declared. Candidate materialization and simulation have
     * already enforced targeting, protection, costs, and the action's actual effect; this comparison
     * only asks whether the resulting projected attackers still beat the defender's legal blocks.
     */
    private fun preventsVisibleImminentCombatLethal(
        before: GameState,
        after: GameState,
        playerId: EntityId,
    ): Boolean {
        if (!isFinalDefensiveCombatWindow(before, playerId) || !visibleDeclaredAttackIsLethal(before, playerId)) {
            return false
        }
        if (after.gameOver) return after.winnerId in after.teamOf(playerId)
        return !visibleDeclaredAttackIsLethal(after, playerId)
    }

    private fun isFinalDefensiveCombatWindow(state: GameState, playerId: EntityId): Boolean {
        if (state.phase != Phase.COMBAT || state.step != Step.DECLARE_ATTACKERS) return false
        if (state.activePlayerId == playerId || state.priorityPlayerId != playerId) return false
        val activePlayer = state.activePlayerId ?: return false
        return state.getEntity(activePlayer)?.has<AttackersDeclaredThisCombatComponent>() == true
    }

    private fun visibleDeclaredAttackIsLethal(state: GameState, playerId: EntityId): Boolean {
        if (state.phase != Phase.COMBAT || state.step != Step.DECLARE_ATTACKERS) return false
        val projected = state.projectedState
        val attackers = state.getBattlefield().filter { attackerId ->
            state.getEntity(attackerId)?.get<AttackingComponent>()?.defenderId == playerId &&
                projected.isCreature(attackerId)
        }
        if (attackers.isEmpty()) return false
        val blockers = CombatMath.getOpponentUntappedCreatures(state, projected, playerId)
        return CombatMath.calculateDamageThroughOptimalBlocking(
            state = state,
            projected = projected,
            attackers = attackers,
            opponentBlockers = blockers,
        ) >= state.lifeTotal(playerId)
    }

    /**
     * Hand the scores this decision produced to [insightSink], best-first, with passing sitting in
     * the ranking at its own score so the waterline every option had to clear is visible.
     */
    private fun recordPriorityInsight(
        state: GameState,
        evaluationState: GameState,
        playerId: EntityId,
        startNanos: Long,
        pass: LegalAction?,
        passScore: Double,
        adjustedPassScore: Double,
        adjusted: List<Triple<LegalAction, Double, AdjustedScore>>,
        dropped: List<AiActionOption>,
        chosenAction: LegalAction?,
    ) {
        val sink = insightSink ?: return
        val options = mutableListOf<AiActionOption>()
        if (pass != null) {
            options += AiActionOption(
                label = "Pass priority",
                actionType = "PassPriority",
                score = adjustedPassScore,
                rawScore = passScore.takeIf { it != adjustedPassScore },
                advantage = 0.0,
                chosen = chosenAction == null,
                baseline = true,
                note = if (adjustedPassScore != passScore) {
                    "opponent's end step — pass discounted so unspent mana isn't wasted"
                } else {
                    null
                },
                action = pass.action,
            )
        }
        for ((action, leafScore, adjustment) in adjusted) {
            options += AiActionOption(
                label = AiInsightLabels.describe(evaluationState, action, action.action),
                actionType = action.actionType,
                cardName = AiInsightLabels.cardName(evaluationState, action.action),
                targets = AiInsightLabels.targetNames(evaluationState, action.action),
                score = adjustment.score,
                rawScore = leafScore.takeIf { it != adjustment.score },
                advantage = adjustment.score - adjustedPassScore,
                chosen = action === chosenAction,
                note = adjustment.note,
                productionAdmissible = adjustment.productionAdmissible,
                productionRejectionReason = adjustment.productionRejectionReason,
                strategicSequencingAdjustment = adjustment.sequencingAdjustment,
                expiringConditionSequencingAdjustment = adjustment.expiringConditionSequencingAdjustment,
                friendlyRemovalAudit = adjustment.friendlyRemovalAudit?.copy(
                    selected = action === chosenAction,
                    selectionReason = when {
                        action === chosenAction -> "selected: highest adjusted option above passing"
                        adjustment.friendlyRemovalAudit.policyDisposition.startsWith("reject") ->
                            adjustment.friendlyRemovalAudit.policyDisposition
                        else -> "not selected: another option or holding had greater adjusted value"
                    },
                ),
                action = action.action,
            )
        }
        options.sortByDescending { it.score }
        options += dropped

        sink.record(
            evaluationState,
            AiDecisionInsight(
                kind = AiDecisionKind.PRIORITY,
                playerId = playerId,
                turnNumber = state.turnNumber,
                step = state.step.name,
                activePlayerId = state.activePlayerId,
                onOwnTurn = state.isActiveTurnFor(playerId),
                baselineLabel = "Pass priority",
                baselineScore = adjustedPassScore,
                chosenLabel = options.firstOrNull { it.chosen }?.label ?: "Pass priority",
                thinkTimeMs = (System.nanoTime() - startNanos) / 1_000_000,
                options = options,
            ),
        )
    }

    /**
     * An option that never reached scoring, so the panel can say what happened to it.
     *
     * [submittable] is false for a candidate the processor already rejected — it stays visible (the
     * AI did consider it) but carries no action, so the local testing mode can't offer to play a
     * line the engine would refuse.
     */
    private fun droppedOption(
        state: GameState,
        action: LegalAction,
        materialized: GameAction,
        note: String,
        submittable: Boolean,
    ): AiActionOption = AiActionOption(
        label = AiInsightLabels.describe(state, action, materialized),
        actionType = action.actionType,
        cardName = AiInsightLabels.cardName(state, materialized),
        targets = AiInsightLabels.targetNames(state, materialized),
        note = note,
        action = materialized.takeIf { submittable },
    )

    /**
     * The concrete action the AI would submit for [action], and the position it leads to.
     *
     * Targets come from [chooseCommittedTargets], and are re-committed **with** simulation when the
     * cheap pick turns out to be inert. That second attempt is the whole point of this function.
     * Below [com.wingedsheep.ai.engine.budget.BudgetTier.NORMAL] the committed targets are
     * [TargetSelection.rank]'s, which scores a target's board value and has no notion of whether the
     * ability does anything *to* it — so on the quiet opponent's-turn window this guard exists for,
     * it can hand back Aphetto Alchemist untapping itself while a tapped creature sits right there.
     * Writing the ability off on that pick would trade a loop for a missed play.
     *
     * Only the inert path pays: the extra simulations are bounded by
     * [RESCUE_TARGET_CANDIDATES] per requirement and are never reached by a candidate that already
     * does something.
     */
    private fun materialize(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
        budget: DecisionBudget,
        here: Long,
    ): Pair<GameAction, SimulationResult> {
        val materialized = chooseCommittedTargets(state, action, playerId, budget)
        val simulation = simulator.simulate(state, materialized)
        // Ordered so the budget that already refines pays nothing at all here — no digest, no
        // second simulation. `chooseAction` digests the leaf it keeps either way.
        if (budget.allowances.refineTargetsBySimulation) return materialized to simulation
        // The three results whose state `chooseAction` never digests. A StoppedAtLimit board is
        // mid-flight, so refining targets against its digest would compare against a position the
        // candidate does not actually reach — hand it back and let the caller drop it.
        if (simulation is SimulationResult.Illegal ||
            simulation is SimulationResult.NeedsDecision ||
            simulation is SimulationResult.StoppedAtLimit
        ) {
            return materialized to simulation
        }
        if (StateProgress.digest(simulation.state) != here) return materialized to simulation

        val refined = chooseCommittedTargets(state, action, playerId, budget, forceTargetRefinement = true)
        if (refined == materialized) return materialized to simulation
        return refined to simulator.simulate(state, refined)
    }

    /**
     * Record a position we are about to act from, so a later candidate that leads back to it is
     * recognised as the circle it is.
     *
     * Only positions we *act* from go in: passing may repeat as often as it likes, and recording it
     * would fill the memory with the windows where the AI does nothing. Bounded because a loop is
     * always short — the two-Alchemist cycle is length two — while a game is thousands of positions,
     * and because a digest carries its turn and step, so an entry can only ever match inside the
     * window where matching means going in circles.
     */
    private fun remember(digest: Long) {
        if (digest in positionsActedFrom) return
        positionsActedFrom.addLast(digest)
        if (positionsActedFrom.size > POSITION_MEMORY) positionsActedFrom.removeFirst()
    }

    /**
     * The candidate actions worth scoring.
     *
     * Mana abilities are excluded either way: activating one on its own is never the AI's move —
     * mana is produced as part of paying for something, by the engine's own auto-tap.
     */
    private fun candidatesFrom(legalActions: List<LegalAction>): List<LegalAction> =
        if (useMeaningfulFilter) {
            // The meaningful filter already removes ordinary mana abilities while retaining a
            // sacrifice-for-mana action whose irreversible cost makes it a strategic choice.
            MeaningfulActionFilter.filterMeaningful(legalActions).filter { it.affordable }
        } else {
            legalActions.filter {
                it.affordable &&
                    (!it.isManaAbility || it.additionalCostInfo?.costType == "SacrificePermanent") &&
                    it.actionType != "PassPriority"
            }
        }

    private fun handleCombatDeclaration(
        state: GameState,
        legalAction: LegalAction,
        playerId: EntityId,
        budget: DecisionBudget,
        startNanos: Long,
    ): LegalAction {
        val trace = if (insightSink != null) CombatPlanTrace() else null
        val action = when (legalAction.actionType) {
            "DeclareAttackers" -> combatAdvisor.chooseAttackers(state, legalAction, playerId, budget, trace)
            "DeclareBlockers" -> combatAdvisor.chooseBlockers(
                state, legalAction, playerId, useSimulation = true, budget = budget, trace = trace
            )
            else -> legalAction.action
        }
        if (trace != null) recordCombatInsight(state, legalAction, playerId, action, trace, startNanos)
        return legalAction.copy(action = action)
    }

    /**
     * Hand the combat plans local search simulated to [insightSink], measured against declaring
     * nothing.
     *
     * The trace is empty on the paths that skip local search altogether — a lethal alpha strike, an
     * opponent with no creatures to block with, no legal blockers — so the submitted plan is
     * recorded on its own with that said plainly, rather than as an unexplained single row.
     */
    private fun recordCombatInsight(
        state: GameState,
        legalAction: LegalAction,
        playerId: EntityId,
        action: GameAction,
        trace: CombatPlanTrace,
        startNanos: Long,
    ) {
        val sink = insightSink ?: return
        val attacking = legalAction.actionType == "DeclareAttackers"
        val chosenPlan: Any = when (action) {
            is DeclareAttackers -> action.attackers
            is DeclareBlockers -> action.blockers.filterValues { it.isNotEmpty() }
            else -> emptyMap<EntityId, EntityId>()
        }
        val describe: (CombatPlan) -> String = { plan ->
            when (plan) {
                is CombatPlan.Attack -> AiInsightLabels.describeAttackPlan(state, plan.attackers)
                is CombatPlan.Block -> AiInsightLabels.describeBlockPlan(state, plan.blockers)
            }
        }
        val planOf: (CombatPlan) -> Any = { plan ->
            when (plan) {
                is CombatPlan.Attack -> plan.attackers
                is CombatPlan.Block -> plan.blockers
            }
        }
        val actionOf: (CombatPlan) -> GameAction = { plan ->
            when (plan) {
                is CombatPlan.Attack -> DeclareAttackers(playerId, plan.attackers)
                is CombatPlan.Block -> DeclareBlockers(playerId, plan.blockers)
            }
        }
        val baselineLabel = if (attacking) "No attacks" else "No blocks"
        val baselineScore = trace.plans
            .firstOrNull { describe(it) == baselineLabel }
            ?.score
            ?: trace.plans.minOfOrNull { it.score }
            ?: 0.0

        val options = trace.plans
            .map { plan ->
                AiActionOption(
                    label = describe(plan),
                    actionType = legalAction.actionType,
                    score = plan.score,
                    advantage = plan.score - baselineScore,
                    chosen = planOf(plan) == chosenPlan,
                    baseline = describe(plan) == baselineLabel,
                    action = actionOf(plan),
                )
            }
            .sortedByDescending { it.score }
            .toMutableList()

        val chosenLabel = if (attacking) {
            AiInsightLabels.describeAttackPlan(state, (action as? DeclareAttackers)?.attackers.orEmpty())
        } else {
            AiInsightLabels.describeBlockPlan(state, (action as? DeclareBlockers)?.blockers.orEmpty())
        }
        if (options.none { it.chosen }) {
            options.add(
                0,
                AiActionOption(
                    label = chosenLabel,
                    actionType = legalAction.actionType,
                    chosen = true,
                    note = "heuristic seed — local search did not run for this declaration",
                    action = action,
                ),
            )
        }

        sink.record(
            state,
            AiDecisionInsight(
                kind = if (attacking) AiDecisionKind.DECLARE_ATTACKERS else AiDecisionKind.DECLARE_BLOCKERS,
                playerId = playerId,
                turnNumber = state.turnNumber,
                step = state.step.name,
                activePlayerId = state.activePlayerId,
                onOwnTurn = state.isActiveTurnFor(playerId),
                baselineLabel = baselineLabel,
                baselineScore = baselineScore,
                chosenLabel = chosenLabel,
                thinkTimeMs = (System.nanoTime() - startNanos) / 1_000_000,
                options = options,
            ),
        )
    }

    /**
     * Apply the two per-card adjustments to a leaf score, in raw evaluator units.
     *
     * Both are deltas on top of whatever the leaf said, which is what lets the rollout evaluator
     * slot in underneath without any of this changing: a hold-policy penalty and a `CardAdvisor`
     * override compose with a rollout mean exactly as they composed with a static evaluation.
     */
    private fun adjustScore(
        state: GameState,
        leafState: GameState,
        action: LegalAction,
        playerId: EntityId,
        leafScore: Double,
        passScore: Double,
        passAlreadyWins: Boolean,
        leafEvents: List<com.wingedsheep.engine.core.GameEvent>,
    ): AdjustedScore {
        val cardName = resolveCardName(state, action) ?: return AdjustedScore(leafScore)
        val cast = action.action as? CastSpell
        val card = cast?.let { state.getEntity(it.cardId)?.get<CardComponent>() }
        val intent = cast?.let { spell ->
            intents.forCast(cardName, spell)
        }
        val shouldHoldFriendlyRemoval = cast != null && card != null && intent != null &&
            SelfRemovalValuation.shouldHold(
                state, leafState, playerId, intent, card, cast, leafScore, passScore,
                passAlreadyWins, boardPresenceWeight,
            )
        val friendlyRemovalAudit = if (insightSink != null && cast != null && card != null && intent != null) {
            SelfRemovalValuation.assess(
                state, leafState, leafEvents, playerId, intent, card, cast, action.manaCostString,
                action.validTargets, leafScore, passScore, passAlreadyWins, boardPresenceWeight,
            )
        } else null
        // Compute the pure sequencing term even for a candidate that a later production hold gate
        // rejects. The floor remains authoritative; retaining the otherwise-applicable term merely
        // lets observational counterfactuals compare equivalent complete lines.
        val sequencing = strategicSequencingAdjustment(
            state, leafState, action, playerId, leafScore, passScore,
        )
        val expiringConditionSequencing = lifeGainEnhancedSequencingAdjustment(
            state, leafState, action, playerId, leafScore,
        )
        fun adjusted(
            score: Double,
            note: String? = null,
            productionAdmissible: Boolean = true,
            sequencingAdjustment: Double = sequencing,
        ) = AdjustedScore(
            score = score,
            note = note,
            friendlyRemovalAudit = friendlyRemovalAudit,
            productionAdmissible = productionAdmissible,
            productionRejectionReason = note.takeUnless { productionAdmissible },
            sequencingAdjustment = sequencingAdjustment,
            expiringConditionSequencingAdjustment = expiringConditionSequencing,
        )

        // Phase 6: what the board looks like after this resolves is only half the question; the
        // other half is whether this was the window — and, for removal, whether this was the target
        // worth spending the card on. The materialized action is what carries the committed
        // targets, so the hold policy is asked about the same spell the processor would receive.
        //
        // The activation is handed over for the same reason: an ability's window is a question about
        // the *ability*, and `cardName` only ever names the permanent it is printed on.
        val timing = holdPolicy.verdictFor(
            state, playerId, cardName,
            cast = action.action as? CastSpell,
            activation = action.action as? ActivateAbility,
        )
        if (timing is TimingVerdict.NoWindow) {
            // The card does nothing here, so nothing the simulation reports should make it beat
            // passing. See [TimingVerdict.NoWindow] for why this is a floor and not a penalty.
            return adjusted(passScore - 1.0, "hold policy: wrong window — floored below passing", false)
        }
        if (shouldHoldLandSacrifice(state, action.action, playerId, cardName)) {
            // A finite discount was not strong enough here: rollout damage and response-window
            // bonuses could still make Lava Dart spend a Mountain on a one-drop, or Fireblast
            // spend two Mountains on speculative face damage. This is a timing decision, not a
            // small material adjustment, so preserve the lands by flooring the candidate below
            // passing until the cast is lethal, near-lethal, or answers a real engine.
            return adjusted(
                passScore - 1.0,
                "land-sacrifice policy: low-value conversion — floored below passing",
                false,
            )
        }
        if (shouldHoldNullForcedSacrifice(state, leafState, action.action, playerId, cardName)) {
            // An edict without a sacrifice is legal, but a pure one has bought no strategic
            // result. Keep legality in the engine and value the result here, below passing.
            return adjusted(
                passScore - 1.0,
                "forced-sacrifice policy: no opposing permanent was sacrificed — floored below passing",
                false,
            )
        }
        if (holdRemovalForBetterTargets && shouldHoldFriendlyRemoval) {
            return adjusted(
                passScore - 1.0,
                "removal policy: friendly target lacks sufficient concrete downstream value",
                false,
            )
        }
        if (shouldDeferForLandUnlockedSequence(state, action, playerId)) {
            return adjusted(
                passScore - 1.0,
                "sequencing policy: legal land play unlocks a superior same-turn line",
                false,
            )
        }
        if (shouldDeferForExecutableExpiringConditionSequence(state, action.action, playerId)) {
            return adjusted(
                passScore - 1.0,
                "sequencing policy: executable expiring-condition line should begin with its enabler",
                false,
            )
        }
        if (shouldHoldNullLifeGain(state, leafState, action.action, playerId, cardName)) {
            // Raw life has board-score value even when no game object can currently exploit it.
            // A pure gain spell with no pressure, payoff, or enabled follow-up is therefore a
            // legal but strategically null resource conversion, and should remain in hand.
            return adjusted(
                passScore - 1.0,
                "lifegain policy: no pressure or concrete payoff — floored below passing",
                false,
            )
        }
        if (isSacrificeManaAction(action) && unlockedProductiveCastIds(state, leafState, playerId).isEmpty()) {
            // Sacrificing a permanent is an irreversible strategic cost, even when the ability is
            // a mana ability. Require an immediate, newly executable productive use rather than
            // consuming the body merely because mana can be generated.
            return adjusted(
                passScore - 1.0,
                "sacrifice-mana policy: no productive use unlocked — floored below passing",
                false,
            )
        }
        val timingDelta = (timing as? TimingVerdict.Adjust)?.delta ?: 0.0
        val timingReason = (timing as? TimingVerdict.Adjust)?.reason ?: "timing"
        val timingNote =
            if (timingDelta != 0.0) "hold policy $timingReason %+.2f".format(timingDelta) else null
        val sacrificeWindowDelta = threatenedSacrificeWindow(state, action.action, playerId)
        val sacrificeWindowNote = sacrificeWindowDelta.takeIf { it != 0.0 }
            ?.let { "sacrifices an opponent-targeted permanent %+.2f".format(it) }

        // Check for card-specific advisor override. Timing is applied outside it, so a per-card
        // advisor still sees the pure board score as its `defaultScore` and a card with both
        // keeps both.
        val sequencingNote = sequencing.takeIf { it != 0.0 }
            ?.let { "structural sequencing %+.2f".format(it) }

        val advisor = advisorRegistry.getAdvisor(cardName)
            ?: return adjusted(
                leafScore + timingDelta + sacrificeWindowDelta + sequencing,
                listOfNotNull(timingNote, sacrificeWindowNote, sequencingNote)
                    .joinToString("; ").ifEmpty { null },
                sequencingAdjustment = sequencing,
            )
        val context = CastContext(
            state = state,
            projected = state.projectedState,
            playerId = playerId,
            action = action,
            passScore = passScore,
            defaultScore = leafScore,
            evaluator = evaluator,
            simulator = simulator
        )
        val override = advisor.evaluateCast(context)
        val advisorNote = override?.let { "${advisor::class.simpleName} replaced the board score" }
        return adjusted(
            (override ?: leafScore) + timingDelta + sacrificeWindowDelta + sequencing,
            listOfNotNull(advisorNote, timingNote, sacrificeWindowNote, sequencingNote)
                .joinToString("; ").ifEmpty { null },
            sequencingAdjustment = sequencing,
        )
    }

    /**
     * A pure forced-sacrifice spell is strategically null when its targeted opponent's
     * battlefield is unchanged after full simulation. The test is intentionally about the
     * resolved result, not whether casting was legal: player-targeted edicts remain executable,
     * while the agent declines to spend one into an empty or otherwise non-sacrificing board.
     *
     * Strictly pure spells only. [IntentCatalog.isPureForcedSacrificeSpell] declines when any
     * additional effect exists, preserving casts whose draw/drain/token rider is useful even when
     * the sacrifice portion does nothing.
     */
    private fun shouldHoldNullForcedSacrifice(
        state: GameState,
        leafState: GameState,
        action: GameAction,
        playerId: EntityId,
        cardName: String,
    ): Boolean {
        val cast = action as? CastSpell ?: return false
        if (!intents.isPureForcedSacrificeSpell(cardName, cast.faceIndex)) return false

        val explicitOpponents = cast.targets.filterIsInstance<ChosenTarget.Player>()
            .map(ChosenTarget.Player::playerId)
            .filter { state.isOpponentTo(it, playerId) }
            .toSet()
        // Some internal planning leaves player selection implicit. A pure forced-sacrifice spell
        // still has no useful result when every opponent's battlefield is unchanged.
        val affectedOpponents = explicitOpponents.ifEmpty {
            state.turnOrder.filter { state.isOpponentTo(it, playerId) }.toSet()
        }
        if (affectedOpponents.isEmpty()) return false

        return affectedOpponents.all { opponentId ->
            state.controlledBattlefield(opponentId).toSet() ==
                leafState.controlledBattlefield(opponentId).toSet()
        }
    }

    private fun isSacrificeManaAction(action: LegalAction): Boolean =
        action.isManaAbility && action.additionalCostInfo?.costType == "SacrificePermanent"

    /** Hold a pure life-only resource until life or a visible event consumer makes it concrete. */
    private fun shouldHoldNullLifeGain(
        state: GameState,
        leafState: GameState,
        action: GameAction,
        playerId: EntityId,
        cardName: String,
    ): Boolean {
        val isPureLifeGain = when (action) {
            is CastSpell -> intents.isPureLifeGainSpell(cardName, action.faceIndex)
            is ActivateAbility -> intents.isPureLifeGainAbility(cardName, action.abilityId)
            else -> false
        }
        if (!isPureLifeGain) return false
        val pendingLifeGain = pendingGuaranteedLifeGain(state, playerId)
        if (visibleRepeatablePayoffCount(state, playerId) > 0) return false
        if (lifeGainNeededForSurvival(state, playerId, pendingLifeGain)) return false
        if (bestLifeGainEnhancedFollowUp(state, leafState, playerId, pendingLifeGain) != null) return false
        return true
    }

    /**
     * Conservative public-board survival test. Every opposing creature is counted at its projected
     * power because it can untap or lose summoning sickness before the next attack; overestimating
     * pressure merely preserves an emergency lifegain option and is safer than suppressing one.
     */
    private fun lifeGainNeededForSurvival(
        state: GameState,
        playerId: EntityId,
        pendingGuaranteedLifeGain: Int = 0,
    ): Boolean {
        val opposingPower = state.turnOrder.asSequence()
            .filter { state.isOpponentTo(it, playerId) }
            .flatMap { state.controlledBattlefield(it).asSequence() }
            .filter { state.getEntity(it)?.get<CardComponent>()?.isCreature == true }
            .sumOf { (state.projectedState.getPower(it) ?: 0).coerceAtLeast(0) }
        return opposingPower >= state.lifeTotal(playerId) + pendingGuaranteedLifeGain
    }

    /**
     * Life already certain from controlled stack objects, after conservative disruption checks.
     * Hidden card identities are never inspected: an opponent with any card in hand means the
     * pending object is not treated as guaranteed. A visible counter-capable permanent or opposing
     * counter object on the stack likewise prevents certainty. This intentionally under-claims;
     * it is used only to suppress a redundant resource expenditure.
     */
    private fun pendingGuaranteedLifeGain(state: GameState, playerId: EntityId): Int {
        if (state.stack.isEmpty()) return 0
        val opponents = state.turnOrder.filter { state.isOpponentTo(it, playerId) }
        if (opponents.any { state.getHand(it).isNotEmpty() }) return 0
        if (opponents.any { opponentId ->
                state.controlledBattlefield(opponentId).any { permanentId ->
                    val permanent = state.getEntity(permanentId) ?: return@any false
                    val name = permanent.get<CardComponent>()?.name ?: return@any false
                    intents.forPermanent(permanent, name).any { IntentTag.COUNTERSPELL in it.tags }
                }
            }
        ) return 0

        return state.stack.mapIndexedNotNull { index, stackId ->
            val stackObject = state.getEntity(stackId) ?: return@mapIndexedNotNull null
            val amount = intents.guaranteedControllerLifeGain(stackObject, playerId)
                ?: return@mapIndexedNotNull null
            val hasOpposingCounterAbove = state.stack.drop(index + 1).any { laterId ->
                val later = state.getEntity(laterId) ?: return@any false
                val controller = later.get<SpellOnStackComponent>()?.casterId
                    ?: later.get<TriggeredAbilityOnStackComponent>()?.controllerId
                    ?: later.get<ActivatedAbilityOnStackComponent>()?.controllerId
                    ?: later.get<AbilityOnStackComponent>()?.controllerId
                controller != null && state.isOpponentTo(controller, playerId) &&
                    intents.forStackObject(later)?.tags?.contains(IntentTag.COUNTERSPELL) == true
            }
            amount.takeUnless { hasOpposingCounterAbove }
        }.sum()
    }

    /**
     * The life event changed a currently executable spell from normal to enhanced, and the whole
     * resource-plus-consumer line is materially better than either stopping after the resource or
     * casting the consumer normally. This couples an expiring condition to its use inside the
     * valid window instead of valuing a theoretical option that the next decision may abandon.
     */
    private fun bestLifeGainEnhancedFollowUp(
        state: GameState,
        leafState: GameState,
        playerId: EntityId,
        pendingGuaranteedLifeGain: Int = pendingGuaranteedLifeGain(state, playerId),
    ): ExpiringConditionFollowUp? {
        if (state.getEntity(playerId)?.has<LifeGainedThisTurnComponent>() == true) return null
        if (pendingGuaranteedLifeGain > 0) return null
        if (leafState.getEntity(playerId)?.has<LifeGainedThisTurnComponent>() != true) return null

        val resourceOnlyScore = evaluator.evaluate(leafState, leafState.projectedState, playerId)
        return simulator.getLegalActions(leafState, playerId).asSequence().mapNotNull { next ->
            if (!next.affordable) return@mapNotNull null
            val nextCast = next.action as? CastSpell ?: return@mapNotNull null
            val nextName = leafState.getEntity(nextCast.cardId)?.get<CardComponent>()?.name ?: return@mapNotNull null
            val nextIntent = nextCast.faceIndex?.let { intents.forFaceIndex(nextName, it) }
                ?: intents.forName(nextName)
            if (nextIntent == null || IntentTag.LIFEGAIN_ENHANCED !in nextIntent.tags) return@mapNotNull null

            val enhancedAction = heuristicTargets(leafState, next, playerId)
            val enhanced = simulator.simulate(leafState, enhancedAction)
            if (enhanced is SimulationResult.Illegal || enhanced is SimulationResult.StoppedAtLimit) {
                return@mapNotNull null
            }
            val enhancedScore = evaluator.evaluate(enhanced.state, enhanced.state.projectedState, playerId)
            val downstream = simulator.getLegalActions(enhanced.state, playerId).asSequence()
                .filter { it.affordable && it.action is CastSpell }
                .mapNotNull { downstream ->
                    val result = simulator.simulate(
                        enhanced.state,
                        heuristicTargets(enhanced.state, downstream, playerId),
                    )
                    if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) {
                        return@mapNotNull null
                    }
                    (downstream.action as CastSpell).cardId to
                        evaluator.evaluate(result.state, result.state.projectedState, playerId)
                }
                .maxByOrNull { it.second }

            val normalState = simulator.getLegalActions(state, playerId).asSequence()
                .filter { it.affordable }
                .firstOrNull { direct ->
                    val directCast = direct.action as? CastSpell
                    directCast?.cardId == nextCast.cardId && directCast.faceIndex == nextCast.faceIndex
                }
                ?.let { direct -> simulator.simulate(state, heuristicTargets(state, direct, playerId)) }
                ?.takeUnless { it is SimulationResult.Illegal || it is SimulationResult.StoppedAtLimit }
                ?.state
            val normalScore = normalState?.let { evaluator.evaluate(it, it.projectedState, playerId) }
                ?: Double.NEGATIVE_INFINITY
            val enhancedCardsRecovered = enhanced.state.getHand(playerId).size - leafState.getHand(playerId).size + 1
            val normalCardsRecovered = normalState?.let {
                it.getHand(playerId).size - state.getHand(playerId).size + 1
            } ?: 0
            // LIFEGAIN_ENHANCED is currently emitted only for a conditional library-selection
            // count. Compare the cards actually recovered, not raw life-weighted board score.
            val materiallyEnhanced = enhancedCardsRecovered > normalCardsRecovered
            if (normalScore.isFinite() && !materiallyEnhanced) {
                return@mapNotNull null
            }
            val alternative = maxOf(resourceOnlyScore, normalScore)
            val projected = maxOf(
                enhancedScore,
                downstream?.second ?: Double.NEGATIVE_INFINITY,
                alternative + EXPIRING_CONDITION_CONSUMPTION_VALUE,
            )
            ExpiringConditionFollowUp(
                nextCast.cardId,
                nextCast.faceIndex,
                downstream?.first,
                projected,
            )
        }.maxByOrNull(ExpiringConditionFollowUp::projectedScore)
    }

    private fun committedExpiringConditionFollowUp(
        state: GameState,
        legalActions: List<LegalAction>,
        playerId: EntityId,
    ): LegalAction? {
        val commitment = expiringConditionCommitment ?: return null
        if (commitment.playerId != playerId || commitment.turn != state.turnNumber) {
            expiringConditionCommitment = null
            return null
        }
        if (state.getEntity(playerId)?.has<LifeGainedThisTurnComponent>() != true) {
            if (state.stack.isEmpty()) expiringConditionCommitment = null
            return null
        }
        val followUp = legalActions.firstOrNull { legal ->
            if (!legal.affordable) return@firstOrNull false
            val cast = legal.action as? CastSpell ?: return@firstOrNull false
            cast.cardId == commitment.cardId && cast.faceIndex == commitment.faceIndex
        }
        if (followUp == null) {
            if (state.stack.isEmpty()) expiringConditionCommitment = null
            return null
        }
        expiringConditionCommitment = null
        return followUp.copy(action = chooseCommittedTargets(state, followUp, playerId))
    }

    private fun rememberExpiringConditionFollowUp(
        state: GameState,
        leafState: GameState,
        action: LegalAction,
        playerId: EntityId,
    ) {
        val cardName = resolveCardName(state, action) ?: return
        val pureLifeGain = when (val gameAction = action.action) {
            is CastSpell -> intents.isPureLifeGainSpell(cardName, gameAction.faceIndex)
            is ActivateAbility -> intents.isPureLifeGainAbility(cardName, gameAction.abilityId)
            else -> false
        }
        if (!pureLifeGain) return
        val followUp = bestLifeGainEnhancedFollowUp(state, leafState, playerId) ?: return
        expiringConditionCommitment = ExpiringConditionCommitment(
            playerId,
            state.turnNumber,
            followUp.cardId,
            followUp.faceIndex,
        )
    }

    /** Casts made newly executable by the leaf, excluding legal-but-strategically-null effects. */
    private fun unlockedProductiveCastIds(
        state: GameState,
        leafState: GameState,
        playerId: EntityId,
    ): Set<EntityId> {
        fun executable(position: GameState): Set<EntityId> {
            val baseline = evaluator.evaluate(position, position.projectedState, playerId)
            return simulator.getLegalActions(position, playerId).asSequence()
                .filter { it.affordable && it.action is CastSpell }
                .mapNotNull { next ->
                    val cast = next.action as CastSpell
                    val filled = heuristicTargets(position, next, playerId)
                    val result = simulator.simulate(position, filled)
                    if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) {
                        return@mapNotNull null
                    }
                    // Executability alone is not a plan. If the resolved leaf is no better than
                    // retaining priority, the generated mana has no concrete productive consumer.
                    if (evaluator.evaluate(result.state, result.state.projectedState, playerId) <= baseline) {
                        return@mapNotNull null
                    }
                    val cardName = resolveCardName(position, next) ?: return@mapNotNull cast.cardId
                    cast.cardId.takeUnless {
                        shouldHoldNullForcedSacrifice(position, result.state, filled, playerId, cardName)
                    }
                }
                .toSet()
        }

        return executable(leafState) - executable(state)
    }

    /**
     * Small, structural option-value terms for actions whose payoff sits one priority decision
     * beyond the ordinary leaf. The normal evaluator still decides whether the action itself is
     * useful; these terms only break blind one-ply ties that the next legal-action set can prove.
     *
     * No card names are involved:
     *
     *  * a repeatable life-gain permanent is worth establishing before another creature when a
     *    visible repeatable counter/drain payoff is already in play;
     *  * a useful spell is worth sequencing before a still-affordable Storm spell;
     *  * a sacrifice mana ability is worth using when it unlocks a spell that was not affordable;
     *  * turning a spell into a land in hand has bounded early-game mana-development value.
     */
    private fun strategicSequencingAdjustment(
        state: GameState,
        leafState: GameState,
        action: LegalAction,
        playerId: EntityId,
        leafScore: Double,
        passScore: Double,
    ): Double {
        var delta = 0.0
        val cast = action.action as? CastSpell
        val cardName = resolveCardName(state, action)
        val intent = cardName?.let(intents::forName)

        if (cast != null && intent != null && intent.repeatable && IntentTag.LIFEGAIN in intent.tags) {
            val hasCreatureFollowUp = leafState.getHand(playerId).any { id ->
                leafState.getEntity(id)?.get<CardComponent>()?.isCreature == true
            }
            val hasVisiblePayoff = state.controlledBattlefield(playerId).any { id ->
                val permanent = state.getEntity(id) ?: return@any false
                val name = permanent.get<CardComponent>()?.name ?: return@any false
                intents.forPermanent(permanent, name).any { payoff ->
                    payoff.repeatable &&
                        (IntentTag.PUMP in payoff.tags || (payoff.opponentDamage ?: 0) > 0)
                }
            }
            if (hasCreatureFollowUp && hasVisiblePayoff) delta += TRIGGER_ENGINE_SETUP_VALUE
        }

        delta += lifeGainEnhancedSequencingAdjustment(state, leafState, action, playerId, leafScore)

        val landPlay = action.action as? PlayLand
        if (landPlay != null) {
            val setup = bestLandUnlockedPayoffSequence(state, playerId)
            if (setup != null && setup.landId == landPlay.cardId) {
                delta += (setup.projectedScore - leafScore).coerceAtLeast(0.0)
            }
            val expiringLine = bestLandUnlockedExpiringConditionLine(state, playerId)
            if (expiringLine != null && expiringLine.landId == landPlay.cardId) {
                delta += (expiringLine.projectedScore - leafScore).coerceAtLeast(0.0)
            }
        }

        if (cast != null && leafScore > passScore) {
            val stormFollowUp = simulator.getLegalActions(leafState, playerId).any { next ->
                if (!next.affordable) return@any false
                val nextCast = next.action as? CastSpell ?: return@any false
                leafState.getEntity(nextCast.cardId)?.get<CardComponent>()
                    ?.baseKeywords?.contains(com.wingedsheep.sdk.core.Keyword.STORM) == true
            }
            if (stormFollowUp) {
                val payoffCount = visibleRepeatablePayoffCount(state, playerId)
                delta += STORM_SETUP_VALUE + payoffCount * STORM_PAYOFF_EVENT_VALUE
            }
        }

        // Alternative costs trade one resource for tempo. Reward that tempo only when the leaf
        // proves the preserved mana immediately enables a meaningful spell; the normal evaluator
        // still prices the life/card/permanent paid, so an idle "free" cast gets no encouragement.
        if (cast?.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE) {
            val bestFollowUpManaValue = simulator.getLegalActions(leafState, playerId)
                .asSequence()
                .filter { it.affordable && it.action is CastSpell }
                .mapNotNull { next ->
                    val nextCast = next.action as CastSpell
                    leafState.getEntity(nextCast.cardId)?.get<CardComponent>()?.manaValue
                }
                .maxOrNull()
            if (bestFollowUpManaValue != null) {
                delta += (bestFollowUpManaValue * ALTERNATIVE_COST_TEMPO_PER_MANA)
                    .coerceAtMost(ALTERNATIVE_COST_TEMPO_CAP)
            }
        }

        if (isSacrificeManaAction(action)) {
            if (unlockedProductiveCastIds(state, leafState, playerId).isNotEmpty()) {
                delta += SACRIFICE_MANA_UNLOCK_VALUE
            }
        }

        val landsBefore = state.getHand(playerId).count { id ->
            state.getEntity(id)?.get<CardComponent>()?.isLand == true
        }
        val landsAfter = leafState.getHand(playerId).count { id ->
            leafState.getEntity(id)?.get<CardComponent>()?.isLand == true
        }
        val landsInPlay = state.projectedState.getBattlefieldControlledBy(playerId)
            .count { state.projectedState.hasType(it, "LAND") }
        if (landsAfter > landsBefore) {
            delta += when {
                landsInPlay <= 2 -> EARLY_LAND_DEVELOPMENT_VALUE
                landsInPlay <= 4 -> MIDGAME_LAND_DEVELOPMENT_VALUE
                else -> 0.0
            }
        }

        // A typecycle is a typed tutor even when the simulation's optional search has not yet
        // exposed the selected card at this scoring boundary. At an actual land shortage the
        // common land-type cycle has immediate option value; late game receives none.
        if (action.action is TypecycleCard && cardName != null &&
            intents.hasLandTypecycling(cardName) && landsInPlay <= 2 && landsBefore == 0
        ) {
            delta += EARLY_LAND_DEVELOPMENT_VALUE
        }

        // Omen/Adventure/split faces are read independently from the permanent face. A cheap tutor
        // face is a distinct mana-development option in an early shortage, without borrowing the
        // typecycling assumption or teaching the policy a card name.
        val faceIntent = cast?.faceIndex?.let { index ->
            cardName?.let { name -> intents.forFaceIndex(name, index) }
        }
        if (faceIntent != null && IntentTag.LAND_TUTOR in faceIntent.tags &&
            landsInPlay <= 2 && landsBefore == 0
        ) {
            delta += EARLY_LAND_DEVELOPMENT_VALUE
        }

        if (cast != null && intent != null && IntentTag.LIFEGAIN_ENHANCED in intent.tags &&
            state.getEntity(playerId)?.has<LifeGainedThisTurnComponent>() == true
        ) {
            delta += LIFEGAIN_ENHANCED_VALUE
        }

        // A tutor that replaces itself is neutral card flow; one that resolves into more than one
        // card has real immediate card-advantage value that a pass rollout can otherwise defer.
        if (cast != null && intent != null && IntentTag.TUTOR in intent.tags) {
            val cardsRecovered = leafState.getHand(playerId).size - state.getHand(playerId).size + 1
            if (cardsRecovered > 1) delta += (cardsRecovered - 1) * TUTOR_CARD_ADVANTAGE_VALUE
        }

        return delta
    }

    private fun lifeGainEnhancedSequencingAdjustment(
        state: GameState,
        leafState: GameState,
        action: LegalAction,
        playerId: EntityId,
        leafScore: Double,
    ): Double {
        val cast = action.action as? CastSpell ?: return 0.0
        val cardName = resolveCardName(state, action) ?: return 0.0
        val intent = intents.forCast(cardName, cast) ?: return 0.0
        if (IntentTag.LIFEGAIN !in intent.tags) return 0.0
        val followUp = bestLifeGainEnhancedFollowUp(state, leafState, playerId) ?: return 0.0
        return (followUp.projectedScore - leafScore).coerceAtLeast(0.0) +
            LIFEGAIN_FOLLOW_UP_UNLOCK_VALUE
    }

    /**
     * Validate one bounded main-phase line that the ordinary one-ply candidate set cannot see:
     * land, deployment/setup, then an immediate event-producing spell. The comparator uses the
     * same land and cards in focal-first order, so a deployment is preferred only when having it
     * present for the pending event creates material value rather than merely adding Storm count.
     *
     * Explicit mana-source variants are authoritative here. An auto-payment that spends the only
     * source needed by the second spell must not erase a complete legal same-turn sequence.
     *
     * SHARED ARGENTUM CHANGE: yes
     */
    private fun bestLandUnlockedPayoffSequence(
        state: GameState,
        playerId: EntityId,
    ): LandUnlockedPayoffSequence? {
        if (!state.isActiveTurnFor(playerId) || state.step !in setOf(Step.PRECOMBAT_MAIN, Step.POSTCOMBAT_MAIN)) {
            return null
        }

        val legalNow = simulator.getLegalActions(state, playerId)
        val immediateEvents = legalNow.filter { isImmediateEventCast(state, it) }
        if (immediateEvents.isEmpty()) return null
        if (immediateEvents.any { focal ->
                val name = resolveCardName(state, focal) ?: return@any false
                val cast = focal.action as CastSpell
                intents.isPureLifeGainSpell(name, cast.faceIndex) && lifeGainNeededForSurvival(state, playerId)
            }
        ) return null

        return legalNow.asSequence().mapNotNull { landAction ->
            val land = landAction.action as? PlayLand ?: return@mapNotNull null
            val landResult = simulator.simulate(state, land)
            if (landResult is SimulationResult.Illegal || landResult is SimulationResult.StoppedAtLimit) {
                return@mapNotNull null
            }
            val landState = landResult.state
            simulator.getLegalActions(landState, playerId).asSequence().mapNotNull { setup ->
                if (setup.action !is CastSpell) {
                    return@mapNotNull null
                }
                val setupId = (setup.action as CastSpell).cardId
                castStatesWithSourceChoices(landState, setup, playerId).asSequence().flatMap { setupState ->
                    simulator.getLegalActions(setupState, playerId).asSequence().filter { focal ->
                        val focalCast = focal.action as? CastSpell
                        focalCast != null && focalCast.cardId != setupId &&
                            isImmediateEventCast(setupState, focal) &&
                            immediateEvents.any { (it.action as CastSpell).cardId == focalCast.cardId }
                    }.flatMap { focal ->
                        val focalId = (focal.action as CastSpell).cardId
                        castStatesWithSourceChoices(setupState, focal, playerId).asSequence().mapNotNull { completed ->
                            val setupFirstScore = evaluator.evaluate(completed, completed.projectedState, playerId)
                            val focalFirstScore = focalFirstCompleteScore(
                                state, playerId, focalId, land.cardId, setupId,
                            ) ?: return@mapNotNull null
                            if (setupFirstScore <= focalFirstScore + MATERIAL_SEQUENCE_MARGIN) return@mapNotNull null
                            LandUnlockedPayoffSequence(
                                land.cardId, setupId, focalId, setupFirstScore, focalFirstScore,
                            )
                        }
                    }
                }.maxByOrNull(LandUnlockedPayoffSequence::projectedScore)
            }.maxByOrNull(LandUnlockedPayoffSequence::projectedScore)
        }.maxByOrNull(LandUnlockedPayoffSequence::projectedScore)
    }

    private fun isImmediateEventCast(state: GameState, legal: LegalAction): Boolean {
        val cast = legal.action as? CastSpell ?: return false
        val name = state.getEntity(cast.cardId)?.get<CardComponent>()?.name ?: return false
        val intent = intents.forCast(name, cast) ?: return false
        return intent.tags.any { it in IMMEDIATE_EVENT_TAGS } || (intent.opponentDamage ?: 0) > 0
    }

    private fun focalFirstCompleteScore(
        state: GameState,
        playerId: EntityId,
        focalId: EntityId,
        landId: EntityId,
        setupId: EntityId,
    ): Double? = simulator.getLegalActions(state, playerId).asSequence()
        .filter { (it.action as? CastSpell)?.cardId == focalId }
        .flatMap { castStatesWithSourceChoices(state, it, playerId).asSequence() }
        .mapNotNull { afterFocal ->
            val land = simulator.getLegalActions(afterFocal, playerId)
                .firstOrNull { (it.action as? PlayLand)?.cardId == landId } ?: return@mapNotNull null
            val afterLand = simulator.simulate(afterFocal, land.action)
            if (afterLand is SimulationResult.Illegal || afterLand is SimulationResult.StoppedAtLimit) return@mapNotNull null
            simulator.getLegalActions(afterLand.state, playerId).asSequence()
                .filter { (it.action as? CastSpell)?.cardId == setupId }
                .flatMap { castStatesWithSourceChoices(afterLand.state, it, playerId).asSequence() }
                .maxOfOrNull { evaluator.evaluate(it, it.projectedState, playerId) }
        }.maxOrNull()

    private fun castStatesWithSourceChoices(
        state: GameState,
        legal: LegalAction,
        playerId: EntityId,
    ): List<GameState> {
        val selected = heuristicTargets(state, legal, playerId) as? CastSpell ?: return emptyList()
        val sources = state.projectedState.getBattlefieldControlledBy(playerId).filter { id ->
            state.projectedState.hasType(id, "LAND") && state.getEntity(id)?.has<TappedComponent>() != true
        }
        val manaValue = legal.manaCostString?.let { ManaCost.parse(it).cmc } ?: sources.size
        val actions = buildList {
            add(selected)
            fun choose(start: Int, remaining: Int, chosen: MutableList<EntityId>) {
                if (remaining == 0) {
                    add(selected.copy(paymentStrategy = PaymentStrategy.Explicit(chosen.toList())))
                    return
                }
                for (index in start..sources.size - remaining) {
                    chosen += sources[index]
                    choose(index + 1, remaining - 1, chosen)
                    chosen.removeAt(chosen.lastIndex)
                }
            }
            if (manaValue in 1..sources.size) choose(0, manaValue, mutableListOf())
        }
        return actions.distinct().mapNotNull { action ->
            when (val result = simulator.simulate(state, action)) {
                is SimulationResult.Illegal, is SimulationResult.StoppedAtLimit -> null
                else -> result.state
            }
        }
    }

    /** Land first, then establish and consume a same-turn expiring condition. */
    private fun bestLandUnlockedExpiringConditionLine(
        state: GameState,
        playerId: EntityId,
    ): LandUnlockedExpiringConditionLine? {
        if (!state.isActiveTurnFor(playerId) || state.step !in setOf(Step.PRECOMBAT_MAIN, Step.POSTCOMBAT_MAIN)) {
            return null
        }
        if (lifeGainNeededForSurvival(state, playerId)) return null
        return simulator.getLegalActions(state, playerId).asSequence().mapNotNull { landAction ->
            val land = landAction.action as? PlayLand ?: return@mapNotNull null
            val landResult = simulator.simulate(state, land)
            if (landResult is SimulationResult.Illegal || landResult is SimulationResult.StoppedAtLimit) {
                return@mapNotNull null
            }
            val landState = landResult.state
            simulator.getLegalActions(landState, playerId).asSequence().mapNotNull { resource ->
                if (!resource.affordable) return@mapNotNull null
                val resourceName = resolveCardName(landState, resource) ?: return@mapNotNull null
                val pureLifeGain = when (val gameAction = resource.action) {
                    is CastSpell -> intents.isPureLifeGainSpell(resourceName, gameAction.faceIndex)
                    is ActivateAbility -> intents.isPureLifeGainAbility(resourceName, gameAction.abilityId)
                    else -> false
                }
                if (!pureLifeGain) return@mapNotNull null
                val resourceResult = simulator.simulate(
                    landState,
                    heuristicTargets(landState, resource, playerId),
                )
                if (resourceResult is SimulationResult.Illegal || resourceResult is SimulationResult.StoppedAtLimit) {
                    return@mapNotNull null
                }
                val followUp = bestLifeGainEnhancedFollowUp(
                    landState,
                    resourceResult.state,
                    playerId,
                ) ?: return@mapNotNull null
                val resourceCardId = when (val gameAction = resource.action) {
                    is CastSpell -> gameAction.cardId
                    is ActivateAbility -> gameAction.sourceId
                    else -> return@mapNotNull null
                }
                val followAction = simulator.getLegalActions(resourceResult.state, playerId)
                    .firstOrNull { legal ->
                        val cast = legal.action as? CastSpell
                        legal.affordable && cast?.cardId == followUp.cardId && cast.faceIndex == followUp.faceIndex
                    }
                val followResult = followAction?.let { legal ->
                    simulator.simulate(resourceResult.state, heuristicTargets(resourceResult.state, legal, playerId))
                }
                val afterFollow = followResult?.takeUnless {
                    it is SimulationResult.Illegal || it is SimulationResult.StoppedAtLimit
                }?.state
                val afterFollowScore = afterFollow?.let {
                    evaluator.evaluate(it, it.projectedState, playerId)
                } ?: followUp.projectedScore
                val downstreamCardId = afterFollow?.let { position ->
                    simulator.getLegalActions(position, playerId).asSequence().mapNotNull { next ->
                        if (!next.affordable || next.action !is CastSpell) return@mapNotNull null
                        val result = simulator.simulate(position, heuristicTargets(position, next, playerId))
                        if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) {
                            return@mapNotNull null
                        }
                        val score = evaluator.evaluate(result.state, result.state.projectedState, playerId)
                        val id = (next.action as CastSpell).cardId
                        (id to score).takeIf { score > afterFollowScore + MATERIAL_SEQUENCE_MARGIN }
                    }.maxByOrNull { it.second }?.first
                }
                LandUnlockedExpiringConditionLine(
                    land.cardId,
                    resourceCardId,
                    followUp.cardId,
                    downstreamCardId,
                    afterFollowScore,
                )
            }.maxByOrNull(LandUnlockedExpiringConditionLine::projectedScore)
        }.maxByOrNull(LandUnlockedExpiringConditionLine::projectedScore)
    }

    private fun isStormCast(state: GameState, action: LegalAction): Boolean {
        val cast = action.action as? CastSpell ?: return false
        return state.getEntity(cast.cardId)?.get<CardComponent>()?.baseKeywords
            ?.contains(com.wingedsheep.sdk.core.Keyword.STORM) == true
    }

    private fun shouldDeferForLandUnlockedSequence(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
    ): Boolean {
        val cast = action.action as? CastSpell ?: return false
        val payoffSequence = bestLandUnlockedPayoffSequence(state, playerId)
        if (payoffSequence != null) {
            if (cast.cardId == payoffSequence.setupCardId) return true
            val selectedFocal = simulator.getLegalActions(state, playerId)
                .firstOrNull { (it.action as? CastSpell)?.cardId == payoffSequence.focalCardId }
            if (selectedFocal != null && CastActionSemanticIdentity.equivalent(state, action, selectedFocal)) {
                return true
            }
        }
        val expiringLine = bestLandUnlockedExpiringConditionLine(state, playerId)
        return expiringLine != null && cast.cardId in setOfNotNull(
            expiringLine.resourceCardId,
            expiringLine.followUpCardId,
            expiringLine.downstreamCardId,
        )
    }

    private fun shouldDeferForExecutableExpiringConditionSequence(
        state: GameState,
        action: GameAction,
        playerId: EntityId,
    ): Boolean {
        val cast = action as? CastSpell ?: return false
        val line = simulator.getLegalActions(state, playerId).asSequence().mapNotNull { resource ->
            if (!resource.affordable) return@mapNotNull null
            val name = resolveCardName(state, resource) ?: return@mapNotNull null
            val pureLifeGain = when (val gameAction = resource.action) {
                is CastSpell -> intents.isPureLifeGainSpell(name, gameAction.faceIndex)
                is ActivateAbility -> intents.isPureLifeGainAbility(name, gameAction.abilityId)
                else -> false
            }
            if (!pureLifeGain) return@mapNotNull null
            val result = simulator.simulate(state, heuristicTargets(state, resource, playerId))
            if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) {
                return@mapNotNull null
            }
            bestLifeGainEnhancedFollowUp(state, result.state, playerId)
        }.maxByOrNull(ExpiringConditionFollowUp::projectedScore) ?: return false
        return cast.cardId == line.cardId || cast.cardId == line.downstreamCardId
    }

    private fun visibleRepeatablePayoffCount(state: GameState, playerId: EntityId): Int =
        state.controlledBattlefield(playerId).count { id ->
            val permanent = state.getEntity(id) ?: return@count false
            val name = permanent.get<CardComponent>()?.name ?: return@count false
            intents.forPermanent(permanent, name).any { payoff ->
                payoff.repeatable && IntentTag.LIFEGAIN_PAYOFF in payoff.tags
            }
        }

    /**
     * Price the irreversible land loss of graveyard/self alternative costs. The ordinary board
     * evaluator sees fewer lands, but direct damage can still swamp that one-ply loss well before
     * it matters. Preserve the resource unless the cast closes the game, leaves the opponent in
     * immediate reach, or removes a visibly high-impact engine.
     */
    private fun shouldHoldLandSacrifice(
        state: GameState,
        action: GameAction,
        playerId: EntityId,
        cardName: String,
    ): Boolean {
        val cast = action as? CastSpell ?: return false
        if (cast.alternativeCostType !in setOf(AlternativeCostType.FLASHBACK, AlternativeCostType.SELF_ALTERNATIVE)) {
            return false
        }
        val sacrificedLands = cast.additionalCostPayment?.sacrificedPermanents.orEmpty().count { id ->
            state.getEntity(id)?.get<CardComponent>()?.typeLine?.isLand == true
        }
        if (sacrificedLands == 0) return false

        val damage = intents.forName(cardName)?.removalReach ?: 0
        val target = cast.targets.singleOrNull()
        if (target is ChosenTarget.Player && state.isOpponentTo(target.playerId, playerId)) {
            val life = state.lifeTotal(target.playerId)
            if (damage >= life || life <= damage + NEAR_LETHAL_REACH) return false
        }
        if (target is ChosenTarget.Permanent) {
            val permanent = state.getEntity(target.entityId)
            val name = permanent?.get<CardComponent>()?.name
            val controller = state.projectedState.getController(target.entityId)
            val toughness = state.projectedState.getToughness(target.entityId)
            val markedDamage = permanent?.get<DamageComponent>()?.amount ?: 0
            val finishesOpposingCreature = controller?.let { state.isOpponentTo(it, playerId) } == true &&
                markedDamage > 0 && toughness != null && damage + markedDamage >= toughness
            if (finishesOpposingCreature) {
                return false
            }
            val isImportantEngine = permanent != null && name != null &&
                intents.forPermanent(permanent, name).any { intent ->
                    intent.repeatable && (intent.opponentDamage ?: 0) >= IMPORTANT_ENGINE_DAMAGE
                }
            if (isImportantEngine) {
                return false
            }
        }
        return true
    }

    /**
     * Reward converting a permanent that an opposing stack object already targets into an
     * additional-cost resource. The information is entirely public, and the adjustment is tied to
     * the payment rather than to a card name: Village Rites, an activated sacrifice outlet, and any
     * future equivalent all get the same window.
     *
     * Rollout scoring can otherwise average the immediate two-for-one with futures in which the
     * draw spell is held, even though passing lets the targeted permanent die for nothing in every
     * branch. Two evaluator points price the card of value recovered by cashing it in.
     */
    private fun threatenedSacrificeWindow(
        state: GameState,
        action: GameAction,
        playerId: EntityId,
    ): Double {
        val sacrificed = when (action) {
            is CastSpell -> action.additionalCostPayment?.sacrificedPermanents.orEmpty()
            is ActivateAbility -> action.costPayment?.sacrificedPermanents.orEmpty()
            else -> emptyList()
        }.toSet()
        if (sacrificed.isEmpty()) return 0.0

        val threatened = state.stack.any { stackId ->
            val stackObject = state.getEntity(stackId) ?: return@any false
            val controller = stackObject.get<SpellOnStackComponent>()?.casterId
                ?: stackObject.get<TriggeredAbilityOnStackComponent>()?.controllerId
                ?: stackObject.get<ActivatedAbilityOnStackComponent>()?.controllerId
                ?: stackObject.get<AbilityOnStackComponent>()?.controllerId
                ?: return@any false
            if (!state.isOpponentTo(controller, playerId)) return@any false
            stackObject.get<TargetsComponent>()?.targets.orEmpty()
                .filterIsInstance<ChosenTarget.Permanent>()
                .any { it.entityId in sacrificed }
        }
        return if (threatened) TARGETED_SACRIFICE_WINDOW else 0.0
    }

    /**
     * A leaf score after per-card adjustment, plus what did the adjusting.
     *
     * The [note] exists for the local testing mode: a candidate the AI passed over despite a strong
     * board score is only explicable if the panel can say *which* policy floored it.
     */
    private data class AdjustedScore(
        val score: Double,
        val note: String? = null,
        val friendlyRemovalAudit: FriendlyRemovalAudit? = null,
        val productionAdmissible: Boolean = true,
        val productionRejectionReason: String? = null,
        val sequencingAdjustment: Double = 0.0,
        val expiringConditionSequencingAdjustment: Double = 0.0,
    )

    /**
     * Pick the targets the AI actually commits to for a chosen targeted action, by simulation
     * rather than [TargetSelection]'s static rank. Simulating each candidate resolves the stack —
     * including spells/abilities already on it — so the evaluator scores the *real* board.
     *
     * This is what stops the classic blunder of aiming two "target creature can't block" effects
     * at the same creature: while the first is still on the stack the heuristic sees that creature
     * at full value and re-picks it, but a simulation that resolves both effects shows re-hitting it
     * gains nothing over neutralizing a second, still-able blocker (which [BoardPresence] now prices
     * lower). Requirements are resolved greedily — others held at their heuristic best — and only
     * the top `budget.allowances.targetCandidates` per requirement are simulated to bound cost. A
     * budget below [com.wingedsheep.ai.engine.budget.BudgetTier.NORMAL] skips the refinement and
     * keeps the heuristic pick: this loop is the most expensive thing a routine priority window can
     * pay for. [materialize] buys it back for the one case where the heuristic pick is not merely
     * worse but useless — see [forceTargetRefinement].
     *
     * Deliberately **not** used inside a rollout playout — see [PlayoutPolicy]. Simulating to pick
     * targets inside a simulation is what would make a playout quadratic.
     */
    private fun chooseCommittedTargets(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
        budget: DecisionBudget = DecisionBudget.legacy(),
        /**
         * Refine by simulation whatever the budget says, and raise the per-requirement cap to
         * [RESCUE_TARGET_CANDIDATES]. Set only by [materialize], and only for an action the cheap
         * pick already made inert — a tier that skips refinement can also cap candidates at 1,
         * which would leave the rescue with nothing to choose between.
         */
        forceTargetRefinement: Boolean = false,
    ): com.wingedsheep.engine.core.GameAction {
        val baseAction = withAutomaticPayments(state, action, playerId)
        if (TargetSelection.targetsAlreadyFilled(baseAction) != false) {
            return withSumGatedExilePayment(state, action, baseAction)
        }
        if (!budget.allowances.refineTargetsBySimulation && !forceTargetRefinement) {
            return heuristicTargets(state, action, playerId)
        }
        val targetInfos = TargetSelection.fillableRequirements(action, useMeaningfulFilter)
            ?: return heuristicTargets(state, action, playerId)

        // Heuristic baseline for every requirement, then refine each one by simulation.
        val chosenTargets = mutableListOf<com.wingedsheep.engine.state.components.stack.ChosenTarget>()
        val chosenIds = mutableSetOf<EntityId>()
        val chosenTargetIds = mutableListOf<EntityId>()
        for (info in targetInfos) {
            val available = if (info.mustDifferFromEarlier) {
                info.validTargets.filterNot(chosenIds::contains)
            } else {
                info.validTargets
            }
            val selectedId = available.maxByOrNull { TargetSelection.rank(state, it, playerId, intents) }
                ?: return heuristicTargets(state, action, playerId)
            chosenTargets += TargetSelection.toChosenTarget(state, info, selectedId, playerId)
            chosenIds += selectedId
            chosenTargetIds += selectedId
        }

        // Only paid for once a requirement actually has rival targets to simulate — every
        // requirement having at most one candidate is the common case, and digesting the whole
        // position for each affordable candidate to find that out is waste.
        val here by lazy(LazyThreadSafetyMode.NONE) { StateProgress.digest(state) }
        val targetCandidates =
            if (forceTargetRefinement) maxOf(budget.allowances.targetCandidates, RESCUE_TARGET_CANDIDATES)
            else budget.allowances.targetCandidates
        for (i in targetInfos.indices) {
            // A forced refinement ignores the clock: it runs only on the inert path, where the
            // alternative is dropping an ability that may well have had a productive target.
            if (budget.expired() && !forceTargetRefinement) break
            val info = targetInfos[i]
            val priorIds = chosenTargetIds.take(i).toSet()
            val candidates = info.validTargets
                .filterNot { info.mustDifferFromEarlier && it in priorIds }
                .sortedByDescending { TargetSelection.rank(state, it, playerId, intents) }
                .take(targetCandidates)
            if (candidates.size <= 1) continue
            val best = candidates.maxByOrNull { candidate ->
                val trial = chosenTargets.toMutableList()
                trial[i] = TargetSelection.toChosenTarget(state, info, candidate, playerId)
                val result = simulator.simulate(state, TargetSelection.applyTargets(baseAction, trial))
                // A target that resolves back into the position we are standing in is not a target
                // choice, it is a no-op wearing one — Aphetto Alchemist untapping itself. Rank it
                // below every real option, so `chooseAction` only ever drops the whole ability as
                // inert when *no* target does anything. A target whose simulation never finished
                // ranks there too, for the same reason: we cannot say what it does.
                result.scoreOrRankLast { leaf ->
                    if (StateProgress.digest(leaf) == here) {
                        Double.NEGATIVE_INFINITY
                    } else {
                        evaluator.evaluate(leaf, leaf.projectedState, playerId)
                    }
                }
            } ?: continue
            chosenTargets[i] = TargetSelection.toChosenTarget(state, info, best, playerId)
            chosenTargetIds[i] = best
        }
        return withSumGatedExilePayment(
            state, action, TargetSelection.applyTargets(baseAction, chosenTargets)
        )
    }

    /** The cheap target pick — one heuristic choice per requirement, no simulation. */
    private fun heuristicTargets(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
    ): com.wingedsheep.engine.core.GameAction = withSumGatedExilePayment(
        state, action,
        TargetSelection.fillHeuristically(
            state, action.copy(action = withAutomaticPayments(state, action, playerId)), playerId,
            fillPartialRequirements = useMeaningfulFilter, intents = intents
        ),
    )

    /**
     * Materialize deterministic payment choices carried by [LegalAction.additionalCostInfo].
     *
     * The enumerator exposes a Blight path as a distinct legal action, but the processor can only
     * distinguish it from the alternative-mana path through `AdditionalCostPayment.blightTargets`.
     * Keeping that choice only in UI metadata made the built-in AI submit the wrong branch for both
     * spells and activated abilities. The first candidate is deterministic and already filtered by
     * projected controller/type/counter legality.
     */
    private fun withAutomaticPayments(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
    ): GameAction {
        val gameAction = withAutomaticTapForGeneric(action, withAutomaticConvoke(action))
        val info = action.additionalCostInfo ?: return gameAction
        val existing = when (gameAction) {
            is CastSpell -> gameAction.additionalCostPayment
            is ActivateAbility -> gameAction.costPayment
            else -> null
        } ?: AdditionalCostPayment()
        fun attach(payment: AdditionalCostPayment): GameAction = when (gameAction) {
            is CastSpell -> gameAction.copy(additionalCostPayment = payment)
            is ActivateAbility -> gameAction.copy(costPayment = payment)
            else -> gameAction
        }

        // A one-card discard or sacrifice can encode most of the line's value: sacrificing the
        // creature a removal spell is already killing, or discarding a graveyard-recursive card
        // before the draw that turns it back on. `take(1)` makes both choices depend on incidental
        // zone order. For untargeted actions, simulate each legal payment and keep the board the
        // normal evaluator prefers. Targeted actions stay on the bounded legacy path because an
        // unfilled target would make every payment simulation illegal; their target-refinement pass
        // still evaluates the completed action afterwards.
        val strategicPool = when (info.costType) {
            "DiscardCard" -> info.validDiscardTargets.takeIf { info.discardCount == 1 }
            "SacrificePermanent" -> info.validSacrificeTargets.takeIf { info.sacrificeCount == 1 }
            else -> null
        }
        if (!action.requiresTargets && strategicPool != null && strategicPool.size > 1) {
            return strategicPool.take(AUTOMATIC_PAYMENT_CANDIDATES).maxByOrNull { chosen ->
                val payment = when (info.costType) {
                    "DiscardCard" -> existing.copy(discardedCards = listOf(chosen))
                    else -> existing.copy(sacrificedPermanents = listOf(chosen))
                }
                simulator.simulate(state, attach(payment)).scoreOrRankLast { leaf ->
                    evaluator.evaluate(leaf, leaf.projectedState, playerId)
                }
            }?.let { chosen ->
                when (info.costType) {
                    "DiscardCard" -> attach(existing.copy(discardedCards = listOf(chosen)))
                    else -> attach(existing.copy(sacrificedPermanents = listOf(chosen)))
                }
            } ?: gameAction
        }

        val payment = when (info.costType) {
            "Blight" -> existing.copy(blightTargets = info.validBlightTargets.take(1))
            "Behold" -> existing.copy(beheldCards = info.validBeholdTargets.take(info.beholdCount))
            "TapPermanents" -> existing.copy(tappedPermanents = info.validTapTargets.take(info.tapCount))
            "DiscardCard" -> existing.copy(discardedCards = info.validDiscardTargets.take(info.discardCount))
            "SacrificePermanent" -> existing.copy(
                sacrificedPermanents = info.validSacrificeTargets.take(info.sacrificeCount)
            )
            "BouncePermanent" -> existing.copy(bouncedPermanents = info.validBounceTargets.take(info.bounceCount))
            "ExileFromGraveyard" -> existing.copy(exiledCards = info.validExileTargets.take(info.exileMinCount))
            // Teamwork N (CR 702.194a): tap as *few* creatures as will clear the total-power
            // threshold, and among equally-few selections the *smallest* bodies — a board with a
            // 5/5 and a 1/1 paying teamwork 1 should turn the 1/1 sideways and keep the better
            // blocker up. So: the cheapest single creature that clears it on its own if there is
            // one, else greedy biggest-first to keep the count down. Creatures at 0 or negative
            // power never help a sum (and can only drag it down), so they are skipped.
            "TapForTotalPower" -> {
                val required = info.tapForPowerRequired
                val contributors = info.tapForPowerCreatures.filter { it.power > 0 }
                val cheapestSolo = contributors.filter { it.power >= required }.minByOrNull { it.power }
                if (cheapestSolo != null) {
                    existing.copy(variableCostPermanents = listOf(cheapestSolo.entityId))
                } else {
                    val chosen = mutableListOf<EntityId>()
                    var total = 0
                    for (creature in contributors.sortedByDescending { it.power }) {
                        if (total >= required) break
                        chosen += creature.entityId
                        total += creature.power
                    }
                    // Unreachable while the enumerator marks an unpayable teamwork variant
                    // unaffordable, but never submit a declaration we can't pay: fall back to the
                    // undeclared cast rather than an action the handler will reject.
                    if (total < required) {
                        return when (gameAction) {
                            is CastSpell -> gameAction.copy(declaredCostSlot = null)
                            else -> gameAction
                        }
                    }
                    existing.copy(variableCostPermanents = chosen)
                }
            }
            else -> return gameAction
        }
        return attach(payment)
    }

    /** The entity a chosen target points at, whichever arm of the union it is. */
    private fun targetEntityId(target: ChosenTarget): EntityId = when (target) {
        is ChosenTarget.Player -> target.playerId
        is ChosenTarget.Permanent -> target.entityId
        is ChosenTarget.Card -> target.cardId
        is ChosenTarget.Spell -> target.spellEntityId
    }

    /**
     * Pay a **sum-gated graveyard exile** cast cost — collect evidence N and its filtered sibling —
     * once the targets are known.
     *
     * Every other additional cost is filled by [withAutomaticPayments] before targeting, which is
     * where the AI picks its targets from. This one can't be: Urgent Necropsy's threshold *is* the
     * summed mana value of the targets ("collect evidence X, where X is the total mana value of the
     * permanents this spell targets"), so it isn't determined until CR 601.2f, after the targets are
     * announced at 601.2c. `exileWeightPerTarget` is what the enumerator ships for exactly that, and
     * this runs on the finished action rather than the bare one.
     *
     * Cards are spent highest-mana-value-first, the same choice `CollectEvidenceResolver.autoSelect`
     * makes, so the AI's selection and the engine's fallback can't disagree about what a payment
     * looks like. When the graveyard can't cover what was targeted the *targets* give way, trimmed
     * from the end until the price is affordable (down to none, which prices at 0 and is always
     * payable): per the printed ruling an unreachable threshold means the caster "can't choose to
     * collect evidence at all", so such a cast would simply be rejected — trimming turns a rejected
     * action into a smaller legal one, and never into a worse one, since a target the AI drops was
     * one it could not have kept.
     */
    private fun withSumGatedExilePayment(
        state: GameState,
        action: LegalAction,
        gameAction: GameAction,
    ): GameAction {
        val cast = gameAction as? CastSpell ?: return gameAction
        val info = action.additionalCostInfo ?: return gameAction
        if (info.costType != "CollectEvidence" && info.costType != "ExileForTotal") return gameAction

        // Most expensive first: the fewest cards that clear the floor.
        val pool = info.validExileTargets
            .filter { state.getEntity(it) != null }
            .sortedByDescending { info.exileCardWeights[it] ?: 0 }
        val available = pool.sumOf { info.exileCardWeights[it] ?: 0 }

        var targets = cast.targets
        var required = info.exileMinTotalWeight + targets.sumOf {
            info.exileWeightPerTarget[targetEntityId(it)] ?: 0
        }
        while (required > available && targets.isNotEmpty()) {
            targets = targets.dropLast(1)
            required = info.exileMinTotalWeight + targets.sumOf {
                info.exileWeightPerTarget[targetEntityId(it)] ?: 0
            }
        }
        if (required > available) return gameAction // nothing left to trim; the engine will refuse

        val chosen = mutableListOf<EntityId>()
        var total = 0
        for (cardId in pool) {
            if (total >= required) break
            chosen += cardId
            total += info.exileCardWeights[cardId] ?: 0
        }
        return cast.copy(
            targets = targets,
            additionalCostPayment = (cast.additionalCostPayment ?: AdditionalCostPayment())
                .copy(exiledCards = chosen),
        )
    }

    /**
     * Turn the Convoke candidates advertised by the legal-action enumerator into the payment the
     * cast handler consumes. Colored pips are satisfied first; remaining creatures pay only the
     * generic part of the cost, so the AI never submits an invalid overpayment.
     */
    private fun withAutomaticConvoke(action: LegalAction): GameAction {
        val cast = action.action as? CastSpell ?: return action.action
        val creatures = action.convokeCreatures.orEmpty()
        val costString = action.manaCostString
        if (!action.hasConvoke || creatures.isEmpty() || costString == null) return cast

        val cost = ManaCost.parse(costString)
        val coloredNeeded = cost.symbols
            .filterIsInstance<ManaSymbol.Colored>()
            .groupingBy { it.color }
            .eachCount()
            .toMutableMap()
        var genericNeeded = cost.genericAmount
        val payments = linkedMapOf<EntityId, ConvokePayment>()
        val unused = creatures.toMutableList()

        for ((color, count) in coloredNeeded) {
            repeat(count) {
                val index = unused.indexOfFirst { color in it.colors }
                if (index >= 0) {
                    val creature = unused.removeAt(index)
                    payments[creature.entityId] = ConvokePayment(color)
                }
            }
        }
        while (genericNeeded > 0 && unused.isNotEmpty()) {
            val creature = unused.removeAt(0)
            payments[creature.entityId] = ConvokePayment()
            genericNeeded--
        }
        if (payments.isEmpty()) return cast

        val existing = cast.alternativePayment ?: AlternativePaymentChoice.NONE
        return cast.copy(
            alternativePayment = existing.copy(
                convokedCreatures = existing.convokedCreatures + payments
            )
        )
    }

    /**
     * Fill in a tap-for-generic payment (improvise CR 702.126, waterbend) the enumerator offered.
     *
     * The enumerator counts these taps toward affordability, so a cast that is only payable *with*
     * them is offered as affordable; submitting it with an empty payment would then be rejected at
     * the mana step. Filling it here keeps the AI's chosen action payable.
     *
     * Deliberately **artifacts only**, even though a waterbend cost also accepts creatures: an
     * artifact is rarely doing anything else this turn, whereas tapping a creature silently gives
     * up an attack or a block. That covers improvise exactly (CR 702.126a is artifacts-only) and
     * leaves waterbend no worse off than before.
     *
     * And only when the taps are actually *needed*. An improvise tap is optional, and filling an
     * optional one can lose the cast: a tapped artifact stops being a mana source but credits only
     * {1}, so tapping Arc Reactor ({T}: Add {C}{C}{C}) for improvise on a board of three lands
     * turns a payable {5} into an unpayable {4} — the AI's own action then hard-errors at the mana
     * step. `tapForGenericRequired == false` says mana alone covers it, so we leave the artifacts
     * up; `true` means the enumerator's affordability check already validated tapping *all* of
     * them, so filling to the cap is safe. Null is the waterbend paths, whose taps pay a cost that
     * is owed either way — unchanged behaviour there.
     */
    private fun withAutomaticTapForGeneric(action: LegalAction, gameAction: GameAction): GameAction {
        if (!action.hasTapForGeneric) return gameAction
        if (action.tapForGenericRequired == false) return gameAction
        val cast = gameAction as? CastSpell ?: return gameAction
        val artifacts = action.tapForGenericPermanents.orEmpty().filterNot { it.isCreature }
        if (artifacts.isEmpty()) return gameAction
        val costString = action.manaCostString ?: return gameAction

        // One tap per generic mana, and never more than the mechanic's own cap (waterbend's {N}).
        val genericInCost = ManaCost.parse(costString).genericAmount
        val cap = minOf(action.tapForGenericAmount ?: genericInCost, genericInCost)
        if (cap <= 0) return gameAction

        val existing = cast.alternativePayment ?: AlternativePaymentChoice.NONE
        return cast.copy(
            alternativePayment = existing.copy(
                tapForGenericPermanents = artifacts.take(cap).mapTo(linkedSetOf()) { it.entityId }
            )
        )
    }

    /**
     * When both a normal cast and a kicker/offspring variant of the same card are
     * affordable, drop the normal variant. The kicker variant is strictly better —
     * it does everything the normal cast does plus the kicker bonus (e.g., offspring
     * creates an additional token). Keeping both inflates the candidate list and
     * triggers unnecessary deep search (the two variants score close together,
     * tripping the "close call" heuristic).
     */
    private fun preferKickerVariants(actions: List<LegalAction>): List<LegalAction> {
        // Collect cardIds that have an affordable CastWithKicker variant
        val kickedCardIds = mutableSetOf<EntityId>()
        for (action in actions) {
            if (action.actionType == "CastWithKicker") {
                val castSpell = action.action as? CastSpell ?: continue
                kickedCardIds.add(castSpell.cardId)
            }
        }
        if (kickedCardIds.isEmpty()) return actions

        // Remove the normal CastSpell variant for those cards
        return actions.filter { action ->
            if (action.actionType == "CastSpell") {
                val castSpell = action.action as? CastSpell ?: return@filter true
                castSpell.cardId !in kickedCardIds
            } else {
                true
            }
        }
    }

    /**
     * Expand an affordable X-cost action into one candidate [LegalAction] per concrete X value, so
     * the normal simulation-based scoring picks the best X instead of defaulting it away.
     *
     * Both halves matter, for different reasons:
     *
     * - **Activated abilities.** Submitting the enumerator's bare action runs it at `xValue = 0` —
     *   the Momir avatar would only ever look for a mana-value-0 creature and make nothing, so the
     *   AI would always pass it over. Only no-target abilities are expanded here; a targeted one
     *   keeps the engine's own choose-X decision path (see [bindsXWithoutTheEnginesHelp]). With no
     *   targets there is nothing to narrow, so the X values *are* the candidates, capped directly.
     * - **Spells.** There is no such decision path on the cast side: `CastSpell.xValue` left null is
     *   bound to 0 as the spell goes on the stack (CR 601.2b), so an un-expanded Day of Black Sun or
     *   Genesis Wave is cast for X=0 and does nothing. Targeted spells are expanded too, with their
     *   target lists narrowed to the chosen X — see [XCostSelection], which owns both the choice of
     *   which X values are worth a simulation and the narrowing that keeps the resulting action
     *   legal.
     *
     * An action with an additional cost we don't know how to pay passes through untouched.
     */
    private fun expandXCostAbilities(
        state: GameState,
        actions: List<LegalAction>,
        playerId: EntityId
    ): List<LegalAction> = actions.flatMap { action ->
        val base = action.action
        val maxX = action.maxAffordableX
        val info = action.additionalCostInfo
        val payableCost = info == null || info.costType == "DiscardCard"
        // The Momir avatar is expanded before the generic affordability guard below, because for it
        // "not worth activating" must mean *dropped*, not "fall through to the bare action". The
        // bare action carries `xValue = 0`, and with no mana available (`maxX == 0`) that is exactly
        // what the guard would let through — spending the once-each-turn activation and a card to
        // look for a mana-value-0 creature, a bucket that holds no castable card at all.
        if (isMomirAvatarActivation(state, action)) {
            return@flatMap momirActivations(state, action, playerId)
        }
        if (!action.hasXCost || maxX == null || maxX < 1 || !payableCost) {
            return@flatMap listOf(action)
        }
        if (!bindsXWithoutTheEnginesHelp(action)) return@flatMap listOf(action)
        when (base) {
            is ActivateAbility -> {
                val discard = chooseActivationDiscard(state, action, playerId)
                if (info?.costType == "DiscardCard" && info.discardCount > 0 && discard == null) {
                    return@flatMap emptyList()
                }
                // A no-target ability has nothing to narrow, so the X values are all there is.
                val xCandidates =
                    XCostSelection.candidateXValues(state, action).take(XCostSelection.MAX_X_CANDIDATES)
                xCandidates.map { x ->
                    action.copy(action = base.copy(xValue = x, costPayment = discard ?: base.costPayment))
                }
            }
            // An empty expansion means the spell is uncastable to any purpose right now (every
            // affordable X leaves a mandatory target slot empty). Dropping it beats offering the
            // bare action, which would be submitted at X=0 and fizzle.
            is CastSpell -> XCostSelection.expandToX(state, action)
            else -> listOf(action)
        }
    }

    /**
     * Whether the AI has to choose this action's X itself.
     *
     * A targeted activated ability is the one shape that must *not* be pre-bound: submitted bare it
     * reaches the engine's own choose-X pause, which [DecisionResponder] answers by simulating each
     * value — strictly better than anything decided here, and it has to pick targets in the same
     * breath anyway. Everything else defaults to `xValue = 0` if the AI stays quiet
     * (`CastSpell.xValue ?: 0`), so staying quiet is not an option.
     */
    private fun bindsXWithoutTheEnginesHelp(action: LegalAction): Boolean =
        !(action.action is ActivateAbility && action.requiresTargets)

    /**
     * Every activation of the Momir avatar worth simulating this turn — possibly none.
     *
     * Returning an empty list is the point: unlike the generic X expansion, "the strategy doesn't
     * want to activate" must not fall back to the enumerator's bare action, whose `xValue` is 0.
     * X=0 is never a candidate here, so the AI can't spend its once-each-turn activation and a card
     * on a mana value with nothing castable in it.
     */
    private fun momirActivations(
        state: GameState,
        action: LegalAction,
        playerId: EntityId,
    ): List<LegalAction> {
        val base = action.action as? ActivateAbility ?: return listOf(action)
        val info = action.additionalCostInfo
        // Shapes this strategy doesn't model (no {X}, or an additional cost we can't pay) fall back
        // to the untouched action rather than being silently dropped.
        if (!action.hasXCost || !(info == null || info.costType == "DiscardCard")) {
            return listOf(action)
        }
        val discard = chooseActivationDiscard(state, action, playerId)
        // No card to discard ⇒ the cost can't be paid at any X.
        if (info?.costType == "DiscardCard" && info.discardCount > 0 && discard == null) {
            return emptyList()
        }
        return momirXCandidates(state, action.maxAffordableX ?: 0, playerId).map { x ->
            action.copy(action = base.copy(xValue = x, costPayment = discard ?: base.costPayment))
        }
    }

    /**
     * Momir Basic is a resource-management format: the hand is nothing but lands, every activation
     * eats one, and the deck draws one card a turn — so the cheap early flips are the ones to skip
     * in order to still hold a card when the mana is there for a real threat.
     *
     * The published guidance agrees on both halves of that:
     *
     * - **Skip the first drops.** Start activating at four mana on the play, three on the draw
     *   (MTG Arena Zone's Momir guide; the Star City Games primer says the same in turn counts —
     *   the player on the play skips turns 1–3, the player on the draw skips turns 1–2).
     * - **Then top out around eight.** Mana values six through nine are the strong band, with seven
     *   and eight the recommended stopping points; nine gets swingy and ten-plus is only worth the
     *   extra turns of ramp if card draw showed up. So once eight is affordable, make an eight
     *   rather than pouring the surplus into a bigger, noisier flip.
     *
     * Below the target the whole available pool is used, which is just curving out.
     *
     * Sources: <https://mtgazone.com/momir-basic-midweek-magic-event-guide/>,
     * <https://articles.starcitygames.com/articles/the-momir-basic-primer/>.
     */
    private fun momirXCandidates(state: GameState, maxX: Int, playerId: EntityId): List<Int> {
        val wentFirst = state.turnOrder.firstOrNull() == playerId
        val firstStrategicX = if (wentFirst) MOMIR_FIRST_X_ON_THE_PLAY else MOMIR_FIRST_X_ON_THE_DRAW
        if (maxX < firstStrategicX) return emptyList()
        if (maxX >= MOMIR_TARGET_X) return listOf(MOMIR_TARGET_X)
        return listOf(maxX)
    }

    private fun isMomirAvatarActivation(state: GameState, action: LegalAction): Boolean {
        if (state.format !is Format.MomirBasic) return false
        val activation = action.action as? ActivateAbility ?: return false
        val card = state.getEntity(activation.sourceId)?.get<CardComponent>() ?: return false
        return card.name == MOMIR_AVATAR_NAME
    }

    /**
     * Choose which card(s) to discard for an activated ability whose additional cost is "discard a
     * card". Prefers a land as fodder (the safest discard, and in Momir Basic the whole hand is
     * basics); falls back to the first available card. Returns null when there is no discard cost.
     */
    private fun chooseActivationDiscard(
        state: GameState,
        action: LegalAction,
        playerId: EntityId
    ): com.wingedsheep.sdk.scripting.AdditionalCostPayment? {
        val info = action.additionalCostInfo ?: return null
        if (info.costType != "DiscardCard" || info.discardCount <= 0) return null
        val candidates = info.validDiscardTargets
        if (candidates.isEmpty()) return null
        val ranked = candidates.sortedByDescending { id ->
            if (state.getEntity(id)?.get<CardComponent>()?.isLand == true) 1 else 0
        }
        return com.wingedsheep.sdk.scripting.AdditionalCostPayment(
            discardedCards = ranked.take(info.discardCount)
        )
    }

    /** Resolve the card name from a legal action's underlying GameAction. */
    private fun resolveCardName(state: GameState, action: LegalAction): String? {
        val entityId = when (val gameAction = action.action) {
            is CastSpell -> gameAction.cardId
            is ActivateAbility -> gameAction.sourceId
            is CycleCard -> gameAction.cardId
            is TypecycleCard -> gameAction.cardId
            else -> return null
        }
        return state.getEntity(entityId)?.get<CardComponent>()?.name
    }

    private companion object {
        /** How many acted-from positions [remember] keeps. See it for why a short memory suffices. */
        const val POSITION_MEMORY = 32

        /**
         * Targets simulated per requirement when rescuing a candidate the cheap pick made inert.
         * A tier below [com.wingedsheep.ai.engine.budget.BudgetTier.NORMAL] can cap candidates at
         * 1, so the rescue needs its own floor; this is the legacy cap.
         */
        const val RESCUE_TARGET_CANDIDATES = 8

        /** Payment choices inspected for a one-card discard/sacrifice cost. */
        const val AUTOMATIC_PAYMENT_CANDIDATES = 8

        val IMMEDIATE_EVENT_TAGS = setOf(
            IntentTag.LIFEGAIN,
            IntentTag.DRAW,
            IntentTag.TOKEN_MAKER,
        )

        /** Establishing a visible event engine before its next creature enters. */
        const val TRIGGER_ENGINE_SETUP_VALUE = 4.0

        /** One useful spell now buys one additional Storm copy on the proven follow-up. */
        const val STORM_SETUP_VALUE = 2.0

        /** Extra value of each visible repeatable payoff receiving the additional Storm event. */
        const val STORM_PAYOFF_EVENT_VALUE = 2.0

        /** Option value of a sacrifice mana action that makes a previously unavailable spell legal. */
        const val SACRIFICE_MANA_UNLOCK_VALUE = 3.0

        /** Bounded value of converting a hand card into a needed early land. */
        const val EARLY_LAND_DEVELOPMENT_VALUE = 5.0

        /** The same conversion after the early shortage, where another land is less urgent. */
        const val MIDGAME_LAND_DEVELOPMENT_VALUE = 0.75

        /** One extra card beyond replacing the tutor itself. */
        const val TUTOR_CARD_ADVANTAGE_VALUE = 1.0

        /** Immediate option value of a structurally explicit life-gained-this-turn enhancement. */
        const val LIFEGAIN_ENHANCED_VALUE = 1.0

        /** Break a pass tie when this life event newly enables a concrete enhanced spell. */
        const val LIFEGAIN_FOLLOW_UP_UNLOCK_VALUE = 0.5

        /** Structural value of consuming a newly established condition before it expires. */
        const val EXPIRING_CONDITION_CONSUMPTION_VALUE = 2.0

        /** A compound line must clear noise in the static evaluator before it can borrow future value. */
        const val MATERIAL_SEQUENCE_MARGIN = 0.10

        /** Tempo value per mana of a spell enabled by paying a non-mana alternative cost. */
        const val ALTERNATIVE_COST_TEMPO_PER_MANA = 0.85

        /** Keeps preserved-mana option value bounded even for unusually expensive follow-ups. */
        const val ALTERNATIVE_COST_TEMPO_CAP = 5.5

        /** Value recovered by cashing in a permanent an opposing stack object already targets. */
        const val TARGETED_SACRIFICE_WINDOW = 2.0

        /** A burn spell leaving this much reach is close enough to preserve race conversion. */
        const val NEAR_LETHAL_REACH = 2

        /** Repeatable face damage per trigger that justifies spending land as removal. */
        const val IMPORTANT_ENGINE_DAMAGE = 2

        /** Where the avatar tops out — the recommended stopping point. See [momirXCandidates]. */
        const val MOMIR_TARGET_X = 8

        /** First activation on the play: turns 1–3 are skipped, so the first flip is a four. */
        const val MOMIR_FIRST_X_ON_THE_PLAY = 4

        /** First activation on the draw — one turn earlier, since the extra card pays for it. */
        const val MOMIR_FIRST_X_ON_THE_DRAW = 3

        const val MOMIR_AVATAR_NAME = "Momir Vig, Simic Visionary"
    }

    private data class ExpiringConditionFollowUp(
        val cardId: EntityId,
        val faceIndex: Int?,
        val downstreamCardId: EntityId?,
        val projectedScore: Double,
    )

    private data class ExpiringConditionCommitment(
        val playerId: EntityId,
        val turn: Int,
        val cardId: EntityId,
        val faceIndex: Int?,
    )

    private data class LandUnlockedPayoffSequence(
        val landId: EntityId,
        val setupCardId: EntityId,
        val focalCardId: EntityId,
        val projectedScore: Double,
        val comparisonScore: Double,
    )

    private data class LandUnlockedExpiringConditionLine(
        val landId: EntityId,
        val resourceCardId: EntityId,
        val followUpCardId: EntityId,
        val downstreamCardId: EntityId?,
        val projectedScore: Double,
    )

}
