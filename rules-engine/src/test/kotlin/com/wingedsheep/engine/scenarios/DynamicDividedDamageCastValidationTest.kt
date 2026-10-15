package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DynamicDividedDamageCastValidationTest : FunSpec({
    val fixtureSpell = card("Dynamic Divided Damage Fixture") {
        manaCost = "{X}{R}"
        colorIdentity = "R"
        typeLine = "Sorcery"
        spell {
            target = TargetCreature(count = 2, minCount = 0, optional = true)
            effect = DividedDamageEffect(
                totalDamage = 0,
                minTargets = 0,
                dynamicTotal = DynamicAmount.XValue,
            )
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + fixtureSpell)
        d.initMirrorMatch(Deck.of("Mountain" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("dynamic divided damage validates the announced distribution against X") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val first = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val second = d.putCreatureOnBattlefield(enemy, "Grizzly Bears")
        val spell = d.putCardInHand(me, fixtureSpell.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 3)

        val result = d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                xValue = 3,
                targets = listOf(
                    ChosenTarget.Permanent(first),
                    ChosenTarget.Permanent(second),
                ),
                damageDistribution = mapOf(first to 1, second to 2),
            )
        )
        result.error shouldBe null
    }

    test("dynamic divided damage rejects a distribution whose sum differs from X") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val first = d.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val second = d.putCreatureOnBattlefield(enemy, "Grizzly Bears")
        val spell = d.putCardInHand(me, fixtureSpell.name)
        d.giveMana(me, Color.RED, 1)
        d.giveColorlessMana(me, 3)

        val result = d.submit(
            CastSpell(
                playerId = me,
                cardId = spell,
                xValue = 3,
                targets = listOf(
                    ChosenTarget.Permanent(first),
                    ChosenTarget.Permanent(second),
                ),
                damageDistribution = mapOf(first to 1, second to 1),
            )
        )
        result.error shouldBe "Total distributed damage (2) must equal 3"
    }

    test("zero dynamic total accepts zero targets and rejects a chosen target") {
        val legal = driver()
        val legalPlayer = legal.activePlayer!!
        val legalSpell = legal.putCardInHand(legalPlayer, fixtureSpell.name)
        legal.giveMana(legalPlayer, Color.RED, 1)
        legal.submit(
            CastSpell(
                playerId = legalPlayer,
                cardId = legalSpell,
                xValue = 0,
                targets = emptyList(),
            )
        ).error shouldBe null

        val illegal = driver()
        val me = illegal.activePlayer!!
        val enemy = illegal.getOpponent(me)
        val target = illegal.putCreatureOnBattlefield(enemy, "Centaur Courser")
        val illegalSpell = illegal.putCardInHand(me, fixtureSpell.name)
        illegal.giveMana(me, Color.RED, 1)
        illegal.submit(
            CastSpell(
                playerId = me,
                cardId = illegalSpell,
                xValue = 0,
                targets = listOf(ChosenTarget.Permanent(target)),
            )
        ).error shouldBe "Cannot choose targets when total divided damage is 0"
    }
})
