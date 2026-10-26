package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Transport shape only; engine occurrence legality is qualified separately. */
class FixedRecipientChoiceTransportTest : ScenarioTestBase() {
    init {
        test("routing and JSON retain distinct occurrence labels for the same preview card") {
            val game = scenario().withPlayers().withCardOnBattlefield(1, "Hill Giant").build()
            val card = game.findPermanent("Hill Giant")!!
            val raw = ChooseOptionDecision(
                id = "occurrence-1", playerId = game.player1Id,
                prompt = "Choose the target occurrence to change",
                context = DecisionContext(sourceId = card),
                options = listOf("Group 1, target 1, division 2", "Group 2, target 1, division 5"),
                optionCardIds = mapOf(0 to listOf(card), 1 to listOf(card)),
            )
            val routed = raw.withClientRoutingId("epoch:occurrence-1")
                .shouldBeInstanceOf<ChooseOptionDecision>()
            routed shouldBe raw.copy(id = "epoch:occurrence-1")
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            json.decodeFromString<PendingDecision>(json.encodeToString<PendingDecision>(routed)) shouldBe routed
            raw.id shouldBe "occurrence-1"
            raw.options.size shouldBe 2
        }

        test("nested routing changes only identity and enrichment preserves occurrence payload") {
            val game = scenario().withPlayers().build()
            val raw = ChooseOptionDecision(
                id = "occurrence-2", playerId = game.player1Id,
                prompt = "Choose the next target occurrence",
                context = DecisionContext(),
                options = listOf("Group 1, target 1", "Group 3, target 2"),
            )
            val routed = raw.withClientRoutingId("epoch:occurrence-2")
            DecisionEnricher(cardRegistry).enrich(routed, game.state, game.player1Id) shouldBe
                raw.copy(id = "epoch:occurrence-2")
            raw.withClientRoutingId("other-epoch:occurrence-2") shouldBe
                raw.copy(id = "other-epoch:occurrence-2")
        }
    }
}
