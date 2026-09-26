package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gym.actorinput.ActorBasicBluePaymentStatus
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorLegalAction
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.gym.contract.StackItemKind
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import java.security.MessageDigest

/** The actor may know its own submitted list. No library entity IDs or order are provided. */
class SphinxStageEOwnDeck private constructor(val sha256: String, internal val cards: Map<String, Int>) {
    companion object {
        private val frozen = setOf(
            "274521097cc731ac5f5aa9b7645a20b13d4546fd73b17dbcd14af0b0a25c4486",
            "f4cf2c64bb07847bd013e9984480f5b0fa76ac3a40dc5a47894a3609e9cb3b2e",
            "548998c77f5f688d793d36cdb9f21ae8b3b63963856875c4b84d82c271196a83",
            "6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62",
        )

        fun fromFrozenCsv(bytes: ByteArray): SphinxStageEOwnDeck {
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            require(hash in frozen) { "Not one of the four frozen Stage-E inputs" }
            val rows = bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }
                .map { it.substringBeforeLast(',') to it.substringAfterLast(',').toInt() }.toList()
            require(rows.map { it.first }.distinct().size == rows.size)
            require(rows.all { it.second > 0 } && rows.sumOf { it.second } == 60)
            return SphinxStageEOwnDeck(hash, rows.toMap())
        }
    }
}

/** A caller selects an already-existing component function; this adapter does not rank actions. */
enum class SphinxStageEComponentCall { CURRENT_CAST, SETUP_DRAW, DEPLOYMENT, COUNTERSPELL }

sealed interface SphinxStageEAdapterResult {
    data class Proposed(val proposal: ActorProposal, val reason: String) : SphinxStageEAdapterResult
    data class Declined(val inputBindingHash: String, val reason: String) : SphinxStageEAdapterResult
    data class Unqualified(val inputBindingHash: String, val requirement: String) : SphinxStageEAdapterResult
}

/**
 * Source candidate for the receiving seam. Only sealed canonical actor data enters a policy call.
 * A declined component call is NOT a PassPriority proposal, and an uncovered surface is NOT a
 * deck loss. Whole-game scheduling, London decisions, land play, search/reorder/May/payment choices,
 * combat and replay/admission remain required. The original 56 policy fixtures are unchanged.
 */
object SphinxStageEActorAdapter {
    private class Unqualified(message: String) : IllegalArgumentException(message)

