package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.Triggers
import com.wingedsheep.sdk.scripting.Effects
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Test-only original reproduction, not a runtime repair or card registration. */
class SourceDeparturePaidChoiceDiagnosticTest : ScenarioTestBase() {
    init {
        cardRegistry.register(card("Departure Choice Probe") {
            manaCost = "{3}"; typeLine = "Creature — Construct"; power = 2; toughness = 2
            selfAlternativeCost = SelfAlternativeCost(ManaCost.parse("{0}"))
            triggeredAbility {
                trigger = Triggers.EntersBattlefield
                interveningIf = CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE)
                effect = Effects.GainLife(3)
            }
        })
        for (selected in listOf(false, true)) for (departed in listOf(false, true)) for (serialized in listOf(false, true)) {
            test("SD selected=$selected departed=$departed serialized=$serialized") {
                var builder = scenario().withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Departure Choice Probe").withCardInHand(1, "Unsummon")
                    .withLandsOnBattlefield(1, "Mountain", 3).withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain") }
                val game = builder.build()
                val startLife = game.getLifeTotal(1)
                if (selected) {
                    val action = game.getLegalActions(1).mapNotNull { it.action as? CastSpell }.first {
                        game.state.getEntity(it.cardId)?.get<CardComponent>()?.name == "Departure Choice Probe" &&
                            it.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
                    }
                    game.execute(action).error shouldBe null
                } else game.castSpell(1, "Departure Choice Probe").error shouldBe null
                var passes = 0
                while (game.findPermanent("Departure Choice Probe") == null && passes++ < 8) {
                    game.passPriority().error shouldBe null
                }
                val id = game.findPermanent("Departure Choice Probe")
                id shouldNotBe null
                val origin = game.state.objectRef(id!!)
                origin shouldNotBe null
                game.getLifeTotal(1) shouldBe startLife
                game.state.stack.size shouldBe if (selected) 1 else 0
                game.state.getEntity(id)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe
                    if (selected) ChoiceValue.Flag else null
                if (serialized) {
                    val before = game.state
                    game.state = SerializationTestSupport.roundTrip(before)
                    game.state shouldBe before
                }
                if (departed) {
                    game.castSpell(1, "Unsummon", id).error shouldBe null
                    var bouncePasses = 0
                    while (!game.state.getHand(game.player1Id).contains(id) && bouncePasses++ < 8) {
                        game.passPriority().error shouldBe null
                    }
                    game.state.getHand(game.player1Id).contains(id) shouldBe true
                    game.state.isCurrentObject(origin!!) shouldBe false
                    game.state.getEntity(id)?.get<CastChoicesComponent>() shouldBe null
                    game.state.stack.size shouldBe if (selected) 1 else 0
                    if (serialized) {
                        val before = game.state
                        game.state = SerializationTestSupport.roundTrip(before)
                        game.state shouldBe before
                    }
                }
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.stack.size shouldBe 0
                // The original paid choice belongs to the pending trigger's original source visit.
                game.getLifeTotal(1) shouldBe startLife + if (selected) 3 else 0
            }
        }
    }
}
