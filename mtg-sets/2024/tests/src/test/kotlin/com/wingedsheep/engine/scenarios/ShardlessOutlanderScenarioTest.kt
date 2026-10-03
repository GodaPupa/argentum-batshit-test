package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.j25.cards.ShardlessOutlander
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ShardlessOutlanderScenarioTest : FunSpec({
    test("basic landcycling pays two, discards, and selects only a basic land") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + ShardlessOutlander)
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val me = d.activePlayer!!
        val card = d.putCardInHand(me, "Shardless Outlander")
        val nonbasic = d.putCardOnTopOfLibrary(me, "Command Tower")
        val basic = d.putCardOnTopOfLibrary(me, "Island")
        val action = TypecycleCard(me, card)
        d.submit(action).isSuccess shouldBe false
        d.findCardInHand(me, "Shardless Outlander") shouldBe card
        d.giveColorlessMana(me, 2)
        val result = d.submit(action)
        (result.isSuccess || result.isPaused) shouldBe true
        d.getGraveyardCardNames(me) shouldContain "Shardless Outlander"
        val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        decision.options shouldContain basic
        decision.options shouldNotContain nonbasic
        d.submitCardSelection(me, listOf(basic)).isSuccess shouldBe true
        d.findCardInHand(me, "Island") shouldBe basic
        d.state.getLibrary(me) shouldNotContain basic
        ShardlessOutlander.keywords shouldContain Keyword.TRAMPLE
        ShardlessOutlander.creatureStats?.basePower shouldBe 6
        ShardlessOutlander.creatureStats?.baseToughness shouldBe 5
    }
})
