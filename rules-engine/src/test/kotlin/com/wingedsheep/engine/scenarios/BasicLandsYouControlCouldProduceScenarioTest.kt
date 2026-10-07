package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.values.ManaColorSet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BasicLandsYouControlCouldProduceScenarioTest : FunSpec({

    val source = card("Basic-Land Color Fixture") {
        manaCost = "{2}"
        typeLine = "Artifact"
        activatedAbility {
            cost = Costs.Tap
            manaAbility = true
            effect = Effects.AddManaOfChoice(ManaColorSet.BasicLandsYouControlCouldProduce)
        }
    }
    val abilityId = source.activatedAbilities.single().id

    fun driver(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + source)
        d.initMirrorMatch(Deck.of("Mountain" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun pool(d: GameTestDriver, player: EntityId): ManaPoolComponent =
        d.state.getEntity(player)?.get<ManaPoolComponent>() ?: ManaPoolComponent()

    test("a tapped basic land you control contributes its color") {
        val d = driver()
        val me = d.activePlayer!!
        val island = d.putLandOnBattlefield(me, "Island")
        d.tapPermanent(island)
        val fixture = d.putPermanentOnBattlefield(me, source.name)

        d.submit(ActivateAbility(me, fixture, abilityId, manaColorChoice = Color.BLUE)).isSuccess shouldBe true
        pool(d, me).blue shouldBe 1
    }

    test("a nonbasic land you control does not widen the color set") {
        val d = driver()
        val me = d.activePlayer!!
        d.putLandOnBattlefield(me, "Mountain")
        d.putLandOnBattlefield(me, "City of Brass")
        val fixture = d.putPermanentOnBattlefield(me, source.name)

        d.submit(ActivateAbility(me, fixture, abilityId, manaColorChoice = Color.GREEN)).isSuccess shouldBe true
        pool(d, me).green shouldBe 0
        pool(d, me).red shouldBe 1
    }

    test("an opponent's basic land does not widen the color set") {
        val d = driver()
        val me = d.activePlayer!!
        val enemy = d.getOpponent(me)
        d.putLandOnBattlefield(me, "Mountain")
        d.putLandOnBattlefield(enemy, "Island")
        val fixture = d.putPermanentOnBattlefield(me, source.name)

        d.submit(ActivateAbility(me, fixture, abilityId, manaColorChoice = Color.BLUE)).isSuccess shouldBe true
        pool(d, me).blue shouldBe 0
        pool(d, me).red shouldBe 1
    }
})
