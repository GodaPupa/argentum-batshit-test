package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.gpt.cards.IzzetGuildmage
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IzzetGuildmageScenarioTest : FunSpec({

    val copyInstantAbility = IzzetGuildmage.activatedAbilities[0].id
    val copySorceryAbility = IzzetGuildmage.activatedAbilities[1].id

    val cheapSorcery = CardDefinition(
        name = "Test Cheap Sorcery",
        manaCost = ManaCost.parse("{R}"),
        typeLine = TypeLine.parse("Sorcery"),
        oracleText = "You gain 2 life.",
        script = CardScript.spell(effect = Effects.GainLife(2))
    )

    val expensiveInstant = CardDefinition(
        name = "Test Expensive Instant",
        manaCost = ManaCost.parse("{2}{U}"),
        typeLine = TypeLine.parse("Instant"),
        oracleText = "You gain 1 life.",
        script = CardScript.spell(effect = Effects.GainLife(1))
    )

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(IzzetGuildmage, cheapSorcery, expensiveInstant))
        d.initMirrorMatch(deck = Deck.of("Island" to 20, "Mountain" to 20), startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    test("blue ability copies a cheap instant you control and may retarget the copy") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        val guildmage = d.putCreatureOnBattlefield(you, "Izzet Guildmage")

        d.giveMana(you, Color.RED, 1)
        val bolt = d.putCardInHand(you, "Lightning Bolt")
        d.castSpell(you, bolt, listOf(opponent)).isSuccess shouldBe true

        d.giveMana(you, Color.BLUE, 3)
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copyInstantAbility,
                targets = listOf(ChosenTarget.Spell(bolt))
            )
        ).isSuccess shouldBe true

        d.bothPass()
        (d.pendingDecision is ChooseTargetsDecision) shouldBe true
        d.submitTargetSelection(you, listOf(you)).isSuccess shouldBe true

        d.bothPass()
        d.bothPass()

        d.getLifeTotal(you) shouldBe 17
        d.getLifeTotal(opponent) shouldBe 17
    }

    test("red ability copies a cheap targetless sorcery you control") {
        val d = driver()
        val you = d.activePlayer!!
        val guildmage = d.putCreatureOnBattlefield(you, "Izzet Guildmage")

        d.giveMana(you, Color.RED, 1)
        val sorcery = d.putCardInHand(you, "Test Cheap Sorcery")
        d.castSpell(you, sorcery).isSuccess shouldBe true

        d.giveMana(you, Color.RED, 3)
        d.submit(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copySorceryAbility,
                targets = listOf(ChosenTarget.Spell(sorcery))
            )
        ).isSuccess shouldBe true

        d.bothPass()
        d.bothPass()
        d.bothPass()

        d.getLifeTotal(you) shouldBe 24
    }

    test("blue ability rejects a sorcery even when its mana value is small enough") {
        val d = driver()
        val you = d.activePlayer!!
        val guildmage = d.putCreatureOnBattlefield(you, "Izzet Guildmage")

        d.giveMana(you, Color.RED, 1)
        val sorcery = d.putCardInHand(you, "Test Cheap Sorcery")
        d.castSpell(you, sorcery).isSuccess shouldBe true

        d.giveMana(you, Color.BLUE, 3)
        d.submitExpectFailure(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copyInstantAbility,
                targets = listOf(ChosenTarget.Spell(sorcery))
            )
        )
    }

    test("blue ability rejects an instant with mana value three") {
        val d = driver()
        val you = d.activePlayer!!
        val guildmage = d.putCreatureOnBattlefield(you, "Izzet Guildmage")

        d.giveMana(you, Color.BLUE, 3)
        val instant = d.putCardInHand(you, "Test Expensive Instant")
        d.castSpell(you, instant).isSuccess shouldBe true

        d.giveMana(you, Color.BLUE, 3)
        d.submitExpectFailure(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copyInstantAbility,
                targets = listOf(ChosenTarget.Spell(instant))
            )
        )
    }

    test("blue ability rejects a qualifying instant controlled by the opponent") {
        val d = driver()
        val you = d.activePlayer!!
        val opponent = d.getOpponent(you)
        val guildmage = d.putCreatureOnBattlefield(you, "Izzet Guildmage")

        d.passPriority(you)
        d.giveMana(opponent, Color.RED, 1)
        val bolt = d.putCardInHand(opponent, "Lightning Bolt")
        d.castSpell(opponent, bolt, listOf(you)).isSuccess shouldBe true
        d.passPriority(opponent)

        d.giveMana(you, Color.BLUE, 3)
        d.submitExpectFailure(
            ActivateAbility(
                playerId = you,
                sourceId = guildmage,
                abilityId = copyInstantAbility,
                targets = listOf(ChosenTarget.Spell(bolt))
            )
        )
    }
})
