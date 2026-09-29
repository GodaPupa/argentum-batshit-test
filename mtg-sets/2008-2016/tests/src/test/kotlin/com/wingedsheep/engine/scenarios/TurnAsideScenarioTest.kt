package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.som.cards.TurnAside
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TurnAsideScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + TurnAside)
        d.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.stackSize > 0 && guard++ < 20) d.bothPass()
    }

    test("counters a spell targeting a permanent controlled by Turn Aside's caster") {
        val d = driver()
        val attacker = d.player1
        val protector = d.player2
        val protected = d.putCreatureOnBattlefield(protector, "Grizzly Bears")

        d.giveMana(attacker, Color.RED, 1)
        val bolt = d.putCardInHand(attacker, "Lightning Bolt")
        d.castSpellWithTargets(attacker, bolt, listOf(ChosenTarget.Permanent(protected))).error shouldBe null

        if (d.priorityPlayer != protector) d.passPriority(attacker)
        d.giveMana(protector, Color.BLUE, 1)
        val turnAside = d.putCardInHand(protector, "Turn Aside")
        d.castSpellWithTargets(protector, turnAside, listOf(ChosenTarget.Spell(bolt))).error shouldBe null

        resolveStack(d)

        d.findPermanent(protector, "Grizzly Bears") shouldBe protected
        d.getGraveyardCardNames(attacker).contains("Lightning Bolt") shouldBe true
        d.getGraveyardCardNames(protector).contains("Turn Aside") shouldBe true
    }

    test("rejects a spell that targets only a permanent Turn Aside's caster does not control") {
        val d = driver()
        val spellCaster = d.player1
        val turnAsideCaster = d.player2
        val target = d.putCreatureOnBattlefield(spellCaster, "Grizzly Bears")

        d.giveMana(spellCaster, Color.GREEN, 1)
        val growth = d.putCardInHand(spellCaster, "Giant Growth")
        d.castSpellWithTargets(spellCaster, growth, listOf(ChosenTarget.Permanent(target))).error shouldBe null

        if (d.priorityPlayer != turnAsideCaster) d.passPriority(spellCaster)
        d.giveMana(turnAsideCaster, Color.BLUE, 1)
        val turnAside = d.putCardInHand(turnAsideCaster, "Turn Aside")
        val result = d.castSpellWithTargets(turnAsideCaster, turnAside, listOf(ChosenTarget.Spell(growth)))

        (result.error != null) shouldBe true
    }

    test("rejects a spell that targets the player rather than a permanent") {
        val d = driver()
        val spellCaster = d.player1
        val turnAsideCaster = d.player2

        d.giveMana(spellCaster, Color.RED, 1)
        val bolt = d.putCardInHand(spellCaster, "Lightning Bolt")
        d.castSpellWithTargets(spellCaster, bolt, listOf(ChosenTarget.Player(turnAsideCaster))).error shouldBe null

        if (d.priorityPlayer != turnAsideCaster) d.passPriority(spellCaster)
        d.giveMana(turnAsideCaster, Color.BLUE, 1)
        val turnAside = d.putCardInHand(turnAsideCaster, "Turn Aside")
        val result = d.castSpellWithTargets(turnAsideCaster, turnAside, listOf(ChosenTarget.Spell(bolt)))

        (result.error != null) shouldBe true
    }
})
