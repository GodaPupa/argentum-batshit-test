package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.leg.cards.ForceSpike
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ForceSpikeScenarioTest : FunSpec({

    fun nameOf(d: GameTestDriver, id: EntityId): String? =
        d.state.getEntity(id)?.get<CardComponent>()?.name

    test("Force Spike counters a spell when its controller declines the one-mana tax") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.registerCard(ForceSpike)
        d.initMirrorMatch(deck = Deck.of("Forest" to 20, "Island" to 20))
        val p1 = d.activePlayer!!
        val p2 = d.getOpponent(p1)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        d.giveMana(p1, Color.GREEN, 1)
        d.giveColorlessMana(p1, 1)
        val bears = d.putCardInHand(p1, "Grizzly Bears")
        d.submit(CastSpell(playerId = p1, cardId = bears)).isSuccess shouldBe true

        d.passPriority(p1)
        d.giveMana(p2, Color.BLUE, 1)
        val forceSpike = d.putCardInHand(p2, "Force Spike")
        val bearsOnStack = d.state.stack.first()
        d.submit(
            CastSpell(
                playerId = p2,
                cardId = forceSpike,
                targets = listOf(ChosenTarget.Spell(bearsOnStack))
            )
        ).isSuccess shouldBe true

        d.bothPass()
        if (d.pendingDecision != null) {
            d.autoResolveDecision()
        }

        d.state.stack.none { nameOf(d, it) == "Grizzly Bears" } shouldBe true
        d.getGraveyardCardNames(p1).contains("Grizzly Bears") shouldBe true
        d.getGraveyardCardNames(p2).contains("Force Spike") shouldBe true
    }
})
