package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dka.cards.ArtfulDodge
import com.wingedsheep.mtg.sets.definitions.dka.cards.ThoughtScour
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase

class TerrorSimpleSupportCardsScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(ThoughtScour, ArtfulDodge))
        driver.initMirrorMatch(
            deck = Deck.of(
                "Mountain" to 20,
                "Grizzly Bears" to 20,
            ),
            skipMulligans = true,
        )
        return driver
    }

    fun GameTestDriver.advanceToPlayer1DeclareAttackers() {
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        var safety = 0
        while (activePlayer != player1 && safety < 50) {
            bothPass()
            passPriorityUntil(Step.DECLARE_ATTACKERS)
            safety++
        }
    }

    test("Thought Scour mills the chosen player two and draws for the caster") {
        val driver = createDriver()
        val caster = driver.player1
        val targetPlayer = driver.player2
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val milledA = driver.putCardOnTopOfLibrary(targetPlayer, "Grizzly Bears")
        val milledB = driver.putCardOnTopOfLibrary(targetPlayer, "Mountain")
        val handBefore = driver.getHandSize(caster)

        val scour = driver.putCardInHand(caster, "Thought Scour")
        driver.giveMana(caster, Color.BLUE)
        driver.castSpell(caster, scour, listOf(targetPlayer)).error shouldBe null
        driver.bothPass()

        withClue("both specifically prepared top cards must be in the targeted player's graveyard") {
            driver.getGraveyard(targetPlayer).containsAll(listOf(milledA, milledB)) shouldBe true
        }
        withClue("the caster draws exactly one card after spending Thought Scour itself") {
            driver.getHandSize(caster) shouldBe handBefore + 1
        }
        withClue("Thought Scour itself resolves to the caster's graveyard") {
            driver.getGraveyard(caster).contains(scour) shouldBe true
        }
    }

    test("Artful Dodge grants can't-be-blocked for the turn and carries flashback U") {
        val driver = createDriver()
        val attacker = driver.putCreatureOnBattlefield(driver.player1, "Grizzly Bears")
        val blocker = driver.putCreatureOnBattlefield(driver.player2, "Grizzly Bears")
        driver.removeSummoningSickness(attacker)
        driver.removeSummoningSickness(blocker)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val dodge = driver.putCardInHand(driver.player1, "Artful Dodge")
        driver.giveMana(driver.player1, Color.BLUE)
        driver.castSpell(driver.player1, dodge, listOf(attacker)).error shouldBe null
        driver.bothPass()

        withClue("the targeted creature receives the temporary unblockable flag") {
            driver.state.projectedState.hasKeyword(attacker, AbilityFlag.CANT_BE_BLOCKED) shouldBe true
        }

        driver.advanceToPlayer1DeclareAttackers()
        driver.declareAttackers(driver.player1, listOf(attacker), driver.player2).isSuccess shouldBe true
        driver.bothPass()
        driver.currentStep shouldBe Step.DECLARE_BLOCKERS

        val blockResult = driver.submitExpectFailure(
            DeclareBlockers(driver.player2, mapOf(blocker to listOf(attacker)))
        )
        blockResult.isSuccess shouldBe false
        blockResult.error shouldContainIgnoringCase "blocked"

        val flashback = ArtfulDodge.keywordAbilities.filterIsInstance<KeywordAbility.Flashback>().single()
        flashback.description shouldBe "Flashback {U}"
    }
})
