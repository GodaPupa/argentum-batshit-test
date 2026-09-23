package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.BestowComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate9Mh3NyxbornScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    private fun plusOneCounters(game: TestGame, entityId: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(entityId)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun castBestow(
        game: TestGame,
        x: Int,
        target: com.wingedsheep.sdk.model.EntityId
    ) = run {
        val hydra = game.state.getHand(game.player1Id).single { id ->
            game.state.getEntity(id)?.get<CardComponent>()?.name == "Nyxborn Hydra"
        }
        game.execute(
            CastSpell(
                playerId = game.player1Id,
                cardId = hydra,
                targets = listOf(ChosenTarget.Permanent(target)),
                xValue = x,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.BESTOW
            )
        )
    }

    init {
        test("Nyxborn Hydra cast normally for X=3 enters as a 3/4 with three counters") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castXSpell(1, "Nyxborn Hydra", xValue = 3).error shouldBe null
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            plusOneCounters(game, hydra) shouldBe 3
            projector.getProjectedPower(game.state, hydra) shouldBe 3
            projector.getProjectedToughness(game.state, hydra) shouldBe 4
            projector.getProjectedKeywords(game.state, hydra).contains(Keyword.REACH) shouldBe true
            projector.getProjectedKeywords(game.state, hydra).contains(Keyword.TRAMPLE) shouldBe true
        }

        test("Nyxborn Hydra bestowed for X=2 is an Aura with two counters and grants the host +2/+2 reach and trample") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            withClue("bestow cast should be legal") {
                castBestow(game, x = 2, target = host).error shouldBe null
            }
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            val hydraCard = game.state.getEntity(hydra)!!.get<CardComponent>()!!
            hydraCard.typeLine.isAura shouldBe true
            hydraCard.typeLine.isCreature shouldBe false
            game.state.getEntity(hydra)!!.get<BestowComponent>() shouldBe BestowComponent(
                originalTypeLine = com.wingedsheep.sdk.core.TypeLine.parse("Enchantment Creature — Hydra")
            )
            game.state.getEntity(hydra)!!.get<AttachedToComponent>()?.targetId shouldBe host
            plusOneCounters(game, hydra) shouldBe 2

            projector.getProjectedPower(game.state, host) shouldBe 3
            projector.getProjectedToughness(game.state, host) shouldBe 3
            projector.getProjectedKeywords(game.state, host).contains(Keyword.REACH) shouldBe true
            projector.getProjectedKeywords(game.state, host).contains(Keyword.TRAMPLE) shouldBe true
        }

        test("bestow target becoming illegal before resolution makes Nyxborn Hydra resolve as a creature") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            castBestow(game, x = 1, target = host).error shouldBe null

            // Make the target illegal before the Bestow spell resolves.
            game.state = game.state
                .removeFromZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD), host)
                .addToZone(ZoneKey(game.player1Id, Zone.GRAVEYARD), host)

            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            val hydraCard = game.state.getEntity(hydra)!!.get<CardComponent>()!!
            hydraCard.typeLine.isCreature shouldBe true
            hydraCard.typeLine.isAura shouldBe false
            game.state.getEntity(hydra)!!.get<BestowComponent>() shouldBe null
            plusOneCounters(game, hydra) shouldBe 1
            projector.getProjectedPower(game.state, hydra) shouldBe 1
            projector.getProjectedToughness(game.state, hydra) shouldBe 2
        }

        test("a bestowed Nyxborn Hydra becomes its normal creature when its host leaves") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Nyxborn Hydra")
                .withCardOnBattlefield(1, "Myr Retriever")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val host = game.findPermanent("Myr Retriever")!!
            castBestow(game, x = 2, target = host).error shouldBe null
            game.resolveStack()

            val hydra = game.findPermanent("Nyxborn Hydra")!!
            game.state = game.state
                .removeFromZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD), host)
                .addToZone(ZoneKey(game.player1Id, Zone.GRAVEYARD), host)

            game.checkStateBasedActions().error shouldBe null

            val hydraCard = game.state.getEntity(hydra)!!.get<CardComponent>()!!
            hydraCard.typeLine.isCreature shouldBe true
            hydraCard.typeLine.isAura shouldBe false
            game.state.getEntity(hydra)!!.get<BestowComponent>() shouldBe null
            game.state.getEntity(hydra)!!.get<AttachedToComponent>() shouldBe null
            plusOneCounters(game, hydra) shouldBe 2
            projector.getProjectedPower(game.state, hydra) shouldBe 2
            projector.getProjectedToughness(game.state, hydra) shouldBe 3
        }
    }
}
