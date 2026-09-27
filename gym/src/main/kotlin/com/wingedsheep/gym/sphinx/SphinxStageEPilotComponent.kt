package com.wingedsheep.gym.sphinx

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A seed-free Stage-E policy component, not a game driver or an information adapter.
 *
 * The future qualified canonical actor supplies these own/public facts and current offers. This
 * type deliberately has no GameState, callback, deck/architecture identifier, hidden hand or
 * ordered library. Passing these facts by hand in a fixture does not qualify the future adapter.
 */
@Serializable
data class SphinxStageEPilotFacts(
    val actorId: String,
    val epoch: Long,
    val libraryCount: Int,
    val availableBlueMana: Int,
    val ownHandNames: List<String>,
    val actualGraveyardApproachIds: List<String> = emptyList(),
    // Derived from the pilot's own frozen list and identities it is entitled to know, not a scan
    // of the library. An unknown positive count is not permission to inspect its order.
    val knownUnseenSphinxCount: Int = 0,
    val window: SphinxStageEWindow = SphinxStageEWindow.OTHER,
) {
    init {
        require(actorId.isNotBlank())
        require(epoch >= 0 && libraryCount >= 0 && availableBlueMana >= 0)
        require(knownUnseenSphinxCount >= 0)
        require(actualGraveyardApproachIds.distinct().size == actualGraveyardApproachIds.size)
    }
}

@Serializable
enum class SphinxStageEWindow { ACTOR_MAIN, OPPONENT_END_STEP, OTHER }

@Serializable
enum class SphinxStageETargetKind { PLAYER, SPELL, PERMANENT }

@Serializable
data class SphinxStageEPublicTarget(
    val id: String,
    val controlledByActor: Boolean,
    val manaValue: Int,
    val counterable: Boolean = true,
    val publiclyVisibleLethal: Boolean = false,
    val kind: SphinxStageETargetKind = SphinxStageETargetKind.SPELL,
) {
    init { require(id.isNotBlank() && manaValue >= 0) }
}

/** Current, actor-visible cast offer. Mono-blue inputs pay generic costs with blue mana as well. */
@Serializable
data class SphinxStageECastOffer(
    val id: String,
    val epoch: Long,
    val cardName: String,
    val affordable: Boolean,
    val totalMana: Int,
    val legalTargets: List<SphinxStageEPublicTarget> = emptyList(),
) {
    init {
        require(id.isNotBlank() && epoch >= 0 && cardName.isNotBlank() && totalMana >= 0)
        require(legalTargets.map { it.id }.distinct().size == legalTargets.size)
    }
}

@Serializable
data class SphinxStageEPolicyInput(
    val facts: SphinxStageEPilotFacts,
    val offer: SphinxStageECastOffer,
)

@Serializable
data class SphinxStageEPolicyChoice(
    val actionId: String? = null,
    val targetId: String? = null,
    val reason: String,
)

object SphinxStageEPilotComponent {
    const val VERSION = "sphinx-stage-e-decision-component-v1"

    private val wire = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun encode(input: SphinxStageEPolicyInput): String = wire.encodeToString(input)
    fun decode(text: String): SphinxStageEPolicyInput = wire.decodeFromString(text)

    private fun current(input: SphinxStageEPolicyInput) {
        require(input.offer.epoch == input.facts.epoch) { "stale cast offer" }
        require(input.offer.cardName in input.facts.ownHandNames) {
            "cast offer is not represented in the actor's supplied own hand"
        }
    }

    /** No predicted cleanup discard, previous offer, or future graveyard count is consulted. */
    fun chooseCurrentCast(input: SphinxStageEPolicyInput): SphinxStageEPolicyChoice {
        current(input)
        return if (!input.offer.affordable || input.offer.totalMana > input.facts.availableBlueMana) {
            SphinxStageEPolicyChoice(reason = "current offer is not payable")
        } else {
            SphinxStageEPolicyChoice(actionId = input.offer.id, reason = "current offer is payable")
        }
    }

    /** A counter that is already affordable reserves its mana; a hypothetical future draw does not. */
    fun interactionReserve(facts: SphinxStageEPilotFacts): Int = when {
        "Counterspell" in facts.ownHandNames && facts.availableBlueMana >= 2 -> 2
        "Spell Pierce" in facts.ownHandNames && facts.availableBlueMana >= 1 -> 1
        "Dispel" in facts.ownHandNames && facts.availableBlueMana >= 1 -> 1
        else -> 0
    }

