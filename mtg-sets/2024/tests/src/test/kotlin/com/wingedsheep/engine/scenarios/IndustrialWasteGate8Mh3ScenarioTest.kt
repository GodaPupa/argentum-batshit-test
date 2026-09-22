package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate8Mh3ScenarioTest : ScenarioTestBase() {
    init {
        test("Writhing Chrysalis cast trigger creates two Spawn and sacrificing one grows it") {
            val game = scenario()
                .withPlayers("Jund", "Opponent")
                .withCardInHand(1, "Writhing Chrysalis")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val chrysalisInHand = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Writhing Chrysalis"
            }

            val cast = game.execute(CastSpell(playerId = game.player1Id, cardId = chrysalisInHand))
            withClue("Writhing Chrysalis should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            val chrysalis = game.findPermanent("Writhing Chrysalis")!!
            val spawns = game.state.getBattlefield(game.player1Id).filter { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Eldrazi Spawn"
            }
            spawns.size shouldBe 2

            val spawnAbility = cardRegistry.getCard("Eldrazi Spawn")!!.script.activatedAbilities.single().id
            val activate = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = spawns.first(),
                    abilityId = spawnAbility
                )
            )
            withClue("Eldrazi Spawn mana ability should activate: ${activate.error}") {
                activate.error shouldBe null
            }
            game.resolveStack()

            val counters = game.state.getEntity(chrysalis)?.get<CountersComponent>()
            (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
        }

        test("Twisted Landscape sacrifices itself and fetches only a Jund basic tapped") {
            val game = scenario()
                .withPlayers("Jund", "Opponent")
                .withCardOnBattlefield(1, "Twisted Landscape")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Plains")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val landscape = game.findPermanent("Twisted Landscape")!!
            val abilityId = cardRegistry.getCard("Twisted Landscape")!!.script.activatedAbilities[1].id
            val result = game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = landscape,
                    abilityId = abilityId
                )
            )
            withClue("Twisted Landscape fetch ability should activate: ${result.error}") {
                result.error shouldBe null
            }
            game.resolveStack()

            val decision = game.getPendingDecision()
            (decision is SelectCardsDecision) shouldBe true
            decision as SelectCardsDecision
            decision.options.size shouldBe 1
            val offeredName = game.state.getEntity(decision.options.single())?.get<CardComponent>()?.name
            offeredName shouldBe "Forest"
            game.selectCards(listOf(decision.options.single()))
            game.resolveStack()

            game.isInGraveyard(1, "Twisted Landscape") shouldBe true
            game.isOnBattlefield("Forest") shouldBe true
        }
    }
}
