package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.ObjectReferenceEnvironment
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.SerializationTestSupport
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.conditions.CastChoiceMade
import io.kotest.matchers.shouldBe

class SourceDepartureChoiceNegativeWitnessTest : ScenarioTestBase() {
    init {
        test("SD09 later generation selected choice is not the original choice") {
            val game = scenario().withPlayers("Player", "Opponent").withCardOnBattlefield(1, "Grizzly Bears").build()
            val id = game.findPermanent("Grizzly Bears")!!
            val origin = game.state.objectRef(id)!!
            val from = game.state.logicalZone(id)!!
            val hand = ZoneKey(game.player1Id, Zone.HAND)
            game.state = game.state.moveToZone(id, from, hand).moveToZone(id, hand, from)
                .updateEntity(id) { it.with(CastChoicesComponent(chosen = mapOf(ChoiceSlot.SELF_ALTERNATIVE to ChoiceValue.Flag))) }
            game.state = SerializationTestSupport.roundTrip(game.state)
            game.state.isCurrentObject(origin) shouldBe false
            game.state.getEntity(id)?.get<CastChoicesComponent>()?.chosen?.get(ChoiceSlot.SELF_ALTERNATIVE) shouldBe ChoiceValue.Flag
            val ctx = EffectContext(sourceId = id, controllerId = game.player1Id,
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin))
            ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE), ctx) shouldBe false
        }
        test("SD09 known empty original choice does not become selected") {
            val game = scenario().withPlayers("Player", "Opponent").withCardOnBattlefield(1, "Grizzly Bears").build()
            val id = game.findPermanent("Grizzly Bears")!!
            val origin = game.state.objectRef(id)!!
            val snapshot = EntitySnapshot.fromProjection(id, game.state)
            snapshot.castChoices shouldBe emptyMap()
            val ctx = EffectContext(sourceId = id, controllerId = game.player1Id,
                objectReferences = ObjectReferenceEnvironment(captured = true, origin = origin, source = origin),
                lastKnownSourceSnapshot = snapshot)
            ConditionEvaluator().evaluate(game.state, CastChoiceMade(ChoiceSlot.SELF_ALTERNATIVE), ctx) shouldBe false
        }
    }
}
