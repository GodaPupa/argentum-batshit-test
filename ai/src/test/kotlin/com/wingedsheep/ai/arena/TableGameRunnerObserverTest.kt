package com.wingedsheep.ai.arena

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan

class TableGameRunnerObserverTest : FunSpec({

    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }
    val forestDeck = Deck.of("Forest" to 60)
    val agent = ArenaAgents.resolve("v0")

    fun run(skipMulligans: Boolean, observer: ArenaTrainingObserver) = TableGameRunner.play(
        registry = registry,
        setup = TableSetup.HEADS_UP,
        agents = listOf(agent, agent),
        decks = listOf(forestDeck, forestDeck),
        seed = 1L,
        groupId = 1,
        rotation = 0,
        maxTurns = 1,
        maxActions = 200,
        trainingObserver = observer,
        skipMulligans = skipMulligans,
    )

    test("accepted transitions expose exact before and after states") {
        var transitions = 0
        var distinctStates = 0
        run(skipMulligans = true, observer = object : ArenaTrainingObserver {
            override fun transition(
                before: GameState,
                acceptedAction: GameAction,
                after: GameState,
                events: List<GameEvent>,
            ) {
                transitions++
                if (before !== after) distinctStates++
            }
        })

        transitions shouldBeGreaterThan 0
        distinctStates shouldBeGreaterThan 0
    }

    test("mulligan switch reaches observers without changing the default") {
        val openingKept = mutableListOf<Boolean>()
        val observer = object : ArenaTrainingObserver {
            override fun gameStarted(state: GameState, seats: List<EntityId>) {
                openingKept += state.getEntity(seats.first())!!
                    .get<MulliganStateComponent>()!!.hasKept
            }
        }

        run(skipMulligans = true, observer = observer)
        run(skipMulligans = false, observer = observer)

        openingKept[0].shouldBeTrue()
        openingKept[1].shouldBeFalse()
    }
})
