package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.LandPlayedEvent
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.PreEntryLandPlayPaymentContinuation
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.LandDropsComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.EntersUntapped
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PreEntryLandPlayPaymentTest : FunSpec({
    val tollLand = card("Pre-entry Toll Land") {
        typeLine = "Land"
        replacementEffect(EntersTapped(payLifeCost = 3))
    }
    val untapper = card("Pre-entry Land Untapper") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(
            EntersUntapped(
                appliesTo = EventPattern.ZoneChangeEvent(
                    filter = GameObjectFilter.Land.youControl(),
                    to = Zone.BATTLEFIELD,
                )
            )
        )
    }
    val json = Json {
        serializersModule = com.wingedsheep.engine.core.engineSerializersModule
        allowStructuredMapKeys = true
        encodeDefaults = true
    }

    fun driver(life: Int = 20): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(tollLand, untapper))
        it.initMirrorMatch(Deck.of("Forest" to 40), startingLife = life)
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun remainingDrops(d: GameTestDriver): Int =
        d.state.getEntity(d.player1)?.get<LandDropsComponent>()?.remaining ?: 1

    test("optional payment pauses before source removal placement land-drop use or entry events") {
        val d = driver()
        val land = d.putCardInHand(d.player1, tollLand.name)
        val oldRef = d.state.objectRef(land)!!

        val result = d.playLand(d.player1, land)

        result.isPaused shouldBe true
        d.state.pendingDecision shouldNotBe null
        (d.state.pendingDecision is YesNoDecision) shouldBe true
        (land in d.state.getHand(d.player1)) shouldBe true
        (land in d.state.getBattlefield()) shouldBe false
        remainingDrops(d) shouldBe 1
        result.events.filterIsInstance<ZoneChangeEvent>().shouldBeEmpty()
        result.events.filterIsInstance<LandPlayedEvent>().shouldBeEmpty()

        val suspension = d.state.continuationStack.last() as Suspension
        val continuation = suspension.answer as PreEntryLandPlayPaymentContinuation
        continuation.source shouldBe oldRef
        continuation.sourceZone shouldBe ZoneKey(d.player1, Zone.HAND)
        continuation.action shouldBe PlayLand(d.player1, land)
        continuation.lifeCost shouldBe 3
    }

    test("serialized yes payment commits life entry land drop and events exactly once") {
        val d = driver()
        val land = d.putCardInHand(d.player1, tollLand.name)
        d.playLand(d.player1, land).isPaused shouldBe true

        d.replaceState(json.decodeFromString<GameState>(json.encodeToString(d.state)))
        val result = d.submitYesNo(d.player1, true)

        result.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 17
        (land in d.state.getHand(d.player1)) shouldBe false
        (land in d.state.getBattlefield()) shouldBe true
        d.state.getEntity(land)?.has<TappedComponent>() shouldBe false
        remainingDrops(d) shouldBe 0
        result.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 1
        result.events.filterIsInstance<LandPlayedEvent>().size shouldBe 1
    }

    test("decline commits the same land play once and enters tapped without paying life") {
        val d = driver()
        val land = d.putCardInHand(d.player1, tollLand.name)
        d.playLand(d.player1, land).isPaused shouldBe true

        val result = d.submitYesNo(d.player1, false)

        result.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 20
        (land in d.state.getBattlefield()) shouldBe true
        d.state.getEntity(land)?.has<TappedComponent>() shouldBe true
        remainingDrops(d) shouldBe 0
        result.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 1
        result.events.filterIsInstance<LandPlayedEvent>().size shouldBe 1
    }

    test("unaffordable life payment is not offered and the land enters tapped") {
        val d = driver(life = 2)
        val land = d.putCardInHand(d.player1, tollLand.name)

        val result = d.playLand(d.player1, land)

        result.isSuccess shouldBe true
        d.state.pendingDecision shouldBe null
        d.getLifeTotal(d.player1) shouldBe 2
        (land in d.state.getBattlefield()) shouldBe true
        d.state.getEntity(land)?.has<TappedComponent>() shouldBe true
        remainingDrops(d) shouldBe 0
    }

    test("stale source zone fails closed without spending life or a land drop") {
        val d = driver()
        val land = d.putCardInHand(d.player1, tollLand.name)
        d.playLand(d.player1, land).isPaused shouldBe true

        val hand = ZoneKey(d.player1, Zone.HAND)
        val graveyard = ZoneKey(d.player1, Zone.GRAVEYARD)
        d.replaceState(d.state.removeFromZone(hand, land).addToZone(graveyard, land))

        val result = d.submitYesNo(d.player1, true)

        result.error shouldNotBe null
        d.getLifeTotal(d.player1) shouldBe 20
        (land in d.state.getBattlefield()) shouldBe false
        (land in d.state.getGraveyard(d.player1)) shouldBe true
        remainingDrops(d) shouldBe 1
    }

    test("a forced-untapped replacement suppresses the optional payment and still enters untapped") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, untapper.name)
        val land = d.putCardInHand(d.player1, tollLand.name)

        val result = d.playLand(d.player1, land)

        result.isSuccess shouldBe true
        d.state.pendingDecision shouldBe null
        d.getLifeTotal(d.player1) shouldBe 20
        (land in d.state.getBattlefield()) shouldBe true
        d.state.getEntity(land)?.has<TappedComponent>() shouldBe false
        remainingDrops(d) shouldBe 0
    }

    test("forced-untapped suppression commits one land play and one entry without a life-payment decision") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, untapper.name)
        val land = d.putCardInHand(d.player1, tollLand.name)

        val result = d.playLand(d.player1, land)

        result.error shouldBe null
        d.state.pendingDecision shouldBe null
        d.getLifeTotal(d.player1) shouldBe 20
        (land in d.state.getHand(d.player1)) shouldBe false
        (land in d.state.getBattlefield()) shouldBe true
        d.state.getEntity(land)?.has<TappedComponent>() shouldBe false
        remainingDrops(d) shouldBe 0
        result.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 1
        result.events.filterIsInstance<LandPlayedEvent>().size shouldBe 1
    }
})
