package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.exo.cards.ShatteringPulse
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ShatteringPulseScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + ShatteringPulse)
        d.initMirrorMatch(
            deck = Deck.of("Mountain" to 40),
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

    fun castWithBuyback(
        d: GameTestDriver,
        caster: EntityId,
        pulse: EntityId,
        target: EntityId
    ) {
        d.giveMana(caster, Color.RED, 5)
        d.submit(
            CastSpell(
                playerId = caster,
                cardId = pulse,
                targets = listOf(ChosenTarget.Permanent(target)),
                paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = ChoiceSlot.BUYBACK
            )
        ).error shouldBe null
    }

    test("legal actions expose buyback with the correct total cost") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(opponent, "Artifact Creature")
        d.putCardInHand(caster, "Shattering Pulse")
        d.giveMana(caster, Color.RED, 5)

        val buybackAction = d.legalActions(caster)
            .singleOrNull { it.description == "Cast Shattering Pulse (Buyback)" }

        withClue("buyback variant should be exposed through the generic BUYBACK rail") {
            (buybackAction != null) shouldBe true
        }
        val cast = buybackAction!!.action as CastSpell
        cast.declaredCostSlot shouldBe ChoiceSlot.BUYBACK
        buybackAction.affordable shouldBe true
        buybackAction.manaCostString shouldBe "{4}{R}"
    }

    test("normal Shattering Pulse destroys an artifact and goes to graveyard") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val pulse = d.putCardInHand(caster, "Shattering Pulse")
        d.giveMana(caster, Color.RED, 2)

        d.castSpellWithTargets(
            caster,
            pulse,
            listOf(ChosenTarget.Permanent(target))
        ).error shouldBe null
        resolveStack(d)

        d.findPermanent(opponent, "Artifact Creature") shouldBe null
        d.getGraveyardCardNames(opponent).contains("Artifact Creature") shouldBe true
        d.getGraveyardCardNames(caster).contains("Shattering Pulse") shouldBe true
        d.getHand(caster).contains(pulse) shouldBe false
    }

    test("paid buyback destroys the artifact and returns Shattering Pulse to hand") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val pulse = d.putCardInHand(caster, "Shattering Pulse")

        castWithBuyback(d, caster, pulse, target)
        resolveStack(d)

        d.findPermanent(opponent, "Artifact Creature") shouldBe null
        d.getGraveyardCardNames(opponent).contains("Artifact Creature") shouldBe true
        d.getHand(caster).contains(pulse) shouldBe true
        d.getGraveyardCardNames(caster).contains("Shattering Pulse") shouldBe false
    }

    test("countered buyback Shattering Pulse goes to graveyard without destroying the artifact") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Artifact Creature")
        val pulse = d.putCardInHand(caster, "Shattering Pulse")
        val counterspell = d.putCardInHand(opponent, "Counterspell")
        d.giveMana(opponent, Color.BLUE, 2)

        castWithBuyback(d, caster, pulse, target)
        d.passPriority(caster).error shouldBe null
        d.castSpellWithTargets(
            opponent,
            counterspell,
            listOf(ChosenTarget.Spell(pulse))
        ).error shouldBe null
        resolveStack(d)

        d.findPermanent(opponent, "Artifact Creature") shouldBe target
        d.getGraveyardCardNames(caster).contains("Shattering Pulse") shouldBe true
        d.getHand(caster).contains(pulse) shouldBe false
    }

    test("nonartifact permanents are not legal targets") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val pulse = d.putCardInHand(caster, "Shattering Pulse")
        d.giveMana(caster, Color.RED, 2)

        withClue("Shattering Pulse must reject a nonartifact permanent") {
            (d.castSpellWithTargets(
                caster,
                pulse,
                listOf(ChosenTarget.Permanent(target))
            ).error != null) shouldBe true
        }
    }
})
