package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.SearchCardInfo
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetRequirementInfo
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IndustrialWasteAdvisorModuleTest : FunSpec({
    fun fixture(): Triple<GameTestDriver, EntityId, DecisionResponder> {
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set ->
                registerCards(set.cards)
                registerCards(set.basicLands)
            }
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        }
        val advisors = CardAdvisorRegistry().also(IndustrialWasteAdvisorModule::register)
        val responder = DecisionResponder(
            GameSimulator(driver.cardRegistry),
            AIPlayer.defaultEvaluator(),
            advisors,
        )
        return Triple(driver, driver.player1Id, responder)
    }

    test("Expedition Map selects the missing Tron piece independent of library order") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Urza's Mine")
        driver.putPermanentOnBattlefield(player, "Urza's Tower")
        val forest = EntityId.of("forest-option")
        val plant = EntityId.of("plant-option")
        val mine = EntityId.of("mine-option")
        val decision = SearchLibraryDecision(
            id = "map-search",
            playerId = player,
            prompt = "Search for a land",
            context = DecisionContext(sourceName = "Expedition Map"),
            options = listOf(forest, mine, plant),
            minSelections = 1,
            maxSelections = 1,
            cards = mapOf(
                forest to land("Forest"),
                mine to land("Urza's Mine"),
                plant to land("Urza's Power Plant"),
            ),
            filterDescription = "land card",
        )

        responder.respond(driver.state, decision, player) shouldBe
            CardsSelectedResponse(decision.id, listOf(plant))
    }

    test("Crop Rotation uses the same missing-piece policy") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Urza's Power Plant")
        driver.putCardInHand(player, "Urza's Tower")
        val mine = EntityId.of("mine-option")
        val tower = EntityId.of("tower-option")
        val decision = SearchLibraryDecision(
            id = "rotation-search",
            playerId = player,
            prompt = "Search for a land",
            context = DecisionContext(sourceName = "Crop Rotation"),
            options = listOf(tower, mine),
            minSelections = 1,
            maxSelections = 1,
            cards = mapOf(tower to land("Urza's Tower"), mine to land("Urza's Mine")),
            filterDescription = "land card",
        )

        responder.respond(driver.state, decision, player) shouldBe
            CardsSelectedResponse(decision.id, listOf(mine))
    }

    test("Myr Retriever death trigger returns the other Retriever") {
        val (driver, player, responder) = fixture()
        val prism = driver.putCardInGraveyard(player, "Prophetic Prism")
        val retriever = driver.putCardInGraveyard(player, "Myr Retriever")
        val decision = ChooseTargetsDecision(
            id = "retriever-target",
            playerId = player,
            prompt = "Choose another target artifact card",
            context = DecisionContext(sourceName = "Myr Retriever"),
            targetRequirements = listOf(TargetRequirementInfo(0, "artifact card in your graveyard")),
            legalTargets = mapOf(0 to listOf(prism, retriever)),
        )

        responder.respond(driver.state, decision, player) shouldBe
            TargetsResponse(decision.id, mapOf(0 to listOf(retriever)))
    }

    test("Ashnod's Altar sacrifices Retriever when the payoff loop is assembled") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Pactdoll Terror")
        val retriever = driver.putPermanentOnBattlefield(player, "Myr Retriever")
        val spare = driver.putPermanentOnBattlefield(player, "Grizzly Bears")
        driver.putCardInGraveyard(player, "Myr Retriever")
        val decision = SelectCardsDecision(
            id = "altar-sacrifice",
            playerId = player,
            prompt = "Select a permanent to sacrifice for Ashnod's Altar",
            context = DecisionContext(sourceName = "Ashnod's Altar"),
            options = listOf(spare, retriever),
            minSelections = 1,
            maxSelections = 1,
        )

        responder.respond(driver.state, decision, player) shouldBe
            CardsSelectedResponse(decision.id, listOf(retriever))
    }
})

private fun land(name: String) = SearchCardInfo(name, "", "Land")
