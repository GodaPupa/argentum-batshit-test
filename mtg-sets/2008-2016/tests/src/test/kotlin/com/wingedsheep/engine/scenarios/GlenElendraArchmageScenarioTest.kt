package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.eve.cards.GlenElendraArchmage
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class GlenElendraArchmageScenarioTest : FunSpec({

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + GlenElendraArchmage)
        d.initMirrorMatch(Deck.of("Island" to 20, "Mountain" to 20), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun putBoltOnStack(d: GameTestDriver, me: EntityId): Pair<EntityId, EntityId> {
        val enemy = d.getOpponent(me)
        val bolt = d.putCardInHand(enemy, "Lightning Bolt")
        d.giveMana(enemy, Color.RED, 1)
        d.passPriority(me).isSuccess shouldBe true
        d.castSpell(enemy, bolt, listOf(me)).isSuccess shouldBe true
        val spell = d.getTopOfStack()!!
        d.passPriority(enemy).isSuccess shouldBe true
        return enemy to spell
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.stackSize > 0 && guard++ < 8) d.bothPass()
        d.stackSize shouldBe 0
    }

    test("self-sacrifice counters a noncreature spell and persist returns Archmage with a minus counter") {
        val d = driver()
        val me = d.activePlayer!!
        val archmage = d.putCreatureOnBattlefield(me, GlenElendraArchmage.name)
        val (enemy, spell) = putBoltOnStack(d, me)

        d.giveMana(me, Color.BLUE, 1)
        d.submit(
            ActivateAbility(
                playerId = me,
                sourceId = archmage,
                abilityId = GlenElendraArchmage.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Spell(spell))
            )
        ).isSuccess shouldBe true

        d.findPermanent(me, GlenElendraArchmage.name) shouldBe null
        resolveStack(d)

        d.getGraveyardCardNames(enemy) shouldContain "Lightning Bolt"
        val returned = d.findPermanent(me, GlenElendraArchmage.name)!!
        d.state.getEntity(returned)!!.get<CountersComponent>()!!.getCount(CounterType.MINUS_ONE_MINUS_ONE) shouldBe 1
        d.state.projectedState.hasKeyword(returned, Keyword.FLYING) shouldBe true
        d.state.projectedState.hasKeyword(returned, Keyword.PERSIST) shouldBe true
    }

    test("creature spell is not a legal target and failed activation does not sacrifice Archmage") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        val archmage = d.putCreatureOnBattlefield(me, GlenElendraArchmage.name)
        val creature = d.putCardInHand(enemy, "Centaur Courser")
        d.giveMana(enemy, Color.GREEN, 1)
        d.giveColorlessMana(enemy, 2)
        d.passPriority(me).isSuccess shouldBe true
        d.castSpell(enemy, creature).isSuccess shouldBe true
        val spell = d.getTopOfStack()!!
        d.passPriority(enemy).isSuccess shouldBe true

        d.giveMana(me, Color.BLUE, 1)
        d.submit(
            ActivateAbility(
                playerId = me,
                sourceId = archmage,
                abilityId = GlenElendraArchmage.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Spell(spell))
            )
        ).isSuccess shouldBe false

        d.findPermanent(me, GlenElendraArchmage.name) shouldBe archmage
    }

    test("after persisting once a second self-sacrifice does not return Archmage again") {
        val d = driver()
        val me = d.activePlayer!!
        val first = d.putCreatureOnBattlefield(me, GlenElendraArchmage.name)
        val (_, firstSpell) = putBoltOnStack(d, me)
        d.giveMana(me, Color.BLUE, 1)
        d.submit(
            ActivateAbility(me, first, GlenElendraArchmage.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Spell(firstSpell)))
        ).isSuccess shouldBe true
        resolveStack(d)

        val returned = d.findPermanent(me, GlenElendraArchmage.name)!!
        d.state.getEntity(returned)!!.get<CountersComponent>()!!.getCount(CounterType.MINUS_ONE_MINUS_ONE) shouldBe 1

        val (_, secondSpell) = putBoltOnStack(d, me)
        d.giveMana(me, Color.BLUE, 1)
        d.submit(
            ActivateAbility(me, returned, GlenElendraArchmage.activatedAbilities.single().id,
                targets = listOf(ChosenTarget.Spell(secondSpell)))
        ).isSuccess shouldBe true
        resolveStack(d)

        d.findPermanent(me, GlenElendraArchmage.name) shouldBe null
        d.getGraveyardCardNames(me) shouldContain GlenElendraArchmage.name
    }
})
