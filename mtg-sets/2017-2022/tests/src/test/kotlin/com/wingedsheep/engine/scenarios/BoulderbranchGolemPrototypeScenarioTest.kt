package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PrototypeComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class BoulderbranchGolemPrototypeScenarioTest : ScenarioTestBase() {
    init {
        test("legal actions expose normal and Prototype casts with their own costs") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Boulderbranch Golem")
                .withLandsOnBattlefield(1, "Forest", 7)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cardId = game.findCardsInHand(1, "Boulderbranch Golem").single()
            val casts = game.getLegalActions(1).filter {
                val cast = it.action as? CastSpell
                cast?.cardId == cardId
            }

            casts.map { it.manaCostString }.shouldContainExactlyInAnyOrder("{7}", "{3}{G}")
            casts.map { (it.action as CastSpell).castForPrototype }
                .shouldContainExactlyInAnyOrder(false, true)
        }

        test("Prototype cast is green MV4 3/3 and gains 3 life on entry") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Boulderbranch Golem")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cardId = game.findCardsInHand(1, "Boulderbranch Golem").single()
            game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = cardId,
                    castForPrototype = true,
                )
            ).error shouldBe null

            val onStack = game.state.getEntity(cardId)?.get<CardComponent>()!!
            withClue("prototype spell uses prototype mana cost and color") {
                onStack.manaCost.toString() shouldBe "{3}{G}"
                onStack.manaValue shouldBe 4
                onStack.colors shouldBe setOf(Color.GREEN)
            }
            withClue("prototype spell retains printed types but uses prototype size") {
                onStack.typeLine.isArtifact shouldBe true
                onStack.typeLine.isCreature shouldBe true
                onStack.baseStats?.basePower shouldBe 3
                onStack.baseStats?.baseToughness shouldBe 3
                game.state.getEntity(cardId)?.has<PrototypeComponent>() shouldBe true
            }

            game.resolveStack()

            val permanent = game.findPermanent("Boulderbranch Golem")!!
            val onBattlefield = game.state.getEntity(permanent)?.get<CardComponent>()!!
            onBattlefield.manaValue shouldBe 4
            onBattlefield.colors shouldBe setOf(Color.GREEN)
            onBattlefield.baseStats?.basePower shouldBe 3
            onBattlefield.baseStats?.baseToughness shouldBe 3
            game.getLifeTotal(1) shouldBe 23
        }

        test("normal cast stays colorless 6/5 and gains 6 life on entry") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Boulderbranch Golem")
                .withLandsOnBattlefield(1, "Forest", 7)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Boulderbranch Golem").error shouldBe null
            game.resolveStack()

            val permanent = game.findPermanent("Boulderbranch Golem")!!
            val card = game.state.getEntity(permanent)?.get<CardComponent>()!!
            card.manaCost.toString() shouldBe "{7}"
            card.manaValue shouldBe 7
            card.colors shouldBe emptySet()
            card.baseStats?.basePower shouldBe 6
            card.baseStats?.baseToughness shouldBe 5
            game.getLifeTotal(1) shouldBe 26
        }

        test("prototype permanent restores printed characteristics after leaving battlefield") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Boulderbranch Golem")
                .withCardInHand(2, "Murder")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withLandsOnBattlefield(2, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cardId = game.findCardsInHand(1, "Boulderbranch Golem").single()
            game.execute(CastSpell(game.player1Id, cardId, castForPrototype = true)).error shouldBe null
            game.resolveStack()
            val permanent = game.findPermanent("Boulderbranch Golem")!!

            game.passPriority().error shouldBe null
            game.castSpell(2, "Murder", permanent).error shouldBe null
            game.resolveStack()

            val graveCardId = game.state.getGraveyard(game.player1Id).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Boulderbranch Golem"
            }
            val restored = game.state.getEntity(graveCardId)?.get<CardComponent>()!!
            restored.manaCost.toString() shouldBe "{7}"
            restored.manaValue shouldBe 7
            restored.colors shouldBe emptySet()
            restored.baseStats?.basePower shouldBe 6
            restored.baseStats?.baseToughness shouldBe 5
            game.state.getEntity(graveCardId)?.has<PrototypeComponent>() shouldBe false
        }

        test("countered Prototype spell restores printed characteristics in graveyard") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Boulderbranch Golem")
                .withCardInHand(2, "Counterspell")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withLandsOnBattlefield(2, "Island", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cardId = game.findCardsInHand(1, "Boulderbranch Golem").single()
            game.execute(CastSpell(game.player1Id, cardId, castForPrototype = true)).error shouldBe null

            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Boulderbranch Golem").error shouldBe null
            game.resolveStack()

            val restored = game.state.getEntity(cardId)?.get<CardComponent>()!!
            restored.manaCost.toString() shouldBe "{7}"
            restored.manaValue shouldBe 7
            restored.colors shouldBe emptySet()
            restored.baseStats?.basePower shouldBe 6
            restored.baseStats?.baseToughness shouldBe 5
            game.state.getEntity(cardId)?.has<PrototypeComponent>() shouldBe false
        }

        test("hand-constructed Prototype on a card without Prototype fails closed") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Forest", 7)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findCardsInHand(1, "Grizzly Bears").single()
            game.execute(
                CastSpell(game.player1Id, bears, castForPrototype = true)
            ).error shouldBe "Grizzly Bears has no Prototype ability"
        }

        test("return to hand clears Prototype and normal recast restores ordinary card") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Boulderbranch Golem")
                .withCardInHand(2, "Unsummon")
                .withLandsOnBattlefield(1, "Forest", 11)
                .withLandsOnBattlefield(2, "Island", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cardId = game.findCardsInHand(1, "Boulderbranch Golem").single()
            game.execute(CastSpell(game.player1Id, cardId, castForPrototype = true)).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 23

            val permanent = game.findPermanent("Boulderbranch Golem")!!
            game.passPriority().error shouldBe null
            game.castSpell(2, "Unsummon", permanent).error shouldBe null
            game.resolveStack()

            val returned = game.findCardsInHand(1, "Boulderbranch Golem").single()
            returned shouldBe cardId
            val restored = game.state.getEntity(returned)?.get<CardComponent>()!!
            restored.manaCost.toString() shouldBe "{7}"
            restored.manaValue shouldBe 7
            restored.colors shouldBe emptySet()
            restored.baseStats?.basePower shouldBe 6
            restored.baseStats?.baseToughness shouldBe 5
            game.state.getEntity(returned)?.has<PrototypeComponent>() shouldBe false

            game.castSpell(1, "Boulderbranch Golem").error shouldBe null
            game.resolveStack()
            val recast = game.findPermanent("Boulderbranch Golem")!!
            val ordinary = game.state.getEntity(recast)?.get<CardComponent>()!!
            ordinary.manaCost.toString() shouldBe "{7}"
            ordinary.manaValue shouldBe 7
            ordinary.baseStats?.basePower shouldBe 6
            ordinary.baseStats?.baseToughness shouldBe 5
            game.getLifeTotal(1) shouldBe 29
        }
    }
}
