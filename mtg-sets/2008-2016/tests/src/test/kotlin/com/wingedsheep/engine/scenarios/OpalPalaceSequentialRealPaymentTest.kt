package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.c13.cards.OpalPalace
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import io.kotest.matchers.shouldBe

/**
 * Prospective real sequential payment seam, not the accepted two-entry aggregation fixture.
 * Only this new case is selected; technical or fixture failure is INCOMPLETE.
 */
class OpalPalaceSequentialRealPaymentTest : ScenarioTestBase() {
    init {
        test("two actual Palace activations pay from physical Plains and preserve both riders") {
            val game = scenario().withPlayers("Commander", "Opponent")
                .withFormat(Format.Commander())
                .withCardOnBattlefield(1, "Opal Palace")
                .withCardOnBattlefield(1, "Opal Palace")
                .withLandsOnBattlefield(1, "Plains", 5)
                .withCardInCommandZone(1, "Serra Angel")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val actor = game.player1Id
            val commander = game.state.getZone(actor, Zone.COMMAND).single()
            game.state = game.state.updateEntity(commander) {
                it.with(CommanderComponent(ownerId = actor))
            }.updateEntity(actor) {
                it.with(CommanderRegistryComponent(listOf(commander)))
            }
            val battlefield = game.state.getBattlefield()
            val palaces = battlefield.filter {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Opal Palace"
            }
            val plains = battlefield.filter {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Plains"
            }
            palaces.size shouldBe 2
            plains.size shouldBe 5
            for ((index, palace) in palaces.withIndex()) {
                val activation = game.execute(ActivateAbility(
                    playerId = actor, sourceId = palace,
                    abilityId = OpalPalace.activatedAbilities[1].id,
                    manaColorChoice = Color.WHITE,
                    paymentStrategy = PaymentStrategy.Explicit(listOf(plains[index])),
                ))
                activation.error shouldBe null
                val riders = game.state.getEntity(actor)!!.get<ManaPoolComponent>()!!
                    .restrictedMana.count {
                        ManaSpellRider.CommanderCastEntryCounters in it.riders
                    }
                riders shouldBe index + 1
            }
            val cast = game.execute(CastSpell(actor, commander))
            cast.error shouldBe null
            game.state.getEntity(commander)!!
                .get<CommanderManaEntryCountersComponent>()!!.count shouldBe 2
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(commander)!!.get<CountersComponent>()!!
                .getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
        }
    }
}
