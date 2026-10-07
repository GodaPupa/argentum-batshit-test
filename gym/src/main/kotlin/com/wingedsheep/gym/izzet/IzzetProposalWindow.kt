package com.wingedsheep.gym.izzet

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Prospective source bindings; these values must be independently authenticated by the runner. */
internal data class IzzetSourcePins(val sourceCommit: String, val deckSha256: String,
    val policySha256: String, val runtimeSha256: String) {
    init {
        require(sourceCommit.matches(Regex("[0-9a-f]{40}")))
        require(listOf(deckSha256, policySha256, runtimeSha256).all { it.matches(Regex("[0-9a-f]{64}")) })
    }
}
internal data class IzzetDecisionEpoch(val attemptId: String, val sequence: Long,
    val actor: String, val priorityActor: String?, val decisionId: String?) {
    init {
        require(attemptId.isNotBlank() && sequence >= 0 && actor.isNotBlank())
        require(priorityActor == null || priorityActor.isNotBlank())
        require(decisionId == null || decisionId.isNotBlank())
        require(decisionId != null || priorityActor == actor)
    }
}

/**
 * A trusted provider supplies fully materialized, currently legal canonical action payloads.
 * This gate never parameterizes engine templates or guesses targets, payments or typed answers.
 */
internal data class IzzetMaterializedOffer(val id: Int, val actionKind: String,
    val decisionKind: String?, val decisionId: String?, val canonicalAction: String)
internal data class IzzetNumberedProposal(val windowSha256: String, val offerId: Int)
internal data class IzzetPilotMenu(val windowSha256: String, val maskedObservation: String,
    val offers: List<IzzetMaterializedOffer>)

/**
 * Dormant trusted-side freshness/identity gate. No engine, RNG, initialization, allocation, pilot
 * strategy or opponent package. The masked projection and legal materialization provider must be
 * independently reviewed; accepting a String is NOT proof that its contents are masked or legal.
 * Inventory below is this gate's finite wire vocabulary, NOT a claim of whole-pilot coverage.
 * Each window is one-use, even when resolution rejects; callers may not retry under a new alias.
 */
internal class IzzetProposalWindow private constructor(
    private val pins: IzzetSourcePins,
    private val epoch: IzzetDecisionEpoch,
    private val observation: String,
    private val captured: List<IzzetMaterializedOffer>,
) {
    private val hash = digest(pins, epoch, observation, captured)
    private var consumed = false
    fun pilotMenu() = IzzetPilotMenu(hash, observation, captured.map { it.copy() })

    /** Current arguments must be recomputed by the trusted owner, never echoed by the pilot. */
    @Synchronized
    fun resolve(proposal: IzzetNumberedProposal, currentPins: IzzetSourcePins,
                currentEpoch: IzzetDecisionEpoch, currentMaskedObservation: String,
                currentOffers: List<IzzetMaterializedOffer>): String {
        check(!consumed) { "Proposal window consumed" }
        consumed = true
        val detached = validate(currentEpoch, currentMaskedObservation, currentOffers)
        require(currentPins == pins && currentEpoch == epoch) { "Source or actor epoch drift" }
        require(digest(currentPins, currentEpoch, currentMaskedObservation, detached) == hash &&
            proposal.windowSha256 == hash) { "Stale observation or action registry" }
        return captured.singleOrNull { it.id == proposal.offerId }?.canonicalAction
            ?: error("Unknown current action identity; no fallback")
    }

    companion object {
        private val actionInventory = mapOf(1 to "PassPriority", 2 to "PlayLand", 3 to "CastSpell",
            4 to "ActivateAbility", 5 to "KeepHand", 6 to "TakeMulligan", 7 to "BottomCards",
            8 to "SubmitDecision", 9 to "DeclareAttackers", 10 to "DeclareBlockers", 11 to "OrderBlockers")
        private val decisionInventory = mapOf(1 to "SelectCards", 2 to "YesNo", 3 to "ChooseTargets",
            4 to "ChooseNumber", 5 to "ChooseOption", 6 to "ChooseManaColor", 7 to "Reorder",
            8 to "SearchLibrary", 9 to "ChooseModes", 10 to "AssignDamage")

        fun open(pins: IzzetSourcePins, epoch: IzzetDecisionEpoch, maskedObservation: String,
                 offers: List<IzzetMaterializedOffer>): IzzetProposalWindow =
            IzzetProposalWindow(pins, epoch, maskedObservation, validate(epoch, maskedObservation, offers))

        private fun validate(epoch: IzzetDecisionEpoch, observation: String,
                             offers: List<IzzetMaterializedOffer>): List<IzzetMaterializedOffer> {
            require(observation.isNotBlank() && observation.length <= 4 * 1024 * 1024)
            require(offers.isNotEmpty() && offers.size <= 100_000)
            val detached = offers.map { it.copy() }
            require(detached.map { it.id }.distinct().size == detached.size)
            detached.forEach {
                require(it.id >= 0 && it.actionKind in actionInventory.values) { "Unsupported action schema" }
                require(it.canonicalAction.isNotBlank() && it.canonicalAction.length <= 1024 * 1024)
                if (epoch.decisionId != null) {
                    require(it.actionKind == "SubmitDecision" && it.decisionId == epoch.decisionId &&
                        it.decisionKind in decisionInventory.values) { "Unsupported or wrong typed decision" }
                } else {
                    require(it.actionKind != "SubmitDecision" && it.decisionId == null && it.decisionKind == null)
                }
            }
            return detached
        }
        private fun digest(p: IzzetSourcePins, e: IzzetDecisionEpoch, observation: String,
                           offers: List<IzzetMaterializedOffer>): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                // Length-prefixed UTF-8 and explicit null flags prevent separator collisions.
                fun field(value: String?) {
                    out.writeBoolean(value != null)
                    if (value != null) { val b = value.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) }
                }
                field("IZZET_PROPOSAL_WINDOW_V1")
                listOf(p.sourceCommit, p.deckSha256, p.policySha256, p.runtimeSha256,
                    e.attemptId, e.actor, e.priorityActor, e.decisionId, observation).forEach(::field)
                out.writeLong(e.sequence); out.writeInt(offers.size)
                offers.forEach { out.writeInt(it.id); field(it.actionKind); field(it.decisionKind)
                    field(it.decisionId); field(it.canonicalAction) }
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
