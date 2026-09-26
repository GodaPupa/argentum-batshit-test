package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SearchCardInfo
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetRequirementInfo
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Selection capability only. Fixed fixtures never read the R1 corpus. */
class IndustrialWasteV2SelectionAdvisorTest : FunSpec({
    fun fixture(): Triple<GameTestDriver, EntityId, DecisionResponder> {
        val driver = GameTestDriver().apply {
            MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
            initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
            replaceState(state.copy(zones = state.zones.mapValues { (key, cards) ->
                if (key.zoneType == Zone.HAND) emptyList() else cards
            }))
        }
        val advisors = CardAdvisorRegistry().also {
            IndustrialWasteV2PilotAdvisorModule.register(it)
        }
        return Triple(driver, driver.activePlayer!!, DecisionResponder(
            GameSimulator(driver.cardRegistry), AIPlayer.defaultEvaluator(), advisors,
        ))
    }

    fun selection(player: EntityId, source: String, vararg cards: Pair<EntityId, String>) =
        SelectCardsDecision(
            id = "visible-selection", playerId = player, prompt = "Choose up to one card",
            context = DecisionContext(sourceName = source),
            options = cards.map { it.first }, minSelections = 0, maxSelections = 1,
            cardInfo = cards.associate { it.first to SearchCardInfo(it.second, "", "") },
        )

    test("Recursive Eggs Rumble fixes black access without inventing Tron in the list") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Forest")
        driver.putPermanentOnBattlefield(player, "Tree of Tales")
        driver.putCardInHand(player, "Blood Fountain")
        val swamp = EntityId.of("swamp")
        val altar = EntityId.of("altar")
        val choice = selection(player, "Malevolent Rumble", altar to "Ashnod's Altar", swamp to "Swamp")
        responder.respond(driver.state, choice, player) shouldBe CardsSelectedResponse(choice.id, listOf(swamp))
    }

    test("collection-shaped Map and Rotation decisions build missing Tron while fixing a visible black need first") {
        val (driver, player, responder) = fixture()
        repeat(4) { driver.putPermanentOnBattlefield(player, "Forest") }
        val forest = EntityId.of("offered-forest")
        val tower = EntityId.of("offered-tower")
        val swamp = EntityId.of("offered-swamp")
        val map = selection(player, "Expedition Map", forest to "Forest", tower to "Urza's Tower")
        responder.respond(driver.state, map, player) shouldBe CardsSelectedResponse(map.id, listOf(tower))
        driver.putCardInHand(player, "Pactdoll Terror")
        val rotation = selection(player, "Crop Rotation", tower to "Urza's Tower", swamp to "Swamp")
        responder.respond(driver.state, rotation, player) shouldBe CardsSelectedResponse(rotation.id, listOf(swamp))
    }

    test("Tron-capable Stirrings takes the missing third piece when colored access is sufficient") {
        val (driver, player, responder) = fixture()
        listOf("Forest", "Urza's Mine", "Urza's Power Plant").forEach {
            driver.putPermanentOnBattlefield(player, it)
        }
        val tower = EntityId.of("tower")
        val altar = EntityId.of("altar")
        val choice = selection(player, "Ancient Stirrings", altar to "Ashnod's Altar", tower to "Urza's Tower")
        responder.respond(driver.state, choice, player) shouldBe CardsSelectedResponse(choice.id, listOf(tower))
    }

    test("one shared Rumble policy selects the missing engine across all four mana architectures") {
        val architectures = listOf(
            listOf("Forest", "Conduit Pylons", "Urza's Mine"), // immutable submitted control
            listOf("Forest", "Swamp", "Urza's Mine"), // Compact Loop
            listOf("Tree of Tales", "Vault of Whispers", "Forest"), // Recursive Eggs
            listOf("Forest", "Swamp", "Conduit Pylons"), // Lean Tron Hybrid
        )
        for (lands in architectures) {
            val (driver, player, responder) = fixture()
            lands.forEach { driver.putPermanentOnBattlefield(player, it) }
            val altar = EntityId.of("altar")
            val egg = EntityId.of("egg")
            val choice = selection(player, "Malevolent Rumble", egg to "Ichor Wellspring", altar to "Ashnod's Altar")
            responder.respond(driver.state, choice, player) shouldBe CardsSelectedResponse(choice.id, listOf(altar))
        }
    }

    test("Blood Fountain returns both offered Retrievers before a redundant expensive creature") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        driver.putPermanentOnBattlefield(player, "Pactdoll Terror")
        val first = driver.putCardInGraveyard(player, "Myr Retriever")
        val second = driver.putCardInGraveyard(player, "Myr Retriever")
        val kinsmith = driver.putCardInGraveyard(player, "Myr Kinsmith")
        val choice = ChooseTargetsDecision(
            id = "fountain", playerId = player, prompt = "Return up to two creature cards",
            context = DecisionContext(sourceName = "Blood Fountain"),
            targetRequirements = listOf(TargetRequirementInfo(0, "creature cards", minTargets = 0, maxTargets = 2)),
            legalTargets = mapOf(0 to listOf(kinsmith, second, first)),
        )
        val expected = listOf(first, second).sortedBy { it.toString() }
        responder.respond(driver.state, choice, player) shouldBe TargetsResponse(choice.id, mapOf(0 to expected))
    }

    test("Kinsmith takes its optional search and Dross recursion prefers the accessible loop creature") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Ashnod's Altar")
        val retriever = driver.putCardInGraveyard(player, "Myr Retriever")
        val kinsmith = driver.putCardInGraveyard(player, "Myr Kinsmith")
        val may = YesNoDecision(
            id = "kinsmith-may", playerId = player, prompt = "Search for a Myr?",
            context = DecisionContext(sourceName = "Myr Kinsmith"),
        )
        responder.respond(driver.state, may, player) shouldBe YesNoResponse(may.id, choice = true)
        val libraryKinsmith = driver.putCardOnTopOfLibrary(player, "Myr Kinsmith")
        val libraryRetriever = driver.putCardOnTopOfLibrary(player, "Myr Retriever")
        val search = SearchLibraryDecision(
            id = "kinsmith-search", playerId = player, prompt = "Choose a Myr",
            context = DecisionContext(sourceName = "Myr Kinsmith"),
            options = listOf(libraryKinsmith, libraryRetriever),
            minSelections = 0, maxSelections = 1,
            cards = mapOf(libraryKinsmith to SearchCardInfo("Myr Kinsmith", "", ""), libraryRetriever to SearchCardInfo("Myr Retriever", "", "")),
            filterDescription = "Myr card",
        )
        responder.respond(driver.state, search, player) shouldBe CardsSelectedResponse(search.id, listOf(libraryRetriever))
        val recur = ChooseTargetsDecision(
            id = "dross-recur", playerId = player, prompt = "Return a creature card",
            context = DecisionContext(sourceName = "Dross Skullbomb"),
            targetRequirements = listOf(TargetRequirementInfo(0, "creature card")),
            legalTargets = mapOf(0 to listOf(kinsmith, retriever)),
        )
        responder.respond(driver.state, recur, player) shouldBe TargetsResponse(recur.id, mapOf(0 to listOf(retriever)))
    }

    test("real Stirrings Rumble and Kinsmith decisions accept the shared selector responses") {
        for ((source, wanted) in listOf(
            "Ancient Stirrings" to "Ashnod's Altar",
            "Malevolent Rumble" to "Ashnod's Altar",
            "Myr Kinsmith" to "Myr Retriever",
        )) {
            val (driver, player, responder) = fixture()
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            repeat(4) { driver.putPermanentOnBattlefield(player, "Forest") }
            val spell = driver.putCardInHand(player, source)
            val selected = driver.putCardOnTopOfLibrary(player, wanted)
            driver.castSpell(player, spell).error shouldBe null
            var transitions = 0
            while (driver.state.stack.isNotEmpty() || driver.pendingDecision != null) {
                check(transitions++ < 30) { "$source did not complete its deterministic fixture" }
                val decision = driver.pendingDecision
                if (decision != null) {
                    driver.submitDecision(decision.playerId, responder.respond(driver.state, decision, decision.playerId))
                        .error shouldBe null
                } else {
                    driver.bothPass().error shouldBe null
                }
            }
            driver.state.getHand(player).contains(selected) shouldBe true
            driver.pendingDecision shouldBe null
        }
    }

    test("opponent hidden identities and future library order cannot change an offered choice") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Forest")
        driver.putPermanentOnBattlefield(player, "Swamp")
        val opponent = driver.state.turnOrder.single { it != player }
        val hidden = driver.putCardInHand(opponent, "Myr Retriever")
        val altar = EntityId.of("altar")
        val egg = EntityId.of("egg")
        val choice = selection(player, "Malevolent Rumble", egg to "Ichor Wellspring", altar to "Ashnod's Altar")
        val before = responder.respond(driver.state, choice, player)

        val hiddenIds = driver.state.getLibrary(player) + driver.state.getLibrary(opponent) + hidden
        val changedEntities = driver.state.entities.toMutableMap()
        hiddenIds.forEachIndexed { index, id ->
            val entity = changedEntities.getValue(id)
            val card = entity.get<CardComponent>()!!
            changedEntities[id] = entity.with(card.copy(name = if (index % 2 == 0) "Pactdoll Terror" else "Swamp"))
        }
        val changedZones = driver.state.zones.toMutableMap()
        listOf(player, opponent).forEach { who ->
            val key = ZoneKey(who, Zone.LIBRARY)
            changedZones[key] = changedZones.getValue(key).reversed()
        }
        val hiddenChanged = driver.state.copy(entities = changedEntities, zones = changedZones)
        responder.respond(hiddenChanged, choice.copy(options = choice.options.reversed()), player) shouldBe before
        before shouldBe CardsSelectedResponse(choice.id, listOf(altar))
        IndustrialWasteV2SelectionAdvisor.targetPreference(hiddenChanged, hidden, player) shouldBe null
    }

    test("Stirrings bottom order is deterministic and never selects a nonselectable card") {
        val (driver, player, responder) = fixture()
        driver.putPermanentOnBattlefield(player, "Forest")
        driver.putPermanentOnBattlefield(player, "Swamp")
        val altar = EntityId.of("altar")
        val egg = EntityId.of("egg")
        val forbidden = EntityId.of("forbidden")
        val choice = selection(player, "Ancient Stirrings", egg to "Ichor Wellspring", altar to "Ashnod's Altar")
            .copy(nonSelectableOptions = listOf(forbidden))
        responder.respond(driver.state, choice, player) shouldBe CardsSelectedResponse(choice.id, listOf(altar))
        val reorder = ReorderLibraryDecision(
            id = "bottom", playerId = player, prompt = "Order the remaining cards",
            context = DecisionContext(sourceName = "Ancient Stirrings"),
            cards = listOf(forbidden, egg),
            cardInfo = mapOf(forbidden to SearchCardInfo("Pactdoll Terror", "", ""), egg to SearchCardInfo("Ichor Wellspring", "", "")),
        )
        responder.respond(driver.state, reorder, player) shouldBe OrderedResponse(reorder.id, listOf(forbidden, egg))
    }
})
