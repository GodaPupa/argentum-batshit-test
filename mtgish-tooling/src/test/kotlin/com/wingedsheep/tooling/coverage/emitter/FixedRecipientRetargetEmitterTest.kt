package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.J
import com.wingedsheep.tooling.coverage.render
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject

class FixedRecipientRetargetEmitterTest : StringSpec({
    val ctx = EmitCtx(emptySet())
    fun emit(args: String, target: String? = "stack") = ctx.renderAction(
        J.parseToJsonElement("""{"_Action":"ChangeATargetOfSpellOrAbilityToPermanent","args":$args}""") as JsonObject,
        target,
    )?.let(::render)
    val exact = """[{"_SpellOrAbility":"Ref_TargetSpellOrAbility"},{"_Permanent":"ThisPermanent"}]"""

    "observed fixed recipient preserves the explicit stack binding and self recipient" {
        emit(exact, "EffectTarget.ContextTarget(2)") shouldBe
            "Effects.ChangeTargetTo(stackObject = EffectTarget.ContextTarget(2), recipient = EffectTarget.Self)"
    }
    "fixed recipient without a bound stack object declines" {
        emit(exact, null).shouldBeNull()
    }
    "fixed recipient rejects missing extra and reversed operands" {
        listOf("[]", """[{"_SpellOrAbility":"Ref_TargetSpellOrAbility"}]""",
            exact.dropLast(1) + ",{}]",
            """[{"_Permanent":"ThisPermanent"},{"_SpellOrAbility":"Ref_TargetSpellOrAbility"}]"""
        ).forEach { emit(it).shouldBeNull() }
    }
    "fixed recipient rejects unknown subjects recipients and nested qualifiers" {
        listOf(
            exact.replace("Ref_TargetSpellOrAbility", "Trigger_ThatSpell"),
            exact.replace("ThisPermanent", "Ref_TargetPermanent"),
            exact.replace("ThisPermanent", "EnchantedPermanent"),
            """[{"_SpellOrAbility":"Ref_TargetSpellOrAbility","args":{}},{"_Permanent":"ThisPermanent"}]""",
            """[{"_SpellOrAbility":"Ref_TargetSpellOrAbility"},{"_Permanent":"ThisPermanent","args":{}}]""",
            """{"_SpellOrAbility":"Ref_TargetSpellOrAbility"}"""
        ).forEach { emit(it).shouldBeNull() }
    }
    "generic retarget remains the existing generic effect" {
        ctx.renderAction(
            J.parseToJsonElement("""{"_Action":"ChangeTargetsOfSpellOrAbility"}""") as JsonObject,
            "stack",
        )?.let(::render) shouldBe "Effects.ChangeTarget()"
    }
})
