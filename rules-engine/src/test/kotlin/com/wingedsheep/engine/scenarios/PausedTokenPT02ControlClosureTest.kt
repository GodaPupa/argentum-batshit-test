package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.support.chooseTriggerOrderInListedOrder
import com.wingedsheep.engine.support.hasPendingTriggerOrder
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Fixed two-token component fixtures; no game or competitive allocation. */
class PausedTokenPT02ControlClosureTest : FunSpec({
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun roundTrip(d: GameTestDriver, serialized: Boolean) {
        if (!serialized) return
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }
    fun tokens(d: GameTestDriver): List<EntityId> = d.state.getBattlefield().filter {
        d.state.getEntity(it)?.has<TokenComponent>() == true
    }
    fun begin(sourceCopy: Boolean, observer: String = "none", separate: Boolean = false,
              riders: Boolean = false): GameTestDriver {
        fun sourceEffect(count: Int): Effect = CreateTokenCopyOfSourceEffect(
            count = count, exileAtStep = if (riders) Step.END else null,
        )
        val body = card("Paused Token Choice Body") {
            manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
            replacementEffect(EntersWithChoice(ChoiceType.COLOR))
            activatedAbility {
                cost = Costs.Free
                effect = if (separate) CompositeEffect(listOf(sourceEffect(1), sourceEffect(1)))
                         else sourceEffect(2)
            }
            if (observer == "coentrant") triggeredAbility {
                trigger = Triggers.OtherCreatureEnters; effect = Effects.GainLife(1)
            }
        }
        val watcher = card("Paused Token External Observer") {
            manaCost = "{0}"; typeLine = "Enchantment"
            triggeredAbility {
                trigger = if (observer == "group")
                    Triggers.OneOrMorePermanentsEnter(GameObjectFilter.Creature)
                    else Triggers.OtherCreatureEnters
                effect = Effects.GainLife(1)
            }
        }
        val d = GameTestDriver().apply {
            registerCards(TestCards.all + listOf(body, watcher))
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val original = d.putCreatureOnBattlefield(if (sourceCopy) d.player1 else d.player2, body.name)
        if (observer == "external" || observer == "group") d.putPermanentOnBattlefield(d.player1, watcher.name)
        if (sourceCopy) {
            d.submit(ActivateAbility(d.player1, original, body.activatedAbilities.single().id)).error shouldBe null
        } else {
            fun targetEffect(count: Int): Effect = CreateTokenCopyOfTargetEffect(
                target = EffectTarget.SpecificEntity(original), count = DynamicAmount.Fixed(count),
                exileAtStep = if (riders) Step.END else null,
            )
            val action = card("Paused Token Copy Action") {
                manaCost = "{0}"; typeLine = "Instant"
                spell {
                    effect = if (separate) CompositeEffect(listOf(targetEffect(1), targetEffect(1)))
                             else targetEffect(2)
                }
            }
            d.registerCards(listOf(action))
            d.castSpell(d.player1, d.putCardInHand(d.player1, action.name)).error shouldBe null
        }
        d.bothPass().error shouldBe null
        (d.pendingDecision is ChooseColorDecision) shouldBe true
        return d
    }
    fun color(d: GameTestDriver, serialized: Boolean) {
        roundTrip(d, serialized)
        val question = d.pendingDecision as ChooseColorDecision
        d.submitDecision(question.playerId, ColorChosenResponse(question.id, Color.BLUE)).error shouldBe null
    }
    fun finish(d: GameTestDriver, serialized: Boolean) {
        repeat(2) {
            (d.pendingDecision is ChooseColorDecision) shouldBe true
            color(d, serialized)
            if (d.state.hasPendingTriggerOrder()) {
                roundTrip(d, serialized)
                d.chooseTriggerOrderInListedOrder()
            }
        }
        d.pendingDecision shouldBe null
        d.state.continuationStack shouldBe emptyList()
        tokens(d).size shouldBe 2
        roundTrip(d, serialized)
    }
    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {
        val suffix = "sourceCopy=$sourceCopy, serialized=$serialized"
        test("PT02 entry triggers remain unstacked while later batch choices wait; $suffix") {
            val d = begin(sourceCopy, observer = "external")
            color(d, serialized)
            // Prospective assertion-oracle correction: a paused resolving instant remains
            // a spell on the stack, but no ETB trigger may be stacked before all choices.
            val stackObjects = d.state.stack.mapNotNull { d.state.getEntity(it) }
            stackObjects.count { it.has<TriggeredAbilityOnStackComponent>() } shouldBe 0
            tokens(d).size shouldBe 0
            if (sourceCopy) {
                d.stackSize shouldBe 0
            } else {
                d.stackSize shouldBe 1
                stackObjects.single().has<SpellOnStackComponent>() shouldBe true
                stackObjects.single().get<CardComponent>()?.name shouldBe "Paused Token Copy Action"
            }
            (d.pendingDecision is ChooseColorDecision) shouldBe true
            d.getLifeTotal(d.player1) shouldBe 20
            color(d, serialized)
            if (d.state.hasPendingTriggerOrder()) d.chooseTriggerOrderInListedOrder()
            d.pendingDecision shouldBe null
            d.stackSize shouldBe 2
            repeat(2) { d.bothPass().error shouldBe null }
            d.getLifeTotal(d.player1) shouldBe 22
        }
    }
})