    /**
     * These are exact known draw sequences in the frozen four 60s. The Thought Scour option is
     * its self-targeting setup line and must carry that exact offered player target. Choosing
     * another player is a separate policy line requiring its own receiving qualification.
     * Unknown draw spells fail closed rather than receiving an invented draw profile.
     */
    fun chooseSetupDraw(input: SphinxStageEPolicyInput): SphinxStageEPolicyChoice {
        current(input)
        val requiredLibrary = when (input.offer.cardName) {
            "Mental Note", "Thought Scour", "Brainstorm", "Lórien Revealed" -> 3
            "Sphinx's Approach" -> 2
            "Ponder", "Preordain" -> 1
            else -> error("unqualified setup draw identity: ${input.offer.cardName}")
        }
        if (input.facts.libraryCount < requiredLibrary) {
            return SphinxStageEPolicyChoice(reason = "mandatory draw would exhaust the library")
        }
        val payable = chooseCurrentCast(input)
        if (payable.actionId == null) return payable
        if (input.facts.availableBlueMana - input.offer.totalMana < interactionReserve(input.facts)) {
            return SphinxStageEPolicyChoice(reason = "preserve available interaction")
        }
        val selfTarget = if (input.offer.cardName == "Thought Scour") {
            input.offer.legalTargets.singleOrNull {
                it.kind == SphinxStageETargetKind.PLAYER && it.id == input.facts.actorId
            }?.id ?: return SphinxStageEPolicyChoice(reason = "self-mill player target is not offered")
        } else null
        return SphinxStageEPolicyChoice(input.offer.id, selfTarget, "safe setup with interaction reserved")
    }

    /**
     * Approach is still on the stack. It and any anticipated cleanup discard cannot supply the
     * four other graveyard cards. The selected objects are an intersection of the current offered
     * choice and the actor's current graveyard facts. No library identity or ordering is inspected.
     */
    fun selectApproachPayment(
        facts: SphinxStageEPilotFacts,
        resolvingSourceId: String,
        offeredGraveyardIds: List<String>,
    ): List<String> {
        require(resolvingSourceId.isNotBlank())
        require(offeredGraveyardIds.distinct().size == offeredGraveyardIds.size)
        if (facts.knownUnseenSphinxCount == 0) return emptyList()
        val actual = facts.actualGraveyardApproachIds.toSet()
        val choices = offeredGraveyardIds.filter { it != resolvingSourceId && it in actual }
        return if (choices.size >= 4) choices.sorted().take(4) else emptyList()
    }

    /**
     * Normal deployment windows only. Emergency blocking, search selections and combat lines
     * still require full receiving pilot fixtures; this component does not declare them absent.
     */
    fun chooseDeployment(input: SphinxStageEPolicyInput): SphinxStageEPolicyChoice {
        current(input)
        val payable = chooseCurrentCast(input)
        if (payable.actionId == null) return payable
        val atWindow = when (input.offer.cardName) {
            "Sphinx's Approach" -> {
                if (input.facts.libraryCount < 2 || input.facts.actualGraveyardApproachIds.size < 4 ||
                    input.facts.knownUnseenSphinxCount == 0) {
                    return SphinxStageEPolicyChoice(reason = "no current Approach deployment")
                }
                input.facts.window == SphinxStageEWindow.OPPONENT_END_STEP
            }
            "Tolarian Terror", "Cryptic Serpent", "Goliath Sphinx" ->
                input.facts.window == SphinxStageEWindow.ACTOR_MAIN
            else -> error("unqualified deployment identity: ${input.offer.cardName}")
        }
        return if (atWindow) {
            SphinxStageEPolicyChoice(actionId = input.offer.id, reason = "legal normal deployment window")
        } else {
            SphinxStageEPolicyChoice(reason = "wait for the normal deployment window")
        }
    }

    /** Targets are selected only from the offered public stack choices. */
    fun chooseCounterspell(input: SphinxStageEPolicyInput): SphinxStageEPolicyChoice {
        current(input)
        require(input.offer.cardName == "Counterspell")
        val payable = chooseCurrentCast(input)
        if (payable.actionId == null) return payable
        val target = input.offer.legalTargets
            .filter { it.kind == SphinxStageETargetKind.SPELL && !it.controlledByActor && it.counterable }
            .sortedWith(compareByDescending<SphinxStageEPublicTarget> { it.publiclyVisibleLethal }
                .thenByDescending { it.manaValue }.thenBy { it.id })
            .firstOrNull()
            ?: return SphinxStageEPolicyChoice(reason = "no useful offered counter target")
        return SphinxStageEPolicyChoice(input.offer.id, target.id, "offered public counter target")
    }
}
