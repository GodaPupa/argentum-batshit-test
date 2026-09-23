package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ulg.cards.Snap
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SnapScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + Snap)
        d.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun castSnapAndPauseForLandChoice(
        d: GameTestDriver,
        caster: EntityId,
        creature: EntityId
    ) {
        if (d.priorityPlayer != caster) d.passPriority(d.priorityPlayer!!)
        d.giveMana(caster, Color.BLUE, 2)
        val snap = d.putCardInHand(caster, "Snap")
        d.castSpellWithTargets(
            caster,
            snap,
            listOf(ChosenTarget.Permanent(creature))
        ).error shouldBe null

        var guard = 0
        while (d.pendingDecision !is SelectCardsDecision && guard++ < 20) {
            d.bothPass()
        }
        withClue("Snap should pause for a resolution-time land choice") {
            (d.pendingDecision is SelectCardsDecision) shouldBe true
        }
    }

    test("returns the targeted creature and may choose zero lands") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2

        val creature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val islandA = d.putLandOnBattlefield(caster, "Island")
        val islandB = d.putLandOnBattlefield(opponent, "Island")
        d.tapPermanent(islandA)
        d.tapPermanent(islandB)

        castSnapAndPauseForLandChoice(d, caster, creature)
        d.submitCardSelection(caster, emptyList()).error shouldBe null

        withClue("target creature should be returned to its owner's hand") {
            d.getHand(opponent).contains(creature) shouldBe true
        }
        withClue("choosing zero lands leaves both tapped") {
            d.isTapped(islandA) shouldBe true
            d.isTapped(islandB) shouldBe true
        }
    }

    test("may untap two lands controlled by different players") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2

        val creature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val ownIsland = d.putLandOnBattlefield(caster, "Island")
        val opposingIsland = d.putLandOnBattlefield(opponent, "Island")
        val unchosenIsland = d.putLandOnBattlefield(caster, "Island")
        d.tapPermanent(ownIsland)
        d.tapPermanent(opposingIsland)
        d.tapPermanent(unchosenIsland)

        castSnapAndPauseForLandChoice(d, caster, creature)

        val decision = d.pendingDecision as SelectCardsDecision
        withClue("both players' lands should be legal resolution-time choices") {
            decision.options.contains(ownIsland) shouldBe true
            decision.options.contains(opposingIsland) shouldBe true
        }
        decision.minSelections shouldBe 0
        decision.maxSelections shouldBe 2

        d.submitCardSelection(caster, listOf(ownIsland, opposingIsland)).error shouldBe null

        d.isTapped(ownIsland) shouldBe false
        d.isTapped(opposingIsland) shouldBe false
        d.isTapped(unchosenIsland) shouldBe true
        d.getHand(opponent).contains(creature) shouldBe true
    }
})
