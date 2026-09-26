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
import com.wingedsheep.sdk.model.EntityId

sealed interface PestMonsterTronActorDecision {
    data class Proposed(val proposal: ActorProposal) : PestMonsterTronActorDecision

    /** The original advisor returned null here. A complete separately qualified pilot is required. */
    data object NoOverride : PestMonsterTronActorDecision
}

/**
 * Mechanical receiving adapter for the existing Monster Tron advisor's pending decisions.
 *
 * The only policy argument is the detached canonical ActorInput. The exact frozen two-player
 * Pest/Monster scope has no teammate or name-changing effects; extending that scope is not qualified
 * here. The shared calculation preserves the supplied question order and null/defer outcomes.
 * Canonical option sorting can change fallback or tied choices relative to the legacy raw question;
 * those information-interface differences still require prospective protocol disposition.
 * NoOverride does not invent a pass or default response. Action ranking, the base profile, mulligans,
 * all other choices, initialized-deck binding and whole-runtime admission remain separate.
 */
class PestMonsterTronActorDecisions(
    private val expectedEpoch: ActorEpoch,
    private val expectedActor: EntityId,
) {
    fun respond(input: ActorInput): PestMonsterTronActorDecision {
        input.verifyBinding(expectedEpoch, expectedActor)
        if (input.observation.players.size != 2 || input.observation.players.any { it.hasLost }) {
            throw UnsupportedPolicyInput("Monster advisor component requires the frozen two-player duel")
        }
        val question = input.decision ?: return PestMonsterTronActorDecision.NoOverride
        require(question.playerId == input.actorId)
        val cards = ActorPublicCards(input)
        val accessibleNames = (cards.ownBoard + cards.hand).map { it.name }
        val source = question.context.sourceName

        val response: DecisionResponse? = when {
            source in setOf("Expedition Map", "Crop Rotation", "Ancient Stirrings") -> when (question) {
                is SearchLibraryDecision -> PestMonsterTronPublicPolicy.tutorSelection(
                    accessibleNames,
                    question.options,
                    question.options.associateWith { question.cards[it]?.name },
                )?.let { CardsSelectedResponse(question.id, listOf(it)) }

                is SelectCardsDecision -> {
                    val info = question.cardInfo
                    val selected = if (info == null && source == "Crop Rotation") {
                        PestMonsterTronPublicPolicy.cropRotationSacrifice(
                            question.options,
                            question.options.associateWith { cards.card(it).name },
                        )
                    } else if (info != null) {
                        PestMonsterTronPublicPolicy.tutorSelection(
                            accessibleNames,
                            question.options,
                            question.options.associateWith { info[it]?.name },
                        )
                    } else null
                    selected?.let { CardsSelectedResponse(question.id, listOf(it)) }
                }

                else -> null
            }

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
