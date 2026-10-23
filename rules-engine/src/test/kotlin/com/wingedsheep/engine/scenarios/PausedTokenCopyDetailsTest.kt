package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Additional fixed copy-entry controls; the original twenty-four cases remain byte exact. */
class PausedTokenCopyDetailsTest : FunSpec({
    val plain = card("Pending Token Host") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        keywords(Keyword.HEXPROOF, Keyword.SHROUD)
    }
    val aura = card("Pending Token Choice Aura") {
        manaCost = "{0}"; typeLine = "Enchantment — Aura"
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
    }.let { it.copy(script = it.script.copy(auraTarget = TargetCreature())) }
    val secret = card("Pending Token Concealed Body") {
        manaCost = "{7}"; typeLine = "Creature — Dragon"; power = 7; toughness = 8
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        keywordAbility(KeywordAbility.ward("{4}"))
    }
    val blueprint = card("Pending Token Intrinsic Blueprint") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        staticAbility { ability = ModifyStats(1, 1, GroupFilter.OtherCreaturesYouControl) }
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(3) }
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(2) }
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(plain, aura, secret, blueprint))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver, serialized: Boolean) {
        if (!serialized) return
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }
    fun tokens(d: GameTestDriver) = d.state.getBattlefield().filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
    fun copy(d: GameTestDriver, source: EntityId, sourceCopy: Boolean, count: Int = 2): EffectResult {
        val effect = if (sourceCopy) CreateTokenCopyOfSourceEffect(count = count)
            else CreateTokenCopyOfTargetEffect(EffectTarget.SpecificEntity(source), count = DynamicAmount.Fixed(count))
        return EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state, effect,
            EffectContext(sourceId = source, controllerId = d.player1)).also { d.replaceState(it.state) }
    }
    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {
        val suffix = "sourceCopy=$sourceCopy, serialized=$serialized"
        test("PX01 Aura hosts and printed choices finish before either copy enters; $suffix") {
            val d = driver()
            val host = d.putCreatureOnBattlefield(d.player2, plain.name)
            val original = d.putPermanentOnBattlefield(d.player1, aura.name)
            d.replaceState(d.state.updateEntity(original) { it.with(AttachedToComponent(host)) }
                .updateEntity(host) { it.with(AttachmentsComponent(listOf(original))) })
            copy(d, original, sourceCopy).error shouldBe null
            val published = mutableListOf<EntityId>()
            repeat(2) { index ->
                restore(d, serialized)
                (d.pendingDecision is ChooseTargetsDecision) shouldBe true
                val hostQuestion = d.pendingDecision as ChooseTargetsDecision
                hostQuestion.legalTargets[0]!!.contains(host) shouldBe true
                tokens(d).size shouldBe 0
                d.submitDecision(hostQuestion.playerId,
                    TargetsResponse(hostQuestion.id, mapOf(0 to listOf(host)))).error shouldBe null
                (d.pendingDecision is ChooseColorDecision) shouldBe true
                tokens(d).size shouldBe 0
                restore(d, serialized)
                val color = d.pendingDecision as ChooseColorDecision
                val result = d.submitDecision(color.playerId, ColorChosenResponse(color.id, Color.BLUE))
                result.error shouldBe null
                published += result.events.filterIsInstance<ZoneChangeEvent>().filter { it.toZone == Zone.BATTLEFIELD }.map { it.entityId }
                tokens(d).size shouldBe if (index == 0) 0 else 2
            }
            d.pendingDecision shouldBe null
            published shouldBe tokens(d)
            tokens(d).forEach { id ->
                d.state.getEntity(id)!!.get<AttachedToComponent>()?.targetId shouldBe host
                d.state.getEntity(id)!!.chosenColor() shouldBe Color.BLUE
            }
            d.state.getEntity(host)!!.get<AttachmentsComponent>()!!.attachedIds.toSet() shouldBe (tokens(d) + original).toSet()
            d.events.filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        }
        test("PX02 face-down source copies public values without concealed entry choices; $suffix") {
            val d = driver()
            val original = d.putCreatureOnBattlefield(d.player1, secret.name)
            d.replaceState(d.state.updateEntity(original) {
                it.with(FaceDownComponent).with(FaceDownModeComponent(FaceDownMode.CLOAK))
            })
            restore(d, serialized)
            copy(d, original, sourceCopy).isSuccess shouldBe true
            d.pendingDecision shouldBe null
            tokens(d).size shouldBe 2
            for (id in tokens(d)) {
                val entity = d.state.getEntity(id)!!
                val card = entity.get<CardComponent>()!!
                card.name shouldBe ""
                card.manaCost shouldBe ManaCost.ZERO
                card.copiableFaceDownWard shouldBe WardCost.Mana("{2}")
                entity.has<FaceDownComponent>() shouldBe false
                d.state.projectedState.getPower(id) shouldBe 2
                d.state.projectedState.getToughness(id) shouldBe 2
                d.state.projectedState.hasKeyword(id, Keyword.WARD) shouldBe true
            }
            restore(d, serialized)
        }
        test("PX03 copied intrinsic token abilities survive removal of their original; $suffix") {
            val d = driver()
            val ally = d.putCreatureOnBattlefield(d.player1, plain.name)
            val made = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                CreateTokenEffect(power = 2, toughness = 3, colors = setOf(Color.BLUE), creatureTypes = setOf("Bear"),
                    staticAbilities = blueprint.script.staticAbilities, triggeredAbilities = blueprint.triggeredAbilities,
                    activatedAbilities = blueprint.activatedAbilities, tapped = true, initialCounters = mapOf("+1/+1" to 3)),
                EffectContext(sourceId = null, controllerId = d.player1))
            made.error shouldBe null
            d.replaceState(made.state)
            val original = tokens(d).single()
            restore(d, serialized)
            val copied = copy(d, original, sourceCopy, count = 1)
            copied.isSuccess shouldBe true
            val id = tokens(d).single { it != original }
            d.state.grantedActivatedAbilities.count { it.entityId == id } shouldBe 1
            d.state.grantedTriggeredAbilities.count { it.entityId == id } shouldBe 1
            d.state.grantedStaticAbilities.count { it.entityId == id } shouldBe 1
            copied.events.filterIsInstance<ZoneChangeEvent>().flatMap { it.observedEntryTriggers.orEmpty() }.size shouldBe 1
            d.state.getEntity(id)!!.has<TappedComponent>() shouldBe false
            (d.state.getEntity(id)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            val moved = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                MoveToZoneEffect(EffectTarget.SpecificEntity(original), Zone.EXILE),
                EffectContext(sourceId = null, controllerId = d.player1))
            moved.error shouldBe null
            d.replaceState(moved.state)
            restore(d, serialized)
            d.state.projectedState.getPower(ally) shouldBe 3
            d.submit(ActivateAbility(d.player1, id, blueprint.activatedAbilities.single().id)).error shouldBe null
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 22
            d.state.projectedState.getPower(ally) shouldBe 3
        }
    }
})