    fun decideCurrentCast(
        input: ActorInput,
        expectedEpoch: ActorEpoch,
        expectedActor: EntityId,
        ownDeck: SphinxStageEOwnDeck,
        offerIndex: Int,
        componentCall: SphinxStageEComponentCall,
    ): SphinxStageEAdapterResult {
        input.verifyBinding(expectedEpoch, expectedActor)
        return try {
            if (input.observation.players.size != 2 || input.observation.players.any { it.hasLost }) {
                unsupported("Stage-E receiving requires two active duel seats")
            }
            if (input.decision != null) unsupported("Pending ${input.decision::class.simpleName} needs whole-choice receiving qualification")
            val legal = input.legalActions.getOrNull(offerIndex)
                ?: throw IllegalArgumentException("Current offer index is absent")
            val cast = legal.action as? CastSpell ?: unsupported("This component seam only represents a current CastSpell")
            require(cast.playerId == input.actorId)
            val ownHand = ownZone(input, Zone.HAND)
            val card = ownHand.singleOrNull { it.entityId == cast.cardId }
                ?: unsupported("The cast is not a card in the actor's currently visible hand")
            if (card.name !in ownDeck.cards) unsupported("An acquired card outside the frozen own list needs qualification")
            val payment = legal.basicBluePayment ?: unsupported("Current canonical payment projection is absent")
            if (payment.status == ActorBasicBluePaymentStatus.UNSUPPORTED) unsupported(payment.reason)
            val available = payment.availableBlueMana ?: unsupported("Current blue mana is absent from the payment projection")
            val total = payment.totalMana ?: unsupported("Current total mana is absent from the payment projection")
            if (payment.status == ActorBasicBluePaymentStatus.PLANNED &&
                payment.blueRemainingAfterPayment != available - total) {
                unsupported("The actual current payment differs from the frozen basic-blue reserve convention")
            }
            val facts = facts(input, ownDeck, ownHand, available)
            val targets = publicTargets(input, legal)
            val offerId = "${input.bindingHash}/cast/$offerIndex"
            val offered = SphinxStageECastOffer(offerId, input.epoch.step, card.name,
                legal.affordable && payment.status == ActorBasicBluePaymentStatus.PLANNED, total, targets)
            val policyInput = SphinxStageEPolicyInput(facts, offered)
            val choice = when (componentCall) {
                SphinxStageEComponentCall.CURRENT_CAST -> SphinxStageEPilotComponent.chooseCurrentCast(policyInput)
                SphinxStageEComponentCall.SETUP_DRAW -> SphinxStageEPilotComponent.chooseSetupDraw(policyInput)
                SphinxStageEComponentCall.DEPLOYMENT -> SphinxStageEPilotComponent.chooseDeployment(policyInput)
                SphinxStageEComponentCall.COUNTERSPELL -> SphinxStageEPilotComponent.chooseCounterspell(policyInput)
            }
            if (choice.actionId == null) return SphinxStageEAdapterResult.Declined(input.bindingHash, choice.reason)
            require(choice.actionId == offerId) { "Component returned a different current offer" }
            val selected = choice.targetId?.let { id ->
                targets.singleOrNull { it.id == id } ?: throw IllegalArgumentException("Component target was not offered")
            }
            val targetRequired = legal.requiresTargets || legal.targetRequirements.orEmpty().any { it.minTargets > 0 }
            if (selected == null && targetRequired) unsupported("The existing component did not select this mandatory target")
            val chosen = selected?.let { target ->
                val id = EntityId(target.id)
                when (target.kind) {
                    SphinxStageETargetKind.PLAYER -> ChosenTarget.Player(id)
                    SphinxStageETargetKind.SPELL -> ChosenTarget.Spell(id)
                    SphinxStageETargetKind.PERMANENT -> ChosenTarget.Permanent(id)
                }
            }
            val action = cast.copy(targets = listOfNotNull(chosen))
            SphinxStageEAdapterResult.Proposed(
                ActorProposal(input.bindingHash, action, input.policyRngState), choice.reason)
        } catch (failure: Unqualified) {
            SphinxStageEAdapterResult.Unqualified(input.bindingHash, requireNotNull(failure.message))
        }
    }

