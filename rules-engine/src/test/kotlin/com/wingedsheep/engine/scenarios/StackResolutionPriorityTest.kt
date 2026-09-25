package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CancelDecisionResponse
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PriorityChangedEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate
import com.wingedsheep.sdk.scripting.effects.CounterCondition
import com.wingedsheep.sdk.scripting.effects.CounterEffect
import com.wingedsheep.sdk.scripting.targets.TargetSpell
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Deterministic rules fixtures, not experimental games or frozen project seed allocations. */
class StackResolutionPriorityTest : FunSpec({
    val tax = card("Priority Tax Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            target("spell", TargetSpell())
            effect = CounterEffect(condition = CounterCondition.UnlessPaysMana(ManaCost.parse("{1}")))
        }
    }
    val modal = card("Priority Modal Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            modal(chooseCount = 1) {
                mode("Gain 1 life") { effect = Effects.GainLife(1) }
                mode("Draw a card") { effect = Effects.DrawCards(1) }
            }
        }
    }
    val witness = card("Priority Trigger Fixture") {
        manaCost = "{R}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 1
        keywords(Keyword.FLASH)
        triggeredAbility {
            trigger = Triggers.EntersBattlefield
            val victim = target("target", Targets.Any)
            effect = Effects.DealDamage(1, victim)
        }
    }
    val pinger = card("Priority Activated Fixture") {
        manaCost = "{R}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 1
        activatedAbility {
            cost = Costs.Mana("{R}")
            val victim = target("target", Targets.Any)
            effect = Effects.DealDamage(1, victim)
        }
    }
    val endTurn = card("Priority End Turn Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell { effect = Effects.EndTheTurn }
    }
    val legend = card("Priority Legend Fixture") {
        manaCost = "{U}"
        typeLine = "Legendary Creature — Wizard"
        power = 2
        toughness = 2
        keywords(Keyword.FLASH)
    }

    val granter = card("Priority Nested Cast Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            effect = Effects.Composite(
                GatherCardsEffect(CardSource.FromZone(Zone.GRAVEYARD,
                    filter = GameObjectFilter.InstantOrSorcery), storeAs = "nested"),
                Effects.CastFromCollectionWithoutPayingCost(from = "nested"),
                Effects.GainLife(2)
            )
        }
    }
    val castWatcher = card("Priority Cast Watcher Fixture") {
        manaCost = "{U}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        triggeredAbility {
            trigger = Triggers.youCastSpell(requires = setOf(SpellCastPredicate.CastFromZone(Zone.GRAVEYARD)))
            effect = Effects.DrawCards(1)
        }
    }
    val gainWatcher = card("Priority Gain Watcher Fixture") {
        manaCost = "{U}"
        typeLine = "Creature — Wizard"
        power = 1
        toughness = 2
        triggeredAbility {
            trigger = Triggers.YouGainLife
            effect = Effects.DrawCards(1)
        }
    }

    fun fixture(seats: Int = 2): Pair<GameTestDriver, List<EntityId>> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(tax, modal, witness, pinger, endTurn, legend, granter, castWatcher, gainWatcher))
        val decks = List(seats) { Deck.of("Island" to 40) }
        val players = driver.initMultiplayer(decks, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver to players
    }

    fun resolveOne(driver: GameTestDriver) {
        val top = driver.state.stack.last()
        repeat(driver.state.activePlayers.size) {
            if (driver.pendingDecision != null || driver.state.stack.lastOrNull() != top) return
            driver.passPriority(driver.priorityPlayer!!).error shouldBe null
        }
    }

    test("an opposing Bolt resolves before the active player legally casts Divination") {
        val (driver, players) = fixture()
        val (active, opponent) = players
        val draw = driver.putCardInHand(active, "Divination")
        val bolt = driver.putCardInHand(opponent, "Lightning Bolt")
        driver.giveMana(active, Color.BLUE, 3)
        driver.giveMana(opponent, Color.RED, 1)
        driver.passPriority(active).error shouldBe null
        driver.castSpell(opponent, bolt, listOf(active)).error shouldBe null
        driver.priorityPlayer shouldBe opponent // CR 117.3c: after casting.
        resolveOne(driver)
        driver.getLifeTotal(active) shouldBe 17
        driver.priorityPlayer shouldBe active // CR 117.3b: after resolving.
        driver.state.priorityPassedBy shouldBe emptySet()
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.events.filterIsInstance<PriorityChangedEvent>().last().playerId shouldBe active
        driver.legalActions(active).any { (it.action as? CastSpell)?.cardId == draw } shouldBe true
        driver.castSpell(active, draw).error shouldBe null
    }

    test("an opposing activated response returns priority to the active player with another spell still on stack") {
        val (driver, players) = fixture()
        val (active, opponent) = players
        val bolt = driver.putCardInHand(active, "Lightning Bolt")
        val source = driver.putPermanentOnBattlefield(opponent, pinger.name)
        driver.giveMana(active, Color.RED, 1)
        driver.giveMana(opponent, Color.RED, 1)
        driver.castSpell(active, bolt, listOf(opponent)).error shouldBe null
        driver.passPriority(active).error shouldBe null
        driver.submit(ActivateAbility(opponent, source, pinger.activatedAbilities.single().id,
            listOf(ChosenTarget.Player(active)))).error shouldBe null
        driver.priorityPlayer shouldBe opponent
        resolveOne(driver)
        driver.getLifeTotal(active) shouldBe 19
        driver.state.stack shouldBe listOf(bolt)
        driver.priorityPlayer shouldBe active
    }

    test("resolution-trigger target selection belongs to its controller but priority returns to the active player") {
        val (driver, players) = fixture()
        val (active, opponent) = players
        val creature = driver.putCardInHand(opponent, witness.name)
        driver.giveMana(opponent, Color.RED, 1)
        driver.passPriority(active).error shouldBe null
        driver.castSpell(opponent, creature).error shouldBe null
        resolveOne(driver)
        val choice = driver.pendingDecision.shouldBeInstanceOf<ChooseTargetsDecision>()
        choice.playerId shouldBe opponent
        driver.state.stackResolutionPendingPriority shouldBe true
        driver.submitTargetSelection(opponent, listOf(active)).error shouldBe null
        driver.priorityPlayer shouldBe active
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.stackSize shouldBe 1
        resolveOne(driver)
        driver.getLifeTotal(active) shouldBe 19
        driver.priorityPlayer shouldBe active
    }

    test("an opposing discard decision survives serialization and cannot seize the next priority window") {
        val (driver, players) = fixture()
        val (active, opponent) = players
        val discard = driver.putCardInHand(active, "Mind Rot")
        driver.giveMana(active, Color.BLACK, 3)
        driver.castSpell(active, discard, listOf(opponent)).error shouldBe null
        resolveOne(driver)
        val choice = driver.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        choice.playerId shouldBe opponent
        val json = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
        val serialized = json.encodeToString(driver.state)
        serialized.contains("\"stackResolutionPendingPriority\":true") shouldBe true
        val restored = json.decodeFromString<GameState>(serialized)
        restored shouldBe driver.state
        driver.replaceState(restored)
        driver.submitCardSelection(opponent, choice.options.take(2)).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.priorityPlayer shouldBe active
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.state.priorityPassedBy shouldBe emptySet()
    }

    test("an opposing legend-rule decision completes before the active player receives priority") {
        val (driver, players) = fixture()
        val (active, opponent) = players
        val older = driver.putPermanentOnBattlefield(opponent, legend.name)
        val newer = driver.putCardInHand(opponent, legend.name)
        driver.giveMana(opponent, Color.BLUE, 1)
        driver.passPriority(active).error shouldBe null
        driver.castSpell(opponent, newer).error shouldBe null
        resolveOne(driver)
        val choice = driver.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        choice.playerId shouldBe opponent
        choice.options.toSet() shouldBe setOf(older, newer)
        driver.state.stackResolutionPendingPriority shouldBe true
        driver.submitCardSelection(opponent, listOf(newer)).error shouldBe null
        driver.state.getBattlefield(opponent).contains(newer) shouldBe true
        driver.state.getGraveyard(opponent).contains(older) shouldBe true
        driver.priorityPlayer shouldBe active
        driver.state.stackResolutionPendingPriority shouldBe false
    }

    for (pay in listOf(false, true)) {
        test("four-player tax answer from a third seat returns to the active player (pay=$pay)") {
            val (driver, players) = fixture(4)
            val active = players[0]
            val countering = players[1]
            val answering = players[2]
            val bolt = driver.putCardInHand(answering, "Lightning Bolt")
            val counter = driver.putCardInHand(countering, tax.name)
            driver.giveMana(answering, Color.RED, 1)
            driver.giveMana(countering, Color.BLUE, 1)
            driver.putLandOnBattlefield(answering, "Island")
            driver.passPriority(active).error shouldBe null
            driver.passPriority(countering).error shouldBe null
            driver.castSpell(answering, bolt, listOf(active)).error shouldBe null
            driver.passPriority(answering).error shouldBe null
            driver.passPriority(players[3]).error shouldBe null
            driver.passPriority(active).error shouldBe null
            driver.castSpellWithTargets(countering, counter, listOf(ChosenTarget.Spell(bolt))).error shouldBe null
            resolveOne(driver)
            driver.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe answering
            driver.state.stackResolutionPendingPriority shouldBe true
            driver.submitYesNo(answering, pay).error shouldBe null
            if (pay) {
                driver.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().playerId shouldBe answering
                driver.state.stackResolutionPendingPriority shouldBe true
                driver.submitManaAutoPayOrDecline(answering, true).error shouldBe null
            }
            driver.pendingDecision shouldBe null
            driver.priorityPlayer shouldBe active
            driver.state.stackResolutionPendingPriority shouldBe false
            driver.state.stack shouldBe if (pay) listOf(bolt) else emptyList()
            // A fresh APNAP round begins at the active seat, irrespective of who paid.
            driver.passPriority(active).error shouldBe null
            driver.priorityPlayer shouldBe countering
            driver.passPriority(countering).error shouldBe null
            driver.priorityPlayer shouldBe answering
        }
    }

    test("a four-player cast-time modal decision retains the caster's priority instead of resetting to active") {
        val (driver, players) = fixture(4)
        val caster = players[2]
        val spell = driver.putCardInHand(caster, modal.name)
        driver.giveMana(caster, Color.BLUE, 1)
        driver.passPriority(players[0]).error shouldBe null
        driver.passPriority(players[1]).error shouldBe null
        driver.castSpell(caster, spell).error shouldBe null
        val choice = driver.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
        choice.playerId shouldBe caster
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.submitDecision(caster, OptionChosenResponse(choice.id, 0)).error shouldBe null
        driver.priorityPlayer shouldBe caster
        driver.state.stack shouldBe listOf(spell)
        resolveOne(driver)
        driver.getLifeTotal(caster) shouldBe 21
        driver.priorityPlayer shouldBe players[0]
    }

    test("a cleanup-window resolution choice restores active priority without advancing or losing another spell") {
        val (driver, players) = fixture()
        val (active, opponent) = players
        // A deterministic CR 514.3 cleanup priority-window fixture; all subsequent casts,
        // resolution passes and the legend-rule answer use public engine actions.
        driver.replaceState(driver.state.copy(phase = Phase.ENDING, step = Step.CLEANUP))
        val bolt = driver.putCardInHand(active, "Lightning Bolt")
        val older = driver.putPermanentOnBattlefield(opponent, legend.name)
        val newer = driver.putCardInHand(opponent, legend.name)
        driver.giveMana(active, Color.RED, 1)
        driver.giveMana(opponent, Color.BLUE, 1)
        driver.castSpell(active, bolt, listOf(opponent)).error shouldBe null
        driver.passPriority(active).error shouldBe null
        driver.castSpell(opponent, newer).error shouldBe null
        resolveOne(driver)
        val choice = driver.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        choice.playerId shouldBe opponent
        choice.options.toSet() shouldBe setOf(older, newer)
        driver.state.stackResolutionPendingPriority shouldBe true
        driver.submitCardSelection(opponent, listOf(newer)).error shouldBe null
        driver.state.step shouldBe Step.CLEANUP
        driver.state.activePlayerId shouldBe active
        driver.state.stack shouldBe listOf(bolt)
        driver.priorityPlayer shouldBe active
        driver.state.stackResolutionPendingPriority shouldBe false
    }

    for (cancel in listOf(false, true)) {
        test("a nested modal cast completes the outer tail and preserves each trigger batch once (cancel=$cancel)") {
            val (driver, players) = fixture()
            val (active, caster) = players
            val castObserver = driver.putPermanentOnBattlefield(caster, castWatcher.name)
            val gainObserver = driver.putPermanentOnBattlefield(caster, gainWatcher.name)
            val nested = driver.putCardInGraveyard(caster, modal.name)
            val outer = driver.putCardInHand(caster, granter.name)
            driver.giveMana(caster, Color.BLUE, 1)
            driver.passPriority(active).error shouldBe null
            driver.castSpell(caster, outer).error shouldBe null
            resolveOne(driver)
            val choice = driver.pendingDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            choice.playerId shouldBe caster
            driver.state.stackResolutionPendingPriority shouldBe true
            driver.getLifeTotal(caster) shouldBe 20
            val response = if (cancel) CancelDecisionResponse(choice.id) else OptionChosenResponse(choice.id, 0)
            driver.submitDecision(caster, response).error shouldBe null
            driver.pendingDecision shouldBe null
            driver.state.continuationStack shouldBe emptyList()
            driver.state.getGraveyard(caster).contains(outer) shouldBe true
            driver.getLifeTotal(caster) shouldBe 22 // The outer tail ran before priority.
            driver.state.stack.contains(nested) shouldBe !cancel
            driver.state.getGraveyard(caster).contains(nested) shouldBe cancel
            val triggers = driver.state.stack.mapNotNull {
                driver.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
            }
            triggers.count { it.sourceId == castObserver } shouldBe if (cancel) 0 else 1
            triggers.count { it.sourceId == gainObserver } shouldBe 1
            driver.stackSize shouldBe if (cancel) 1 else 3
            driver.priorityPlayer shouldBe active
            driver.state.stackResolutionPendingPriority shouldBe false
        }
    }

    test("a departed active player is skipped after an opposing spell resolves") {
        val (driver, players) = fixture(4)
        val bolt = driver.putCardInHand(players[2], "Lightning Bolt")
        driver.giveMana(players[2], Color.RED, 1)
        driver.passPriority(players[0]).error shouldBe null
        driver.passPriority(players[1]).error shouldBe null
        driver.castSpell(players[2], bolt, listOf(players[3])).error shouldBe null
        driver.submit(Concede(players[0])).error shouldBe null
        resolveOne(driver)
        driver.getLifeTotal(players[3]) shouldBe 17
        driver.priorityPlayer shouldBe players[1]
        driver.state.stackResolutionPendingPriority shouldBe false
    }

    test("a departing decision owner clears the abandoned resolution boundary") {
        val (driver, players) = fixture(4)
        val discard = driver.putCardInHand(players[0], "Mind Rot")
        driver.giveMana(players[0], Color.BLACK, 3)
        driver.castSpell(players[0], discard, listOf(players[2])).error shouldBe null
        resolveOne(driver)
        driver.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>().playerId shouldBe players[2]
        driver.state.stackResolutionPendingPriority shouldBe true
        driver.submit(Concede(players[2])).error shouldBe null
        driver.pendingDecision shouldBe null
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.priorityPlayer shouldBe players[0]
    }

    test("ending the turn clears the resolution boundary before the next step's priority") {
        val (driver, players) = fixture()
        val spell = driver.putCardInHand(players[1], endTurn.name)
        driver.giveMana(players[1], Color.BLUE, 1)
        driver.passPriority(players[0]).error shouldBe null
        driver.castSpell(players[1], spell).error shouldBe null
        resolveOne(driver)
        driver.state.stackResolutionPendingPriority shouldBe false
    }

    test("a terminal resolution clears its boundary and grants no further priority") {
        val (driver, players) = fixture()
        val bolt = driver.putCardInHand(players[1], "Lightning Bolt")
        driver.giveMana(players[1], Color.RED, 1)
        driver.setLifeTotal(players[0], 3)
        driver.passPriority(players[0]).error shouldBe null
        driver.castSpell(players[1], bolt, listOf(players[0])).error shouldBe null
        resolveOne(driver)
        driver.state.gameOver shouldBe true
        driver.state.stackResolutionPendingPriority shouldBe false
        driver.priorityPlayer shouldBe null
    }

    test("the false marker is absent from ordinary serialized states even with defaults enabled") {
        val json = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
        val encoded = json.encodeToString(GameState())
        encoded.contains("stackResolutionPendingPriority") shouldBe false
        json.decodeFromString<GameState>(encoded).stackResolutionPendingPriority shouldBe false
    }

    test("a rejected malformed stack resolution cannot publish a marker or mutate the attempted pass") {
        val (driver, players) = fixture()
        val malformed = driver.putCardInHand(players[0], "Island")
        // A negative imported-state fixture: the listed stack entity has no spell/ability
        // component. This never represents a legal sampled game action or a synthetic result.
        driver.replaceState(driver.state.copy(stack = listOf(malformed)))
        driver.passPriority(players[0]).error shouldBe null
        val before = driver.state
        val rejected = driver.passPriority(players[1])
        rejected.error shouldBe "Unknown stack item type"
        rejected.state shouldBe before
        rejected.events shouldBe emptyList()
        driver.state shouldBe before
        driver.state.stackResolutionPendingPriority shouldBe false
    }
})
