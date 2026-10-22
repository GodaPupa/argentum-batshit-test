package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.event.GrantedActivatedAbility
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Copiable characteristics are creation/printing/copy facts, never later grants or hidden faces. */
class CopiableEntryValueScenarioTest : FunSpec({
    val plain = card("Copy Value Plain") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
    }
    val secret = card("Copy Value Hidden Identity") {
        manaCost = "{4}{G}{G}"; typeLine = "Creature — Wurm"; power = 6; toughness = 7
        keywords(Keyword.TRAMPLE)
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(9) }
    }
    val blueprint = card("Copy Value Token Blueprint") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        staticAbility { ability = ModifyStats(1, 1, GroupFilter.OtherCreaturesYouControl) }
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(3) }
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(2) }
    }
    val external = card("Copy Value External Grant") {
        manaCost = "{0}"; typeLine = "Creature — Human"; power = 1; toughness = 1
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(7) }
    }
    val xBody = card("Copy Value X Body") {
        manaCost = "{X}{2}"; typeLine = "Artifact Creature — Construct"; power = 2; toughness = 3
    }
    val copier = card("Copy Value Copier") {
        manaCost = "{0}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy())
    }
    val entry = card("Copy Value Direct Entry") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
            move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
        } }
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(plain, secret, blueprint, external, xBody, copier, entry))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val encoded = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), encoded)
        json.encodeToString(GameState.serializer(), restored) shouldBe encoded
        d.replaceState(restored)
    }
    fun component(d: GameTestDriver, id: EntityId) = d.state.getEntity(id).shouldNotBeNull()
        .get<CardComponent>().shouldNotBeNull()
    fun copy(d: GameTestDriver, target: EntityId, direct: Boolean, serialized: Boolean): EntityId {
        val entrant = if (direct) d.putCardOnTopOfLibrary(d.player1, copier.name)
            else d.putCardInHand(d.player1, copier.name)
        val spell = if (direct) d.putCardInHand(d.player1, entry.name) else entrant
        d.castSpell(d.player1, spell).error shouldBe null
        d.bothPass().error shouldBe null
        val question = (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        question.options.contains(target) shouldBe true
        d.state.getBattlefield().contains(entrant) shouldBe false
        if (serialized) restore(d)
        d.submitCardSelection(d.player1, listOf(target)).error shouldBe null
        d.pendingDecision shouldBe null
        d.state.getBattlefield().contains(entrant) shouldBe true
        return entrant
    }
    fun externalGrant(d: GameTestDriver, target: EntityId) {
        d.replaceState(d.state.copy(grantedActivatedAbilities = d.state.grantedActivatedAbilities +
            GrantedActivatedAbility(target, external.activatedAbilities.single(), Duration.Permanent)))
    }
    for (direct in listOf(false, true)) for (serialized in listOf(false, true)) {
        test("entry copies intrinsic token abilities but not later grants or token status; direct=$direct, serialized=$serialized") {
            val d = fixture()
            val ally = d.putCreatureOnBattlefield(d.player1, plain.name)
            val created = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                CreateTokenEffect(power = 2, toughness = 3, colors = setOf(Color.BLUE), creatureTypes = setOf("Bear"),
                    name = "Copy Value Intrinsic Token", keywords = setOf(Keyword.FLYING), tapped = true,
                    initialCounters = mapOf("+1/+1" to 3), staticAbilities = blueprint.script.staticAbilities,
                    triggeredAbilities = blueprint.triggeredAbilities, activatedAbilities = blueprint.activatedAbilities),
                EffectContext(sourceId = null, controllerId = d.player2))
            created.isSuccess shouldBe true
            val token = created.events.filterIsInstance<ZoneChangeEvent>().single { it.toZone == Zone.BATTLEFIELD }.entityId
            d.replaceState(created.state)
            externalGrant(d, token)
            val boosted = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                Effects.ModifyStats(4, 4, EffectTarget.SpecificEntity(token)),
                EffectContext(sourceId = null, controllerId = d.player2))
            boosted.isSuccess shouldBe true
            d.replaceState(boosted.state)
            d.state.projectedState.getPower(token) shouldBe 9
            val first = copy(d, token, direct, serialized)
            d.state.projectedState.getPower(ally) shouldBe 3
            d.state.projectedState.getPower(first) shouldBe 2
            d.state.projectedState.hasKeyword(first, Keyword.FLYING) shouldBe true
            d.state.getEntity(first).shouldNotBeNull().has<TokenComponent>() shouldBe false
            d.state.getEntity(first).shouldNotBeNull().has<TappedComponent>() shouldBe false
            (d.state.getEntity(first).shouldNotBeNull().get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            d.stackSize shouldBe 1
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 23
            d.submit(ActivateAbility(d.player1, first, blueprint.activatedAbilities.single().id)).error shouldBe null
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 25
            d.submit(ActivateAbility(d.player1, first, external.activatedAbilities.single().id)).error.shouldNotBeNull()
            d.getLifeTotal(d.player1) shouldBe 25
            // The first copy must own its copied characteristics after the original token is gone.
            val departed = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                MoveToZoneEffect(EffectTarget.SpecificEntity(token), Zone.EXILE),
                EffectContext(sourceId = null, controllerId = d.player2))
            departed.isSuccess shouldBe true
            d.replaceState(departed.state)
            val second = copy(d, first, direct = false, serialized = true)
            d.state.projectedState.getPower(ally) shouldBe 4
            d.stackSize shouldBe 1
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 28
            d.submit(ActivateAbility(d.player1, second, blueprint.activatedAbilities.single().id)).error shouldBe null
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 30
            val returned = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
                Effects.ReturnToHand(EffectTarget.SpecificEntity(first)),
                EffectContext(sourceId = null, controllerId = d.player1))
            returned.isSuccess shouldBe true
            d.replaceState(returned.state)
            component(d, first).name shouldBe copier.name
            // Departure grants can remain for leaves-trigger LKI; a later entry must clear them.
            d.state.projectedState.getPower(ally) shouldBe 3
            d.castSpell(d.player1, first).error shouldBe null
            d.bothPass().error shouldBe null
            val decline = (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
            decline.minSelections shouldBe 0
            d.submitCardSelection(d.player1, emptyList()).error shouldBe null
            d.pendingDecision shouldBe null
            d.stackSize shouldBe 0
            d.state.projectedState.getPower(first) shouldBe 0
            d.state.grantedActivatedAbilities.any { it.entityId == first } shouldBe false
            d.state.grantedTriggeredAbilities.any { it.entityId == first } shouldBe false
            d.state.grantedStaticAbilities.any { it.entityId == first } shouldBe false
            d.state.projectedState.getPower(ally) shouldBe 3
        }
        test("entry copies the face-down values as a face-up nameless creature; direct=$direct, serialized=$serialized") {
            val d = fixture()
            val hidden = d.putCreatureOnBattlefield(d.player2, secret.name)
            d.replaceState(d.state.updateEntity(hidden) { it.with(FaceDownComponent) })
            d.state.projectedState.getPower(hidden) shouldBe 2
            val entrant = copy(d, hidden, direct, serialized)
            d.state.projectedState.getPower(entrant) shouldBe 2
            d.state.projectedState.getToughness(entrant) shouldBe 2
            component(d, entrant).name shouldBe ""
            component(d, entrant).manaValue shouldBe 0
            component(d, entrant).manaCost shouldBe ManaCost.ZERO
            component(d, entrant).typeLine.cardTypes shouldBe setOf(CardType.CREATURE)
            component(d, entrant).typeLine.subtypes shouldBe emptySet()
            d.state.projectedState.getColors(entrant) shouldBe emptySet()
            d.state.projectedState.hasKeyword(entrant, Keyword.TRAMPLE) shouldBe false
            d.state.getEntity(entrant).shouldNotBeNull().has<FaceDownComponent>() shouldBe false
            d.stackSize shouldBe 0
            d.submit(ActivateAbility(d.player1, entrant, secret.activatedAbilities.single().id)).error.shouldNotBeNull()
            val event = d.events.filterIsInstance<ZoneChangeEvent>().single { it.entityId == entrant && it.toZone == Zone.BATTLEFIELD }
            Json.encodeToString(GameEvent.serializer(), event).contains(secret.name) shouldBe false
        }
    }
    test("ordinary printed copy excludes external grants counters and tapped status") {
        val d = fixture()
        val target = d.putCreatureOnBattlefield(d.player2, plain.name)
        d.replaceState(d.state.updateEntity(target) {
            it.with(TappedComponent).with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 3))
        })
        externalGrant(d, target)
        val entrant = copy(d, target, direct = false, serialized = true)
        d.state.projectedState.getPower(entrant) shouldBe 2
        d.state.getEntity(entrant).shouldNotBeNull().has<TappedComponent>() shouldBe false
        (d.state.getEntity(entrant).shouldNotBeNull().get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
        d.submit(ActivateAbility(d.player1, entrant, external.activatedAbilities.single().id)).error.shouldNotBeNull()
    }
    test("copy retains the X symbol while X contributes zero to battlefield mana value") {
        val d = fixture()
        val target = d.putCreatureOnBattlefield(d.player2, xBody.name)
        val entrant = copy(d, target, direct = true, serialized = true)
        component(d, entrant).manaCost.hasX shouldBe true
        component(d, entrant).manaValue shouldBe 2
        d.state.projectedState.getPower(entrant) shouldBe 2
        component(d, entrant).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT, CardType.CREATURE)
    }
})
