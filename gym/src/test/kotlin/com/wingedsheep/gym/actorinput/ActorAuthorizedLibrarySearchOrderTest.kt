package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Fixed current-question projection only; no seed allocation, answer, or game outcome. */
class ActorAuthorizedLibrarySearchOrderTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val epoch = ActorEpoch("authorized-search-receiver-v1", "fixed-question", 4)

    init {
        test("current source-bound top search preserves only offered order; ordinary look sorts") {
            val (base, actor, source, options) = fixture()
            val authorized = pause(base, actor, source, options)
            val actorInput = project(authorized, actor)
            val question = actorInput.decision as SelectCardsDecision
            question.options shouldBe options
            question.authorizedLibrarySearch shouldBe null
            actorInput.observation.decisionCards.map { it.entityId } shouldBe
                actorInput.observation.decisionCards.map { it.entityId }.sortedBy { it.value }
            actorInput.canonicalJson().contains("resolutionKey") shouldBe false
            actorInput.canonicalJson().contains("sourceOrigin") shouldBe false
            actorInput.verifyBinding(epoch, actor)
            shouldThrow<ObservationBoundaryException> {
                actorInput.verifyBinding(epoch.copy(step = epoch.step + 1), actor)
            }.failure shouldBe BoundaryFailure.STALE_INPUT

            val ordinary = project(pause(base, actor, source, options, authorized = false), actor)
            (ordinary.decision as SelectCardsDecision).options shouldBe options.sortedBy { it.value }
            ordinary.canonicalJson() shouldNotBe actorInput.canonicalJson()
        }

        test("stale, altered, and replaced proof never preserves order") {
            val (base, actor, source, options) = fixture()
            val origin = requireNotNull(base.objectRef(source))
            val variants = listOf<(AuthorizedLibrarySearchChoice) -> AuthorizedLibrarySearchChoice>(
                { it.copy(decisionId = "stale") },
                { it.copy(actorId = EntityId.of("wrong-actor")) },
                { it.copy(sourceOrigin = origin.copy(generation = origin.generation + 1)) },
                { it.copy(resolutionKey = "other-resolution") },
                { it.copy(offeredHandles = it.offeredHandles.reversed()) },
                { it.copy(offeredObjects = it.offeredObjects.reversed()) },
                { it.copy(portion = ResolvedLibrarySearchPortion.Top(1)) },
            )
            for (mutate in variants) {
                val state = pause(base, actor, source, options, mutate = mutate)
                (project(state, actor).decision as SelectCardsDecision).options shouldBe
                    options.sortedBy { it.value }
            }
            val changedLibrary = pause(base, actor, source, options).let { state ->
                state.copy(zones = state.zones + (ZoneKey(actor, Zone.LIBRARY) to
                    state.getLibrary(actor).reversed()))
            }
            (project(changedLibrary, actor).decision as SelectCardsDecision).options shouldBe
                options.sortedBy { it.value }

            // A paused card can leave and reenter under the same EntityId. The old offer does
            // not authorize the new object, even if the library slot order is restored.
            val paused = pause(base, actor, source, options)
            val from = ZoneKey(actor, Zone.LIBRARY)
            val hand = ZoneKey(actor, Zone.HAND)
            val departed = paused.moveToZone(options.first(), from, hand)
            val returned = departed.moveToZone(options.first(), hand, from).copy(
                zones = returned.zones + (from to paused.getLibrary(actor)),
            )
            returned.objectRef(options.first()) shouldNotBe paused.objectRef(options.first())
            (project(returned, actor).decision as SelectCardsDecision).options shouldBe
                options.sortedBy { it.value }
        }
    }

    private fun fixture(): Four<GameState, EntityId, EntityId, List<EntityId>> {
        val game = scenario().withPlayers().withRngSeed(0xA1706001L)
            .withCardOnBattlefield(1, "Expedition Map")
            .withCardInLibrary(1, "Forest").withCardInLibrary(1, "Island")
            .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Mountain").build()
        val actor = game.player1Id
        val source = requireNotNull(game.findPermanent("Expedition Map"))
        val library = game.state.getLibrary(actor).sortedBy { it.value }.reversed()
        val base = game.state.copy(zones = game.state.zones + (ZoneKey(actor, Zone.LIBRARY) to library))
        return Four(base, actor, source, library.take(2))
    }

    private fun pause(
        state: GameState,
        actor: EntityId,
        source: EntityId,
        options: List<EntityId>,
        authorized: Boolean = true,
        mutate: (AuthorizedLibrarySearchChoice) -> AuthorizedLibrarySearchChoice = { it },
    ): GameState {
        val origin = requireNotNull(state.objectRef(source))
        val refs = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin,
            resolutionKey = "map-resolution")
        val info = options.associateWith { id ->
            val card = state.getEntity(id)!!.require<CardComponent>()
            SearchCardInfo(card.name, card.manaCost.toString(), card.typeLine.toString(), null)
        }
        return state.suspendForDecision(
            question = { id ->
                val proof = AuthorizedLibrarySearchChoice(
                    decisionId = id, chooserId = actor, actorId = actor,
                    libraryOwner = actor, portion = ResolvedLibrarySearchPortion.Top(2),
                    sourceOrigin = origin, resolutionKey = "map-resolution",
                    offeredHandles = options,
                    offeredObjects = options.map { requireNotNull(state.objectRef(it)) },
                )
                SelectCardsDecision(id, actor, "Search two", DecisionContext(sourceId = source),
                    options, 0, 1, cardInfo = info,
                    authorizedLibrarySearch = if (authorized) mutate(proof) else null)
            },
            answer = SelectFromCollectionContinuation(
                playerId = actor, sourceId = source, sourceName = "Expedition Map",
                allCards = options, storeSelected = "found", storeRemainder = null,
                objectReferences = refs,
            ),
        ).state
    }

    private fun project(state: GameState, actor: EntityId): ActorInput =
        adapter.build(state, actor, emptyList(), epoch, 0xA1706002L)
}

private data class Four<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
