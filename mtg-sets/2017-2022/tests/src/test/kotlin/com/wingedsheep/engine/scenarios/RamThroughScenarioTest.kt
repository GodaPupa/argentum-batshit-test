package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.core.LifeChangeReason
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.iko.cards.RamThrough
import com.wingedsheep.mtg.sets.definitions.`10e`.cards.SpiritLink
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RamThroughScenarioTest : FunSpec({
    val trampler = card("Ram Fixture Trampler") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Beast"
        power = 5
        toughness = 5
        keywords(Keyword.TRAMPLE, Keyword.LIFELINK)
    }
    val ordinary = card("Ram Fixture Ordinary") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Beast"
        power = 5
        toughness = 5
        keywords(Keyword.LIFELINK)
    }
    val linkedTrampler = card("Ram Fixture Linked Trampler") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Beast"
        power = 5
        toughness = 5
        keywords(Keyword.TRAMPLE)
    }
    val bounce = card("Ram Fixture Bounce") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell { target = Targets.Creature; effect = Effects.ReturnToHand(com.wingedsheep.sdk.scripting.targets.EffectTarget.ContextTarget(0)) }
    }
    val grantTrample = card("Ram Fixture Trample") {
        manaCost = "{G}"
        typeLine = "Instant"
        spell { target = Targets.Creature; effect = Effects.GrantKeyword(Keyword.TRAMPLE) }
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(RamThrough, SpiritLink, trampler, ordinary, linkedTrampler, bounce, grantTrample))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun castRam(d: GameTestDriver, source: com.wingedsheep.sdk.model.EntityId, target: com.wingedsheep.sdk.model.EntityId) {
        val ram = d.putCardInHand(d.player1, RamThrough.name)
        d.giveMana(d.player1, Color.GREEN, 1)
        d.giveColorlessMana(d.player1, 1)
        d.castSpell(d.player1, ram, listOf(source, target)).isSuccess shouldBe true
    }
    for (trample in listOf(false, true)) {
        test("real spell preserves total damage and creature source attribution; trample=$trample") {
            val d = fixture()
            val source = d.putCreatureOnBattlefield(d.player1, if (trample) trampler.name else ordinary.name)
            val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
            castRam(d, source, target)
            d.bothPass().isSuccess shouldBe true
            d.getLifeTotal(d.player1) shouldBe 25
            d.getLifeTotal(d.player2) shouldBe if (trample) 17 else 20
            d.state.getGraveyard(d.player2).contains(target) shouldBe true
            val damage = d.events.filterIsInstance<DamageDealtEvent>()
            damage.map { it.targetId to it.amount } shouldBe if (trample)
                listOf(target to 2, d.player2 to 3) else listOf(target to 5)
            damage.all { it.sourceId == source && !it.isCombatDamage } shouldBe true
            if (trample) {
                damage.map { it.simultaneousDamageGroupIndex } shouldBe listOf(0, 1)
                damage.map { it.simultaneousDamageGroupSize } shouldBe listOf(2, 2)
                d.events.filterIsInstance<LifeChangedEvent>()
                    .filter { it.reason == LifeChangeReason.LIFE_GAIN }
                    .map { it.newLife - it.oldLife } shouldBe listOf(5)
            }
        }
    }
    test("marked damage determines excess without rerouting more damage than source power") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, trampler.name)
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(target, DamageComponent(1))
        castRam(d, source, target)
        d.bothPass().isSuccess shouldBe true
        d.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe
            listOf(target to 1, d.player2 to 4)
        d.getLifeTotal(d.player1) shouldBe 25
        d.getLifeTotal(d.player2) shouldBe 16
    }
    test("power and trample are both read after responses resolve") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, ordinary.name)
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        castRam(d, source, target)
        for (spellName in listOf("Giant Growth", grantTrample.name)) {
            val spell = d.putCardInHand(d.player1, spellName)
            d.giveMana(d.player1, Color.GREEN, 1)
            d.castSpell(d.player1, spell, listOf(source)).isSuccess shouldBe true
            d.bothPass().isSuccess shouldBe true
        }
        d.bothPass().isSuccess shouldBe true
        d.events.filterIsInstance<DamageDealtEvent>().map { it.targetId to it.amount } shouldBe
            listOf(target to 2, d.player2 to 6)
        d.getLifeTotal(d.player1) shouldBe 28
    }
    for (removeSource in listOf(true, false)) {
        test("one illegal target prevents both creature and excess damage; source illegal=$removeSource") {
            val d = fixture()
            val source = d.putCreatureOnBattlefield(d.player1, trampler.name)
            val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
            castRam(d, source, target)
            val response = d.putCardInHand(d.player1, bounce.name)
            d.giveMana(d.player1, Color.BLUE, 1)
            d.castSpell(d.player1, response, listOf(if (removeSource) source else target)).isSuccess shouldBe true
            d.bothPass().isSuccess shouldBe true
            d.bothPass().isSuccess shouldBe true
            d.events.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
            d.getLifeTotal(d.player1) shouldBe 20
            d.getLifeTotal(d.player2) shouldBe 20
        }
    }
    test("split excess damage fires Spirit Link once for the combined source event") {
        val d = fixture()
        val source = d.putCreatureOnBattlefield(d.player1, linkedTrampler.name)
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        val link = d.putCardInHand(d.player1, SpiritLink.name)
        d.giveMana(d.player1, Color.WHITE, 1)
        d.castSpell(d.player1, link, listOf(source)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        castRam(d, source, target)
        d.bothPass().isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true

        d.getLifeTotal(d.player1) shouldBe 25
        d.events.filterIsInstance<LifeChangedEvent>()
            .filter { it.reason == LifeChangeReason.LIFE_GAIN }
            .map { it.newLife - it.oldLife } shouldBe listOf(5)
    }

    test("only a controlled first creature and an opposing second creature form legal targets") {
        val d = fixture()
        val own = d.putCreatureOnBattlefield(d.player1, trampler.name)
        val other = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val secondOwn = d.putCreatureOnBattlefield(d.player1, ordinary.name)
        val land = d.putLandOnBattlefield(d.player2, "Forest")
        val ram = d.putCardInHand(d.player1, RamThrough.name)
        d.giveMana(d.player1, Color.GREEN, 1)
        d.giveColorlessMana(d.player1, 1)
        for (targets in listOf(listOf(other, own), listOf(own, secondOwn), listOf(own, d.player2), listOf(own, land))) {
            d.castSpell(d.player1, ram, targets).isSuccess shouldBe false
        }
        d.castSpell(d.player1, ram, listOf(own, other)).isSuccess shouldBe true
        d.bothPass().isSuccess shouldBe true
    }
})
