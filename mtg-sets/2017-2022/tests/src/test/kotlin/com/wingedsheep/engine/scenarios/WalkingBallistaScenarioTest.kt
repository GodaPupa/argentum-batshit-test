package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ManaSpentEvent
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Exact-card qualification for Walking Ballista (AER #181). */
class WalkingBallistaScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private fun plusOne(game: TestGame, id: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        for (announcedX in 0..3) for (startingCounters in listOf(0, 1, 3, 6)) {
            test("R4 matrix X=$announcedX initial Animar=$startingCounters uses actual payment counters and damage") {
                val payment = (2 * announcedX - startingCounters).coerceAtLeast(0)
                val game = scenario().withPlayers("Manual", "Opponent")
                    .withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardInHand(1, "Walking Ballista").withCardInHand(1, "Forest")
                    .withLandsOnBattlefield(1, "Forest", payment.coerceAtLeast(1))
                    .withLifeTotal(2, 20).withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val animar = game.findPermanent("Animar, Soul of Elements")!!
                game.state = game.state.updateEntity(animar) {
                    it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, startingCounters))
                }
                val ballista = game.state.getHand(game.player1Id).single {
                    game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Walking Ballista"
                }
                val handLand = game.state.getHand(game.player1Id).single { it != ballista }
                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val offered = enumerator.enumerate(game.state, game.player1Id)
                    .single { (it.action as? CastSpell)?.cardId == ballista }
                check(offered.maxAffordableX!! >= announcedX)
                val cast = game.execute(CastSpell(game.player1Id, ballista, xValue = announcedX))
                cast.error shouldBe null
                cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe payment
                cast.events.filterIsInstance<SpellCastEvent>().single().manaValue shouldBe announcedX * 2
                game.state.manaValueOf(ballista) shouldBe announcedX * 2
                game.resolveStack().forEach { it.error shouldBe null }
                plusOne(game, animar) shouldBe startingCounters + 1
                game.state.manaValueOf(ballista) shouldBe 0
                if (announcedX == 0) {
                    game.findPermanent("Walking Ballista") shouldBe null
                } else {
                    plusOne(game, ballista) shouldBe announcedX
                    repeat(announcedX) { used ->
                        val damageAbility = cardRegistry.getCard("Walking Ballista")!!.script.activatedAbilities[1].id
                        val ping = enumerator.enumerate(game.state, game.player1Id).single {
                            val activation = it.action as? ActivateAbility
                            activation?.sourceId == ballista && activation.abilityId == damageAbility
                        }
                        ping.affordable shouldBe true
                        ping.requiresTargets shouldBe true
                        check(game.player2Id in ping.validTargets.orEmpty())
                        check(handLand !in ping.validTargets.orEmpty())
                        if (used == 0) {
                            game.passPriority().error shouldBe null
                            game.state.priorityPlayerId shouldBe game.player2Id
                            val outOfPriority = game.execute(ActivateAbility(game.player1Id, ballista, damageAbility,
                                targets = listOf(entityIdToChosenTarget(game.state, game.player2Id))))
                            check(outOfPriority.error != null) { "Controller still needs priority" }
                            plusOne(game, ballista) shouldBe announcedX
                            game.passPriority().error shouldBe null
                            game.state.priorityPlayerId shouldBe game.player1Id
                        }
                        val wrongPilot = game.execute(ActivateAbility(game.player2Id, ballista, damageAbility,
                            targets = listOf(entityIdToChosenTarget(game.state, game.player1Id))))
                        check(wrongPilot.error != null) { "Noncontroller must not activate Ballista" }
                        plusOne(game, ballista) shouldBe announcedX - used
                        val result = game.execute(ActivateAbility(game.player1Id, ballista, damageAbility,
                            targets = listOf(entityIdToChosenTarget(game.state, game.player2Id))))
                        result.error shouldBe null
                        result.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 0
                        game.resolveStack().forEach { it.error shouldBe null }
                        game.getLifeTotal(2) shouldBe 19 - used
                    }
                    game.findPermanent("Walking Ballista") shouldBe null
                }
                println("R4_BALLISTA_MATRIX X=$announcedX animarBefore=$startingCounters manaPaid=$payment " +
                    "animarAfter=${plusOne(game, animar)} pings=$announcedX opponentLife=${game.getLifeTotal(2)} castEvents=${cast.events}")
            }
        }
        test("five Animar counters reduce double X three to one and enumeration advertises X three") {
            val game = scenario().withPlayers("Manual", "Opponent")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Walking Ballista")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val animar = game.findPermanent("Animar, Soul of Elements")!!
            game.state = game.state.updateEntity(animar) {
                it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 5))
            }
            val services = EngineServices(cardRegistry)
            val actions = LegalActionEnumerator(cardRegistry, services.manaSolver, services.costCalculator,
                services.predicateEvaluator, services.conditionEvaluator, services.turnManager)
                .enumerate(game.state, game.player1Id)
            val ballistaId = game.state.getHand(game.player1Id).single()
            actions.first { (it.action as? CastSpell)?.cardId == ballistaId }.maxAffordableX shouldBe 3
            val cast = game.castXSpell(1, "Walking Ballista", xValue = 3)
            cast.error shouldBe null
            cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 1
            game.resolveStack()
            plusOne(game, game.findPermanent("Walking Ballista")!!) shouldBe 3
        }

        test("Animar reduces total double X payment while Ballista still enters with chosen X counters") {
            val game = scenario().withPlayers("Manual", "Opponent")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Walking Ballista")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val animar = game.findPermanent("Animar, Soul of Elements")!!
            game.state = game.state.updateEntity(animar) {
                it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 6))
            }
            val cast = game.castXSpell(1, "Walking Ballista", xValue = 3)
            cast.error shouldBe null
            cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 0
            game.resolveStack()
            plusOne(game, game.findPermanent("Walking Ballista")!!) shouldBe 3
            plusOne(game, animar) shouldBe 7
        }

        test("X zero Ballista dies even though Animar receives the creature cast counter") {
            val game = scenario().withPlayers("Manual", "Opponent")
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Walking Ballista")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val animar = game.findPermanent("Animar, Soul of Elements")!!
            game.castXSpell(1, "Walking Ballista", xValue = 0).error shouldBe null
            game.resolveStack()
            game.findPermanent("Walking Ballista") shouldBe null
            plusOne(game, animar) shouldBe 1
        }

        test("Walking Ballista pays twice X and enters with exactly X +1/+1 counters") {
            val game = scenario()
                .withPlayers("Manual", "Opponent")
                .withCardInHand(1, "Walking Ballista")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castXSpell(1, "Walking Ballista", xValue = 2)
            withClue("X=2 on {X}{X} must cost four mana") {
                cast.error shouldBe null
                cast.events.filterIsInstance<ManaSpentEvent>().sumOf { it.total } shouldBe 4
            }
            game.resolveStack()

            val ballista = game.findPermanent("Walking Ballista")!!
            plusOne(game, ballista) shouldBe 2
            projector.getProjectedPower(game.state, ballista) shouldBe 2
            projector.getProjectedToughness(game.state, ballista) shouldBe 2
        }

        test("Walking Ballista can buy a counter then remove one to deal one damage") {
            val game = scenario()
                .withPlayers("Manual", "Opponent")
                .withCardInHand(1, "Walking Ballista")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withLifeTotal(2, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castXSpell(1, "Walking Ballista", xValue = 1).error shouldBe null
            game.resolveStack()

            val ballista = game.findPermanent("Walking Ballista")!!
            plusOne(game, ballista) shouldBe 1
            val definition = cardRegistry.getCard("Walking Ballista")!!

            val addCounter = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = ballista,
                    abilityId = definition.script.activatedAbilities[0].id,
                )
            )
            withClue("the {4} counter ability should be payable from the four untapped Forests") {
                addCounter.error shouldBe null
            }
            game.resolveStack()
            plusOne(game, ballista) shouldBe 2

            val ping = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = ballista,
                    abilityId = definition.script.activatedAbilities[1].id,
                    targets = listOf(entityIdToChosenTarget(game.state, game.player2Id)),
                )
            )
            withClue("removing a +1/+1 counter is the activation cost") {
                ping.error shouldBe null
                plusOne(game, ballista) shouldBe 1
            }
            game.resolveStack()

            game.getLifeTotal(2) shouldBe 19
            plusOne(game, ballista) shouldBe 1
            projector.getProjectedPower(game.state, ballista) shouldBe 1
            projector.getProjectedToughness(game.state, ballista) shouldBe 1
        }
    }
}