    private fun facts(
        input: ActorInput,
        ownDeck: SphinxStageEOwnDeck,
        hand: List<EntityFeatures>,
        availableBlue: Int,
    ): SphinxStageEPilotFacts {
        val player = input.observation.players.single { it.id == input.actorId }
        val library = input.observation.zones.single { it.ownerId == input.actorId && it.zoneType == Zone.LIBRARY }
        require(library.size == player.librarySize)
        // Deliberately read only size; library.cards and decisionCards are not inspected.
        val graveyard = ownZone(input, Zone.GRAVEYARD)
        val initialSphinxCount = ownDeck.cards["Goliath Sphinx"] ?: 0
        val sphinxOutside = if (initialSphinxCount == 0) 0 else {
            val ownZones = listOf(Zone.HAND, Zone.GRAVEYARD, Zone.EXILE, Zone.COMMAND, Zone.SIDEBOARD)
                .flatMap { ownZone(input, it) }
            val ownBoard = input.observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }
                .flatMap { it.cards }.filter { it.ownerId == input.actorId }
            val outside = (ownZones + ownBoard).distinctBy { it.entityId }
            if (outside.any { it.cardDefinitionId == null || it.faceDown }) {
                unsupported("An unidentified owned object prevents current Sphinx accounting")
            }
            val knownIds = outside.filter { it.name == "Goliath Sphinx" }.map { it.entityId }.toMutableSet()
            input.observation.stack.filter { it.view.kind == StackItemKind.SPELL }.forEach { item ->
                val spell = item.spell ?: unsupported("Stack spell ownership is not projected")
                if (spell.ownerId == input.actorId) {
                    if (spell.faceDown) unsupported("An owned face-down spell needs prior-knowledge qualification")
                    if (item.view.name == "Goliath Sphinx") knownIds.add(item.view.entityId)
                }
            }
            knownIds.size
        }
        if (sphinxOutside > initialSphinxCount) unsupported("Owned Sphinx identities exceed the frozen list")
        val window = when {
            input.observation.activePlayerId == input.actorId && input.observation.step.isMainPhase -> SphinxStageEWindow.ACTOR_MAIN
            input.observation.activePlayerId != input.actorId && input.observation.step == Step.END -> SphinxStageEWindow.OPPONENT_END_STEP
            else -> SphinxStageEWindow.OTHER
        }
        return SphinxStageEPilotFacts(input.actorId.value, input.epoch.step, library.size, availableBlue,
            hand.map { it.name }, graveyard.filter { it.name == "Sphinx's Approach" }.map { it.entityId.value },
            initialSphinxCount - sphinxOutside, window)
    }

    private fun ownZone(input: ActorInput, zone: Zone): List<EntityFeatures> {
        require(zone != Zone.LIBRARY)
        val view = input.observation.zones.single { it.ownerId == input.actorId && it.zoneType == zone }
        if (view.size != view.cards.size) unsupported("The actor cannot identify its complete $zone zone")
        require(view.cards.map { it.entityId }.distinct().size == view.cards.size)
        return view.cards
    }

    private fun publicTargets(input: ActorInput, legal: ActorLegalAction): List<SphinxStageEPublicTarget> {
        val requirements = legal.targetRequirements.orEmpty()
        if (requirements.size > 1 || requirements.any {
                it.index != 0 || it.minTargets != 1 || it.maxTargets != 1 || it.mustDifferFromEarlier ||
                    it.xConstrainsManaValue || it.xConstrainsManaValueExactly || it.xConstrainsPower || it.xConstrainsCount
            } || legal.xConstrainsTargetManaValue || legal.xConstrainsTargetManaValueExactly ||
            legal.xConstrainsTargetPower || legal.xConstrainsTargetCount) {
            unsupported("The offered target domain needs a multi-target or constrained-target adapter")
        }
        if (legal.requiresTargets && (legal.targetCount != 1 || legal.minTargets != 1)) {
            unsupported("The offered target cardinality is outside this component seam")
        }
        val ids = requirements.singleOrNull()?.validTargets ?: legal.validTargets.orEmpty()
        if (requirements.isNotEmpty() && legal.validTargets != null && legal.validTargets.toSet() != ids.toSet()) {
            unsupported("Current target views disagree; no offered target may be silently removed")
        }
        require(ids.distinct().size == ids.size)
        val board = input.observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }
            .flatMap { it.cards }.associateBy { it.entityId }
        return ids.map { id ->
            when {
                input.observation.players.any { it.id == id } -> SphinxStageEPublicTarget(id.value,
                    id == input.actorId, 0, counterable = false, kind = SphinxStageETargetKind.PLAYER)
                input.observation.stack.any { it.view.entityId == id } -> {
                    val item = input.observation.stack.single { it.view.entityId == id }
                    if (item.view.kind != StackItemKind.SPELL) unsupported("An offered stack ability needs its own target policy")
                    if (item.view.controllerId == null) unsupported("An offered spell lacks its public controller")
                    val spell = item.spell ?: unsupported("The offered stack spell lacks current public characteristics")
                    val counterable = spell.counterable ?: unsupported(requireNotNull(spell.counterabilityUnavailableReason))
                    if (item.view.name !in boundedStackSpellNames) unsupported("Public threat classification for ${item.view.name} is unqualified")
                    // None of these bounded spell identities directly deals lethal damage to
                    // the actor or makes the actor draw. Thought Scour mills its target but its
                    // controller draws: an empty target library alone is not a loss event.
                    // Future opponent spells need their own public immediate-lethal projection.
                    SphinxStageEPublicTarget(id.value, item.view.controllerId == input.actorId, spell.manaValue,
                        counterable, false, SphinxStageETargetKind.SPELL)
                }
                id in board -> {
                    val card = board.getValue(id)
                    SphinxStageEPublicTarget(id.value, card.controllerId == input.actorId, card.manaValue,
                        counterable = false, kind = SphinxStageETargetKind.PERMANENT)
                }
                else -> unsupported("An offered target is absent from the public player/stack/battlefield views")
            }
        }
    }

    // Public spell identities in the four frozen inputs. This is a projection boundary, not a
    // declaration that future Stage-E opponent packages may omit their own threat qualification.
    private val boundedStackSpellNames = setOf("Sphinx's Approach", "Goliath Sphinx", "Tolarian Terror",
        "Cryptic Serpent", "Lórien Revealed", "Ponder", "Brainstorm", "Preordain", "Mental Note",
        "Thought Scour", "Counterspell", "Spell Pierce", "Snap", "Deem Inferior", "Artful Dodge",
        "Sleep of the Dead", "Dispel")

    private fun unsupported(requirement: String): Nothing = throw Unqualified(requirement)
}
