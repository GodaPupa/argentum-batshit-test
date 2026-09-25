package com.wingedsheep.engine.mechanics.stack

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SpellCounteredEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CantBeCounteredComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** CR 702.34a: a paid flashback cost replaces each tested counter destination with exile. */
class FlashbackCounterDestinationTest : FunSpec({
    val spellCard = card("Flashback Counter Fixture") {
        manaCost = "{G}"
        typeLine = "Instant"
        spell { effect = Effects.GainLife(3) }
        keywordAbility(KeywordAbility.flashback("{G}"))
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(spellCard))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun cast(d: GameTestDriver, flashback: Boolean): EntityId {
        val id = if (flashback) d.putCardInGraveyard(d.player1, spellCard.name)
            else d.putCardInHand(d.player1, spellCard.name)
        d.giveMana(d.player1, Color.GREEN, 1)
        d.submit(CastSpell(playerId = d.player1, cardId = id,
            useAlternativeCost = flashback,
            alternativeCostType = if (flashback) AlternativeCostType.FLASHBACK else null)).isSuccess shouldBe true
        val stacked = d.state.getEntity(id)!!.get<SpellOnStackComponent>()!!
        stacked.alternativeCost shouldBe if (flashback) AlternativeCostType.FLASHBACK else null
        stacked.castFromZone shouldBe if (flashback) Zone.GRAVEYARD else Zone.HAND
        return id
    }
    fun counter(d: GameTestDriver, id: EntityId, destination: Zone) =
        StackResolver(cardRegistry = d.cardRegistry).let { resolver ->
            when (destination) {
                Zone.GRAVEYARD -> resolver.counterSpell(d.state, id)
                Zone.HAND -> resolver.counterSpellToHand(d.state, id)
                Zone.LIBRARY -> resolver.counterSpellToLibraryTop(d.state, id)
                else -> error("fixture counter destination")
            }
        }
    for (destination in listOf(Zone.GRAVEYARD, Zone.HAND, Zone.LIBRARY)) {
        for (flashback in listOf(false, true)) {
            test("counter destination $destination honors paid flashback=$flashback and emits the actual transition") {
                val d = fixture()
                val id = cast(d, flashback)
                val oldObject = d.state.objectRef(id)
                val expected = if (flashback) Zone.EXILE else destination
                val result = counter(d, id, destination)
                result.isSuccess shouldBe true
                result.state.stack.contains(id) shouldBe false
                result.state.getZone(ZoneKey(d.player1, expected)).contains(id) shouldBe true
                if (expected == Zone.LIBRARY) result.state.getLibrary(d.player1).first() shouldBe id
                result.state.getEntity(id)!!.get<SpellOnStackComponent>() shouldBe null
                result.events.filterIsInstance<SpellCounteredEvent>().size shouldBe 1
                val moved = result.events.filterIsInstance<ZoneChangeEvent>().single()
                moved.fromZone shouldBe Zone.STACK
                moved.toZone shouldBe expected
                moved.oldObject shouldBe oldObject
                moved.newObject shouldBe result.state.objectRef(id)
                d.replaceState(result.state)
                d.getLifeTotal(d.player1) shouldBe 20
            }
        }
    }
    test("uncounterable flashback remains on the stack without destination or counter events") {
        val d = fixture()
        val id = cast(d, true)
        d.addComponent(id, CantBeCounteredComponent)
        for (destination in listOf(Zone.GRAVEYARD, Zone.HAND, Zone.LIBRARY)) {
            val result = counter(d, id, destination)
            result.isSuccess shouldBe true
            result.state shouldBe d.state
            result.events shouldBe emptyList()
        }
    }
    test("paid flashback provenance survives state serialization before a library-top counter") {
        val d = fixture()
        val id = cast(d, true)
        val before = counter(d, id, Zone.LIBRARY)
        d.replaceState(SerializationTestSupport.roundTrip(d.state))
        counter(d, id, Zone.LIBRARY) shouldBe before
    }
    for (paid in listOf(null, AlternativeCostType.MAYHEM)) {
        test("graveyard origin and printed flashback do not override another paid identity: $paid") {
            val d = fixture()
            val id = cast(d, true)
            // Model the counter's input from a different graveyard-casting permission. This probe
            // deliberately qualifies counter provenance only, not another casting permission.
            d.replaceState(d.state.updateEntity(id) { c ->
                c.with(c.get<SpellOnStackComponent>()!!.copy(alternativeCost = paid))
            })
            val result = counter(d, id, Zone.GRAVEYARD)
            result.isSuccess shouldBe true
            result.state.getGraveyard(d.player1).contains(id) shouldBe true
            result.state.getZone(ZoneKey(d.player1, Zone.EXILE)).contains(id) shouldBe false
        }
    }
})
