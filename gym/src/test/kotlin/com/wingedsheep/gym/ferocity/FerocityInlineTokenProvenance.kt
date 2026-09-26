package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.replacement.ActiveReplacements
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.identity.TextReplacementComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.giftEffect
import com.wingedsheep.sdk.dsl.giftKeyword
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CreateAdditionalToken
import com.wingedsheep.sdk.scripting.GiftKind
import com.wingedsheep.sdk.scripting.ModifyTokenCount
import com.wingedsheep.sdk.scripting.MultiplyTokenCreation
import com.wingedsheep.sdk.scripting.ReplaceTokenCreationWithAttachedCopy
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

internal const val FEROCITY_INLINE_TOKEN_DEPENDENCY = "ferocity/inline-tokens/gift-fish/v1"
internal const val FEROCITY_GIFT_FISH_RECIPE = "gift-fish/v1"
internal val FEROCITY_INLINE_TOKEN_SOURCE_KEYS = setOf(
    "ferocity/inline-source/GiftDsl.kt",
    "ferocity/inline-source/CreateTokenExecutor.kt",
    "ferocity/inline-source/TokenCreationReplacementHelper.kt",
    "ferocity/inline-source/StackResolver.kt",
)

/** Trusted admission artifact, never an actor input or a substitute CardDefinition. */
@Serializable
internal data class FerocityInlineTokenAdmission(
    val schemaVersion: Int = 1,
    val recipeId: String,
    val sourceDefinitionLookup: String,
    val sourceDefinitionSha256: String,
    val createTokenDescriptor: FerocityPayload,
    val runtimeDependencySha256: Map<String, String>,
)

internal fun ferocityInlineTokenAdmissionSha256(admission: FerocityInlineTokenAdmission): String =
    FerocityJournalCodec.sha(FerocityJournalCodec.canonical(FerocityInlineTokenAdmission.serializer(), admission))

internal fun verifyFerocityInlineTokenAdmission(
    admission: FerocityInlineTokenAdmission,
    pins: FerocitySourcePins,
    registry: CardRegistry,
) {
    require(admission.schemaVersion == 1 && admission.recipeId == FEROCITY_GIFT_FISH_RECIPE) {
        "Unsupported inline-token recipe/schema"
    }
    require(pins.dependencySha256[FEROCITY_INLINE_TOKEN_DEPENDENCY] == ferocityInlineTokenAdmissionSha256(admission)) {
        "Inline-token admission is not the exact pinned artifact"
    }
    require(admission.runtimeDependencySha256.keys == FEROCITY_INLINE_TOKEN_SOURCE_KEYS) {
        "Inline-token recipe requires its complete reviewed source dependency set"
    }
    admission.runtimeDependencySha256.forEach { (key, digest) ->
        requireSha256(digest)
        require(pins.dependencySha256[key] == digest) { "Inline-token runtime source mismatch: $key" }
    }
    requireSha256(admission.sourceDefinitionSha256)
    require(pins.cardDefinitionSha256[admission.sourceDefinitionLookup] == admission.sourceDefinitionSha256)
    val definition = registry.requireCard(admission.sourceDefinitionLookup)
    require(definition.name == "Sazacap's Brew" && definition.giftKeyword()?.kind == GiftKind.TAPPED_FISH &&
        definition.typeLine.isInstant && definition.cardFaces.isEmpty() && definition.backFace == null) {
        "Only the admitted single-face Brew gift source is supported"
    }
    require(FerocityJournalCodec.card(definition).canonicalSha256 == admission.sourceDefinitionSha256)
    // StackResolver currently resolves the source's natural-name registry binding. Verify that
    // binding too: an alternate printing must not silently select a different definition.
    require(FerocityJournalCodec.card(registry.requireCard(definition.name)).canonicalSha256 == admission.sourceDefinitionSha256)
    val actual = FerocityJournalCodec.restore(Effect.serializer(), admission.createTokenDescriptor)
    require(actual is CreateTokenEffect && actual == giftEffect(GiftKind.TAPPED_FISH)) {
        "Inline token descriptor differs from the bounded, fixed GiftDsl recipe"
    }
    require(admission.createTokenDescriptor == FerocityJournalCodec.payload(Effect.serializer(), giftEffect(GiftKind.TAPPED_FISH)))
}

/** Exact causal proof. This trusted history is not included in any actor observation. */
@Serializable
internal data class FerocityInlineTokenProof(
    val recipeId: String,
    val createdObject: ObjectRef,
    val sourceObject: ObjectRef,
    val sourceDefinitionSha256: String,
    val descriptorSha256: String,
    val admissionSha256: String,
    val casterId: EntityId,
    val recipientId: EntityId,
    val initialBaseCard: CardComponent,
)

