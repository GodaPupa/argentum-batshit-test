package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mh3.cards.DiscipleOfFreyalise
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DiscipleOfFreyaliseScenarioTest : ScenarioTestBase() {
    init {
        test("canonical front and back characteristics are preserved") {
            DiscipleOfFreyalise.name shouldBe "Disciple of Freyalise"
            DiscipleOfFreyalise.manaCost.toString() shouldBe "{3}{G}{G}{G}"
            DiscipleOfFreyalise.typeLine.cardTypes.contains(CardType.CREATURE) shouldBe true
            DiscipleOfFreyalise.creatureStats?.basePower shouldBe 3
            DiscipleOfFreyalise.creatureStats?.baseToughness shouldBe 3
            DiscipleOfFreyalise.metadata.rarity shouldBe com.wingedsheep.sdk.model.Rarity.UNCOMMON
            DiscipleOfFreyalise.metadata.collectorNumber shouldBe "250"
            DiscipleOfFreyalise.metadata.artist shouldBe "Valera Lutfullina"
            val back = DiscipleOfFreyalise.backFace!!
            back.name shouldBe "Garden of Freyalise"
            back.typeLine.isLand shouldBe true
            back.manaCost.isEmpty() shouldBe true
            back.script.replacementEffects.filterIsInstance<com.wingedsheep.sdk.scripting.EntersTapped>()
                .single().payLifeCost shouldBe 3
        }

        test("front ETB may sacrifice another creature and uses last-known power for both life and cards") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Disciple of Freyalise")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardOnBattlefield(1, "Centaur Courser")
                .withCardOnBattlefield(1, "Llanowar Elves")
                .withCardOnBattlefield(1, "Glorious Anthem")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val courser = game.findPermanent("Centaur Courser")!!
            val lifeBefore = game.getLifeTotal(1)
            val handBefore = game.handSize(1)

            game.castSpell(1, "Disciple of Freyalise").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true)
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            val decision = game.getPendingDecision() as SelectCardsDecision
            withClue("Disciple says another creature") {
                decision.options.contains(game.findPermanent("Disciple of Freyalise")!!) shouldBe false
            }
            game.selectCards(listOf(courser))
            game.resolveStack()

            withClue("Anthem made the sacrificed Courser 4 power as it last existed") {
                game.getLifeTotal(1) shouldBe lifeBefore + 4
            }
            withClue("the same LKI X draws four; casting Disciple spent one card first") {
                game.handSize(1) shouldBe handBefore - 1 + 4
            }
            game.isInGraveyard(1, "Centaur Courser") shouldBe true
        }

        test("declining the front ETB sacrifice does nothing") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Disciple of Freyalise")
                .withLandsOnBattlefield(1, "Forest", 6)
                .withCardOnBattlefield(1, "Centaur Courser")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val lifeBefore = game.getLifeTotal(1)
            game.castSpell(1, "Disciple of Freyalise").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(false)
            game.resolveStack()
            game.getLifeTotal(1) shouldBe lifeBefore
            game.isOnBattlefield("Centaur Courser") shouldBe true
        }

        test("Garden pay three life happens before entry and enters untapped") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Disciple of Freyalise")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val id = game.findCardsInHand(1, "Disciple of Freyalise").single()
            val result = game.execute(PlayLand(game.player1Id, id, asBackFace = true))
            result.error shouldBe null
            withClue("pre-entry choice pauses before battlefield visibility") {
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                (id in game.state.getBattlefield()) shouldBe false
            }
            game.answerYesNo(true)
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 17
            game.state.getEntity(id)!!.get<CardComponent>()!!.name shouldBe "Garden of Freyalise"
            game.state.getEntity(id)!!.has<TappedComponent>() shouldBe false
        }

        test("Garden decline enters tapped and pays no life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Disciple of Freyalise")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val id = game.findCardsInHand(1, "Disciple of Freyalise").single()
            game.execute(PlayLand(game.player1Id, id, asBackFace = true)).error shouldBe null
            game.answerYesNo(false)
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.state.getEntity(id)!!.has<TappedComponent>() shouldBe true
        }

        test("Garden does not offer an unaffordable life payment") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Disciple of Freyalise")
                .withLifeTotal(1, 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val id = game.findCardsInHand(1, "Disciple of Freyalise").single()
            game.execute(PlayLand(game.player1Id, id, asBackFace = true)).error shouldBe null
            game.hasPendingDecision() shouldBe false
            game.getLifeTotal(1) shouldBe 2
            game.state.getEntity(id)!!.has<TappedComponent>() shouldBe true
        }

        test("Garden taps for green and the card is front-face identity in hand") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Disciple of Freyalise")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val id = game.findCardsInHand(1, "Disciple of Freyalise").single()
            game.state.getEntity(id)!!.get<CardComponent>()!!.name shouldBe "Disciple of Freyalise"
            game.execute(PlayLand(game.player1Id, id, asBackFace = true)).error shouldBe null
            game.answerYesNo(true)
            game.resolveStack()
            val manaAbility = DiscipleOfFreyalise.backFace!!.script.activatedAbilities.single { it.isManaAbility }
            game.execute(ActivateAbility(game.player1Id, id, manaAbility.id)).error shouldBe null
            game.state.getEntity(game.player1Id)!!.get<ManaPoolComponent>()!!.green shouldBe 1
        }
    }
}
