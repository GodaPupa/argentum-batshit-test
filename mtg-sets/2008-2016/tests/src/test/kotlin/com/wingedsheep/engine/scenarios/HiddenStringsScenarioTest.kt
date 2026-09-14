package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.CipherEncodedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dgm.cards.HiddenStrings
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class HiddenStringsScenarioTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCard(HiddenStrings)
        initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun chooseMode(game: GameTestDriver, startsWith: String) {
        val decision = game.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = decision.options.indexOfFirst { it.startsWith(startsWith) }
        (index >= 0) shouldBe true
        game.submitDecision(
            decision.playerId,
            OptionChosenResponse(decision.id, index),
        ).error shouldBe null
    }

    test("the two targets make independent tap or untap choices") {
        val game = driver()
        val player = game.activePlayer!!
        val first = game.putCreatureOnBattlefield(player, "Grizzly Bears")
        val second = game.putCreatureOnBattlefield(player, "Centaur Courser")
        game.tapPermanent(second)
        val spell = game.putCardInHand(player, "Hidden Strings")
        game.giveMana(player, Color.BLUE, 2)

        game.castSpell(player, spell, targets = listOf(first, second)).error shouldBe null
        game.bothPass()
        game.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        game.submitYesNo(player, true)
        chooseMode(game, "Tap")
        game.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        game.submitYesNo(player, true)
        chooseMode(game, "Untap")
        game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        game.selectCards(emptyList())

        game.isTapped(first) shouldBe true
        game.isTapped(second) shouldBe false
        game.getGraveyard(player).contains(spell) shouldBe true
    }

    test("encoding exiles the card and the encoded creature's combat damage offers a free copy") {
        val game = driver()
        val player = game.activePlayer!!
        val opponent = game.getOpponent(player)
        val host = game.putCreatureOnBattlefield(player, "Grizzly Bears")
        game.removeSummoningSickness(host)
        val spell = game.putCardInHand(player, "Hidden Strings")
        game.giveMana(player, Color.BLUE, 2)

        game.castSpell(player, spell, targets = emptyList()).error shouldBe null
        game.bothPass()
        val encode = game.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        encode.options.contains(host) shouldBe true
        game.selectCards(listOf(host))

        game.getExile(player).contains(spell) shouldBe true
        game.state.getEntity(spell)?.get<CipherEncodedComponent>()?.creatureId shouldBe host

        game.passPriorityUntil(Step.DECLARE_ATTACKERS)
        game.declareAttackers(player, listOf(host), opponent).error shouldBe null
        game.passPriorityUntil(Step.COMBAT_DAMAGE)
        while (game.state.pendingDecision != null) game.autoResolveDecision()
        while (game.state.stack.isNotEmpty() && game.state.pendingDecision == null) game.bothPass()

        game.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        game.submitYesNo(player, true)
        // Casting the copy reuses normal spell announcement, so its optional targets are chosen now.
        game.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.ChooseTargetsDecision>()
        game.skipTargets()
        game.getExile(player).contains(spell) shouldBe true
    }
})
