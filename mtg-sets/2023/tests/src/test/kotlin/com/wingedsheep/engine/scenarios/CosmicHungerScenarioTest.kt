package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.battlefield.ProtectorComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mom.cards.CosmicHunger
import com.wingedsheep.mtg.sets.definitions.mom.cards.InvasionOfInnistrad
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CosmicHungerScenarioTest : FunSpec({
    val sourceCard = card("Cosmic Fixture Source") {
        manaCost = "{G}"
        typeLine = "Creature — Beast"
        power = 2
        toughness = 3
        keywords(Keyword.LIFELINK)
    }
    val sturdy = card("Cosmic Fixture Creature") {
        manaCost = "{3}{G}"
        typeLine = "Creature — Beast"
        power = 3
        toughness = 9
    }
    val walker = card("Cosmic Fixture Walker") {
        manaCost = "{4}{R}"
        typeLine = "Planeswalker — Test"
        startingLoyalty = 8
    }
    fun fixture(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + listOf(CosmicHunger, InvasionOfInnistrad, sourceCard, sturdy, walker))
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    for (kind in listOf("creature", "planeswalker", "battle")) {
        for (ownVictim in listOf(true, false)) {
            test("controlled creature deals real damage to $kind; friendly victim allowed=$ownVictim") {
                val d = fixture()
                val me = d.activePlayer!!
                val enemy = d.getOpponent(me)
                val owner = if (ownVictim) me else enemy
                val source = d.putCreatureOnBattlefield(me, sourceCard.name)
                val target = when (kind) {
                    "creature" -> d.putCreatureOnBattlefield(owner, sturdy.name)
                    "planeswalker" -> d.putPermanentOnBattlefield(owner, walker.name).also {
                        d.addComponent(it, CountersComponent(mapOf(CounterType.LOYALTY to 8)))
                    }
                    else -> d.putPermanentOnBattlefield(owner, InvasionOfInnistrad.name).also {
                        d.addComponent(it, CountersComponent(mapOf(CounterType.DEFENSE to 5)))
                        d.addComponent(it, ProtectorComponent(d.getOpponent(owner)))
                    }
                }
                val spell = d.putCardInHand(me, CosmicHunger.name)
                d.giveMana(me, Color.GREEN, 1)
                d.giveColorlessMana(me, 1)
                d.castSpell(me, spell, listOf(source, target)).isSuccess shouldBe true
                d.bothPass()
                when (kind) {
                    "creature" -> (d.state.getEntity(target)!!.get<DamageComponent>()?.amount ?: 0) shouldBe 2
                    "planeswalker" -> d.state.getEntity(target)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY) shouldBe 6
                    else -> d.state.getEntity(target)!!.get<CountersComponent>()!!.getCount(CounterType.DEFENSE) shouldBe 3
                }
                val damage = d.events.filterIsInstance<DamageDealtEvent>().last { it.targetId == target }
                damage.sourceId shouldBe source
                damage.amount shouldBe 2
                damage.isCombatDamage shouldBe false
                d.getLifeTotal(me) shouldBe 22
            }
        }
    }
    test("the two targets must differ, source must be controlled, and players or ordinary noncreatures are illegal") {
        val d = fixture()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val own = d.putCreatureOnBattlefield(me, sourceCard.name)
        val other = d.putCreatureOnBattlefield(enemy, sturdy.name)
        val land = d.putLandOnBattlefield(enemy, "Forest")
        val enchantment = d.putPermanentOnBattlefield(enemy, "Test Enchantment")
        val spell = d.putCardInHand(me, CosmicHunger.name)
        d.giveMana(me, Color.GREEN, 1)
        d.giveColorlessMana(me, 1)
        for (targets in listOf(listOf(own, own), listOf(other, own), listOf(own, enemy), listOf(own, land), listOf(own, enchantment))) {
            d.castSpell(me, spell, targets).isSuccess shouldBe false
        }
        d.castSpell(me, spell, listOf(own, other)).isSuccess shouldBe true
        d.bothPass()
    }
    test("damage reads source power at resolution after a pump response") {
        val d = fixture()
        val me = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(me, sourceCard.name)
        val target = d.putCreatureOnBattlefield(d.getOpponent(me), sturdy.name)
        val hunger = d.putCardInHand(me, CosmicHunger.name)
        d.giveMana(me, Color.GREEN, 1)
        d.giveColorlessMana(me, 1)
        d.castSpell(me, hunger, listOf(source, target)).isSuccess shouldBe true
        val pump = d.putCardInHand(me, "Giant Growth")
        d.giveMana(me, Color.GREEN, 1)
        d.castSpell(me, pump, listOf(source)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        (d.state.getEntity(target)!!.get<DamageComponent>()?.amount ?: 0) shouldBe 5
        d.getLifeTotal(me) shouldBe 25
    }
    test("an illegal source on resolution deals no damage despite the victim remaining legal") {
        val d = fixture()
        val me = d.activePlayer!!
        val source = d.putCreatureOnBattlefield(me, sourceCard.name)
        val target = d.putCreatureOnBattlefield(d.getOpponent(me), sturdy.name)
        val hunger = d.putCardInHand(me, CosmicHunger.name)
        d.giveMana(me, Color.GREEN, 1)
        d.giveColorlessMana(me, 1)
        d.castSpell(me, hunger, listOf(source, target)).isSuccess shouldBe true
        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(source)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        (d.state.getEntity(target)!!.get<DamageComponent>()?.amount ?: 0) shouldBe 0
        d.events.filterIsInstance<DamageDealtEvent>().any { it.targetId == target } shouldBe false
        d.getLifeTotal(me) shouldBe 20
    }
    test("an illegal victim on resolution receives no damage and source lifelink gains no life") {
        val d = fixture()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val source = d.putCreatureOnBattlefield(me, sourceCard.name)
        val target = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val hunger = d.putCardInHand(me, CosmicHunger.name)
        d.giveMana(me, Color.GREEN, 1)
        d.giveColorlessMana(me, 1)
        d.castSpell(me, hunger, listOf(source, target)).isSuccess shouldBe true
        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(target)).isSuccess shouldBe true
        d.bothPass()
        d.state.getGraveyard(enemy).contains(target) shouldBe true
        val eventStart = d.events.size
        d.bothPass()
        d.events.drop(eventStart).filterIsInstance<DamageDealtEvent>().size shouldBe 0
        d.getLifeTotal(me) shouldBe 20
    }
})
