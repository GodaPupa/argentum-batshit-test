package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.conditions.PlayerCastSpellsThisTurn
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.matchers.shouldBe

/** Test-only own-history diagnostic; no Surge keyword or printed card is registered. */
class SelfAlternativePriorSpellEligibilityDiagnosticTest : ScenarioTestBase() {
    private data class History(val id: String, val eligible: Boolean, val serialized: Boolean = false)

    init {
        cardRegistry.register(card("Own Prior Spell Probe") {
            manaCost = "{3}"
            typeLine = "Creature — Construct"
            power = 1
            toughness = 1
            keywords(Keyword.FLASH)
            selfAlternativeCost = SelfAlternativeCost(
                ManaCost.parse("{1}"), condition = PlayerCastSpellsThisTurn(Player.You, atLeast = 1)
            )
        })
        cardRegistry.register(card("Prior History Probe") {
            manaCost = "{0}"
            typeLine = "Artifact"
            keywords(Keyword.FLASH)
        })
        val histories = listOf(
            History("H0", false), History("H1", true), History("H2", true),
            History("H3", true), History("H4", false), History("H5", false),
            History("H2", true, true), History("H4", false, true)
        )
        for (row in histories) {
            // Every arm forks this same immutable pre-decision state. Building a history never
            // calls the eligibility evaluator or enumerator, so it cannot gate direct submission.
            val baseline by lazy { history(row) }
            val label = "PS ${row.id} serialized=${row.serialized}"
            test("$label enumeration") {
                val game = fork(baseline)
                val before = game.state
                val id = game.findCardsInHand(1, "Own Prior Spell Probe").single()
                val actions = game.getLegalActions(1).mapNotNull { it.action as? CastSpell }
                    .filter { it.cardId == id }
                actions.count { !it.useAlternativeCost && it.alternativeCostType == null } shouldBe 1
                val alternatives = actions.filter { it.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE }
                alternatives.isNotEmpty() shouldBe row.eligible // W_ENUM
                alternatives.size shouldBe if (row.eligible) 1 else 0
                alternatives.all { it.useAlternativeCost } shouldBe true
                game.state shouldBe before
            }
            test("$label normal") {
                val game = fork(baseline)
                val id = game.findCardsInHand(1, "Own Prior Spell Probe").single()
                val before = game.state
                val tappedBefore = tapped(game)
                val result = game.execute(CastSpell(game.player1Id, id))
                result.error shouldBe null
                game.state.pendingDecision shouldBe null
                game.state.stack.size shouldBe before.stack.size + 1
                game.state.stack.contains(id) shouldBe true
                tapped(game) - tappedBefore shouldBe 3
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>() shouldBe
                    before.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                game.state.getEntity(id)?.get<SpellOnStackComponent>()?.alternativeCost shouldBe null
                game.resolveStack().forEach { it.error shouldBe null }
                game.findPermanent("Own Prior Spell Probe") shouldBe id
                game.state.getEntity(id)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe null
            }
            test("$label direct") {
                val game = fork(baseline)
                val before = game.state
                val id = game.findCardsInHand(1, "Own Prior Spell Probe").single()
                val tappedBefore = tapped(game)
                // Construct independently of enumeration. Neither price nor eligibility chooses
                // the discriminator: this explicit client action selects SELF_ALTERNATIVE.
                val action = CastSpell(game.player1Id, id, useAlternativeCost = true,
                    alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE)
                val result = game.execute(action)
                (result.error == null) shouldBe row.eligible // W_AUTH
                if (row.eligible) {
                    game.state.pendingDecision shouldBe null
                    game.state.stack.size shouldBe before.stack.size + 1
                    game.state.stack.contains(id) shouldBe true
                    tapped(game) - tappedBefore shouldBe 1
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>() shouldBe
                        before.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                    game.state.getEntity(id)?.get<SpellOnStackComponent>()?.alternativeCost shouldBe AlternativeCostType.SELF_ALTERNATIVE
                    game.resolveStack().forEach { it.error shouldBe null }
                    game.findPermanent("Own Prior Spell Probe") shouldBe id
                    game.state.getEntity(id)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe ChoiceValue.Flag
                } else {
                    result.error shouldBe "Alternative cost is not available: " +
                        PlayerCastSpellsThisTurn(Player.You, atLeast = 1).description
                    result.state shouldBe before
                    game.state shouldBe before
                    tapped(game) shouldBe tappedBefore
                    game.state.stack shouldBe before.stack
                    game.state.getHand(game.player1Id).contains(id) shouldBe true
                    game.state.getEntity(id)?.get<SpellOnStackComponent>() shouldBe null
                    game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>() shouldBe
                        before.getEntity(game.player1Id)?.get<ManaPoolComponent>()
                }
            }
        }
    }

