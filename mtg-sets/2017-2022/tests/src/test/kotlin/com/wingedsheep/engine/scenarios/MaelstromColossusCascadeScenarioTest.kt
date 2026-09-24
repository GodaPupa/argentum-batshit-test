package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.cmr.cards.MaelstromColossus
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.CascadeEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MaelstromColossusCascadeScenarioTest : FunSpec({
    test("casting Maelstrom Colossus puts its Cascade trigger on the stack") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(MaelstromColossus))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val caster = driver.activePlayer!!
        repeat(8) { driver.putLandOnBattlefield(caster, "Forest") }
        val colossus = driver.putCardInHand(caster, "Maelstrom Colossus")

        driver.castSpell(caster, colossus).isSuccess shouldBe true

        val cascadeTriggers = driver.state.stack.mapNotNull { id ->
            driver.state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()
        }.filter { it.effect is CascadeEffect }

        cascadeTriggers.size shouldBe 1
        cascadeTriggers.single().sourceId shouldBe colossus
    }
})
