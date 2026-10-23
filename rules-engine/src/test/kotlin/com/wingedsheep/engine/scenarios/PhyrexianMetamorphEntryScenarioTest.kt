package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.*
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.nph.cards.PhyrexianMetamorph
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

/** Additional exact-card entry rulings; the original ten-case bank remains unchanged. */
class PhyrexianMetamorphEntryScenarioTest : FunSpec({
    val body = card("Metamorph Entry Choice Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        replacementEffect(EntersWithCounters(count = 2, selfOnly = true))
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(3) }
    }
    val plain = card("Metamorph Entry Plain") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        keywords(Keyword.HEXPROOF)
    }
    val coentrant = card("Metamorph Entry Coentrant") {
        manaCost = "{0}"; typeLine = "Artifact"
    }
    val blueprint = card("Metamorph Intrinsic Blueprint") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        staticAbility { ability = ModifyStats(1, 1, GroupFilter.OtherCreaturesYouControl) }
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(3) }
        activatedAbility { cost = Costs.Free; effect = Effects.GainLife(2) }
    }
    val secret = card("Metamorph Secret Face") {
        manaCost = "{6}"; typeLine = "Creature — Dragon"; power = 6; toughness = 7
        keywordAbility(KeywordAbility.ward("{4}"))
    }
    val xBody = card("Metamorph X Body") {
        manaCost = "{X}{2}"; typeLine = "Creature — Hydra"; power = 2; toughness = 3
        replacementEffect(EntersWithDynamicCounters(count = DynamicAmount.XValue))
    }
    fun entry(count: Int) = card("Metamorph Entry Spell $count") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(count)))
            move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
        } }
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(PhyrexianMetamorph, body, plain, coentrant,
            blueprint, secret, xBody, entry(1), entry(2)))
        initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val state = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), state) shouldBe raw
        d.replaceState(state)
    }
    fun component(d: GameTestDriver, id: EntityId) = d.state.getEntity(id).shouldNotBeNull()
        .get<CardComponent>().shouldNotBeNull()
    fun cast(d: GameTestDriver): EntityId {
        val id = d.putCardInHand(d.player1, PhyrexianMetamorph.name)
        d.giveMana(d.player1, Color.BLUE, 4)
        d.castSpell(d.player1, id).error shouldBe null
        d.bothPass().error shouldBe null
        return id
    }
    fun select(d: GameTestDriver, id: EntityId?) {
        val decision = (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        decision.minSelections shouldBe 0
        decision.useTargetingUI shouldBe true
        if (id != null) decision.options.contains(id) shouldBe true
        d.submitCardSelection(d.player1, listOfNotNull(id)).error shouldBe null
    }
    for (direct in listOf(false, true)) {
        test("Metamorph chooses copied color afresh before one entry and one trigger; direct=$direct") {
            val d = driver()
            val source = d.putCreatureOnBattlefield(d.player2, body.name)
            d.replaceState(d.state.updateEntity(source) {
                it.withCastChoice(ChoiceSlot.COLOR, ChoiceValue.ColorChoice(Color.RED))
            })
            val id = if (direct) {
                val card = d.putCardOnTopOfLibrary(d.player1, PhyrexianMetamorph.name)
                val spell = d.putCardInHand(d.player1, entry(1).name)
                d.castSpell(d.player1, spell).error shouldBe null
                d.bothPass().error shouldBe null
                card
            } else cast(d)
            val oldObject = d.state.objectRef(id)
            restore(d)
            select(d, source)
            d.state.getBattlefield().contains(id) shouldBe false
            d.state.objectRef(id) shouldBe oldObject
            d.state.getEntity(id).shouldNotBeNull().get<BattlefieldEntryTimestampComponent>() shouldBe null
            restore(d)
            val color = (d.pendingDecision as? ChooseColorDecision).shouldNotBeNull()
            d.submitDecision(color.playerId, ColorChosenResponse(color.id, Color.BLUE)).error shouldBe null
            d.pendingDecision shouldBe null
            d.state.getEntity(id).shouldNotBeNull().chosenColor() shouldBe Color.BLUE
            d.state.getEntity(source).shouldNotBeNull().chosenColor() shouldBe Color.RED
            d.state.getEntity(id).shouldNotBeNull().get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            component(d, id).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT, CardType.CREATURE)
            d.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == id && it.toZone == Zone.BATTLEFIELD } shouldBe 1
            d.stackSize shouldBe 1
            d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 23
            d.stackSize shouldBe 0
        }
    }
    for (copyFirst in listOf(false, true)) {
        test("Metamorph cannot select a simultaneous artifact coentrant; copyFirst=$copyFirst") {
            val d = driver()
            val existing = d.putCreatureOnBattlefield(d.player2, plain.name)
            val order = if (copyFirst) listOf(PhyrexianMetamorph.name, coentrant.name)
                else listOf(coentrant.name, PhyrexianMetamorph.name)
            val ids = order.reversed().associateWith { d.putCardOnTopOfLibrary(d.player1, it) }
            val copy = ids.getValue(PhyrexianMetamorph.name)
            val other = ids.getValue(coentrant.name)
            val spell = d.putCardInHand(d.player1, entry(2).name)
            d.castSpell(d.player1, spell).error shouldBe null
            d.bothPass().error shouldBe null
            val decision = (d.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
            decision.options.contains(other) shouldBe false
            decision.options.contains(existing) shouldBe true
            d.state.getBattlefield().contains(copy) shouldBe false
            d.state.getBattlefield().contains(other) shouldBe false
            restore(d)
            select(d, existing)
            d.pendingDecision shouldBe null
            component(d, copy).name shouldBe plain.name
            component(d, copy).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT, CardType.CREATURE)
            d.state.getBattlefield().contains(other) shouldBe true
            d.state.projectedState.hasKeyword(copy, Keyword.HEXPROOF) shouldBe true
        }
    }
    test("Metamorph owns intrinsic token abilities after the original leaves") {
        val d = driver()
        val ally = d.putCreatureOnBattlefield(d.player1, plain.name)
        val made = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            CreateTokenEffect(power = 2, toughness = 3, colors = setOf(Color.BLUE), creatureTypes = setOf("Bear"),
                staticAbilities = blueprint.script.staticAbilities, triggeredAbilities = blueprint.triggeredAbilities,
                activatedAbilities = blueprint.activatedAbilities, tapped = true, initialCounters = mapOf("+1/+1" to 3)),
            EffectContext(sourceId = null, controllerId = d.player2))
        made.error shouldBe null
        d.replaceState(made.state)
        val token = made.events.filterIsInstance<ZoneChangeEvent>().single { it.toZone == Zone.BATTLEFIELD }.entityId
        val id = cast(d)
        select(d, token)
        component(d, id).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT, CardType.CREATURE)
        d.state.getEntity(id).shouldNotBeNull().has<TokenComponent>() shouldBe false
        d.state.getEntity(id).shouldNotBeNull().has<TappedComponent>() shouldBe false
        (d.state.getEntity(id).shouldNotBeNull().get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
        d.state.projectedState.getPower(id) shouldBe 2
        d.state.projectedState.getPower(ally) shouldBe 3
        d.stackSize shouldBe 1
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 23
        val moved = EngineServices(d.cardRegistry).effectExecutorRegistry.execute(d.state,
            MoveToZoneEffect(EffectTarget.SpecificEntity(token), Zone.EXILE), EffectContext(sourceId = null, controllerId = d.player2))
        moved.error shouldBe null
        d.replaceState(moved.state)
        restore(d)
        d.submit(ActivateAbility(d.player1, id, blueprint.activatedAbilities.single().id)).error shouldBe null
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 25
        d.state.projectedState.getPower(ally) shouldBe 3
    }
    test("Metamorph copies public cloaked values with ward two as a face-up artifact creature") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, secret.name)
        d.replaceState(d.state.updateEntity(source) { it.with(FaceDownComponent).with(FaceDownModeComponent(FaceDownMode.CLOAK)) })
        val id = cast(d)
        restore(d)
        select(d, source)
        val copied = component(d, id)
        copied.name shouldBe ""
        copied.manaCost shouldBe ManaCost.ZERO
        copied.typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT, CardType.CREATURE)
        copied.copiableFaceDownWard shouldBe WardCost.Mana("{2}")
        d.state.getEntity(id).shouldNotBeNull().has<FaceDownComponent>() shouldBe false
        d.state.projectedState.getPower(id) shouldBe 2
        d.state.projectedState.getToughness(id) shouldBe 2
        d.state.projectedState.hasKeyword(id, Keyword.WARD) shouldBe true
    }
    test("Metamorph retains copied X symbols but neither prior counters nor an X payment") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player2, xBody.name)
        d.replaceState(d.state.updateEntity(source) {
            it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 5))
                .with(CastChoicesComponent(x = 5))
        })
        val id = cast(d)
        select(d, source)
        component(d, id).manaCost shouldBe ManaCost.parse("{X}{2}")
        component(d, id).manaValue shouldBe 2
        (d.state.getEntity(id).shouldNotBeNull().get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
        d.state.projectedState.getPower(id) shouldBe 2
        component(d, id).typeLine.cardTypes shouldBe setOf(CardType.ARTIFACT, CardType.CREATURE)
    }
    test("declining Metamorph's copy can survive under an existing anthem") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, blueprint.name)
        val id = cast(d)
        select(d, null)
        component(d, id).name shouldBe PhyrexianMetamorph.name
        component(d, id).baseStats shouldBe PhyrexianMetamorph.creatureStats
        d.state.getBattlefield().contains(id) shouldBe true
        d.state.projectedState.getPower(id) shouldBe 1
        d.state.projectedState.getToughness(id) shouldBe 1
        d.stackSize shouldBe 0
    }
})
