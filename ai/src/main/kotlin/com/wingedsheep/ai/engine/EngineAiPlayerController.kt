package com.wingedsheep.ai.engine

import com.wingedsheep.ai.ActionResponse
import com.wingedsheep.ai.AiPlayerController
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.BattlefieldEntry
import com.wingedsheep.engine.handlers.effects.EnterTappedReplacements
import com.wingedsheep.engine.handlers.effects.EnterUntappedReplacements
import com.wingedsheep.engine.legalactions.utils.LandDropUtils
import com.wingedsheep.engine.legalactions.utils.CostEnumerationUtils
import com.wingedsheep.engine.legalactions.utils.SelectionCostPresentation
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.PlayerCantPlayFromHandComponent
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.ManaSymbol
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EntersAsCopy
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.PreventCycling
import com.wingedsheep.sdk.scripting.costs.CostAtom
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EngineAiPlayerController::class.java)

/**
 * AI controller powered by the built-in rules-engine [AIPlayer].
 *
 * Runs entirely locally with no API calls. Uses the engine's ActionProcessor, board evaluator,
 * [Strategist] and [CombatAdvisor] directly, configured by
 * [AiProfile.PRODUCTION_CANDIDATE_EXPIRING]
 * — rollout candidate evaluation over a determinized state, on a four-tier decision budget, with a
 * land drop priced as the card conversion it is, land *order* priced by the mana it makes usable,
 * a combat trick held until blocks are in and searched properly once they are, the race scored
 * in urgency rather than in turns, removal held for a target worth a card, and a creature priced by
 * what it can still do — marked damage wearing off at cleanup, "can't attack" costing the power —
 * a cantrip cashed in the window where its mana was going to evaporate anyway, a counterspell
 * held while the caster still has the mana for something bigger, and an activated ability whose
 * whole payoff expires at cleanup held for a window that can actually spend it.
 * Each superseded configuration is kept as the baseline its successor was measured against:
 * [AiProfile.PRODUCTION_CANDIDATE_COUNTERPATIENCE] ran immediately before it,
 * [AiProfile.PRODUCTION_CANDIDATE_CANTRIP] before it,
 * [AiProfile.PRODUCTION_CANDIDATE_BOARDVALUE] before it,
 * [AiProfile.PRODUCTION_CANDIDATE_PATIENCE] before it,
 * [AiProfile.PRODUCTION_CANDIDATE_RACECLOCK] before it,
 * [AiProfile.PRODUCTION_CANDIDATE_TRICKWINDOW] before it,
 * [AiProfile.PRODUCTION_CANDIDATE_LANDSEQ] before it,
 * [AiProfile.PRODUCTION_CANDIDATE_LANDDROP] before that,
 * [AiProfile.PRODUCTION_CANDIDATE_TUNED] before that, and the greedy 1-ply
 * [AiProfile.PRODUCTION] first of all.
 *
 * Requires a [gameStateProvider] to access the real (unmasked) [GameState] from
 * the [com.wingedsheep.gameserver.session.GameSession]. This allows the engine AI
 * to simulate actions and evaluate board states accurately.
 */
