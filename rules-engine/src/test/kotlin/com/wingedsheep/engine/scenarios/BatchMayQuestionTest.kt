package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.MayEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Separately resolving optional triggers cannot share a pre-stack consent answer.
 * CR 603.3d and 603.5 verified against the official 2026-09-25 rules archive.
 * The original five tests encoded the incorrect early-consent behavior; exact old bytes
 * are retained under ferocity-recycling/evidence/source-boundaries/before-optional-target-timing/.
 * Targets are chosen on placement and each effect's may choice occurs on its resolution.
 * These fixed rules scenarios are not randomized matchup games.
 */
class BatchMayQuestionTest : FunSpec({

    val batchPinger = card("Batch Pinger") {
        manaCost = "{1}"
        typeLine = "Creature — Test"
        power = 1
        toughness = 1
        oracleText = "Whenever another creature you control enters the battlefield, you may have " +
            "Batch Pinger deal 1 damage to any target."
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            val t = target("target", Targets.Any)
            effect = MayEffect(Effects.DealDamage(1, t))
        }
    }

    // Vanilla creature whose entry fires every Batch Pinger's "another creature enters" trigger.
    val batchBear = card("Batch Bear") {
        manaCost = "{1}"
        typeLine = "Creature — Test"
        power = 2
        toughness = 2
    }

    val responseBolt = card("Optional Timing Response Fixture") {
        manaCost = "{R}"
        typeLine = "Instant"
        spell {
            val t = target("target creature", Targets.Creature)
            effect = Effects.DealDamage(3, t)
        }
    }

    fun driverWithPingers(count: Int): Triple<GameTestDriver, EntityId, EntityId> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(batchPinger, batchBear, responseBolt))
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.replaceState(driver.state.copy(rng = GameRng.seeded(0xFE000007)))

        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        repeat(count) { driver.putCreatureOnBattlefield(player, "Batch Pinger") }

        driver.giveColorlessMana(player, 1)
        val bear = driver.putCardInHand(player, "Batch Bear")
        driver.castSpell(player, bear).isSuccess shouldBe true
        driver.bothPass() // resolve the bear; it enters and every Batch Pinger triggers
        return Triple(driver, player, opponent)
    }

    // Used explicitly by the four two-trigger cases; no generic decision auto-answering.
    fun orderTwoPings(driver: GameTestDriver, player: EntityId) {
        val ordering = driver.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        ordering.playerId shouldBe player
        ordering.options.size shouldBe 2
        ordering.options.all { it.contains("Batch Pinger") } shouldBe true
        driver.submitDecision(player, OptionChosenResponse(ordering.id, 0)).error shouldBe null
        driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
    }

    fun chooseAllTargets(driver: GameTestDriver, count: Int, target: EntityId) {
        repeat(count) {
            val question = driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
            driver.submitTargetSelection(question.playerId, listOf(target)).error shouldBe null
        }
        driver.pendingDecision shouldBe null
    }

    fun stackedPings(driver: GameTestDriver) = driver.state.stack.mapNotNull {
        driver.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
    }.count { it.sourceName == "Batch Pinger" }

    fun resolveChoice(driver: GameTestDriver, player: EntityId, answer: Boolean) {
        driver.bothPass()
        driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe player
        driver.submitYesNo(player, answer).error shouldBe null
    }

    test("two optional triggers choose targets before either consent decision") {
        val (driver, player, opponent) = driverWithPingers(2)
        orderTwoPings(driver, player)
        driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        chooseAllTargets(driver, 2, opponent)
        stackedPings(driver) shouldBe 2
        driver.assertLifeTotal(opponent, 20)
    }

    test("each targeted trigger asks at its own resolution and both may be accepted") {
        val (driver, player, opponent) = driverWithPingers(2)
        orderTwoPings(driver, player)
        chooseAllTargets(driver, 2, opponent)
        resolveChoice(driver, player, true)
        stackedPings(driver) shouldBe 1
        driver.assertLifeTotal(opponent, 19)
        resolveChoice(driver, player, true)
        stackedPings(driver) shouldBe 0
        driver.assertLifeTotal(opponent, 18)
    }

    test("declining both effects still requires both triggers to resolve") {
        val (driver, player, opponent) = driverWithPingers(2)
        orderTwoPings(driver, player)
        chooseAllTargets(driver, 2, opponent)
        stackedPings(driver) shouldBe 2
        resolveChoice(driver, player, false)
        stackedPings(driver) shouldBe 1
        resolveChoice(driver, player, false)
        stackedPings(driver) shouldBe 0
        driver.assertLifeTotal(opponent, 20)
    }

    test("identical triggers retain independent resolution choices") {
        val (driver, player, opponent) = driverWithPingers(2)
        orderTwoPings(driver, player)
        chooseAllTargets(driver, 2, opponent)
        resolveChoice(driver, player, false)
        driver.assertLifeTotal(opponent, 20)
        resolveChoice(driver, player, true)
        driver.assertLifeTotal(opponent, 19)
    }

    test("a single optional trigger chooses its target before offering yes or no") {
        val (driver, player, opponent) = driverWithPingers(1)
        driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        chooseAllTargets(driver, 1, opponent)
        stackedPings(driver) shouldBe 1
        resolveChoice(driver, player, false)
        driver.assertLifeTotal(opponent, 20)
    }

    test("may does not make a required target optional") {
        val (driver, player, opponent) = driverWithPingers(1)
        driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        val before = driver.state
        driver.submitTargetSelection(player, emptyList()).error.shouldNotBeNull()
        driver.state shouldBe before
        chooseAllTargets(driver, 1, opponent)
        resolveChoice(driver, player, true)
        driver.assertLifeTotal(opponent, 19)
    }

    test("removing the chosen target in response causes a fizzle without a may decision") {
        val (driver, player, opponent) = driverWithPingers(1)
        val target = driver.state.getBattlefield().single {
            driver.state.getEntity(it)?.get<CardComponent>()?.name == "Batch Bear"
        }
        chooseAllTargets(driver, 1, target)
        driver.giveMana(opponent, Color.RED, 1)
        val bolt = driver.putCardInHand(opponent, responseBolt.name)
        driver.passPriority(player).error shouldBe null
        driver.castSpell(opponent, bolt, listOf(target)).error shouldBe null
        driver.bothPass()
        driver.pendingDecision shouldBe null
        stackedPings(driver) shouldBe 1
        driver.bothPass()
        driver.pendingDecision shouldBe null
        stackedPings(driver) shouldBe 0
        driver.assertLifeTotal(opponent, 20)
    }
})
