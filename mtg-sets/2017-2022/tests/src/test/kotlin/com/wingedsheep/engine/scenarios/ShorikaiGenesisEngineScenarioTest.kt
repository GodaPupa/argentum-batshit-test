package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dom.cards.Weatherlight
import com.wingedsheep.mtg.sets.definitions.nec.cards.ShorikaiGenesisEngine
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

class ShorikaiGenesisEngineScenarioTest : FunSpec({
    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCards(listOf(ShorikaiGenesisEngine, Weatherlight, PredefinedTokens.Pilot))
        d.initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("Shorikai carries Crew 8") {
        val crew = ShorikaiGenesisEngine.keywordAbilities
            .filterIsInstance<KeywordAbility.Numeric>()
            .single { it.keyword == Keyword.CREW }
        crew.n shouldBe 8
    }

    test("Shorikai activation draws two discards one and creates a Pilot") {
        val d = driver()
        val shorikai = d.putPermanentOnBattlefield(d.player1, "Shorikai, Genesis Engine")
        d.giveColorlessMana(d.player1, 1)
        val before = d.getHandSize(d.player1)
        val ability = d.cardRegistry.getCard("Shorikai, Genesis Engine")!!.script.activatedAbilities.single()
        d.submitSuccess(ActivateAbility(d.player1, shorikai, ability.id))
        d.bothPass()

        val discard = d.state.pendingDecision as SelectCardsDecision
        discard.options.isNotEmpty() shouldBe true
        d.submitCardSelection(d.player1, listOf(discard.options.first())).error shouldBe null
        var guard = 0
        while (d.state.stack.isNotEmpty() && d.state.pendingDecision == null && guard++ < 10) d.bothPass()

        d.getHandSize(d.player1) shouldBe before + 1
        d.findPermanent(d.player1, "Pilot").shouldNotBeNull()
    }

    test("a Pilot crews as power three") {
        val d = driver()
        val pilot = d.putCreatureOnBattlefield(d.player1, "Pilot")
        val weatherlight = d.putPermanentOnBattlefield(d.player1, "Weatherlight")
        d.submitSuccess(CrewVehicle(d.player1, weatherlight, listOf(pilot)))
        d.isTapped(pilot) shouldBe true
    }
})
