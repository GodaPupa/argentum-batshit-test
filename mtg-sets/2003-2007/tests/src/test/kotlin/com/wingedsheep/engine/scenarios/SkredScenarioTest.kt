package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.csp.cards.BorealDruid
import com.wingedsheep.mtg.sets.definitions.csp.cards.Skred
import com.wingedsheep.mtg.sets.definitions.ice.cards.SnowCoveredIsland
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SkredScenarioTest : FunSpec({

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + Skred + SnowCoveredIsland + BorealDruid)
        d.initMirrorMatch(
            deck = Deck.of("Mountain" to 40),
            skipMulligans = true,
            startingPlayer = 0
        )
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun castSkred(d: GameTestDriver, target: com.wingedsheep.sdk.model.EntityId) {
        val caster = d.player1
        d.giveMana(caster, Color.RED, 1)
        val skred = d.putCardInHand(caster, "Skred")
        d.castSpellWithTargets(
            caster,
            skred,
            listOf(ChosenTarget.Permanent(target))
        ).error shouldBe null
    }

    fun resolveStack(d: GameTestDriver) {
        var guard = 0
        while (d.state.stack.isNotEmpty() && guard++ < 30) {
            d.bothPass()
        }
        d.state.stack.isEmpty() shouldBe true
    }

    test("counts snow permanents of different permanent types") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2

        d.putLandOnBattlefield(caster, "Snow-Covered Island")
        d.putCreatureOnBattlefield(caster, "Boreal Druid")
        val target = d.putCreatureOnBattlefield(opponent, "Artifact Creature")

        castSkred(d, target)
        resolveStack(d)

        withClue("one snow land plus one snow creature should make Skred deal 2") {
            d.findPermanent(opponent, "Artifact Creature") shouldBe null
            d.getGraveyardCardNames(opponent).contains("Artifact Creature") shouldBe true
        }
    }

    test("non-snow permanents do not increase Skred damage") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2

        d.putLandOnBattlefield(caster, "Snow-Covered Island")
        repeat(4) { d.putLandOnBattlefield(caster, "Mountain") }
        val target = d.putCreatureOnBattlefield(opponent, "Artifact Creature")

        castSkred(d, target)
        resolveStack(d)

        withClue("only the one snow permanent should count, so a 2/2 survives") {
            d.findPermanent(opponent, "Artifact Creature") shouldBe target
        }
    }

    test("snow count is evaluated at resolution") {
        val d = driver()
        val caster = d.player1
        val opponent = d.player2

        d.putLandOnBattlefield(caster, "Snow-Covered Island")
        val target = d.putCreatureOnBattlefield(opponent, "Artifact Creature")

        castSkred(d, target)
        d.putCreatureOnBattlefield(caster, "Boreal Druid")
        resolveStack(d)

        withClue("the second snow permanent entered after casting but before resolution") {
            d.findPermanent(opponent, "Artifact Creature") shouldBe null
        }
    }

    test("Skred cannot target a noncreature permanent") {
        val d = driver()
        val caster = d.player1
        val land = d.putLandOnBattlefield(d.player2, "Mountain")
        d.giveMana(caster, Color.RED, 1)
        val skred = d.putCardInHand(caster, "Skred")

        withClue("a land is not a legal Skred target") {
            (d.castSpellWithTargets(
                caster,
                skred,
                listOf(ChosenTarget.Permanent(land))
            ).error != null) shouldBe true
        }
    }
})
