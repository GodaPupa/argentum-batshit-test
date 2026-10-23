package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Fixed mode composition: one printed riot and two independently granted instances. */
class PausedTokenRiotCompositionTest : FunSpec({
    val body = card("Pending Riot Body") {
        manaCost = "{0}"; typeLine = "Creature — Spider"; power = 2; toughness = 3
        riot()
    }
    val lord = card("Pending Riot Source") {
        manaCost = "{0}"; typeLine = "Enchantment"
        staticAbility { ability = GrantKeyword(Keyword.RIOT, GroupFilter(GameObjectFilter.Creature.youControl())) }
    }
    val doubler = card("Pending Riot Counter Doubler") {
        manaCost = "{0}"; typeLine = "Enchantment"
        replacementEffect(DoubleCounterPlacement())
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {
        test("PR01 printed and granted modes remain independent until one atomic entry; sourceCopy=$sourceCopy, serialized=$serialized") {
            val d = GameTestDriver().apply {
                registerCards(TestCards.all + listOf(body, lord, doubler))
                initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
                passPriorityUntil(Step.PRECOMBAT_MAIN)
            }
            val source = d.putCreatureOnBattlefield(d.player1, body.name)
            repeat(2) { d.putPermanentOnBattlefield(d.player1, lord.name) }
            d.putPermanentOnBattlefield(d.player1, doubler.name)
            fun tokens() = d.state.getBattlefield().filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
            fun restore() {
                if (!serialized) return
                val raw = json.encodeToString(GameState.serializer(), d.state)
                val restored = json.decodeFromString(GameState.serializer(), raw)
                json.encodeToString(GameState.serializer(), restored) shouldBe raw
                d.replaceState(restored)
            }
            val effect = if (sourceCopy) CreateTokenCopyOfSourceEffect(count = 1)
                else CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(source))
            val start = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, effect, EffectContext(source, d.player1))
            start.error shouldBe null
            d.replaceState(start.state)
            val published = mutableListOf<ZoneChangeEvent>()
            for (option in listOf(0, 0, 1)) {
                restore()
                (d.pendingDecision is ChooseOptionDecision) shouldBe true
                tokens().size shouldBe 0
                val question = d.pendingDecision as ChooseOptionDecision
                question.options shouldBe RIOT_MODE_OPTIONS.map { it.label }
                val result = d.submitDecision(question.playerId, OptionChosenResponse(question.id, option))
                result.error shouldBe null
                published += result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }
            }
            d.pendingDecision shouldBe null
            val token = tokens().single()
            d.state.getEntity(token)!!.chosenModeId() shouldBe RIOT_MODE_COUNTER
            d.state.getEntity(token)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 4
            d.state.projectedState.hasKeyword(token, Keyword.HASTE) shouldBe true
            published.map { it.entityId } shouldBe listOf(token)
            restore()
        }
    }
    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) for (double in listOf(false, true)) {
        test("PR02 copied general modifiers are not coentrant replacement providers; sourceCopy=$sourceCopy, serialized=$serialized, double=$double") {
            val modifier = card("Pending Copied Modifier $double") {
                manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
                replacementEffect(EntersWithChoice(ChoiceType.COLOR))
                replacementEffect(EntersWithCounters(count = 2, selfOnly = true))
                if (double) replacementEffect(DoubleCounterPlacement())
                else replacementEffect(ModifyCounterPlacement(modifier = 1))
            }
            val d = GameTestDriver().apply {
                registerCards(TestCards.all + modifier)
                initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
                passPriorityUntil(Step.PRECOMBAT_MAIN)
            }
            val source = d.putCreatureOnBattlefield(d.player1, modifier.name)
            val effect = if (sourceCopy) CreateTokenCopyOfSourceEffect(count = 2)
                else CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(source), count = com.wingedsheep.sdk.scripting.values.DynamicAmount.Fixed(2))
            val start = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, effect, EffectContext(source, d.player1))
            start.error shouldBe null
            d.replaceState(start.state)
            repeat(2) {
                if (serialized) {
                    val raw = json.encodeToString(GameState.serializer(), d.state)
                    val restored = json.decodeFromString(GameState.serializer(), raw)
                    json.encodeToString(GameState.serializer(), restored) shouldBe raw
                    d.replaceState(restored)
                }
                (d.pendingDecision is ChooseColorDecision) shouldBe true
                val question = d.pendingDecision as ChooseColorDecision
                d.submitDecision(question.playerId, ColorChosenResponse(question.id, Color.BLUE)).error shouldBe null
            }
            d.pendingDecision shouldBe null
            val tokens = d.state.getBattlefield().filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
            tokens.size shouldBe 2
            for (id in tokens) {
                d.state.getEntity(id)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe if (double) 4 else 3
                d.state.getEntity(id)!!.chosenColor() shouldBe Color.BLUE
            }
        }
    }
})
