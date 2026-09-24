package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PrototypeComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.bro.cards.BoulderbranchGolem
import com.wingedsheep.mtg.sets.definitions.cmr.cards.MaelstromColossus
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CascadeEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class MaelstromColossusCascadeScenarioTest : FunSpec({
    fun driver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(MaelstromColossus, BoulderbranchGolem))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    fun castMaelstromIntoBoulderbranch(driver: GameTestDriver): Pair<EntityId, ChooseOptionDecision> {
        val caster = driver.activePlayer!!
        driver.putCardOnTopOfLibrary(caster, "Boulderbranch Golem")
        driver.giveColorlessMana(caster, 8)
        val colossus = driver.putCardInHand(caster, "Maelstrom Colossus")
        driver.castSpell(caster, colossus).isSuccess shouldBe true
        driver.bothPass()

        val decision = driver.pendingDecision as? ChooseOptionDecision
            ?: error("Cascade into a Prototype card must expose the normal/Prototype cast-mode choice")
        decision.options.shouldContainExactly(
            "Cast normally for free",
            "Cast as Prototype for free",
            "Decline",
        )
        return caster to decision
    }

    test("casting Maelstrom Colossus puts its Cascade trigger on the stack") {
        val driver = driver()
        val caster = driver.activePlayer!!
        driver.giveColorlessMana(caster, 8)
        val colossus = driver.putCardInHand(caster, "Maelstrom Colossus")

        driver.castSpell(caster, colossus).isSuccess shouldBe true

        val cascadeTriggers = driver.state.stack.mapNotNull { id ->
            driver.state.getEntity(id)?.get<TriggeredAbilityOnStackComponent>()
        }.filter { it.effect is CascadeEffect }

        cascadeTriggers.size shouldBe 1
        cascadeTriggers.single().sourceId shouldBe colossus
    }

    test("Cascade can cast Boulderbranch Golem normally for free") {
        val driver = driver()
        val (caster, decision) = castMaelstromIntoBoulderbranch(driver)

        driver.submitDecision(caster, OptionChosenResponse(decision.id, 0)).error shouldBe null

        val golemId = driver.state.stack.single { id ->
            driver.state.getEntity(id)?.get<CardComponent>()?.name == "Boulderbranch Golem"
        }
        val spell = driver.state.getEntity(golemId)?.get<CardComponent>()!!
        spell.manaCost.toString() shouldBe "{7}"
        spell.manaValue shouldBe 7
        spell.colors shouldBe emptySet()
        spell.baseStats?.basePower shouldBe 6
        spell.baseStats?.baseToughness shouldBe 5
        driver.state.getEntity(golemId)?.has<PrototypeComponent>() shouldBe false

        while (driver.state.stack.isNotEmpty() && driver.pendingDecision == null) driver.bothPass()
        driver.getLifeTotal(caster) shouldBe 26
    }

    test("Cascade can cast Boulderbranch Golem as Prototype for free") {
        val driver = driver()
        val (caster, decision) = castMaelstromIntoBoulderbranch(driver)

        driver.submitDecision(caster, OptionChosenResponse(decision.id, 1)).error shouldBe null

        val golemId = driver.state.stack.single { id ->
            driver.state.getEntity(id)?.get<CardComponent>()?.name == "Boulderbranch Golem"
        }
        val spell = driver.state.getEntity(golemId)?.get<CardComponent>()!!
        spell.manaCost.toString() shouldBe "{3}{G}"
        spell.manaValue shouldBe 4
        spell.colors shouldBe setOf(Color.GREEN)
        spell.baseStats?.basePower shouldBe 3
        spell.baseStats?.baseToughness shouldBe 3
        driver.state.getEntity(golemId)?.has<PrototypeComponent>() shouldBe true

        while (driver.state.stack.isNotEmpty() && driver.pendingDecision == null) driver.bothPass()
        driver.getLifeTotal(caster) shouldBe 23
    }
})
