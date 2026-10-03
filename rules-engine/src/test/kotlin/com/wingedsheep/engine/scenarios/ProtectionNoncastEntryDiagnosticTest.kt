package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutorRegistry
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GrantProtectionFromChosenColorToGroup
import com.wingedsheep.sdk.scripting.effects.ReturnSelfToBattlefieldAttachedEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe

/** Kept separate from lifecycle qualification: real noncast entry must announce its as-entry choice. */
class ProtectionNoncastEntryDiagnosticTest : ScenarioTestBase() {
    init {
        val host = card("Noncast Choice Host") {
            manaCost = "{G}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
        }
        val aura = card("Noncast Choice Aura") {
            manaCost = "{W}"; typeLine = "Enchantment — Aura"; auraTarget = Targets.Creature
            replacementEffect(EntersWithChoice(ChoiceType.COLOR))
            staticAbility { ability = GrantProtectionFromChosenColorToGroup(
                filter = GroupFilter.attachedCreature(), retainsPreexistingControlledAttachments = true) }
        }
        cardRegistry.register(host); cardRegistry.register(aura)
        test("returning an Aura attached announces its color before completing entry") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, host.name)
                .withCardInGraveyard(1, aura.name).build()
            val hostId = g.findPermanent(host.name)!!
            val source = g.findCardsInGraveyard(1, aura.name).single()
            val controller = g.state.projectedState.getController(hostId)!!
            val result = EffectExecutorRegistry(cardRegistry = cardRegistry).execute(g.state,
                ReturnSelfToBattlefieldAttachedEffect(EffectTarget.SpecificEntity(hostId)),
                EffectContext(sourceId = source, controllerId = controller))
            (result.pendingDecision is ChooseColorDecision) shouldBe true
        }
    }
}
