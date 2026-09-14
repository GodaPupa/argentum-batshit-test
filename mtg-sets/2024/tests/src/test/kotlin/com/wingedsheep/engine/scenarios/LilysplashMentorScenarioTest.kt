package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.OrderObjectsDecision
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.blb.cards.LilysplashMentor
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CreatureStats
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId
import com.wingedsheep.sdk.scripting.GrantActivatedAbility
import com.wingedsheep.sdk.scripting.TimingRule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

/** Deterministic coverage for Lilysplash Mentor's blink activation and restrictions. */
class LilysplashMentorScenarioTest : FunSpec({

    val abilityId = LilysplashMentor.activatedAbilities.single().id

    fun driver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 20, "Island" to 20),
            startingPlayer = 0,
            skipMulligans = true,
        )
        return driver
    }

    fun GameTestDriver.fundActivation(player: EntityId) {
        giveMana(player, Color.GREEN, 1)
        giveMana(player, Color.BLUE, 2)
    }

    fun GameTestDriver.activate(
        source: EntityId,
        target: EntityId,
        paymentStrategy: PaymentStrategy = PaymentStrategy.AutoPay,
    ) = submit(
        ActivateAbility(
            playerId = activePlayer!!,
            sourceId = source,
            abilityId = abilityId,
            targets = listOf(entityIdToChosenTarget(state, target)),
            paymentStrategy = paymentStrategy,
        ),
    )

    fun GameTestDriver.tapBasicForMana(player: EntityId, land: EntityId, symbol: Char) {
        submit(
            ActivateAbility(
                playerId = player,
                sourceId = land,
                abilityId = AbilityId.intrinsicMana(symbol),
            ),
        ).isSuccess shouldBe true
    }

    fun GameTestDriver.attachAura(player: EntityId, auraName: String, target: EntityId) {
        val aura = putPermanentOnBattlefield(player, auraName)
        addComponent(aura, AttachedToComponent(target))
        val existing = state.getEntity(target)?.get<AttachmentsComponent>()?.attachedIds.orEmpty()
        addComponent(target, AttachmentsComponent(existing + aura))
    }

    fun GameTestDriver.tapGrantedAuraAbility(
        player: EntityId,
        land: EntityId,
        auraName: String,
        color: Color,
    ) {
        val ability = cardRegistry.requireCard(auraName).staticAbilities
            .filterIsInstance<GrantActivatedAbility>()
            .single()
            .ability
        submit(
            ActivateAbility(
                playerId = player,
                sourceId = land,
                abilityId = ability.id,
                manaColorChoice = color,
            ),
        ).isSuccess shouldBe true
    }

    fun GameTestDriver.tapFertileGroundLand(player: EntityId, land: EntityId, symbol: Char) {
        val result = submit(
            ActivateAbility(
                playerId = player,
                sourceId = land,
                abilityId = AbilityId.intrinsicMana(symbol),
            ),
        )
        result.isPaused shouldBe true
        submitDecision(player, ColorChosenResponse(result.pendingDecision!!.id, Color.BLUE))
    }

    fun GameTestDriver.totalMana(player: EntityId): Int {
        val pool = state.getEntity(player)?.get<ManaPoolComponent>() ?: return 0
        return pool.white + pool.blue + pool.black + pool.red + pool.green + pool.colorless
    }

    fun GameTestDriver.resolveLilysplashTriggers(
        opponent: EntityId,
        lands: List<EntityId>,
    ) {
        var guard = 0
        while ((state.stack.isNotEmpty() || pendingDecision != null) && guard++ < 100) {
            when (val decision = pendingDecision) {
                is OrderObjectsDecision -> submitDecision(
                    decision.playerId,
                    OrderedResponse(decision.id, decision.objects),
                )
                is ChooseTargetsDecision -> {
                    val targetsByRequirement = decision.targetRequirements.associate { requirement ->
                        val legal = decision.legalTargets[requirement.index].orEmpty().toSet()
                        requirement.index to if (opponent in legal) {
                            listOf(opponent)
                        } else {
                            lands.filter { it in legal }
                        }
                    }
                    submitMultiTargetSelection(decision.playerId, targetsByRequirement).error shouldBe null
                }
                null -> bothPass()
                else -> error("Unexpected Lilysplash combo decision: ${decision::class.simpleName}")
            }
        }
        guard shouldNotBe 100
        state.stack.isEmpty() shouldBe true
        pendingDecision shouldBe null
    }

    fun GameTestDriver.advanceUntilGameOver(player: EntityId) {
        var guard = 0
        while (!state.gameOver && guard++ < 300) {
            if (pendingDecision != null) {
                autoResolveDecision()
            } else {
                bothPass()
            }
        }
        guard shouldNotBe 300
        assertGameOver(expectedWinner = player)
    }

    fun GameTestDriver.createMouseToken(player: EntityId): EntityId {
        val tokenId = EntityId.generate()
        val card = CardComponent(
            cardDefinitionId = "token:Mouse",
            name = "Mouse Token",
            manaCost = ManaCost.ZERO,
            typeLine = TypeLine.parse("Creature — Mouse"),
            baseStats = CreatureStats(1, 1),
            colors = setOf(Color.WHITE),
            ownerId = player,
        )
        replaceState(
            state
                .withEntity(
                    tokenId,
                    ComponentContainer.of(
                        card,
                        TokenComponent,
                        ControllerComponent(player),
                        SummoningSicknessComponent,
                    ),
                )
                .addToZone(ZoneKey(player, Zone.BATTLEFIELD), tokenId),
        )
        return tokenId
    }

    test("the activation is sorcery-speed") {
        LilysplashMentor.activatedAbilities.single().timing shouldBe TimingRule.SorcerySpeed

        val driver = driver()
        val player = driver.activePlayer!!
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val bears = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.passPriorityUntil(Step.UPKEEP)
        driver.fundActivation(player)

        val result = driver.activate(mentor, bears)

        result.isSuccess shouldBe false
        result.error.orEmpty() shouldContain "sorcery"
    }

    test("it blinks another creature you control and returns it with exactly one counter") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val bears = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.addComponent(
            bears,
            CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)),
        )
        driver.fundActivation(player)

        driver.activate(mentor, bears).isSuccess shouldBe true
        driver.bothPass()

        val returned = driver.findPermanent(player, "Grizzly Bears")!!
        driver.state.getEntity(returned)
            ?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
    }

    test("it cannot target Lilysplash Mentor itself") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.fundActivation(player)

        val result = driver.activate(mentor, mentor)

        result.isSuccess shouldBe false
        result.error.shouldNotBeNull()
    }

    test("it cannot target a creature an opponent controls") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val bears = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        driver.fundActivation(player)

        val result = driver.activate(mentor, bears)

        result.isSuccess shouldBe false
        result.error.shouldNotBeNull()
    }

    test("an exiled token does not return or receive a counter") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val token = driver.createMouseToken(player)
        driver.fundActivation(player)

        driver.activate(mentor, token).isSuccess shouldBe true
        driver.bothPass()

        driver.state.getBattlefield(player).contains(token) shouldBe false
        driver.findPermanent(player, "Mouse Token") shouldBe null
    }

    test("Peregrine Drake makes two mana per activation with five basic lands") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.putCreatureOnBattlefield(player, "Peregrine Drake")
        val forests = List(3) { driver.putLandOnBattlefield(player, "Forest") }
        val islands = List(2) { driver.putLandOnBattlefield(player, "Island") }
        val lands = forests + islands

        repeat(2) { iteration ->
            forests.forEach { driver.tapBasicForMana(player, it, 'G') }
            islands.forEach { driver.tapBasicForMana(player, it, 'U') }
            val drake = driver.findPermanent(player, "Peregrine Drake")!!

            driver.activate(mentor, drake, PaymentStrategy.FromPool).isSuccess shouldBe true
            lands.count(driver::isTapped) shouldBe 5
            driver.totalMana(player) shouldBe (iteration + 1) * 2

            driver.bothPass()
            driver.submitTargetSelection(player, lands).isSuccess shouldBe true
            driver.bothPass()

            lands.count(driver::isTapped) shouldBe 0
            driver.totalMana(player) shouldBe (iteration + 1) * 2
        }
    }

    test("Peregrine Drake converts repeated activations into a Sage's Row Denizen win") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.putCreatureOnBattlefield(player, "Peregrine Drake")
        driver.putCreatureOnBattlefield(player, "Sage's Row Denizen")
        val forests = List(3) { driver.putLandOnBattlefield(player, "Forest") }
        val islands = List(2) { driver.putLandOnBattlefield(player, "Island") }
        val lands = forests + islands
        val opponentLibrary = ZoneKey(opponent, Zone.LIBRARY)
        driver.replaceState(
            driver.state.copy(
                zones = driver.state.zones +
                    (opponentLibrary to driver.state.getZone(opponentLibrary).take(4)),
            ),
        )

        repeat(2) { iteration ->
            forests.forEach { driver.tapBasicForMana(player, it, 'G') }
            islands.forEach { driver.tapBasicForMana(player, it, 'U') }
            val drake = driver.findPermanent(player, "Peregrine Drake")!!

            driver.activate(mentor, drake, PaymentStrategy.FromPool).isSuccess shouldBe true
            driver.resolveLilysplashTriggers(opponent, lands)

            lands.count(driver::isTapped) shouldBe 0
            driver.totalMana(player) shouldBe (iteration + 1) * 2
            driver.state.getZone(opponentLibrary).size shouldBe 4 - ((iteration + 1) * 2)
        }

        driver.advanceUntilGameOver(player)
    }

    test("Cloud of Faeries loses one basic-land activation and cannot immediately repeat") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.putCreatureOnBattlefield(player, "Cloud of Faeries")
        val forests = List(2) { driver.putLandOnBattlefield(player, "Forest") }
        val island = driver.putLandOnBattlefield(player, "Island")
        val lands = forests + island

        forests.forEach { driver.tapBasicForMana(player, it, 'G') }
        driver.tapBasicForMana(player, island, 'U')
        val faeries = driver.findPermanent(player, "Cloud of Faeries")!!
        driver.activate(mentor, faeries, PaymentStrategy.FromPool).isSuccess shouldBe true
        driver.totalMana(player) shouldBe 0

        driver.bothPass()
        driver.submitTargetSelection(player, listOf(forests.first(), island)).isSuccess shouldBe true
        driver.bothPass()

        lands.count(driver::isTapped) shouldBe 1
        val returnedFaeries = driver.findPermanent(player, "Cloud of Faeries")!!
        driver.activate(mentor, returnedFaeries).isSuccess shouldBe false
    }

    test("Cloud of Faeries makes one mana per activation with two enhanced lands") {
        val driver = driver()
        val player = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.putCreatureOnBattlefield(player, "Cloud of Faeries")
        val newHorizonsLand = driver.putLandOnBattlefield(player, "Forest")
        val fertileGroundLand = driver.putLandOnBattlefield(player, "Forest")
        val lands = listOf(newHorizonsLand, fertileGroundLand)
        driver.attachAura(player, "New Horizons", newHorizonsLand)
        driver.attachAura(player, "Fertile Ground", fertileGroundLand)

        repeat(2) { iteration ->
            driver.tapGrantedAuraAbility(player, newHorizonsLand, "New Horizons", Color.GREEN)
            driver.tapFertileGroundLand(player, fertileGroundLand, 'G')
            val faeries = driver.findPermanent(player, "Cloud of Faeries")!!

            driver.activate(mentor, faeries, PaymentStrategy.FromPool).isSuccess shouldBe true
            lands.count(driver::isTapped) shouldBe 2
            driver.totalMana(player) shouldBe iteration + 1

            driver.bothPass()
            driver.submitTargetSelection(player, lands).isSuccess shouldBe true
            driver.bothPass()

            lands.count(driver::isTapped) shouldBe 0
            driver.totalMana(player) shouldBe iteration + 1
        }
    }

    test("enhanced-land Cloud of Faeries converts repeated activations into a Sage's Row Denizen win") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        driver.putCreatureOnBattlefield(player, "Cloud of Faeries")
        driver.putCreatureOnBattlefield(player, "Sage's Row Denizen")
        val newHorizonsLand = driver.putLandOnBattlefield(player, "Forest")
        val fertileGroundLand = driver.putLandOnBattlefield(player, "Forest")
        val lands = listOf(newHorizonsLand, fertileGroundLand)
        driver.attachAura(player, "New Horizons", newHorizonsLand)
        driver.attachAura(player, "Fertile Ground", fertileGroundLand)
        val opponentLibrary = ZoneKey(opponent, Zone.LIBRARY)
        driver.replaceState(
            driver.state.copy(
                zones = driver.state.zones +
                    (opponentLibrary to driver.state.getZone(opponentLibrary).take(4)),
            ),
        )

        repeat(2) { iteration ->
            driver.tapGrantedAuraAbility(player, newHorizonsLand, "New Horizons", Color.GREEN)
            driver.tapFertileGroundLand(player, fertileGroundLand, 'G')
            val faeries = driver.findPermanent(player, "Cloud of Faeries")!!

            driver.activate(mentor, faeries, PaymentStrategy.FromPool).isSuccess shouldBe true
            driver.resolveLilysplashTriggers(opponent, lands)

            lands.count(driver::isTapped) shouldBe 0
            driver.totalMana(player) shouldBe iteration + 1
            driver.state.getZone(opponentLibrary).size shouldBe 4 - ((iteration + 1) * 2)
        }

        driver.advanceUntilGameOver(player)
    }

    test("the activation fizzles when its only target is returned to hand in response") {
        val driver = driver()
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val mentor = driver.putCreatureOnBattlefield(player, "Lilysplash Mentor")
        val drake = driver.putCreatureOnBattlefield(player, "Peregrine Drake")
        val unsummon = driver.putCardInHand(opponent, "Unsummon")
        driver.fundActivation(player)

        driver.activate(mentor, drake).isSuccess shouldBe true
        driver.passPriority(player)
        driver.giveMana(opponent, Color.BLUE, 1)
        driver.castSpell(opponent, unsummon, listOf(drake)).isSuccess shouldBe true
        driver.bothPass()
        driver.findCardInHand(player, "Peregrine Drake") shouldBe drake

        driver.bothPass()

        driver.findPermanent(player, "Peregrine Drake") shouldBe null
        driver.findCardInHand(player, "Peregrine Drake") shouldBe drake
    }
})
