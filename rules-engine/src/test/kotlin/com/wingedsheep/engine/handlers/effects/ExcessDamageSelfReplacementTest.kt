package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.DamageDealtByPlayersThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.DamageDealtThisTurnComponent
import com.wingedsheep.engine.state.components.identity.ProtectionComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.DoubleDamage
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.PreventDamageEffect
import com.wingedsheep.sdk.scripting.events.SourceFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** CR 120.4a / 614.15: excess is split before ordinary replacement/prevention, once per recipient. */
class ExcessDamageSelfReplacementTest : FunSpec({
    val source = card("Excess Fixture Source") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Beast"
        power = 5
        toughness = 5
        keywords(Keyword.TRAMPLE, Keyword.LIFELINK)
    }
    val deathtouch = card("Excess Fixture Deathtouch") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Beast"
        power = 5
        toughness = 5
        keywords(Keyword.TRAMPLE, Keyword.LIFELINK, Keyword.DEATHTOUCH)
    }
    val wither = card("Excess Fixture Wither") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Beast"
        power = 5
        toughness = 5
        keywords(Keyword.TRAMPLE, Keyword.LIFELINK, Keyword.WITHER)
    }
    val doubler = card("Excess Fixture Doubler") {
        manaCost = "{3}{R}"
        typeLine = "Enchantment"
        replacementEffect(DoubleDamage(appliesTo = EventPattern.DamageEvent(
            source = SourceFilter.Matching(GameObjectFilter.Creature.youControl()))))
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(source, deathtouch, wither, doubler))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    for ((label, sourceName, priorDamage, expectedCreature, expectedPlayer) in listOf(
        listOf("ordinary", source.name, "0", "2", "3"),
        listOf("prior damage", source.name, "1", "1", "4"),
        listOf("deathtouch", deathtouch.name, "0", "1", "4"),
        listOf("wither", wither.name, "0", "2", "3"))) {
        test("excess damage conserves event amounts, source tracking and lifelink: $label") {
            val d = fixture()
            val creature = d.putCreatureOnBattlefield(d.player1, sourceName)
            val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
            if (priorDamage.toInt() > 0) d.replaceState(d.state.updateEntity(victim) {
                it.with(DamageComponent(amount = priorDamage.toInt()))
            })
            val before = d.state
            val result = DamageUtils.dealDamageToTarget(before, victim, 5, creature, excessToController = true)
            result.isSuccess shouldBe true
            val damage = result.events.filterIsInstance<DamageDealtEvent>()
            damage.map { it.targetId to it.amount } shouldBe listOf(
                victim to expectedCreature.toInt(), d.player2 to expectedPlayer.toInt())
            damage.all { it.sourceId == creature && !it.isCombatDamage } shouldBe true
            result.state.getEntity(creature)!!.get<DamageDealtThisTurnComponent>()!!.amount shouldBe 5
            result.state.getEntity(victim)!!.get<DamageDealtByPlayersThisTurnComponent>()!!.perPlayer[d.player1] shouldBe expectedCreature.toInt()
            if (label == "wither") {
                result.state.getEntity(victim)!!.get<CountersComponent>()!!.getCount(CounterType.MINUS_ONE_MINUS_ONE) shouldBe 2
            } else {
                result.state.getEntity(victim)!!.get<DamageComponent>()!!.amount shouldBe priorDamage.toInt() + expectedCreature.toInt()
            }
            d.replaceState(result.state)
            d.getLifeTotal(d.player1) shouldBe 25
            d.getLifeTotal(d.player2) shouldBe 20 - expectedPlayer.toInt()
            before.getEntity(creature)!!.get<DamageDealtThisTurnComponent>() shouldBe null
        }
    }
    test("without the excess clause all five damage stays on the creature exactly once") {
        val d = fixture()
        val creature = d.putCreatureOnBattlefield(d.player1, source.name)
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val result = DamageUtils.dealDamageToTarget(d.state, victim, 5, creature)
        result.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe listOf(victim to 5)
        d.replaceState(result.state)
        d.getLifeTotal(d.player1) shouldBe 25
        d.getLifeTotal(d.player2) shouldBe 20
    }
    test("protection from creatures prevents the creature portion without erasing controller excess") {
        val d = fixture()
        val creature = d.putCreatureOnBattlefield(d.player1, source.name)
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.replaceState(d.state.updateEntity(victim) {
            it.with(ProtectionComponent(colors = emptySet(), cardTypes = setOf("CREATURE")))
        })
        val result = DamageUtils.dealDamageToTarget(d.state, victim, 5, creature, excessToController = true)
        result.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe listOf(d.player2 to 3)
        result.state.getEntity(victim)!!.get<DamageComponent>() shouldBe null
        result.state.getEntity(creature)!!.get<DamageDealtThisTurnComponent>()!!.amount shouldBe 3
        d.replaceState(result.state)
        d.getLifeTotal(d.player1) shouldBe 23
        d.getLifeTotal(d.player2) shouldBe 17
    }
    test("a finite recipient shield reduces its portion without increasing the already split excess") {
        val d = fixture()
        val creature = d.putCreatureOnBattlefield(d.player1, source.name)
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val services = EngineServices(d.cardRegistry)
        val shield = services.effectExecutorRegistry.execute(d.state,
            PreventDamageEffect(target = EffectTarget.SpecificEntity(victim), amount = DynamicAmount.Fixed(1)),
            EffectContext(sourceId = null, controllerId = d.player2))
        val result = DamageUtils.dealDamageToTarget(shield.state, victim, 5, creature, excessToController = true)
        result.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe
            listOf(victim to 1, d.player2 to 3)
        d.replaceState(result.state)
        d.getLifeTotal(d.player1) shouldBe 24
        d.getLifeTotal(d.player2) shouldBe 17
    }
    test("ordinary source doubling applies once to each already split portion") {
        val d = fixture()
        val creature = d.putCreatureOnBattlefield(d.player1, source.name)
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putPermanentOnBattlefield(d.player1, doubler.name)
        val result = DamageUtils.dealDamageToTarget(d.state, victim, 5, creature, excessToController = true)
        result.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe
            listOf(victim to 4, d.player2 to 6)
        result.state.getEntity(creature)!!.get<DamageDealtThisTurnComponent>()!!.amount shouldBe 10
        d.replaceState(result.state)
        d.getLifeTotal(d.player1) shouldBe 30
        d.getLifeTotal(d.player2) shouldBe 14
    }
})
