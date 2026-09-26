package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Required frozen-rule attribution; all fixtures are synthetic and excluded from the R1 corpus. */
class IndustrialWasteV2SacrificeCheckpointTest : FunSpec({
    fun game() = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        registerCards(PredefinedTokens.allTokens)
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0, seed = 9_250_925_112L)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
        replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }))
    }
    fun checkpoint(driver: GameTestDriver): IndustrialWasteV2CheckpointMana {
        val player = driver.player1
        val ids = driver.state.getHand(player) + driver.state.getGraveyard(player) +
            driver.state.projectedState.getBattlefieldControlledBy(player)
        val copies = ids.distinct().associateWith { id ->
            "${driver.state.getEntity(id)!!.get<CardComponent>()!!.name}#$id"
        }
        return IndustrialWasteV2CheckpointManaClassifier.classify(
            driver.state, player, copies,
            GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player), driver.cardRegistry,
        )
    }
    listOf("Eviscerator's Insight", "Fanatical Offering").forEach { name ->
        test("$name omitted for missing black still records the frozen colored failure") {
            val driver = game()
            repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Urza's Mine") }
            driver.putPermanentOnBattlefield(driver.player1, "Ichor Wellspring")
            driver.putCardInHand(driver.player1, name)
            val metric = checkpoint(driver)
            metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
            metric.cards.single().manaCost shouldBe "{1}{B}"
            metric.coloredManaFailure shouldBe true
            metric.totalManaStranded shouldBe false
            metric.unresolved shouldBe false
        }
        test("$name without a sacrifice is not misattributed to colored mana") {
            val driver = game()
            repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Urza's Mine") }
            driver.putCardInHand(driver.player1, name)
            val metric = checkpoint(driver)
            metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY
            metric.coloredManaFailure shouldBe false
            metric.totalManaStranded shouldBe false
        }
        test("$name with a sacrifice but only one mana is a total shortfall") {
            val driver = game()
            driver.putPermanentOnBattlefield(driver.player1, "Swamp")
            driver.putPermanentOnBattlefield(driver.player1, "Ichor Wellspring")
            driver.putCardInHand(driver.player1, name)
            val metric = checkpoint(driver)
            metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
            metric.coloredManaFailure shouldBe false
            metric.totalManaStranded shouldBe true
            metric.unresolved shouldBe false
        }
        test("$name ordinary payment witness keeps its sacrifice for the actual cast") {
            val driver = game()
            val player = driver.player1
            driver.putPermanentOnBattlefield(player, "Swamp")
            driver.putPermanentOnBattlefield(player, "Urza's Mine")
            val sacrifice = driver.putPermanentOnBattlefield(player, "Ichor Wellspring")
            val spell = driver.putCardInHand(player, name)
            checkpoint(driver).cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
            driver.submit(CastSpell(player, spell, additionalCostPayment = AdditionalCostPayment(
                sacrificedPermanents = listOf(sacrifice),
            ))).error shouldBe null
            (sacrifice in driver.state.getGraveyard(player)) shouldBe true
        }
    }
    test("Crop Rotation needs a sacrificable land and a real green payment") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Urza's Mine")
        driver.putCardInHand(driver.player1, "Crop Rotation")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
        metric.coloredManaFailure shouldBe true
        metric.unresolved shouldBe false
    }
    test("Crop Rotation cannot spend floating green without its additional land sacrifice") {
        val driver = game()
        driver.giveMana(driver.player1, Color.GREEN)
        driver.putCardInHand(driver.player1, "Crop Rotation")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY
        metric.coloredManaFailure shouldBe false
    }
    test("Crop Rotation can use one Forest for ordinary mana and then sacrifice that tapped Forest") {
        val driver = game()
        val player = driver.player1
        val forest = driver.putPermanentOnBattlefield(player, "Forest")
        val rotation = driver.putCardInHand(player, "Crop Rotation")
        checkpoint(driver).cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
        val manaAction = GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player)
            .map { it.action }.filterIsInstance<ActivateAbility>().single { it.sourceId == forest }
        driver.submit(manaAction).error shouldBe null
        driver.submit(CastSpell(player, rotation, additionalCostPayment = AdditionalCostPayment(
            sacrificedPermanents = listOf(forest),
        ))).error shouldBe null
        (forest in driver.state.getGraveyard(player)) shouldBe true
    }
    test("Insight flashback missing black is a five-mana graveyard failure and never hand stranding") {
        val driver = game()
        repeat(5) { driver.putPermanentOnBattlefield(driver.player1, "Urza's Mine") }
        driver.putPermanentOnBattlefield(driver.player1, "Ichor Wellspring")
        driver.putCardInGraveyard(driver.player1, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.cards shouldBe emptyList()
        metric.relevantGraveyardSpells.single().manaCost shouldBe "{4}{B}"
        metric.relevantGraveyardSpells.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNAVAILABLE_COLORED_PAYMENT
        metric.coloredManaFailure shouldBe true
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe false
    }
    test("Insight flashback total shortfall never strands a card in hand") {
        val driver = game()
        repeat(4) { driver.putPermanentOnBattlefield(driver.player1, "Swamp") }
        driver.putPermanentOnBattlefield(driver.player1, "Ichor Wellspring")
        driver.putCardInGraveyard(driver.player1, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.relevantGraveyardSpells.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.INSUFFICIENT_TOTAL_MANA
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
    }
    test("Insight flashback still needs its ordinary sacrifice even when enumeration omits its metadata") {
        val driver = game()
        repeat(5) { driver.putPermanentOnBattlefield(driver.player1, "Swamp") }
        driver.putCardInGraveyard(driver.player1, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.relevantGraveyardSpells.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.TIMING_OR_OTHER_LEGALITY
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
    }
    test("Insight flashback with actual ordinary mana and sacrifice is executable") {
        val driver = game()
        val player = driver.player1
        repeat(5) { driver.putPermanentOnBattlefield(player, "Swamp") }
        val sacrifice = driver.putPermanentOnBattlefield(player, "Ichor Wellspring")
        val insight = driver.putCardInGraveyard(player, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.relevantGraveyardSpells.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
        val flashback = GameSimulator(driver.cardRegistry).getLegalActions(driver.state, player)
            .map { it.action }.filterIsInstance<CastSpell>().single { it.cardId == insight }
        driver.submit(flashback.copy(additionalCostPayment = AdditionalCostPayment(
            sacrificedPermanents = listOf(sacrifice),
        ))).error shouldBe null
        (sacrifice in driver.state.getGraveyard(player)) shouldBe true
    }
    test("Chromatic Star cannot be assumed to supply color and remain the spell's sacrifice") {
        val driver = game()
        repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Urza's Mine") }
        driver.putPermanentOnBattlefield(driver.player1, "Chromatic Star")
        driver.putCardInHand(driver.player1, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }
    test("repeatable Altar mana is unresolved rather than a false fixed bonus proof") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Ashnod's Altar")
        repeat(3) { driver.putPermanentOnBattlefield(driver.player1, "Myr Retriever") }
        driver.putCardInGraveyard(driver.player1, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.relevantGraveyardSpells.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }
    test("ordinary spell cannot count two Altars consuming the same creature as four mana") {
        val driver = game()
        repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Ashnod's Altar") }
        driver.putPermanentOnBattlefield(driver.player1, "Myr Retriever")
        driver.putCardInHand(driver.player1, "Golem Foundry")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }
    test("ordinary spell cannot treat one Altar as a maximum of one activation") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Ashnod's Altar")
        repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Myr Retriever") }
        driver.putCardInHand(driver.player1, "Golem Foundry")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }
    test("recovery activation with unqualified explicit funding is not an aggregate color proof") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Ashnod's Altar")
        repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Myr Retriever") }
        driver.putPermanentOnBattlefield(driver.player1, "Blood Fountain")
        driver.putCardInGraveyard(driver.player1, "Myr Retriever")
        val metric = checkpoint(driver)
        metric.relevantActivatedAbilities.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.coloredManaFailure shouldBe false
        metric.unresolved shouldBe true
    }
    test("ordinary positive payment does not become unresolved merely because an Altar also exists") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Ashnod's Altar")
        repeat(3) { driver.putPermanentOnBattlefield(driver.player1, "Swamp") }
        driver.putCardInHand(driver.player1, "Golem Foundry")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
        metric.unresolved shouldBe false
    }
    test("real Rumble Spawn cannot pay and remain the additional sacrifice") {
        val driver = game()
        val player = driver.player1
        repeat(2) { driver.putPermanentOnBattlefield(player, "Forest") }
        val rumble = driver.putCardInHand(player, "Malevolent Rumble")
        driver.submit(CastSpell(player, rumble)).error shouldBe null
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
        val spawn = driver.state.projectedState.getBattlefieldControlledBy(player).single {
            driver.state.getEntity(it)?.get<CardComponent>()?.name == "Eldrazi Spawn"
        }
        val spawnCard = driver.state.getEntity(spawn)!!.get<CardComponent>()!!
        spawnCard.cardDefinitionId shouldBe "Eldrazi Spawn"
        driver.cardRegistry.getCard(spawnCard.cardDefinitionId)!!.script.activatedAbilities.any { it.isManaAbility } shouldBe true
        driver.replaceState(driver.state.copy(zones = driver.state.zones.mapValues { (key, cards) ->
            if (key.zoneType == Zone.HAND) emptyList() else cards
        }).updateEntity(player) { it.with(ManaPoolComponent(black = 1)) })
        driver.putCardInHand(player, "Eviscerator's Insight")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }

    test("unfunded Prophetic Prism cannot be a positive generic-mana witness") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Prophetic Prism")
        driver.putCardInHand(driver.player1, "Chromatic Star")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }
    test("two unfunded Prisms cannot establish a circular colored payment witness") {
        val driver = game()
        repeat(2) { driver.putPermanentOnBattlefield(driver.player1, "Prophetic Prism") }
        driver.putCardInHand(driver.player1, "Ancient Stirrings")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.UNRESOLVED_PAYMENT_SHAPE
        metric.coloredManaFailure shouldBe false
        metric.totalManaStranded shouldBe false
        metric.unresolved shouldBe true
    }
    test("a genuine basic-land witness remains executable with an unrelated paid filter present") {
        val driver = game()
        driver.putPermanentOnBattlefield(driver.player1, "Swamp")
        driver.putPermanentOnBattlefield(driver.player1, "Prophetic Prism")
        driver.putCardInHand(driver.player1, "Chromatic Star")
        val metric = checkpoint(driver)
        metric.cards.single().status shouldBe IndustrialWasteV2CheckpointCardStatus.EXECUTABLE
        metric.unresolved shouldBe false
    }

})
