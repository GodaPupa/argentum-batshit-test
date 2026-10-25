package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.Duration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class AbilitySourceCostDepartureTest : FunSpec({
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    for (protected in listOf(false, true)) test("SI actual sacrifice cost protected $protected") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(deck = Deck.of("Mountain" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val source = d.putCreatureOnBattlefield(d.player1, "Ghitu Fire-Eater")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(source)
        val original = d.state.objectRef(source)!!
        d.submitSuccess(ActivateAbility(d.player1, source,
            d.cardRegistry.requireCard("Ghitu Fire-Eater").activatedAbilities[0].id,
            targets = listOf(ChosenTarget.Permanent(target))))
        val ability = d.state.stack.mapNotNull { d.state.getEntity(it)?.get<ActivatedAbilityOnStackComponent>() }.single()
        ability.lastKnownSourceSnapshot!!.objectRef shouldBe original
        ability.lastKnownSourceSnapshot!!.colors shouldBe setOf("RED")
        d.state.isCurrentObject(original) shouldBe false
        var state = d.state
        if (protected) state = state.addFloatingEffect(Layer.ABILITY,
            SerializableModification.GrantKeyword("PROTECTION_FROM_RED"), setOf(target), Duration.EndOfTurn,
            EffectContext(sourceId = target, controllerId = d.player2))
        val encoded = json.encodeToString(GameState.serializer(), state)
        d.replaceState(json.decodeFromString(GameState.serializer(), encoded))
        json.encodeToString(GameState.serializer(), d.state) shouldBe encoded
        var guard = 0
        while (d.state.stack.isNotEmpty() && guard++ < 20) d.bothPass()
        d.state.stack shouldBe emptyList()
        (target in d.state.getBattlefield()) shouldBe protected
        d.getGraveyard(d.player1).contains(source) shouldBe true
    }
})
