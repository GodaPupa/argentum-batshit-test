package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.PlayerLeftGameComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

/** An actual Ballista elimination must remove that player from future target choices. */
class WalkingBallistaDepartedPlayerTest : ScenarioTestBase() {
    init {
        test("real four-player elimination excludes departed target and preserves remaining legal shots") {
            val game = scenario().withPlayers("Manual", "First opponent")
                .withLifeTotal(1, 40).withLifeTotal(2, 1)
                .withCardInHand(1, "Walking Ballista").withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val reference = requireNotNull(game.state.getEntity(game.player2Id))
            val extra = listOf(EntityId("player-3"), EntityId("player-4"))
            extra.forEach { id -> game.state = game.state.withEntity(id,
                reference.with(PlayerComponent(id.value)).with(LifeTotalComponent(2))) }
            game.state = game.state.copy(turnOrder = game.state.turnOrder + extra)
            val cast = game.execute(CastSpell(game.player1Id, game.state.getHand(game.player1Id).single(), xValue = 2))
            cast.error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val ballista = requireNotNull(game.findPermanent("Walking Ballista"))
            val ability = cardRegistry.getCard("Walking Ballista")!!.script.activatedAbilities[1].id
            fun shot(target: EntityId) = ActivateAbility(game.player1Id, ballista, ability,
                targets = listOf(ChosenTarget.Player(target)))
            val first = game.execute(shot(game.player2Id))
            first.error shouldBe null
            val elimination = game.resolveStack()
            elimination.forEach { it.error shouldBe null }
            check(elimination.flatMap { it.events }.any { it is PlayerLeftGameEvent && it.playerId == game.player2Id })
            check(game.state.getEntity(game.player2Id)!!.has<PlayerLeftGameComponent>())
            game.state.activePlayers shouldBe listOf(game.player1Id) + extra
            val offered = LegalActionEnumerator.create(cardRegistry).enumerate(game.state, game.player1Id)
                .single { (it.action as? ActivateAbility)?.let { a -> a.sourceId == ballista && a.abilityId == ability } == true }
            check(game.player2Id !in offered.validTargets.orEmpty())
            check(extra.all { it in offered.validTargets.orEmpty() })
            val rejected = game.execute(shot(game.player2Id))
            rejected.error shouldBe "Target player is no longer in the game"
            game.state.getEntity(ballista)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            val remaining = game.execute(shot(extra.first()))
            remaining.error shouldBe null
            val damage = game.resolveStack()
            damage.forEach { it.error shouldBe null }
            game.state.lifeTotal(extra.first()) shouldBe 1
            game.state.gameOver shouldBe false
            println("R4_DEPARTED_TARGET first=${first.events} elimination=${elimination.flatMap { it.events }} rejected=${rejected.error} remaining=${remaining.events} damage=${damage.flatMap { it.events }}")
        }
    }
}
