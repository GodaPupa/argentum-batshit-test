package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mid.cards.MalevolentHermit
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class MalevolentHermitScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + MalevolentHermit)
        d.initMirrorMatch(Deck.of("Island" to 20, "Mountain" to 20), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.stackSize > 0 && guard++ < 12) d.bothPass()
    }

    fun putEnemyBoltOnStack(d: GameTestDriver, me: EntityId): Pair<EntityId, EntityId> {
        val enemy = d.getOpponent(me)
        val bolt = d.putCardInHand(enemy, "Lightning Bolt")
        d.giveMana(enemy, Color.RED, 1)
        d.passPriority(me).isSuccess shouldBe true
        d.castSpell(enemy, bolt, listOf(me)).isSuccess shouldBe true
        val spell = d.getTopOfStack()!!
        d.passPriority(enemy).isSuccess shouldBe true
        return enemy to spell
    }

    test("front self-sacrifice counters target noncreature spell when its controller cannot pay three") {
        val d = driver()
        val me = d.activePlayer!!
        val hermit = d.putCreatureOnBattlefield(me, MalevolentHermit.name)
        val (enemy, spell) = putEnemyBoltOnStack(d, me)

        d.giveMana(me, Color.BLUE, 1)
        d.submit(
            ActivateAbility(
                playerId = me,
                sourceId = hermit,
                abilityId = MalevolentHermit.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Spell(spell)),
            )
        ).isSuccess shouldBe true

        d.findPermanent(me, MalevolentHermit.name) shouldBe null
        resolveStack(d)
        d.getGraveyardCardNames(enemy) shouldContain "Lightning Bolt"
        d.getLifeTotal(me) shouldBe 20
    }

    test("front ability cannot target a creature spell and failed activation does not sacrifice Hermit") {
        val d = driver()
        val enemy = d.activePlayer!!
        val me = d.getOpponent(enemy)
        val hermit = d.putCreatureOnBattlefield(me, MalevolentHermit.name)
        val creature = d.putCardInHand(enemy, "Centaur Courser")
        d.giveMana(enemy, Color.GREEN, 1)
        d.giveColorlessMana(enemy, 2)
        d.castSpell(enemy, creature).isSuccess shouldBe true
        val spell = d.getTopOfStack()!!
        d.passPriority(enemy).isSuccess shouldBe true

        d.giveMana(me, Color.BLUE, 1)
        d.submit(
            ActivateAbility(
                playerId = me,
                sourceId = hermit,
                abilityId = MalevolentHermit.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Spell(spell)),
            )
        ).isSuccess shouldBe false

        d.findPermanent(me, MalevolentHermit.name) shouldBe hermit
    }

    test("disturb enters as Benevolent Geist and protects only its controller's noncreature spells") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val hermit = d.putCardInGraveyard(me, MalevolentHermit.name)
        d.giveMana(me, Color.BLUE, 3)

        d.submit(
            CastSpell(
                playerId = me,
                cardId = hermit,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.DISTURB,
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(d)

        val geist = d.findPermanent(me, "Benevolent Geist")!!
        d.state.projectedState.hasKeyword(geist, Keyword.FLYING) shouldBe true

        val myBolt = d.putCardInHand(me, "Lightning Bolt")
        val enemyCounter = d.putCardInHand(enemy, "Counterspell")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, myBolt, listOf(enemy)).isSuccess shouldBe true
        val mySpell = d.getTopOfStack()!!
        d.passPriority(me).isSuccess shouldBe true
        d.giveMana(enemy, Color.BLUE, 2)
        d.submit(
            CastSpell(
                playerId = enemy,
                cardId = enemyCounter,
                targets = listOf(ChosenTarget.Spell(mySpell)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        d.bothPass()
        d.getTopOfStackName() shouldBe "Lightning Bolt"
        d.bothPass()
        d.getLifeTotal(enemy) shouldBe 17

        val enemyBolt = d.putCardInHand(enemy, "Lightning Bolt")
        val myCounter = d.putCardInHand(me, "Counterspell")
        d.giveMana(enemy, Color.RED, 1)
        d.passPriority(me).isSuccess shouldBe true
        d.castSpell(enemy, enemyBolt, listOf(me)).isSuccess shouldBe true
        val enemySpell = d.getTopOfStack()!!
        d.passPriority(enemy).isSuccess shouldBe true
        d.giveMana(me, Color.BLUE, 2)
        d.submit(
            CastSpell(
                playerId = me,
                cardId = myCounter,
                targets = listOf(ChosenTarget.Spell(enemySpell)),
                paymentStrategy = PaymentStrategy.FromPool,
            )
        ).isSuccess shouldBe true
        resolveStack(d)
        d.getGraveyardCardNames(enemy) shouldContain "Lightning Bolt"
        d.getLifeTotal(me) shouldBe 20
    }

    test("Benevolent Geist is exiled rather than put into a graveyard") {
        val d = driver()
        val me = d.activePlayer!!
        val geist = d.putCreatureOnBattlefield(me, "Benevolent Geist")
        val bolt = d.putCardInHand(me, "Lightning Bolt")
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, bolt, listOf(geist)).isSuccess shouldBe true
        resolveStack(d)

        d.state.getExile(me) shouldContain geist
        d.getGraveyard(me) shouldNotContain geist
    }
})
