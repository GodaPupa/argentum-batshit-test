package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.shouldBe

/**
 * Seed-free raw M5 harness preflight for the frozen Monster London comparator.
 * It invokes the existing private raw predicates reflectively without modifying controller logic.
 */
class PestMonsterLondonRawM5ProbeTest : ScenarioTestBase() {
    private fun summaries(game: TestGame, ids: List<com.wingedsheep.sdk.model.EntityId>) =
        ids.associateWith { id ->
            val card = requireNotNull(game.state.getEntity(id)?.get<CardComponent>())
            CardSummary(
                name = card.name,
                manaCost = card.manaCost.toString(),
                typeLine = card.typeLine.toString(),
                oracleText = card.oracleText,
            )
        }

    private fun position(hand: List<String>, library: List<String> = emptyList()): TestGame {
        require(hand.size == 7)
        val b = scenario().withPlayers("Monster", "Opponent")
        hand.forEach { b.withCardInHand(1, it) }
        library.forEach { b.withCardInLibrary(1, it) }
        return b.build()
    }

    private fun rawAtom(game: TestGame, spellName: String): Pair<Boolean, Boolean> {
        val hand = game.state.getHand(game.player1Id)
        val cards = summaries(game, hand)
        val controller = EngineAiPlayerController(cardRegistry, game.player1Id, gameStateProvider = { game.state })
        val guaranteedMethod = controller.javaClass.declaredMethods.single {
            it.name.startsWith("guaranteedSecondLandAccess")
        }.apply { isAccessible = true }
        val guaranteed = guaranteedMethod.invoke(controller, hand, cards)
        val spell = hand.single { cards.getValue(it).name == spellName }
        val developmentMethod = controller.javaClass.declaredMethods.single {
            it.name.startsWith("hasPayableEarlyDevelopmentLine")
        }.apply { isAccessible = true }
        val value = developmentMethod.invoke(controller, game.state, hand, spell, guaranteed) as Boolean
        return (guaranteed != null) to value
    }

    init {
        test("raw frozen M5 representative atoms expose deterministic truth without changing controller source") {
            val cases = listOf(
                Triple(
                    position(listOf("Forest","Forest","Ancient Stirrings",
                        "Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus")),
                    "Ancient Stirrings", false to true,
                ),
                Triple(
                    position(listOf("Urza's Tower","Urza's Mine","Ancient Stirrings",
                        "Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus")),
                    "Ancient Stirrings", false to false,
                ),
                Triple(
                    position(listOf("Urza's Tower","Urza's Mine","Expedition Map",
                        "Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus")),
                    "Expedition Map", false to true,
                ),
                Triple(
                    position(listOf("Bojuka Bog","Expedition Map",
                        "Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Bramble Wurm")),
                    "Expedition Map", false to false,
                ),
                Triple(
                    position(listOf("Forest","Generous Ent","Ancient Stirrings",
                        "Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus"),
                        library=listOf("Forest")),
                    "Ancient Stirrings", true to true,
                ),
                Triple(
                    position(listOf("Forest","Generous Ent","Expedition Map",
                        "Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus","Maelstrom Colossus"),
                        library=listOf("Forest")),
                    "Expedition Map", true to true,
                ),
            )
            cases.forEach { (game, spell, expected) ->
                rawAtom(game, spell) shouldBe expected
            }
        }
    }
}
