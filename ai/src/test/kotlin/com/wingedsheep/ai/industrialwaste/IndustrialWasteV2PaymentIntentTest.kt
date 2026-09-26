package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.builtins.ListSerializer

class IndustrialWasteV2PaymentIntentTest : FunSpec({
    fun fixture(): Pair<GameTestDriver, EntityId> {
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
            registerCards(PredefinedTokens.allTokens)
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0, seed = 9_250_925_111L)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
            replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
                if (key.zoneType == Zone.HAND) emptyList() else cards
            }))
        }
        return driver to driver.activePlayer!!
    }

    fun legal(driver: GameTestDriver, player: EntityId) = GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player)

    fun resolve(driver: GameTestDriver) {
        val responder = DecisionResponder(GameSimulator(driver.cardRegistry), AIPlayer.defaultEvaluator(),
            CardAdvisorRegistry().also { IndustrialWasteV2PilotAdvisorModule.register(it) })
        var count = 0
        while (driver.state.stack.isNotEmpty() || driver.pendingDecision != null) {
            check(count++ < 50)
            val decision = driver.pendingDecision
            if (decision != null) {
                driver.submitDecision(decision.playerId, responder.respond(driver.state, decision, decision.playerId)).error shouldBe null
            } else driver.bothPass().error shouldBe null
        }
    }

    fun readyFoundry(driver: GameTestDriver, player: EntityId) {
        driver.putPermanentOnBattlefield(player, "Golem Foundry")
        val opponent = driver.state.getOpponents(player).single()
        driver.replaceState(driver.state.updateEntity(opponent) { it.with(LifeTotalComponent(1)) })
    }

    test("already automatic payment submits the unchanged selected action without funding") {
        val (driver, player) = fixture()
        driver.putPermanentOnBattlefield(player, "Forest")
        val star = driver.putPermanentOnBattlefield(player, "Chromatic Star")
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
        val selected = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        selected.sourceId shouldBe star
        records shouldBe emptyList()
        driver.submit(selected).error shouldBe null
    }

    test("Altar funding retains selected Star across death trigger instead of rescoring returned Retriever") {
        val (driver, player) = fixture()
        readyFoundry(driver, player)
        val altar = driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        val first = driver.putPermanentOnBattlefield(player, "Myr Retriever")
        val second = driver.putCardInGraveyard(player, "Myr Retriever")
        val star = driver.putPermanentOnBattlefield(player, "Chromatic Star")
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
        val original = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<ActivateAbility>()
        original.sourceId shouldBe star
        val funding = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        funding.sourceId shouldBe altar
        funding.costPayment!!.sacrificedPermanents shouldBe listOf(first)
        driver.submit(funding).error shouldBe null
        resolve(driver)
        driver.state.getHand(player).contains(second) shouldBe true
        IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<CastSpell>().cardId shouldBe second
        val selected = binder.choose(driver.state, player, legal(driver, player))
        selected shouldBe original
        driver.submit(selected).error shouldBe null
        driver.state.getGraveyard(player).contains(star) shouldBe true
        driver.state.getHand(player).contains(second) shouldBe true
        records.map { it.stage } shouldBe listOf("SELECTED_BEFORE_FUNDING", "FUNDING_ACTION", "SUBMIT_SELECTED_ACTION")
        val codec = IndustrialWasteV2AllocationTrace.CODEC
        codec.decodeFromString(ListSerializer(IndustrialWasteV2PaymentIntentRecord.serializer()),
            codec.encodeToString(ListSerializer(IndustrialWasteV2PaymentIntentRecord.serializer()), records)) shouldBe records
    }

    test("one Spawn cannot be both spell sacrifice and explicit funding resource") {
        val (driver, player) = fixture()
        repeat(2) { driver.putPermanentOnBattlefield(player, "Forest") }
        val rumble = driver.putCardInHand(player, "Malevolent Rumble")
        driver.submit(CastSpell(player, rumble)).error shouldBe null
        resolve(driver)
        driver.replaceState(driver.state.copy(zones = driver.state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }).updateEntity(player) { it.with(ManaPoolComponent(black = 1)) })
        // Both available sacrifice-mana routes share the exact material reserved for the spell.
        // The frozen Altar sacrifice policy also refuses using Spawn for that separate ability.
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putCardInHand(player, "Eviscerator's Insight")
        // The general engine can sacrifice the Altar instead and use the Spawn's mana.
        // The frozen pilot binds the Spawn as its final material; that bound payment is impossible.
        val menu = legal(driver, player)
        val insight = menu.single { it.affordable && (it.action as? CastSpell)?.let { action ->
            driver.state.getEntity(action.cardId)?.get<CardComponent>()?.name == "Eviscerator's Insight"
        } == true }
        val bound = IndustrialWasteV2PublicActionPolicy.bind(driver.state, player, insight) as CastSpell
        val selectedMaterial = bound.additionalCostPayment!!.sacrificedPermanents.single()
        driver.state.getEntity(selectedMaterial)!!.get<CardComponent>()!!.name shouldBe "Eldrazi Spawn"
        val before = driver.state
        IndustrialWasteV2PaymentBinder(driver.cardRegistry).choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<PassPriority>()
        driver.state shouldBe before
    }

    test("real Rumble Spawn funds the selected Star through its exact predefined ability") {
        val (driver, player) = fixture()
        repeat(2) { driver.putPermanentOnBattlefield(player, "Forest") }
        val rumble = driver.putCardInHand(player, "Malevolent Rumble")
        driver.submit(CastSpell(player, rumble)).error shouldBe null
        resolve(driver)
        driver.replaceState(driver.state.copy(zones = driver.state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
        val spawn = driver.state.projectedState.getBattlefieldControlledBy(player).single {
            driver.state.getEntity(it)?.get<CardComponent>()?.name == "Eldrazi Spawn"
        }
        val spawnCard = driver.state.getEntity(spawn)!!.get<CardComponent>()!!
        spawnCard.cardDefinitionId shouldBe "Eldrazi Spawn"
        val spawnAbility = driver.cardRegistry.getCard(spawnCard.cardDefinitionId)!!.script.activatedAbilities.single { it.isManaAbility }
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        val star = driver.putPermanentOnBattlefield(player, "Chromatic Star")
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
        val selected = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<ActivateAbility>()
        selected.sourceId shouldBe star
        val funding = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        funding.sourceId shouldBe spawn
        funding.abilityId shouldBe spawnAbility.id
        driver.submit(funding).error shouldBe null
        driver.state.getEntity(player)!!.get<ManaPoolComponent>()!!.colorless shouldBe 1
        binder.choose(driver.state, player, legal(driver, player)) shouldBe selected
        driver.submit(selected).error shouldBe null
        records.map { it.stage } shouldBe listOf("SELECTED_BEFORE_FUNDING", "FUNDING_ACTION", "SUBMIT_SELECTED_ACTION")
    }

    test("Insight explicitly floats its selected Prism before the additional sacrifice") {
        val (driver, player) = fixture()
        repeat(2) { driver.putPermanentOnBattlefield(player, "Forest") }
        val prism = driver.putPermanentOnBattlefield(player, "Prophetic Prism")
        val insight = driver.putCardInHand(player, "Eviscerator's Insight")
        val original = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<CastSpell>()
        original.cardId shouldBe insight
        original.additionalCostPayment!!.sacrificedPermanents shouldBe listOf(prism)
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
        val first = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        driver.state.getEntity(first.sourceId)!!.get<CardComponent>()!!.name shouldBe "Forest"
        driver.submit(first).error shouldBe null
        val second = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        second.sourceId shouldBe prism
        second.manaColorChoice shouldBe Color.BLACK
        driver.submit(second).error shouldBe null
        driver.state.getEntity(prism)!!.has<TappedComponent>() shouldBe true
        val cast = binder.choose(driver.state, player, legal(driver, player))
        cast shouldBe original
        driver.submit(cast).error shouldBe null
        driver.state.getGraveyard(player).contains(prism) shouldBe true
        records.map { it.stage } shouldBe listOf("SELECTED_BEFORE_FUNDING", "FUNDING_ACTION", "FUNDING_ACTION", "SUBMIT_SELECTED_ACTION")
    }

    test("an unfunded Prism automatic solution does not authorize a selected generic spell") {
        val (driver, player) = fixture()
        driver.putPermanentOnBattlefield(player, "Prophetic Prism")
        val map = driver.putCardInHand(player, "Expedition Map")
        legal(driver, player).none { it.affordable && (it.action as? CastSpell)?.cardId == map } shouldBe true
        val before = driver.state
        IndustrialWasteV2PaymentBinder(driver.cardRegistry).choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<PassPriority>()
        driver.state shouldBe before
    }

    test("a policy-ineligible second creature never authorizes partial funding for a three-mana spell") {
        val (driver, player) = fixture()
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Myr Retriever")
        driver.putPermanentOnBattlefield(player, "Myr Kinsmith")
        val extraAltar = driver.putCardInHand(player, "Ashnod's Altar")
        val menu = legal(driver, player)
        menu.any { it.affordable && (it.action as? CastSpell)?.cardId == extraAltar } shouldBe true
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val before = driver.state
        IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
            .choose(driver.state, player, menu).shouldBeInstanceOf<PassPriority>()
        driver.state shouldBe before
        records shouldBe emptyList()
    }

    test("delegated automatic Prism payment may choose green while explicit policy would choose black") {
        val (driver, player) = fixture()
        repeat(2) { driver.putPermanentOnBattlefield(player, "Urza's Mine") }
        val prism = driver.putPermanentOnBattlefield(player, "Prophetic Prism")
        val rumble = driver.putCardInHand(player, "Malevolent Rumble")
        driver.putCardInHand(player, "Eviscerator's Insight")
        val menu = legal(driver, player)
        val explicitPrism = menu.single { (it.action as? ActivateAbility)?.sourceId == prism }
        (IndustrialWasteV2PublicActionPolicy.bind(driver.state, player, explicitPrism) as ActivateAbility)
            .manaColorChoice shouldBe Color.BLACK
        val selected = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, menu)
            .shouldBeInstanceOf<CastSpell>()
        selected.cardId shouldBe rumble
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
            .choose(driver.state, player, menu) shouldBe selected
        records shouldBe emptyList()
        driver.submit(selected).error shouldBe null
        driver.state.getEntity(prism)!!.has<TappedComponent>() shouldBe true
    }

    test("explicit Retriever funding retains delegated automatic green payment at its witnessed suffix") {
        val (driver, player) = fixture()
        val altar = driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        val material = driver.putPermanentOnBattlefield(player, "Myr Retriever")
        val prism = driver.putPermanentOnBattlefield(player, "Prophetic Prism")
        driver.putPermanentOnBattlefield(player, "Urza's Mine")
        val rumble = driver.putCardInHand(player, "Malevolent Rumble")
        driver.putCardInHand(player, "Eviscerator's Insight")
        val selected = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<CastSpell>()
        selected.cardId shouldBe rumble
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
        val funding = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        funding.sourceId shouldBe altar
        funding.costPayment!!.sacrificedPermanents shouldBe listOf(material)
        driver.submit(funding).error shouldBe null
        resolve(driver)
        val explicitPrism = legal(driver, player).single { (it.action as? ActivateAbility)?.sourceId == prism }
        (IndustrialWasteV2PublicActionPolicy.bind(driver.state, player, explicitPrism) as ActivateAbility)
            .manaColorChoice shouldBe Color.BLACK
        binder.choose(driver.state, player, legal(driver, player)) shouldBe selected
        driver.submit(selected).error shouldBe null
        driver.state.getEntity(prism)!!.has<TappedComponent>() shouldBe true
        records.map { it.stage } shouldBe listOf("SELECTED_BEFORE_FUNDING", "FUNDING_ACTION", "SUBMIT_SELECTED_ACTION")
    }

    test("complete repeated Retriever funding keeps the original three-mana spell and both real payments") {
        val (driver, player) = fixture()
        val altar = driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        val retrievers = List(2) { driver.putPermanentOnBattlefield(player, "Myr Retriever") }
        val extraAltar = driver.putCardInHand(player, "Ashnod's Altar")
        val original = IndustrialWasteV2PublicActionPolicy.choose(driver.state, player, legal(driver, player))
            .shouldBeInstanceOf<CastSpell>()
        original.cardId shouldBe extraAltar
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
        val consumed = mutableListOf<EntityId>()
        repeat(2) {
            val funding = binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
            funding.sourceId shouldBe altar
            consumed += funding.costPayment!!.sacrificedPermanents.single()
            driver.submit(funding).error shouldBe null
            resolve(driver)
        }
        consumed.toSet() shouldBe retrievers.toSet()
        binder.choose(driver.state, player, legal(driver, player)) shouldBe original
        driver.submit(original).error shouldBe null
        records.map { it.stage } shouldBe listOf("SELECTED_BEFORE_FUNDING", "FUNDING_ACTION", "FUNDING_ACTION", "SUBMIT_SELECTED_ACTION")
    }

    test("lost selected source invalidates retained intent without selecting a replacement") {
        val (driver, player) = fixture()
        readyFoundry(driver, player)
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Myr Retriever")
        driver.putCardInGraveyard(player, "Myr Retriever")
        val star = driver.putPermanentOnBattlefield(player, "Chromatic Star")
        val binder = IndustrialWasteV2PaymentBinder(driver.cardRegistry)
        binder.choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<ActivateAbility>()
        driver.replaceState(driver.state.removeEntity(star))
        shouldThrow<IllegalStateException> { binder.choose(driver.state, player, legal(driver, player)) }
    }

    test("colorless Altar mana alone does not admit black recovery") {
        val (driver, player) = fixture()
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Myr Retriever")
        driver.putPermanentOnBattlefield(player, "Blood Fountain")
        driver.putCardInGraveyard(player, "Myr Retriever")
        val records = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        IndustrialWasteV2PaymentBinder(driver.cardRegistry, records::add)
            .choose(driver.state, player, legal(driver, player)).shouldBeInstanceOf<PassPriority>()
        records shouldBe emptyList()
    }
})