/**
 * One attempt's append-only causal history. The caller records a real RESULT durably before
 * calling acceptApplied. Replays rebuild the same history from completed, verified transitions.
 */
internal class FerocityInlineTokenTracker(
    admission: FerocityInlineTokenAdmission?,
    private val pins: FerocitySourcePins,
    private val registry: CardRegistry,
) {
    private val admission = admission?.let {
        val detached = FerocityJournalCodec.restore(FerocityInlineTokenAdmission.serializer(),
            FerocityJournalCodec.payload(FerocityInlineTokenAdmission.serializer(), it))
        verifyFerocityInlineTokenAdmission(detached, pins, registry)
        detached
    }
    private val proofs = linkedMapOf<ObjectRef, FerocityInlineTokenProof>()
    val verifiedProofs: List<FerocityInlineTokenProof> get() = proofs.values.toList()
    val proofDigest: String get() = FerocityJournalCodec.sha(FerocityJournalCodec.canonical(
        ListSerializer(FerocityInlineTokenProof.serializer()), verifiedProofs))

    fun requireState(state: GameState) {
        // A copy effect must not bypass this boundary by replacing token:Fish with the name
        // of a normally pinned card while retaining the same token incarnation.
        proofs.values.forEach { proof ->
            if (state.isCurrentObject(proof.createdObject)) {
                val entity = requireNotNull(state.getEntity(proof.createdObject.entityId))
                require(entity.get<CardComponent>() == proof.initialBaseCard && entity.has<TokenComponent>() &&
                    !entity.has<CopyOfComponent>()) { "Known inline token changed its base identity or token marker" }
            }
        }
        state.entities.forEach { (id, entity) ->
            val card = entity.get<CardComponent>() ?: return@forEach
            if (!card.cardDefinitionId.startsWith("token:")) return@forEach
            require(admission != null) { "Inline tokens are not admitted" }
            val objectRef = requireNotNull(state.objectRef(id)) { "Inline token has no current ObjectRef" }
            val proof = requireNotNull(proofs[objectRef]) { "Inline token lacks its complete causal creation prefix: $id" }
            require(state.getBattlefield().contains(id) && entity.has<TokenComponent>()) {
                "Departed or unmarked inline token cannot inherit a battlefield proof"
            }
            require(card == proof.initialBaseCard) { "Inline token base identity changed without an admitted copy/definition route" }
            require(state.projectedState.getController(id) in state.turnOrder) { "Inline token has no valid public controller" }
        }
    }

    fun acceptApplied(before: GameState, action: GameAction, after: GameState, events: List<GameEvent>) {
        requireState(before)
        val zoneChanges = events.filterIsInstance<ZoneChangeEvent>()
        val created = zoneChanges.filter { event ->
            event.fromZone == null && event.toZone == Zone.BATTLEFIELD &&
                (after.getEntity(event.entityId)?.get<CardComponent>()?.cardDefinitionId?.startsWith("token:") == true ||
                    zoneChanges.any { it.entityId == event.entityId && it.lastKnown?.cardDefinitionId?.startsWith("token:") == true } ||
                    registry.getCard(event.entityName) == null)
        }
        val newlyPresent = after.entities.filter { (id, entity) ->
            entity.get<CardComponent>()?.cardDefinitionId?.startsWith("token:") == true &&
                before.objectRef(id) != after.objectRef(id)
        }.keys
        val gifts = events.filterIsInstance<GiftGivenEvent>()
        if (created.isEmpty() && newlyPresent.isEmpty() && gifts.isEmpty()) {
            requireState(after)
            return
        }
        val recipe = requireNotNull(admission) { "A transition created an unadmitted inline token or gift" }
        require(action is PassPriority && before.pendingDecision == null && action.playerId == before.priorityPlayerId) {
            "This recipe only admits uninterrupted resolution of the public top Brew spell"
        }
        require(created.size == 1 && newlyPresent == setOf(created.single().entityId) && gifts.size == 1) {
            "Gift token count or causal event count is outside the admitted recipe"
        }
        require(ActiveReplacements.all(before).none { active ->
            active.effect is MultiplyTokenCreation || active.effect is ModifyTokenCount ||
                active.effect is CreateAdditionalToken || active.effect is ReplaceTokenCreationWithAttachedCopy
        }) { "Token replacement effects require separate qualification" }

        val sourceId = requireNotNull(before.stack.lastOrNull()) { "Gift creation has no prior top stack source" }
        val sourceEntity = requireNotNull(before.getEntity(sourceId))
        val sourceCard = requireNotNull(sourceEntity.get<CardComponent>())
        val spell = requireNotNull(sourceEntity.get<SpellOnStackComponent>()) { "Gift source is not a spell" }
        val sourceObject = requireNotNull(before.objectRef(sourceId))
        val sourceDefinition = registry.requireCard(sourceCard.cardDefinitionId)
        require(FerocityJournalCodec.card(sourceDefinition).canonicalSha256 == recipe.sourceDefinitionSha256 &&
            sourceCard.name == "Sazacap's Brew" && sourceDefinition.name == sourceCard.name)
        require(!sourceEntity.has<CopyOfComponent>() && !sourceEntity.has<TextReplacementComponent>() &&
            !spell.castFaceDown && spell.faceIndex == null &&
            spell.splicedCardNames.isEmpty()) { "Copied, hidden or spliced gift source is not admitted" }
        val recipient = requireNotNull(spell.giftRecipient) { "No gift was promised by the prior stack object" }
        require(spell.casterId in before.turnOrder && recipient in before.turnOrder && recipient != spell.casterId)
        val gift = gifts.single()
        require(gift.sourceId == sourceId && gift.sourceName == sourceCard.name && gift.controllerId == spell.casterId) {
            "Gift marker does not identify the exact prior resolving source/controller"
        }
        val sourceExit = zoneChanges.singleOrNull { it.entityId == sourceId && it.fromZone == Zone.STACK }
        require(sourceExit?.oldObject == sourceObject) {
            "Completed gift does not preserve the prior resolving spell's exact object identity"
        }
        require(!after.isCurrentObject(sourceObject) || sourceId !in after.stack) { "Gift source has not completed its resolution" }

        val entry = created.single()
        val createdObject = requireNotNull(entry.newObject) { "Created token event lacks exact new ObjectRef" }
        require(entry.oldObject == null && entry.lastKnown == null && entry.copyOfOriginalName == null &&
            entry.requestedDestination == Zone.BATTLEFIELD && entry.transitionCause == ZoneTransitionCause.PRIMARY &&
            entry.entityName == "Fish Token" && entry.ownerId == recipient)
        require(createdObject.entityId == entry.entityId && after.objectRef(entry.entityId) == createdObject &&
            before.getEntity(entry.entityId) == null && proofs.keys.none { it.entityId == entry.entityId }) {
            "Token creation reused or misstated an object identity"
        }
        require(events.indexOf(entry) < events.indexOf(gift)) { "Gift marker preceded its token creation" }
        val entity = requireNotNull(after.getEntity(entry.entityId)) { "Immediate token disappearance is not admitted by this recipe" }
        val expected = expectedFishCard(recipient, recipe)
        require(entity.get<CardComponent>() == expected && entity.has<TokenComponent>() && entity.has<TappedComponent>() &&
            entity.has<SummoningSicknessComponent>() && entity.has<EnteredThisTurnComponent>() &&
            entity.get<ControllerComponent>()?.playerId == recipient &&
            after.projectedState.getController(entry.entityId) == recipient && entry.entityId in after.getBattlefield()) {
            "Created token does not match the admitted Fish base identity, controller and entry markers"
        }
        require(!entity.has<CopyOfComponent>() && entity.get<CountersComponent>()?.counters.orEmpty().isEmpty() &&
            after.grantedActivatedAbilities.none { it.entityId == entry.entityId } &&
            after.grantedTriggeredAbilities.none { it.entityId == entry.entityId } &&
            after.grantedStaticAbilities.none { it.entityId == entry.entityId }) {
            "Creation-time token counters, copy or added abilities require separate qualification"
        }
        val proof = FerocityInlineTokenProof(recipe.recipeId, createdObject, sourceObject, recipe.sourceDefinitionSha256,
            recipe.createTokenDescriptor.canonicalSha256, ferocityInlineTokenAdmissionSha256(recipe),
            spell.casterId, recipient, expected)
        proofs[createdObject] = proof
        try { requireState(after) } catch (error: Exception) {
            // Validation is atomic for this trusted tracker; the durable engine RESULT remains.
            proofs.remove(createdObject)
            throw error
        }
    }
}

private fun expectedFishCard(recipient: EntityId, admission: FerocityInlineTokenAdmission): CardComponent {
    val effect = FerocityJournalCodec.restore(Effect.serializer(), admission.createTokenDescriptor) as CreateTokenEffect
    return CardComponent("token:Fish", "Fish Token", ManaCost.ZERO, TypeLine.parse("Creature - Fish"),
        baseStats = CreatureStats(1, 1), baseKeywords = emptySet(), colors = setOf(Color.BLUE),
        ownerId = recipient, imageUri = effect.imageUri)
}
