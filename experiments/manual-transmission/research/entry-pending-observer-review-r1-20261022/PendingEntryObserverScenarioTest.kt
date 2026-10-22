package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Real copied-entry choices inside a resolving spell; queued triggers wait as one group. */
class PendingEntryObserverScenarioTest : FunSpec({
    val body = card("Pending Observer Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }
    val copier = card("Pending Observer Copier") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }
    for (once in listOf(false, true)) for (serialized in listOf(false, true)) {
        test("copied entry combines pre-pause and later observations before ordering and caps; once=$once, serialized=$serialized") {
            val observer = card("Pending Entry Observer $once") {
                manaCost = "{0}"; typeLine = "Enchantment"
                triggeredAbility {
                    trigger = Triggers.OtherCreatureEnters; oncePerTurn = once
                    effect = Effects.GainLife(3)
                }
            }
            val spell = card("Pending Observer Spell $once") {
                manaCost = "{0}"; typeLine = "Instant"
                spell { effect = Effects.Pipeline {
                    run(CreateTokenEffect(power = 2, toughness = 2,
                        colors = setOf(Color.GREEN), creatureTypes = setOf("Bear")))
                    val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
                    move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
                    val oldObserver = gather(CardSource.BattlefieldMatching(GameObjectFilter.Any.named(observer.name)))
                    move(oldObserver, CardDestination.ToZone(Zone.EXILE))
                } }
            }
            val d = GameTestDriver().apply {
                registerCards(TestCards.all + listOf(body, copier, observer, spell))
                initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
                passPriorityUntil(Step.PRECOMBAT_MAIN)
            }
            val target = d.putCreatureOnBattlefield(d.player2, body.name)
            val source = d.putPermanentOnBattlefield(d.player1, observer.name)
            val entering = d.putCardOnTopOfLibrary(d.player1, copier.name)
            d.castSpell(d.player1, d.putCardInHand(d.player1, spell.name)).error shouldBe null
            d.bothPass().error shouldBe null
            (d.pendingDecision is SelectCardsDecision) shouldBe true
            d.state.continuationStack.filterIsInstance<PendingTriggersContinuation>()
                .flatMap { it.remainingTriggers }.count { it.sourceId == source } shouldBe 1
            d.getLifeTotal(d.player1) shouldBe 20
            if (serialized) restore(d)
            d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
            (d.pendingDecision is ChooseColorDecision) shouldBe true
            d.state.getBattlefield().contains(entering) shouldBe false
            d.getLifeTotal(d.player1) shouldBe 20
            if (serialized) restore(d)
            val color = d.pendingDecision as ChooseColorDecision
            d.submitDecision(color.playerId, ColorChosenResponse(color.id, Color.BLUE)).error shouldBe null
            d.state.getBattlefield().contains(entering) shouldBe true
            d.state.getBattlefield().contains(source) shouldBe false
            (d.pendingDecision is ChooseOptionDecision) shouldBe !once
            if (!once) {
                repeat(2) {
                    val order = d.pendingDecision as? ChooseOptionDecision
                    if (order != null) {
                        if (serialized) restore(d)
                        d.submitDecision(order.playerId, OptionChosenResponse(order.id, 0)).error shouldBe null
                    }
                }
            }
            d.pendingDecision shouldBe null
            d.state.continuationStack shouldBe emptyList()
            d.stackSize shouldBe if (once) 1 else 2
            d.getLifeTotal(d.player1) shouldBe 20
            repeat(if (once) 1 else 2) { d.bothPass().error shouldBe null }
            d.getLifeTotal(d.player1) shouldBe if (once) 23 else 26
            d.stackSize shouldBe 0
        }
    }
})