    private fun fork(initial: TestGame) = TestGame(initial.state, initial.player1Id,
        initial.player2Id, cardRegistry, actionProcessor, stateTransformer)

    private fun tapped(game: TestGame): Int = game.state.getBattlefield(game.player1Id).count {
        game.state.getEntity(it)?.get<TappedComponent>() != null
    }

    private fun history(row: History): TestGame {
        var builder = scenario().withPlayers("Player", "Opponent")
            .withCardInHand(1, "Own Prior Spell Probe")
            .withCardInHand(1, "Prior History Probe")
            .withCardInHand(2, "Prior History Probe").withCardInHand(2, "Counterspell")
            .withLandsOnBattlefield(1, "Mountain", 6).withLandsOnBattlefield(2, "Island", 2)
            .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        repeat(10) { builder = builder.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Island") }
        val game = builder.build()
        val prior = game.findCardsInHand(1, "Prior History Probe").single()
        val opposingPrior = game.findCardsInHand(2, "Prior History Probe").single()
        val firstTurn = game.state.turnNumber
        when (row.id) {
            "H0" -> Unit
            "H1", "H2", "H3", "H5" -> {
                game.castSpell(1, "Prior History Probe").error shouldBe null
                game.state.stack.contains(prior) shouldBe true
                game.state.getEntity(prior)?.get<SpellOnStackComponent>()?.casterId shouldBe game.player1Id
                if (row.id == "H2") {
                    game.passPriority().error shouldBe null
                    game.state.priorityPlayerId shouldBe game.player2Id
                    game.castSpellTargetingStackSpell(2, "Counterspell", "Prior History Probe").error shouldBe null
                }
                if (row.id != "H3") {
                    game.resolveStack().forEach { it.error shouldBe null }
                    game.state.stack.isEmpty() shouldBe true
                    if (row.id == "H2") {
                        game.findCardsInGraveyard(1, "Prior History Probe") shouldBe listOf(prior)
                        game.findCardsInGraveyard(2, "Counterspell").size shouldBe 1
                    } else {
                        game.findPermanent("Prior History Probe") shouldBe prior
                    }
                }
                if (row.id == "H5") {
                    game.state.spellsCastThisTurnByPlayer[game.player1Id].orEmpty().size shouldBe 1
                    // Use real turn progression, never state.copy to erase the cast tracker.
                    repeat(2) {
                        game.passUntilPhase(Phase.ENDING, Step.END)
                        game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    }
                    game.state.turnNumber shouldBe firstTurn + 2
                }
            }
            "H4" -> {
                game.passPriority().error shouldBe null
                game.state.priorityPlayerId shouldBe game.player2Id
                game.castSpell(2, "Prior History Probe").error shouldBe null
                game.state.getEntity(opposingPrior)?.get<SpellOnStackComponent>()?.casterId shouldBe game.player2Id
                game.resolveStack().forEach { it.error shouldBe null }
                game.findPermanent("Prior History Probe") shouldBe opposingPrior
            }
            else -> error("Unknown history ${row.id}")
        }
        game.state.activePlayerId shouldBe game.player1Id
        game.state.priorityPlayerId shouldBe game.player1Id
        game.state.phase shouldBe Phase.PRECOMBAT_MAIN
        game.state.step shouldBe Step.PRECOMBAT_MAIN
        game.state.pendingDecision shouldBe null
        game.state.gameOver shouldBe false
        game.state.stack shouldBe if (row.id == "H3") listOf(prior) else emptyList()
        game.state.spellsCastThisTurnByPlayer[game.player1Id].orEmpty().size shouldBe if (row.eligible) 1 else 0
        game.state.spellsCastThisTurnByPlayer[game.player2Id].orEmpty().size shouldBe if (row.id in listOf("H2", "H4")) 1 else 0
        if (row.id != "H5") game.state.turnNumber shouldBe firstTurn
        if (row.serialized) {
            val before = game.state
            game.state = SerializationTestSupport.roundTrip(before)
            game.state shouldBe before
            game.state.spellsCastThisTurnByPlayer shouldBe before.spellsCastThisTurnByPlayer
            game.state.priorityPlayerId shouldBe before.priorityPlayerId
        }
        return game
    }
}
