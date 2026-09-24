package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.neo.cards.MastersRebuke
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.core.ManaCost
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MastersRebukeBatchAFScenarioTest : FunSpec({
    val attacker = CardDefinition.creature(
        name = "Batch AF Four Power",
        manaCost = ManaCost.parse("{3}{G}"),
        subtypes = setOf(com.wingedsheep.sdk.core.Subtype("Beast")),
        power = 4,
        toughness = 4,
    )
    val target = CardDefinition.creature(
        name = "Batch AF Target",
        manaCost = ManaCost.parse("{4}{G}"),
        subtypes = setOf(com.wingedsheep.sdk.core.Subtype("Beast")),
        power = 5,
        toughness = 6,
    )

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(MastersRebuke, attacker, target))
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("source creature deals damage equal to its power without taking reciprocal damage") {
        val d = driver()
        val caster = d.activePlayer!!
        val opponent = d.getOpponent(caster)
        val source = d.putCreatureOnBattlefield(caster, "Batch AF Four Power")
        val recipient = d.putCreatureOnBattlefield(opponent, "Batch AF Target")
        d.giveMana(caster, Color.GREEN, 1)
        d.giveColorlessMana(caster, 1)
        val spell = d.putCardInHand(caster, "Master's Rebuke")

        d.castSpellWithTargets(
            caster,
            spell,
            listOf(ChosenTarget.Permanent(source), ChosenTarget.Permanent(recipient))
        )
        d.bothPass()

        d.state.getEntity(recipient)?.get<DamageComponent>()?.amount shouldBe 4
        (d.state.getEntity(source)?.get<DamageComponent>()?.amount ?: 0) shouldBe 0
    }
})
