package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.LandDropsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.EntersTapped
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** General pre-entry optional-payment transaction; no Disciple-specific shortcut. */
class PreEntryLandPlayPaymentTest : ScenarioTestBase() {
    private val paymentLand = card("Pre-entry Payment Land") {
        typeLine = "Land"
        oracleText = "As this land enters, you may pay 3 life. If you don't, it enters tapped."
        replacementEffect(EntersTapped(payLifeCost = 3))
    }
    private val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    private fun game() = scenario()
        .withPlayers("A", "B")
        .withCardInHand(1, paymentLand.name)
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun restored(state: GameState): GameState =
        json.decodeFromString(json.encodeToString(state))

    init {
        cardRegistry.register(paymentLand)

        test("serialized payment decision occurs before visibility events and land-drop consumption") {
            val g = game()
            val id = g.findCardsInHand(1, paymentLand.name).single()
            val paused = g.execute(PlayLand(g.player1Id, id))
            paused.error shouldBe null
            (g.state.pendingDecision is YesNoDecision) shouldBe true
            (id in g.state.getBattlefield()) shouldBe false
            (id in g.state.getHand(g.player1Id)) shouldBe true
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 1
            paused.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 0
            paused.events.filterIsInstance<LandPlayedEvent>().size shouldBe 0

            g.state = restored(g.state)
            val decision = g.state.pendingDecision as YesNoDecision
            val done = g.submitDecision(YesNoResponse(decision.id, true))
            done.error shouldBe null
            g.getLifeTotal(1) shouldBe 17
            (id in g.state.getBattlefield()) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe false
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
            done.events.filterIsInstance<ZoneChangeEvent>().count {
                it.entityId == id && it.toZone == Zone.BATTLEFIELD
            } shouldBe 1
            done.events.filterIsInstance<LandPlayedEvent>().count { it.landId == id } shouldBe 1

            (g.submitDecision(YesNoResponse(decision.id, true)).error != null) shouldBe true
            g.getLifeTotal(1) shouldBe 17
            g.state.getBattlefield().count { it == id } shouldBe 1
        }

        test("declining commits exactly one tapped entry without life payment") {
            val g = game()
            val id = g.findCardsInHand(1, paymentLand.name).single()
            g.execute(PlayLand(g.player1Id, id)).error shouldBe null
            val decision = g.state.pendingDecision as YesNoDecision
            val done = g.submitDecision(YesNoResponse(decision.id, false))
            done.error shouldBe null
            g.getLifeTotal(1) shouldBe 20
            (id in g.state.getBattlefield()) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
            done.events.filterIsInstance<ZoneChangeEvent>().count {
                it.entityId == id && it.toZone == Zone.BATTLEFIELD
            } shouldBe 1
        }

        test("unaffordable payment is not offered and land enters tapped") {
            val g = game()
            val id = g.findCardsInHand(1, paymentLand.name).single()
            g.state = g.state.updateEntity(g.player1Id) { it.with(LifeTotalComponent(2)) }
            val done = g.execute(PlayLand(g.player1Id, id))
            done.error shouldBe null
            g.state.pendingDecision shouldBe null
            g.getLifeTotal(1) shouldBe 2
            (id in g.state.getBattlefield()) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe true
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
        }

        test("stale announced source fails closed without payment drop or entry") {
            val g = game()
            val id = g.findCardsInHand(1, paymentLand.name).single()
            g.execute(PlayLand(g.player1Id, id)).error shouldBe null
            val decision = g.state.pendingDecision as YesNoDecision
            val moved = ZoneTransitionService.moveToZone(g.state, id, Zone.EXILE)
            g.state = restored(moved.state)

            val stale = g.submitDecision(YesNoResponse(decision.id, true))
            (stale.error != null) shouldBe true
            g.getLifeTotal(1) shouldBe 20
            (id in g.state.getBattlefield()) shouldBe false
            g.state.getEntity(g.player1Id)?.get<LandDropsComponent>()?.remaining shouldBe 1
        }

        test("an enters-untapped replacement suppresses a moot payment prompt") {
            val g = scenario()
                .withPlayers("A", "B")
                .withCardOnBattlefield(1, "The Wandering Minstrel")
                .withCardInHand(1, paymentLand.name)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val id = g.findCardsInHand(1, paymentLand.name).single()
            val done = g.execute(PlayLand(g.player1Id, id))
            done.error shouldBe null
            g.state.pendingDecision shouldBe null
            g.getLifeTotal(1) shouldBe 20
            (id in g.state.getBattlefield()) shouldBe true
            g.state.getEntity(id)?.has<TappedComponent>() shouldBe false
        }
    }
}
