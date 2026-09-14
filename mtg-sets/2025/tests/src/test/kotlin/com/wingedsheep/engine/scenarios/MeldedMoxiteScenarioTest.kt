package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.PermanentsSacrificedEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Seedless Gate 2 rules coverage for the canonical Melded Moxite definition.
 *
 * These fixtures submit one action at a time through the authoritative action processor. They do
 * not use a matchup driver, shuffle a deck, generate a seed, or execute an experimental game.
 */
class MeldedMoxiteScenarioTest : ScenarioTestBase() {

    private fun moxiteGame(handCards: List<String>, libraryCards: List<String>): TestGame {
        val builder = scenario()
            .withPlayers("Mono Red", "Pest Control")
            .withCardInHand(1, "Melded Moxite")
            .withLandsOnBattlefield(1, "Mountain", 2)
            .withActivePlayer(1)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        handCards.forEach { builder.withCardInHand(1, it) }
        libraryCards.forEach { builder.withCardInLibrary(1, it) }
        return builder.build()
    }

    init {
        context("Melded Moxite") {

            test("accepting the ETB discards the selected card and draws exactly two") {
                val game = moxiteGame(
                    handCards = listOf("Mountain", "Lightning Bolt"),
                    libraryCards = listOf("Mountain", "Lava Dart", "Fireblast"),
                )
                val libraryBefore = game.librarySize(1)

                val cast = game.castSpell(1, "Melded Moxite")
                cast.error shouldBe null
                if (game.getPendingDecision() is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().error shouldBe null
                }
                game.resolveStack()

                withClue("the resolved ETB must expose the optional discard decision") {
                    (game.getPendingDecision() is YesNoDecision) shouldBe true
                }
                game.answerYesNo(true).error shouldBe null
                val discard = game.getPendingDecision()
                withClue("accepting the may requires an actual discard selection") {
                    (discard is SelectCardsDecision) shouldBe true
                }
                val bolt = (discard as SelectCardsDecision).options.single { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Lightning Bolt"
                }
                val discardResult = game.selectCards(listOf(bolt))
                discardResult.error shouldBe null
                val resolution = game.resolveStack()
                val events = discardResult.events + resolution.flatMap { it.events }

                game.librarySize(1) shouldBe libraryBefore - 2
                game.handSize(1) shouldBe 3
                game.state.getGraveyard(game.player1Id) shouldContain bolt
                events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
                events.filterIsInstance<ZoneChangeEvent>().any {
                    it.entityId == bolt && it.fromZone == Zone.HAND && it.toZone == Zone.GRAVEYARD
                } shouldBe true

                withClue("the completed trigger state must survive canonical JSON serialization") {
                    SerializationTestSupport.roundTrip(game.state) shouldBe game.state
                }
            }

            test("declining the ETB neither discards nor draws") {
                val game = moxiteGame(
                    handCards = listOf("Lightning Bolt"),
                    libraryCards = listOf("Mountain", "Lava Dart"),
                )
                val libraryBefore = game.librarySize(1)

                game.castSpell(1, "Melded Moxite").error shouldBe null
                if (game.getPendingDecision() is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().error shouldBe null
                }
                game.resolveStack()
                (game.getPendingDecision() is YesNoDecision) shouldBe true
                val declined = game.answerYesNo(false)
                declined.error shouldBe null
                val resolution = game.resolveStack()

                game.librarySize(1) shouldBe libraryBefore
                game.handSize(1) shouldBe 1
                game.graveyardSize(1) shouldBe 0
                game.isOnBattlefield("Melded Moxite") shouldBe true
                (declined.events + resolution.flatMap { it.events })
                    .filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
            }

            test("the ETB cannot draw when no card can be discarded") {
                val game = moxiteGame(
                    handCards = emptyList(),
                    libraryCards = listOf("Mountain", "Lava Dart"),
                )
                val libraryBefore = game.librarySize(1)

                game.castSpell(1, "Melded Moxite").error shouldBe null
                if (game.getPendingDecision() is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().error shouldBe null
                }
                val resolution = game.resolveStack()

                withClue("an impossible discard cost must not expose or complete the draw branch") {
                    game.getPendingDecision() shouldBe null
                    game.librarySize(1) shouldBe libraryBefore
                    resolution.flatMap { it.events }.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
                }
            }

            test("paying three and sacrificing Moxite creates one tapped 2/2 colorless Robot artifact token") {
                val game = scenario()
                    .withPlayers("Mono Red", "Pest Control")
                    .withCardOnBattlefield(1, "Melded Moxite")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val moxite = game.findPermanent("Melded Moxite")!!
                val ability = cardRegistry.requireCard("Melded Moxite").activatedAbilities.single()

                val activation = game.execute(
                    ActivateAbility(game.player1Id, moxite, ability.id)
                )
                activation.error shouldBe null
                val payment = if (game.getPendingDecision() is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().also { it.error shouldBe null }
                } else null
                val resolution = game.resolveStack()
                val events = activation.events + payment?.events.orEmpty() + resolution.flatMap { it.events }

                game.isInGraveyard(1, "Melded Moxite") shouldBe true
                val robot = game.state.getBattlefield(game.player1Id).single { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Robot Token"
                }
                val robotEntity = game.state.getEntity(robot)!!
                val robotCard = robotEntity.get<CardComponent>()!!
                robotEntity.get<TokenComponent>() shouldBe TokenComponent
                robotEntity.has<TappedComponent>() shouldBe true
                robotCard.baseStats?.basePower shouldBe 2
                robotCard.baseStats?.baseToughness shouldBe 2
                robotCard.colors shouldBe emptySet()
                robotCard.typeLine.isArtifact shouldBe true
                robotCard.typeLine.isCreature shouldBe true
                robotCard.typeLine.subtypes.map { it.value } shouldContain "Robot"
                events.filterIsInstance<PermanentsSacrificedEvent>().single().permanentIds shouldContain moxite
                events.filterIsInstance<ZoneChangeEvent>().any {
                    it.entityId == moxite && it.fromZone == Zone.BATTLEFIELD && it.toZone == Zone.GRAVEYARD
                } shouldBe true
                events.filterIsInstance<ZoneChangeEvent>().any {
                    it.entityId == robot && it.toZone == Zone.BATTLEFIELD
                } shouldBe true

                withClue("the sacrificed source and created token must survive serialization") {
                    SerializationTestSupport.roundTrip(game.state) shouldBe game.state
                }
            }

            test("the sacrifice ability cannot be activated without three mana") {
                val game = scenario()
                    .withPlayers("Mono Red", "Pest Control")
                    .withCardOnBattlefield(1, "Melded Moxite")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val moxite = game.findPermanent("Melded Moxite")!!
                val ability = cardRegistry.requireCard("Melded Moxite").activatedAbilities.single()

                val result = game.execute(ActivateAbility(game.player1Id, moxite, ability.id))

                (result.error != null) shouldBe true
                game.findPermanent("Melded Moxite") shouldBe moxite
                game.isInGraveyard(1, "Melded Moxite") shouldBe false
                game.state.getBattlefield(game.player1Id).none { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Robot Token"
                } shouldBe true
            }
        }
    }
}
