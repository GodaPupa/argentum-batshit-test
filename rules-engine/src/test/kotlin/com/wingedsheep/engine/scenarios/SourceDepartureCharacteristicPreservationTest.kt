package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Preserve departure-time characteristics while retaining the independent selected casting choice. */
class SourceDepartureCharacteristicPreservationTest : ScenarioTestBase() {
    init {
        for (gated in listOf(false, true)) {
            val name = "Departure Power Probe $gated"
            cardRegistry.register(card(name) {
                manaCost = "{0}"; typeLine = "Creature — Construct"; power = 2; toughness = 2
                selfAlternativeCost = SelfAlternativeCost(ManaCost.parse("{0}"))
                triggeredAbility {
                    trigger = Triggers.EntersBattlefield
                    if (gated) interveningIf = CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE)
                    effect = Effects.GainLife(DynamicAmount.EntityProperty(EntityReference.Source, EntityNumericProperty.Power))
                }
            })
            test("SD preservation departure power after growth gated=$gated") {
                var builder = scenario().withPlayers("Player", "Opponent")
                    .withCardInHand(1, name).withCardInHand(1, "Giant Growth").withCardInHand(1, "Unsummon")
                    .withLandsOnBattlefield(1, "Forest", 1).withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(5) { builder = builder.withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain") }
                val game = builder.build()
                val action = game.getLegalActions(1).mapNotNull { it.action as? CastSpell }.first {
                    game.state.getEntity(it.cardId)?.get<CardComponent>()?.name == name &&
                        it.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
                }
                game.execute(action).error shouldBe null
                var passes = 0
                while (game.findPermanent(name) == null && passes++ < 8) game.passPriority().error shouldBe null
                game.findPermanent(name) shouldBe action.cardId
                val origin = game.state.objectRef(action.cardId)
                origin shouldNotBe null
                game.state.stack.size shouldBe 1
                game.castSpell(1, "Giant Growth", action.cardId).error shouldBe null
                passes = 0
                while (game.state.projectedState.getPower(action.cardId) != 5 && passes++ < 8) game.passPriority().error shouldBe null
                game.state.projectedState.getPower(action.cardId) shouldBe 5
                game.state.stack.size shouldBe 1
                game.getLifeTotal(1) shouldBe 20
                game.castSpell(1, "Unsummon", action.cardId).error shouldBe null
                passes = 0
                while (action.cardId !in game.state.getHand(game.player1Id) && passes++ < 8) game.passPriority().error shouldBe null
                game.state.getHand(game.player1Id).contains(action.cardId) shouldBe true
                game.state.isCurrentObject(origin!!) shouldBe false
                game.state.getEntity(action.cardId)?.get<LastKnownPermanentComponent>()?.snapshot?.power shouldBe 5
                val before = game.state
                game.state = SerializationTestSupport.roundTrip(before)
                game.state shouldBe before
                game.resolveStack().forEach { it.error shouldBe null }
                game.state.stack.size shouldBe 0
                game.getLifeTotal(1) shouldBe 25
            }
        }
    }
}
