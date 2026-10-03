package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.DoubleFacedComponent
import com.wingedsheep.engine.state.components.player.LandDropsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Generalized pre-entry land-play/payment transaction regressions. */
class PreEntryLandPlayPaymentTest : ScenarioTestBase() {
    private val shock = card("Pre-entry payment land") {
        typeLine = "Land — Forest"
        oracleText = "As this land enters, you may pay 3 life. If you don't, it enters tapped."
        replacementEffect(EntersTapped(payLifeCost = 3))
    }

    private val front = card("Pre-entry MDFC front") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Elf"
        power = 2
        toughness = 2
    }

    private val garden = card("Pre-entry MDFC garden") {
        typeLine = "Land"
        oracleText = "As this land enters, you may pay 3 life. If you don't, it enters tapped.\n{T}: Add {G}."
        replacementEffect(EntersTapped(payLifeCost = 3))
        activatedAbility {
            cost = AbilityCost.Tap
            effect = AddManaEffect(Color.GREEN)
            manaAbility = true
            timing = TimingRule.ManaAbility
        }
    }

    private val mdfc = CardDefinition.modalDoubleFacedSpellLand(front, garden)

    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    init {
        cardRegistry.register(shock)
        cardRegistry.register(mdfc)

        test("optional life choice pauses before source removal payment drop consumption or battlefield visibility") {
            val g = gameWith(shock.name)
            val id = g.findCardsInHand(1, shock.name).single()

            g.execute(PlayLand(g.player1Id, id)).error shouldBe null

            g.hasPendingDecision() shouldBe true
            g.state.getHand(g.player1Id).contains(id) shouldBe true
            g.state.getBattlefield(g.player1Id).contains(id) shouldBe false
            g.state.lifeTotal(g.player1Id) shouldBe 20
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 1
            g.state.continuationStack.last() is PreEntryLandPlayContinuation shouldBe true
        }

        test("serialized resume pays exactly once then places untapped and consumes exactly one drop") {
            val g = gameWith(shock.name)
            val id = g.findCardsInHand(1, shock.name).single()
            g.execute(PlayLand(g.player1Id, id)).error shouldBe null

            g.state = json.decodeFromString<GameState>(json.encodeToString(g.state))
            g.answerYesNo(true).error shouldBe null

            g.hasPendingDecision() shouldBe false
            g.state.lifeTotal(g.player1Id) shouldBe 17
            g.state.getBattlefield(g.player1Id).contains(id) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe false
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
        }

        test("declining places tapped without life loss and consumes exactly one drop") {
            val g = gameWith(shock.name)
            val id = g.findCardsInHand(1, shock.name).single()
            g.execute(PlayLand(g.player1Id, id)).error shouldBe null
            g.answerYesNo(false).error shouldBe null

            g.state.lifeTotal(g.player1Id) shouldBe 20
            g.state.getBattlefield(g.player1Id).contains(id) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
        }

        test("unaffordable optional payment cannot be chosen and commits the tapped decline without a prompt") {
            val g = gameWith(shock.name, life = 2)
            val id = g.findCardsInHand(1, shock.name).single()
            g.execute(PlayLand(g.player1Id, id)).error shouldBe null

            g.hasPendingDecision() shouldBe false
            g.state.lifeTotal(g.player1Id) shouldBe 2
            g.state.getBattlefield(g.player1Id).contains(id) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
        }

        test("stale source visit fails closed on resume without payment placement or drop consumption") {
            val g = gameWith(shock.name)
            val id = g.findCardsInHand(1, shock.name).single()
            g.execute(PlayLand(g.player1Id, id)).error shouldBe null

            val moved = ZoneTransitionService.moveToZone(g.state, id, Zone.GRAVEYARD)
            g.state = moved.state
            val resumed = g.answerYesNo(true)

            resumed.error shouldNotBe null
            g.state.lifeTotal(g.player1Id) shouldBe 20
            g.state.getBattlefield(g.player1Id).contains(id) shouldBe false
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 1
        }

        test("castable-front land-back MDFC preserves front in hand and commits the selected back face only after payment") {
            val g = gameWith(front.name)
            val id = g.findCardsInHand(1, front.name).single()

            g.execute(PlayLand(g.player1Id, id, asBackFace = true)).error shouldBe null
            g.hasPendingDecision() shouldBe true
            g.state.getEntity(id)?.get<CardComponent>()?.name shouldBe front.name
            g.state.getHand(g.player1Id).contains(id) shouldBe true

            g.state = json.decodeFromString<GameState>(json.encodeToString(g.state))
            g.answerYesNo(true).error shouldBe null

            g.state.lifeTotal(g.player1Id) shouldBe 17
            g.state.getEntity(id)?.get<CardComponent>()?.name shouldBe garden.name
            g.state.getEntity(id)?.get<DoubleFacedComponent>()?.currentFace shouldBe
                DoubleFacedComponent.Face.BACK
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe false
        }

        test("global lands-enter-untapped replacement elides a moot payment and leaves life unchanged") {
            val g = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, shock.name)
                .withCardOnBattlefield(1, "The Wandering Minstrel")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val id = g.findCardsInHand(1, shock.name).single()

            g.execute(PlayLand(g.player1Id, id)).error shouldBe null

            g.hasPendingDecision() shouldBe false
            g.state.lifeTotal(g.player1Id) shouldBe 20
            g.state.getBattlefield(g.player1Id).contains(id) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe false
        }
    }

    private fun gameWith(cardName: String, life: Int = 20) = scenario()
        .withPlayers("Player", "Opponent")
        .withLifeTotal(1, life)
        .withCardInHand(1, cardName)
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()
}
