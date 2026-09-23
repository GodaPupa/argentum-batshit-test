package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.som.cards.TurnAside
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TurnAsideScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + TurnAside)
        d.initMirrorMatch(
            deck = Deck.of("Island" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.state.stack.isNotEmpty() && guard++ < 30) {
            d.bothPass()
        }
        withClue("stack should resolve completely") {
            d.state.stack.isEmpty() shouldBe true
        }
    }

    test("counters a spell that targets a permanent you control") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val protected = d.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val bolt = d.putCardInHand(opponent, "Lightning Bolt")
        val turnAside = d.putCardInHand(caster, "Turn Aside")
        d.giveMana(opponent, Color.RED, 1)
        d.giveMana(caster, Color.BLUE, 1)

        d.passPriority(caster).error shouldBe null
        d.castSpellWithTargets(
            opponent,
            bolt,
            listOf(ChosenTarget.Permanent(protected))
        ).error shouldBe null

        d.passPriority(opponent).error shouldBe null
        d.castSpellWithTargets(
            caster,
            turnAside,
            listOf(ChosenTarget.Spell(bolt))
        ).error shouldBe null

        resolveStack(d)

        d.findPermanent(caster, "Grizzly Bears") shouldBe protected
        d.getGraveyardCardNames(opponent).contains("Lightning Bolt") shouldBe true
        d.getGraveyardCardNames(caster).contains("Turn Aside") shouldBe true
    }

    test("rejects a spell that targets only an opponent permanent") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val opposingPermanent = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val bolt = d.putCardInHand(opponent, "Lightning Bolt")
        val turnAside = d.putCardInHand(caster, "Turn Aside")
        d.giveMana(opponent, Color.RED, 1)
        d.giveMana(caster, Color.BLUE, 1)

        d.passPriority(caster).error shouldBe null
        d.castSpellWithTargets(
            opponent,
            bolt,
            listOf(ChosenTarget.Permanent(opposingPermanent))
        ).error shouldBe null

        d.passPriority(opponent).error shouldBe null
        withClue("Turn Aside must reject a spell with no target permanent controlled by its caster") {
            (d.castSpellWithTargets(
                caster,
                turnAside,
                listOf(ChosenTarget.Spell(bolt))
            ).error != null) shouldBe true
        }
    }

    test("does not require the target spell to be controlled by an opponent") {
        val d = driver()
        val caster = d.player1
        val protected = d.putCreatureOnBattlefield(caster, "Grizzly Bears")
        val growth = d.putCardInHand(caster, "Giant Growth")
        val turnAside = d.putCardInHand(caster, "Turn Aside")
        d.giveMana(caster, Color.GREEN, 1)
        d.giveMana(caster, Color.BLUE, 1)

        d.castSpellWithTargets(
            caster,
            growth,
            listOf(ChosenTarget.Permanent(protected))
        ).error shouldBe null

        d.castSpellWithTargets(
            caster,
            turnAside,
            listOf(ChosenTarget.Spell(growth))
        ).error shouldBe null

        resolveStack(d)

        d.getGraveyardCardNames(caster).contains("Giant Growth") shouldBe true
        d.getGraveyardCardNames(caster).contains("Turn Aside") shouldBe true
    }
})
