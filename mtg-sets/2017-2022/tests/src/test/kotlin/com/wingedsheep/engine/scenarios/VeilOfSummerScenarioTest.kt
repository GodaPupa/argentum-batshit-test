package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.mechanics.targeting.PlayerColorHexproof
import com.wingedsheep.mtg.sets.definitions.m20.cards.VeilOfSummer
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VeilOfSummerScenarioTest : FunSpec({
    val probes = listOf("U", "B", "R").map { color -> card("Veil Probe $color") {
        manaCost = "{0}"; colorIndicator = color; typeLine = "Instant"
        spell { effect = Effects.GainLife(1) }
    } }
    val blueDamage = card("Veil Blue Damage") {
        manaCost = "{0}"; colorIndicator = "U"; typeLine = "Instant"
        spell { target = Targets.Any; effect = Effects.DealDamage(3, com.wingedsheep.sdk.scripting.targets.EffectTarget.ContextTarget(0)) }
    }
    val counter = card("Veil Counter Probe") {
        manaCost = "{0}"; colorIndicator = "U"; typeLine = "Instant"
        spell { target = Targets.Spell; effect = Effects.CounterSpell().then(Effects.GainLife(2)) }
    }
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + probes + listOf(VeilOfSummer, blueDamage, counter))
        it.initMirrorMatch(Deck.of("Forest" to 40)); it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun cast(d: GameTestDriver, player: EntityId, name: String, targets: List<EntityId> = emptyList()): EntityId {
        val id = d.putCardInHand(player, name)
        if (name == VeilOfSummer.name) d.giveMana(player, Color.GREEN)
        if (d.priorityPlayer != player) d.passPriority(d.priorityPlayer!!).error shouldBe null
        d.castSpellWithTargets(player, id, targets.map { target ->
            when {
                target in d.state.stack -> ChosenTarget.Spell(target)
                target in d.state.turnOrder -> ChosenTarget.Player(target)
                else -> ChosenTarget.Permanent(target)
            }
        }).error shouldBe null
        return id
    }
    fun resolve(d: GameTestDriver) { d.bothPass().error shouldBe null }

    test("no prior matching opponent cast gives no draw but grants both scopes") {
        val d = driver(); val before = d.getHandSize(d.player1)
        cast(d, d.player1, VeilOfSummer.name); resolve(d)
        d.getHandSize(d.player1) shouldBe before
        PlayerColorHexproof.applies(d.state, d.player1, d.player2, setOf(Color.BLUE)) shouldBe true
        PlayerColorHexproof.applies(d.state, d.player1, d.player2, setOf(Color.BLACK)) shouldBe true
    }
    for (color in listOf("U", "B", "R")) test("draw checks actual opponent cast history for $color") {
        val d = driver(); cast(d, d.player2, "Veil Probe $color"); resolve(d)
        val before = d.getHandSize(d.player1)
        cast(d, d.player1, VeilOfSummer.name); resolve(d)
        d.getHandSize(d.player1) shouldBe before + if (color == "R") 0 else 1
    }
    test("multiple blue and black casts still draw only one card") {
        val d = driver()
        for (color in listOf("U", "B")) { cast(d, d.player2, "Veil Probe $color"); resolve(d) }
        val before = d.getHandSize(d.player1); cast(d, d.player1, VeilOfSummer.name); resolve(d)
        d.getHandSize(d.player1) shouldBe before + 1
    }
    test("own blue spell does not satisfy opponent draw condition") {
        val d = driver(); cast(d, d.player1, "Veil Probe U"); resolve(d)
        val before = d.getHandSize(d.player1); cast(d, d.player1, VeilOfSummer.name); resolve(d)
        d.getHandSize(d.player1) shouldBe before
    }
    test("protects existing controlled noncreature and creature but not opponent or later entrant") {
        val d = driver()
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val land = d.putLandOnBattlefield(d.player1, "Forest")
        val other = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        cast(d, d.player1, VeilOfSummer.name); resolve(d)
        val later = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        for (id in listOf(bear, land)) {
            d.state.projectedState.hasKeyword(id, "HEXPROOF_FROM_BLUE") shouldBe true
            d.state.projectedState.hasKeyword(id, "HEXPROOF_FROM_BLACK") shouldBe true
            d.state.projectedState.hasKeyword(id, "HEXPROOF") shouldBe false
        }
        for (id in listOf(other, later)) d.state.projectedState.hasKeyword(id, "HEXPROOF_FROM_BLUE") shouldBe false
    }
    test("blue spell already targeting player becomes illegal after veil resolves") {
        val d = driver(); val before = d.getLifeTotal(d.player1)
        cast(d, d.player2, blueDamage.name, listOf(d.player1))
        cast(d, d.player1, VeilOfSummer.name); resolve(d); resolve(d)
        d.getLifeTotal(d.player1) shouldBe before
    }
    test("blue spell already targeting permanent becomes illegal after veil resolves") {
        val d = driver(); val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        cast(d, d.player2, blueDamage.name, listOf(bear))
        cast(d, d.player1, VeilOfSummer.name); resolve(d); resolve(d)
        (bear in d.state.getBattlefield()) shouldBe true
    }
    test("veil itself can be countered before its protection resolves") {
        val d = driver(); val veil = cast(d, d.player1, VeilOfSummer.name)
        cast(d, d.player2, counter.name, listOf(veil)); resolve(d)
        PlayerColorHexproof.applies(d.state, d.player1, d.player2, setOf(Color.BLUE)) shouldBe false
        (veil in d.state.stack) shouldBe false
    }
    test("already controlled spell is protected when veil resolves above its counterspell") {
        val d = driver(); val probe = cast(d, d.player1, "Veil Probe R")
        cast(d, d.player2, counter.name, listOf(probe))
        cast(d, d.player1, VeilOfSummer.name); resolve(d); resolve(d)
        (probe in d.state.stack) shouldBe true
        resolve(d)
    }
    test("own blue spell may target protected player and still deals damage") {
        val d = driver(); cast(d, d.player1, VeilOfSummer.name); resolve(d)
        val before = d.getLifeTotal(d.player1)
        cast(d, d.player1, blueDamage.name, listOf(d.player1)); resolve(d)
        d.getLifeTotal(d.player1) shouldBe before - 3
    }
    test("cleanup expires player and permanent hexproof and spell counter protection") {
        val d = driver(); val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        cast(d, d.player1, VeilOfSummer.name); resolve(d)
        d.replaceState(com.wingedsheep.engine.core.CleanupPhaseManager(d.cardRegistry, com.wingedsheep.engine.handlers.DecisionHandler()).cleanupEndOfTurn(d.state))
        PlayerColorHexproof.applies(d.state, d.player1, d.player2, setOf(Color.BLUE)) shouldBe false
        d.state.projectedState.hasKeyword(bear, "HEXPROOF_FROM_BLUE") shouldBe false
        (d.state.getEntity(d.player1)?.has<com.wingedsheep.engine.state.components.player.SpellsCantBeCounteredComponent>() == true) shouldBe false
    }
    test("a blue spell cast after veil announcement still satisfies draw when veil resolves") {
        val d = driver(); val before = d.getHandSize(d.player1)
        cast(d, d.player1, VeilOfSummer.name)
        cast(d, d.player2, "Veil Probe U"); resolve(d); resolve(d)
        d.getHandSize(d.player1) shouldBe before + 1
    }
    test("later controlled spell remains targetable but cannot be countered and counter rider happens") {
        val d = driver(); cast(d, d.player1, VeilOfSummer.name); resolve(d)
        val probe = cast(d, d.player1, "Veil Probe R")
        val before = d.getLifeTotal(d.player2)
        cast(d, d.player2, counter.name, listOf(probe)); resolve(d)
        (probe in d.state.stack) shouldBe true
        d.getLifeTotal(d.player2) shouldBe before + 2
        resolve(d)
    }
})
