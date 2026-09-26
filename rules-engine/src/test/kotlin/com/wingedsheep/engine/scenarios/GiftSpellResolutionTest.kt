package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.gift
import com.wingedsheep.sdk.dsl.splice
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.scripting.GiftKind
import com.wingedsheep.sdk.scripting.effects.MayEffect
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Generic spell-Gift boundary cases. Canonical Brew has its own dedicated scenario file. */
class GiftSpellResolutionTest : ScenarioTestBase() {
    private val onlyGift = card("Only Gift Fixture") {
        manaCost = "{U}"
        typeLine = "Sorcery"
        gift(GiftKind.FOOD)
    }
    private val foodAndDraw = card("Gift And Draw Fixture") {
        manaCost = "{U}"
        typeLine = "Instant — Arcane"
        gift(GiftKind.FOOD)
        spell { effect = Effects.DrawCards(1) }
    }
    private val spliceDraw = card("Splice Draw Fixture") {
        manaCost = "{U}"
        typeLine = "Instant — Arcane"
        splice("{1}{U}")
        spell { effect = Effects.DrawCards(1) }
    }
    private val foodAndPause = card("Gift And Pause Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        gift(GiftKind.FOOD)
        spell { effect = MayEffect(Effects.DrawCards(1)) }
    }
    private val costShape = card("Gift Conditional Cost Fixture") {
        manaCost = "{7}{U}"
        typeLine = "Instant"
        gift(GiftKind.FOOD)
        additionalCost(Costs.additional.DiscardCards())
        spell {
            val player = target("player", Targets.Player)
            effect = Effects.DrawCards(1, player)
            val giftedPlayer = giftTarget("player", Targets.Player)
            val creature = giftTarget("creature", Targets.CreatureYouControl)
            giftEffect = Effects.Composite(Effects.DrawCards(1, giftedPlayer), Effects.ModifyStats(1, 0, creature))
        }
    }
    private val unsupported = card("Unsupported Gift Shape Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        gift(GiftKind.FOOD)
        spell {
            val player = target("player", Targets.Player)
            effect = Effects.DrawCards(1, player)
            val creature = giftTarget("creature", Targets.Creature)
            giftEffect = Effects.ModifyStats(1, 1, creature)
        }
    }
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }

    private fun setup(name: String) = scenario().withPlayers("Giver", "Recipient")
        .withCardInHand(1, name).withLandsOnBattlefield(1, "Island", 1)
        .apply { repeat(6) { withCardInLibrary(1, "Island"); withCardInLibrary(2, "Forest") } }
        .withRngSeed(0xFEC602).withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

    private fun food(state: GameState, controller: EntityId) = state.getBattlefield().filter {
        state.projectedState.getController(it) == controller &&
            state.getEntity(it)?.get<CardComponent>()?.typeLine?.subtypes?.any { subtype -> subtype.value == "Food" } == true
    }

    private fun TestGame.finish(): List<GameEvent> {
        val results = resolveStack()
        results.forEach { it.error shouldBe null }
        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        state.gameOver shouldBe false
        return results.flatMap { it.events }
    }

    private fun multiplayer(): Pair<GameTestDriver, List<EntityId>> {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + PredefinedTokens.allTokens + listOf(foodAndDraw))
        // Initialization contains only identical Islands. Subsequent engine RNG is fixed;
        // this is a mechanics fixture, not a shuffled-deck game sample.
        val players = driver.initMultiplayer(List(3) { Deck.of("Island" to 40) })
        driver.replaceState(driver.state.copy(rng = GameRng(0xFEC603)))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver to players
    }

    private fun finish(driver: GameTestDriver) {
        var passes = 0
        while (driver.state.stack.isNotEmpty() && driver.state.pendingDecision == null && passes++ < 30) {
            driver.submitSuccess(PassPriority(driver.priorityPlayer!!))
        }
        driver.state.stack shouldBe emptyList()
        driver.state.pendingDecision shouldBe null
        driver.state.gameOver shouldBe false
    }

    init {
        cardRegistry.register(listOf(onlyGift, foodAndDraw, spliceDraw, foodAndPause, costShape, unsupported))

        test("a gift-only spell gives its promised gift without requiring another main effect") {
            val game = setup(onlyGift.name).build()
            game.execute(CastSpell(game.player1Id, game.findCardsInHand(1, onlyGift.name).single(),
                giftRecipient = game.player2Id)).error shouldBe null
            val events = game.finish()
            food(game.state, game.player2Id).size shouldBe 1
            food(game.state, game.player1Id) shouldBe emptyList()
            events.filterIsInstance<GiftGivenEvent>().size shouldBe 1
        }

        test("give-gift occurs after the entire spell including its spliced text") {
            val game = setup(foodAndDraw.name).withCardInHand(1, spliceDraw.name)
                .withLandsOnBattlefield(1, "Island", 2).build()
            val source = game.findCardsInHand(1, foodAndDraw.name).single()
            val spliced = game.findCardsInHand(1, spliceDraw.name).single()
            val offered = game.getLegalActions(1).single {
                it.actionType == "CastWithGift" && (it.action as? CastSpell)?.cardId == source &&
                    (it.action as CastSpell).splicedCardIds == listOf(spliced)
            }
            game.execute(offered.action).error shouldBe null
            val events = game.finish()
            game.findCardsInHand(1, spliceDraw.name) shouldBe listOf(spliced)
            game.handSize(1) shouldBe 3
            food(game.state, game.player2Id).size shouldBe 1
            events.filterIsInstance<CardsDrawnEvent>().sumOf { it.count } shouldBe 2
            events.filterIsInstance<GiftGivenEvent>().size shouldBe 1
            (events.indexOfLast { it is CardsDrawnEvent } < events.indexOfFirst { it is GiftGivenEvent }) shouldBe true
        }

        test("a pause after the gift survives JSON without repeating the gift or giving it early") {
            val game = setup(foodAndPause.name).build()
            game.execute(CastSpell(game.player1Id, game.findCardsInHand(1, foodAndPause.name).single(),
                giftRecipient = game.player2Id)).error shouldBe null
            val beforePause = game.resolveStack()
            beforePause.forEach { it.error shouldBe null }
            game.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            food(game.state, game.player2Id).size shouldBe 1
            beforePause.flatMap { it.events }.filterIsInstance<GiftGivenEvent>() shouldBe emptyList()
            game.state = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), game.state))
            val resumed = game.answerYesNo(true)
            resumed.error shouldBe null
            val events = resumed.events + game.finish()
            game.handSize(1) shouldBe 1
            food(game.state, game.player2Id).size shouldBe 1
            events.filterIsInstance<GiftGivenEvent>().size shouldBe 1
        }

        test("an opposing controller's copy keeps the original promised recipient even when that is itself") {
            val game = setup(foodAndDraw.name).build()
            val source = game.findCardsInHand(1, foodAndDraw.name).single()
            game.execute(CastSpell(game.player1Id, source, giftRecipient = game.player2Id)).error shouldBe null
            val copied = EngineServices(cardRegistry).stackResolver.putSpellCopy(
                game.state, source, controllerId = game.player2Id)
            copied.error shouldBe null
            game.state = copied.state
            val copy = game.state.getEntity(game.state.stack.last())!!.get<SpellOnStackComponent>()!!
            copy.casterId shouldBe game.player2Id
            copy.giftRecipient shouldBe game.player2Id
            val events = game.finish()
            food(game.state, game.player2Id).size shouldBe 2
            food(game.state, game.player1Id) shouldBe emptyList()
            game.handSize(1) shouldBe 1
            game.handSize(2) shouldBe 1
            events.filterIsInstance<GiftGivenEvent>().map { it.controllerId } shouldBe listOf(game.player2Id, game.player1Id)
        }

        test("a free Gift cast still selects conditional targets and pays the discard") {
            val game = setup(costShape.name).withCardOnBattlefield(1, "Omniscience")
                .withCardOnBattlefield(1, "Grizzly Bears").withCardInHand(1, "Forest").build()
            val source = game.findCardsInHand(1, costShape.name).single()
            val gifted = game.getLegalActions(1).single {
                it.actionType == "CastWithGift" && (it.action as? CastSpell)?.cardId == source &&
                    (it.action as CastSpell).useWithoutPayingManaCost
            }
            gifted.targetRequirements.shouldNotBeNull().size shouldBe 2
            val bears = game.findPermanent("Grizzly Bears")!!
            val discard = game.findCardsInHand(1, "Forest").single()
            val action = (gifted.action as CastSpell).copy(
                targets = listOf(ChosenTarget.Player(game.player1Id), ChosenTarget.Permanent(bears)),
                additionalCostPayment = AdditionalCostPayment(discardedCards = listOf(discard)))
            game.execute(action).error shouldBe null
            game.isInGraveyard(1, "Forest") shouldBe true
            game.finish()
            game.handSize(1) shouldBe 1
            food(game.state, game.player2Id).size shouldBe 1
            game.state.projectedState.getPower(bears) shouldBe 3
        }

        test("multiplayer locks the chosen opponent while casting without a resolution recipient prompt") {
            val (driver, players) = multiplayer()
            val caster = players[0]
            val source = driver.putCardInHand(caster, foodAndDraw.name)
            driver.giveMana(caster, Color.BLUE, 1)
            val offers = driver.legalActions(caster).filter {
                it.actionType == "CastWithGift" && (it.action as? CastSpell)?.cardId == source
            }
            offers.map { (it.action as CastSpell).giftRecipient }.toSet() shouldBe setOf(players[1], players[2])
            driver.submitSuccess(offers.single { (it.action as CastSpell).giftRecipient == players[2] }.action)
            driver.state.getEntity(source)!!.get<SpellOnStackComponent>()!!.giftRecipient shouldBe players[2]
            finish(driver)
            food(driver.state, players[1]) shouldBe emptyList()
            food(driver.state, players[2]).size shouldBe 1
        }

        test("a promised recipient leaving a multiplayer game does not redirect its gift to the caster") {
            val (driver, players) = multiplayer()
            val caster = players[0]
            val source = driver.putCardInHand(caster, foodAndDraw.name)
            driver.giveMana(caster, Color.BLUE, 1)
            val handBefore = driver.getHandSize(caster)
            driver.submitSuccess(CastSpell(caster, source, giftRecipient = players[2]))
            driver.submitSuccess(Concede(players[2]))
            driver.state.activePlayers.contains(players[2]) shouldBe false
            finish(driver)
            food(driver.state, caster) shouldBe emptyList()
            food(driver.state, players[1]) shouldBe emptyList()
            driver.getHandSize(caster) shouldBe handBefore
            driver.events.filterIsInstance<GiftGivenEvent>().count { it.sourceId == source } shouldBe 1
        }

        test("unsupported replacement target shapes fail visibly in both action and menu paths") {
            val game = setup(unsupported.name).withCardOnBattlefield(1, "Grizzly Bears").build()
            val source = game.findCardsInHand(1, unsupported.name).single()
            val before = game.state
            val rejected = game.execute(CastSpell(game.player1Id, source,
                listOf(ChosenTarget.Permanent(game.findPermanent("Grizzly Bears")!!)), giftRecipient = game.player2Id))
            rejected.error.shouldNotBeNull()
            rejected.state shouldBe before
            shouldThrow<IllegalStateException> { game.getLegalActions(1) }
        }
    }
}
