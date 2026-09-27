package com.wingedsheep.gym.pest

import com.wingedsheep.ai.engine.advisor.modules.PestMonsterTronPublicPolicy
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.actorinput.ActorPublicCards
import com.wingedsheep.gym.actorinput.UnsupportedPolicyInput
import com.wingedsheep.gym.actorinput.isType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

sealed interface PestMonsterTronActorDecision {
    data class Proposed(val proposal: ActorProposal) : PestMonsterTronActorDecision

    /** The original advisor returned null here. A complete separately qualified pilot is required. */
    data object NoOverride : PestMonsterTronActorDecision
}

/**
 * Mechanical receiving adapter for the existing Monster Tron advisor's pending decisions.
 *
 * Policy facts come only from detached ActorInput; the search witness carries no extra card data.
 * The exact frozen two-player Pest/Monster
 * scope has no teammate or name-changing effects; extending that scope is not qualified here.
 * The copied calculation preserves the supplied question order and null/defer outcomes. Search
 * answers require a separate same-question witness from the trusted typed proof projection;
 * cardInfo and sourceName alone grant no library-search authority. NoOverride remains fail-closed.
 * The archived MC10 raw/canonical difference remains an observed failure of the older projection;
 * exact physical-handle equivalence on this receiving source still requires a fresh bounded test.
 * NoOverride does not invent a pass or default response. Action ranking, the base profile, mulligans,
 * all other choices, initialized-deck binding and whole-runtime admission remain separate.
 */
class PestMonsterTronActorDecisions(
    private val expectedEpoch: ActorEpoch,
    private val expectedActor: EntityId,
) {
    fun respond(input: ActorInput): PestMonsterTronActorDecision = respondCurrent(input, null)

    internal fun respond(search: PestMonsterVerifiedSearch): PestMonsterTronActorDecision =
        respondCurrent(search.input, search)

    private fun respondCurrent(input: ActorInput, search: PestMonsterVerifiedSearch?): PestMonsterTronActorDecision {
        input.verifyBinding(expectedEpoch, expectedActor)
        if (input.observation.players.size != 2 || input.observation.players.any { it.hasLost }) {
            throw UnsupportedPolicyInput("Monster advisor component requires the frozen two-player duel")
        }
        val question = input.decision ?: return PestMonsterTronActorDecision.NoOverride
        require(question.playerId == input.actorId)
        val cards = ActorPublicCards(input)
        val accessibleNames = (cards.ownBoard + cards.hand).map { it.name }
        val source = question.context.sourceName
        val sourceId = question.context.sourceId ?: return PestMonsterTronActorDecision.NoOverride
        val ownVisibleCard = cards.cardOrNull(sourceId)?.let { card ->
            card.name == source && (card.ownerId == input.actorId || card.controllerId == input.actorId)
        } ?: false
        val ownVisibleStackSource = input.observation.stack.any { item ->
            (item.view.entityId == sourceId || item.sourceId == sourceId) &&
                item.view.name == source && item.view.controllerId == input.actorId
        }
        if (!ownVisibleCard && !ownVisibleStackSource) return PestMonsterTronActorDecision.NoOverride

        fun simpleOneCardChoice(choice: SelectCardsDecision): Boolean =
            choice.minSelections in 0..1 && choice.maxSelections == 1 && !choice.ordered &&
                choice.options.isNotEmpty() && choice.options.distinct().size == choice.options.size &&
                choice.nonSelectableOptions.isEmpty() && !choice.onePerCardType &&
                !choice.onePerColor && !choice.onePerCardName && !choice.onePerBasicLandType &&
                !choice.onePerPower && choice.conditionalMinimums.isEmpty() &&
                choice.maxTotalManaValue == null && choice.minTotalManaValue == null &&
                choice.maxTotalPower == null

        val response: DecisionResponse? = when {
            source in setOf("Expedition Map", "Crop Rotation") && question is SelectCardsDecision &&
                simpleOneCardChoice(question) && question.cardInfo != null &&
                question.cardInfo.keys == question.options.toSet() && search?.matches(input, question) == true -> {
                val info = requireNotNull(question.cardInfo)
                PestMonsterTronPublicPolicy.tutorSelection(
                    accessibleNames, question.options,
                    question.options.associateWith { info[it]?.name },
                )?.let { CardsSelectedResponse(question.id, listOf(it)) }
            }

            source == "Crop Rotation" && question is SelectCardsDecision &&
                simpleOneCardChoice(question) && question.cardInfo == null &&
                question.options.all { id ->
                    cards.cardOrNull(id)?.let { it.zone == Zone.BATTLEFIELD &&
                        it.controllerId == input.actorId && it.isType("Land") } == true
                } -> PestMonsterTronPublicPolicy.cropRotationSacrifice(
                    question.options, question.options.associateWith { cards.card(it).name },
                )?.let { CardsSelectedResponse(question.id, listOf(it)) }

            // Legacy SearchLibraryDecision has no trusted current-question proof in this receiver.
            question is SearchLibraryDecision -> null

            source == "Bojuka Bog" && question is ChooseTargetsDecision -> {
                val requirement = question.targetRequirements.singleOrNull()
                if (requirement == null) null else {
                    val legalOpponents = question.legalTargets[requirement.index].orEmpty()
                        .filter { it == cards.opponentId }
                    PestMonsterTronPublicPolicy.bojukaBogTarget(
                        legalOpponents,
                        mapOf(cards.opponentId to cards.opponentPlayer.graveyardSize),
                    )?.let { TargetsResponse(question.id, mapOf(requirement.index to listOf(it))) }
                }
            }

            else -> null
        }
        if (response == null) return PestMonsterTronActorDecision.NoOverride
        val action = ActorChoiceSupport.submit(input, response)
        return PestMonsterTronActorDecision.Proposed(ActorChoiceSupport.proposal(input, action))
    }
}
