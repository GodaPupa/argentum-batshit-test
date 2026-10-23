package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.TokenComponent
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
class PausedTokenInstructionScenarioTest : FunSpec({
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
        test("PT01 no batch member enters before every as-enters choice; $suffix") {
            val d = begin(sourceCopy)
            roundTrip(d, serialized)
            tokens(d).size shouldBe 0 // PT01 first baseline mismatch
            color(d, serialized)
            (d.pendingDecision is ChooseColorDecision) shouldBe true
            tokens(d).size shouldBe 0
            color(d, serialized)
            d.pendingDecision shouldBe null
            tokens(d).size shouldBe 2
        }
        test("PT02 entry triggers remain unstacked while later batch choices wait; $suffix") {
            val d = begin(sourceCopy, observer = "external")
            color(d, serialized)
            d.stackSize shouldBe 0 // PT02 first baseline mismatch
            (d.pendingDecision is ChooseColorDecision) shouldBe true
            d.getLifeTotal(d.player1) shouldBe 20
            color(d, serialized)
            if (d.state.hasPendingTriggerOrder()) d.chooseTriggerOrderInListedOrder()
            d.pendingDecision shouldBe null
            d.stackSize shouldBe 2
            repeat(2) { d.bothPass().error shouldBe null }
            d.getLifeTotal(d.player1) shouldBe 22
        }
        test("PT03 copied coentrant observers see both simultaneous arrivals; $suffix") {
            val d = begin(sourceCopy, observer = "coentrant")
            finish(d, serialized)
            val expected = if (sourceCopy) 4 else 2
            d.stackSize shouldBe expected // PT03 first baseline mismatch
            repeat(expected) { d.bothPass().error shouldBe null }
            d.getLifeTotal(d.player1) shouldBe 20 + expected
        }
        test("PT04 one-or-more observer sees one paused token instruction; $suffix") {
            val d = begin(sourceCopy, observer = "group")
            finish(d, serialized)
            d.stackSize shouldBe 1 // PT04 first baseline mismatch
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 21
        }
        test("PT05 separate copy instructions retain separate entry groups; $suffix") {
            val d = begin(sourceCopy, observer = "group", separate = true)
            finish(d, serialized)
            d.stackSize shouldBe 2
            repeat(2) { d.bothPass().error shouldBe null }
            d.getLifeTotal(d.player1) shouldBe 22
        }
        test("PT06 every paused copy retains its delayed exile rider; $suffix") {
            val d = begin(sourceCopy, riders = true)
            finish(d, serialized)
            d.state.delayedTriggers.size shouldBe 2 // PT06 source-copy baseline mismatch
            d.state.delayedTriggers.map { it.fireAtStep }.toSet() shouldBe setOf(Step.END)
            val targetIds = d.state.delayedTriggers.map {
                ((it.effect as MoveToZoneEffect).target as EffectTarget.SpecificEntity).entityId
            }
            targetIds.toSet() shouldBe tokens(d).toSet()
            targetIds.size shouldBe targetIds.toSet().size
        }
    }
})
