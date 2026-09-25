package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SearchCardInfo
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed public-information fixtures; never an R1 ordering allocation. */
class IndustrialWasteV2PublicActionPolicyTest : FunSpec({
    fun fixture(): Pair<GameTestDriver, EntityId> {
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0, seed = 9_250_925_001L)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
            replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
                if (key.zoneType == Zone.HAND) emptyList() else cards
            }))
        }
        return driver to driver.activePlayer!!
    }
    fun responder(driver: GameTestDriver) = DecisionResponder(
        GameSimulator(driver.cardRegistry), AIPlayer.defaultEvaluator(),
        CardAdvisorRegistry().also {
            IndustrialWasteV2PilotAdvisorModule.register(it)
        },
    )
    fun resolve(driver: GameTestDriver) {
        val policy = responder(driver)
        var count = 0
        while (driver.state.stack.isNotEmpty() || driver.pendingDecision != null) {
            check(count++ < 30)
            val decision = driver.pendingDecision
            if (decision != null) {
                driver.submitDecision(decision.playerId, policy.respond(driver.state, decision, decision.playerId)).error shouldBe null
            } else driver.bothPass().error shouldBe null
        }
    }
    fun choose(driver: GameTestDriver, player: EntityId) = IndustrialWasteV2PublicActionPolicy.choose(
        driver.state, player, GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player),
    )

    test("public development policy casts an affordable missing Altar through the real engine") {
        val (driver, player) = fixture()
        repeat(3) { driver.putPermanentOnBattlefield(player, "Forest") }
        val altar = driver.putCardInHand(player, "Ashnod's Altar")
        val action = choose(driver, player).shouldBeInstanceOf<CastSpell>()
        action.cardId shouldBe altar
        driver.submit(action).error shouldBe null
        resolve(driver)
        driver.state.getBattlefield().contains(altar) shouldBe true
    }

    test("Star activation pays one mana and chooses an observed hand's black pip") {
        val (driver, player) = fixture()
        driver.putPermanentOnBattlefield(player, "Forest")
        val star = driver.putPermanentOnBattlefield(player, "Chromatic Star")
        driver.putCardInHand(player, "Eviscerator's Insight")
        val drawn = driver.putCardOnTopOfLibrary(player, "Myr Retriever")
        val action = choose(driver, player).shouldBeInstanceOf<ActivateAbility>()
        action.sourceId shouldBe star
        driver.submit(action).error shouldBe null
        driver.state.getEntity(player)!!.get<ManaPoolComponent>()!!.black shouldBe 1
        driver.state.getGraveyard(player).contains(star) shouldBe true
        resolve(driver)
        driver.state.getHand(player).contains(drawn) shouldBe true
    }

    test("Insight pays with Wellspring and preserves live loop hardware") {
        val (driver, player) = fixture()
        driver.putPermanentOnBattlefield(player, "Forest")
        driver.putPermanentOnBattlefield(player, "Swamp")
        val altar = driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        val wellspring = driver.putPermanentOnBattlefield(player, "Ichor Wellspring")
        val retriever = driver.putPermanentOnBattlefield(player, "Myr Retriever")
        driver.putCardInHand(player, "Eviscerator's Insight")
        val action = choose(driver, player).shouldBeInstanceOf<CastSpell>()
        action.additionalCostPayment!!.sacrificedPermanents shouldBe listOf(wellspring)
        driver.submit(action).error shouldBe null
        resolve(driver)
        driver.state.getBattlefield().contains(altar) shouldBe true
        driver.state.getBattlefield().contains(retriever) shouldBe true
        driver.state.getGraveyard(player).contains(wellspring) shouldBe true
        driver.state.getHand(player).size shouldBe 3
    }

    test("public loop actions sacrifice return and recast a Retriever using real generated mana") {
        val (driver, player) = fixture()
        val altar = driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Pactdoll Terror")
        val first = driver.putPermanentOnBattlefield(player, "Myr Retriever")
        val second = driver.putCardInGraveyard(player, "Myr Retriever")
        val sacrifice = choose(driver, player).shouldBeInstanceOf<ActivateAbility>()
        sacrifice.sourceId shouldBe altar
        sacrifice.costPayment!!.sacrificedPermanents shouldBe listOf(first)
        driver.submit(sacrifice).error shouldBe null
        resolve(driver)
        driver.state.getHand(player).contains(second) shouldBe true
        driver.state.getEntity(player)!!.get<ManaPoolComponent>()!!.colorless shouldBe 2
        val recast = choose(driver, player).shouldBeInstanceOf<CastSpell>()
        recast.cardId shouldBe second
        driver.submit(recast).error shouldBe null
        resolve(driver)
        driver.state.getBattlefield().contains(second) shouldBe true
        driver.state.getGraveyard(player).contains(first) shouldBe true
        driver.state.getEntity(player)!!.get<ManaPoolComponent>()!!.colorless shouldBe 0
    }

    test("Dross binds an offered graveyard target and pays black recursion mana") {
        val (driver, player) = fixture()
        repeat(3) { driver.putPermanentOnBattlefield(player, "Swamp") }
        val bomb = driver.putPermanentOnBattlefield(player, "Dross Skullbomb")
        val retriever = driver.putCardInGraveyard(player, "Myr Retriever")
        val action = choose(driver, player).shouldBeInstanceOf<ActivateAbility>()
        action.sourceId shouldBe bomb
        action.targets.size shouldBe 1
        driver.submit(action).error shouldBe null
        resolve(driver)
        driver.state.getHand(player).contains(retriever) shouldBe true
        driver.state.getHand(player).size shouldBe 2
    }

    test("Foundry loop creates lethal power then wins through the next legal combat") {
        val (driver, player) = fixture()
        val opponent = driver.state.getOpponents(player).single()
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Golem Foundry")
        driver.putPermanentOnBattlefield(player, "Myr Retriever")
        driver.putCardInGraveyard(player, "Myr Retriever")
        var actions = 0
        while (true) {
            val action = choose(driver, player)
            if (action is PassPriority) break
            check(actions++ < 100) { "Public policy failed to stop its Foundry loop" }
            driver.submit(action).error shouldBe null
            resolve(driver)
        }
        val golems = driver.state.getBattlefield().filter {
            driver.state.getEntity(it)!!.has<TokenComponent>() && driver.state.projectedState.hasSubtype(it, "Golem")
        }
        golems.isNotEmpty() shouldBe true
        val prospectivePower = driver.state.projectedState.getBattlefieldControlledBy(player)
            .filter { driver.state.projectedState.isCreature(it) }
            .sumOf { driver.state.projectedState.getPower(it) ?: 0 }
        (prospectivePower >= 20) shouldBe true
        driver.state.gameOver shouldBe false
        driver.state.getEntity(opponent)!!.get<LifeTotalComponent>()!!.life shouldBe 20

        // The real turn manager skips a declaration step with no valid attackers.
        // Waiting for DECLARE_ATTACKERS here would reach the following own turn.
        val creationTurn = driver.state.turnNumber
        golems.all { driver.state.getEntity(it)!!.has<SummoningSicknessComponent>() } shouldBe true
        TurnManager(driver.cardRegistry).getValidAttackers(driver.state, player) shouldBe emptyList()
        driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        driver.state.turnNumber shouldBe creationTurn
        driver.activePlayer shouldBe player
        driver.state.getEntity(opponent)!!.get<LifeTotalComponent>()!!.life shouldBe 20
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.activePlayer shouldBe opponent
        driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.activePlayer shouldBe player
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        val attack = choose(driver, player).shouldBeInstanceOf<DeclareAttackers>()
        attack.attackers.keys.containsAll(golems) shouldBe true
        attack.attackers.values.toSet() shouldBe setOf(opponent)
        driver.submit(attack).error shouldBe null
        var passes = 0
        while (!driver.state.gameOver) {
            check(passes++ < 30)
            val who = driver.state.priorityPlayerId!!
            val legal = GameSimulator(driver.cardRegistry).getLegalActions(driver.state, who)
            val action = if (who == opponent) IndustrialWasteV2PublicActionPolicy.choosePassive(driver.state, who, legal)
                else IndustrialWasteV2PublicActionPolicy.choose(driver.state, who, legal)
            driver.submit(action).error shouldBe null
        }
        driver.state.winnerId shouldBe player
        (driver.state.getEntity(opponent)!!.get<LifeTotalComponent>()!!.life <= 0) shouldBe true
    }

    test("passive Forest fixture plays one land and then passes without a second land drop") {
        val (driver, player) = fixture()
        driver.putCardInHand(player, "Forest")
        driver.putCardInHand(player, "Forest")
        val simulator = GameSimulator(driver.cardRegistry)
        val action = IndustrialWasteV2PublicActionPolicy.choosePassive(driver.state, player, simulator.getLegalActions(driver.state, player))
            .shouldBeInstanceOf<PlayLand>()
        driver.submit(action).error shouldBe null
        driver.state.getHand(player).size shouldBe 1
        IndustrialWasteV2PublicActionPolicy.choosePassive(driver.state, player, simulator.getLegalActions(driver.state, player))
            .shouldBeInstanceOf<PassPriority>()
    }

    test("scry and surveil keep an engine card while discarding surplus mana") {
        val (driver, player) = fixture()
        repeat(4) { driver.putPermanentOnBattlefield(player, "Forest") }
        driver.putPermanentOnBattlefield(player, "Swamp")
        val land = EntityId.of("surplus-land")
        val altar = EntityId.of("needed-altar")
        val policy = responder(driver)
        for (source in listOf("Candy Trail", "Conduit Pylons", "Giant's Boulder")) {
            val decision = SelectCardsDecision(
                id = "scry-$source", playerId = player, prompt = "Choose cards to move",
                context = DecisionContext(sourceName = source),
                options = listOf(altar, land), minSelections = 0, maxSelections = 2,
                cardInfo = mapOf(altar to SearchCardInfo("Ashnod's Altar", "", ""), land to SearchCardInfo("Forest", "", "")),
            )
            policy.respond(driver.state, decision, player) shouldBe CardsSelectedResponse(decision.id, listOf(land))
        }
    }

    test("real Candy Trail and Boulder scry continuations keep Altar above a surplus land") {
        for (source in listOf("Candy Trail", "Giant's Boulder")) {
            val (driver, player) = fixture()
            repeat(4) { driver.putPermanentOnBattlefield(player, "Forest") }
            driver.putPermanentOnBattlefield(player, "Swamp")
            val spell = driver.putCardInHand(player, source)
            val wanted = driver.putCardOnTopOfLibrary(player, "Ashnod's Altar")
            val surplus = driver.putCardOnTopOfLibrary(player, "Forest")
            val action = choose(driver, player).shouldBeInstanceOf<CastSpell>()
            action.cardId shouldBe spell
            driver.submit(action).error shouldBe null
            resolve(driver)
            driver.state.getLibrary(player).first() shouldBe wanted
            driver.state.getLibrary(player).last() shouldBe surplus
        }
    }

    test("opening policy has one common keep rule and honors the three-mulligan bound") {
        val policy = IndustrialWasteV2PublicActionPolicy
        policy.keepOpeningHand(listOf("Forest", "Urza's Mine", "Candy Trail", "Myr Retriever", "Ashnod's Altar", "Pactdoll Terror", "Myr Kinsmith"), 0) shouldBe true
        policy.keepOpeningHand(listOf("Tree of Tales", "Vault of Whispers", "Dross Skullbomb", "Myr Retriever", "Ashnod's Altar", "Pactdoll Terror", "Blood Fountain"), 0) shouldBe true
        policy.keepOpeningHand(List(7) { "Forest" }, 0) shouldBe false
        policy.keepOpeningHand(List(7) { "Ashnod's Altar" }, 2) shouldBe false
        policy.keepOpeningHand(List(7) { "Ashnod's Altar" }, 3) shouldBe true
        val cards = listOf("Forest", "Swamp", "Urza's Mine", "Urza's Tower", "Candy Trail", "Myr Retriever", "Pactdoll Terror")
            .mapIndexed { i, name -> EntityId.of("opening-$i") to name }.toMap()
        val bottom = policy.bottomOpeningHand(cards, 3)
        bottom.size shouldBe 3
        bottom.toSet().size shouldBe 3
        bottom.all { it in cards } shouldBe true
        cards.filterKeys { it !in bottom }.values.count { it in setOf("Forest", "Swamp", "Urza's Mine", "Urza's Tower") } shouldBe 2
    }

    test("real two-player London mulligans draw seven then bottom three at the forced keep") {
        val (driver) = fixture()
        driver.initMirrorMatch(Deck.of("Forest" to 60), skipMulligans = false, startingPlayer = 0, seed = 9_250_925_001L)
        val player = driver.player1
        repeat(3) { taken ->
            val names = driver.state.getHand(player).map { driver.state.getEntity(it)!!.get<CardComponent>()!!.name }
            names.size shouldBe 7
            IndustrialWasteV2PublicActionPolicy.keepOpeningHand(names, taken) shouldBe false
            driver.submit(TakeMulligan(player)).error shouldBe null
            driver.state.getHand(player).size shouldBe 7
        }
        val mulligan = driver.state.getEntity(player)!!.get<MulliganStateComponent>()!!
        mulligan.mulligansTaken shouldBe 3
        mulligan.freeMulligan shouldBe false
        val cards = driver.state.getHand(player).associateWith { "Forest" }
        IndustrialWasteV2PublicActionPolicy.keepOpeningHand(cards.values.toList(), 3) shouldBe true
        driver.submit(KeepHand(player)).error shouldBe null
        driver.submit(KeepHand(driver.player2)).error shouldBe null
        driver.submit(BottomCards(player, IndustrialWasteV2PublicActionPolicy.bottomOpeningHand(cards, 3))).error shouldBe null
        driver.state.getHand(player).size shouldBe 4
        driver.state.getLibrary(player).size shouldBe 56
    }

    test("action choice ignores opponent hidden identity and both future library orders") {
        val (driver, player) = fixture()
        repeat(3) { driver.putPermanentOnBattlefield(player, "Forest") }
        driver.putCardInHand(player, "Ashnod's Altar")
        val opponent = driver.state.turnOrder.single { it != player }
        val hidden = driver.putCardInHand(opponent, "Myr Retriever")
        val initial = choose(driver, player)
        val entity = driver.state.getEntity(hidden)!!
        val cards = driver.state.entities + (hidden to entity.with(entity.get<CardComponent>()!!.copy(name = "Pactdoll Terror")))
        val zones = driver.state.zones.toMutableMap()
        listOf(player, opponent).forEach { who ->
            val key = ZoneKey(who, Zone.LIBRARY)
            zones[key] = zones.getValue(key).reversed()
        }
        driver.replaceState(driver.state.copy(entities = cards, zones = zones))
        choose(driver, player) shouldBe initial
    }

    test("explicit regression seed repeats initial state and identities while another seed changes shuffle") {
        val (first) = fixture()
        val (second) = fixture()
        val deck = Deck.of("Forest" to 20, "Swamp" to 20)
        first.initMirrorMatch(deck, skipMulligans = false, startingPlayer = 0, seed = 9_250_925_001L)
        second.initMirrorMatch(deck, skipMulligans = false, startingPlayer = 0, seed = 9_250_925_001L)
        first.state shouldBe second.state
        first.player1 shouldBe second.player1
        val firstNames = first.state.getLibrary(first.player1).map { first.state.getEntity(it)!!.get<CardComponent>()!!.name }
        second.initMirrorMatch(deck, skipMulligans = false, startingPlayer = 0, seed = 9_250_925_002L)
        val otherNames = second.state.getLibrary(second.player1).map { second.state.getEntity(it)!!.get<CardComponent>()!!.name }
        firstNames shouldNotBe otherNames
    }
})