class EngineAiPlayerController(
    private val cardRegistry: CardRegistry,
    private val playerId: EntityId,
    private val gameStateProvider: () -> GameState?,
    /**
     * Local testing mode: publishes the scores the [Strategist] assigned each candidate, so a human
     * can browse what the AI weighed. Null in normal play.
     */
    insightSink: AiInsightSink? = null,
) : AiPlayerController {

    private val mulliganManaSolver = ManaSolver(cardRegistry)
    private val mulliganPredicateEvaluator = PredicateEvaluator()
    private val mulliganConditionEvaluator = ConditionEvaluator()
    private val mulliganCostUtils = CostEnumerationUtils(
        mulliganManaSolver,
        CostCalculator(cardRegistry, mulliganPredicateEvaluator, mulliganConditionEvaluator),
        mulliganPredicateEvaluator,
        cardRegistry,
    )

    private data class GuaranteedLandAccess(
        val landId: EntityId,
        val acquisitionCardId: EntityId,
        val acquiredLandId: EntityId,
    )

    private val aiPlayer =
        AIPlayer.create(
            cardRegistry, playerId, AiProfile.PRODUCTION_CANDIDATE_EXPIRING, insightSink = insightSink,
        )

    override fun chooseAction(
        state: ClientGameState,
        legalActions: List<LegalActionInfo>,
        pendingDecision: PendingDecision?,
        recentGameLog: List<String>
    ): ActionResponse {
        val gameState = gameStateProvider()
        if (gameState == null) {
            logger.warn("Engine AI: no game state available, passing priority")
            return ActionResponse.SubmitAction(PassPriority(playerId))
        }

        // Handle pending decisions using the engine AI's responder
        if (pendingDecision != null && pendingDecision.playerId == playerId) {
            val response = aiPlayer.respondToDecision(gameState, pendingDecision)
            logger.info("Engine AI decision: {} → {}", pendingDecision::class.simpleName, response::class.simpleName)
            return ActionResponse.SubmitDecision(playerId, response)
        }

        if (legalActions.isEmpty()) {
            return ActionResponse.SubmitAction(PassPriority(playerId))
        }

        // Single action → just take it, UNLESS it's a combat declaration
        // (DeclareAttackers/DeclareBlockers default to empty maps and need the AI to fill them in)
        if (legalActions.size == 1) {
            val action = legalActions.first().action
            val isCombatDeclaration = action is DeclareAttackers || action is DeclareBlockers
            if (!isCombatDeclaration) {
                return ActionResponse.SubmitAction(action)
            }
        }

        // Use the engine AI to choose the best action from the real game state
        val action = aiPlayer.chooseAction(gameState)
        logger.info("Engine AI chose: {}", action::class.simpleName)
        return ActionResponse.SubmitAction(action)
    }

    override fun decideMulligan(mulliganMessage: MulliganInfo): Boolean {
        // Momir Basic: every deck is 60 basic lands and the avatar's only cost is generic {X}, so
        // every opening hand is interchangeable — a mulligan can only shrink the hand (the London
        // mulligan bottoms a card each time) without ever improving it. Always keep. Without this,
        // the generic "6-7 lands is a flood → mulligan" heuristic below mulligans every all-lands
        // Momir hand down to the forced keep at mulliganCount >= 2, leaving the AI on a 5-card hand.
        if (gameStateProvider()?.format is Format.MomirBasic) return true

        val handSize = mulliganMessage.hand.size
        val mulliganCount = mulliganMessage.mulliganCount

        if (handSize <= 5 || mulliganCount >= 2) return true

        val cards = mulliganMessage.cards
        val landCount = mulliganMessage.hand.count { entityId ->
            cards[entityId]?.typeLine?.contains("Land", ignoreCase = true) == true
        }

        val guaranteedLandAccess = if (landCount == 1) {
            guaranteedSecondLandAccess(mulliganMessage.hand, cards)
        } else null
        // A deterministic, immediately fundable typecycling line counts as exactly one virtual
        // land for this minimum-land gate only. Every downstream color/castability heuristic keeps
        // using the physical land count, so recognizing the line cannot force an otherwise weak
        // hand to be kept.
        val effectiveLandCount = landCount + if (guaranteedLandAccess != null) 1 else 0
        val reasonableLandCount = effectiveLandCount in 2..5
        val coloredSources = mulliganMessage.hand
            .mapNotNull(cards::get)
            .filter { it.typeLine?.contains("Land", ignoreCase = true) == true }
            .flatMapTo(mutableSetOf(), ::colorsProducedBy)
        // Preserve the existing early horizon: at most turn three, reduced to the number of
        // deterministic opening-hand land drops. A recognized typecycling line contributes its
        // one virtual land here as well as at the minimum-land gate.
        val earlyHorizon = effectiveLandCount.coerceAtMost(3)
        val earlySpells = mulliganMessage.hand
            .mapNotNull { entityId -> cards[entityId]?.let { entityId to it } }
            .filterNot { (_, summary) -> summary.typeLine?.contains("Land", ignoreCase = true) == true }
            .mapNotNull { (entityId, summary) ->
                summary.manaCost?.let { runCatching { ManaCost.parse(it) }.getOrNull() }
                    ?.takeIf { it.cmc <= earlyHorizon }
                    ?.let { Triple(entityId, summary, it) }
            }
        val castableEarly = earlySpells.count { (_, _, cost) ->
            cost.symbols.all { symbol ->
                symbol.colors.isEmpty() || symbol is ManaSymbol.Phyrexian ||
                    symbol.colors.any { it in coloredSources }
            }
        }
        val coloredMismatch = earlySpells.size - castableEarly
        // A nominal two-land hand is not functional when nearly all of its cheap plays ask for a
        // color those lands cannot make. One incidental castable (often a reactive protection
        // spell) does not turn four stranded early spells into a keep.
        // No evidence is not positive evidence. Keep the established colored-source tolerance,
        // but require a nonempty early set before it can establish color functionality.
        val colorFunctional = earlySpells.isNotEmpty() &&
            (coloredMismatch < 3 || castableEarly >= 2)
        val developmentFunctional = earlySpells.any { (cardId, _, _) ->
            hasPayableEarlyDevelopmentLine(
                state = gameStateProvider() ?: return@any false,
                hand = mulliganMessage.hand,
                cardId = cardId,
                guaranteedLandAccess = guaranteedLandAccess,
            )
        }
        val keep = reasonableLandCount && colorFunctional && developmentFunctional
        logger.info(
            "Engine AI mulligan: hand={} cards, {} physical lands, {} virtual lands, " +
                "{}/{} early spells color-compatible, development={} → {}",
            handSize, landCount, effectiveLandCount - landCount, castableEarly, earlySpells.size,
            developmentFunctional,
            if (keep) "KEEP" else "MULLIGAN"
        )
        return keep
    }

    /**
     * Proves that [cardId] can be cast normally within the existing turn-three mulligan horizon
     * using only deterministic land resources from the retained opening hand. Targeted interaction
     * does not need a pregame target, but every controller-supplied additional cost must already be
     * payable. This is a reachability check, not a card-value or strategic-weight adjustment.
     */
    private fun hasPayableEarlyDevelopmentLine(
        state: GameState,
        hand: List<EntityId>,
        cardId: EntityId,
        guaranteedLandAccess: GuaranteedLandAccess?,
    ): Boolean {
        val actualHand = state.getHand(playerId).toSet()
        if (cardId !in actualHand) return false
        val component = state.getEntity(cardId)?.get<CardComponent>() ?: return false
        val definition = cardRegistry.getCard(component.cardDefinitionId) ?: return false
        if (definition.typeLine.isLand || definition.hasNoManaCost) return false
        if (!definition.typeLine.isPermanent && definition.script.spellEffect == null) return false
        // A restriction whose future satisfaction would require projected game development is not
        // deterministic opening-hand evidence. Ordinary instants, sorceries, and permanents have
        // no entry here; target requirements are deliberately handled separately.
        if (definition.script.castRestrictions.isNotEmpty()) return false

        val physicalLandIds = hand.filter { entityId ->
            entityId in actualHand &&
                state.getEntity(entityId)?.get<CardComponent>()?.typeLine?.isLand == true &&
                !LandDropUtils.playerCantPlayLands(
                    state,
                    playerId,
                    cardRegistry,
                    mulliganConditionEvaluator,
                    landCardId = entityId,
                )
        }
        val landPlans = deterministicLandPlans(physicalLandIds, guaranteedLandAccess)
        return landPlans.any { plan ->
            val planningState = stateAfterLandPlan(state, plan, guaranteedLandAccess) ?: return@any false
            val sources = mulliganManaSolver.findAvailableManaSources(planningState, playerId)
            mulliganManaSolver.solve(
                planningState,
                playerId,
                definition.manaCost,
                precomputedSources = sources,
            ) != null && canPayOpeningHandAdditionalCosts(planningState, cardId, definition.script.additionalCosts)
        }
    }

    private fun deterministicLandPlans(
        physicalLandIds: List<EntityId>,
        guaranteedLandAccess: GuaranteedLandAccess?,
    ): List<List<EntityId>> {
        val horizon = (physicalLandIds.size + if (guaranteedLandAccess != null) 1 else 0).coerceAtMost(3)
        if (horizon <= 0) return emptyList()
        if (guaranteedLandAccess != null) {
            // The cycling line fixes the first two land drops: play the sole land, cycle, then play
            // the acquired land. It never invents additional draws or a second acquisition.
            return listOf(listOf(guaranteedLandAccess.landId, guaranteedLandAccess.acquiredLandId))
        }
        return physicalLandIds.permutations(horizon)
    }

    private fun <T> List<T>.permutations(length: Int): List<List<T>> {
        if (length == 0) return listOf(emptyList())
        return flatMapIndexed { index, item ->
            (take(index) + drop(index + 1)).permutations(length - 1).map { listOf(item) + it }
        }
    }

    private fun stateAfterLandPlan(
        initial: GameState,
        landPlan: List<EntityId>,
        guaranteedLandAccess: GuaranteedLandAccess?,
    ): GameState? {
        if (landPlan.isEmpty()) return null
        var simulated = initial
        val played = mutableListOf<EntityId>()

        if (guaranteedLandAccess != null) {
            // Cycling consumes the acquisition card. The searched land is known to be available,
            // but its former library position is never consulted or used.
            simulated = simulated
                .removeFromZone(ZoneKey(playerId, Zone.HAND), guaranteedLandAccess.acquisitionCardId)
                .addToZone(ZoneKey(playerId, Zone.GRAVEYARD), guaranteedLandAccess.acquisitionCardId)
        }

        for ((turnIndex, landId) in landPlan.withIndex()) {
            if (turnIndex > 0) {
                for (playedLand in played) {
                    simulated = simulated.updateEntity(playedLand) { it.without<TappedComponent>() }
                }
            }
            val sourceZone = if (
                guaranteedLandAccess != null && landId == guaranteedLandAccess.acquiredLandId
            ) Zone.LIBRARY else Zone.HAND
            simulated = stateAfterPlanningLandPlay(simulated, landId, sourceZone) ?: return null
            played += landId
        }
        return simulated
    }

    /** Fail closed for uncommon mandatory-cost shapes not proven payable by the shared picker seam. */
    private fun canPayOpeningHandAdditionalCosts(
        state: GameState,
        castCardId: EntityId,
        costs: List<AdditionalCost>,
    ): Boolean = costs.all { cost ->
        when (cost) {
            is AdditionalCost.Atom -> when (val atom = cost.atom) {
                is CostAtom.PayLife -> state.lifeTotal(playerId) >= atom.amount
                is CostAtom.DiscardHand -> true
                is CostAtom.Mana -> {
                    val sources = mulliganManaSolver.findAvailableManaSources(state, playerId)
                    mulliganManaSolver.solve(state, playerId, atom.cost, precomputedSources = sources) != null
                }
                else -> {
                    val candidates = SelectionCostPresentation.candidates(
                        state,
                        playerId,
                        castCardId,
                        cost,
                        mulliganCostUtils,
                        mulliganPredicateEvaluator,
                    )
                    SelectionCostPresentation.selectionCount(cost) > 0 &&
                        SelectionCostPresentation.canPay(state, playerId, castCardId, cost, candidates)
                }
            }
            is AdditionalCost.Composite -> canPayOpeningHandAdditionalCosts(state, castCardId, cost.steps)
            is AdditionalCost.Choice -> cost.options.any {
                canPayOpeningHandAdditionalCosts(state, castCardId, listOf(it))
            }
            else -> false
        }
    }

    /**
     * Finds one guaranteed second-land line without looking at library order. The card must have a
     * registered typed-cycling ability, be in the player's actual hand, be payable solely by the
     * one land after that land enters untapped, and have at least one matching target somewhere in
     * the player's library. Ordinary cycling/draw effects have no search filter and never qualify.
     */
    private fun guaranteedSecondLandAccess(
        hand: List<EntityId>,
        cards: Map<EntityId, CardSummary>,
    ): GuaranteedLandAccess? {
        val state = gameStateProvider() ?: return null
        val actualHand = state.getHand(playerId).toSet()
        val landIds = hand.filter { entityId ->
            entityId in actualHand &&
                state.getEntity(entityId)?.get<CardComponent>()?.typeLine?.isLand == true
        }
        if (landIds.size != 1) return null

        val landId = landIds.single()
        if (state.getEntity(playerId)?.has<PlayerCantPlayFromHandComponent>() == true) return null
        if (LandDropUtils.playerCantPlayLands(
                state,
                playerId,
                cardRegistry,
                mulliganConditionEvaluator,
                landCardId = landId,
            )
        ) return null
        if (cyclingIsPrevented(state)) return null

        val stateAfterLand = stateAfterGuaranteedUntappedLandPlay(state, landId) ?: return null
        val soleLandSources = mulliganManaSolver.findAvailableManaSources(stateAfterLand, playerId)
            .filter { it.entityId == landId && !it.requiresSacrifice }
        if (soleLandSources.isEmpty()) return null

        for (cardId in hand) {
            if (cardId == landId || cardId !in actualHand || cards[cardId] == null) continue
            val component = state.getEntity(cardId)?.get<CardComponent>() ?: continue
            val definition = cardRegistry.getCard(component.cardDefinitionId) ?: continue
            val typedCycling = definition.keywordAbilities
                .filterIsInstance<KeywordAbility.Cycling>()
                .firstOrNull { it.searchFilter != null }
                ?: continue
            val targetFilter = typedCycling.searchFilter ?: continue

            // Only existence is observed. No positional information from the hidden library is
            // retained or used, so a library reorder cannot affect this decision.
            val targetId = state.getLibrary(playerId)
                .filter { candidateId ->
                    mulliganPredicateEvaluator.matches(
                        state,
                        state.projectedState,
                        candidateId,
                        targetFilter,
                        PredicateContext(controllerId = playerId, sourceId = cardId),
                    )
                }
                // Pick a representative by intrinsic definition identity, never by hidden library
                // position. The mulligan decision learns only that a legal searched-for land exists.
                .minByOrNull { candidateId ->
                    state.getEntity(candidateId)?.get<CardComponent>()?.cardDefinitionId.orEmpty()
                }
                ?: continue

            val payment = mulliganManaSolver.solve(
                stateAfterLand,
                playerId,
                typedCycling.cost,
                precomputedSources = soleLandSources,
            ) ?: continue
            if (payment.sources.any { it.entityId != landId || it.requiresSacrifice }) continue

            return GuaranteedLandAccess(landId, cardId, targetId)
        }
        return null
    }

    /**
     * Builds an immutable planning state after the sole land is played at the first legal
     * opportunity. Choice-dependent or copy-dependent land entries are not deterministic enough
     * for virtual-land credit. A land that is guaranteed to enter tapped cannot fund the line.
     */
    private fun stateAfterGuaranteedUntappedLandPlay(state: GameState, landId: EntityId): GameState? {
        val simulated = stateAfterPlanningLandPlay(state, landId, Zone.HAND) ?: return null
        if (simulated.getEntity(landId)?.has<TappedComponent>() == true) return null
        return simulated
    }

    /** Places one deterministic planned land drop and applies its real entry-tapped semantics. */
    private fun stateAfterPlanningLandPlay(
        state: GameState,
        landId: EntityId,
        sourceZone: Zone,
    ): GameState? {
        val component = state.getEntity(landId)?.get<CardComponent>() ?: return null
        val definition = cardRegistry.getCard(component.cardDefinitionId) ?: return null
        if (!definition.typeLine.isLand) return null
        if (definition.script.replacementEffects.any { it is EntersAsCopy || it is EntersWithChoice }) {
            return null
        }

        var simulated = state
            .removeFromZone(ZoneKey(playerId, sourceZone), landId)
            .updateEntity(landId) { it.with(ControllerComponent(playerId)) }
        simulated = BattlefieldEntry.place(simulated, playerId, landId)

        val entersUntapped = EnterUntappedReplacements.entersUntapped(simulated, landId, playerId)
        if (!entersUntapped) {
            val selfForcesTapped = definition.script.replacementEffects
                .filterIsInstance<EntersTapped>()
                .any { replacement ->
                    when {
                        replacement.payLifeCost != null ->
                            state.lifeTotal(playerId) <= replacement.payLifeCost!!
                        replacement.unlessCondition == null -> true
                        else -> !mulliganConditionEvaluator.evaluate(
                            simulated,
                            replacement.unlessCondition!!,
                            EffectContext(sourceId = landId, controllerId = playerId),
                        )
                    }
                }
            val globallyForcedTapped = EnterTappedReplacements.entersTapped(simulated, landId, playerId)
            if (selfForcesTapped || globallyForcedTapped) {
                simulated = simulated.updateEntity(landId) { it.with(TappedComponent) }
            }
        }
        return simulated
    }

    private fun cyclingIsPrevented(state: GameState): Boolean =
        state.getBattlefield().any { sourceId ->
            val component = state.getEntity(sourceId)?.get<CardComponent>() ?: return@any false
            cardRegistry.getCard(component.cardDefinitionId)
                ?.script?.staticAbilities?.any { it is PreventCycling } == true
        }

    private fun colorsProducedBy(card: CardSummary): Set<Color> {
        val basic = when (card.name) {
            "Plains" -> setOf(Color.WHITE)
            "Island" -> setOf(Color.BLUE)
            "Swamp" -> setOf(Color.BLACK)
            "Mountain" -> setOf(Color.RED)
            "Forest" -> setOf(Color.GREEN)
            else -> emptySet()
        }
        val text = card.oracleText.orEmpty()
        if ("mana of any color" in text.lowercase()) return Color.entries.toSet()
        val printed = Regex("""\{([WUBRG])}""").findAll(text)
            .mapNotNull { match -> Color.fromSymbol(match.groupValues[1].single()) }
            .toSet()
        return basic + printed
    }

    override fun chooseBottomCards(message: BottomCardsInfo): List<EntityId> {
        val count = message.cardsToPutOnBottom
        if (count <= 0 || message.hand.isEmpty()) return emptyList()

        val cards = message.cards

        // Rank cards: keep lands (up to 3) and cheap spells, bottom excess lands and expensive spells
        val lands = mutableListOf<EntityId>()
        val spells = mutableListOf<EntityId>()

        for (entityId in message.hand) {
            val isLand = cards[entityId]?.typeLine?.contains("Land", ignoreCase = true) == true
            if (isLand) lands.add(entityId) else spells.add(entityId)
        }

        val toBottom = mutableListOf<EntityId>()
        val targetLands = if (message.hand.size - count <= 5) 2 else 3
        val protectedLandAccess = guaranteedSecondLandAccess(message.hand, cards)
        val protectedIds = protectedLandAccess?.let {
            setOf(it.landId, it.acquisitionCardId)
        }.orEmpty()

        // Bottom excess lands
        if (lands.size > targetLands) {
            toBottom.addAll(lands.drop(targetLands))
        }

        // If we need more, bottom most expensive spells
        if (toBottom.size < count) {
            val expensive = spells.filterNot { it in protectedIds }.sortedByDescending { entityId ->
                LimitedPickScorer.parseCmc(cards[entityId]?.manaCost ?: "")
            }
            for (spell in expensive) {
                if (toBottom.size >= count) break
                toBottom.add(spell)
            }
        }

        // A normal London mulligan always leaves enough non-pair cards to bottom, but keep this
        // fallback total and legal if a caller supplies an unusual hand/count combination.
        if (toBottom.size < count) {
            for (entityId in message.hand) {
                if (toBottom.size >= count) break
                if (entityId !in toBottom && entityId !in protectedIds) toBottom.add(entityId)
            }
        }

        val result = toBottom.take(count)
        logger.info("Engine AI bottom cards: {} of {} → {}", result.size, message.hand.size,
            result.mapNotNull { cards[it]?.name })
        return result
    }

    override fun setDeckList(deckList: Map<String, Int>, archetype: String?) {
        // Engine AI evaluates board state directly — deck knowledge isn't needed
    }

    // =========================================================================
    // Draft Picking (Heuristic)
    // =========================================================================
    //
    // The color-aware per-card scoring lives in the shared [LimitedPickScorer] so the bot and the
    // human-facing "Suggest Pick" advisor stay in lockstep. This controller only orchestrates which
    // cards to take.

    private fun inferColors(pickedSoFar: List<CardSummary>): Map<Char, Int> =
        LimitedPickScorer.inferColors(pickedSoFar)

    private fun rateCard(card: CardSummary, colorCommitment: Map<Char, Int>, pickedSoFar: List<CardSummary>): Double =
        LimitedPickScorer.score(card, colorCommitment, pickedSoFar)

    override fun chooseDraftPick(
        pack: List<CardSummary>,
        pickedSoFar: List<CardSummary>,
        packNumber: Int,
        pickNumber: Int,
        picksRequired: Int,
        passDirection: String
    ): List<String> {
        val colorCommitment = inferColors(pickedSoFar)
        val ranked = pack.sortedByDescending { rateCard(it, colorCommitment, pickedSoFar) }
        val picks = ranked.take(picksRequired).map { it.name }
        logger.info("Engine AI draft pick P{}p{}: {} (from {} cards, committed colors: {})",
            packNumber, pickNumber, picks.joinToString(", "), pack.size,
            colorCommitment.entries.sortedByDescending { it.value }.take(2).joinToString("") { "${it.key}" })
        return picks
    }

    override fun chooseWinstonAction(
        pileCards: List<CardSummary>,
        pileIndex: Int,
        pileSizes: List<Int>,
        pickedSoFar: List<CardSummary>
    ): Boolean {
        val colorCommitment = inferColors(pickedSoFar)
        val totalRating = pileCards.sumOf { rateCard(it, colorCommitment, pickedSoFar) }
        // Take pile if average card quality is decent, or if there's a standout card
        val avgRating = totalRating / pileCards.size.coerceAtLeast(1)
        val bestCard = pileCards.maxByOrNull { rateCard(it, colorCommitment, pickedSoFar) }
        val bestRating = if (bestCard != null) rateCard(bestCard, colorCommitment, pickedSoFar) else 0.0

        // More willing to take larger piles (more cards = more value even if some are weak)
        val sizeBonus = (pileCards.size - 1) * 1.0
        val take = avgRating + sizeBonus >= 5.0 || bestRating >= 8.0

        // On the last pile, always take (forced by Winston rules, but just in case)
        val isLastPile = pileIndex == pileSizes.size - 1

        logger.info("Engine AI Winston pile {}: {} cards, avg={}, best={} → {}",
            pileIndex, pileCards.size, "%.1f".format(avgRating), "%.1f".format(bestRating),
            if (take || isLastPile) "TAKE" else "SKIP")
        return take || isLastPile
    }

    override fun chooseGridDraftPick(
        grid: List<CardSummary?>,
        availableSelections: List<String>,
        pickedSoFar: List<CardSummary>
    ): String {
        val colorCommitment = inferColors(pickedSoFar)

        // Rate each available selection by sum of card ratings in that row/column
        val best = availableSelections.maxByOrNull { selection ->
            val cards = getGridCards(grid, selection)
            cards.sumOf { rateCard(it, colorCommitment, pickedSoFar) }
        } ?: availableSelections.first()

        val cards = getGridCards(grid, best)
        logger.info("Engine AI grid pick: {} (cards: {})",
            best, cards.joinToString(", ") { it.name })
        return best
    }

    private fun getGridCards(grid: List<CardSummary?>, selection: String): List<CardSummary> {
        val indices = when {
            selection.startsWith("ROW_") -> {
                val row = selection.removePrefix("ROW_").toInt()
                listOf(row * 3, row * 3 + 1, row * 3 + 2)
            }
            selection.startsWith("COL_") -> {
                val col = selection.removePrefix("COL_").toInt()
                listOf(col, col + 3, col + 6)
            }
            else -> emptyList()
        }
        return indices.mapNotNull { if (it < grid.size) grid[it] else null }
    }

}
