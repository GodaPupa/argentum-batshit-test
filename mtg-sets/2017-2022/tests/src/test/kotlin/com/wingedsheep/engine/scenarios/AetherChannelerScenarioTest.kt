package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

// This receiving GameTestDriver predates its seeded init convenience overload. Use the real
// initializer and the fixed two-seat state directly; no unseeded setup or shared helper change.
private val GameTestDriver.firstSeat: EntityId get() = state.turnOrder[0]
private val GameTestDriver.secondSeat: EntityId get() = state.turnOrder[1]

/** Exact card interactions on excluded fixed fixtures; every submitted action replays from serialized state. */
class AetherChannelerScenarioTest : FunSpec({
    val birdMode = "Create a 1/1 white Bird creature token with flying."
    val bounceMode = "Return another target nonland permanent to its owner's hand."
    val drawMode = "Draw a card."
    fun game() = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        registerCards(PredefinedTokens.allTokens)
        val deck = Deck.of("Forest" to 40)
        val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Fixture 1", deck), PlayerConfig("Fixture 2", deck)),
            skipMulligans = true, startingPlayerIndex = 0, seed = 0x414554484552L,
        ))
        replaceState(initialized.state)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
        repeat(3) { putPermanentOnBattlefield(firstSeat, "Island") }
        putPermanentOnBattlefield(secondSeat, "Island")
    }
    fun submit(driver: GameTestDriver, action: GameAction): ExecutionResult {
        val before = SerializationTestSupport.roundTrip(driver.state)
        val actual = driver.submit(action)
        val replay = ActionProcessor(driver.cardRegistry).process(before, action).result
        replay.error shouldBe actual.error
        SerializationTestSupport.encodeState(replay.state) shouldBe SerializationTestSupport.encodeState(actual.state)
        SerializationTestSupport.encodeEvents(replay.events) shouldBe SerializationTestSupport.encodeEvents(actual.events)
        return actual
    }
    fun resolveUntilDecision(driver: GameTestDriver) {
        var actions = 0
        while (driver.state.stack.isNotEmpty() && driver.pendingDecision == null) {
            check(actions++ < 40)
            submit(driver, PassPriority(driver.state.priorityPlayerId ?: error("Stack without priority"))).error shouldBe null
        }
    }
    fun enter(driver: GameTestDriver): EntityId {
        val source = driver.putCardInHand(driver.firstSeat, "Aether Channeler")
        submit(driver, CastSpell(driver.firstSeat, source)).error shouldBe null
        resolveUntilDecision(driver)
        driver.pendingDecision shouldNotBe null
        (driver.pendingDecision is ChooseOptionDecision) shouldBe true
        return source
    }
    fun choose(driver: GameTestDriver, mode: String) {
        val decision = driver.pendingDecision as ChooseOptionDecision
        val index = decision.options.indexOf(mode)
        check(index >= 0) { "Mode not offered: $mode / ${decision.options}" }
        submit(driver, SubmitDecision(decision.playerId, OptionChosenResponse(decision.id, index))).error shouldBe null
    }
    fun target(driver: GameTestDriver, target: EntityId) {
        val decision = driver.pendingDecision as ChooseTargetsDecision
        submit(driver, SubmitDecision(decision.playerId, TargetsResponse(decision.id, mapOf(0 to listOf(target))))).error shouldBe null
    }
    fun birds(driver: GameTestDriver) = driver.state.projectedState.getBattlefieldControlledBy(driver.firstSeat)
        .filter { driver.state.getEntity(it)?.has<TokenComponent>() == true && driver.state.projectedState.hasSubtype(it, "Bird") }

    test("Bird mode creates exactly a white flying 1-1 and does not draw") {
        val driver = game()
        enter(driver)
        val library = driver.state.getLibrary(driver.firstSeat)
        choose(driver, birdMode)
        resolveUntilDecision(driver)
        val bird = birds(driver).single()
        // CR 111.4: an unnamed token's name is its subtype(s) plus "Token".
        driver.state.getEntity(bird)?.get<CardComponent>()?.name shouldBe "Bird Token"
        driver.state.getEntity(bird)?.get<CardComponent>()?.ownerId shouldBe driver.firstSeat
        driver.state.projectedState.getPower(bird) shouldBe 1
        driver.state.projectedState.getToughness(bird) shouldBe 1
        driver.state.projectedState.getColors(bird) shouldBe setOf("WHITE")
        driver.state.projectedState.hasKeyword(bird, Keyword.FLYING) shouldBe true
        driver.state.getLibrary(driver.firstSeat) shouldBe library
        driver.state.getHand(driver.firstSeat) shouldBe emptyList()
    }
    test("draw mode moves exactly one real library card into the controller's hand") {
        val driver = game()
        enter(driver)
        val library = driver.state.getLibrary(driver.firstSeat)
        choose(driver, drawMode)
        resolveUntilDecision(driver)
        driver.state.getHand(driver.firstSeat) shouldBe listOf(library.first())
        driver.state.getLibrary(driver.firstSeat) shouldBe library.drop(1)
        driver.state.getHand(driver.secondSeat) shouldBe emptyList()
        birds(driver) shouldBe emptyList()
    }
    test("bounce mode returns an opposing creature and no other mode benefit") {
        val driver = game()
        val bear = driver.putPermanentOnBattlefield(driver.secondSeat, "Grizzly Bears")
        enter(driver)
        choose(driver, bounceMode)
        target(driver, bear)
        driver.pendingDecision shouldBe null
        driver.state.stack.size shouldBe 1
        (bear in driver.state.projectedState.getBattlefieldControlledBy(driver.secondSeat)) shouldBe true
        resolveUntilDecision(driver)
        driver.state.getHand(driver.secondSeat) shouldBe listOf(bear)
        driver.state.getHand(driver.firstSeat) shouldBe emptyList()
        birds(driver) shouldBe emptyList()
    }
    test("bounce can target the controller's noncreature artifact") {
        val driver = game()
        val artifact = driver.putPermanentOnBattlefield(driver.firstSeat, "Sol Ring")
        enter(driver)
        choose(driver, bounceMode)
        target(driver, artifact)
        resolveUntilDecision(driver)
        driver.state.getHand(driver.firstSeat) shouldBe listOf(artifact)
    }
    test("another excludes only this source and every land, with illegal responses rejected unchanged") {
        val driver = game()
        val other = driver.putPermanentOnBattlefield(driver.firstSeat, "Aether Channeler")
        val land = driver.state.projectedState.getBattlefieldControlledBy(driver.firstSeat).first {
            driver.state.projectedState.hasType(it, "LAND")
        }
        val source = enter(driver)
        choose(driver, bounceMode)
        val decision = driver.pendingDecision as ChooseTargetsDecision
        (other in decision.legalTargets.getValue(0)) shouldBe true
        (source in decision.legalTargets.getValue(0)) shouldBe false
        (land in decision.legalTargets.getValue(0)) shouldBe false
        val before = SerializationTestSupport.encodeState(driver.state)
        for (invalid in listOf(source, land)) {
            submit(driver, SubmitDecision(decision.playerId, TargetsResponse(decision.id, mapOf(0 to listOf(invalid))))).error shouldNotBe null
            SerializationTestSupport.encodeState(driver.state) shouldBe before
        }
        target(driver, other)
        resolveUntilDecision(driver)
        driver.state.getHand(driver.firstSeat) shouldBe listOf(other)
        (source in driver.state.projectedState.getBattlefieldControlledBy(driver.firstSeat)) shouldBe true
    }
    test("when only lands and the source exist, the illegal bounce mode is not offered") {
        val driver = game()
        enter(driver)
        (driver.pendingDecision as ChooseOptionDecision).options shouldBe listOf(birdMode, drawMode)
        choose(driver, drawMode)
        resolveUntilDecision(driver)
        driver.state.getHand(driver.firstSeat).size shouldBe 1
    }
    test("bounce returns a controlled opposing card to its owner rather than its controller") {
        val driver = game()
        val bear = driver.putPermanentOnBattlefield(driver.secondSeat, "Grizzly Bears")
        driver.replaceState(driver.state.updateEntity(bear) { it.with(ControllerComponent(driver.firstSeat)) })
        (bear in driver.state.projectedState.getBattlefieldControlledBy(driver.firstSeat)) shouldBe true
        enter(driver)
        choose(driver, bounceMode)
        target(driver, bear)
        resolveUntilDecision(driver)
        driver.state.getHand(driver.secondSeat) shouldBe listOf(bear)
        driver.state.getHand(driver.firstSeat) shouldBe emptyList()
    }
    test("an opponent cannot act between the trigger's mode and target announcements") {
        val driver = game()
        val bear = driver.putPermanentOnBattlefield(driver.secondSeat, "Grizzly Bears")
        enter(driver)
        val beforeMode = SerializationTestSupport.encodeState(driver.state)
        submit(driver, PassPriority(driver.secondSeat)).error shouldNotBe null
        SerializationTestSupport.encodeState(driver.state) shouldBe beforeMode
        choose(driver, bounceMode)
        val beforeTarget = SerializationTestSupport.encodeState(driver.state)
        submit(driver, PassPriority(driver.secondSeat)).error shouldNotBe null
        SerializationTestSupport.encodeState(driver.state) shouldBe beforeTarget
        target(driver, bear)
        driver.pendingDecision shouldBe null
        driver.state.stack.size shouldBe 1
        driver.priorityPlayer shouldBe driver.firstSeat
        submit(driver, PassPriority(driver.firstSeat)).error shouldBe null
        driver.priorityPlayer shouldBe driver.secondSeat
    }
    test("removing Channeler in response preserves its already targeted bounce trigger") {
        val driver = game()
        val bear = driver.putPermanentOnBattlefield(driver.secondSeat, "Grizzly Bears")
        val unsummon = driver.putCardInHand(driver.secondSeat, "Unsummon")
        val source = enter(driver)
        choose(driver, bounceMode)
        target(driver, bear)
        submit(driver, PassPriority(driver.firstSeat)).error shouldBe null
        submit(driver, CastSpell(driver.secondSeat, unsummon, targets = listOf(ChosenTarget.Permanent(source)))).error shouldBe null
        resolveUntilDecision(driver)
        driver.state.getHand(driver.firstSeat) shouldBe listOf(source)
        driver.state.getHand(driver.secondSeat) shouldBe listOf(bear)
        birds(driver) shouldBe emptyList()
    }
    test("removing the target in response cannot turn a fizzled bounce into a draw or Bird") {
        val driver = game()
        val bear = driver.putPermanentOnBattlefield(driver.secondSeat, "Grizzly Bears")
        val unsummon = driver.putCardInHand(driver.secondSeat, "Unsummon")
        enter(driver)
        choose(driver, bounceMode)
        target(driver, bear)
        val library = driver.state.getLibrary(driver.firstSeat)
        submit(driver, PassPriority(driver.firstSeat)).error shouldBe null
        submit(driver, CastSpell(driver.secondSeat, unsummon, targets = listOf(ChosenTarget.Permanent(bear)))).error shouldBe null
        resolveUntilDecision(driver)
        driver.state.getHand(driver.secondSeat) shouldBe listOf(bear)
        driver.state.getHand(driver.firstSeat) shouldBe emptyList()
        driver.state.getLibrary(driver.firstSeat) shouldBe library
        birds(driver) shouldBe emptyList()
    }
})
