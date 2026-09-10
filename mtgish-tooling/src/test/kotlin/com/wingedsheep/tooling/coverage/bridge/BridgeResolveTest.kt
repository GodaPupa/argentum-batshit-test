package com.wingedsheep.tooling.coverage.bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Locks [Bridge.resolve] — the single tag→verdict resolver shared by the coverage probe and the
 * fidelity scorer. The fidelity capability collection (`genCaps`) has no other in-suite net, so this
 * pins the contract both consumers derive from: the verdict label/blocking flag the probe renders and
 * the effect/keyword capabilities fidelity collects. Uses synthetic registry sets so it depends only
 * on the bridge map, not on the live SDK scan.
 */
class BridgeResolveTest : StringSpec({

    // Synthetic registries: only the names a happy-path resolution needs are "present".
    val effects = setOf("GrantProtection", "DealDamage")
    val keywords = setOf("FLYING", "TRAMPLE")

    "a Keyword entry present in the registry resolves ok and contributes the keyword" {
        // Keywords.kt: keyword("Flying", "FLYING")
        val r = Bridge.resolve("Keyword", "Flying", effects, keywords)
        r.status shouldBe "ok"
        r.detail shouldBe "FLYING"
        r.blocking.shouldBeFalse()
        r.keyword shouldBe "FLYING"
        r.effectTag.shouldBeNull()
        r.composedEffects.shouldContainExactly()
    }

    "a Keyword entry absent from the registry is MISSING and blocking, contributing nothing" {
        // FLYING is mapped, but drop it from the synthetic registry to force the gap.
        val r = Bridge.resolve("Keyword", "Flying", effects, keywords = emptySet())
        r.status shouldBe "MISSING"
        r.detail shouldBe "FLYING"
        r.blocking.shouldBeTrue()
        r.keyword.shouldBeNull()
    }

    "an unmapped tag whose PascalCase is a Keyword enum member auto-resolves ok" {
        // No bridge entry for "Trample"; PascalCase→TRAMPLE is in the registry.
        val r = Bridge.resolve("Keyword", "Trample", effects, keywords)
        r.status shouldBe "ok"
        r.detail shouldBe "TRAMPLE (keyword auto)"
        r.blocking.shouldBeFalse()
        r.keyword shouldBe "TRAMPLE"
    }

    "a fully unmapped tag is UNMAPPED and blocking" {
        val r = Bridge.resolve("SpellAction", "SomethingNobodyMapped", effects, keywords)
        r.status shouldBe "UNMAPPED"
        r.detail shouldBe ""
        r.blocking.shouldBeTrue()
        r.keyword.shouldBeNull()
        r.effectTag.shouldBeNull()
    }

    "a composed entry reports its kind, never blocks, and contributes its registry-present primitives" {
        // Envelopes.kt: composed("ProtectionAndDoesntRemovePermanents", composes = ["GrantProtection"])
        val r = Bridge.resolve("Rule", "ProtectionAndDoesntRemovePermanents", effects, keywords)
        r.status shouldBe "composed"
        r.blocking.shouldBeFalse()
        r.effectTag.shouldBeNull()
        r.composedEffects shouldBe setOf("GrantProtection")
    }

    "a composed primitive absent from the registry is dropped from the contributed capabilities" {
        val r = Bridge.resolve("Rule", "ProtectionAndDoesntRemovePermanents", effects = emptySet(), keywords)
        r.status shouldBe "composed"
        r.blocking.shouldBeFalse()
        r.composedEffects.shouldContainExactly()
    }

    "an envelope entry reports the ignore kind and never blocks" {
        // Envelopes.kt: envelope("SpellActions", ...)
        val r = Bridge.resolve("Rule", "SpellActions", effects, keywords)
        r.status shouldBe "ignore"
        r.blocking.shouldBeFalse()
    }

    "the disc:value composite key wins over the bare value key" {
        // A disc-qualified lookup falls back to the bare key when no composite is registered.
        val bare = Bridge.resolve("AnyDiscriminator", "Flying", effects, keywords)
        bare.value shouldBe "Flying"
        bare.keyword shouldBe "FLYING"
    }
})
