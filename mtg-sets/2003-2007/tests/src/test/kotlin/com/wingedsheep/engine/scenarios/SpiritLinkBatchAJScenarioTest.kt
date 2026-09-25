package com.wingedsheep.engine.scenarios

import com.wingedsheep.mtg.sets.definitions.`10e`.cards.SpiritLink
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.ContextPropertyKey
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SpiritLinkBatchAJScenarioTest : FunSpec({
    test("Spirit Link reuses the attached damage to controller life-gain rail exactly") {
        SpiritLink.manaCost shouldBe ManaCost.parse("{W}")
        SpiritLink.typeLine.toString() shouldBe "Enchantment — Aura"
        SpiritLink.auraTarget.shouldBeInstanceOf<TargetCreature>()

        val ability = SpiritLink.script.triggeredAbilities.single()
        ability.binding shouldBe TriggerBinding.ATTACHED

        val gain = ability.effect.shouldBeInstanceOf<GainLifeEffect>()
        gain.amount shouldBe DynamicAmount.ContextProperty(ContextPropertyKey.TRIGGER_DAMAGE_AMOUNT)
    }
})
