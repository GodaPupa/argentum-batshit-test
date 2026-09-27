package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.AuthorizedLibrarySearchChoice
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.ResolvedLibrarySearchPortion
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectFromCollectionContinuation
import com.wingedsheep.engine.core.suspendForDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthorizedSearchDecisionMaskingTest : FunSpec({
    val chooser = EntityId("chooser")
    val opponent = EntityId("opponent")
    val source = EntityId("source")
    val first = EntityId("first")
    val second = EntityId("second")

    test("only the current actor receives a proof bound to the routed decision ID") {
        val initial = GameState(turnOrder = listOf(chooser, opponent))
        val paused = initial.suspendForDecision(
            question = { id ->
                SelectCardsDecision(
                    id = id,
                    playerId = chooser,
                    prompt = "Search",
                    context = DecisionContext(sourceId = source),
                    options = listOf(second, first),
                    minSelections = 0,
                    maxSelections = 1,
                    authorizedLibrarySearch = AuthorizedLibrarySearchChoice(
                        decisionId = id,
                        chooserId = chooser,
                        actorId = chooser,
                        libraryOwner = chooser,
                        portion = ResolvedLibrarySearchPortion.Whole,
                        sourceOrigin = ObjectRef(source, 3),
                        resolutionKey = "source:resolution",
                        offeredHandles = listOf(second, first),
                    ),
                )
            },
            answer = SelectFromCollectionContinuation(
                playerId = chooser,
                sourceId = source,
                sourceName = "Search",
                allCards = listOf(second, first),
                storeSelected = "found",
                storeRemainder = null,
            ),
        )
        val state = paused.state
        val decision = state.pendingDecision as SelectCardsDecision
        val presenter = DecisionEnricher(CardRegistry())
        val actorView = presenter.enrich(decision, state, chooser) as SelectCardsDecision
        val wireId = "current-epoch:${decision.id}"
        val routed = actorView.withClientRoutingId(wireId) as SelectCardsDecision

        routed.id shouldBe wireId
        routed.authorizedLibrarySearch?.decisionId shouldBe wireId
        routed.authorizedLibrarySearch?.offeredHandles shouldBe listOf(second, first)
        (presenter.enrich(decision, state, opponent) as SelectCardsDecision)
            .authorizedLibrarySearch shouldBe null
        (presenter.enrich(decision.copy(id = "stale"), state, chooser) as SelectCardsDecision)
            .authorizedLibrarySearch shouldBe null
    }
})
